package base;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class ConfigManager {

    private static final Properties config = new Properties();

    static {
        load("config.properties");
        load("identity.properties");
        load("account.properties");
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
    // Convenience method for your use case
    // -----------------------------------------------------
    public static List<String> getExpectedRoles() {
        return getList("identity.expected.roles");
    }
    
    // -----------------------------------------------------
    // Get account types as list (comma-separated)
    // -----------------------------------------------------
    public static List<String> getAccountTypes() {
        String value = get("accounts");
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
    
    public static Map<String, String> getAccountExpectedAttributes(String type) {
        return getByPrefix("account." + type + ".expected.attributes.");
    }
    public static String getAccountApplication(String type) {
        return get("account." + type + ".application");
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
}