package tests.identity;

import tests.base.BaseTest;

import base.ScimSchemas;
import services.IdentityService;
import services.WorkflowService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.testng.Reporter;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import base.ConfigManager;
import io.restassured.response.Response;
import model.Identity;
import factory.IdentityDataFactory;
import factory.IdentityDataProvider;
import factory.IdentityDataSet.IdentitySection;
import factory.LaunchedWorkflowDataFactory;
import utils.TestUtils;

@Listeners(reporting.IdentityPhaseReporter.class)
public class IdentityTest extends BaseTest {

    private final IdentityService service = new IdentityService();
    private final WorkflowService workflowService = new WorkflowService();
    private final Map<String, IdentityContext> identities = new LinkedHashMap<>();
    private final String suffix;
    private SoftAssert softAssert;
    private long testStartTime;

    {
        String raw = ConfigManager.getTestSuffix();
        if (raw == null) {
            suffix = "";                                     // not set → no suffix
        } else if ("random".equalsIgnoreCase(raw.trim())) {
            suffix = String.valueOf(System.currentTimeMillis());  // random → auto-generate
        } else {
            suffix = raw.trim();                             // fixed value
        }
    }

    static class IdentityContext {
        String userId;
        Identity identity;
        String identityKey;
    }

    @Test(description = "SCIM: Per-identity lifecycle driven by identity.properties .tests list")
    public void testLifecycle() {
        softAssert = new SoftAssert();
        testStartTime = System.currentTimeMillis();
        Reporter.log("=== Starting identity lifecycle (suffix: " + suffix + ") ===");

        // ── Phase 0: Initialize IdentityContexts (create or resolve per key) ──
        List<String> keys = ConfigManager.getIdentityKeys();
        softAssert.assertFalse(keys.isEmpty(), "No identity keys configured via 'identities' property");
        for (String key : keys) {
            IdentityContext ctx = new IdentityContext();
            ctx.identityKey = key;
            List<String> phases = ConfigManager.getIdentityTests(key);
            if (phases.contains("create")) {
                // Create the identity via SCIM POST
                Reporter.log(">>> Creating identity: " + key);
                Identity user = IdentityDataFactory.createIdentity(suffix, key);
                var response = service.createUser(user);
                softAssert.assertEquals(response.statusCode(), 201, "Create failed for identity: " + key);
                ctx.userId = response.jsonPath().getString("id");
                softAssert.assertNotNull(ctx.userId, "User ID must not be null for: " + key);
                ctx.identity = user;
                Reporter.log("<<< Created identity: " + key + " -> id=" + ctx.userId);
            } else {
                // Look up existing identity by userName (from expected properties)
                String expectedUserName = IdentityDataFactory.getExpectedUserName(suffix, key);
                Reporter.log(">>> Resolving existing identity: " + key + " (userName=" + expectedUserName + ")");
                var response = service.findUserByUserName(expectedUserName);
                if (response.statusCode() == 200) {
                    List<Map<String, Object>> resources = response.jsonPath().getList("Resources");
                    if (resources != null && !resources.isEmpty()) {
                        Map<String, Object> user = resources.get(0);
                        ctx.userId = (String) user.get("id");
                    }
                }
                softAssert.assertNotNull(ctx.userId,
                        "Could not resolve identity: " + key + " via userName: " + expectedUserName
                                + " (status=" + response.statusCode() + ")");
                ctx.identity = IdentityDataFactory.createIdentityFromExpected(suffix, key);
                Reporter.log("<<< Resolved identity: " + key + " -> id=" + ctx.userId);
            }
            identities.put(key, ctx);
        }

        // ── Per-identity ordered lifecycle ──────────────────────────────────
        for (IdentityContext ctx : identities.values()) {
            List<String> phases = ConfigManager.getIdentityTests(ctx.identityKey);
            Reporter.log("=== Identity: " + ctx.identityKey + " (" + phases.size() + " phases) ===");
            for (String phase : phases) {
                // Parse optional qualifier: "modify:1" → name="modify", qualifier="1"
                String phaseName = phase;
                String qualifier = "";
                int colonIdx = phase.indexOf(':');
                if (colonIdx >= 0) {
                    phaseName = phase.substring(0, colonIdx);
                    qualifier = phase.substring(colonIdx + 1);
                }
                String phaseLabel = phase + (qualifier.isEmpty() ? "" : " (qualifier='" + qualifier + "')");
                Reporter.log("  Phase: " + phaseLabel);
                long phaseStart = System.currentTimeMillis();
                switch (phaseName) {
                    case "create":
                        break; // already handled during initialization
                    case "task":
                        doExecuteTask(ctx, qualifier);
                        break;
                    case "verifyCreate":
                        doVerifyIdentity(ctx, "identity." + ctx.identityKey + ".expected.", qualifier);
                        break;
                    case "modify":
                        doModifyIdentity(ctx, qualifier);
                        break;
                    case "verifyModify":
                        doVerifyIdentity(ctx, "identity." + ctx.identityKey + ".expectedAfterModify."
                                + (qualifier.isEmpty() ? "" : qualifier + "."), qualifier);
                        break;
                    case "deleteAccounts":
                        doDeleteAccounts(ctx);
                        break;
                    case "delete":
                        doDeleteIdentity(ctx);
                        break;
                    default:
                        softAssert.fail("Unknown phase: " + phase + " for identity: " + ctx.identityKey);
                }
                long phaseDuration = System.currentTimeMillis() - phaseStart;
                Reporter.log("  Phase: " + phaseLabel + " -> " + phaseDuration + "ms");
            }
            Reporter.log("=== Identity: " + ctx.identityKey + " complete ===");
        }

        softAssert.assertAll();
        long totalDuration = System.currentTimeMillis() - testStartTime;
        Reporter.log("=== All phases completed in " + totalDuration + "ms ===");
    }

    // ── Phase methods (single IdentityContext) ──────────────────────────────

    /**
     * Launches the My-WF-TaskLauncher workflow for the given task name,
     * waits for completion, and asserts it finished with status Success.
     * Used by the {@code task:<taskName>} phase.
     */
    private void doExecuteTask(IdentityContext ctx, String taskName) {
        var workflow = LaunchedWorkflowDataFactory.createWorkflow(ctx.identity.userName, taskName);
        var response = workflowService.launchWorkflow(workflow);
        softAssert.assertEquals(response.statusCode(), 201,
                "Task launch failed for " + taskName + " on: " + ctx.identityKey);
        String workflowId = response.jsonPath().getString("id");
        softAssert.assertNotNull(workflowId);
        TestUtils.waitForWorkflowCompletion(workflowService, workflowId,
                TestUtils.waitTimeout(), TestUtils.waitPoll());
        var result = workflowService.getWorkflow(workflowId);
        softAssert.assertEquals(
                result.jsonPath().getString("completionStatus"),
                "Success",
                "Task " + taskName + " failed for: " + ctx.identityKey
        );
    }

    private void doVerifyIdentity(IdentityContext ctx, String expectedPrefix, String qualifier) {
        TestUtils.waitForCondition(
                () -> service.getUser(ctx.userId).statusCode() == 200,
                TestUtils.waitTimeout(), TestUtils.waitPoll()
        );
        Response response = service.getUser(ctx.userId);
        softAssert.assertEquals(response.statusCode(), 200, "Get failed for: " + ctx.identityKey);
        softAssert.assertEquals(response.jsonPath().getString("id"), ctx.userId);
        // Resolve JSON section for attribute verification
        IdentitySection jsonSection = null;
        if (IdentityDataProvider.isJsonSource()) {
            String sectionName = (qualifier == null || qualifier.isEmpty())
                    ? "expectedCreate" : "expectedModify";
            jsonSection = IdentityDataProvider.getExpectedSection(
                    ctx.identityKey, sectionName, qualifier);
        }
        verifyIdentity(response, ctx.identityKey, expectedPrefix, jsonSection);

        // Consolidated role verification — roles are read from expectedCreate (no qualifier)
        List<String> expectedRoles = ConfigManager.getIdentityExpectedRoles(ctx.identityKey);
        if (expectedRoles != null && !expectedRoles.isEmpty()) {
            doVerifyRoles(ctx, expectedRoles);
        }

        // Consolidated account verification — accounts may be qualified (expectedCreate vs expectedModify)
        List<String> accountTypes = ConfigManager.getAccountTypes(ctx.identityKey, qualifier);
        if (accountTypes != null && !accountTypes.isEmpty()) {
            doVerifyAccounts(ctx, qualifier);
        }
    }

    private void doVerifyRoles(IdentityContext ctx, List<String> expectedRoles) {
        TestUtils.waitForCondition(() -> {
            var resp = service.getUserWithRoles(ctx.userId);
            if (resp.statusCode() != 200) return false;
            List<String> actualRoles = resp.jsonPath().getList(
                    ScimSchemas.JSONPATH_SAILPOINT + "roles.display");
            return actualRoles != null && actualRoles.containsAll(expectedRoles);
        }, TestUtils.waitTimeout(), TestUtils.waitPoll());
        var response = service.getUserWithRoles(ctx.userId);
        softAssert.assertEquals(response.statusCode(), 200);
        List<String> actualRoles = response.jsonPath().getList(
                ScimSchemas.JSONPATH_SAILPOINT + "roles.display");
        softAssert.assertNotNull(actualRoles, "Roles must not be null for: " + ctx.identityKey);
        softAssert.assertTrue(
                actualRoles.containsAll(expectedRoles),
                "Missing expected birthright roles for: " + ctx.identityKey
                        + ". Expected: " + expectedRoles + " but found: " + actualRoles
        );
        int matched = 0;
        for (String role : expectedRoles) {
            if (actualRoles.contains(role)) matched++;
        }
        // Summary BEFORE per-role details
        Reporter.log("  [verifyRoles] Expected: " + expectedRoles + " matched " + matched + "/" + expectedRoles.size());
        for (String role : expectedRoles) {
            boolean ok = actualRoles.contains(role);
            Reporter.log("    [role] " + role + (ok ? " ✓" : " ✗"));
        }
    }

    @SuppressWarnings("unchecked")
    private void doVerifyAccounts(IdentityContext ctx, String qualifier) {
        var response = service.getUserAccounts(ctx.userId);
        softAssert.assertEquals(response.statusCode(), 200,
                "Accounts fetch failed for: " + ctx.identityKey);
        List<Map<String, Object>> accountRefs = response.jsonPath().getList(
                ScimSchemas.JSONPATH_SAILPOINT + "accounts");
        softAssert.assertNotNull(accountRefs, "Account list is null for: " + ctx.identityKey);
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (Map<String, Object> ref : accountRefs) {
            String refUrl = (String) ref.get("$ref");
            softAssert.assertNotNull(refUrl,
                    "Missing $ref in account reference for: " + ctx.identityKey);
            Response acctResponse = service.getAccountByRef(refUrl);
            softAssert.assertEquals(acctResponse.statusCode(), 200,
                    "Failed to fetch full account details from: " + refUrl);
            accounts.add(acctResponse.jsonPath().getMap(""));
        }
        for (String type : ConfigManager.getAccountTypes(ctx.identityKey, qualifier)) {
            String expectedApp = ConfigManager.getAccountApplication(ctx.identityKey, type, qualifier);
            Map<String, Object> account = accounts.stream()
                    .filter(acc -> {
                        Map<String, Object> app =
                                (Map<String, Object>) acc.get("application");
                        String appName = app != null
                                ? (String) app.get("displayName")
                                : null;
                        return expectedApp.equals(appName);
                    })
                    .findFirst()
                    .orElse(null);
            boolean shouldExist = Boolean.parseBoolean(
                    ConfigManager.getAccountExists(ctx.identityKey, type, qualifier));
            if (shouldExist) {
                softAssert.assertNotNull(account,
                        "Account missing for type: " + type + " on identity: " + ctx.identityKey);
                String schemaKey = ScimSchemas.SCHEMA_SAILPOINT_APP_ACCOUNT_PREFIX
                        + expectedApp + ":account";
                Map<String, Object> acctAttrs = (Map<String, Object>) account.get(schemaKey);
                softAssert.assertNotNull(acctAttrs, "No schema attributes found for " + type
                        + " on identity: " + ctx.identityKey);
                Map<String, String> expectedAttrs =
                        ConfigManager.getAccountExpectedAttributes(ctx.identityKey, type, qualifier);
                // Summary BEFORE per-attribute details
                int attrCount = expectedAttrs.size();
                Reporter.log("  [verifyAccounts] App: " + expectedApp + " (" + attrCount + " attrs)");
                for (var entry : expectedAttrs.entrySet()) {
                    String actual = String.valueOf(acctAttrs.get(entry.getKey()));
                    String expected = TestUtils.resolveSuffix(entry.getValue(), suffix);
                    softAssert.assertEquals(
                            actual,
                            expected,
                            "Mismatch in " + type + " for attribute: " + entry.getKey()
                                    + " on identity: " + ctx.identityKey);
                    Reporter.log("    [acct:" + type + "] " + entry.getKey() + " → " + actual);
                }
            } else {
                softAssert.assertNull(account,
                        "Account should NOT exist for type: " + type
                                + " on identity: " + ctx.identityKey);
                Reporter.log("  [verifyAccounts] App: " + expectedApp + " (should not exist)");
            }
        }
    }

    private void doModifyIdentity(IdentityContext ctx, String qualifier) {
        Identity identity = IdentityDataFactory.createIdentityForModify(suffix, ctx.identityKey, qualifier);
        identity.id = ctx.userId;
        var response = service.putUser(ctx.userId, identity);
        softAssert.assertTrue(
                response.statusCode() == 200 || response.statusCode() == 204,
                "PUT failed for " + ctx.identityKey + " (qualifier='" + qualifier + "'): " + response.statusCode());
    }

    private void doDeleteAccounts(IdentityContext ctx) {
        var response = service.getUserAccounts(ctx.userId);
        softAssert.assertEquals(response.statusCode(), 200,
                "Accounts fetch failed for delete on: " + ctx.identityKey);
        List<Map<String, Object>> accountRefs = response.jsonPath().getList(
                ScimSchemas.JSONPATH_SAILPOINT + "accounts");
        if (accountRefs == null || accountRefs.isEmpty()) return;
        for (Map<String, Object> ref : accountRefs) {
            String refUrl = (String) ref.get("$ref");
            softAssert.assertNotNull(refUrl,
                    "Missing $ref in account reference for: " + ctx.identityKey);
            var deleteResponse = service.deleteAccountByRef(refUrl);
            softAssert.assertTrue(
                    deleteResponse.statusCode() == 204 || deleteResponse.statusCode() == 200,
                    "Account delete failed for " + ctx.identityKey + " on ref: " + refUrl
                            + " \u2014 status: " + deleteResponse.statusCode());
        }
    }

    private void doDeleteIdentity(IdentityContext ctx) {
        var response = service.deleteUser(ctx.userId);
        softAssert.assertTrue(
                response.statusCode() == 204 || response.statusCode() == 200,
                "Unexpected delete status for " + ctx.identityKey + ": " + response.statusCode()
        );
    }

    // ── Shared helpers ─────────────────────────────────────────────────────

    /**
     * Verifies identity attributes from a SCIM GET response.
     * <p>
     * In JSON mode (when {@code jsonSection} is non-null), reads expected values
     * from the {@link IdentitySection} POJO fields (supports {@code {suffix}}).
     * In properties mode, reads from config properties under {@code expectedPrefix}.
     *
     * @return the number of attributes that were validated
     */
    private int verifyIdentity(Response response, String identityKey,
                               String expectedPrefix, IdentitySection jsonSection) {
        if (jsonSection != null) {
            return verifyIdentityFromJson(response, identityKey, jsonSection);
        }
        return verifyIdentityFromProperties(response, identityKey, expectedPrefix);
    }

    /**
     * Properties-mode attribute verification.
     * Reads expected values from {@link ConfigManager} under the given prefix.
     */
    private int verifyIdentityFromProperties(Response response, String identityKey,
                                             String expectedPrefix) {
        String p = expectedPrefix;
        String ent = ScimSchemas.JSONPATH_ENTERPRISE;
        String sp = ScimSchemas.JSONPATH_SAILPOINT;

        // ── Pre-count expected attributes ────────────────────────────────
        int count = 0;
        String[] coreKeys = {"userName", "firstname", "lastname", "displayName",
                "userType", "email", "active"};
        for (String key : coreKeys) {
            if (ConfigManager.getOptional(p + key) != null) count++;
        }
        if (ConfigManager.getOptional(p + "managerValue") != null) count++;
        if (ConfigManager.getOptional(p + "managerDisplayName") != null) count++;
        String spPrefix = p + "sailpoint.";
        Map<String, String> spExpected = ConfigManager.getByPrefix(spPrefix);
        count += spExpected.size();

        // ── Log summary FIRST (before sub-details) ───────────────────────
        Reporter.log("  [verifyIdentity] Attributes checked: " + count);

        // ── Core SCIM attributes ─────────────────────────────────────────
        if (ConfigManager.getOptional(p + "userName") != null) {
            String actual = response.jsonPath().getString("userName");
            Reporter.log("    [attr] userName → " + actual);
        }
        TestUtils.verifyStringAttr(response, p + "userName", "userName", suffix, softAssert);
        if (ConfigManager.getOptional(p + "firstname") != null) {
            String actual = response.jsonPath().getString("name.givenName");
            Reporter.log("    [attr] firstname → " + actual);
        }
        TestUtils.verifyStringAttr(response, p + "firstname", "name.givenName", suffix, softAssert);
        if (ConfigManager.getOptional(p + "lastname") != null) {
            String actual = response.jsonPath().getString("name.familyName");
            Reporter.log("    [attr] lastname → " + actual);
        }
        TestUtils.verifyStringAttr(response, p + "lastname", "name.familyName", suffix, softAssert);
        if (ConfigManager.getOptional(p + "displayName") != null) {
            String actual = response.jsonPath().getString("displayName");
            Reporter.log("    [attr] displayName → " + actual);
        }
        TestUtils.verifyStringAttr(response, p + "displayName", "displayName", suffix, softAssert);
        if (ConfigManager.getOptional(p + "userType") != null) {
            String actual = response.jsonPath().getString("userType");
            Reporter.log("    [attr] userType → " + actual);
        }
        TestUtils.verifyStringAttr(response, p + "userType", "userType", suffix, softAssert);
        if (ConfigManager.getOptional(p + "email") != null) {
            String actual = response.jsonPath().getString("emails[0].value");
            Reporter.log("    [attr] email → " + actual);
        }
        TestUtils.verifyStringAttr(response, p + "email", "emails[0].value", suffix, softAssert);
        if (ConfigManager.getOptional(p + "active") != null) {
            String actual = String.valueOf(response.jsonPath().getBoolean("active"));
            Reporter.log("    [attr] active → " + actual);
        }
        TestUtils.verifyBooleanAttr(response, p + "active", "active", softAssert);

        // ── Enterprise extension — manager ───────────────────────────────
        if (ConfigManager.getOptional(p + "managerValue") != null) {
            String actual = response.jsonPath().getString(ent + "manager.value");
            Reporter.log("    [attr] managerValue → " + actual);
        }
        TestUtils.verifyStringAttr(response, p + "managerValue", ent + "manager.value", suffix, softAssert);
        if (ConfigManager.getOptional(p + "managerDisplayName") != null) {
            String actual = response.jsonPath().getString(ent + "manager.displayName");
            Reporter.log("    [attr] managerDisplayName → " + actual);
        }
        TestUtils.verifyStringAttr(response, p + "managerDisplayName", ent + "manager.displayName", suffix, softAssert);

        // ── SailPoint extension ──────────────────────────────────────────
        for (Map.Entry<String, String> entry : spExpected.entrySet()) {
            String rawAttrName = entry.getKey();
            boolean isArray = rawAttrName.endsWith("[]");
            String attrName = isArray ? rawAttrName.substring(0, rawAttrName.length() - 2) : rawAttrName;
            String jsonPath = isArray ? sp + attrName + "[0]" : sp + attrName;
            String expectedValue = isArray
                    ? entry.getValue().split("\\s*,\\s*")[0]
                    : entry.getValue();
            String actual = response.jsonPath().getString(jsonPath);
            softAssert.assertEquals(actual, TestUtils.resolveSuffix(expectedValue, suffix),
                    "Mismatch for sailpoint." + attrName + " on: " + identityKey);
            Reporter.log("    [attr] sailpoint." + attrName + " → " + actual);
        }
        return count;
    }

    /**
     * JSON-mode attribute verification.
     * Reads expected values from the {@link IdentitySection} POJO fields.
     * Supports {@code {suffix}} placeholders in expected values.
     */
    private int verifyIdentityFromJson(Response response, String identityKey,
                                       IdentitySection section) {
        String ent = ScimSchemas.JSONPATH_ENTERPRISE;
        String sp = ScimSchemas.JSONPATH_SAILPOINT;

        // ── Pre-count expected attributes ────────────────────────────────
        int count = 0;
        if (section.getUserName() != null) count++;
        if (section.getFirstname() != null) count++;
        if (section.getLastname() != null) count++;
        if (section.getDisplayName() != null) count++;
        if (section.getUserType() != null) count++;
        if (section.getEmail() != null) count++;
        if (section.getActive() != null) count++;
        if (section.getManagerValue() != null) count++;
        if (section.getManagerDisplayName() != null) count++;
        Map<String, Object> spAttrs = section.getSailpoint();
        if (spAttrs != null) count += spAttrs.size();

        // ── Log summary FIRST (before sub-details) ───────────────────────
        Reporter.log("  [verifyIdentity] Attributes checked: " + count);

        // ── Core SCIM attributes ─────────────────────────────────────────
        if (section.getUserName() != null) {
            String actual = response.jsonPath().getString("userName");
            String expected = TestUtils.resolveSuffix(section.getUserName(), suffix);
            softAssert.assertEquals(actual, expected,
                    "Mismatch: userName on: " + identityKey);
            Reporter.log("    [attr] userName → " + actual);
        }
        if (section.getFirstname() != null) {
            String actual = response.jsonPath().getString("name.givenName");
            String expected = TestUtils.resolveSuffix(section.getFirstname(), suffix);
            softAssert.assertEquals(actual, expected,
                    "Mismatch: firstname on: " + identityKey);
            Reporter.log("    [attr] firstname → " + actual);
        }
        if (section.getLastname() != null) {
            String actual = response.jsonPath().getString("name.familyName");
            String expected = TestUtils.resolveSuffix(section.getLastname(), suffix);
            softAssert.assertEquals(actual, expected,
                    "Mismatch: lastname on: " + identityKey);
            Reporter.log("    [attr] lastname → " + actual);
        }
        if (section.getDisplayName() != null) {
            String actual = response.jsonPath().getString("displayName");
            String expected = TestUtils.resolveSuffix(section.getDisplayName(), suffix);
            softAssert.assertEquals(actual, expected,
                    "Mismatch: displayName on: " + identityKey);
            Reporter.log("    [attr] displayName → " + actual);
        }
        if (section.getUserType() != null) {
            String actual = response.jsonPath().getString("userType");
            String expected = TestUtils.resolveSuffix(section.getUserType(), suffix);
            softAssert.assertEquals(actual, expected,
                    "Mismatch: userType on: " + identityKey);
            Reporter.log("    [attr] userType → " + actual);
        }
        if (section.getEmail() != null) {
            String actual = response.jsonPath().getString("emails[0].value");
            String expected = TestUtils.resolveSuffix(section.getEmail(), suffix);
            softAssert.assertEquals(actual, expected,
                    "Mismatch: email on: " + identityKey);
            Reporter.log("    [attr] email → " + actual);
        }
        if (section.getActive() != null) {
            String actual = String.valueOf(response.jsonPath().getBoolean("active"));
            String expected = String.valueOf(section.getActive());
            softAssert.assertEquals(actual, expected,
                    "Mismatch: active on: " + identityKey);
            Reporter.log("    [attr] active → " + actual);
        }

        // ── Enterprise extension — manager ───────────────────────────────
        if (section.getManagerValue() != null) {
            String actual = response.jsonPath().getString(ent + "manager.value");
            String expected = TestUtils.resolveSuffix(section.getManagerValue(), suffix);
            softAssert.assertEquals(actual, expected,
                    "Mismatch: managerValue on: " + identityKey);
            Reporter.log("    [attr] managerValue → " + actual);
        }
        if (section.getManagerDisplayName() != null) {
            String actual = response.jsonPath().getString(ent + "manager.displayName");
            String expected = TestUtils.resolveSuffix(section.getManagerDisplayName(), suffix);
            softAssert.assertEquals(actual, expected,
                    "Mismatch: managerDisplayName on: " + identityKey);
            Reporter.log("    [attr] managerDisplayName → " + actual);
        }

        // ── SailPoint extension ──────────────────────────────────────────
        if (spAttrs != null) {
            for (Map.Entry<String, Object> entry : spAttrs.entrySet()) {
                String attrName = entry.getKey();
                String jsonPath = sp + attrName;
                Object rawValue = entry.getValue();
                if (rawValue instanceof List) {
                    // Multi-valued attribute (e.g. "capabilities": ["A", "B"])
                    List<?> rawList = (List<?>) rawValue;
                    List<String> expectedList = new ArrayList<>();
                    for (Object item : rawList) {
                        expectedList.add(TestUtils.resolveSuffix(
                                item != null ? String.valueOf(item) : "", suffix));
                    }
                    List<String> actualList = response.jsonPath().getList(jsonPath);
                    softAssert.assertEquals(actualList, expectedList,
                            "Mismatch for sailpoint." + attrName + " on: " + identityKey);
                    Reporter.log("    [attr] sailpoint." + attrName + " → " + actualList);
                } else {
                    // Single-valued attribute
                    String actual = response.jsonPath().getString(jsonPath);
                    String rawExpected = String.valueOf(rawValue);
                    String expected = TestUtils.resolveSuffix(rawExpected, suffix);
                    softAssert.assertEquals(actual, expected,
                            "Mismatch for sailpoint." + attrName + " on: " + identityKey);
                    Reporter.log("    [attr] sailpoint." + attrName + " → " + actual);
                }
            }
        }

        return count;
    }
}
