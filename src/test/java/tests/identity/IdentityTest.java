package tests.identity;

import tests.base.BaseTest;

import services.IdentityService;
import services.WorkflowService;

import java.util.List;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import base.ConfigManager;
import model.Identity;
import factory.IdentityDataFactory;
import factory.LaunchedWorkflowDataFactory;
import utils.TestUtils;

public class IdentityTest extends BaseTest {

    private final IdentityService service = new IdentityService();
    private String userId;
    private Identity user;
    private String suffix = String.valueOf(System.currentTimeMillis());

    @Test(description = "SCIM: Create user")

    public void testCreateIdentity() {
        user = IdentityDataFactory.createIdentity(suffix);
        var response = service.createUser(user);
        Assert.assertEquals(response.statusCode(), 201);
        userId = response.jsonPath().getString("id");
        Assert.assertNotNull(userId, "User ID must not be null");
    }

    // -----------------------------------------------------

    @Test(dependsOnMethods = "testCreateIdentity",

          description = "SCIM: Verify created user")

    public void testVerifyIdentity() {

        // wait until available

        TestUtils.waitForCondition(
                () -> service.getUser(userId).statusCode() == 200,
                30,
                1000
        );

        var response = service.getUser(userId);

        Assert.assertEquals(response.statusCode(), 200);

        // --- granular checks ---

        Assert.assertEquals(
                response.jsonPath().getString("id"),
                userId
        );

        Assert.assertEquals(
                response.jsonPath().getString("userName"),
                ConfigManager.get("identity.expectedInput.userName") + "." + suffix
        );

        Assert.assertEquals(
                response.jsonPath().getString("name.givenName"),
                ConfigManager.get("identity.expectedInput.givenName")
        );

        Assert.assertEquals(
                response.jsonPath().getString("name.familyName"),
                ConfigManager.get("identity.expectedInput.familyName")
        );

        Assert.assertEquals(
                response.jsonPath().getString("displayName"),
                ConfigManager.get("identity.expectedInput.displayName")
        );

        Assert.assertEquals(
                response.jsonPath().getString("emails[0].value"),
                suffix + "." + ConfigManager.get("identity.expectedInput.email")
        );
        
        Assert.assertEquals(
        	    response.jsonPath().getString("'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'.manager.value"),
        	    ConfigManager.get("identity.expectedInput.managerValue")
        	);

        Assert.assertEquals(
                response.jsonPath().getBoolean("active"),
                Boolean.valueOf(ConfigManager.get("identity.expectedInput.active"))
        );
    }

    // -----------------------------------------------------
    

    @Test(dependsOnMethods = "testVerifyIdentity",
    	      description = "SCIM: Launch workflow for user")
    	public void testLaunchWorkflowRefreshIdentity() {
    	    WorkflowService workflowService = new WorkflowService();
    	    String taskName = ConfigManager.get("task.name1");
    	    var workflow = LaunchedWorkflowDataFactory.createWorkflow(user.userName,taskName);
    	    var response = workflowService.launchWorkflow(workflow);
    	    Assert.assertEquals(response.statusCode(), 201);
    	    
    	    String workflowId = response.jsonPath().getString("id");
    	    Assert.assertNotNull(workflowId);
    	    
    	    // wait for completion
    	    TestUtils.waitForWorkflowCompletion(
    	            workflowService,
    	            workflowId,
    	            60,
    	            2000
    	    );
    	    var result = workflowService.getWorkflow(workflowId);
    	    String status = result.jsonPath().getString("completionStatus");
    	    Assert.assertEquals(status, "Success");
    	}
    
    // -----------------------------------------------------
    @Test(dependsOnMethods = "testLaunchWorkflowRefreshIdentity",

    	      description = "SCIM: Validate birthright role assignment")

    	public void testBirthrightRoleAssignment() {
    	    List<String> expectedRoles = ConfigManager.getExpectedRoles();
    	    TestUtils.waitForCondition(() -> {
    	        var response = service.getUserWithRoles(userId);
    	        response.jsonPath().prettyPrint();
    	        if (response.statusCode() != 200) return false;
    	        List<String> actualRoles = response.jsonPath().getList(
    	        	    "'urn:ietf:params:scim:schemas:sailpoint:1.0:User'.roles.display"
    	        	);    	        
    	        return actualRoles != null && actualRoles.containsAll(expectedRoles);
    	    }, 60, 2000);
    	    var response = service.getUserWithRoles(userId);
    	    Assert.assertEquals(response.statusCode(), 200);
    	    List<String> actualRoles = response.jsonPath().getList(
    	    		  "'urn:ietf:params:scim:schemas:sailpoint:1.0:User'.roles.display"
    	    );
    	    Assert.assertNotNull(actualRoles, "Roles must not be null");
    	    Assert.assertTrue(
    	            actualRoles.containsAll(expectedRoles),
    	            "Missing expected birthright roles. Expected: " + expectedRoles +
    	            " but found: " + actualRoles
    	    );

    	}
 // -----------------------------------------------------
    

    @Test(dependsOnMethods = "testVerifyIdentity",
    	      description = "SCIM: Launch workflow for LDAP account aggregation")
    	public void testLaunchLdapAggregationWorkflow() {
    	    WorkflowService workflowService = new WorkflowService();
    	    String taskName = ConfigManager.get("task.name2");
    	    var workflow = LaunchedWorkflowDataFactory.createWorkflow(user.userName,taskName);
    	    var response = workflowService.launchWorkflow(workflow);
    	    Assert.assertEquals(response.statusCode(), 201);
    	    
    	    String workflowId = response.jsonPath().getString("id");
    	    Assert.assertNotNull(workflowId);
    	    
    	    // wait for completion
    	    TestUtils.waitForWorkflowCompletion(
    	            workflowService,
    	            workflowId,
    	            60,
    	            2000
    	    );
    	    var result = workflowService.getWorkflow(workflowId);
    	    String status = result.jsonPath().getString("completionStatus");
    	    Assert.assertEquals(status, "Success");
    	}
    
    // -----------------------------------------------------
    @Test(dependsOnMethods = "testLaunchLdapAggregationWorkflow",
    	      description = "SCIM: Verify provisioned accounts")
    	public void testVerifyAccounts() {

    	    var response = service.getUserAccounts(userId);
    	    response.jsonPath().prettyPrint();
    	    Assert.assertEquals(response.statusCode(), 200);

    	    List<Map<String, Object>> accounts = response.jsonPath().getList(
    	    		"'urn:ietf:params:scim:schemas:sailpoint:1.0:User'.accounts"
    	    );

    	    for (String type : ConfigManager.getAccountTypes()) {
    	        String expectedApp = ConfigManager.getAccountApplication(type);

    	        Map<String, Object> account = accounts.stream()
    	                .filter(acc -> expectedApp.equals(acc.get("applicationName")))
    	                .findFirst()
    	                .orElse(null);

    	        boolean shouldExist = Boolean.parseBoolean(
    	                ConfigManager.get("account." + type + ".expected.exists")
    	        );

    	        if (shouldExist) {
    	            Assert.assertNotNull(account, "Account missing for: " + type);

    	            Map<String, String> expectedAttrs =
    	                    ConfigManager.getAccountExpectedAttributes(type);

    	            for (var entry : expectedAttrs.entrySet()) {

    	                Object actual = account.get(entry.getKey());

    	                Assert.assertEquals(
    	                        String.valueOf(actual),
    	                        entry.getValue(),
    	                        "Mismatch in " + type + " for attribute: " + entry.getKey()
    	                );
    	            }

    	        } else {
    	            Assert.assertNull(account, "Account should NOT exist for: " + type);
    	        }
    	    }
    	}
    
    //-------------------------------------------------	
    
    @Test(dependsOnMethods = "testVerifyAccounts",
          description = "SCIM: Delete user")
    public void testDeleteUser() {
        var response = service.deleteUser(userId);
        Assert.assertTrue(
                response.statusCode() == 204 || response.statusCode() == 200,
                "Unexpected delete status: " + response.statusCode()
        );
    }
    // -----------------------------------------------------
}