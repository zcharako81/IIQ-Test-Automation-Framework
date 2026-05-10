package model;

import java.util.List;

public class Identity {
    public List<String> schemas;
    public String userName;
    public Name name;
    public String displayName;
    public String title;
    public String userType;
    public String preferredLanguage;
    public String timezone;
    public String locale;
    public String nickName;
    public String externalId;
    public String profileUrl;
    public List<Email> emails;
    public List<PhoneNumber> phoneNumbers;
    public List<Address> addresses;
    public boolean active;

    @com.fasterxml.jackson.annotation.JsonProperty("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
    public EnterpriseExtension enterpriseExtension;

    public static class EnterpriseExtension {
        public Manager manager;
        public String employeeNumber;
        public String costCenter;
        public String organization;
        public String division;
        public String department;
    }
    public static class Manager {
        public String value;
        public String displayName;
    }
    public static class Name {
        public String givenName;
        public String familyName;
        public String middleName;
        public String honorificPrefix;
        public String honorificSuffix;
    }

    public static class Email {
        public String value;
        public boolean primary;
    }

    public static class PhoneNumber {
        public String value;
        public String type;
        public boolean primary;
    }

    public static class Address {
        public String streetAddress;
        public String locality;
        public String region;
        public String postalCode;
        public String country;
        public String type;
        public boolean primary;
    }
}
