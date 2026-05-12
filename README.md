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

This project is an independent test automation framework and is not affiliated with or endorsed by SailPoint Technologies. It contains no SailPoint code, libraries, JARs or binaries. 

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
├── config.properties    # Global test config (URL, auth, timeouts)
├── identity.properties  # Identity + account test data (per-identity)
│
src/test/iiq
│
└── config/My-WF-TaskLauncher   # Workflow XML (must be imported into IIQ)
```
---

## 👉 Instructions

- **Prerequisite**: Workflow `My-WF-TaskLauncher` must be imported into IIQ before test execution.
- **All tests are defined in `identity.properties`**: The entire test scenario — identities, lifecycle phases, expected attributes, roles, accounts, and account attributes — is configured in a single properties file. No Java code changes are needed to define or modify test cases.
- **Define your test scenario**: Start by listing your test identities under the `identities` key. For each identity, provide input attributes, expected values, expected roles, and account validations. Everything is driven by property conventions documented below.
- **Phase list**: Define the identity lifecycle via `identity.<key>.tests` in `identity.properties`. All tasks are launched via the unified `task:<taskName>` phase (e.g. `task:RefreshIdentitySingle`, `task:LdapAccountAggregation`). The identity name is passed automatically as a workflow filter.
- **Multi-identity mode**: Define identities via the `identities` key in `identity.properties`. Each identity gets its own set of input, expected, role, and account properties.
- **Optional create phase**: If omitted, the framework looks up the identity by `userName` using a SCIM filter query (must already exist in IIQ).
- **{suffix} placeholder**: Controlled by `test.suffix` in `config.properties`: `random` auto-generates a timestamp, a fixed value reuses a prior run's suffix, omitted uses values as-is.
- **SailPoint extension attributes**: Add any IIQ attribute via the `sailpoint.` prefix (e.g. `identity.<key>.input.sailpoint.title=Software Engineer`). Multi-value arrays use `[]` suffix: `capabilities[]=val1,val2`. No Java code changes needed.
- **Multiple roles**: Defined as comma-separated values in `identity.<key>.expected.roles`.
- **Test class**: `src/test/java/tests/identity/IdentityTest.java` (suite defined in `Testng.xml`).

---

## ⚙️ Configuration

All configuration is driven by two properties files loaded in order (later wins on key collision):

| File | Purpose |
|---|---|
| `config.properties` | IIQ URL, auth, timeouts, logging, suffix |
| `identity.properties` | Identity input + expected attributes, roles, and **per-identity account validation** |

### Global config (`config.properties`)

```
base.url=http://localhost:8080/identityiq
auth.type=basic
username=REPLACE_ME
password=REPLACE_ME

workflow.name=My-WF-TaskLauncher

# --- Wait timeouts ---
wait.timeout.seconds=60
wait.poll.interval.ms=2000

# --- Logging ---
logging.enabled=false

# --- Suffix for uniqueness across test runs ---
#   random      → auto-generated from System.currentTimeMillis()
#   <fixed>     → fixed value for cross-run reuse
#   (absent)    → no suffix, values used as-is
test.suffix=random
```

### Identity + Account test data (`identity.properties`)

```
# List of identity keys for multi-identity mode (required)
identities=user1,user2

# Per-identity phase list (absent = run all default phases)
identity.user1.tests=create,task:RefreshIdentitySingle,\
  task:LdapAccountAggregation,verifyCreate,verifyRoles,\
  verifyAccounts,modify:1,verifyModify:1,\
  verifyAccounts:1,deleteAccounts,delete

# --- Identity: user1 ---
identity.user1.input.userName=john.doe
identity.user1.input.firstname=John
identity.user1.input.lastname=Doe
identity.user1.input.displayName=John Doe
identity.user1.input.email=john.doe@acme.com
identity.user1.input.userType=employee
identity.user1.input.active=true
identity.user1.input.sailpoint.title=Software Engineer
identity.user1.input.sailpoint.department=Engineering
identity.user1.input.sailpoint.location=New York
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
identity.user1.expected.roles=ALL_ACTIVE_USERS,ANOTHER_ROLE

# Per-identity account validation
identity.user1.accounts=ldap
identity.user1.account.ldap.application=LDAP-Test
identity.user1.account.ldap.expected.exists=true
identity.user1.account.ldap.expected.attributes.uid=john.doe.{suffix}
identity.user1.account.ldap.expected.attributes.cn=john.doe.{suffix}
identity.user1.account.ldap.expected.attributes.givenName=John
identity.user1.account.ldap.expected.attributes.sn=Doe
```

### Attribute schema split — `.input.*` vs `.input.sailpoint.*`

Attributes map to different SCIM JSON namespaces:

| Prefix | SCIM Schema | Java handling |
|---|---|---|
| `.input.<attr>` | `urn:ietf:params:scim:schemas:core:2.0:User` + enterprise extension | Compiled POJO fields (`userName`, `name`, `displayName`, `userType`, `emails`, `active`, `manager`) |
| `.input.sailpoint.<attr>` | `urn:ietf:params:scim:schemas:sailpoint:1.0:User` | Generic `Map<String, Object>` — adding attributes requires **zero Java changes** |

Any `sailpoint.*` property is optional; skip the line and the framework omits it. Multi-value arrays use `[]` in the key name (e.g. `capabilities[]=val1,val2`).

### Account validation flow

1. `GET /Users/{id}?attributes=...accounts` — returns account references
2. For each reference, `GET $ref` — fetches the Account resource including `application.displayName` and schema-specific attributes
3. Match by `application.displayName` against the expected application name
4. Validate expected attributes against the schema-specific nested map

### Modify lifecycle

The framework modifies identities via **SCIM PUT** (`PUT /scim/v2/Users/{id}`) and re-verifies:

```
verifyAccounts → modify → verifyModify → deleteAccounts
```

- **`.modify.*`** — Documents what changed (not read by Java).
- **`.expectedAfterModify.*`** — Full expected state after modification. Supports `{suffix}` and the same core/sailpoint schema split.

Example:
```
identity.user1.expectedAfterModify.userName=john.doe.{suffix}
identity.user1.expectedAfterModify.displayName=John Doe PATCHED
identity.user1.expectedAfterModify.sailpoint.title=Senior Software Engineer
identity.user1.expectedAfterModify.sailpoint.Identity_End_Date=2029-12-31
```

### Multi-round modify + account verification

Phases `modify`, `verifyModify`, and `verifyAccounts` support an optional colon qualifier for multiple rounds:

```
identity.user1.tests=...,modify:1,verifyModify:1,verifyAccounts:1,\
  modify:2,verifyModify:2,verifyAccounts:2,...

identity.user1.expectedAfterModify.1.lastname=Smith
identity.user1.accounts.1=ldap
identity.user1.account.1.ldap.expected.attributes.sn=Smith

identity.user1.expectedAfterModify.2.lastname=Jones
identity.user1.accounts.2=ldap
identity.user1.account.2.ldap.expected.attributes.sn=Jones
```

| Phase | Property prefix (no qualifier) | Property prefix (qualifier `N`) |
|---|---|---|
| `modify` / `verifyModify` | `identity.<key>.expectedAfterModify.*` | `identity.<key>.expectedAfterModify.N.*` |
| `verifyAccounts` | `identity.<key>.accounts`, `account.<type>.*` | `identity.<key>.accounts.N`, `account.N.<type>.*` |

---

## 📐 API Contract Constants — `base/ScimSchemas.java`

SCIM schema URNs and REST endpoint paths are API-contract constants defined in a single class:

| Constant | Value |
|---|---|
| `SCHEMA_CORE_USER` | `urn:ietf:params:scim:schemas:core:2.0:User` |
| `SCHEMA_ENTERPRISE_USER` | `urn:ietf:params:scim:schemas:extension:enterprise:2.0:User` |
| `SCHEMA_SAILPOINT_USER` | `urn:ietf:params:scim:schemas:sailpoint:1.0:User` |
| `SCHEMA_SAILPOINT_WORKFLOW` | `urn:ietf:params:scim:schemas:sailpoint:1.0:LaunchedWorkflow` |
| `SCHEMA_SAILPOINT_APP_ACCOUNT_PREFIX` | `urn:...:Application:Schema:` |
| `USERS_FULL_PATH` | `/scim/v2/Users` |
| `WORKFLOWS_ENDPOINT` | `/LaunchedWorkflows` |
| `QUERY_ROLES` | `attributes=...User:roles` |
| `QUERY_ACCOUNTS` | `attributes=...User:accounts` |

---

## 🧪 Test Execution

```bash
mvn clean test
```

Or via TestNG suite:

```
mvn test -DsuiteXmlFile=Testng.xml
```

The single `@Test` method `testLifecycle()` runs a per-identity ordered phase list from `identity.<key>.tests`. Default lifecycle:

```
create → verifyCreate → verifyRoles → verifyAccounts → modify → verifyModify → deleteAccounts → delete
```

If `.tests` is absent, the full default lifecycle above runs. Phases can be repeated; duplicates allowed.

### Qualified phases

| Phase | Qualifier | Purpose |
|---|---|---|
| `task:<taskName>` | IIQ task name (e.g. `RefreshIdentitySingle`) | Run any IIQ task by name. The current identity is passed as a filter. |
| `modify:<N>`, `verifyModify:<N>` | Numeric index | Multi-round modify — maps to `expectedAfterModify.<N>.*` |
| `verifyAccounts:<N>` | Numeric index | Account re-verification per round — maps to `accounts.N` / `account.N.<type>.*` |

```
# Multi-round modify
identity.user1.tests=...,modify:1,verifyModify:1,verifyAccounts:1,\
  modify:2,verifyModify:2,verifyAccounts:2,...

# Arbitrary task
identity.user1.tests=...,task:SomeCustomTask,verifyCreate,...

# Account aggregation
identity.user1.tests=...,task:RefreshIdentitySingle,\
  task:LdapAccountAggregation,task:LdapAccountGroupAggregation,\
  verifyCreate,...

# Leaver / rehire
identity.user1.tests=create,task:RefreshIdentitySingle,verifyCreate,\
  verifyRoles,verifyAccounts,modify,verifyModify,verifyAccounts,\
  modify,verifyModify,verifyAccounts,deleteAccounts,delete
```

---

## 📦 Dependencies

- `org.testng:testng`
- `io.rest-assured:rest-assured`
- `org.apache.commons:commons-lang3`

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
