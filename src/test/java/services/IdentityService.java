package services;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import base.ApiClient;
import base.ConfigManager;
import base.ScimSchemas;
import io.restassured.response.Response;
import model.Identity;
import utils.RetryUtils;

public class IdentityService {

    private static final String ENDPOINT = ScimSchemas.USERS_FULL_PATH;
    private final RetryUtils retry = new RetryUtils();

    public Response createUser(Identity user) {
        return ApiClient.post(ENDPOINT, user);
    }

    public Response putUser(String id, Identity user) {
        return ApiClient.put(ENDPOINT + "/" + id, user);
    }

    /**
     * GET /Users/{id} with retry on transient failures.
     */
    public Response getUser(String id) {
        return retry.executeWithRetry(
                () -> ApiClient.get(ENDPOINT + "/" + id),
                "GET /Users/" + id
        );
    }

    /**
     * GET /Users/{id}?attributes=...roles with retry on transient failures.
     */
    public Response getUserWithRoles(String id) {
        return retry.executeWithRetry(
                () -> ApiClient.get(ENDPOINT + "/" + id + "?" + ScimSchemas.QUERY_ROLES),
                "GET /Users/" + id + " (roles)"
        );
    }

    /**
     * GET /Users/{id}?attributes=...accounts with retry on transient failures.
     */
    public Response getUserAccounts(String id) {
        return retry.executeWithRetry(
                () -> ApiClient.get(ENDPOINT + "/" + id + "?" + ScimSchemas.QUERY_ACCOUNTS),
                "GET /Users/" + id + " (accounts)"
        );
    }

    /**
     * GET account by $ref URL with retry on transient failures.
     */
    public Response getAccountByRef(String refUrl) {
        String baseUrl = ConfigManager.get("base.url");
        String path = refUrl.substring(refUrl.indexOf(baseUrl) + baseUrl.length());
        return retry.executeWithRetry(
                () -> ApiClient.get(path),
                "GET " + path
        );
    }

    /**
     * Finds a SCIM user by userName using the SCIM filter query.
     * GET /Users?filter=userName eq "&lt;userName&gt;"
     * Returns the full ListResponse. The caller should extract Resources[0] if found.
     * <p>
     * No retry — this operation may legitimately return 200 (found) or 200 with empty list (not found).
     */
    public Response findUserByUserName(String userName) {
        String filter = "userName eq \"" + userName + "\"";
        String encodedFilter = URLEncoder.encode(filter, StandardCharsets.UTF_8);
        return ApiClient.get(ENDPOINT + "?filter=" + encodedFilter);
    }

    public Response patchUser(String id, Object patchBody) {
        return ApiClient.patch(ENDPOINT + "/" + id, patchBody);
    }

    public Response deleteAccountByRef(String refUrl) {
        String baseUrl = ConfigManager.get("base.url");
        String path = refUrl.substring(refUrl.indexOf(baseUrl) + baseUrl.length());
        return ApiClient.delete(path);
    }

    public Response deleteUser(String id) {
        return ApiClient.delete(ENDPOINT + "/" + id);
    }
}
