# SailPoint IdentityIQ Test Automation Framework

A modular and extensible test automation framework for **SailPoint IdentityIQ (IIQ)** using **SCIM APIs**, **TestNG**, and **REST-Assured**.
It can be executed standalone or integrated to a DevOps pipeline. 

This framework supports end-to-end IAM testing including:

* ✅ Create identities / Verify identities / Delete identities
* ✅ Multi-identity lifecycle — test multiple identities in one run
* ✅ Launch Workflows (via SCIM)
* ✅ Launch Tasks (via Workflow)
* ✅ Verify Identity attributes
* ✅ Verify Birthright Role assignments
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
- **managerValue**: Must be replaced with a valid IIQ identity ID (the `id` field of an existing user, e.g. `spadmin`). Find the correct ID by querying `GET /scim/v2/Users?filter=userName eq "spadmin"` on your IIQ server and copying the `id` value. The same applies to `managerDisplayName` (set to the userName of the manager).
- **{suffix} placeholder**: Appended to `userName`, `email`, and account attributes like `uid` and `cn` to ensure uniqueness per run (resolved from `System.currentTimeMillis()`).
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
# SailPoint extension (IIQ-native SCIM equivalents)
identity.user1.input.title=Software Engineer
identity.user1.input.department=Engineering
identity.user1.input.location=New York

identity.user1.expected.userName=john.doe.{suffix}
identity.user1.expected.firstname=John
identity.user1.expected.lastname=Doe
identity.user1.expected.email={suffix}.john.doe@acme.com
identity.user1.expected.userType=employee
identity.user1.expected.title=Software Engineer
identity.user1.expected.department=Engineering
identity.user1.expected.location=New York
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

### Account validation flow

1. `GET /Users/{id}?attributes=...accounts` — returns account references (`displayName`, `value`, `$ref`)
2. For each reference, `GET $ref` — fetches the full Account resource which includes `application` (a reference object with `displayName`) and schema-specific attributes (e.g., `urn:ietf:params:scim:schemas:sailpoint:1.0:Application:Schema:LDAP-Test:account`)
3. Match by `application.displayName` against the expected application name
4. Validate expected attributes against the schema-specific nested map

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
7. `testDeleteIdentities`

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
