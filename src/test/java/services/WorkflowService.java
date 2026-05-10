package services;

import java.util.HashMap;
import java.util.Map;

import base.ApiClient;
import base.ScimSchemas;
import model.LaunchedWorkflow;
import io.restassured.response.Response;

public class WorkflowService {
    private static final String ENDPOINT =
            ScimSchemas.SCIM_BASE_PATH + ScimSchemas.WORKFLOWS_ENDPOINT;

    public Response launchWorkflow(LaunchedWorkflow wf) {
    	

        Map<String, Object> payload = new HashMap<>();

        payload.put(ScimSchemas.SCHEMA_SAILPOINT_WORKFLOW, wf);

        return ApiClient.post(ENDPOINT, payload);

    }

    public Response getWorkflow(String id) {
        return ApiClient.get(ENDPOINT + "/" + id);
    }

}