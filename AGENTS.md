# AGENTS.md — IIQ Test Automation Framework

## Build & run

```bash
mvn clean test                     # runs testng.xml suite
mvn test -DsuiteXmlFile=testng.xml # same, explicit
```

- Java source level 8, but `maven-compiler-plugin` uses `--release 15`. Test with JDK 15+.
- `war` packaging is legacy (tests only, no deployable). Do not change it without understanding the Eclipse/M2e setup.

## Test structure

- **Single test class**: `tests.identity.IdentityTest extends BaseTest`. Suite defined in `testng.xml`.
- **Tests are strictly sequential** via TestNG `dependsOnMethods`:
  1. `testCreateIdentities` → `testVerifyIdentities` → `testLaunchWorkflowRefreshIdentities` → `testBirthrightRoleAssignment` → `testLaunchLdapAggregationWorkflows` → `testVerifyAccounts` → `testDeleteIdentities`
- The suffix (`System.currentTimeMillis()`) appended to `userName` and email ensures uniqueness per run. **Do not reuse suffix across test runs** for delete assertions.

## Configuration

3 properties files loaded at startup into a single `Properties` object (loaded in order; later wins on key collision):

| File | Purpose |
|---|---|
| `src/test/resources/config.properties` | IIQ URL, auth, SCIM paths, workflow/task names, timeouts |
| `src/test/resources/identity.properties` | Identity input + expected attributes, roles |
| `src/test/resources/account.properties` | Per-application account expectations (prefix-based) |

**Critical keys** that must be set before first run:
- `base.url` — IIQ server (default points to `172.16.198.129:8080`)
- `username` / `password` — default `spadmin` / `admin`
- `task.name1` / `task.name2` — IIQ task names for refresh and aggregation
- `workflow.name` — must match the workflow XML name (`My-WF-TaskLauncher`)
- `identities` — comma-separated list of identity keys (required); each key needs `identity.<key>.input.*`, `identity.<key>.expected.*`, and `identity.<key>.expected.roles`
- `accounts` — comma-separated list of account types; each type needs `account.<type>.application`, `.expected.exists`, and `.expected.attributes.<attr>` keys

## Prerequisite — IIQ server

1. Import `src/test/iiq/config/My-wf-tasklauncher.xml` into IIQ **before** running tests. The workflow launches an arbitrary named task, passing `identityName` as a task filter.
2. The IIQ server must be reachable at `base.url`.
3. Basic auth is the only supported `auth.type`.

## API layer

- All SCIM calls go through `base.ApiClient` → RestAssured with `log().all()` (verbose by default).
- SCIM endpoints (from `config.properties`): `/scim/v2/Users`, `/scim/v2/LaunchedWorkflows`.
- Workflow launch wraps the payload in `urn:ietf:params:scim:schemas:sailpoint:1.0:LaunchedWorkflow` schema.
- Identity roles/accounts retrieved via SCIM query params: `?attributes=urn:ietf:params:scim:schemas:sailpoint:1.0:User:roles` (and similar for accounts).

## Quirks & gotchas

- **Assertion style**: Tests use raw `Assert.assertEquals` on `jsonPath()` strings. If the IIQ SCIM response shape changes, tests break silently.
- **No teardown context**: `testDeleteIdentities` runs last; if it fails, identities stay in IIQ. There is no `@AfterClass` cleanup.
- **`waitForWorkflowCompletion`** in `TestUtils` polls `completionStatus` accepting: `Success`, `Error`, `TempError`, `Terminated`, `Warning`. It does **not** assert success — the caller does that separately.
- **Workflow input**: `LaunchedWorkflowDataFactory` hardcodes input keys `identityName` and `taskName`. These must match the workflow XML variable names.
- **`ResponseValidator`** exists but is **not used** by any test class. Callers use manual status-code assertions instead.
- **pom.xml duplication**: `maven-surefire-plugin` declared in both `<pluginManagement>` and `<plugins>`. If you change the version, update both or the outer one wins.
- `mvn clean` deletes `test-output/` (listed in `.gitignore`).

## Multi-identity mode

- **`IdentityTest`** iterates over all identity keys inside each `dependsOnMethods`-chained test method.
- Each identity key has its own `identity.<key>.input.*` (creation), `identity.<key>.expected.*` (verification), and `identity.<key>.expected.roles` (role assertion) property blocks.
- Account validation (`testVerifyAccounts`) is shared across all identities (application-based, not identity-specific).
- If any identity fails at any lifecycle stage, the assertion failure stops the test for all identities in that method.
- The same `System.currentTimeMillis()` suffix is appended to all identities created in a single run, so they share a timestamp.

## What not to do

- Do not add `src/main/java` resources — the framework is entirely in `src/test/java`.
- Do not add JUnit tests — the framework uses TestNG exclusively (JUnit 4 jar is unused; do not introduce dependency on it).
- Do not refactor `IdentityDataFactory` to use `ConfigManager` for identity properties — it loads `identity.properties` independently via its own `Properties` instance. Changing this risks breaking the load order.
