package factory;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;
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

        // SailPoint extension attributes (IIQ-native equivalents)
        Identity.SailPointUser sp = null;
        sp = setSpIfPresent(sp, props, p + "title", (e, v) -> e.title = v);
        sp = setSpIfPresent(sp, props, p + "department", (e, v) -> e.department = v);
        sp = setSpIfPresent(sp, props, p + "location", (e, v) -> e.location = v);
        if (sp != null) {
            user.sailPointUser = sp;
        }

        return user;
    }

    private static Identity.SailPointUser setSpIfPresent(
            Identity.SailPointUser sp, Properties props, String key,
            java.util.function.BiConsumer<Identity.SailPointUser, String> setter) {
        String value = props.getProperty(key);
        if (value != null && !value.isEmpty()) {
            Identity.SailPointUser s = sp != null ? sp : new Identity.SailPointUser();
            setter.accept(s, value);
            return s;
        }
        return sp;
    }
}
