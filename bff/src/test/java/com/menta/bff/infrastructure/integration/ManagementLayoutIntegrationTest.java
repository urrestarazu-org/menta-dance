package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The shared management layout and the Spanish 403 page (#354).
 *
 * <p>The 403 page is served by the servlet container's error dispatch, which MockMvc does not perform, so
 * those cases send a real HTTP request to the random port reusing the session created by a real login.
 * </p>
 */
@DisplayName("Management layout Integration Tests")
class ManagementLayoutIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    private Cookie logIn(String role) throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(TestAccessTokens.withRole(role)))));
        MvcResult login = mockMvc.perform(post("/login").param("email", "user@example.com")
                .param("password", "Password1").with(csrf())).andReturn();
        Cookie session = login.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    private ResponseEntity<String> browse(String path, Cookie session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.TEXT_HTML));
        if (session != null) {
            headers.add(HttpHeaders.COOKIE, "SESSION=" + session.getValue());
        }
        return http.exchange(URI.create("http://localhost:" + port + path), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("the management home renders the shared layout with a labelled navigation and the greeting")
    void homeRendersTheManagementLayout() throws Exception {
        Cookie session = logIn("INSTRUCTOR");

        String html = mockMvc.perform(get("/gestion").cookie(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("<title>Gestión - Menta Dance</title>")
                .contains("Saltar al contenido").contains("<header").contains("<footer");
        String main = mainOf(html);
        assertThat(main).contains("<nav aria-label=\"Gestión\"")
                .containsPattern("<a[^>]*href=\"/gestion\"[^>]*>\\s*Inicio\\s*</a>")
                .contains("user@example.com")
                .contains("<h1");
    }

    @Test
    @DisplayName("the home link is marked as the current page")
    void homeLinkIsCurrent() throws Exception {
        Cookie session = logIn("ADMIN");

        String main = mainOf(mockMvc.perform(get("/gestion").cookie(session))
                .andReturn().getResponse().getContentAsString());

        assertThat(main).containsPattern("<a[^>]*aria-current=\"page\"[^>]*>\\s*Inicio\\s*</a>");
    }

    @Test
    @DisplayName("no section is linked while none has a screen: the navigation never shows dead links")
    void noDeadLinksYet() throws Exception {
        Cookie session = logIn("ADMIN");

        String main = mainOf(mockMvc.perform(get("/gestion").cookie(session))
                .andReturn().getResponse().getContentAsString());

        assertThat(main).doesNotContain("/gestion/")
                .contains("Las secciones de gestión se irán habilitando");
    }

    @Test
    @DisplayName("a role without access gets the Spanish 403 page, not the generic error page")
    void instructorOnBillingGetsTheSpanish403() throws Exception {
        Cookie session = logIn("INSTRUCTOR");

        ResponseEntity<String> response = browse("/gestion/billing", session);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        String html = response.getBody();
        assertThat(html).contains("<title>Acceso denegado - Menta Dance</title>")
                .contains("No tenés acceso a esta página")
                .doesNotContain("No pudimos mostrar la página")
                .doesNotContain("Algo salió mal");
        assertThat(mainOf(html)).containsPattern("<a[^>]*href=\"/gestion\"[^>]*>\\s*Volver a gestión\\s*</a>");
    }

    @Test
    @DisplayName("a student refused on the management home is sent back to the dashboard")
    void studentOnGestionGetsTheSpanish403WithDashboardLink() throws Exception {
        Cookie session = logIn("STUDENT");

        ResponseEntity<String> response = browse("/gestion", session);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        String main = mainOf(response.getBody());
        assertThat(main).contains("No tenés acceso a esta página")
                .containsPattern("<a[^>]*href=\"/dashboard\"[^>]*>\\s*Volver a mi panel\\s*</a>")
                .doesNotContain("href=\"/gestion\"");
    }

    @Test
    @DisplayName("the 403 page leaks no upstream or stack details")
    void the403PageLeaksNothing() throws Exception {
        Cookie session = logIn("RECEPTIONIST");

        String html = browse("/gestion/billing", session).getBody();

        assertThat(html).doesNotContain("AccessDeniedException").doesNotContain("Access Denied")
                .doesNotContain("org.springframework").doesNotContain("Whitelabel")
                .doesNotContain("/api/v1").doesNotContain("at com.menta")
                .doesNotContain("trace");
    }
}
