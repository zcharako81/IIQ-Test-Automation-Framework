package services;

import java.util.HashMap;
import java.util.Map;

import base.ApiClient;
import base.ConfigManager;
import model.LaunchedWorkflow;
import io.restassured.response.Response;

public class WorkflowService {
	private static final String WORKFLOW_SCHEMA =
		    "urn:ietf:params:scim:schemas:sailpoint:1.0:LaunchedWorkflow";

    private static final String ENDPOINT =
        ConfigManager.get("scim.base.path") +
        ConfigManager.get("scim.workflows.endpoint");

    public Response launchWorkflow(LaunchedWorkflow wf) {
    	

        Map<String, Object> payload = new HashMap<>();

        payload.put(WORKFLOW_SCHEMA, wf);

        return ApiClient.post(ENDPOINT, payload);

    }

    public Response getWorkflow(String id) {
        return ApiClient.get(ENDPOINT + "/" + id);
    }

}