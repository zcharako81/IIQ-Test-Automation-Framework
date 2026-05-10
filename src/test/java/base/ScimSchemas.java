package base;

/**
 * SCIM schema URN constants for SailPoint IdentityIQ.
 *
 * These are API-contract constants defined by the SailPoint SCIM implementation.
 * They are not user-configurable — this class is the single source of truth
 * so that every reference in services, models, and tests stays in sync.
 */
public final class ScimSchemas {

    private ScimSchemas() {}

    // ── Schema URNs ──────────────────────────────────────────────────────

    /** Core SCIM 2.0 User schema. */
    public static final String SCHEMA_CORE_USER =
            "urn:ietf:params:scim:schemas:core:2.0:User";

    /** SCIM 2.0 Enterprise User extension. */
    public static final String SCHEMA_ENTERPRISE_USER =
            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    /** SailPoint extension schema for identity attributes (objectConfig). */
    public static final String SCHEMA_SAILPOINT_USER =
            "urn:ietf:params:scim:schemas:sailpoint:1.0:User";

    /** SailPoint extension schema for launched workflows. */
    public static final String SCHEMA_SAILPOINT_WORKFLOW =
            "urn:ietf:params:scim:schemas:sailpoint:1.0:LaunchedWorkflow";

    /** Prefix for per-application account schema URNs. */
    public static final String SCHEMA_SAILPOINT_APP_ACCOUNT_PREFIX =
            "urn:ietf:params:scim:schemas:sailpoint:1.0:Application:Schema:";

    // ── Query parameter snippets (used by IdentityService) ───────────────

    /** Query param to fetch roles: ?attributes=...User:roles */
    public static final String QUERY_ROLES =
            "attributes=" + SCHEMA_SAILPOINT_USER + ":roles";

    /** Query param to fetch accounts: ?attributes=...User:accounts */
    public static final String QUERY_ACCOUNTS =
            "attributes=" + SCHEMA_SAILPOINT_USER + ":accounts";

    // ── REST-Assured JSONPath prefixes (used in assertions) ──────────────

    /** JSONPath prefix for the Enterprise User extension (includes trailing dot). */
    public static final String JSONPATH_ENTERPRISE =
            "'" + SCHEMA_ENTERPRISE_USER + "'.";

    /** JSONPath prefix for the SailPoint User extension (includes trailing dot). */
    public static final String JSONPATH_SAILPOINT =
            "'" + SCHEMA_SAILPOINT_USER + "'.";

    // ── REST endpoint paths ─────────────────────────────────────────────

    /** SCIM base path (always /scim/v2). */
    public static final String SCIM_BASE_PATH = "/scim/v2";

    /** SCIM Users resource endpoint. */
    public static final String USERS_ENDPOINT = "/Users";

    /** SCIM LaunchedWorkflows resource endpoint. */
    public static final String WORKFLOWS_ENDPOINT = "/LaunchedWorkflows";

    /** Full path to the Users resource: /scim/v2/Users */
    public static final String USERS_FULL_PATH = SCIM_BASE_PATH + USERS_ENDPOINT;
}
