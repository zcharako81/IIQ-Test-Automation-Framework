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
│   ├── IdentityDataFactory.java      # Factory API (delegates to provider)
│   ├── IdentityDataProvider.java     # Unified data access layer
│   └── IdentityDataSet.java          # JSON data model classes
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
- **Multi-identity mode**: Define identities via the `identities` key. Each identity gets its own set of create, expected, role, and account properties.
- **Optional create phase**: If omitted, the framework looks up the identity by `userName` using a SCIM filter query (must already exist in IIQ).
- **{suffix} placeholder**: Controlled by `test.suffix` in `config.properties`: `random` auto-generates a timestamp, a fixed value reuses a prior run's suffix, omitted uses values as-is.
- **SailPoint extension attributes**: Add any IIQ or custom attribute inside the `"sailpoint": { ... }` block. Multi-value attributes use JSON arrays (e.g. `"capabilities": ["A", "B"]`). No Java code changes needed.
- **Multiple roles**: Defined as a JSON array in the `"roles"` key within `expectedCreate` / `expectedModify` sections.
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

### Account validation flow

1. `GET /Users/{id}?attributes=...accounts` — returns account references
2. For each reference, `GET $ref` — fetches the Account resource including `application.displayName` and schema-specific attributes
3. Match by `application.displayName` against the expected application name
4. Validate expected attributes against the schema-specific nested map

### Modify lifecycle

The framework modifies identities via **SCIM PATCH** and re-verifies:

```
modify → verifyModify → deleteAccounts
```

The `modify` section contains only the changed attributes (PATCH semantics), while `expectedModify` must contain the full expected state after modification (PUT semantics).

```json
"modify": {
  "1": {
    "displayName": "John Doe PATCHED",
    "sailpoint": { "title": "Senior Software Engineer" }
  }
}
```

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
          "capabilities": ["Auditor", "Role Administrator"]
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
          "location": "New York"
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

### Qualified phases

| Phase | Qualifier | Purpose |
|---|---|---|
| `task:<taskName>` | IIQ task name (e.g. `RefreshIdentitySingle`) | Run any IIQ task by name. The current identity is passed as a filter. |
| `modify:<N>`, `verifyModify:<N>` | Numeric index | Multi-round modify — maps to `expectedModify.<N>.*` |
| `verifyAccounts:<N>` | Numeric index | Account re-verification per round — maps to `accounts.N` / `account.N.<type>.*` |

---

## 📊 Enhanced Phase Detail Reporting (v1.2.0+)

Starting with version 1.2.0, each verify phase emits a detail line in the TestNG `Reporter.log` output, indicating exactly what was checked:

```
=== Starting identity lifecycle (suffix: 1747234800000) ===
...
=== Identity: user1 (6 phases) ===
  Phase: task:RefreshIdentitySingle -> 4231ms
  [verifyIdentity] Attributes checked: 8          ← core + sailpoint attrs
  Phase: verifyCreate -> 312ms
  [verifyRoles] Expected: [ALL_ACTIVE_USERS] matched 1/1
  Phase: verifyRoles -> 215ms
  [verifyAccounts] App: LDAP-Test (4 attrs)       ← per-application attribute count
  Phase: verifyAccounts -> 487ms
  Phase: modify -> 134ms
  [verifyIdentity] Attributes checked: 9          ← modify round (includes extra attrs)
  Phase: verifyModify -> 298ms
  Phase: deleteAccounts -> 321ms
  Phase: delete -> 98ms
=== Identity: user1 complete ===
=== All phases completed in 18730ms ===
```

### Detail line format by phase

| Phase | Detail line | Example |
|---|---|---|
| `verifyCreate` | `[verifyIdentity] Attributes checked: <N>` | `[verifyIdentity] Attributes checked: 8` |
| `verifyModify` | `[verifyIdentity] Attributes checked: <N>` | `[verifyIdentity] Attributes checked: 9` |
| `verifyRoles` | `[verifyRoles] Expected: [<roles>] matched <M>/<N>` | `[verifyRoles] Expected: [ALL_ACTIVE_USERS] matched 1/1` |
| `verifyAccounts` | `[verifyAccounts] App: <app> (<N> attrs)` | `[verifyAccounts] App: LDAP-Test (4 attrs)` |
| `verifyAccounts` (not exists) | `[verifyAccounts] App: <app> (should not exist)` | `[verifyAccounts] App: LDAP-Test (should not exist)` |

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
