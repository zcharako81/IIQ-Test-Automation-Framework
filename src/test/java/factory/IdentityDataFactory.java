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
	    
	    public static Identity createIdentity(String suffix) {
	    	 Identity user = new Identity();
	         // SCIM mandatory schema
	         user.schemas = List.of(
	                 "urn:ietf:params:scim:schemas:core:2.0:User",
	                 "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
	         );

	         user.userName = props.getProperty("identity.input.userName") + "." + suffix;
	         user.displayName = props.getProperty("identity.input.displayName");
	         user.active = Boolean.valueOf(props.getProperty("identity.input.active"));
	         
	         // Name object
	         Identity.Name name = new Identity.Name();
	         name.givenName = props.getProperty("identity.input.givenName");
	         name.familyName = props.getProperty("identity.input.familyName");
	         user.name = name;

	         // Email
	         Identity.Email email = new Identity.Email();
	         email.value = suffix + "." + props.getProperty("identity.input.email");
	         email.primary = true;
	         user.emails = List.of(email);
	         
	         // NEU: Manager / Enterprise Extension
	         String managerName = props.getProperty("identity.input.managerValue"); 
	         if (managerName != null && !managerName.isEmpty()) {
	             Identity.EnterpriseExtension enterprise = new Identity.EnterpriseExtension();
	             Identity.Manager manager = new Identity.Manager();
	             
	             manager.value = managerName;
	             manager.displayName = props.getProperty("identity.input.managerDisplayName");
	             
	             enterprise.manager = manager;
	             user.enterpriseExtension = enterprise;
	         }
	         return user;
	     }
	    }
	    	   