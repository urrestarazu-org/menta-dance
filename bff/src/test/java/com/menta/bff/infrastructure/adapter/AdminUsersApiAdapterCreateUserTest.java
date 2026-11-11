package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.CreateUserCommand;
import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.port.out.AdminUsersApiClient;
import com.menta.bff.infrastructure.config.AuthProperties;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("AdminUsersApiAdapter.createUser")
class AdminUsersApiAdapterCreateUserTest {

    private static final String USERS = "/api/v1/admin/users";
    private static final String TOKEN = "access-token-abc";
    private static final CreateUserCommand COMMAND =
            new CreateUserCommand("recepcion@example.com", "Ada", "Lovelace", UserRole.RECEPTIONIST);
    private static final String CREATED_BODY =
            """
            {"id": "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f", "email": "recepcion@example.com", "firstName": "Ada",
             "lastName": "Lovelace", "role": "RECEPTIONIST", "status": "PENDING_ACTIVATION", "demo": false,
             "createdAt": "2026-11-10T09:30:00"}
            """;

    private AdminUsersApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        AuthProperties properties = new AuthProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(Duration.ofSeconds(1));
        adapter = new AdminUsersApiAdapter(WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(), properties);
    }

    private static void stubReply(int status, String contentType, String body) {
        stubFor(post(urlPathEqualTo(USERS)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", contentType).withBody(body)));
    }

    private static void stubProblem(int status, String code) {
        stubReply(status, "application/problem+json",
                "{\"type\":\"https://menta.dance/problems/x\",\"status\":%d,\"detail\":\"secret detail\",\"code\":\"%s\"}"
                        .formatted(status, code));
    }

    @Test
    @DisplayName("posts the four fields as JSON with the bearer token and accepts a 201")
    void createsTheAccount() {
        stubReply(201, "application/json", CREATED_BODY);

        assertThatCode(() -> adapter.createUser(TOKEN, COMMAND)).doesNotThrowAnyException();

        verify(postRequestedFor(urlPathEqualTo(USERS))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("{\"email\": \"recepcion@example.com\", \"firstName\": \"Ada\","
                        + " \"lastName\": \"Lovelace\", \"role\": \"RECEPTIONIST\"}", false, false)));
    }

    @Test
    @DisplayName("a 201 is a success even when its body is empty or unreadable: the account already exists")
    void unreadableCreatedBodyIsStillSuccess() {
        stubReply(201, "application/json", "<not json>");

        assertThatCode(() -> adapter.createUser(TOKEN, COMMAND)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a 409 EMAIL_ALREADY_REGISTERED is the typed email-taken failure, with nothing of the reply in it")
    void emailAlreadyRegistered() {
        stubProblem(409, "EMAIL_ALREADY_REGISTERED");

        assertThatThrownBy(() -> adapter.createUser(TOKEN, COMMAND))
                .isInstanceOf(AdminUsersApiClient.EmailAlreadyRegisteredException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN)
                .hasMessageNotContaining("recepcion@example.com");
    }

    static Stream<String> otherConflicts() {
        return Stream.of(
                "{\"code\":\"SOMETHING_ELSE\"}",
                "{\"detail\":\"no code\"}",
                "<html>not json</html>",
                "");
    }

    @ParameterizedTest(name = "409 body #{index} is unavailable")
    @MethodSource("otherConflicts")
    @DisplayName("a 409 with another code, without a code or with an unreadable body is unavailable")
    void otherConflictsAreUnavailable(String body) {
        stubReply(409, "application/problem+json", body);

        assertThatThrownBy(() -> adapter.createUser(TOKEN, COMMAND))
                .isInstanceOf(AdminUsersApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a 400 is an invalid request, whatever its body says")
    void badRequest() {
        stubProblem(400, "INVALID_REQUEST");

        assertThatThrownBy(() -> adapter.createUser(TOKEN, COMMAND))
                .isInstanceOf(AdminUsersApiClient.InvalidRequestException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest(name = "status {0} is unavailable")
    @ValueSource(ints = {200, 204, 401, 403, 404, 429, 500, 503})
    @DisplayName("any other status is unavailable, never a leak of the token or the body")
    void otherStatuses(int status) {
        stubReply(status, "application/problem+json", "{\"detail\":\"secret detail\"}");

        assertThatThrownBy(() -> adapter.createUser(TOKEN, COMMAND))
                .isInstanceOf(AdminUsersApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    @DisplayName("a dropped connection and a response slower than the timeout are unavailable")
    void transportErrorsAndTimeout() {
        stubFor(post(urlPathEqualTo(USERS)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.createUser(TOKEN, COMMAND))
                .isInstanceOf(AdminUsersApiClient.ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);

        stubFor(post(urlPathEqualTo(USERS)).willReturn(aResponse().withStatus(201).withFixedDelay(2_000)
                .withHeader("Content-Type", "application/json").withBody(CREATED_BODY)));
        assertThatThrownBy(() -> adapter.createUser(TOKEN, COMMAND))
                .isInstanceOf(AdminUsersApiClient.ServiceUnavailableException.class);
    }
}
