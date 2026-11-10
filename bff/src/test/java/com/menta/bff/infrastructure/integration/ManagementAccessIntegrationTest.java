package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Who may open what under {@code /gestion/**} (#354). The BFF only avoids showing useless screens: the
 * API decides the real authorization. Each case logs in for real against the stubbed Auth API and
 * reuses the session cookie.
 *
 * <p>There are no business screens behind the prefixes yet, so "allowed" is observed as 404 (the security
 * chain let the request through and nothing handles it) and "forbidden" as 403.
 * </p>
 */
@DisplayName("Management area access Integration Tests")
class ManagementAccessIntegrationTest extends BaseIntegrationTest {

    private static final String ADMIN = "ADMIN";
    private static final String INSTRUCTOR = "INSTRUCTOR";
    private static final String RECEPTIONIST = "RECEPTIONIST";
    private static final String STUDENT = "STUDENT";

    private Cookie logIn(String role) throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(TestAccessTokens.withRole(role)))));
        Cookie session = mockMvc.perform(post("/login").param("email", "user@example.com")
                        .param("password", "Password1").with(csrf()))
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    @ParameterizedTest(name = "{0} opens the management home")
    @ValueSource(strings = {ADMIN, INSTRUCTOR, RECEPTIONIST})
    @DisplayName("every staff role opens GET /gestion")
    void staffOpenTheHome(String role) throws Exception {
        Cookie session = logIn(role);

        mockMvc.perform(get("/gestion").cookie(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a student is refused the management home")
    void studentRefusedTheHome() throws Exception {
        Cookie session = logIn(STUDENT);

        mockMvc.perform(get("/gestion").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page")
    void anonymousGoesToLogin() throws Exception {
        mockMvc.perform(get("/gestion"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/gestion/billing"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    static Stream<Arguments> prefixMatrix() {
        return Stream.of(
                // content and sessions: administrator and instructor
                Arguments.of("/gestion/contenido", ADMIN, true),
                Arguments.of("/gestion/contenido", INSTRUCTOR, true),
                Arguments.of("/gestion/contenido", RECEPTIONIST, false),
                Arguments.of("/gestion/contenido", STUDENT, false),
                Arguments.of("/gestion/contenido/cursos/1", INSTRUCTOR, true),
                Arguments.of("/gestion/contenido/cursos/1", RECEPTIONIST, false),
                Arguments.of("/gestion/sesiones", ADMIN, true),
                Arguments.of("/gestion/sesiones", INSTRUCTOR, true),
                Arguments.of("/gestion/sesiones", RECEPTIONIST, false),
                Arguments.of("/gestion/sesiones", STUDENT, false),
                // reception: receptionist and administrator
                Arguments.of("/gestion/recepcion", RECEPTIONIST, true),
                Arguments.of("/gestion/recepcion", ADMIN, true),
                Arguments.of("/gestion/recepcion", INSTRUCTOR, false),
                Arguments.of("/gestion/recepcion", STUDENT, false),
                Arguments.of("/gestion/recepcion/asistencia", RECEPTIONIST, true),
                Arguments.of("/gestion/recepcion/asistencia", INSTRUCTOR, false),
                // billing and devices: administrator only
                Arguments.of("/gestion/billing", ADMIN, true),
                Arguments.of("/gestion/billing", INSTRUCTOR, false),
                Arguments.of("/gestion/billing", RECEPTIONIST, false),
                Arguments.of("/gestion/billing", STUDENT, false),
                Arguments.of("/gestion/billing/pagos/1", RECEPTIONIST, false),
                Arguments.of("/gestion/dispositivos", ADMIN, true),
                Arguments.of("/gestion/dispositivos", INSTRUCTOR, false),
                Arguments.of("/gestion/dispositivos", RECEPTIONIST, false),
                Arguments.of("/gestion/dispositivos", STUDENT, false),
                // any prefix nobody planned stays closed to everyone but the administrator
                Arguments.of("/gestion/otra-cosa", ADMIN, true),
                Arguments.of("/gestion/otra-cosa", INSTRUCTOR, false),
                Arguments.of("/gestion/otra-cosa", RECEPTIONIST, false),
                Arguments.of("/gestion/otra-cosa", STUDENT, false),
                Arguments.of("/gestion/otra/cosa/mas", RECEPTIONIST, false));
    }

    @ParameterizedTest(name = "{0} as {1}: allowed={2}")
    @MethodSource("prefixMatrix")
    @DisplayName("each /gestion prefix admits only its roles (allowed = 404, nothing handles it; refused = 403)")
    void prefixMatrixIsEnforced(String path, String role, boolean allowed) throws Exception {
        Cookie session = logIn(role);

        mockMvc.perform(get(path).cookie(session))
                .andExpect(allowed ? status().isNotFound() : status().isForbidden());
    }

    @ParameterizedTest(name = "{0} is refused for POST /gestion/billing/anything")
    @ValueSource(strings = {INSTRUCTOR, RECEPTIONIST, STUDENT})
    @DisplayName("the prefix rules apply to every verb, not only GET")
    void prefixRulesCoverEveryVerb(String role) throws Exception {
        Cookie session = logIn(role);

        mockMvc.perform(post("/gestion/billing/anything").cookie(session).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
