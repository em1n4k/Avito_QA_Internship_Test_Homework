package qa.avito;

import static io.restassured.RestAssured.given;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;

public class ApiClient {

    private static final String BASE_URL = "https://qa-internship.avito.com";

    // Подготавливаем запрос. Если токена нет, не добавляем Authorization.
    private RequestSpecification request(String token) {
        RequestSpecification request = given().baseUri(BASE_URL);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    public Response register(Map<String, String> body) {
        return request(null).contentType("application/json").body(body).post("/api/1/register");
    }

    public Response authorize(Map<String, String> body) {
        return request(null).contentType("application/json").body(body).post("/api/1/autorize");
    }

    public Response createItem(Map<String, Object> body, String token) {
        return request(token).contentType("application/json").body(body).post("/api/1/item");
    }

    public Response createItemWithUserHeader(Map<String, Object> body, String token, int userId) {
        return request(token)
                .contentType("application/json")
                .header("X-UserId", userId)
                .body(body)
                .post("/api/1/item");
    }

    public Response getItem(String id, String token) {
        return request(token).get("/api/1/item/" + id);
    }

    public Response getSellerItems(int sellerID, String token) {
        return request(token).get("/api/1/" + sellerID + "/item");
    }

    public Response getStatistics(String id, String token, int version) {
        return request(token).get("/api/" + version + "/statistic/" + id);
    }

    public Response deleteItem(String id, String token) {
        return request(token).delete("/api/2/item/" + id);
    }
}
