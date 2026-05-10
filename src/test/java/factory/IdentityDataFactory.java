package factory;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import base.ScimSchemas;
import model.Identity;

public class IdentityDataFactory {
    private static Properties props = new Properties();
    static {
        try (InputStream is = IdentityDataFactory.class
                .getClassLoader()
                .getResourceAsStream("identity.properties")) {
            props.load(is);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load identity properties", e);
        }
    }

    /**
     * Creates an Identity POJO from .input.* properties for SCIM POST (create).
     * Suffix is appended/prepended — .input.* values do NOT contain {suffix}.
     */
    public static Identity createIdentity(String suffix, String identityKey) {
        return buildIdentity(suffix, identityKey, "input", true);
    }

    /**
     * Creates an Identity POJO from .expectedAfterModify.* properties for SCIM PUT (modify).
     * Suffix is resolved via {suffix} placeholder — .expectedAfterModify.* values DO contain {suffix}.
     */
    public static Identity createIdentityForModify(String suffix, String identityKey) {
        return buildIdentity(suffix, identityKey, "expectedAfterModify", false);
    }

    /**
     * Internal builder shared by createIdentity and createIdentityForModify.
     * @param suffix       the unique timestamp suffix
     * @param identityKey  the identity key (e.g. "user1")
     * @param section      the property section: "input" or "expectedAfterModify"
     * @param isCreate     true = append/prepend suffix; false = replace {suffix} placeholder
     */
    private static Identity buildIdentity(String suffix, String identityKey,
                                          String section, boolean isCreate) {
        Identity user = new Identity();
        user.schemas = List.of(
                ScimSchemas.SCHEMA_CORE_USER,
                ScimSchemas.SCHEMA_ENTERPRISE_USER,
                ScimSchemas.SCHEMA_SAILPOINT_USER
        );

        String p = "identity." + identityKey + "." + section + ".";

        // userName
        String rawUserName = props.getProperty(p + "userName");
        user.userName = isCreate
                ? rawUserName + "." + suffix
                : rawUserName.replace("{suffix}", suffix);

        user.displayName = props.getProperty(p + "displayName");
        user.userType = props.getProperty(p + "userType");
        user.active = Boolean.parseBoolean(props.getProperty(p + "active"));

        // Name sub-attributes
        Identity.Name name = new Identity.Name();
        name.givenName = props.getProperty(p + "firstname");
        name.familyName = props.getProperty(p + "lastname");
        user.name = name;

        // Email
        String rawEmail = props.getProperty(p + "email");
        Identity.Email email = new Identity.Email();
        email.value = isCreate
                ? suffix + "." + rawEmail
                : rawEmail.replace("{suffix}", suffix);
        email.primary = true;
        user.emails = List.of(email);

        // Enterprise extension (manager only)
        String mgrVal = props.getProperty(p + "managerValue");
        if (mgrVal != null && !mgrVal.isEmpty()) {
            Identity.EnterpriseExtension ext = new Identity.EnterpriseExtension();
            Identity.EnterpriseExtension.Manager mgr = new Identity.EnterpriseExtension.Manager();
            mgr.value = mgrVal;
            mgr.displayName = props.getProperty(p + "managerDisplayName");
            ext.manager = mgr;
            user.enterpriseExtension = ext;
        }

        // SailPoint extension — dynamically built from properties
        String spPrefix = p + "sailpoint.";
        Map<String, Object> spMap = null;
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(spPrefix)) {
                if (spMap == null) spMap = new LinkedHashMap<>();
                String rawAttrName = key.substring(spPrefix.length());
                String value = props.getProperty(key);
                if (value == null || value.isEmpty()) continue;
                // Attribute key ending with [] → multi-value array
                if (rawAttrName.endsWith("[]")) {
                    String cleanName = rawAttrName.substring(0, rawAttrName.length() - 2);
                    spMap.put(cleanName, Arrays.asList(value.split("\\s*,\\s*")));
                } else if (value.contains("{suffix}")) {
                    spMap.put(rawAttrName, value.replace("{suffix}", suffix));
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
}
