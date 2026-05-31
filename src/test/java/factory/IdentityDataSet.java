package factory;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO model for the identity.jsonc test data file.
 * <p>
 * Root structure:
 * <pre>
 * {
 *   "identities": {
 *     "user1": { ... },
 *     "user2": { ... }
 *   }
 * }
 * </pre>
 *
 * <p>The {@code verifyModify} field is a {@code Map<String, IdentitySection>}
 * keyed by qualifier. Use key {@code ""} (empty string) for unqualified modify
 * (phase {@code modify} / {@code verifyModify}), and {@code "1"}, {@code "2"}, etc.
 * for qualified multi-round modify (phase {@code modify:1} / {@code verifyModify:1}).
 *
 * <p>The {@code modify} field holds <b>sparse</b> change data for SCIM PATCH —
 * ONLY the attributes that changed per round, keyed by qualifier ("1", "2").
 * This is distinct from {@code verifyModify}, which holds the full
 * expected state used for post-modify verification.
 *
 * <p>Accounts live inside each {@link IdentitySection} ( {@code verifyCreate.accounts},
 * {@code verifyModify.<qual>.accounts} ), keyed by type (e.g. "ldap").
 */
public class IdentityDataSet {

    private Map<String, IdentityEntry> identities;

    public Map<String, IdentityEntry> getIdentities() {
        return identities;
    }

    public void setIdentities(Map<String, IdentityEntry> identities) {
        this.identities = identities;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Entry per identity key
    // ─────────────────────────────────────────────────────────────────────

    public static class IdentityEntry {
        /**
         * Optional exact userName of a pre-existing identity in IIQ.
         * When set and no {@code create} phase is present, the framework
         * uses this value directly for the SCIM filter lookup, bypassing
         * suffix-based {@code verifyCreate.userName} resolution.
         * Supports {@code {suffix}} if needed.
         */
        private String existingUserName;

        private List<String> tests;
        @JsonProperty("create")
        private IdentitySection input;
        @JsonProperty("verifyCreate")
        private IdentitySection verifyCreate;

        /**
         * Expected state after modify, keyed by qualifier.
         * Key {@code ""} for unqualified modify, {@code "1"}, {@code "2"} etc. for multi-round.
         */
        @JsonProperty("verifyModify")
        private Map<String, IdentitySection> verifyModify;

        /**
         * Sparse modify data for SCIM PATCH, keyed by qualifier ("1", "2").
         * Contains ONLY the changed attributes (no full state).
         * Used by {@code modify:<qualifier>} phase.
         */
        @JsonProperty("modify")
        private Map<String, Map<String, Object>> modify;

        /**
         * Optional per-phase descriptions, keyed by the full phase string
         * (e.g. {@code "modify:1"}, {@code "task:RefreshIdentitySingle"}).
         * Displayed in the HTML report as a muted sub-label below the phase name.
         */
        private Map<String, String> descriptions;

        // ── Getters / Setters ────────────────────────────────────────

        public String getExistingUserName() { return existingUserName; }
        public void setExistingUserName(String existingUserName) { this.existingUserName = existingUserName; }

        public List<String> getTests() { return tests; }
        public void setTests(List<String> tests) { this.tests = tests; }

        /** Returns sparse modify data keyed by qualifier, or null. */
        public Map<String, Map<String, Object>> getModify() { return modify; }
        public void setModify(Map<String, Map<String, Object>> modify) { this.modify = modify; }

        public IdentitySection getInput() { return input; }
        public void setInput(IdentitySection input) { this.input = input; }

        public IdentitySection getVerifyCreate() { return verifyCreate; }
        public void setVerifyCreate(IdentitySection verifyCreate) { this.verifyCreate = verifyCreate; }

        public Map<String, IdentitySection> getVerifyModify() { return verifyModify; }
        public void setVerifyModify(Map<String, IdentitySection> verifyModify) { this.verifyModify = verifyModify; }

        /** Returns per-phase descriptions, or null if not configured. */
        public Map<String, String> getDescriptions() { return descriptions; }
        public void setDescriptions(Map<String, String> descriptions) { this.descriptions = descriptions; }

        /**
         * Returns the expected section for a given modify qualifier.
         * @param qualifier empty string for unqualified, or "1", "2" etc. for qualified rounds
         * @return the IdentitySection, or null if not found
         */
        public IdentitySection getModifySection(String qualifier) {
            if (verifyModify == null) return null;
            return verifyModify.get(qualifier);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Section (shared by input, verifyCreate, verifyModify)
    // ─────────────────────────────────────────────────────────────────────

    public static class IdentitySection {
        private String userName;
        private String firstname;
        private String lastname;
        private String displayName;
        private String email;
        private String userType;
        private String managerValue;
        private String managerDisplayName;
        private Boolean active;         // Boolean to allow null (optional)
        private List<String> roles;     // Only meaningful in "verifyCreate" section
        private Map<String, Object> sailpoint;
        private Map<String, AccountEntry> accounts;  // keyed by type (e.g. "ldap")

        // ── Getters / Setters ────────────────────────────────────────

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }

        public String getFirstname() { return firstname; }
        public void setFirstname(String firstname) { this.firstname = firstname; }

        public String getLastname() { return lastname; }
        public void setLastname(String lastname) { this.lastname = lastname; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getUserType() { return userType; }
        public void setUserType(String userType) { this.userType = userType; }

        public String getManagerValue() { return managerValue; }
        public void setManagerValue(String managerValue) { this.managerValue = managerValue; }

        public String getManagerDisplayName() { return managerDisplayName; }
        public void setManagerDisplayName(String managerDisplayName) { this.managerDisplayName = managerDisplayName; }

        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }

        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }

        public Map<String, Object> getSailpoint() { return sailpoint; }
        public void setSailpoint(Map<String, Object> sailpoint) { this.sailpoint = sailpoint; }

        public Map<String, AccountEntry> getAccounts() { return accounts; }
        public void setAccounts(Map<String, AccountEntry> accounts) { this.accounts = accounts; }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Account entry
    // ─────────────────────────────────────────────────────────────────────

    public static class AccountEntry {
        private String application;
        private AccountExpected expected;

        public String getApplication() { return application; }
        public void setApplication(String application) { this.application = application; }

        public AccountExpected getExpected() { return expected; }
        public void setExpected(AccountExpected expected) { this.expected = expected; }
    }

    public static class AccountExpected {
        private boolean exists;
        private Map<String, String> attributes;
        private List<EntitlementExpected> entitlements;

        public boolean isExists() { return exists; }
        public void setExists(boolean exists) { this.exists = exists; }

        public Map<String, String> getAttributes() { return attributes; }
        public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }

        public List<EntitlementExpected> getEntitlements() { return entitlements; }
        public void setEntitlements(List<EntitlementExpected> entitlements) { this.entitlements = entitlements; }
    }

    /**
     * Expected entitlement on an account.
     * <p>Each entitlement is identified by its {@code display} name and
     * optionally its {@code type} (e.g. "group", "role", "entitlement").
     * Both fields support the {suffix} placeholder.
     */
    public static class EntitlementExpected {
        private String display;
        private String type;

        public String getDisplay() { return display; }
        public void setDisplay(String display) { this.display = display; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
