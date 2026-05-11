package base;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class ConfigManager {

    private static final Properties config = new Properties();

    static {
        load("config.properties");
        load("identity.properties");
    }

    // -----------------------------------------------------
    // Load properties file
    // -----------------------------------------------------
    private static void load(String fileName) {
        try (InputStream is = ConfigManager.class
                .getClassLoader()
                .getResourceAsStream(fileName)) {

            if (is == null) {
                throw new RuntimeException("Could not find properties file: " + fileName);
            }

            config.load(is);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load properties file: " + fileName, e);
        }
    }

    // -----------------------------------------------------
    // Get single property
    // -----------------------------------------------------
    public static String get(String key) {
        String value = config.getProperty(key);

        if (value == null) {
            throw new RuntimeException("Missing property: " + key);
        }

        return value.trim();
    }

    // -----------------------------------------------------
    // Get optional property (no exception)
    // -----------------------------------------------------
    public static String getOptional(String key) {
        String value = config.getProperty(key);
        return value != null ? value.trim() : null;
    }

    // -----------------------------------------------------
    // Get list property (comma-separated)
    // -----------------------------------------------------
    public static List<String> getList(String key) {
        String value = get(key); // already validates null

        if (value.isEmpty()) {
            throw new RuntimeException("Property '" + key + "' is empty");
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------
    // Logging control
    // -----------------------------------------------------
    public static boolean isLoggingEnabled() {
        return "true".equalsIgnoreCase(getOptional("logging.enabled"));
    }

    public static List<String> getIdentityKeys() {
        String value = getOptional("identities");
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Default ordered lifecycle phases (backward compatible with old dependsOnMethods chain).
     */
    private static final List<String> DEFAULT_PHASES = List.of(
            "create", "refresh", "aggregation", "verifyCreate", "verifyRoles",
            "verifyAccounts", "modify", "verifyModify", "deleteAccounts", "delete"
    );

    /**
     * Returns the ordered list of test phases to execute for a given identity key.
     * If the property is absent or empty, returns the default lifecycle order.
     * Duplicates are preserved, allowing phases to repeat.
     * Example: identity.user1.tests=create,refresh,verifyCreate,modify,verifyModify,verifyAccounts,delete
     */
    public static List<String> getIdentityTests(String identityKey) {
        String value = getOptional("identity." + identityKey + ".tests");
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_PHASES;
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public static List<String> getIdentityExpectedRoles(String identityKey) {
        return getList("identity." + identityKey + ".expected.roles");
    }

    /**
     * Returns the account types for an identity (no qualifier — backward compatible).
     * Reads from {@code identity.<key>.accounts}.
     */
    public static List<String> getAccountTypes(String identityKey) {
        return getAccountTypes(identityKey, "");
    }

    /**
     * Returns the account types for an identity, optionally qualified.
     * No qualifier: reads from {@code identity.<key>.accounts}.
     * With qualifier: reads from {@code identity.<key>.accounts.<qualifier>}.
     * Example: qualifier="1" → {@code identity.user1.accounts.1}
     */
    public static List<String> getAccountTypes(String identityKey, String qualifier) {
        String propKey = qualifier.isEmpty()
                ? "identity." + identityKey + ".accounts"
                : "identity." + identityKey + ".accounts." + qualifier;
        String value = getOptional(propKey);
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Returns expected account attributes for an identity+type (no qualifier — backward compatible).
     * Reads from {@code identity.<key>.account.<type>.expected.attributes.} prefix.
     */
    public static Map<String, String> getAccountExpectedAttributes(String identityKey, String type) {
        return getAccountExpectedAttributes(identityKey, type, "");
    }

    /**
     * Returns expected account attributes for an identity+type, optionally qualified.
     * No qualifier: prefix is {@code identity.<key>.account.<type>.expected.attributes.}.
     * With qualifier: prefix is {@code identity.<key>.account.<qualifier>.<type>.expected.attributes.}.
     */
    public static Map<String, String> getAccountExpectedAttributes(String identityKey, String type, String qualifier) {
        String prefix = qualifier.isEmpty()
                ? "identity." + identityKey + ".account." + type + ".expected.attributes."
                : "identity." + identityKey + ".account." + qualifier + "." + type + ".expected.attributes.";
        return getByPrefix(prefix);
    }

    /**
     * Returns the application name for an account type (no qualifier — backward compatible).
     * Reads from {@code identity.<key>.account.<type>.application}.
     */
    public static String getAccountApplication(String identityKey, String type) {
        return getAccountApplication(identityKey, type, "");
    }

    /**
     * Returns the application name for an account type, optionally qualified.
     * No qualifier: reads from {@code identity.<key>.account.<type>.application}.
     * With qualifier: reads from {@code identity.<key>.account.<qualifier>.<type>.application}.
     */
    public static String getAccountApplication(String identityKey, String type, String qualifier) {
        String propKey = qualifier.isEmpty()
                ? "identity." + identityKey + ".account." + type + ".application"
                : "identity." + identityKey + ".account." + qualifier + "." + type + ".application";
        return get(propKey);
    }

    /**
     * Returns the exists flag for an account type (no qualifier — backward compatible).
     * Reads from {@code identity.<key>.account.<type>.expected.exists}.
     */
    public static String getAccountExists(String identityKey, String type) {
        return getAccountExists(identityKey, type, "");
    }

    /**
     * Returns the exists flag for an account type, optionally qualified.
     * No qualifier: reads from {@code identity.<key>.account.<type>.expected.exists}.
     * With qualifier: reads from {@code identity.<key>.account.<qualifier>.<type>.expected.exists}.
     */
    public static String getAccountExists(String identityKey, String type, String qualifier) {
        String propKey = qualifier.isEmpty()
                ? "identity." + identityKey + ".account." + type + ".expected.exists"
                : "identity." + identityKey + ".account." + qualifier + "." + type + ".expected.exists";
        String value = getOptional(propKey);
        return value != null ? value : "false";
    }
    public static Map<String, String> getByPrefix(String prefix) {
        Map<String, String> result = new HashMap<>();
        for (String key : config.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                String cleanKey = key.substring(prefix.length());
                result.put(cleanKey, config.getProperty(key));
            }
        }
        return result;
    }

    /**
     * Returns the set of all unique account types across all identities.
     * Derives app keys from every {@code identity.<key>.accounts} property.
     */
    public static Set<String> getAllAccountTypes() {
        Set<String> allTypes = new LinkedHashSet<>();
        for (String identityKey : getIdentityKeys()) {
            allTypes.addAll(getAccountTypes(identityKey));
        }
        return allTypes;
    }

    /**
     * Returns the IIQ aggregation task name for the given app key.
     * Reads from {@code task.aggregation.<appKey>} in config.properties.
     */
    public static String getAggregationTaskName(String appKey) {
        return get("task.aggregation." + appKey);
    }
}