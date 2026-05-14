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
* ✅ Dynamic attributes — any sailpoint.* property (incl. custom attributes)

Disclaimer: This project is an independent test automation framework and is not affiliated with or endorsed by SailPoint Technologies. It contains no SailPoint code, libraries, JARs or binaries. 

---

## 🧱 Tech Stack

| Component        | Technology |
|-----------------|-----------|
| Language        | Java 15+ |
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
├── services/            # API service layer (Identity, Workflow) with retry
├── factory/             # Test data builders + data providers
├── utils/               # Helper utilities (waits, validation, retry, response validation)
├── reporting/           # Custom TestNG IReporter (emailable-report.html)
├── tests/
│   ├── base/            # Base test classes
│   └── identity/        # Identity lifecycle tests
│
src/test/resources
├── config.properties    # Global test config (URL, auth, timeouts, data source, retry)
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
- **Test class**: `src/test/java/tests/identity/IdentityTest.java` (suite defined in `testng.xml`).
- **Set the manager UUID for your environment**: The `managerValue` in `identity.json` is a UUID specific to the IIQ instance. After setting up your IIQ server, run `GET /scim/v2/Users?filter=userName eq "The Administrator"`, copy the `id` from the response, and replace every occurrence of the old UUID in `identity.json` (and `identity.properties` if using properties mode).

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

# --- HTTP timeouts (milliseconds, uncomment to customize) ---
# connect.timeout.ms=10000
# read.timeout.ms=30000
# socket.timeout.ms=30000

# --- Retry on transient failures (uncomment to customize) ---
# retry.max.attempts=3
# retry.initial.delay.ms=1000
# retry.backoff.multiplier=2.0
```

### ⏱️ Retry & Timeout Configuration

In addition to wait timeouts for polling operations, the framework supports independent HTTP-level timeouts and retry for transient API failures:

| Config Key | Default | Purpose |
|---|---|---|
| `connect.timeout.ms` | 10000 | TCP connection timeout |
| `read.timeout.ms` | 30000 | Data read timeout |
| `socket.timeout.ms` | 30000 | Socket timeout |
| `retry.max.attempts` | 3 | Max retries for idempotent GET operations |
| `retry.initial.delay.ms` | 1000 | Initial delay before first retry |
| `retry.backoff.multiplier` | 2.0 | Exponential backoff multiplier |

Retry is applied automatically for all idempotent GET operations in `IdentityService` (`getUser`, `getUserWithRoles`, `getUserAccounts`, `getAccountByRef`) and `WorkflowService` (`getWorkflow`). Non-idempotent operations (POST, PUT, PATCH, DELETE) are never retried.

---

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
| `userName`, `firstname`, `lastname`, `displayName`, `userType`, `email`, `active`, `managerValue` | `urn:ietf:params:scim:schemas:core:2.0:User` + `enterprise` extension | Compiled POJO fields. `managerValue` is an **IIQ-instance-specific UUID** — update all occurrences when targeting a different IIQ environment (see troubleshooting below). |
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
mvn test -DsuiteXmlFile=testng.xml
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
This makes it easy to verify at a glance which attributes were tested, which roles matched, and which applications were validated — without scrolling through raw JSON responses.

---

## 📦 Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `org.testng:testng` | 7.11.0 | Test framework |
| `io.rest-assured:rest-assured` | 6.0.0 | REST API testing |
| `org.hamcrest:hamcrest` | 3.0 | Matchers (bundled with RestAssured) |
| `org.apache.commons:commons-configuration2` | 2.9.0 | Configuration loading |
| `com.fasterxml.jackson.core:jackson-databind` | 2.17.0 | JSON parsing for identity.json |

---

## 🔧 Troubleshooting

### Test fails with "Connection refused" or timeout
- Verify IIQ server is running and reachable at `base.url`.
- Check `connect.timeout.ms` / `read.timeout.ms` in config.properties; increase if network is slow.
- Ensure no firewall blocking port 8080.

### Workflow tasks fail with "completionStatus: Error"
- Verify `My-WF-TaskLauncher` is imported into IIQ (see Prerequisite).
- Check the task name in the phase list — must exactly match the IIQ task name (e.g. `task:RefreshIdentitySingle`).
- Verify `workflow.name` in config.properties matches the workflow XML name.
- Ensure the identity exists in IIQ before running task phases.

### Identity creation fails with 409 Conflict
- The username may already exist in IIQ. Use a fresh `test.suffix` (or `random`) to avoid name collisions.
- Delete the identity manually in IIQ or choose a new suffix.

### Role/account verification fails
- After identity creation, run `task:RefreshIdentitySingle` (or equivalent) to trigger role/account aggregation.
- Roles and accounts are verified via SCIM query params — check that the expected roles match the role `display` values in IIQ.
- For accounts, verify the `application` name in `identity.json` matches the application `displayName` in IIQ.

### managerValue assertions fail after switching IIQ environments
- The `managerValue` in `identity.json` (and `identity.properties`) is a hardcoded UUID specific to the IIQ instance the tests were written against. When switching to a different IIQ server, update **every occurrence** of the UUID with the target instance's manager identity UUID.
- To find the correct UUID: `GET /scim/v2/Users?filter=userName eq "The Administrator"` and copy the `id` field from the response. Replace all `managerValue` entries in `identity.json` and `identity.properties` with this value.
- If the manager's `displayName` also differs, update `managerDisplayName` entries accordingly.

### HTML report is empty or shows "0 identities"
- The report parses `Reporter.log()` output. Make sure `logging.enabled=true` in config.properties.
- Check that `test-output/emailable-report.html` was generated after the run.

### Maven build fails with "java.lang.System.out.println" or compilation errors
- Ensure you have JDK 15+ installed and `JAVA_HOME` points to it.
- Run `mvn clean compile` first to clear stale class files.

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
