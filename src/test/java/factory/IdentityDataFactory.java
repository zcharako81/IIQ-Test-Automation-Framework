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
                "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
        );

        String p = "identity." + identityKey + ".input.";

        user.userName = props.getProperty(p + "userName") + "." + suffix;
        user.displayName = props.getProperty(p + "displayName");
        user.active = Boolean.valueOf(props.getProperty(p + "active"));

        // Name sub-attributes
        Identity.Name name = new Identity.Name();
        name.givenName = props.getProperty(p + "givenName");
        name.familyName = props.getProperty(p + "familyName");
        setIfPresent(props, p + "middleName", v -> name.middleName = v);
        setIfPresent(props, p + "honorificPrefix", v -> name.honorificPrefix = v);
        setIfPresent(props, p + "honorificSuffix", v -> name.honorificSuffix = v);
        user.name = name;

        // Email
        Identity.Email email = new Identity.Email();
        email.value = suffix + "." + props.getProperty(p + "email");
        email.primary = true;
        user.emails = List.of(email);

        // Simple string attributes
        setIfPresent(props, p + "title", v -> user.title = v);
        setIfPresent(props, p + "userType", v -> user.userType = v);
        setIfPresent(props, p + "preferredLanguage", v -> user.preferredLanguage = v);
        setIfPresent(props, p + "timezone", v -> user.timezone = v);
        setIfPresent(props, p + "locale", v -> user.locale = v);
        setIfPresent(props, p + "nickName", v -> user.nickName = v);
        setIfPresent(props, p + "externalId", v -> user.externalId = v);
        setIfPresent(props, p + "profileUrl", v -> user.profileUrl = v);

        // Phone number (single, optional)
        String phoneVal = props.getProperty(p + "phoneNumber");
        if (phoneVal != null && !phoneVal.isEmpty()) {
            Identity.PhoneNumber phone = new Identity.PhoneNumber();
            phone.value = phoneVal;
            phone.type = props.getProperty(p + "phoneNumberType", "work");
            phone.primary = Boolean.parseBoolean(props.getProperty(p + "phoneNumberPrimary", "true"));
            user.phoneNumbers = List.of(phone);
        }

        // Address (single, optional)
        String addrStreet = props.getProperty(p + "addressStreet");
        if (addrStreet != null && !addrStreet.isEmpty()) {
            Identity.Address addr = new Identity.Address();
            addr.streetAddress = addrStreet;
            addr.locality = props.getProperty(p + "addressLocality");
            addr.region = props.getProperty(p + "addressRegion");
            addr.postalCode = props.getProperty(p + "addressPostalCode");
            addr.country = props.getProperty(p + "addressCountry");
            addr.type = props.getProperty(p + "addressType", "work");
            addr.primary = Boolean.parseBoolean(props.getProperty(p + "addressPrimary", "true"));
            user.addresses = List.of(addr);
        }

        // Enterprise Extension
        Identity.EnterpriseExtension ext = null;
        String mgrVal = props.getProperty(p + "managerValue");
        if (mgrVal != null && !mgrVal.isEmpty()) {
            ext = new Identity.EnterpriseExtension();
            Identity.Manager mgr = new Identity.Manager();
            mgr.value = mgrVal;
            mgr.displayName = props.getProperty(p + "managerDisplayName");
            ext.manager = mgr;
        }
        ext = setEntIfPresent(ext, props, p + "employeeNumber", (e, v) -> e.employeeNumber = v);
        ext = setEntIfPresent(ext, props, p + "costCenter", (e, v) -> e.costCenter = v);
        ext = setEntIfPresent(ext, props, p + "organization", (e, v) -> e.organization = v);
        ext = setEntIfPresent(ext, props, p + "division", (e, v) -> e.division = v);
        ext = setEntIfPresent(ext, props, p + "department", (e, v) -> e.department = v);
        if (ext != null) {
            user.enterpriseExtension = ext;
        }

        return user;
    }

    private static boolean setIfPresent(Properties props, String key, java.util.function.Consumer<String> setter) {
        String value = props.getProperty(key);
        if (value != null && !value.isEmpty()) {
            setter.accept(value);
            return true;
        }
        return false;
    }

    private static Identity.EnterpriseExtension setEntIfPresent(
            Identity.EnterpriseExtension ext, Properties props, String key,
            java.util.function.BiConsumer<Identity.EnterpriseExtension, String> setter) {
        String value = props.getProperty(key);
        if (value != null && !value.isEmpty()) {
            Identity.EnterpriseExtension e = ext != null ? ext : new Identity.EnterpriseExtension();
            setter.accept(e, value);
            return e;
        }
        return ext;
    }
}