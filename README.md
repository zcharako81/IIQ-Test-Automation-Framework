# SailPoint IdentityIQ Test Automation Framework

A modular and extensible test automation framework for **SailPoint IdentityIQ (IIQ)** using **SCIM APIs**, **TestNG**, and **REST-Assured**.
It can be executed standalone or integrated into a DevOps pipeline. 

This framework supports end-to-end IAM testing including:

* ✅ 100% config-driven — no code changes needed for test scenarios
* ✅ Identity CRUD — create, read, update, delete
* ✅ Task execution — any IIQ task via task:<taskName>
* ✅ Birthright roles — verify role assignments
* ✅ Account provisioning — verify accounts per application
* ✅ Multi-identity — simultaneous lifecycle for many identities
* ✅ Multi-round modify — multiple modification rounds
* ✅ Dynamic attributes — any sailpoint.* property, zero Java changes

Disclaimer: This project is an independent test automation framework and is not affiliated with or endorsed by SailPoint Technologies. It contains no SailPoint code, libraries, JARs or binaries. 

---

## 🧱 Tech Stack

| Component        | Technology |
|-----------------|-----------|
| Language        | Java 11+ |
| Build Tool      | Maven |
| Test Framework  | TestNG |
| API Testing     | REST-Assured |
| Configuration   | JSON (properties legacy) |
| Tested Against | SailPoint IdentityIQ 8.5 |
---

## 📁 Project Structure
```
src/test/java
│
├── base/                # Core framework classes (API, config, auth, SCIM schemas)
├── model/               # SCIM models (Identity, Workflow, etc.)
├── services/            # API service layer (Identity, Workflow)
├── factory/             # Test data builders + data providers
├── utils/               # Helper utilities (waits, validation)
├── tests/
│   ├── base/            # Base test classes
│   └── identity/        # Identity lifecycle tests
│
src/test/resources
├── config.properties    # Global test config (URL, auth, timeouts, data source)
├── identity.json        # Identity test data (JSON format, supports SCIM PATCH)
│
src/test/iiq
│
└── config/My-WF-TaskLauncher   # Workflow XML (must be imported into IIQ)
```
---

## 👉 Instructions

- **Prerequisite**: Workflow `My-WF-TaskLauncher` must be imported into IIQ before test execution.
- **All tests are defined in `identity.json`**: The entire test scenario — identities, lifecycle phases, expected attributes, roles, accounts, and account attributes — is configured in a single JSON data file. No Java code changes are needed to define or modify test cases.
- **Define your test scenario**: Start by listing your test identities under the `identities` key. For each identity, provide create attributes, expected values, expected roles, and account validations. Everything is driven by conventions documented below.
- **Phase list**: Define the identity lifecycle via the `tests` array. All tasks are launched via the unified `task:<taskName>` phase (e.g. `task:RefreshIdentitySingle`). The identity name is passed automatically as a workflow filter.
- **Test class**: `src/test/java/tests/identity/IdentityTest.java` (suite defined in `Testng.xml`).

---

## ⚙️ Configuration

Identity test data is defined in **`identity.json`**. Selection is controlled by `identity.data.source` in `config.properties`:

| File | Purpose |
|---|---|
| `config.properties` | IIQ URL, auth, timeouts, logging, suffix, data source |
| `identity.json` | Identity test data — structured JSON, supports SCIM PATCH |

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

# --- Identity data source ---
#   json   → load test data from identity.json (recommended)
# Default is 'properties' (backward compatible).
identity.data.source=json
```

### Test phases

Define the identity lifecycle by listing phases in the `tests` array. Each phase executes sequentially; duplicates are allowed for multi-round scenarios.

| Phase | Description |
|---|---|
| `create` | Creates the identity via `POST /scim/v2/Users` using the attributes from the `create` section |
| `task:<taskName>` | Launches the `My-WF-TaskLauncher` workflow for the specified IIQ task (e.g. `task:RefreshIdentitySingle`). The identity name is passed automatically as a task filter. Waits for completion and asserts `Success`. |
| `verifyCreate` | Fetches the identity via `GET /scim/v2/Users/{id}` and asserts all core, enterprise, and SailPoint extension attributes match `expectedCreate`; also verifies `roles` and `accounts` from the same section (with polling for roles) |
| `modify` | Modifies the identity via **SCIM PATCH** using the attributes from the `modify` section (`modify:1`, `modify:2`, etc. for multi-round) |
| `verifyModify` | Fetches the identity and asserts attributes match the corresponding `expectedModify` section (`verifyModify:1` → `expectedModify.1`); also verifies accounts from the same section |
| `deleteAccounts` | Fetches all account references and deletes each via its `$ref` URL |
| `delete` | Deletes the identity via `DELETE /scim/v2/Users/{id}` |

---

### JSON Data Source — `identity.json`

When `identity.data.source=json` is set in `config.properties`, test data is loaded from `identity.json`. The JSON format is structured, supports SCIM PATCH for partial modify, and nests all attributes per identity.

#### JSON structure overview

```json
{
  "identities": {
    "user1": {
      "tests": ["create", "task:RefreshIdentitySingle", "task:LDAPAccountAggregation", "verifyCreate", "modify:1", "task:RefreshIdentitySingle", "task:LDAPAccountAggregation", "verifyModify:1"],
      "create": {
        "userName": "john.doe",
        "firstname": "John",
        "lastname": "Doe",
        "displayName": "John Doe",
        "email": "john.doe@acme.com",
        "userType": "employee",
        "active": true,
        "sailpoint": {
          "title": "Software Engineer",
          "department": "Engineering",
          "location": "New York",
          "capabilities": ["Auditor", "RoleAdministrator"]
        }
      },
      "expectedCreate": {
        "userName": "john.doe.{suffix}",
        "firstname": "John",
        "lastname": "Doe",
        "displayName": "John Doe",
        "email": "{suffix}.john.doe@acme.com",
        "userType": "employee",
        "active": true,
        "sailpoint": {
          "title": "Software Engineer",
          "department": "Engineering",
          "location": "New York",
          "capabilities": ["Auditor", "RoleAdministrator"]
        },
        "roles": ["ALL_ACTIVE_USERS", "LDAP_ALL_USERS"],
        "accounts": {
          "ldap": {
            "application": "LDAP-Test",
            "expected": {
              "exists": true,
              "attributes": {
                "uid": "john.doe.{suffix}",
                "cn": "john.doe.{suffix}",
                "givenName": "John",
                "sn": "Doe"
              }
            }
          }
        }
      },
      "modify": {
        "1": {
          "displayName": "John Doe PATCHED",
          "sailpoint": {
            "title": "Senior Software Engineer",
            "Identity_End_Date": "2029-12-31"
          }
        }
      },
      "expectedModify": {
        "1": {
          "userName": "john.doe.{suffix}",
          "firstname": "John",
          "lastname": "Doe",
          "displayName": "John Doe PATCHED",
          "email": "{suffix}.john.doe@acme.com",
          "userType": "employee",
          "active": true,
          "sailpoint": {
            "title": "Senior Software Engineer",
            "department": "Engineering",
            "location": "New York",
            "Identity_End_Date": "2029-12-31"
          },
          "accounts": {
            "ad": {
              "application": "ActiveDirectory-Test",
              "expected": {
                "exists": false,
                "attributes": {
                  "displayName": "John Doe",
                  "mail": "{suffix}.john.doe@acme.com"
                }
              }
            }
          }
        }
      }
    }
  }
}
```

#### SCIM schema mapping

Attributes inside `create` / `expectedCreate` / `expectedModify` sections map to different SCIM JSON namespaces:

| JSON key | SCIM Schema | Java handling |
|---|---|---|
| `userName`, `firstname`, `lastname`, `displayName`, `userType`, `email`, `active`, `managerValue` | `urn:ietf:params:scim:schemas:core:2.0:User` + `enterprise` extension | Compiled POJO fields |
| `sailpoint.*` (e.g. `sailpoint.title`, `sailpoint.department`) | `urn:ietf:params:scim:schemas:sailpoint:1.0:User` | `Map<String, Object>` — adding attributes requires **zero Java changes** |

Any `sailpoint.*` block is optional; omit it and the framework skips the SailPoint extension entirely.

> **Note on unqualified modify**: In JSON, unqualified modify uses key `""` (empty string), while qualified rounds use `"1"`, `"2"`, etc. The `modify` section contains only the changed attributes (PATCH semantics), while `expectedModify` must contain the full expected state after modification (PUT semantics).

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

The single `@Test` method `testLifecycle()` runs a per-identity ordered phase list from the `tests` array. Default lifecycle:

```
create → verifyCreate  → modify → verifyModify → deleteAccounts → delete
```

If `.tests` is absent, the full default lifecycle above runs. Phases can be repeated; duplicates allowed.

---

## 📊 HTML Report — `emailable-report.html` (v1.2.0+)

After every test run the framework generates a standalone HTML report at `test-output/emailable-report.html`. The report is structured as follows:

### Header & Summary

The top of the report shows the overall test status, timestamps, suffix, and summary cards:

```
┌──────────────────────────────────────────────────────────────────┐
│  ✅ Identity Lifecycle Report                                    │
│  PASSED  |  2 identities  |  Total: 18.7s  |  2026-05-14 12:34  │
└──────────────────────────────────────────────────────────────────┘
┌──────────┬──────────┬──────────┬──────────┐
│ Identities│  Passed  │  Failed  │ Duration │
│     2     │    2     │    0     │  18.7s   │
└──────────┴──────────┴──────────┴──────────┘
```

### Per-Identity Cards

Each identity is rendered as a card with a badge, phase count, and total duration:

```
┌──────────────────────────────────────────────────────────────────┐
│  👤 user1                                    ✅ Passed           │
│  6 phases  18.7s total                                         │
├──────────────────────────────────────────────────────────────────┤
│ Phase              Duration              Time        Status     │
│ ──────────────────────────────────────────────────────────────── │
│ ⚙️ task:Refresh    ████████████████    4.2s         ✅          │
│ ✅ verifyCreate    ██████              312ms        ✅          │
│   ▶ 📋 Attributes checked: 8                                   │
│   ▶ 🏆 Roles: [ALL_ACTIVE_USERS] matched 1/1                   │
│   ▶ 🔗 App: LDAP-Test (4 attrs)                                │
│ ✏️ modify          ███                 134ms        ✅          │
│ ✅ verifyModify    ██████              298ms        ✅          │
│   ▶ 📋 Attributes checked: 9                                   │
│ ➖ deleteAccounts  ██████              321ms        ✅          │
│ ➖ delete          ██                  98ms         ✅          │
└──────────────────────────────────────────────────────────────────┘
```

Each phase has:
- **Icon** — ⚙️ task, ✅ verify, ✏️ modify, ➕ create, ➖ delete
- **Duration bar** — proportional to the longest phase
- **Status** — ✅ phase passed or ❌ assertion failure detected
- **Expandable detail sections** — click to reveal attributes, roles, and account attributes

### Expandable Detail Sections

Click each detail summary to expand:

| Section | Icon | Content |
|---|---|---|
| `Attributes checked: N` | 📋 | Key → value grid of every verified attribute |
| `Roles: [...] matched M/N` | 🏆 | Bullet list of expected roles with ✅/❌ per role |
| `App: <name> (N attrs)` | 🔗 | Per-application attribute table (key → value) |
| `App: <name> (should not exist)` | 🔗 | Indicates the account was expected to be absent |

### Failure Reporting

When `SoftAssert` assertions fail, the report automatically captures the failure:

```
┌──────────────────────────────────────────────────────────────────┐
│  👤 user1                                    ❌ Failed           │
├──────────────────────────────────────────────────────────────────┤
│  ❌ Test Assertions Failed                                       │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ The following asserts failed:                             │   │
│  │   Mismatch: displayName on: user1 expected [John Doe      │   │
│  │   PATCHED] but found [Jane Doe]                           │   │
│  └──────────────────────────────────────────────────────────┘   │
├──────────────────────────────────────────────────────────────────┤
│ Phase              Duration              Time        Status     │
│ ✅ verifyCreate    ██████              312ms        ❌          │
│ ...                                                             │
└──────────────────────────────────────────────────────────────────┘
```

- **Red error box** appears between the identity header and the phase table
- Error message is shown with monospace formatting; long messages are truncated with a *Show full failure details* expansion link
- The **Status column** marks the relevant verify phase with ❌ by classifying the failed attribute (identity, role, or account)
- Only the identity whose key appears in the assertion error message is marked failed — other identities remain ✅ Passed

### Detail line format by phase

| Phase | Detail line | Example |
|---|---|---|
| `verifyCreate` / `verifyModify` | `[verifyIdentity] Attributes checked: <N>` | `[verifyIdentity] Attributes checked: 8` |
| *(nested inside verify phase)* | `[verifyRoles] Expected: [<roles>] matched <M>/<N>` | `[verifyRoles] Expected: [ALL_ACTIVE_USERS] matched 1/1` |
| *(nested inside verify phase)* | `[verifyAccounts] App: <app> (<N> attrs)` | `[verifyAccounts] App: LDAP-Test (4 attrs)` |
| *(not exists)* | `[verifyAccounts] App: <app> (should not exist)` | `[verifyAccounts] App: LDAP-Test (should not exist)` |

This makes it easy to verify at a glance which attributes were tested, which roles matched, and which applications were validated — without scrolling through raw JSON responses.

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
