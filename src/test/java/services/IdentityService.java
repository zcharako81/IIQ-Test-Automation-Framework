package services;

import base.ApiClient;
import base.ConfigManager;
import io.restassured.response.Response;
import model.Identity;

public class IdentityService {

    private static final String ENDPOINT =
            ConfigManager.get("scim.base.path") +
            ConfigManager.get("scim.users.endpoint");

    public Response createUser(Identity user) {
        return ApiClient.post(ENDPOINT, user);
    }

    public Response getUser(String id) {
        return ApiClient.get(ENDPOINT + "/" + id);
    }
    
    public Response getUserWithRoles( String id) {
        String rolesAttr = ConfigManager.get("identity.scim.roles");
        return ApiClient.get(ENDPOINT + "/" + id + "?" + rolesAttr);
    }
    
    public Response getUserAccounts(String id) {
        String attr =ConfigManager.get("identity.scim.accounts");
        return ApiClient.get(ENDPOINT + "/" + id + "?" + attr);
    }

    public Response getAccountByRef(String refUrl) {
        String baseUrl = ConfigManager.get("base.url");
        String path = refUrl.substring(refUrl.indexOf(baseUrl) + baseUrl.length());
        return ApiClient.get(path);
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