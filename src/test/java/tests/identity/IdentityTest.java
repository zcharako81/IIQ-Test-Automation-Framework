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
            String ent = "'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'.";

            Assert.assertEquals(response.jsonPath().getString("id"), ctx.userId);
            verifyStringAttr(response, p + "userName", "userName", suffix);
            verifyStringAttr(response, p + "givenName", "name.givenName", suffix);
            verifyStringAttr(response, p + "familyName", "name.familyName", suffix);
            verifyStringAttr(response, p + "middleName", "name.middleName", suffix);
            verifyStringAttr(response, p + "honorificPrefix", "name.honorificPrefix", suffix);
            verifyStringAttr(response, p + "honorificSuffix", "name.honorificSuffix", suffix);
            verifyStringAttr(response, p + "displayName", "displayName", suffix);
            verifyStringAttr(response, p + "email", "emails[0].value", suffix);
            verifyStringAttr(response, p + "title", "title", suffix);
            verifyStringAttr(response, p + "userType", "userType", suffix);
            verifyStringAttr(response, p + "preferredLanguage", "preferredLanguage", suffix);
            verifyStringAttr(response, p + "timezone", "timezone", suffix);
            verifyStringAttr(response, p + "locale", "locale", suffix);
            verifyStringAttr(response, p + "nickName", "nickName", suffix);
            verifyStringAttr(response, p + "externalId", "externalId", suffix);
            verifyStringAttr(response, p + "profileUrl", "profileUrl", suffix);
            verifyBooleanAttr(response, p + "active", "active");

            // Enterprise extension
            verifyStringAttr(response, p + "managerValue", ent + "manager.value", suffix);
            verifyStringAttr(response, p + "employeeNumber", ent + "employeeNumber", suffix);
            verifyStringAttr(response, p + "department", ent + "department", suffix);
            verifyStringAttr(response, p + "costCenter", ent + "costCenter", suffix);
            verifyStringAttr(response, p + "division", ent + "division", suffix);
            verifyStringAttr(response, p + "organization", ent + "organization", suffix);

            // Phone number (single entry, optional)
            verifyStringAttr(response, p + "phoneNumber", "phoneNumbers[0].value", suffix);
            verifyStringAttr(response, p + "phoneNumberType", "phoneNumbers[0].type", suffix);

            // Address (single entry, optional)
            verifyStringAttr(response, p + "addressStreet", "addresses[0].streetAddress", suffix);
            verifyStringAttr(response, p + "addressLocality", "addresses[0].locality", suffix);
            verifyStringAttr(response, p + "addressRegion", "addresses[0].region", suffix);
            verifyStringAttr(response, p + "addressPostalCode", "addresses[0].postalCode", suffix);
            verifyStringAttr(response, p + "addressCountry", "addresses[0].country", suffix);
        }
    }

    /** Asserts a string attribute only if the property key exists. Skips silently if missing. */
    private void verifyStringAttr(Response r, String propKey, String jsonPath, String suffix) {
        String expected = ConfigManager.getOptional(propKey);
        if (expected == null) return;
        String actual = r.jsonPath().getString(jsonPath);
        Assert.assertEquals(actual, expected.replace("{suffix}", suffix), "Mismatch: " + propKey);
    }

    /** Asserts a boolean attribute only if the property key exists. Skips silently if missing. */
    private void verifyBooleanAttr(Response r, String propKey, String jsonPath) {
        String expected = ConfigManager.getOptional(propKey);
        if (expected == null) return;
        Boolean actual = r.jsonPath().getBoolean(jsonPath);
        Assert.assertEquals(actual, Boolean.valueOf(expected), "Mismatch: " + propKey);
    }

    @Test(dependsOnMethods = "testVerifyIdentities",
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
          description = "SCIM: Validate birthright role assignment")
    public void testBirthrightRoleAssignment() {
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

    @Test(dependsOnMethods = "testBirthrightRoleAssignment",
          description = "SCIM: Launch LDAP aggregation workflow")
    public void testLaunchLdapAggregationWorkflows() {
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

    @SuppressWarnings("unchecked")
    @Test(dependsOnMethods = "testLaunchLdapAggregationWorkflows",
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
