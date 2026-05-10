package tests.identity;

import tests.base.BaseTest;

import base.ScimSchemas;
import services.IdentityService;
import services.WorkflowService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

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

    static class IdentityContext {
        String userId;
        Identity identity;
        String identityKey;
    }

    @Test(description = "SCIM: Create identities")
    public void testCreateIdentities() {
        List<String> keys = ConfigManager.getIdentityKeys();
        Assert.assertFalse(keys.isEmpty(), "No identity keys configured via 'identities' property");
        for (String key : keys) {
            Identity user = IdentityDataFactory.createIdentity(suffix, key);
            var response = service.createUser(user);
            Assert.assertEquals(response.statusCode(), 201, "Create failed for identity: " + key);
            String userId = response.jsonPath().getString("id");
            Assert.assertNotNull(userId, "User ID must not be null for: " + key);
            IdentityContext ctx = new IdentityContext();
            ctx.userId = userId;
            ctx.identity = user;
            ctx.identityKey = key;
            identities.put(key, ctx);
        }
    }

    @Test(dependsOnMethods = "testCreateIdentities",
          description = "SCIM: Launch refresh workflow for identities")
    public void testLaunchWorkflowRefreshIdentities() {
        String taskName = ConfigManager.get("task.name1");
        for (IdentityContext ctx : identities.values()) {
            if (!shouldRun(ctx.identityKey, "refresh")) continue;
            var workflow = LaunchedWorkflowDataFactory.createWorkflow(ctx.identity.userName, taskName);
            var response = workflowService.launchWorkflow(workflow);
            Assert.assertEquals(response.statusCode(), 201, "Workflow launch failed for: " + ctx.identityKey);
            String workflowId = response.jsonPath().getString("id");
            Assert.assertNotNull(workflowId);
            TestUtils.waitForWorkflowCompletion(workflowService, workflowId, TestUtils.waitTimeout(), TestUtils.waitPoll());
            var result = workflowService.getWorkflow(workflowId);
            Assert.assertEquals(
                    result.jsonPath().getString("completionStatus"),
                    "Success",
                    "Refresh workflow failed for: " + ctx.identityKey
            );
        }
    }

    @Test(dependsOnMethods = "testLaunchWorkflowRefreshIdentities",
          description = "SCIM: Launch aggregation workflows for all applications")
    public void testLaunchWorkflowAggregations() {
        Set<String> appKeys = ConfigManager.getAllAccountTypes();
        for (String appKey : appKeys) {
            String taskName = ConfigManager.getAggregationTaskName(appKey);
            for (IdentityContext ctx : identities.values()) {
                if (!shouldRun(ctx.identityKey, "aggregation")) continue;
                // Only launch if this identity has an account for this app
                List<String> identityApps = ConfigManager.getAccountTypes(ctx.identityKey);
                if (!identityApps.contains(appKey)) continue;
                var workflow = LaunchedWorkflowDataFactory.createWorkflow(ctx.identity.userName, taskName);
                var response = workflowService.launchWorkflow(workflow);
                Assert.assertEquals(response.statusCode(), 201,
                        "Aggregation launch failed for app: " + appKey + " identity: " + ctx.identityKey);
                String workflowId = response.jsonPath().getString("id");
                Assert.assertNotNull(workflowId);
                TestUtils.waitForWorkflowCompletion(workflowService, workflowId,
                        TestUtils.waitTimeout(), TestUtils.aggregationPoll());
                var result = workflowService.getWorkflow(workflowId);
                Assert.assertEquals(
                        result.jsonPath().getString("completionStatus"),
                        "Success",
                        "Aggregation workflow failed for app: " + appKey + " identity: " + ctx.identityKey
                );
            }
        }
    }

    @Test(dependsOnMethods = "testLaunchWorkflowAggregations",
          description = "SCIM: Verify identities")
    public void testVerifyIdentities() {
        for (IdentityContext ctx : identities.values()) {
            if (!shouldRun(ctx.identityKey, "verifyCreate")) continue;
            TestUtils.waitForCondition(
                    () -> service.getUser(ctx.userId).statusCode() == 200,
                    TestUtils.waitTimeout(), TestUtils.waitPoll()
            );
            String expectedPrefix = "identity." + ctx.identityKey + ".expected.";
            Response response = service.getUser(ctx.userId);
            Assert.assertEquals(response.statusCode(), 200, "Get failed for: " + ctx.identityKey);
            Assert.assertEquals(response.jsonPath().getString("id"), ctx.userId);
            verifyIdentity(response, ctx.identityKey, expectedPrefix);
        }
    }

    /**
     * Verifies identity attributes from a SCIM GET response against properties
     * under the given expectedPrefix (e.g. "identity.user1.expected." or
     * "identity.user1.expectedAfterModify.").
     */
    private void verifyIdentity(Response response, String identityKey, String expectedPrefix) {
        String p = expectedPrefix;

        TestUtils.verifyStringAttr(response, p + "userName", "userName", suffix);
        TestUtils.verifyStringAttr(response, p + "firstname", "name.givenName", suffix);
        TestUtils.verifyStringAttr(response, p + "lastname", "name.familyName", suffix);
        TestUtils.verifyStringAttr(response, p + "displayName", "displayName", suffix);
        TestUtils.verifyStringAttr(response, p + "userType", "userType", suffix);
        TestUtils.verifyStringAttr(response, p + "email", "emails[0].value", suffix);
        TestUtils.verifyBooleanAttr(response, p + "active", "active");

        // Enterprise extension — manager
        String ent = ScimSchemas.JSONPATH_ENTERPRISE;
        TestUtils.verifyStringAttr(response, p + "managerValue", ent + "manager.value", suffix);
        TestUtils.verifyStringAttr(response, p + "managerDisplayName", ent + "manager.displayName", suffix);

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
            Assert.assertEquals(actual, expectedValue.replace("{suffix}", suffix),
                    "Mismatch for sailpoint." + attrName + " on: " + identityKey);
        }
    }

    /**
     * Returns true if the given test phase should execute for this identity.
     * If no .tests property is configured, all phases run (backward compatible).
     * The 'create' phase always returns true (mandatory).
     */
    private boolean shouldRun(String identityKey, String phase) {
        if ("create".equals(phase)) return true;
        List<String> tests = ConfigManager.getIdentityTests(identityKey);
        return tests == null || tests.contains(phase);
    }

    @Test(dependsOnMethods = "testVerifyIdentities",
          description = "SCIM: Verify birthright role assignment")
    public void testVerifyBirthrightRoleAssignment() {
        for (IdentityContext ctx : identities.values()) {
            if (!shouldRun(ctx.identityKey, "verifyRoles")) continue;
            List<String> expectedRoles = ConfigManager.getIdentityExpectedRoles(ctx.identityKey);
            Assert.assertNotNull(expectedRoles, "No expected roles defined for: " + ctx.identityKey);
            TestUtils.waitForCondition(() -> {
                var response = service.getUserWithRoles(ctx.userId);
                if (response.statusCode() != 200) return false;
                List<String> actualRoles = response.jsonPath().getList(
                        ScimSchemas.JSONPATH_SAILPOINT + "roles.display");
                return actualRoles != null && actualRoles.containsAll(expectedRoles);
            }, TestUtils.waitTimeout(), TestUtils.waitPoll());
            var response = service.getUserWithRoles(ctx.userId);
            Assert.assertEquals(response.statusCode(), 200);
            List<String> actualRoles = response.jsonPath().getList(
                    ScimSchemas.JSONPATH_SAILPOINT + "roles.display");
            Assert.assertNotNull(actualRoles, "Roles must not be null for: " + ctx.identityKey);
            Assert.assertTrue(
                    actualRoles.containsAll(expectedRoles),
                    "Missing expected birthright roles for: " + ctx.identityKey +
                    ". Expected: " + expectedRoles + " but found: " + actualRoles
            );
        }
    }

    @SuppressWarnings("unchecked")
    @Test(dependsOnMethods = "testVerifyBirthrightRoleAssignment",
          description = "SCIM: Verify provisioned accounts")
    public void testVerifyAccounts() {
        for (IdentityContext ctx : identities.values()) {
            if (!shouldRun(ctx.identityKey, "verifyAccounts")) continue;
            var response = service.getUserAccounts(ctx.userId);
            Assert.assertEquals(response.statusCode(), 200, "Accounts fetch failed for: " + ctx.identityKey);
            // Extract account references from the User response (displayName, value, $ref)
            List<Map<String, Object>> accountRefs = response.jsonPath().getList(
                    ScimSchemas.JSONPATH_SAILPOINT + "accounts");
            Assert.assertNotNull(accountRefs, "Account list is null for: " + ctx.identityKey);
            // Resolve each account $ref to full Account resource (includes applicationName)
            List<Map<String, Object>> accounts = new ArrayList<>();
            for (Map<String, Object> ref : accountRefs) {
                String refUrl = (String) ref.get("$ref");
                Assert.assertNotNull(refUrl, "Missing $ref in account reference for: " + ctx.identityKey);
                Response acctResponse = service.getAccountByRef(refUrl);
                Assert.assertEquals(acctResponse.statusCode(), 200,
                        "Failed to fetch full account details from: " + refUrl);
                accounts.add(acctResponse.jsonPath().getMap(""));
            }
            for (String type : ConfigManager.getAccountTypes(ctx.identityKey)) {
                String expectedApp = ConfigManager.getAccountApplication(ctx.identityKey, type);
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
                        ConfigManager.getAccountExists(ctx.identityKey, type));
                if (shouldExist) {
                    Assert.assertNotNull(account, "Account missing for type: " + type + " on identity: " + ctx.identityKey);
                    // Attributes are nested under a schema-specific key
                    String schemaKey = ScimSchemas.SCHEMA_SAILPOINT_APP_ACCOUNT_PREFIX
                            + expectedApp + ":account";
                    Map<String, Object> acctAttrs = (Map<String, Object>) account.get(schemaKey);
                    Assert.assertNotNull(acctAttrs, "No schema attributes found for " + type
                            + " on identity: " + ctx.identityKey);
                    Map<String, String> expectedAttrs = ConfigManager.getAccountExpectedAttributes(ctx.identityKey, type);
                    for (var entry : expectedAttrs.entrySet()) {
                        Object actual = acctAttrs.get(entry.getKey());
                        String expected = entry.getValue().replace("{suffix}", suffix);
                        Assert.assertEquals(
                                String.valueOf(actual),
                                expected,
                                "Mismatch in " + type + " for attribute: " + entry.getKey() +
                                " on identity: " + ctx.identityKey);
                    }
                } else {
                    Assert.assertNull(account, "Account should NOT exist for type: " + type + " on identity: " + ctx.identityKey);
                }
            }
        }
    }

    @Test(dependsOnMethods = "testVerifyAccounts",
          description = "SCIM: Modify identities (PUT)")
    public void testModifyIdentities() {
        for (IdentityContext ctx : identities.values()) {
            if (!shouldRun(ctx.identityKey, "modify")) continue;
            Identity identity = IdentityDataFactory.createIdentityForModify(suffix, ctx.identityKey);
            identity.id = ctx.userId;
            var response = service.putUser(ctx.userId, identity);
            Assert.assertTrue(
                    response.statusCode() == 200 || response.statusCode() == 204,
                    "PUT failed for " + ctx.identityKey + ": " + response.statusCode());
        }
    }

    @Test(dependsOnMethods = "testModifyIdentities",
          description = "SCIM: Verify modified identities")
    public void testVerifyModifiedIdentities() {
        for (IdentityContext ctx : identities.values()) {
            if (!shouldRun(ctx.identityKey, "verifyModify")) continue;
            TestUtils.waitForCondition(
                    () -> service.getUser(ctx.userId).statusCode() == 200,
                    TestUtils.waitTimeout(), TestUtils.waitPoll()
            );
            String expectedPrefix = "identity." + ctx.identityKey + ".expectedAfterModify.";
            Response response = service.getUser(ctx.userId);
            Assert.assertEquals(response.statusCode(), 200, "Get failed for: " + ctx.identityKey);
            Assert.assertEquals(response.jsonPath().getString("id"), ctx.userId);
            verifyIdentity(response, ctx.identityKey, expectedPrefix);
        }
    }

    @Test(dependsOnMethods = "testVerifyModifiedIdentities",
          description = "SCIM: Delete provisioned accounts")
    public void testDeleteAccounts() {
        for (IdentityContext ctx : identities.values()) {
            if (!shouldRun(ctx.identityKey, "deleteAccounts")) continue;
            var response = service.getUserAccounts(ctx.userId);
            Assert.assertEquals(response.statusCode(), 200,
                    "Accounts fetch failed for delete on: " + ctx.identityKey);
            List<Map<String, Object>> accountRefs = response.jsonPath().getList(
                    ScimSchemas.JSONPATH_SAILPOINT + "accounts");
            if (accountRefs == null || accountRefs.isEmpty()) continue;
            for (Map<String, Object> ref : accountRefs) {
                String refUrl = (String) ref.get("$ref");
                Assert.assertNotNull(refUrl,
                        "Missing $ref in account reference for: " + ctx.identityKey);
                var deleteResponse = service.deleteAccountByRef(refUrl);
                Assert.assertTrue(
                        deleteResponse.statusCode() == 204 || deleteResponse.statusCode() == 200,
                        "Account delete failed for " + ctx.identityKey + " on ref: " + refUrl
                                + " — status: " + deleteResponse.statusCode());
            }
        }
    }

    @Test(dependsOnMethods = "testDeleteAccounts",
          description = "SCIM: Delete identities")
    public void testDeleteIdentities() {
        for (IdentityContext ctx : identities.values()) {
            if (!shouldRun(ctx.identityKey, "delete")) continue;
            var response = service.deleteUser(ctx.userId);
            Assert.assertTrue(
                    response.statusCode() == 204 || response.statusCode() == 200,
                    "Unexpected delete status for " + ctx.identityKey + ": " + response.statusCode()
            );
        }
    }
}
