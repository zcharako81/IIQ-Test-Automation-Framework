package services;

import base.ApiClient;
import base.ConfigManager;
import base.ScimSchemas;
import io.restassured.response.Response;
import model.Identity;

public class IdentityService {

    private static final String ENDPOINT = ScimSchemas.USERS_FULL_PATH;

    public Response createUser(Identity user) {
        return ApiClient.post(ENDPOINT, user);
    }

    public Response putUser(String id, Identity user) {
        return ApiClient.put(ENDPOINT + "/" + id, user);
    }

    public Response getUser(String id) {
        return ApiClient.get(ENDPOINT + "/" + id);
    }
    
    public Response getUserWithRoles( String id) {
        return ApiClient.get(ENDPOINT + "/" + id + "?" + ScimSchemas.QUERY_ROLES);
    }
    
    public Response getUserAccounts(String id) {
        return ApiClient.get(ENDPOINT + "/" + id + "?" + ScimSchemas.QUERY_ACCOUNTS);
    }

    public Response getAccountByRef(String refUrl) {
        String baseUrl = ConfigManager.get("base.url");
        String path = refUrl.substring(refUrl.indexOf(baseUrl) + baseUrl.length());
        return ApiClient.get(path);
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