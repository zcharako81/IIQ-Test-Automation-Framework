package factory;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO model for the identity.json test data file.
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
 * <p>The {@code expectedModify} field is a {@code Map<String, IdentitySection>}
 * keyed by qualifier. Use key {@code ""} (empty string) for unqualified modify
 * (phase {@code modify} / {@code verifyModify}), and {@code "1"}, {@code "2"}, etc.
 * for qualified multi-round modify (phase {@code modify:1} / {@code verifyModify:1}).
 *
 * <p>The {@code modify} field holds <b>sparse</b> change data for SCIM PATCH —
 * ONLY the attributes that changed per round, keyed by qualifier ("1", "2").
 * This is distinct from {@code expectedModify}, which holds the full
 * expected state used for post-modify verification.
 *
 * <p>Accounts live inside each {@link IdentitySection} ( {@code expectedCreate.accounts},
 * {@code expectedModify.<qual>.accounts} ), keyed by type (e.g. "ldap").
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
        private List<String> tests;
        @JsonProperty("create")
        private IdentitySection input;
        @JsonProperty("expectedCreate")
        private IdentitySection expected;

        /**
         * Expected state after modify, keyed by qualifier.
         * Key {@code ""} for unqualified modify, {@code "1"}, {@code "2"} etc. for multi-round.
         */
        @JsonProperty("expectedModify")
        private Map<String, IdentitySection> expectedModify;

        /**
         * Sparse modify data for SCIM PATCH, keyed by qualifier ("1", "2").
         * Contains ONLY the changed attributes (no full state).
         * Used by {@code modify:<qualifier>} phase.
         */
        @JsonProperty("modify")
        private Map<String, Map<String, Object>> modify;

        // ── Getters / Setters ────────────────────────────────────────

        public List<String> getTests() { return tests; }
        public void setTests(List<String> tests) { this.tests = tests; }

        /** Returns sparse modify data keyed by qualifier, or null. */
        public Map<String, Map<String, Object>> getModify() { return modify; }
        public void setModify(Map<String, Map<String, Object>> modify) { this.modify = modify; }

        public IdentitySection getInput() { return input; }
        public void setInput(IdentitySection input) { this.input = input; }

        public IdentitySection getExpected() { return expected; }
        public void setExpected(IdentitySection expected) { this.expected = expected; }

        public Map<String, IdentitySection> getExpectedModify() { return expectedModify; }
        public void setExpectedModify(Map<String, IdentitySection> expectedModify) { this.expectedModify = expectedModify; }

        /**
         * Returns the expected section for a given modify qualifier.
         * @param qualifier empty string for unqualified, or "1", "2" etc. for qualified rounds
         * @return the IdentitySection, or null if not found
         */
        public IdentitySection getModifySection(String qualifier) {
            if (expectedModify == null) return null;
            return expectedModify.get(qualifier);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Section (shared by input, expected, expectedModify)
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
        private List<String> roles;     // Only meaningful in "expectedCreate" section
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

        public boolean isExists() { return exists; }
        public void setExists(boolean exists) { this.exists = exists; }

        public Map<String, String> getAttributes() { return attributes; }
        public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
    }
}
