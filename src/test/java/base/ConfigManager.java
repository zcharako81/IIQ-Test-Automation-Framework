package base;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import factory.IdentityDataProvider;

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
                // identity.properties is optional — JSON may be the data source
                if ("identity.properties".equals(fileName)) return;
                throw new RuntimeException("Could not find properties file: " + fileName);
            }

            config.load(is);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load properties file: " + fileName, e);
        }
    }

    /**
     * Returns the configured identity data source.
     * <p>
     * Reads {@code identity.data.source} from config.properties.
     * Supported values:
     * <ul>
     *   <li>{@code json} — load from {@code identity.json}</li>
     *   <li>{@code properties} — load from {@code identity.properties}</li>
     * </ul>
     * Default is {@code properties} (backward compatible).
     */
    public static String getIdentityDataSource() {
        String value = getOptional("identity.data.source");
        return value != null ? value.trim() : "properties";
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

    /**
     * Returns the list of configured identity keys.
     * Delegates to {@link IdentityDataProvider} which loads from
     * the source specified by {@code identity.data.source} in config.properties.
     */
    public static List<String> getIdentityKeys() {
        return IdentityDataProvider.getIdentityKeys();
    }

    /**
     * Returns the ordered list of test phases to execute for a given identity key.
     * Delegates to {@link IdentityDataProvider}.
     */
    public static List<String> getIdentityTests(String identityKey) {
        return IdentityDataProvider.getIdentityTests(identityKey);
    }

    /**
     * Returns the list of expected birthright roles for an identity.
     * Delegates to {@link IdentityDataProvider}.
     */
    public static List<String> getIdentityExpectedRoles(String identityKey) {
        return IdentityDataProvider.getIdentityExpectedRoles(identityKey);
    }

    /**
     * Returns the account types for an identity, optionally qualified.
     * Delegates to {@link IdentityDataProvider}.
     */
    public static List<String> getAccountTypes(String identityKey, String qualifier) {
        return IdentityDataProvider.getAccountTypes(identityKey, qualifier);
    }

    /**
     * Returns expected account attributes for an identity+type (no qualifier — backward compatible).
     * Delegates to {@link IdentityDataProvider}.
     */
    public static Map<String, String> getAccountExpectedAttributes(String identityKey, String type) {
        return getAccountExpectedAttributes(identityKey, type, "");
    }

    /**
     * Returns expected account attributes for an identity+type, optionally qualified.
     * Delegates to {@link IdentityDataProvider}.
     */
    public static Map<String, String> getAccountExpectedAttributes(String identityKey, String type, String qualifier) {
        return IdentityDataProvider.getAccountExpectedAttributes(identityKey, type, qualifier);
    }

    /**
     * Returns the application name for an account type (no qualifier — backward compatible).
     * Delegates to {@link IdentityDataProvider}.
     */
    public static String getAccountApplication(String identityKey, String type) {
        return getAccountApplication(identityKey, type, "");
    }

    /**
     * Returns the application name for an account type, optionally qualified.
     * Delegates to {@link IdentityDataProvider}.
     */
    public static String getAccountApplication(String identityKey, String type, String qualifier) {
        return IdentityDataProvider.getAccountApplication(identityKey, type, qualifier);
    }

    /**
     * Returns the exists flag for an account type (no qualifier — backward compatible).
     * Delegates to {@link IdentityDataProvider}.
     */
    public static String getAccountExists(String identityKey, String type) {
        return getAccountExists(identityKey, type, "");
    }

    /**
     * Returns the exists flag for an account type, optionally qualified.
     * Delegates to {@link IdentityDataProvider}.
     */
    public static String getAccountExists(String identityKey, String type, String qualifier) {
        return IdentityDataProvider.getAccountExists(identityKey, type, qualifier);
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
     * Returns an optional fixed suffix from config.properties (test.suffix).
     * When set, overrides System.currentTimeMillis() so identities from a
     * previous creation run can be looked up (when the create phase is absent).
     * Returns null if not configured.
     */
    public static String getTestSuffix() {
        return getOptional("test.suffix");
    }
}