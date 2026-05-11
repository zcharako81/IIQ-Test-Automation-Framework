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
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import base.ConfigManager;
import io.restassured.response.Response;
import model.Identity;
import factory.IdentityDataFactory;
import factory.LaunchedWorkflowDataFactory;
import utils.TestUtils;

public class IdentityTest extends BaseTest {

    private final IdentityService service = new IdentityService();
    private final WorkflowService workflowService = new WorkflowService();
    private final Map<String, IdentityContext> identities = new LinkedHashMap<>();
    private final String suffix = String.valueOf(System.currentTimeMillis());
    private SoftAssert softAssert;

    static class IdentityContext {
        String userId;
        Identity identity;
        String identityKey;
    }

    @Test(description = "SCIM: Per-identity lifecycle driven by identity.properties .tests list")
    public void testLifecycle() {
        softAssert = new SoftAssert();
        Reporter.log("=== Starting identity lifecycle (suffix: " + suffix + ") ===");

        // ── Phase 0: Create all identities (mandatory) ──────────────────────
        List<String> keys = ConfigManager.getIdentityKeys();
        softAssert.assertFalse(keys.isEmpty(), "No identity keys configured via 'identities' property");
        for (String key : keys) {
            Reporter.log(">>> Creating identity: " + key);
            Identity user = IdentityDataFactory.createIdentity(suffix, key);
            var response = service.createUser(user);
            softAssert.assertEquals(response.statusCode(), 201, "Create failed for identity: " + key);
            String userId = response.jsonPath().getString("id");
            softAssert.assertNotNull(userId, "User ID must not be null for: " + key);
            IdentityContext ctx = new IdentityContext();
            ctx.userId = userId;
            ctx.identity = user;
            ctx.identityKey = key;
            identities.put(key, ctx);
            Reporter.log("<<< Created identity: " + key + " -> id=" + userId);
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
                Reporter.log("  Phase: " + phase + (qualifier.isEmpty() ? "" : " (qualifier='" + qualifier + "')"));
                switch (phaseName) {
                    case "create":
                        break; // already done above
                    case "refresh":
                        doRefresh(ctx);
                        break;
                    case "aggregation":
                        doLaunchAggregations(ctx);
                        break;
                    case "verifyCreate":
                        doVerifyIdentity(ctx, "identity." + ctx.identityKey + ".expected.");
                        break;
                    case "verifyRoles":
                        doVerifyRoles(ctx);
                        break;
                    case "verifyAccounts":
                        doVerifyAccounts(ctx, qualifier);
                        break;
                    case "modify":
                        doModifyIdentity(ctx, qualifier);
                        break;
                    case "verifyModify":
                        doVerifyIdentity(ctx, "identity." + ctx.identityKey + ".expectedAfterModify."
                                + (qualifier.isEmpty() ? "" : qualifier + "."));
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
                Reporter.log("  \u2713 Phase: " + phase + " done");
            }
            Reporter.log("=== Identity: " + ctx.identityKey + " complete ===");
        }

        softAssert.assertAll();
        Reporter.log("=== All phases completed ===");
    }

    // ── Phase methods (single IdentityContext) ──────────────────────────────

    private void doRefresh(IdentityContext ctx) {
        String taskName = ConfigManager.get("task.refresh");
        var workflow = LaunchedWorkflowDataFactory.createWorkflow(ctx.identity.userName, taskName);
        var response = workflowService.launchWorkflow(workflow);
        softAssert.assertEquals(response.statusCode(), 201,
                "Workflow launch failed for: " + ctx.identityKey);
        String workflowId = response.jsonPath().getString("id");
        softAssert.assertNotNull(workflowId);
        TestUtils.waitForWorkflowCompletion(workflowService, workflowId,
                TestUtils.waitTimeout(), TestUtils.waitPoll());
        var result = workflowService.getWorkflow(workflowId);
        softAssert.assertEquals(
                result.jsonPath().getString("completionStatus"),
                "Success",
                "Refresh workflow failed for: " + ctx.identityKey
        );
    }

    private void doLaunchAggregations(IdentityContext ctx) {
        List<String> identityApps = ConfigManager.getAccountTypes(ctx.identityKey);
        for (String appKey : identityApps) {
            String taskName = ConfigManager.getAggregationTaskName(appKey);
            var workflow = LaunchedWorkflowDataFactory.createWorkflow(ctx.identity.userName, taskName);
            var response = workflowService.launchWorkflow(workflow);
            softAssert.assertEquals(response.statusCode(), 201,
                    "Aggregation launch failed for app: " + appKey
                            + " identity: " + ctx.identityKey);
            String workflowId = response.jsonPath().getString("id");
            softAssert.assertNotNull(workflowId);
            TestUtils.waitForWorkflowCompletion(workflowService, workflowId,
                    TestUtils.waitTimeout(), TestUtils.aggregationPoll());
            var result = workflowService.getWorkflow(workflowId);
            softAssert.assertEquals(
                    result.jsonPath().getString("completionStatus"),
                    "Success",
                    "Aggregation workflow failed for app: " + appKey
                            + " identity: " + ctx.identityKey
            );
        }
    }

    private void doVerifyIdentity(IdentityContext ctx, String expectedPrefix) {
        TestUtils.waitForCondition(
                () -> service.getUser(ctx.userId).statusCode() == 200,
                TestUtils.waitTimeout(), TestUtils.waitPoll()
        );
        Response response = service.getUser(ctx.userId);
        softAssert.assertEquals(response.statusCode(), 200, "Get failed for: " + ctx.identityKey);
        softAssert.assertEquals(response.jsonPath().getString("id"), ctx.userId);
        verifyIdentity(response, ctx.identityKey, expectedPrefix);
    }

    private void doVerifyRoles(IdentityContext ctx) {
        List<String> expectedRoles = ConfigManager.getIdentityExpectedRoles(ctx.identityKey);
        softAssert.assertNotNull(expectedRoles, "No expected roles defined for: " + ctx.identityKey);
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
                for (var entry : expectedAttrs.entrySet()) {
                    Object actual = acctAttrs.get(entry.getKey());
                    String expected = entry.getValue().replace("{suffix}", suffix);
                    softAssert.assertEquals(
                            String.valueOf(actual),
                            expected,
                            "Mismatch in " + type + " for attribute: " + entry.getKey()
                                    + " on identity: " + ctx.identityKey);
                }
            } else {
                softAssert.assertNull(account,
                        "Account should NOT exist for type: " + type
                                + " on identity: " + ctx.identityKey);
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
     * Verifies identity attributes from a SCIM GET response against properties
     * under the given expectedPrefix (e.g. "identity.user1.expected." or
     * "identity.user1.expectedAfterModify.").
     */
    private void verifyIdentity(Response response, String identityKey, String expectedPrefix) {
        String p = expectedPrefix;

        TestUtils.verifyStringAttr(response, p + "userName", "userName", suffix, softAssert);
        TestUtils.verifyStringAttr(response, p + "firstname", "name.givenName", suffix, softAssert);
        TestUtils.verifyStringAttr(response, p + "lastname", "name.familyName", suffix, softAssert);
        TestUtils.verifyStringAttr(response, p + "displayName", "displayName", suffix, softAssert);
        TestUtils.verifyStringAttr(response, p + "userType", "userType", suffix, softAssert);
        TestUtils.verifyStringAttr(response, p + "email", "emails[0].value", suffix, softAssert);
        TestUtils.verifyBooleanAttr(response, p + "active", "active", softAssert);

        // Enterprise extension — manager
        String ent = ScimSchemas.JSONPATH_ENTERPRISE;
        TestUtils.verifyStringAttr(response, p + "managerValue", ent + "manager.value", suffix, softAssert);
        TestUtils.verifyStringAttr(response, p + "managerDisplayName", ent + "manager.displayName", suffix, softAssert);

        // SailPoint extension — dynamically verified from expected properties
        String sp = ScimSchemas.JSONPATH_SAILPOINT;
        String spPrefix = p + "sailpoint.";
        Map<String, String> spExpected = ConfigManager.getByPrefix(spPrefix);
        for (Map.Entry<String, String> entry : spExpected.entrySet()) {
            String rawAttrName = entry.getKey();
            boolean isArray = rawAttrName.endsWith("[]");
            String attrName = isArray ? rawAttrName.substring(0, rawAttrName.length() - 2) : rawAttrName;
            String jsonPath = isArray ? sp + attrName + "[0]" : sp + attrName;
            String expectedValue = isArray
                    ? entry.getValue().split("\\s*,\\s*")[0]
                    : entry.getValue();
            String actual = response.jsonPath().getString(jsonPath);
            softAssert.assertEquals(actual, expectedValue.replace("{suffix}", suffix),
                    "Mismatch for sailpoint." + attrName + " on: " + identityKey);
        }
    }
}
