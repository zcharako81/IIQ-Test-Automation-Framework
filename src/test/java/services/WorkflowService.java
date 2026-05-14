package services;

import java.util.HashMap;
import java.util.Map;

import base.ApiClient;
import base.ScimSchemas;
import io.restassured.response.Response;
import model.LaunchedWorkflow;
import utils.RetryUtils;

public class WorkflowService {

    private static final String ENDPOINT =
            ScimSchemas.SCIM_BASE_PATH + ScimSchemas.WORKFLOWS_ENDPOINT;

    private final RetryUtils retry = new RetryUtils();

    public Response launchWorkflow(LaunchedWorkflow wf) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(ScimSchemas.SCHEMA_SAILPOINT_WORKFLOW, wf);
        return ApiClient.post(ENDPOINT, payload);
    }

    /**
     * GET /LaunchedWorkflows/{id} with retry on transient failures.
     */
    public Response getWorkflow(String id) {
        return retry.executeWithRetry(
                () -> ApiClient.get(ENDPOINT + "/" + id),
                "GET /LaunchedWorkflows/" + id
        );
    }
}
