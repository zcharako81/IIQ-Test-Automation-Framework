package model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Identity {
    public List<String> schemas;
    public String id;
    public String userName;
    public Name name;
    public String displayName;
    public String userType;
    public List<Email> emails;
    public boolean active;

    @JsonProperty("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
    public EnterpriseExtension enterpriseExtension;

    @JsonProperty("urn:ietf:params:scim:schemas:sailpoint:1.0:User")
    public Map<String, Object> sailPointUser;

    public static class Name {
        public String givenName;
        public String familyName;
    }

    public static class Email {
        public String value;
        public boolean primary;
    }

    public static class EnterpriseExtension {
        public Manager manager;

        public static class Manager {
            public String value;
            public String displayName;
        }
    }
}
