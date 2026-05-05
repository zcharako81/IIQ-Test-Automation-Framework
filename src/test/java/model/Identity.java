package model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Identity {
    public List<String> schemas;
    public String userName;
    public Name name;
    public String displayName;
    public List<Email> emails;
    public boolean active;
    
    @com.fasterxml.jackson.annotation.JsonProperty("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
    public EnterpriseExtension enterpriseExtension;

    public static class EnterpriseExtension {
        public Manager manager;
    }
    public static class Manager {
        public String value;
        public String displayName;
    }
    public static class Name {
        public String givenName;
        public String familyName;
    }

    public static class Email {
        public String value;
        public boolean primary;
    }
}
