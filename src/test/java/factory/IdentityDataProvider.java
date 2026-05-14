package factory;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import base.ConfigManager;
import base.ScimSchemas;
import factory.IdentityDataSet.AccountEntry;
import factory.IdentityDataSet.IdentityEntry;
import factory.IdentityDataSet.IdentitySection;
import model.Identity;
import utils.TestUtils;

/**
 * Unified provider for identity test data.
 * <p>
 * The data source is chosen via the {@code identity.data.source} property
 * in {@code config.properties}:
 * <ul>
 *   <li>{@code json} — loads from {@code identity.json} using Jackson (supports SCIM PATCH)</li>
 *   <li>{@code properties} — loads from {@code identity.properties} (backward compatible, SCIM PUT)</li>
 * </ul>
 * This is transparent to callers — all methods return the same structure
 * regardless of the underlying source.
 * <p>
 * Design principles:
 * <ul>
 *   <li>Config-driven source selection — explicit, no classpath auto-detection</li>
 *   <li>Same API for both sources — one code path, two backends</li>
 *   <li>All methods are static — no instance management needed</li>
 * </ul>
 */
public class IdentityDataProvider {

    private static IdentityDataSet dataSet;
    private static Properties fallbackProps;
    private static boolean useJson = false;

    static {
        String source = "properties";   // safe default for backward compat
        try {
            source = ConfigManager.getIdentityDataSource();
        } catch (Exception e) {
            // ConfigManager not yet available — use default
        }

        if ("json".equalsIgnoreCase(source.trim())) {
            useJson = true;
            try (InputStream is = IdentityDataProvider.class
                    .getClassLoader().getResourceAsStream("identity.json")) {
                if (is == null) {
                    throw new RuntimeException(
                            "identity.data.source=json but identity.json not found on classpath");
                }
                ObjectMapper mapper = new ObjectMapper();
                dataSet = mapper.readValue(is, IdentityDataSet.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load identity.json", e);
            }
        } else {
            useJson = false;
            fallbackProps = new Properties();
            try (InputStream is = IdentityDataProvider.class
                    .getClassLoader().getResourceAsStream("identity.properties")) {
                if (is == null) {
                    throw new RuntimeException(
                            "identity.data.source=properties but identity.properties not found on classpath");
                }
                fallbackProps.load(is);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load identity.properties", e);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Status
    // ─────────────────────────────────────────────────────────────────────

    /** Returns {@code true} when the provider is backed by identity.json. */
    public static boolean isJsonSource() {
        return useJson;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Identity keys
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the list of configured identity keys.
     * From JSON: the keys of the {@code identities} map.
     * From properties: the {@code identities} comma-separated property.
     */
    public static List<String> getIdentityKeys() {
        if (useJson) {
            if (dataSet == null || dataSet.getIdentities() == null) {
                return List.of();
            }
            return new ArrayList<>(dataSet.getIdentities().keySet());
        }
        String value = fallbackProps.getProperty("identities");
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Entry access
    // ─────────────────────────────────────────────────────────────────────

    /** Returns the IdentityEntry for the given key (JSON only). */
    private static IdentityEntry getEntry(String identityKey) {
        if (!useJson) return null;
        if (dataSet == null || dataSet.getIdentities() == null) {
            return null;
        }
        return dataSet.getIdentities().get(identityKey);
    }

    /** Shorthand to get a non-null entry (throws if missing). */
    private static IdentityEntry requireEntry(String identityKey) {
        IdentityEntry entry = getEntry(identityKey);
        if (entry == null) {
            throw new RuntimeException("Identity entry not found for key: " + identityKey);
        }
        return entry;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tests / lifecycle phases
    // ─────────────────────────────────────────────────────────────────────

    /** Default ordered lifecycle phases (backward compatible). */
    private static final List<String> DEFAULT_PHASES = List.of(
            "create", "verifyCreate",
            "modify", "verifyModify", "deleteAccounts", "delete"
    );

    /**
     * Returns the ordered list of test phases for a given identity key.
     * From JSON: the {@code tests} list in the entry, or DEFAULT_PHASES if absent.
     * From properties: the {@code identity.<key>.tests} property, or DEFAULT_PHASES.
     */
    public static List<String> getIdentityTests(String identityKey) {
        if (useJson) {
            IdentityEntry entry = getEntry(identityKey);
            if (entry == null || entry.getTests() == null || entry.getTests().isEmpty()) {
                return DEFAULT_PHASES;
            }
            return entry.getTests();
        }
        String value = fallbackProps.getProperty("identity." + identityKey + ".tests");
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_PHASES;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Expected roles
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the list of expected birthright roles for an identity.
     * From JSON: the {@code roles} list in the {@code expected} section.
     * From properties: the {@code identity.<key>.expected.roles} comma-separated property.
     */
    public static List<String> getIdentityExpectedRoles(String identityKey) {
        if (useJson) {
            IdentityEntry entry = getEntry(identityKey);
            if (entry == null || entry.getExpected() == null) {
                return List.of();
            }
            return entry.getExpected().getRoles() != null
                    ? entry.getExpected().getRoles()
                    : List.of();
        }
        String value = fallbackProps.getProperty("identity." + identityKey + ".expectedCreate.roles");
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Account types
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the account type list for an identity, optionally qualified.
     * @param identityKey the identity key
     * @param qualifier   empty string for base accounts, or "1", "2" for per-round accounts
     */
    /**
     * Returns account type list for properties source.
     * JSON source handles accounts directly via {@link IdentitySection#getAccounts()}.
     */
    public static List<String> getAccountTypes(String identityKey, String qualifier) {
        String propKey = (qualifier == null || qualifier.isEmpty())
                ? "identity." + identityKey + ".accounts"
                : "identity." + identityKey + ".accounts." + qualifier;
        String value = fallbackProps.getProperty(propKey);
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Account application
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns account application for properties source.
     * JSON source handles accounts directly via {@link IdentitySection#getAccounts()}.
     */
    public static String getAccountApplication(String identityKey, String type, String qualifier) {
        String propKey = (qualifier == null || qualifier.isEmpty())
                ? "identity." + identityKey + ".account." + type + ".application"
                : "identity." + identityKey + ".account." + qualifier + "." + type + ".application";
        return fallbackProps.getProperty(propKey);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Account exists flag
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns account exists flag for properties source.
     * JSON source handles accounts directly via {@link IdentitySection#getAccounts()}.
     */
    public static String getAccountExists(String identityKey, String type, String qualifier) {
        String propKey = (qualifier == null || qualifier.isEmpty())
                ? "identity." + identityKey + ".account." + type + ".expected.exists"
                : "identity." + identityKey + ".account." + qualifier + "." + type + ".expected.exists";
        String value = fallbackProps.getProperty(propKey);
        return value != null ? value : "false";
    }

    // ─────────────────────────────────────────────────────────────────────
    // Account expected attributes
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns account expected attributes for properties source.
     * JSON source handles accounts directly via {@link IdentitySection#getAccounts()}.
     */
    public static Map<String, String> getAccountExpectedAttributes(String identityKey, String type, String qualifier) {
        String prefix = (qualifier == null || qualifier.isEmpty())
                ? "identity." + identityKey + ".account." + type + ".expected.attributes."
                : "identity." + identityKey + ".account." + qualifier + "." + type + ".expected.attributes.";
        return getByPrefixFromProps(prefix);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Identity POJO builders  (replaces IdentityDataFactory)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Creates an Identity POJO from the {@code create} section for SCIM POST (create).
     * Suffix is appended/prepended — create values do NOT contain {@code {suffix}}.
     */
    public static Identity createIdentity(String suffix, String identityKey) {
        return buildIdentity(suffix, identityKey, "create", true);
    }

    /**
     * Creates an Identity POJO from the {@code expectedModify} section for SCIM PUT (modify).
     * This is the backward-compatible variant (unqualified, no qualifier).
     */
    public static Identity createIdentityForModify(String suffix, String identityKey) {
        return createIdentityForModify(suffix, identityKey, "");
    }

    /**
     * Creates an Identity POJO from the {@code expectedModify} section for SCIM PUT (modify).
     * @param suffix      the suffix for {suffix} resolution
     * @param identityKey the identity key
     * @param qualifier   empty for unqualified, "1", "2" etc. for qualified rounds
     */
    public static Identity createIdentityForModify(String suffix, String identityKey, String qualifier) {
        return buildIdentity(suffix, identityKey, "expectedModify", false, qualifier);
    }

    /**
     * Creates an Identity POJO from the {@code expectedCreate} section (post-creation expected state).
     * Used when the create phase is absent and an existing identity is looked up.
     */
    public static Identity createIdentityFromExpected(String suffix, String identityKey) {
        return buildIdentity(suffix, identityKey, "expectedCreate", false);
    }

    /**
     * Returns the expected userName for an identity from the {@code expectedCreate} section.
     * Replaces {@code {suffix}} with the given suffix value.
     */
    public static String getExpectedUserName(String suffix, String identityKey) {
        if (useJson) {
            IdentityEntry entry = requireEntry(identityKey);
            if (entry.getExpected() == null || entry.getExpected().getUserName() == null) {
                throw new RuntimeException("Missing expectedCreate.userName for identity: " + identityKey);
            }
            return TestUtils.resolveSuffix(entry.getExpected().getUserName(), suffix);
        }
        String raw = fallbackProps.getProperty("identity." + identityKey + ".expectedCreate.userName");
        if (raw == null) {
            throw new RuntimeException("Missing identity." + identityKey + ".expected.userName");
        }
        return TestUtils.resolveSuffix(raw, suffix);
    }

    // ─────────────────────────────────────────────────────────────────────
    // IdentitySection access for verification
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the full IdentitySection for a given identity, section, and optional qualifier.
     * <p>
     * Section names:
     * <ul>
     *   <li>{@code "expectedCreate"} — post-creation expected state</li>
     *   <li>{@code "create"} — creation input</li>
     *   <li>{@code "expectedModify"} — post-modify expected state (uses qualifier)</li>
     * </ul>
     *
     * @param identityKey the identity key
     * @param section     the section name
     * @param qualifier   empty for unqualified, or round number for qualified after-modify
     * @return the IdentitySection, or null if not found
     */
    public static IdentitySection getExpectedSection(String identityKey, String section, String qualifier) {
        if (!useJson) return null;  // Only supported for JSON source

        IdentityEntry entry = getEntry(identityKey);
        if (entry == null) return null;

        switch (section) {
            case "create":
                return entry.getInput();
            case "expectedCreate":
                return entry.getExpected();
            case "expectedModify":
                return entry.getModifySection(qualifier);
            default:
                return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // PATCH body builder (JSON source only — sparse modify data)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns SCIM PATCH data for the {@code modify:<qualifier>} phase.
     * <p>
     * Reads the sparse {@code modify} section from identity.json (only the
     * changed attributes) and builds a {@code Map<String, Object>} suitable
     * for direct use as a SCIM PATCH body in the standard RFC 7644 format:
     * <pre>
     * {
     *   "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
     *   "Operations": [{
     *     "op": "replace",
     *     "value": {
     *       "displayName": "John Doe PATCHED",
     *       "urn:...:sailpoint:1.0:User": { "title": "Senior Software Engineer" }
     *     }
     *   }]
     * }
     * </pre>
     * <p>
     * Only available when using the JSON data source ({@link #isJsonSource()}).
     * Falls back to null when using identity.properties.
     *
     * @param suffix       the suffix for {suffix} resolution
     * @param identityKey  the identity key
     * @param qualifier    the round qualifier ("1", "2") — empty is NOT supported for PATCH
     * @return the PATCH body map, or null if not available
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildPatchBody(String suffix, String identityKey, String qualifier) {
        if (!useJson) return null;

        IdentityEntry entry = getEntry(identityKey);
        if (entry == null) return null;

        Map<String, Map<String, Object>> modifyData = entry.getModify();
        if (modifyData == null || !modifyData.containsKey(qualifier)) {
            return null;
        }

        // Deep-copy the raw modify data for this qualifier (to avoid mutating the model)
        Map<String, Object> raw = deepCopy(modifyData.get(qualifier));
        if (raw == null || raw.isEmpty()) return null;

        // Resolve {suffix} in all string values recursively
        resolveSuffixInValue(raw, suffix);

        // Build the PATCH "value" part — map attribute names to SCIM paths
        Map<String, Object> patchValue = new LinkedHashMap<>();

        for (Map.Entry<String, Object> me : raw.entrySet()) {
            String key = me.getKey();
            Object val = me.getValue();

            switch (key) {
                // Core direct attributes
                case "displayName":
                case "userName":
                case "userType":
                case "locale":
                case "timezone":
                case "nickName":
                case "profileUrl":
                case "preferredLanguage":
                case "title":
                case "externalId":
                    patchValue.put(key, val);
                    break;

                // Name sub-attributes → map to name.givenName / name.familyName etc.
                case "firstname":
                    patchValue.put("name.givenName", val);
                    break;
                case "lastname":
                    patchValue.put("name.familyName", val);
                    break;
                case "middleName":
                    patchValue.put("name.middleName", val);
                    break;

                // Email → map to emails[0].value
                case "email":
                    Map<String, Object> emailObj = new LinkedHashMap<>();
                    emailObj.put("value", val);
                    emailObj.put("primary", true);
                    patchValue.put("emails", List.of(emailObj));
                    break;

                // Active flag
                case "active":
                    patchValue.put("active", val);
                    break;

                // SailPoint extension
                case "sailpoint":
                    if (val instanceof Map) {
                        Map<String, Object> spMap = (Map<String, Object>) val;
                        patchValue.put(ScimSchemas.SCHEMA_SAILPOINT_USER, spMap);
                    }
                    break;

                // Enterprise extension — manager
                case "managerValue":
                    // Build or merge into the enterprise extension map
                    Map<String, Object> entMap = (Map<String, Object>)
                            patchValue.computeIfAbsent(ScimSchemas.SCHEMA_ENTERPRISE_USER,
                                    k -> new LinkedHashMap<String, Object>());
                    Map<String, Object> mgrMap = (Map<String, Object>)
                            entMap.computeIfAbsent("manager",
                                    k -> new LinkedHashMap<String, Object>());
                    mgrMap.put("value", val);
                    break;

                case "managerDisplayName":
                    Map<String, Object> entMap2 = (Map<String, Object>)
                            patchValue.computeIfAbsent(ScimSchemas.SCHEMA_ENTERPRISE_USER,
                                    k -> new LinkedHashMap<String, Object>());
                    Map<String, Object> mgrMap2 = (Map<String, Object>)
                            entMap2.computeIfAbsent("manager",
                                    k -> new LinkedHashMap<String, Object>());
                    mgrMap2.put("displayName", val);
                    break;

                default:
                    // Unknown attribute — pass through as-is
                    patchValue.put(key, val);
                    break;
            }
        }

        // Wrap in RFC 7644 PatchOp
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("op", "replace");
        operation.put("value", patchValue);

        Map<String, Object> patchBody = new LinkedHashMap<>();
        patchBody.put("schemas", List.of("urn:ietf:params:scim:api:messages:2.0:PatchOp"));
        patchBody.put("Operations", List.of(operation));

        return patchBody;
    }

    /**
     * Deep-copies a Map<String, Object> (recursively). Mutating the result
     * does not affect the original IdentityDataSet model.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> original) {
        if (original == null) return null;
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : original.entrySet()) {
            Object val = e.getValue();
            if (val instanceof Map) {
                copy.put(e.getKey(), deepCopy((Map<String, Object>) val));
            } else if (val instanceof List) {
                copy.put(e.getKey(), new ArrayList<>((List<?>) val));
            } else {
                copy.put(e.getKey(), val);
            }
        }
        return copy;
    }

    /**
     * Recursively resolves {suffix} in all String values within a map.
     */
    @SuppressWarnings("unchecked")
    private static void resolveSuffixInValue(Object value, String suffix) {
        if (value instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                Object v = entry.getValue();
                if (v instanceof String) {
                    entry.setValue(TestUtils.resolveSuffix((String) v, suffix));
                } else {
                    resolveSuffixInValue(v, suffix);
                }
            }
        } else if (value instanceof List) {
            for (Object item : (List<?>) value) {
                resolveSuffixInValue(item, suffix);
            }
        }
        // Primitives and null — nothing to resolve
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internal builder (JSON path)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds an Identity POJO from JSON or properties.
     *
     * @param suffix       the suffix for uniqueness
     * @param identityKey  the identity key
     * @param section      "create", "expectedCreate", or "expectedModify"
     * @param isCreate     true = append suffix; false = resolve {suffix}
     */
    private static Identity buildIdentity(String suffix, String identityKey,
                                          String section, boolean isCreate) {
        return buildIdentity(suffix, identityKey, section, isCreate, "");
    }

    /**
     * Builds an Identity POJO from JSON or properties, with optional qualifier.
     *
     * @param suffix       the suffix for uniqueness
     * @param identityKey  the identity key
     * @param section      "create", "expectedCreate", or "expectedModify"
     * @param isCreate     true = append suffix; false = resolve {suffix}
     * @param qualifier    qualifier for expectedModify (empty for unqualified)
     */
    private static Identity buildIdentity(String suffix, String identityKey,
                                          String section, boolean isCreate,
                                          String qualifier) {
        if (useJson) {
            return buildIdentityFromJson(suffix, identityKey, section, isCreate, qualifier);
        }
        return buildIdentityFromProps(suffix, identityKey, section, isCreate, qualifier);
    }

    // ── JSON path ────────────────────────────────────────────────────────

    private static Identity buildIdentityFromJson(String suffix, String identityKey,
                                                   String section, boolean isCreate,
                                                   String qualifier) {
        IdentitySection data = getExpectedSection(identityKey, section, qualifier);
        if (data == null) {
            throw new RuntimeException("Missing " + section + " section for identity: " + identityKey
                    + (qualifier.isEmpty() ? "" : " (qualifier=" + qualifier + ")"));
        }
        return buildIdentityFromSection(suffix, data, isCreate, identityKey);
    }

    /**
     * Core builder — converts an IdentitySection POJO into an Identity SCIM model.
     */
    private static Identity buildIdentityFromSection(String suffix, IdentitySection data,
                                                      boolean isCreate, String identityKey) {
        Identity user = new Identity();
        user.schemas = List.of(
                ScimSchemas.SCHEMA_CORE_USER,
                ScimSchemas.SCHEMA_ENTERPRISE_USER,
                ScimSchemas.SCHEMA_SAILPOINT_USER
        );

        // userName
        if (data.getUserName() != null) {
            user.userName = isCreate
                    ? (suffix.isEmpty() ? data.getUserName() : data.getUserName() + "." + suffix)
                    : TestUtils.resolveSuffix(data.getUserName(), suffix);
        }

        // displayName
        user.displayName = isCreate
                ? data.getDisplayName()
                : TestUtils.resolveSuffix(data.getDisplayName(), suffix);

        user.userType = data.getUserType();

        // active
        if (data.getActive() != null) {
            user.active = data.getActive();
        }

        // Name sub-attributes
        if (data.getFirstname() != null || data.getLastname() != null) {
            Identity.Name name = new Identity.Name();
            name.givenName = data.getFirstname();
            name.familyName = data.getLastname();
            user.name = name;
        }

        // Email
        if (data.getEmail() != null && !data.getEmail().isEmpty()) {
            Identity.Email email = new Identity.Email();
            email.value = isCreate
                    ? (suffix.isEmpty() ? data.getEmail() : suffix + "." + data.getEmail())
                    : TestUtils.resolveSuffix(data.getEmail(), suffix);
            email.primary = true;
            user.emails = List.of(email);
        }

        // Enterprise extension — manager
        if (data.getManagerValue() != null && !data.getManagerValue().isEmpty()) {
            Identity.EnterpriseExtension ext = new Identity.EnterpriseExtension();
            Identity.EnterpriseExtension.Manager mgr = new Identity.EnterpriseExtension.Manager();
            mgr.value = data.getManagerValue();
            mgr.displayName = data.getManagerDisplayName();
            ext.manager = mgr;
            user.enterpriseExtension = ext;
        }

        // SailPoint extension — dynamically built from map
        if (data.getSailpoint() != null && !data.getSailpoint().isEmpty()) {
            Map<String, Object> spMap = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : data.getSailpoint().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value == null) continue;

                if (value instanceof List) {
                    // Multi-value array — keep as List
                    @SuppressWarnings("unchecked")
                    List<Object> listValue = (List<Object>) value;
                    spMap.put(key, listValue);
                } else if (value instanceof String) {
                    String strVal = (String) value;
                    if (strVal.contains("{suffix}")) {
                        spMap.put(key, TestUtils.resolveSuffix(strVal, suffix));
                    } else {
                        spMap.put(key, strVal);
                    }
                } else {
                    spMap.put(key, value);
                }
            }
            user.sailPointUser = spMap;
        }

        return user;
    }

    // ── Properties fallback path ─────────────────────────────────────────

    private static Identity buildIdentityFromProps(String suffix, String identityKey,
                                                    String section, boolean isCreate,
                                                    String qualifier) {
        Identity user = new Identity();
        user.schemas = List.of(
                ScimSchemas.SCHEMA_CORE_USER,
                ScimSchemas.SCHEMA_ENTERPRISE_USER,
                ScimSchemas.SCHEMA_SAILPOINT_USER
        );

        String p = "identity." + identityKey + "."
                + (section.equals("expectedModify") && !qualifier.isEmpty()
                        ? section + "." + qualifier + "."
                        : section + ".");

        // userName
        String rawUserName = fallbackProps.getProperty(p + "userName");
        if (rawUserName == null) {
            throw new RuntimeException("Missing property: " + p + "userName");
        }
        user.userName = isCreate
                ? (suffix.isEmpty() ? rawUserName : rawUserName + "." + suffix)
                : TestUtils.resolveSuffix(rawUserName, suffix);

        user.displayName = fallbackProps.getProperty(p + "displayName");
        user.userType = fallbackProps.getProperty(p + "userType");
        String activeStr = fallbackProps.getProperty(p + "active");
        if (activeStr != null) {
            user.active = Boolean.parseBoolean(activeStr);
        }

        // Name sub-attributes
        Identity.Name name = new Identity.Name();
        name.givenName = fallbackProps.getProperty(p + "firstname");
        name.familyName = fallbackProps.getProperty(p + "lastname");
        user.name = name;

        // Email
        String rawEmail = fallbackProps.getProperty(p + "email");
        if (rawEmail != null && !rawEmail.isEmpty()) {
            Identity.Email email = new Identity.Email();
            email.value = isCreate
                    ? (suffix.isEmpty() ? rawEmail : suffix + "." + rawEmail)
                    : TestUtils.resolveSuffix(rawEmail, suffix);
            email.primary = true;
            user.emails = List.of(email);
        }

        // Enterprise extension (manager only)
        String mgrVal = fallbackProps.getProperty(p + "managerValue");
        if (mgrVal != null && !mgrVal.isEmpty()) {
            Identity.EnterpriseExtension ext = new Identity.EnterpriseExtension();
            Identity.EnterpriseExtension.Manager mgr = new Identity.EnterpriseExtension.Manager();
            mgr.value = mgrVal;
            mgr.displayName = fallbackProps.getProperty(p + "managerDisplayName");
            ext.manager = mgr;
            user.enterpriseExtension = ext;
        }

        // SailPoint extension — dynamically built from properties
        String spPrefix = p + "sailpoint.";
        Map<String, Object> spMap = null;
        for (String key : fallbackProps.stringPropertyNames()) {
            if (key.startsWith(spPrefix)) {
                if (spMap == null) spMap = new LinkedHashMap<>();
                String rawAttrName = key.substring(spPrefix.length());
                String value = fallbackProps.getProperty(key);
                if (value == null || value.isEmpty()) continue;
                if (rawAttrName.endsWith("[]")) {
                    String cleanName = rawAttrName.substring(0, rawAttrName.length() - 2);
                    spMap.put(cleanName, Arrays.asList(value.split("\\s*,\\s*")));
                } else if (value.contains("{suffix}")) {
                    spMap.put(rawAttrName, TestUtils.resolveSuffix(value, suffix));
                } else {
                    spMap.put(rawAttrName, value);
                }
            }
        }
        if (spMap != null) {
            user.sailPointUser = spMap;
        }

        return user;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Flat prefix lookup (for backward compat with property-style access)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns all properties matching a given prefix from the properties fallback source.
     * Only works when the provider is backed by identity.properties (not JSON).
     */
    private static Map<String, String> getByPrefixFromProps(String prefix) {
        Map<String, String> result = new HashMap<>();
        for (String key : fallbackProps.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                String cleanKey = key.substring(prefix.length());
                result.put(cleanKey, fallbackProps.getProperty(key));
            }
        }
        return result;
    }

    /**
     * Returns expected SailPoint attributes for verification as a flat map.
     * When JSON-backed, flattens the {@code sailpoint} map with {@code capabilities[]} convention.
     * When properties-backed, delegates to the property prefix scan.
     *
     * @param identityKey the identity key
     * @param section     "expectedCreate" or "expectedModify"
     * @param qualifier   empty or round qualifier
     * @return Map of attribute name → expected string value (with {suffix} unresolved)
     */
    public static Map<String, String> getExpectedSailPointFlat(String identityKey,
                                                                String section,
                                                                String qualifier) {
        if (!useJson) {
            String p = "identity." + identityKey + "." + section + "."
                    + (section.equals("expectedModify") && !qualifier.isEmpty()
                            ? qualifier + "." : "")
                    + "sailpoint.";
            return getByPrefixFromProps(p);
        }

        // JSON path: flatten the sailpoint map to property-style entries
        IdentitySection sec = getExpectedSection(identityKey, section, qualifier);
        if (sec == null || sec.getSailpoint() == null) return Map.of();

        Map<String, String> flat = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : sec.getSailpoint().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof List) {
                // Multi-value → flatten to capabilities[]=val1,val2 convention
                List<?> list = (List<?>) value;
                String joined = list.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                flat.put(key + "[]", joined);
            } else {
                flat.put(key, String.valueOf(value));
            }
        }
        return flat;
    }
}
