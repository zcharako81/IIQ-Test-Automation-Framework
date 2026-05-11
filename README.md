# SailPoint IdentityIQ Test Automation Framework

A modular and extensible test automation framework for **SailPoint IdentityIQ (IIQ)** using **SCIM APIs**, **TestNG**, and **REST-Assured**.
It can be executed standalone or integrated into a DevOps pipeline. 

This framework supports end-to-end IAM testing including:

* ✅ Create identities / Modify identities / Delete identities
* ✅ Multi-identity lifecycle — test multiple identities in one run
* ✅ Multi-value attribute support
* ✅ Launch Workflows
* ✅ Launch Tasks
* ✅ Verify Identity attributes
* ✅ Verify Birthright Role assignments (multi-assignement)
* ✅ Verify provisioned accounts (multi-application)
* ✅ Multi-round modify — repeat modify/verify/accounts with different values
---

## 🧱 Tech Stack

| Component        | Technology |
|-----------------|-----------|
| Language        | Java 11+ |
| Build Tool      | Maven |
| Test Framework  | TestNG |
| API Testing     | REST-Assured |
| Configuration   | Properties files |
| Tested Against | SailPoint IdentityIQ 8.5 |
---

## 📁 Project Structure
```
src/test/java
│
├── base/                # Core framework classes (API, config, auth, SCIM schemas)
├── model/               # SCIM models (Identity, Workflow, etc.)
├── services/            # API service layer (Identity, Workflow)
├── factory/             # Test data builders
├── utils/               # Helper utilities (waits, validation)
├── tests/
│   ├── base/            # Base test classes
│   └── identity/        # Identity lifecycle tests
│
src/test/resources
├── config.properties    # Global test config (URL, auth, task names, timeouts)
├── identity.properties  # Identity + account test data (per-identity)
│
src/test/iiq
│
└── config/My-WF-TaskLauncher   # Workflow XML (must be imported into IIQ)
```
---

## 👉 Instructions

- **Prerequisite**: Workflow `My-WF-TaskLauncher` must be imported into IIQ before test execution.
- **Task names**: Can be changed in `config.properties` (e.g. for Identity Refresh or Account Aggregation). The identity name is passed as a task filter to reduce execution time.
- **Multi-identity mode**: Define identities via the `identities` key in `identity.properties`. Each identity gets its own set of input, expected, role, and account properties. Accounts are defined via `identity.<key>.accounts` (comma-separated for multiple accounts per identity).
- **Optional create phase**: The `create` phase is no longer mandatory. If omitted from an identity's `.tests` list, the framework looks up the existing identity by `userName` using a SCIM filter query. To reference identities from a previous run, set `test.suffix` in `config.properties` to the suffix value used during that creation run. Without `test.suffix`, a new timestamp is generated each run.
- **managerValue**: Must be replaced with a valid IIQ identity ID (the `id` field of an existing user, e.g. `spadmin`).
- **{suffix} placeholder**: Appended to `userName`, `email`, and account attributes like `uid` and `cn` to ensure uniqueness per run. When `test.suffix` is set in `config.properties`, that value is used. When omitted, no suffix is applied — properties are used as-is.
- **SailPoint extension (generic)**: Any SailPoint SCIM extension attribute can be added via the `sailpoint.` prefix in property keys. Input: `identity.<key>.input.sailpoint.<attrName>=<value>`. Expected: `identity.<key>.expected.sailpoint.<attrName>=<value>`. This dynamically builds the `urn:ietf:params:scim:schemas:sailpoint:1.0:User` map without touching Java code. For multi-value array attributes, append `[]` to the key name: `identity.<key>.input.sailpoint.capabilities[]=val1,val2,val3` — the value is split by comma and sent as a JSON array. Single-value attributes use no suffix. Optional attributes can be removed entirely — the framework skips them gracefully.
- **Multiple roles**: Defined as comma-separated values in `identity.<key>.expected.roles`. For example: `identity.user1.expected.roles=ALL_ACTIVE_USERS,ANOTHER_ROLE`.
- **Test class**: `src/test/java/tests/identity/IdentityTest.java` (suite defined in `Testng.xml`).

## ⚙️ Configuration

All configuration is driven by two properties files loaded in order (later wins on key collision):

| File | Purpose |
|---|---|
| `config.properties` | IIQ URL, auth, workflow/task names, timeouts, logging, aggregation task mappings |
| `identity.properties` | Identity input + expected attributes, roles, and **per-identity account validation** |

### Global config (`config.properties`)

```
base.url=http://localhost:8080/identityiq
auth.type=basic
username=REPLACE_ME
password=REPLACE_ME

workflow.name=My-WF-TaskLauncher

task.refresh=RefreshIdentitySingle
# Application aggregation tasks — one per app key used in identity.properties
task.aggregation.ldap=LdapAccountAggregation
task.aggregation.ad=AdAccountAggregation
task.aggregation.scim=ScimAccountAggregration

# --- Wait timeouts (read by TestUtils helpers) ---
wait.timeout.seconds=60
wait.poll.interval.ms=2000
wait.aggregation.poll.interval.ms=5000

# --- Logging ---
logging.enabled=false

# --- Optional suffix (omit to use values as-is) ---
# test.suffix=1712345678901
```

### Identity + Account test data (`identity.properties`)

Each identity key (`user1`, `user2`, etc.) has its own block covering input, expected attributes, roles, and account expectations:

```
# List of identity keys for multi-identity mode (required)
identities=user1,user2

# Optional per-identity phase selection (absent = run all phases).
# Omit 'create' to reference an identity from a prior creation run:
#   identity.user3.tests=refresh,verifyCreate,delete
#
# --- Identity: user1 ---
identity.user1.input.userName=john.doe
identity.user1.input.firstname=John
identity.user1.input.lastname=Doe
identity.user1.input.displayName=John Doe
identity.user1.input.email=john.doe@acme.com
identity.user1.input.userType=employee
identity.user1.input.active=true
# SailPoint extension — any attribute via sailpoint. prefix (dynamically mapped)
identity.user1.input.sailpoint.title=Software Engineer
identity.user1.input.sailpoint.department=Engineering
identity.user1.input.sailpoint.location=New York
# Multi-value array attribute (suffix [] in key name)
identity.user1.input.sailpoint.capabilities[]=Auditor,Role Administrator

identity.user1.expected.userName=john.doe.{suffix}
identity.user1.expected.firstname=John
identity.user1.expected.lastname=Doe
identity.user1.expected.email={suffix}.john.doe@acme.com
identity.user1.expected.userType=employee
identity.user1.expected.sailpoint.title=Software Engineer
identity.user1.expected.sailpoint.department=Engineering
identity.user1.expected.sailpoint.location=New York
identity.user1.expected.sailpoint.capabilities[]=Auditor,Role Administrator
# comma-separated for multiple roles
identity.user1.expected.roles=ALL_ACTIVE_USERS,ANOTHER_ROLE

# Per-identity account validation (comma-separated for multiple accounts)
identity.user1.accounts=ldap
identity.user1.account.ldap.application=LDAP-Test
identity.user1.account.ldap.expected.exists=true
identity.user1.account.ldap.expected.attributes.uid=john.doe.{suffix}
identity.user1.account.ldap.expected.attributes.cn=john.doe.{suffix}
identity.user1.account.ldap.expected.attributes.givenName=John
identity.user1.account.ldap.expected.attributes.sn=Doe
# Add a second app:
# identity.user1.accounts=ldap,ad
# identity.user1.account.ad.application=ActiveDirectory-Test
# identity.user1.account.ad.expected.exists=true
# identity.user1.account.ad.expected.attributes.sAMAccountName=john.doe.{suffix}
```

The `{suffix}` placeholder is automatically replaced at runtime with the configured `test.suffix` value (or removed if empty), matching the suffix appended to identities during creation.

### Attribute schema split — `.input.*` vs `.input.sailpoint.*`

Attributes in `identity.properties` are split into two groups that map to **different SCIM JSON namespaces**:

| Prefix | SCIM Schema | Java handling | Example |
|---|---|---|---|
| `.input.<attr>` (no `sailpoint.`) | `urn:ietf:params:scim:schemas:core:2.0:User` + `urn:ietf:params:scim:schemas:extension:enterprise:2.0:User` (manager) | **Compiled** — dedicated fields in the `Identity` POJO (`userName`, `name.givenName`, `name.familyName`, `displayName`, `userType`, `emails`, `active`, `manager`) | `identity.user1.input.firstname=John` |
| `.input.sailpoint.<attr>` | `urn:ietf:params:scim:schemas:sailpoint:1.0:User` | **Generic** — stored as `Map<String, Object>`; discovered dynamically by property prefix. Adding a new attribute requires **zero Java code changes**. | `identity.user1.input.sailpoint.title=Software Engineer` |

**Why two mechanisms?** Core SCIM 2.0 attributes (`userName`, `name`, `emails`) are well-defined standards that change infrequently — they benefit from compile-time safety with typed POJO fields. SailPoint extension attributes (`title`, `department`, `location`, `capabilities`, `costcenter`, etc.) are IIQ ObjectConfig fields specific to each deployment. The generic `sailpoint.` prefix lets you add any IIQ attribute without touching Java, keeping the framework deployment-agnostic. The same split applies to `.expected.*` / `.expected.sailpoint.*` for verification. For multi-value array attributes, append `[]` to the property key: `identity.user1.input.sailpoint.capabilities[]=val1,val2,val3`.

**Optionality**: Any `.input.sailpoint.*` or `.expected.sailpoint.*` line can be removed entirely — the framework skips it gracefully during creation and verification. For multi-value array attributes, append `[]` to the key name: `identity.user1.input.sailpoint.capabilities[]=val1,val2,val3` — the value is split by comma and sent as a JSON array. Single-value attributes use no suffix.

### Account validation flow

1. `GET /Users/{id}?attributes=...accounts` — returns account references (`displayName`, `value`, `$ref`)
2. For each reference, `GET $ref` — fetches the full Account resource which includes `application` (a reference object with `displayName`) and schema-specific attributes (e.g., `urn:ietf:params:scim:schemas:sailpoint:1.0:Application:Schema:LDAP-Test:account`)
3. Match by `application.displayName` against the expected application name
4. Validate expected attributes against the schema-specific nested map

> **Note:** This framework uses **LDAP** as the target application for account provisioning. The dummy application name configured in the test data is `LDAP-Test`. Both the application name and expected account attributes can be customized per identity in `identity.properties`.

### Modify lifecycle — `.modify.*` and `.expectedAfterModify.*`

After creation and account verification, the framework modifies each identity via **SCIM PUT** (`PUT /scim/v2/Users/{id}`) and then re-verifies:

```
verifyAccounts → modify → verifyModify → deleteAccounts
```

- **`.modify.*`** — Partial attribute set documented in `identity.properties`. Not read by Java directly (the PUT uses `.expectedAfterModify.*` as the full representation), but kept for documentation of what changed.
- **`.expectedAfterModify.*`** — Full expected state after modification. Read by `IdentityDataFactory.createIdentityForModify()` and verified by `verifyIdentity()`.

The same schema split applies: `.expectedAfterModify.*` for core/enterprise, `.expectedAfterModify.sailpoint.*` for the SailPoint extension. The `{suffix}` placeholder is supported the same way as `.expected.*`. When no `test.suffix` is configured, `{suffix}` is replaced with an empty string — adjust your properties accordingly.

Example:
```
identity.user1.modify.displayName=John Doe PATCHED
identity.user1.modify.sailpoint.title=Senior Software Engineer

identity.user1.expectedAfterModify.userName=john.doe.{suffix}
identity.user1.expectedAfterModify.firstname=John
identity.user1.expectedAfterModify.displayName=John Doe PATCHED
identity.user1.expectedAfterModify.sailpoint.title=Senior Software Engineer
identity.user1.expectedAfterModify.sailpoint.Identity_End_Date=2029-12-31
```

### Multi-round modify + account verification

Phases `modify`, `verifyModify`, and `verifyAccounts` support an **optional qualifier** (colon separator) to run multiple modification rounds with different values. The qualifier maps to indexed property sections:

```
# Phase list with 2 modify rounds, each followed by account re-verification
identity.user1.tests=create,...,modify:1,verifyModify:1,verifyAccounts:1,\
  modify:2,verifyModify:2,verifyAccounts:2,deleteAccounts,delete

# Round 1 — lastname → Smith
identity.user1.expectedAfterModify.1.lastname=Smith
identity.user1.expectedAfterModify.1.displayName=John Smith
# ...
identity.user1.accounts.1=ldap
identity.user1.account.1.ldap.expected.attributes.sn=Smith

# Round 2 — lastname → Jones
identity.user1.expectedAfterModify.2.lastname=Jones
identity.user1.expectedAfterModify.2.displayName=John Jones
# ...
identity.user1.accounts.2=ldap
identity.user1.account.2.ldap.expected.attributes.sn=Jones
```

**Property section mapping:**

| Phase | Property prefix (no qualifier) | Property prefix (qualifier `N`) |
|---|---|---|
| `modify` | `identity.<key>.expectedAfterModify.*` | `identity.<key>.expectedAfterModify.N.*` |
| `verifyModify` | `identity.<key>.expectedAfterModify.*` | `identity.<key>.expectedAfterModify.N.*` |
| `verifyAccounts` | `identity.<key>.accounts`, `identity.<key>.account.<type>.*` | `identity.<key>.accounts.N`, `identity.<key>.account.N.<type>.*` |

Unqualified `modify`/`verifyModify`/`verifyAccounts` remain fully backward compatible.

---

## 📐 API Contract Constants — `base/ScimSchemas.java`

SCIM schema URNs and REST endpoint paths are **API-contract constants** defined by RFC 7644 and the SailPoint SCIM extension. They never change between environments, so they live in a single Java constants class rather than `config.properties`:

| Constant | Value |
|---|---|
| `SCHEMA_CORE_USER` | `urn:ietf:params:scim:schemas:core:2.0:User` |
| `SCHEMA_ENTERPRISE_USER` | `urn:ietf:params:scim:schemas:extension:enterprise:2.0:User` |
| `SCHEMA_SAILPOINT_USER` | `urn:ietf:params:scim:schemas:sailpoint:1.0:User` |
| `SCHEMA_SAILPOINT_WORKFLOW` | `urn:ietf:params:scim:schemas:sailpoint:1.0:LaunchedWorkflow` |
| `SCHEMA_SAILPOINT_APP_ACCOUNT_PREFIX` | `urn:...:Application:Schema:` |
| `SCIM_BASE_PATH` | `/scim/v2` |
| `USERS_ENDPOINT` | `/Users` |
| `WORKFLOWS_ENDPOINT` | `/LaunchedWorkflows` |
| `USERS_FULL_PATH` | `/scim/v2/Users` |
| `QUERY_ROLES` | `attributes=...User:roles` |
| `QUERY_ACCOUNTS` | `attributes=...User:accounts` |
| `JSONPATH_ENTERPRISE` | `'...enterprise:2.0:User'.` |
| `JSONPATH_SAILPOINT` | `'...sailpoint:1.0:User'.` |

The derived constants (`QUERY_*`, `JSONPATH_*`, `USERS_FULL_PATH`) are built from the base URNs and paths, ensuring every reference across services, models, and tests stays in sync. If SailPoint ever changes a schema URN in a future release, you update it in **one place** only.

---

## 🧪 Test Execution

Run all tests:

```bash
mvn clean test
```

Or via TestNG suite:

```
mvn test -DsuiteXmlFile=Testng.xml
```

The single `@Test` method `testLifecycle()` runs a per-identity ordered phase list read from `identity.<key>.tests` in `identity.properties`. Phases execute in the order listed; duplicates allowed for repeatable scenarios. Default lifecycle order:

```
create → refresh → aggregation → verifyCreate → verifyRoles → verifyAccounts → modify → verifyModify → deleteAccounts → delete
```

Each identity's phase list runs independently (per-identity mode). If `.tests` property is absent, the full default lifecycle above runs.

**Qualified phases for multi-round modify** — `modify`, `verifyModify`, and `verifyAccounts` support an optional colon qualifier to target different property sections. This enables multiple modification rounds with different values:

```
identity.user1.tests=create,refresh,aggregation,verifyCreate,verifyRoles,\
  verifyAccounts,modify:1,verifyModify:1,verifyAccounts:1,\
  modify:2,verifyModify:2,verifyAccounts:2,deleteAccounts,delete
```

Each qualifier maps to an indexed property section. See [Multi-round modify + account verification](#multi-round-modify--account-verification) for the property naming convention.

**Leaver / rehire scenario** — repeat `modify → verifyModify → verifyAccounts` to simulate attribute changes triggering downstream provisioning:

```
identity.user1.tests=create,refresh,aggregation,verifyCreate,verifyRoles,verifyAccounts,modify,verifyModify,verifyAccounts,deleteAccounts,delete
```

Any phase can be repeated any number of times. No Java changes needed.

---

## 📦 Dependencies

Defined in `pom.xml`:

- `org.testng:testng`
- `io.rest-assured:rest-assured`
- `org.apache.commons:commons-lang3`
- (Optional) JSON serializer like Jackson (if added later)

---

🧠 Design Principles

* Separation of concerns (model / service / test)
* Config-driven test data
* SCIM-first approach
* Reusable utilities
* Extensible for multiple connectors
* Per-identity configuration for multi-identity scenarios
---
👤 Author: Zisis Charakopidis (zisis.charakopidis@icloud.com)
