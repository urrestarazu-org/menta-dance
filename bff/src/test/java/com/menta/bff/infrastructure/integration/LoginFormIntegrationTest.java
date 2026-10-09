package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Posts the login form using the field names the rendered {@code /login} page
 * actually declares, instead of names the test author assumes.
 *
 * <p>The other login tests hard-code the parameter names, so a mismatch between
 * {@code login.html} and the {@code formLogin()} configuration passes them and
 * only fails in a browser (#490). Deriving the names from the HTML makes that
 * mismatch break the build.
 * </p>
 */
@DisplayName("Login form Integration Tests")
class LoginFormIntegrationTest extends BaseIntegrationTest {

    private static final Pattern EMAIL_FIELD = Pattern.compile("<input[^>]*type=\"email\"[^>]*name=\"([^\"]+)\"");
    private static final Pattern PASSWORD_FIELD =
            Pattern.compile("<input[^>]*type=\"password\"[^>]*name=\"([^\"]+)\"");

    @Test
    @DisplayName("enviar el formulario renderizado de /login inicia sesión y redirige a /dashboard")
    void submittingTheRenderedForm_logsIn() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Refresh-Token", "refresh_token_form")
                        .withBody(
                                """
                                {
                                  "access_token": "%s",
                                  "token_type": "Bearer",
                                  "expires_in": 3600
                                }
                                """.formatted(TestAccessTokens.withRole("STUDENT")))));

        String html = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String emailField = fieldName(EMAIL_FIELD, html);
        String passwordField = fieldName(PASSWORD_FIELD, html);

        mockMvc.perform(post("/login")
                        .param(emailField, "user@example.com")
                        .param(passwordField, "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        WIRE_MOCK_SERVER.verify(postRequestedFor(urlEqualTo("/api/v1/auth/login"))
                .withRequestBody(matchingJsonPath("$.email", equalTo("user@example.com")))
                .withRequestBody(matchingJsonPath("$.password", equalTo("password123"))));
    }

    private static String fieldName(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        assertThat(matcher.find())
                .withFailMessage("The rendered /login page has no input matching %s", pattern)
                .isTrue();
        return matcher.group(1);
    }
}
