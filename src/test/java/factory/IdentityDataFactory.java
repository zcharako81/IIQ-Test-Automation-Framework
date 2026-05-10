package factory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import model.Identity;
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

        String prefix = "identity." + identityKey + ".input.";

        user.userName = props.getProperty(prefix + "userName") + "." + suffix;
        user.displayName = props.getProperty(prefix + "displayName");
        user.active = Boolean.valueOf(props.getProperty(prefix + "active"));

        Identity.Name name = new Identity.Name();
        name.givenName = props.getProperty(prefix + "givenName");
        name.familyName = props.getProperty(prefix + "familyName");
        user.name = name;

        Identity.Email email = new Identity.Email();
        email.value = suffix + "." + props.getProperty(prefix + "email");
        email.primary = true;
        user.emails = List.of(email);

        String managerName = props.getProperty(prefix + "managerValue");
        if (managerName != null && !managerName.isEmpty()) {
            Identity.EnterpriseExtension enterprise = new Identity.EnterpriseExtension();
            Identity.Manager manager = new Identity.Manager();
            manager.value = managerName;
            manager.displayName = props.getProperty(prefix + "managerDisplayName");
            enterprise.manager = manager;
            user.enterpriseExtension = enterprise;
        }
        return user;
    }

}