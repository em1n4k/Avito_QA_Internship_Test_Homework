package qa.avito;

import static org.junit.jupiter.api.Assertions.*;

import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ItemTest {

    private final ApiClient api = new ApiClient();
    private static final String PASSWORD = "some-strong-password";

    private User user;
    private final List<User> users = new ArrayList<>();
    private final Map<String, String> createdItems = new HashMap<>();

    private record User(int id, String token) {}

    // Подготовка пользователя

    @BeforeEach
    void setUp() {
        user = registerAndAuthorize();
    }

    private User registerAndAuthorize() {
        String username = "qa_" + UUID.randomUUID().toString().substring(0, 12);

        Map<String, String> credentials =
                Map.of(
                        "username", username,
                        "password", PASSWORD);

        Response registration = api.register(credentials);
        assertEquals(201, registration.statusCode());

        int id = registration.jsonPath().getInt("id");

        Response authorization = api.authorize(credentials);
        assertEquals(200, authorization.statusCode());

        String token = authorization.jsonPath().getString("accessToken");
        assertNotNull(token);
        assertFalse(token.isBlank());

        User newUser = new User(id, token);
        users.add(newUser);

        return newUser;
    }

    // Подготовка объявления

    private Map<String, Object> itemBody(int sellerID) {
        return new HashMap<>(
                Map.of(
                        "sellerID",
                        sellerID,
                        "name",
                        "testItem",
                        "price",
                        9900,
                        "statistics",
                        Map.of(
                                "likes", 21,
                                "viewCount", 11,
                                "contacts", 43)));
    }

    private String extractItemId(Response response, String token) {
        assertEquals(200, response.statusCode(), response.asString());

        String status = response.jsonPath().getString("status");
        assertNotNull(status);

        String id = status.substring(status.lastIndexOf(" ") + 1);

        assertDoesNotThrow(() -> UUID.fromString(id));
        createdItems.put(id, token);

        return id;
    }

    private String createItem(Map<String, Object> body, User seller) {
        Response response = api.createItem(body, seller.token());
        return extractItemId(response, seller.token());
    }

    // Вспомогательные проверки

    private void assertItem(String id, Map<String, Object> body, String token) {

        Response response = api.getItem(id, token);
        assertEquals(200, response.statusCode(), response.asString());

        List<Map<String, Object>> items = response.jsonPath().getList("$");
        assertEquals(1, items.size());

        Map<String, Object> item = items.get(0);

        assertEquals(id, item.get("id"));
        assertEquals(body.get("sellerID"), item.get("sellerId"));
        assertEquals(body.get("name"), item.get("name"));
        assertEquals(body.get("price"), item.get("price"));

        assertEquals(body.get("statistics"), item.get("statistics"), response.asString());
    }

    private void assertEmptySellerItems(User seller) {
        Response response = api.getSellerItems(seller.id(), seller.token());

        assertEquals(200, response.statusCode(), response.asString());
        assertTrue(response.jsonPath().getList("$").isEmpty());
    }

    private void checkBoundaryValue(Map<String, Object> body) {
        Response response = api.createItem(body, user.token());

        System.out.println("Граничные данные: " + body + ", HTTP " + response.statusCode());
        if (response.statusCode() == 200) {
            String id = extractItemId(response, user.token());
            assertItem(id, body, user.token());
        } else {
            assertEquals(400, response.statusCode(), response.asString());
            assertEmptySellerItems(user);
        }
    }

    // Очистка тестовых данных

    private void collectCreatedItems(User seller) {
        Response response = api.getSellerItems(seller.id(), seller.token());

        if (response.statusCode() != 200) {
            System.err.println("Не удалось получить объявления для очистки");
            return;
        }

        List<Map<String, Object>> items = response.jsonPath().getList("$");

        for (Map<String, Object> item : items) {
            // Удаляем только объявления наших тестовых пользователей.
            if (!Integer.valueOf(seller.id()).equals(item.get("sellerId"))) {
                continue;
            }
            if (item.get("id") instanceof String) {
                String id = (String) item.get("id");
                createdItems.put(id, seller.token());
            }
        }
    }

    @AfterEach
    void cleanUp() {
        for (User seller : users) {
            try {
                collectCreatedItems(seller);
            } catch (RuntimeException e) {
                System.err.println("Ошибка получения объявлений для очистки");
            }
        }

        for (String id : createdItems.keySet()) {
            try {
                Response response = api.deleteItem(id, createdItems.get(id));

                if (response.statusCode() != 200 && response.statusCode() != 404) {
                    System.err.println("Не удалось удалить объявление: " + id);
                }
            } catch (RuntimeException e) {
                System.err.println("Ошибка удаления объявления: " + id);
            }
        }
    }

    @Test
    @DisplayName("TC-09 — Создание объявления")
    void createItemSuccessfully() {
        Map<String, Object> body = itemBody(user.id());

        String id = createItem(body, user);

        assertItem(id, body, user.token());
    }

    @Test
    @DisplayName("TC-10 — Два одинаковых объявления")
    void createIdenticalItems() {
        Map<String, Object> body = itemBody(user.id());

        String firstId = createItem(body, user);
        String secondId = createItem(body, user);

        assertNotEquals(firstId, secondId);

        assertItem(firstId, body, user.token());
        assertItem(secondId, body, user.token());
    }

    @Test
    @DisplayName("TC-11 — Создание с чужим sellerID")
    void createItemWithAnotherSellerId() {
        User anotherUser = registerAndAuthorize();
        Map<String, Object> body = itemBody(anotherUser.id());

        Response response = api.createItem(body, user.token());

        assertTrue(response.statusCode() == 400 || response.statusCode() == 401);

        assertEmptySellerItems(anotherUser);
        assertEmptySellerItems(user);
    }

    @Test
    @DisplayName("TC-12 — Подмена X-UserId и sellerID")
    void createItemWithSpoofedUserHeader() {
        User anotherUser = registerAndAuthorize();
        Map<String, Object> body = itemBody(anotherUser.id());

        Response response = api.createItemWithUserHeader(body, user.token(), anotherUser.id());

        assertTrue(response.statusCode() == 400 || response.statusCode() == 401);

        assertEmptySellerItems(anotherUser);
        assertEmptySellerItems(user);
    }

    @Test
    @DisplayName("TC-12 — Чужой X-UserId и собственный sellerID")
    void createItemWithOwnSellerIdAndSpoofedHeader() {
        User anotherUser = registerAndAuthorize();
        Map<String, Object> body = itemBody(user.id());

        Response response = api.createItemWithUserHeader(body, user.token(), anotherUser.id());

        if (response.statusCode() == 200) {
            String id = extractItemId(response, user.token());
            assertItem(id, body, user.token());

            Response sellerItems = api.getSellerItems(user.id(), user.token());

            assertEquals(200, sellerItems.statusCode());
            assertEquals(1, sellerItems.jsonPath().getList("$").size());
            assertEquals(id, sellerItems.jsonPath().getString("[0].id"));
        } else {
            assertTrue(response.statusCode() == 400 || response.statusCode() == 401);

            assertEmptySellerItems(user);
        }

        assertEmptySellerItems(anotherUser);
    }

    @Test
    @DisplayName("TC-13 — Неверный тип price")
    void createItemWithInvalidPriceType() {
        Map<String, Object> body = itemBody(user.id());
        body.put("price", "abc");

        Response response = api.createItem(body, user.token());

        assertEquals(400, response.statusCode(), response.asString());
        assertEmptySellerItems(user);
    }

    @Test
    @DisplayName("TC-13 — Неверный тип statistics")
    void createItemWithInvalidStatisticsType() {
        Map<String, Object> body = itemBody(user.id());
        body.put("statistics", "invalid");

        Response response = api.createItem(body, user.token());

        assertEquals(400, response.statusCode(), response.asString());
        assertEmptySellerItems(user);
    }

    @Test
    @DisplayName("TC-14 — Пустое название")
    void createItemWithEmptyName() {
        Map<String, Object> body = itemBody(user.id());
        body.put("name", "");

        checkBoundaryValue(body);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("TC-15 — Граничные значения статистики")
    void createItemWithBoundaryStatistics(int value) {
        Map<String, Object> body = itemBody(user.id());

        body.put(
                "statistics",
                Map.of(
                        "likes", value,
                        "viewCount", value,
                        "contacts", value));

        checkBoundaryValue(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"likes", "viewCount", "contacts"})
    @DisplayName("TC-16 — Отрицательное значение счётчика")
    void createItemWithNegativeStatistics(String field) {
        Map<String, Object> body = itemBody(user.id());

        Map<String, Integer> statistics =
                new HashMap<>(
                        Map.of(
                                "likes", 21,
                                "viewCount", 11,
                                "contacts", 43));

        statistics.put(field, -1);
        body.put("statistics", statistics);

        checkBoundaryValue(body);
    }

    @Test
    @DisplayName("TC-17 — Отрицательная цена")
    void createItemWithNegativePrice() {
        Map<String, Object> body = itemBody(user.id());
        body.put("price", -1);

        checkBoundaryValue(body);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("TC-18 — Граничные значения цены")
    void createItemWithBoundaryPrice(int price) {
        Map<String, Object> body = itemBody(user.id());
        body.put("price", price);

        checkBoundaryValue(body);
    }

    // Получение объявлений

    @Test
    @DisplayName("TC-19 — Получение существующего объявления")
    void getExistingItem() {
        Map<String, Object> body = itemBody(user.id());

        String id = createItem(body, user);

        assertItem(id, body, user.token());
    }

    @Test
    @DisplayName("TC-20 — Получение несуществующего объявления")
    void getNonExistingItem() {
        String id = UUID.randomUUID().toString();

        Response response = api.getItem(id, user.token());

        assertEquals(404, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("TC-21 — Получение объявления с неверным UUID")
    void getItemWithInvalidId() {
        Response response = api.getItem("not-a-uuid", user.token());

        assertEquals(400, response.statusCode(), response.asString());
    }

    @Test
    @DisplayName("TC-22 — Получение объявлений продавца")
    void getSellerItemsSuccessfully() {
        User anotherUser = registerAndAuthorize();

        Map<String, Object> body = itemBody(user.id());
        Map<String, Object> anotherBody = itemBody(anotherUser.id());

        String firstId = createItem(body, user);
        String secondId = createItem(body, user);
        String anotherId = createItem(anotherBody, anotherUser);

        Response response = api.getSellerItems(user.id(), user.token());

        assertEquals(200, response.statusCode(), response.asString());

        List<String> ids = response.jsonPath().getList("id");

        assertEquals(2, ids.size());
        assertTrue(ids.contains(firstId));
        assertTrue(ids.contains(secondId));
        assertFalse(ids.contains(anotherId));

        List<Map<String, Object>> items = response.jsonPath().getList("$");

        for (Map<String, Object> item : items) {
            assertEquals(user.id(), item.get("sellerId"));
            assertEquals(body.get("name"), item.get("name"));
            assertEquals(body.get("price"), item.get("price"));
            assertEquals(body.get("statistics"), item.get("statistics"));
        }
    }

    @Test
    @DisplayName("TC-23 — Пустой список объявлений продавца")
    void getEmptySellerItems() {
        assertEmptySellerItems(user);
    }

    // Получение статистики

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    @DisplayName("TC-24 — Получение статистики объявления")
    void getItemStatistics(int version) {
        Map<String, Object> body = itemBody(user.id());

        String id = createItem(body, user);

        Response response = api.getStatistics(id, user.token(), version);

        assertEquals(200, response.statusCode(), response.asString());

        Map<String, Object> statistics;

        // V1 по контракту возвращает массив. Формат V2 не описан.
        if (version == 1 || response.asString().trim().startsWith("[")) {
            List<Map<String, Object>> items = response.jsonPath().getList("$");
            assertEquals(1, items.size(), response.asString());
            statistics = items.get(0);
        } else {
            statistics = response.jsonPath().getMap("$");
        }

        assertEquals(21, statistics.get("likes"), response.asString());
        assertEquals(11, statistics.get("viewCount"), response.asString());
        assertEquals(43, statistics.get("contacts"), response.asString());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    @DisplayName("TC-25 — Статистика несуществующего объявления")
    void getNonExistingItemStatistics(int version) {
        String id = UUID.randomUUID().toString();

        Response response = api.getStatistics(id, user.token(), version);

        assertEquals(404, response.statusCode(), response.asString());
    }

    // Удаление объявлений

    @Test
    @DisplayName("TC-26 — Удаление объявления")
    void deleteItemSuccessfully() {
        Map<String, Object> body = itemBody(user.id());
        String id = createItem(body, user);

        Response response = api.deleteItem(id, user.token());
        assertEquals(200, response.statusCode(), response.asString());

        Response deletedItem = api.getItem(id, user.token());
        assertEquals(404, deletedItem.statusCode(), deletedItem.asString());

        assertEmptySellerItems(user);
    }

    @Test
    @DisplayName("TC-27 — Повторное удаление объявления")
    void deleteItemTwice() {
        Map<String, Object> body = itemBody(user.id());
        String id = createItem(body, user);

        Response firstResponse = api.deleteItem(id, user.token());
        assertEquals(200, firstResponse.statusCode(), firstResponse.asString());

        Response secondResponse = api.deleteItem(id, user.token());
        assertEquals(404, secondResponse.statusCode(), secondResponse.asString());
    }

    @Test
    @DisplayName("TC-28 — Удаление объявления с неверным UUID")
    void deleteItemWithInvalidId() {
        Response response = api.deleteItem("not-a-uuid", user.token());

        assertEquals(400, response.statusCode(), response.asString());
    }

    // Проверка доступа к защищённым ручкам

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"invalid-token"})
    @DisplayName("TC-29/30 — Создание объявления без доступа")
    void createItemWithoutAccess(String token) {
        Map<String, Object> body = itemBody(user.id());
        String id = createItem(body, user);

        Response response = api.createItem(body, token);
        assertEquals(401, response.statusCode(), response.asString());

        Response items = api.getSellerItems(user.id(), user.token());
        assertEquals(200, items.statusCode());

        List<String> ids = items.jsonPath().getList("id");

        assertEquals(1, ids.size());
        assertEquals(id, ids.get(0));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"invalid-token"})
    @DisplayName("TC-29/30 — Получение объявления без доступа")
    void getItemWithoutAccess(String token) {
        String id = createItem(itemBody(user.id()), user);

        Response response = api.getItem(id, token);

        assertEquals(401, response.statusCode(), response.asString());
        assertFalse(response.asString().contains("testItem"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"invalid-token"})
    @DisplayName("TC-29/30 — Получение списка объявлений без доступа")
    void getSellerItemsWithoutAccess(String token) {
        createItem(itemBody(user.id()), user);

        Response response = api.getSellerItems(user.id(), token);

        assertEquals(401, response.statusCode(), response.asString());
        assertFalse(response.asString().contains("testItem"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"invalid-token"})
    @DisplayName("TC-29/30 — Получение статистики v1 без доступа")
    void getStatisticsV1WithoutAccess(String token) {
        String id = createItem(itemBody(user.id()), user);

        Response response = api.getStatistics(id, token, 1);

        assertEquals(401, response.statusCode(), response.asString());
        assertFalse(response.asString().contains("\"likes\""));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"invalid-token"})
    @DisplayName("TC-29/30 — Получение статистики v2 без доступа")
    void getStatisticsV2WithoutAccess(String token) {
        String id = createItem(itemBody(user.id()), user);

        Response response = api.getStatistics(id, token, 2);

        assertEquals(401, response.statusCode(), response.asString());
        assertFalse(response.asString().contains("\"likes\""));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"invalid-token"})
    @DisplayName("TC-29/30 — Удаление объявления без доступа")
    void deleteItemWithoutAccess(String token) {
        Map<String, Object> body = itemBody(user.id());
        String id = createItem(body, user);

        Response response = api.deleteItem(id, token);
        assertEquals(401, response.statusCode(), response.asString());

        assertItem(id, body, user.token());
    }

    @Test
    @DisplayName("TC-32 — Проверка формата JSON")
    void checkResponseFormat() {
        Map<String, Object> body = itemBody(user.id());
        String id = createItem(body, user);

        Response item = api.getItem(id, user.token());
        Response statisticsV1 = api.getStatistics(id, user.token(), 1);
        Response statisticsV2 = api.getStatistics(id, user.token(), 2);

        for (Response response : List.of(item, statisticsV1, statisticsV2)) {
            assertEquals(200, response.statusCode());
            assertTrue(response.contentType().startsWith("application/json"));

            assertNotNull(response.jsonPath().get("$"));
        }
    }

    // Сквозной E2E-сценарий

    @Test
    @DisplayName("TC-33 — Полный сценарий работы с объявлением")
    void itemLifecycle() {
        Map<String, Object> body = itemBody(user.id());

        String id = createItem(body, user);

        assertItem(id, body, user.token());

        Response statisticsV1 = api.getStatistics(id, user.token(), 1);
        Response statisticsV2 = api.getStatistics(id, user.token(), 2);

        for (Response response : List.of(statisticsV1, statisticsV2)) {
            assertEquals(200, response.statusCode());

            Map<String, Object> statistics;

            if (response.asString().trim().startsWith("[")) {
                List<Map<String, Object>> items = response.jsonPath().getList("$");
                assertEquals(1, items.size());
                statistics = items.get(0);
            } else {
                statistics = response.jsonPath().getMap("$");
            }

            assertEquals(body.get("statistics"), statistics);
        }

        Response deletion = api.deleteItem(id, user.token());
        assertEquals(200, deletion.statusCode());

        Response deletedItem = api.getItem(id, user.token());
        assertEquals(404, deletedItem.statusCode());

        assertEmptySellerItems(user);
    }
}
