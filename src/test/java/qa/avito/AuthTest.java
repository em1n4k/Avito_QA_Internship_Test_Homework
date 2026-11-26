package qa.avito;

import static org.junit.jupiter.api.Assertions.*;

import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuthTest {

    private final ApiClient api = new ApiClient();
    private static final String PASSWORD = "some-strong-password";

    private String generateUsername() {
        return "qa_" + UUID.randomUUID().toString().substring(0, 12);
    }

    private Map<String, String> credentials(String username) {
        return new HashMap<>(Map.of("username", username, "password", PASSWORD));
    }

    private String registerUser() {
        String username = generateUsername();
        Response response = api.register(credentials(username));
        assertEquals(201, response.statusCode());
        return username;
    }

    // При неуспешной авторизации токен не должен выдаваться.
    private void assertRejectedLogin(Response response, int expectedStatus) {
        assertEquals(expectedStatus, response.statusCode());
        if (response.asString().trim().startsWith("{")) {
            assertNull(response.jsonPath().get("accessToken"));
        }
    }

    @Test
    @DisplayName("TC-01 — Регистрация нового пользователя")
    void registerSuccessfully() {
        String username = generateUsername();
        Response response = api.register(credentials(username));

        assertEquals(201, response.statusCode());
        Object id = response.jsonPath().get("id");
        assertTrue(id instanceof Number);
        assertEquals(username, response.jsonPath().getString("username"));
    }

    @Test
    @DisplayName("TC-02 — Повторная регистрация")
    void registerExistingUser() {
        String username = registerUser();
        Response response = api.register(credentials(username));
        assertEquals(409, response.statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "password"})
    @DisplayName("TC-03 — Пустое имя или пароль")
    void registerWithEmptyField(String field) {
        Map<String, String> body = credentials(generateUsername());
        body.put(field, "");

        Response response = api.register(body);
        assertEquals(400, response.statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "password"})
    @DisplayName("TC-04 — Отсутствует имя или пароль")
    void registerWithMissingField(String field) {
        Map<String, String> body = credentials(generateUsername());
        body.remove(field);

        Response response = api.register(body);
        assertEquals(400, response.statusCode());
    }

    @Test
    @DisplayName("TC-05 — Успешная авторизация")
    void authorizeSuccessfully() {
        String username = registerUser();
        Response response = api.authorize(credentials(username));

        assertEquals(200, response.statusCode());
        Object token = response.jsonPath().get("accessToken");
        assertTrue(token instanceof String);
        assertFalse(((String) token).isBlank());
        assertEquals("Bearer", response.jsonPath().getString("tokenType"));
    }

    @Test
    @DisplayName("TC-06 — Неверный пароль")
    void authorizeWithWrongPassword() {
        String username = registerUser();
        Map<String, String> body = credentials(username);
        body.put("password", "wrong-password");

        Response response = api.authorize(body);
        assertRejectedLogin(response, 401);
    }

    @Test
    @DisplayName("TC-07 — Несуществующий пользователь")
    void authorizeNonExistingUser() {
        Response response = api.authorize(credentials(generateUsername()));
        assertRejectedLogin(response, 401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"username", "password"})
    @DisplayName("TC-08 — Отсутствует имя или пароль при авторизации")
    void authorizeWithMissingField(String field) {
        String username = registerUser();
        Map<String, String> body = credentials(username);
        body.remove(field);

        Response response = api.authorize(body);
        assertRejectedLogin(response, 400);
    }

    @Test
    @DisplayName("TC-31 — Пароль не возвращается в ответе")
    void passwordIsNotReturned() {
        String username = generateUsername();

        Map<String, String> body = credentials(username);

        Response registration = api.register(body);
        assertEquals(201, registration.statusCode());

        assertFalse(registration.jsonPath().getMap("$").containsKey("password"));
        assertFalse(registration.asString().contains(PASSWORD));

        Response authorization = api.authorize(body);
        assertEquals(200, authorization.statusCode());

        assertFalse(authorization.jsonPath().getMap("$").containsKey("password"));
        assertFalse(authorization.asString().contains(PASSWORD));
    }
}
