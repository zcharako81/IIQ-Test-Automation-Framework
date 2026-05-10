package tests.identity;

import tests.base.BaseTest;

import services.IdentityService;
import services.WorkflowService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            var workflow = LaunchedWorkflowDataFactory.createWorkflow(ctx.identity.userName, taskName);
            var response = workflowService.launchWorkflow(workflow);
            Assert.assertEquals(response.statusCode(), 201, "Workflow launch failed for: " + ctx.identityKey);
            String workflowId = response.jsonPath().getString("id");
            Assert.assertNotNull(workflowId);
            TestUtils.waitForWorkflowCompletion(workflowService, workflowId, 60, 2000);
            var result = workflowService.getWorkflow(workflowId);
            Assert.assertEquals(
                    result.jsonPath().getString("completionStatus"),
                    "Success",
                    "Refresh workflow failed for: " + ctx.identityKey
            );
        }
    }

    @Test(dependsOnMethods = "testLaunchWorkflowRefreshIdentities",
          description = "SCIM: Launch LDAP aggregation workflow")
    public void testLaunchWorkflowLdapAggregation() {
        String taskName = ConfigManager.get("task.name2");
        for (IdentityContext ctx : identities.values()) {
            var workflow = LaunchedWorkflowDataFactory.createWorkflow(ctx.identity.userName, taskName);
            var response = workflowService.launchWorkflow(workflow);
            Assert.assertEquals(response.statusCode(), 201, "Aggregation launch failed for: " + ctx.identityKey);
            String workflowId = response.jsonPath().getString("id");
            Assert.assertNotNull(workflowId);
            TestUtils.waitForWorkflowCompletion(workflowService, workflowId, 60, 5000);
            var result = workflowService.getWorkflow(workflowId);
            Assert.assertEquals(
                    result.jsonPath().getString("completionStatus"),
                    "Success",
                    "Aggregation workflow failed for: " + ctx.identityKey
            );
        }
    }

    @Test(dependsOnMethods = "testLaunchWorkflowLdapAggregation",
          description = "SCIM: Verify identities")
    public void testVerifyIdentities() {
        for (IdentityContext ctx : identities.values()) {
            TestUtils.waitForCondition(
                    () -> service.getUser(ctx.userId).statusCode() == 200,
                    30, 1000
            );
            var response = service.getUser(ctx.userId);
            Assert.assertEquals(response.statusCode(), 200, "Get failed for: " + ctx.identityKey);

            String p = "identity." + ctx.identityKey + ".expected.";

            Assert.assertEquals(response.jsonPath().getString("id"), ctx.userId);
            TestUtils.verifyStringAttr(response, p + "userName", "userName", suffix);
            TestUtils.verifyStringAttr(response, p + "firstname", "name.givenName", suffix);
            TestUtils.verifyStringAttr(response, p + "lastname", "name.familyName", suffix);
            TestUtils.verifyStringAttr(response, p + "displayName", "displayName", suffix);
            TestUtils.verifyStringAttr(response, p + "userType", "userType", suffix);
            TestUtils.verifyStringAttr(response, p + "email", "emails[0].value", suffix);
            TestUtils.verifyBooleanAttr(response, p + "active", "active");

            // Enterprise extension — manager
            String ent = "'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'.";
            TestUtils.verifyStringAttr(response, p + "managerValue", ent + "manager.value", suffix);
            TestUtils.verifyStringAttr(response, p + "managerDisplayName", ent + "manager.displayName", suffix);

            // SailPoint extension — dynamically verified from expected properties
            String sp = "'urn:ietf:params:scim:schemas:sailpoint:1.0:User'.";
            String spPrefix = "identity." + ctx.identityKey + ".expected.sailpoint.";
            Map<String, String> spExpected = ConfigManager.getByPrefix(spPrefix);
            for (Map.Entry<String, String> entry : spExpected.entrySet()) {
                String attrName = entry.getKey();
                boolean isArray = entry.getValue().contains(",");
                String jsonPath = isArray ? sp + attrName + "[0]" : sp + attrName;
                String expectedValue = isArray
                        ? entry.getValue().split("\\s*,\\s*")[0]
                        : entry.getValue();
                String actual = response.jsonPath().getString(jsonPath);
                Assert.assertEquals(actual, expectedValue.replace("{suffix}", suffix),
                        "Mismatch for sailpoint." + attrName + " on: " + ctx.identityKey);
            }
        }
    }

    @Test(dependsOnMethods = "testVerifyIdentities",
          description = "SCIM: Verify birthright role assignment")
    public void testVerifyBirthrightRoleAssignment() {
        for (IdentityContext ctx : identities.values()) {
            List<String> expectedRoles = ConfigManager.getIdentityExpectedRoles(ctx.identityKey);
            Assert.assertNotNull(expectedRoles, "No expected roles defined for: " + ctx.identityKey);
            TestUtils.waitForCondition(() -> {
                var response = service.getUserWithRoles(ctx.userId);
                if (response.statusCode() != 200) return false;
                List<String> actualRoles = response.jsonPath().getList(
                        "'urn:ietf:params:scim:schemas:sailpoint:1.0:User'.roles.display");
                return actualRoles != null && actualRoles.containsAll(expectedRoles);
            }, 60, 2000);
            var response = service.getUserWithRoles(ctx.userId);
            Assert.assertEquals(response.statusCode(), 200);
            List<String> actualRoles = response.jsonPath().getList(
                    "'urn:ietf:params:scim:schemas:sailpoint:1.0:User'.roles.display");
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
            var response = service.getUserAccounts(ctx.userId);
            response.prettyPrint();
            Assert.assertEquals(response.statusCode(), 200, "Accounts fetch failed for: " + ctx.identityKey);
            // Extract account references from the User response (displayName, value, $ref)
            List<Map<String, Object>> accountRefs = response.jsonPath().getList(
                    "'urn:ietf:params:scim:schemas:sailpoint:1.0:User'.accounts");
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
                    String schemaKey = "urn:ietf:params:scim:schemas:sailpoint:1.0:Application:Schema:"
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
          description = "SCIM: Delete identities")
    public void testDeleteIdentities() {
        for (IdentityContext ctx : identities.values()) {
            var response = service.deleteUser(ctx.userId);
            Assert.assertTrue(
                    response.statusCode() == 204 || response.statusCode() == 200,
                    "Unexpected delete status for " + ctx.identityKey + ": " + response.statusCode()
            );
        }
    }
}
