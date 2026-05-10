# SailPoint IdentityIQ Test Automation Framework

A modular and extensible test automation framework for **SailPoint IdentityIQ (IIQ)** using **SCIM APIs**, **TestNG**, and **REST-Assured**.
It can be executed standalone or integrated to a DevOps pipeline. 

This framework supports end-to-end IAM testing including:

* ✅ Create identities / Modify identities / Delete identities
* ✅ Multi-identity lifecycle — test multiple identities in one run
* ✅ Launch Workflows (via SCIM)
* ✅ Launch Tasks (via Workflow)
* ✅ Verify Identity attributes
* ✅ Verify Birthright Role assignments (multi-assignement)
* ✅ Verify provisioned accounts (multi-application)
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
├── base/                # Core framework classes (API, config, auth)
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
- **managerValue**: Must be replaced with a valid IIQ identity ID (the `id` field of an existing user, e.g. `spadmin`).
- **{suffix} placeholder**: Appended to `userName`, `email`, and account attributes like `uid` and `cn` to ensure uniqueness per run (resolved from `System.currentTimeMillis()`).
- **SailPoint extension (generic)**: Any SailPoint SCIM extension attribute can be added via the `sailpoint.` prefix in property keys. Input: `identity.<key>.input.sailpoint.<attrName>=<value>`. Expected: `identity.<key>.expected.sailpoint.<attrName>=<value>`. This dynamically builds the `urn:ietf:params:scim:schemas:sailpoint:1.0:User` map without touching Java code. Known array attributes (`capabilities`, `costcenter`) can be comma-separated and are automatically converted to JSON arrays. Optional attributes can be removed entirely — the framework skips them gracefully.
- **Multiple roles**: Defined as comma-separated values in `identity.<key>.expected.roles`. For example: `identity.user1.expected.roles=ALL_ACTIVE_USERS,ANOTHER_ROLE`.
- **Test class**: `src/test/java/tests/identity/IdentityTest.java` (suite defined in `Testng.xml`).

## ⚙️ Configuration

All configuration is driven by two properties files loaded in order (later wins on key collision):

| File | Purpose |
|---|---|
| `config.properties` | IIQ URL, auth, SCIM paths, workflow/task names, timeouts |
| `identity.properties` | Identity input + expected attributes, roles, and **per-identity account validation** |

### Global config (`config.properties`)

```
base.url=http://localhost:8080/identityiq
auth.type=basic
username=REPLACE_ME
password=REPLACE_ME

scim.base.path=/scim/v2
scim.users.endpoint=/Users
scim.workflows.endpoint=/LaunchedWorkflows

workflow.name=My-WF-TaskLauncher
task.name1=RefreshIdentitySingle
task.name2=LdapAccountAggregation

identity.scim.roles=attributes=urn:ietf:params:scim:schemas:sailpoint:1.0:User:roles
identity.scim.accounts=attributes=urn:ietf:params:scim:schemas:sailpoint:1.0:User:accounts

wait.timeout.seconds=30
workflow.wait.timeout.seconds=60
```

### Identity + Account test data (`identity.properties`)

Each identity key (`user1`, `user2`, etc.) has its own block covering input, expected attributes, roles, and account expectations:

```
# List of identity keys for multi-identity mode (required)
identities=user1,user2

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

identity.user1.expected.userName=john.doe.{suffix}
identity.user1.expected.firstname=John
identity.user1.expected.lastname=Doe
identity.user1.expected.email={suffix}.john.doe@acme.com
identity.user1.expected.userType=employee
identity.user1.expected.sailpoint.title=Software Engineer
identity.user1.expected.sailpoint.department=Engineering
identity.user1.expected.sailpoint.location=New York
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
```

The `{suffix}` placeholder is automatically replaced at runtime with `System.currentTimeMillis()`, matching the unique suffix appended to identities during creation.

### Attribute schema split — `.input.*` vs `.input.sailpoint.*`

Attributes in `identity.properties` are split into two groups that map to **different SCIM JSON namespaces**:

| Prefix | SCIM Schema | Java handling | Example |
|---|---|---|---|
| `.input.<attr>` (no `sailpoint.`) | `urn:ietf:params:scim:schemas:core:2.0:User` + `urn:ietf:params:scim:schemas:extension:enterprise:2.0:User` (manager) | **Compiled** — dedicated fields in the `Identity` POJO (`userName`, `name.givenName`, `name.familyName`, `displayName`, `userType`, `emails`, `active`, `manager`) | `identity.user1.input.firstname=John` |
| `.input.sailpoint.<attr>` | `urn:ietf:params:scim:schemas:sailpoint:1.0:User` | **Generic** — stored as `Map<String, Object>`; discovered dynamically by property prefix. Adding a new attribute requires **zero Java code changes**. | `identity.user1.input.sailpoint.title=Software Engineer` |

**Why two mechanisms?** Core SCIM 2.0 attributes (`userName`, `name`, `emails`) are well-defined standards that change infrequently — they benefit from compile-time safety with typed POJO fields. SailPoint extension attributes (`title`, `department`, `location`, `capabilities`, `costcenter`, etc.) are IIQ ObjectConfig fields specific to each deployment. The generic `sailpoint.` prefix lets you add any IIQ attribute without touching Java, keeping the framework deployment-agnostic. The same split applies to `.expected.*` / `.expected.sailpoint.*` for verification.

**Optionality**: Any `.input.sailpoint.*` or `.expected.sailpoint.*` line can be removed entirely — the framework skips it gracefully during creation and verification.

### Account validation flow

1. `GET /Users/{id}?attributes=...accounts` — returns account references (`displayName`, `value`, `$ref`)
2. For each reference, `GET $ref` — fetches the full Account resource which includes `application` (a reference object with `displayName`) and schema-specific attributes (e.g., `urn:ietf:params:scim:schemas:sailpoint:1.0:Application:Schema:LDAP-Test:account`)
3. Match by `application.displayName` against the expected application name
4. Validate expected attributes against the schema-specific nested map

> **Note:** This framework uses **LDAP** as the target application for account provisioning. The dummy application name configured in the test data is `LDAP-Test`. Both the application name and expected account attributes can be customized per identity in `identity.properties`.

### Modify lifecycle — `.modify.*` and `.expectedAfterModify.*`

After creation and account verification, the framework modifies each identity via **SCIM PUT** (`PUT /scim/v2/Users/{id}`) and then re-verifies:

```
testVerifyAccounts → testModifyIdentities → testVerifyModifiedIdentities → testDeleteAccounts
```

- **`.modify.*`** — Partial attribute set documented in `identity.properties`. Not read by Java directly (the PUT uses `.expectedAfterModify.*` as the full representation), but kept for documentation of what changed.
- **`.expectedAfterModify.*`** — Full expected state after modification. Read by `IdentityDataFactory.createIdentityForModify()` and verified by `testVerifyModifiedIdentities()`.

The same schema split applies: `.expectedAfterModify.*` for core/enterprise, `.expectedAfterModify.sailpoint.*` for the SailPoint extension. The `{suffix}` placeholder is supported the same way as `.expected.*`.

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

Tests are strictly sequential via `dependsOnMethods`:
1. `testCreateIdentities`
2. `testLaunchWorkflowRefreshIdentities`
3. `testLaunchWorkflowLdapAggregation`
4. `testVerifyIdentities`
5. `testVerifyBirthrightRoleAssignment`
6. `testVerifyAccounts`
7. `testModifyIdentities`
8. `testVerifyModifiedIdentities`
9. `testDeleteAccounts`
10. `testDeleteIdentities`

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
