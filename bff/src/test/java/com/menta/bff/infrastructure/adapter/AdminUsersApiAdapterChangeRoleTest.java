package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("AdminUsersApiAdapter.changeRole")
class AdminUsersApiAdapterChangeRoleTest {

    private static final String USER_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String ROLE_PATH = "/api/v1/admin/users/" + USER_ID + "/role";
    private static final String TOKEN = "access-token-abc";
    private static final String UPDATED_BODY =
            """
            {"id": "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "email": "ada@example.com", "firstName": "Ada",
             "lastName": "Lovelace", "role": "INSTRUCTOR", "status": "ACTIVE", "demo": false,
             "createdAt": "2026-11-10T09:30:00"}
            """;

    private AdminUsersApiAdapter adapter;

    /** Generous on purpose: the first request of a cold JVM on a slow CI runner can take over a second. */
    private static final Duration GENEROUS_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        adapter = adapterWith(wireMock, GENEROUS_TIMEOUT);
    }

    private static AdminUsersApiAdapter adapterWith(WireMockRuntimeInfo wireMock, Duration timeout) {
        AuthProperties properties = new AuthProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(timeout);
        return new AdminUsersApiAdapter(WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(), properties);
    }

    private static void stubReply(int status, String contentType, String body) {
        stubFor(patch(urlPathMatching("/api/v1/admin/users/.*")).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", contentType).withBody(body)));
    }

    private static void stubProblem(int status, String code) {
        stubReply(status, "application/problem+json",
                "{\"type\":\"https://menta.dance/problems/x\",\"status\":%d,\"detail\":\"secret detail\",\"code\":\"%s\"}"
                        .formatted(status, code));
    }

    @Test
    @DisplayName("patches the role path with the bearer token and a JSON body, and returns the updated account")
    void changesTheRole() {
        stubReply(200, "application/json", UPDATED_BODY);

        UserSummary updated = adapter.changeRole(TOKEN, USER_ID, UserRole.INSTRUCTOR);

        assertThat(updated).isEqualTo(new UserSummary(USER_ID, "ada@example.com", "Ada", "Lovelace",
                UserRole.INSTRUCTOR, UserStatus.ACTIVE, LocalDateTime.parse("2026-11-10T09:30:00")));
        verify(patchRequestedFor(urlEqualTo(ROLE_PATH))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("{\"role\": \"INSTRUCTOR\"}", false, false)));
    }

    @Test
    @DisplayName("the user id travels as an encoded path segment: it cannot add segments or a query")
    void userIdIsEncodedAsOneSegment() {
        stubReply(200, "application/json", UPDATED_BODY);

        adapter.changeRole(TOKEN, "a/../b?x=1", UserRole.ADMIN);

        verify(patchRequestedFor(urlEqualTo("/api/v1/admin/users/a%2F..%2Fb%3Fx%3D1/role")));
    }

    @Test
    @DisplayName("a 404 USER_NOT_FOUND is the typed not-found failure, with nothing of the reply in it")
    void userNotFound() {
        stubProblem(404, "USER_NOT_FOUND");

        assertThatThrownBy(() -> adapter.changeRole(TOKEN, USER_ID, UserRole.ADMIN))
                .isInstanceOf(AdminUsersApiClient.UserNotFoundException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    @DisplayName("a 409 LAST_ADMIN_CANNOT_BE_DEMOTED is the typed last-admin failure")
    void lastAdmin() {
        stubProblem(409, "LAST_ADMIN_CANNOT_BE_DEMOTED");

        assertThatThrownBy(() -> adapter.changeRole(TOKEN, USER_ID, UserRole.STUDENT))
                .isInstanceOf(AdminUsersApiClient.LastAdminCannotBeDemotedException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN);
    }

    static Stream<Arguments> otherProblems() {
        return Stream.of(404, 409).flatMap(status -> Stream.of(
                "{\"code\":\"SOMETHING_ELSE\"}",
                "{\"code\":\"" + (status == 404 ? "LAST_ADMIN_CANNOT_BE_DEMOTED" : "USER_NOT_FOUND") + "\"}",
                "{\"detail\":\"no code\"}",
                "<html>not json</html>",
                "").map(body -> Arguments.of(status, body)));
    }

    @ParameterizedTest(name = "[{index}] {0} with body {1} is unavailable")
    @MethodSource("otherProblems")
    @DisplayName("a 404 or 409 with another code, without a code or with an unreadable body is unavailable")
    void otherProblemsAreUnavailable(int status, String body) {
        stubReply(status, "application/problem+json", body);

        assertThatThrownBy(() -> adapter.changeRole(TOKEN, USER_ID, UserRole.ADMIN))
                .isInstanceOf(AdminUsersApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a 400 is an invalid request, whatever its body says")
    void badRequest() {
        stubProblem(400, "INVALID_REQUEST");

        assertThatThrownBy(() -> adapter.changeRole(TOKEN, USER_ID, UserRole.ADMIN))
                .isInstanceOf(AdminUsersApiClient.InvalidRequestException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest(name = "status {0} is unavailable")
    @ValueSource(ints = {201, 204, 401, 403, 429, 500, 503})
    @DisplayName("any other status is unavailable, never a leak of the token or the body")
    void otherStatuses(int status) {
        stubReply(status, "application/problem+json", "{\"detail\":\"secret detail\"}");

        assertThatThrownBy(() -> adapter.changeRole(TOKEN, USER_ID, UserRole.ADMIN))
                .isInstanceOf(AdminUsersApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest(name = "[{index}] a 200 with {0} is unavailable")
    @ValueSource(strings = {
        "<not json>",
        "",
        "{\"id\": \"x\", \"email\": \"ada@example.com\"}",
        "{\"id\": \"x\", \"email\": \"a@example.com\", \"role\": \"WIZARD\", \"status\": \"ACTIVE\","
                + " \"createdAt\": \"2026-11-10T09:30:00\"}"})
    @DisplayName("a 200 whose body is not a complete account is unavailable: repeating the change is harmless")
    void unusableBodyIsUnavailable(String body) {
        stubReply(200, "application/json", body);

        assertThatThrownBy(() -> adapter.changeRole(TOKEN, USER_ID, UserRole.ADMIN))
                .isInstanceOf(AdminUsersApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a dropped connection and a response slower than the timeout are unavailable")
    void transportErrorsAndTimeout(WireMockRuntimeInfo wireMock) {
        stubFor(patch(urlEqualTo(ROLE_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.changeRole(TOKEN, USER_ID, UserRole.ADMIN))
                .isInstanceOf(AdminUsersApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);

        AdminUsersApiAdapter impatient = adapterWith(wireMock, SHORT_TIMEOUT);
        stubFor(patch(urlEqualTo(ROLE_PATH)).willReturn(aResponse().withStatus(200).withFixedDelay(2_000)
                .withHeader("Content-Type", "application/json").withBody(UPDATED_BODY)));
        assertThatThrownBy(() -> impatient.changeRole(TOKEN, USER_ID, UserRole.ADMIN))
                .isInstanceOf(AdminUsersApiClient.ServiceUnavailableException.class);
    }
}
