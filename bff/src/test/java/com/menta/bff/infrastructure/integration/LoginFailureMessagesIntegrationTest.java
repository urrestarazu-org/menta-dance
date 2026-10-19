package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Posts the login form against a stubbed Auth API and reads the message the login page
 * renders afterwards (#479), following the redirect with the same browser session.
 */
@DisplayName("Login failure messages Integration Tests")
class LoginFailureMessagesIntegrationTest extends BaseIntegrationTest {

    private static final String ERROR_URL = "/login?error=true";

    private static ResponseDefinitionBuilder problem(int status, String code) {
        return aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"code\":\"" + code + "\",\"status\":" + status + "}");
    }

    private String loginPageAfterFailure(ResponseDefinitionBuilder authApiAnswer) throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(authApiAnswer));

        MvcResult failure = mockMvc.perform(post("/login")
                        .param("email", "user@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(ERROR_URL))
                .andReturn();
        Cookie session = failure.getResponse().getCookie("SESSION");
        assertThat(session).as("the failure travels in the browser session").isNotNull();

        return mockMvc.perform(get(ERROR_URL).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @Test
    @DisplayName("credenciales inválidas muestran el mensaje genérico")
    void invalidCredentials_showTheGenericMessage() throws Exception {
        String html = loginPageAfterFailure(problem(401, "INVALID_CREDENTIALS"));

        assertThat(html).contains("Email o contraseña inválidos").doesNotContain("reenviar-activacion");
    }

    @Test
    @DisplayName("cuenta bloqueada (423) muestra el mensaje de cuenta bloqueada")
    void lockedAccount_showsTheLockedMessage() throws Exception {
        String html = loginPageAfterFailure(problem(423, "USER_LOCKED"));

        assertThat(html)
                .contains("Tu cuenta está bloqueada")
                .doesNotContain("USER_LOCKED")
                .doesNotContain("Email o contraseña inválidos");
    }

    @Test
    @DisplayName("demasiados intentos (429) muestra el tiempo de espera en minutos")
    void rateLimited_showsTheWait() throws Exception {
        String html = loginPageAfterFailure(problem(429, "LOGIN_RATE_LIMITED").withHeader("Retry-After", "120"));

        assertThat(html)
                .contains("Demasiados intentos de inicio de sesión. Probá de nuevo en 2 minutos.")
                .doesNotContain("LOGIN_RATE_LIMITED");
    }

    @Test
    @DisplayName("demasiados intentos (429) sin Retry-After muestra un mensaje genérico de espera")
    void rateLimitedWithoutRetryAfter_showsAGenericWait() throws Exception {
        String html = loginPageAfterFailure(problem(429, "LOGIN_RATE_LIMITED"));

        assertThat(html).contains("Demasiados intentos de inicio de sesión. Probá de nuevo en unos minutos.");
    }

    @Test
    @DisplayName("cuenta sin activar (403) muestra el mensaje y el enlace para reenviar la activación")
    void notActivated_showsTheMessageAndTheResendLink() throws Exception {
        String html = loginPageAfterFailure(problem(403, "ACCOUNT_NOT_ACTIVATED"));

        assertThat(html)
                .contains("Tu cuenta todavía no está activada")
                .contains("href=\"/reenviar-activacion\"")
                .doesNotContain("ACCOUNT_NOT_ACTIVATED");
    }

    @Test
    @DisplayName("Auth API caída (503) o respuesta imprevista (418) muestra servicio no disponible")
    void unavailableOrUnexpected_showsServiceUnavailable() throws Exception {
        assertThat(loginPageAfterFailure(problem(503, "UNAVAILABLE")))
                .contains("El servicio no está disponible por el momento");
        assertThat(loginPageAfterFailure(aResponse().withStatus(418).withBody("teapot")))
                .contains("El servicio no está disponible por el momento");
    }

    @Test
    @DisplayName("el mensaje se muestra una sola vez")
    void theMessageIsShownOnce() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(problem(423, "USER_LOCKED")));
        Cookie session = mockMvc.perform(post("/login")
                        .param("email", "user@example.com")
                        .param("password", "password123")
                        .with(csrf()))
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        mockMvc.perform(get(ERROR_URL).cookie(session)).andExpect(status().isOk());

        String second = mockMvc.perform(get(ERROR_URL).cookie(session))
                .andReturn().getResponse().getContentAsString();

        assertThat(second).contains("Email o contraseña inválidos").doesNotContain("Tu cuenta está bloqueada");
    }
}
