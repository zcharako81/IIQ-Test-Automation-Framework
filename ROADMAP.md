# ROADMAP — IIQ Test Automation Framework

## Vision

A comprehensive, SCIM-first test automation framework for SailPoint IdentityIQ that covers the full IAM lifecycle — identity management, governance and compliance, provisioning and connectors — all driven from configuration files with zero Java code changes.

```
┌──────────────────────────────────────────────────────────────┐
│                    IIQ Test Automation Framework              │
├──────────────────────────────────────────────────────────────┤
│  Identity          Governance         Provisioning           │
│  Lifecycle          & Compliance        & Connectors         │
│                                     │                        │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐        │
│  │ Create/      │   │ Certifica-  │   │ Account     │        │
│  │ Modify/      │   │ tions       │   │ CRUD        │        │
│  │ Delete       │   │             │   │             │        │
│  ├─────────────┤   ├─────────────┤   ├─────────────┤        │
│  │ Roles       │   │ Access      │   │ Entitlement │        │
│  │ (birthright)│   │ Requests    │   │ provisioning│        │
│  ├─────────────┤   ├─────────────┤   ├─────────────┤        │
│  │ Accounts    │   │ SoD/Policy  │   │ Correlation │        │
│  │ (verify)    │   │ Violations  │   │ Testing     │        │
│  ├─────────────┤   ├─────────────┤   └─────────────┘        │
│  │ Tasks/      │   │ Reports     │                          │
│  │ Workflows   │   │ Validation  │    Operations             │
│  └─────────────┘   └─────────────┘                          │
│                                     │                        │
│                         ┌─────────────────────┐              │
│                         │  Config Validation   │              │
│                         │  CI/CD Integration   │              │
│                         │  Parallel Execution  │              │
│                         │  HTML Reporting      │              │
│                         │  Data-driven tests   │              │
│                         └─────────────────────┘              │
├──────────────────────────────────────────────────────────────┤
│                    SCIM API + REST API Layer                  │
└──────────────────────────────────────────────────────────────┘
```

---

## Current State — v1 (Identity Lifecycle)

The framework currently covers identity lifecycle testing via SCIM APIs:

- Identity CRUD (create, read, update, delete)
- Task execution via the unified `task:<taskName>` phase
- Birthright role assignment verification
- Account provisioning verification per application
- Multi-round modify with different attribute values
- Multi-identity lifecycle in a single run
- Dynamic SailPoint extension attributes (zero Java changes)
- Configurable suffix for uniqueness across test runs
- 100% config-driven — no code changes needed for test scenarios
- **JSON data file support** — `identity.json` as a structured alternative to `identity.properties`, selected via `identity.data.source=json` in `config.properties` (no classpath auto-detection)
- **Consistent phase↔section naming** — data sections `expectedCreate`/`expectedModify` align with phase names `verifyCreate`/`verifyModify`; no more confusion between `verifyCreate` → `expected` or `verifyModify` → `expectedAfterModify`
- **Roles merged into verifyCreate/verifyModify** — `verifyRoles` phase removed; role verification with polling is now part of `doVerifyIdentity`; roles are read from the section's `.roles` field (JSON) or from `expectedCreate.roles` (properties)
- **Accounts merged into expectedCreate/expectedModify** — `verifyAccounts` phase removed; accounts live inside each `IdentitySection` (`expectedCreate.accounts`, `expectedModify.<qual>.accounts`); account verification is now part of `doVerifyIdentity` alongside attribute and role checks
- **Unified `expectedModify` map** — single `Map<String, IdentitySection>` field replaces the previous two-field split (`expectedModify` + `expectedModifyQualified`); unqualified modify uses key `""`, multi-round uses `"1"`, `"2"`, etc.
- **SCIM PATCH partial modify** — modify only single attributes using sparse change data in `modify` section, applied via RFC 7644 PATCH (JSON source only; properties source falls back to PUT)

---

## Short-term — Quick Wins (SCIM-based)

These features reuse the existing SCIM patterns and architecture — new service classes, endpoints in `ScimSchemas.java`, and model objects following the same conventions as `Identity` and `LaunchedWorkflow`.

| Feature | IIQ Endpoint | Value |
|---|---|---|
| **Account CRUD** | `/scim/v2/Accounts` | Full provisioning lifecycle — create, verify, disable, delete accounts directly |
| **Role & Entitlement queries** | `/scim/v2/Roles`, `/scim/v2/Entitlements` | Verify role and entitlement catalog configuration programmatically |
| **Policy violation simulation** | `POST /scim/v2/CheckedPolicyViolations` | Pre-provisioning "what-if" check against separation-of-duties policies |
| **Task result verification** | `/scim/v2/TaskResults` | Poll actual task execution results directly (more reliable than workflow proxy) |
| **Alert testing** | `/scim/v2/Alerts` | Verify IIQ alerting rules fire correctly |

---

## Medium-term — Governance & Compliance (REST API)

These features require IIQ's native REST API layer (not SCIM). A new service layer and authentication path would be needed.

| Feature | API Type | Value |
|---|---|---|
| **Certification campaigns** | IIQ REST | Automate access review testing — create campaigns, make decisions, verify outcomes |
| **Access requests** | IIQ REST | Test submit, approve, and reject flows for roles and entitlements |
| **SoD policy CRUD** | IIQ REST | Create and verify separation-of-duties policies |
| **Report validation** | IIQ REST | Trigger reports and validate output programmatically |

---

## Long-term — Infrastructure & Operations

Framework-level enhancements that benefit all feature areas.

| Feature | Value |
|---|---|
| **Parallel execution** | Run multiple identity lifecycles concurrently for soak and performance testing |
| **CI/CD integration** | Jenkins pipeline templates, GitHub Actions workflows for automated regression runs |
| **OAuth2 authentication** | Support OAuth 2.0 client credentials alongside basic auth |
| **Data-driven test sources** | ~~JSON~~ (done), YAML or CSV test data files as an alternative to properties |
| **HTML reporting** | Structured test execution reports with pass/fail summaries and trends |
| **Configuration validation** | Automated validation of IIQ setup (build maps, correlation rules, transforms) |

---

## Contribution & Feedback

This roadmap is a living document. Priorities may shift based on real-world usage and community feedback. Open an issue or pull request to discuss any of these features.
