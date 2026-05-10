package factory;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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

    private static final Set<String> SAILPOINT_ARRAY_ATTRS = new HashSet<>(
            Arrays.asList("capabilities", "costcenter"));

    public static Identity createIdentity(String suffix, String identityKey) {
        Identity user = new Identity();
        user.schemas = List.of(
                "urn:ietf:params:scim:schemas:core:2.0:User",
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User",
                "urn:ietf:params:scim:schemas:sailpoint:1.0:User"
        );

        String p = "identity." + identityKey + ".input.";

        user.userName = props.getProperty(p + "userName") + "." + suffix;
        user.displayName = props.getProperty(p + "displayName");
        user.userType = props.getProperty(p + "userType");
        user.active = Boolean.parseBoolean(props.getProperty(p + "active"));

        // Name sub-attributes (property keys use IIQ ObjectConfig names)
        Identity.Name name = new Identity.Name();
        name.givenName = props.getProperty(p + "firstname");
        name.familyName = props.getProperty(p + "lastname");
        user.name = name;

        // Email
        Identity.Email email = new Identity.Email();
        email.value = suffix + "." + props.getProperty(p + "email");
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
                String attrName = key.substring(spPrefix.length());
                String value = props.getProperty(key);
                if (value != null && !value.isEmpty()) {
                    if (SAILPOINT_ARRAY_ATTRS.contains(attrName)) {
                        spMap.put(attrName, Arrays.asList(value.split("\\s*,\\s*")));
                    } else {
                        spMap.put(attrName, value);
                    }
                }
            }
        }
        if (spMap != null) {
            user.sailPointUser = spMap;
        }

        return user;
    }
}
