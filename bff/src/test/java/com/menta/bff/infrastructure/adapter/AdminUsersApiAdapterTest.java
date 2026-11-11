package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.UserListQuery;
import com.menta.bff.application.dto.UserPage;
import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.dto.UserStatus;
import com.menta.bff.application.dto.UserSummary;
import com.menta.bff.application.port.out.AdminUsersApiClient;
import com.menta.bff.infrastructure.config.AuthProperties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("AdminUsersApiAdapter")
class AdminUsersApiAdapterTest {

    private static final String USERS = "/api/v1/admin/users";
    private static final String TOKEN = "access-token-abc";
    private static final UserListQuery FIRST_PAGE = new UserListQuery(null, null, 0, 20);

    private static final String PAGE_BODY =
            """
            {"items": [
              {"id": "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "email": "ada@example.com", "firstName": "Ada",
               "lastName": "Lovelace", "role": "RECEPTIONIST", "status": "ACTIVE", "demo": false,
               "createdAt": "2026-10-01T09:30:00"},
              {"id": "6c7a8d9f-2e3b-4f4c-8d5e-1b2c3d4e5f60", "email": "admin@example.com", "firstName": null,
               "lastName": null, "role": "ADMIN", "status": "PENDING_ACTIVATION", "demo": true,
               "createdAt": "2026-10-02T10:15:30.123"}],
             "page": 1, "size": 20, "totalElements": 22, "totalPages": 2}
            """;

    private static final String ITEM_WITHOUT_ID = oneItem("\"email\": \"a@example.com\", \"role\": \"ADMIN\","
            + " \"status\": \"ACTIVE\", \"createdAt\": \"2026-10-01T09:30:00\"");
    private static final String ITEM_WITHOUT_EMAIL = oneItem("\"id\": \"u-1\", \"role\": \"ADMIN\","
            + " \"status\": \"ACTIVE\", \"createdAt\": \"2026-10-01T09:30:00\"");
    private static final String ITEM_WITHOUT_ROLE = oneItem("\"id\": \"u-1\", \"email\": \"a@example.com\","
            + " \"status\": \"ACTIVE\", \"createdAt\": \"2026-10-01T09:30:00\"");
    private static final String ITEM_WITHOUT_STATUS = oneItem("\"id\": \"u-1\", \"email\": \"a@example.com\","
            + " \"role\": \"ADMIN\", \"createdAt\": \"2026-10-01T09:30:00\"");
    private static final String ITEM_WITHOUT_DATE = oneItem("\"id\": \"u-1\", \"email\": \"a@example.com\","
            + " \"role\": \"ADMIN\", \"status\": \"ACTIVE\"");
    private static final String ITEM_WITH_UNKNOWN_ROLE = oneItem("\"id\": \"u-1\", \"email\": \"a@example.com\","
            + " \"role\": \"WIZARD\", \"status\": \"ACTIVE\", \"createdAt\": \"2026-10-01T09:30:00\"");
    private static final String ITEM_WITH_BAD_DATE = oneItem("\"id\": \"u-1\", \"email\": \"a@example.com\","
            + " \"role\": \"ADMIN\", \"status\": \"ACTIVE\", \"createdAt\": \"yesterday\"");

    private AdminUsersApiAdapter adapter;

    private static String oneItem(String fields) {
        return "{\"items\": [{" + fields + "}], \"page\": 0, \"size\": 20, \"totalElements\": 1, \"totalPages\": 1}";
    }

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        AuthProperties properties = new AuthProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(Duration.ofMillis(500));
        adapter = new AdminUsersApiAdapter(WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(), properties);
    }

    private static void stubBody(int status, String body) {
        stubFor(get(urlPathEqualTo(USERS)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    @Test
    @DisplayName("sends the bearer token and maps a page of accounts with its paging metadata")
    void mapsAPage() {
        stubBody(200, PAGE_BODY);

        UserPage page = adapter.listUsers(TOKEN, FIRST_PAGE);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(22L);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).containsExactly(
                new UserSummary("5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "ada@example.com", "Ada", "Lovelace",
                        UserRole.RECEPTIONIST, UserStatus.ACTIVE, LocalDateTime.of(2026, 10, 1, 9, 30)),
                new UserSummary("6c7a8d9f-2e3b-4f4c-8d5e-1b2c3d4e5f60", "admin@example.com", null, null,
                        UserRole.ADMIN, UserStatus.PENDING_ACTIVATION,
                        LocalDateTime.of(2026, 10, 2, 10, 15, 30, 123_000_000)));
        verify(getRequestedFor(urlPathEqualTo(USERS)).withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    @DisplayName("an empty page keeps the totals")
    void emptyPage() {
        stubBody(200, "{\"items\": [], \"page\": 3, \"size\": 20, \"totalElements\": 41, \"totalPages\": 3}");

        UserPage page = adapter.listUsers(TOKEN, new UserListQuery(null, null, 3, 20));

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(41L);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("sends page and size, and role and text only when present")
    void sendsTheQuery() {
        stubBody(200, PAGE_BODY);

        adapter.listUsers(TOKEN, new UserListQuery(UserRole.INSTRUCTOR, "ada", 2, 20));
        adapter.listUsers(TOKEN, FIRST_PAGE);

        verify(getRequestedFor(urlPathEqualTo(USERS)).withQueryParam("role", equalTo("INSTRUCTOR"))
                .withQueryParam("q", equalTo("ada")).withQueryParam("page", equalTo("2"))
                .withQueryParam("size", equalTo("20")));
        verify(getRequestedFor(urlPathEqualTo(USERS)).withQueryParam("role", absent())
                .withQueryParam("q", absent()).withQueryParam("page", equalTo("0"))
                .withQueryParam("size", equalTo("20")));
    }

    @Test
    @DisplayName("the search text is encoded as a value: + & % and spaces reach the API as typed")
    void encodesTheSearchText() {
        stubBody(200, PAGE_BODY);

        adapter.listUsers(TOKEN, new UserListQuery(null, "a+b&role=ADMIN %41 ñ", 0, 20));

        verify(getRequestedFor(urlPathEqualTo(USERS))
                .withQueryParam("q", equalTo("a+b&role=ADMIN %41 ñ"))
                .withQueryParam("role", absent()));
    }

    @Test
    @DisplayName("a 400 is an invalid request")
    void badRequest() {
        stubBody(400, "{\"code\":\"INVALID_REQUEST\",\"detail\":\"secret detail\"}");

        assertThatThrownBy(() -> adapter.listUsers(TOKEN, FIRST_PAGE))
                .isInstanceOf(AdminUsersApiClient.InvalidRequestException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest(name = "status {0} is unavailable")
    @ValueSource(ints = {401, 403, 404, 429, 500, 503})
    @DisplayName("any other error status is unavailable, never a leak of the token or the body")
    void errorStatuses(int status) {
        stubBody(status, "{\"detail\":\"secret detail\"}");

        assertThatThrownBy(() -> adapter.listUsers(TOKEN, FIRST_PAGE))
                .isInstanceOf(AdminUsersApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN);
    }

    static Stream<String> unusableBodies() {
        return Stream.of(
                "<html>not json</html>",
                "",
                "{}",
                "{\"items\": [], \"size\": 20, \"totalElements\": 0, \"totalPages\": 0}",
                "{\"items\": [], \"page\": 0, \"totalElements\": 0, \"totalPages\": 0}",
                "{\"items\": [], \"page\": 0, \"size\": 20, \"totalPages\": 0}",
                "{\"items\": [], \"page\": 0, \"size\": 20, \"totalElements\": 0}",
                "{\"items\": [null], \"page\": 0, \"size\": 20, \"totalElements\": 1, \"totalPages\": 1}",
                ITEM_WITHOUT_ID, ITEM_WITHOUT_EMAIL, ITEM_WITHOUT_ROLE, ITEM_WITHOUT_STATUS, ITEM_WITHOUT_DATE,
                ITEM_WITH_UNKNOWN_ROLE, ITEM_WITH_BAD_DATE);
    }

    @ParameterizedTest(name = "body #{index} is unavailable")
    @MethodSource("unusableBodies")
    @DisplayName("an unreadable, empty or incomplete body is unavailable")
    void unusableBodies(String body) {
        stubBody(200, body);

        assertThatThrownBy(() -> adapter.listUsers(TOKEN, FIRST_PAGE))
                .isInstanceOf(AdminUsersApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a dropped connection and a response slower than the timeout are unavailable")
    void transportErrorsAndTimeout() {
        stubFor(get(urlPathEqualTo(USERS)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.listUsers(TOKEN, FIRST_PAGE))
                .isInstanceOf(AdminUsersApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);

        stubFor(get(urlPathEqualTo(USERS)).willReturn(aResponse().withStatus(200).withFixedDelay(2_000)
                .withHeader("Content-Type", "application/json").withBody(PAGE_BODY)));
        assertThatThrownBy(() -> adapter.listUsers(TOKEN, FIRST_PAGE))
                .isInstanceOf(AdminUsersApiClient.ServiceUnavailableException.class);
    }
}
