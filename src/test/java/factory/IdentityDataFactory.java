package factory;

import model.Identity;

/**
 * Factory for building {@link Identity} POJOs from test data.
 * <p>
 * This class now delegates entirely to {@link IdentityDataProvider},
 * which loads from {@code identity.json} (if present) or falls back to
 * {@code identity.properties} (backward compatible).
 * <p>
 * The public API is unchanged so existing callers work without modification.
 *
 * @see IdentityDataProvider
 */
public class IdentityDataFactory {

    /**
     * Creates an Identity POJO from .input.* properties for SCIM POST (create).
     * Suffix is appended/prepended — .input.* values do NOT contain {suffix}.
     */
    public static Identity createIdentity(String suffix, String identityKey) {
        return IdentityDataProvider.createIdentity(suffix, identityKey);
    }

    /**
     * Creates an Identity POJO from .expectedModify.* properties for SCIM PUT (modify).
     * This is the backward-compatible variant — no qualifier, uses bare expectedModify section.
     */
    public static Identity createIdentityForModify(String suffix, String identityKey) {
        return IdentityDataProvider.createIdentityForModify(suffix, identityKey, "");
    }

    /**
     * Creates an Identity POJO from .expectedModify[.{@literal <qualifier>}].*
     * properties for SCIM PUT (modify).
     *
     * @param suffix      the unique suffix for {suffix} resolution
     * @param identityKey the identity key (e.g. "user1")
     * @param qualifier   empty for bare expectedModify, or "1", "2" etc. for qualified rounds
     */
    public static Identity createIdentityForModify(String suffix, String identityKey, String qualifier) {
        return IdentityDataProvider.createIdentityForModify(suffix, identityKey, qualifier);
    }

    /**
     * Resolves the expected userName for an identity.
     */
    public static String getExpectedUserName(String suffix, String identityKey) {
        return IdentityDataProvider.getExpectedUserName(suffix, identityKey);
    }

    /**
     * Creates an Identity POJO from .expected.* properties (post-creation expected state).
     */
    public static Identity createIdentityFromExpected(String suffix, String identityKey) {
        return IdentityDataProvider.createIdentityFromExpected(suffix, identityKey);
    }
}
