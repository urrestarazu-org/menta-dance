package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The "Gestión" link to {@code /gestion} (#828): only the roles allowed on {@code GET /gestion} see it, in
 * the site header (desktop and mobile menus), the footer and the dashboard. A student and an anonymous
 * visitor do not get it anywhere in the HTML. The authorization itself stays in the security config.
 */
@DisplayName("Management link Integration Tests")
class StaffManagementLinkIntegrationTest extends BaseIntegrationTest {

    private static final String LINK = "href=\"/gestion\"";
    private static final Pattern LINK_TEXT = Pattern.compile("href=\"/gestion\"[^>]*>\\s*Gestión\\s*</a>");
    private static final List<String> PAGES = List.of("/", "/contacto", "/dashboard");

    private Cookie logIn(String role) throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo("/api/v1/catalog/courses")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"courses\": []}")));
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

    private String page(String path, Cookie session) throws Exception {
        var request = get(path);
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static String between(String html, String open, String close) {
        return html.substring(html.indexOf(open), html.indexOf(close));
    }

    private static int count(String text, String token) {
        return text.split(Pattern.quote(token), -1).length - 1;
    }

    @ParameterizedTest(name = "{0} sees Gestión in the header (both menus) and the footer")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR", "RECEPTIONIST"})
    @DisplayName("the staff roles get the link to /gestion in the shared header and footer")
    void staffSeesTheLink(String role) throws Exception {
        Cookie session = logIn(role);

        for (String path : PAGES) {
            String html = page(path, session);
            String header = between(html, "<header", "</header>");

            assertThat(count(header, LINK)).as("%s header (desktop + mobile menu)", path).isEqualTo(2);
            assertThat(LINK_TEXT.matcher(header).results().count()).as("%s header text", path).isEqualTo(2);
            assertThat(header.indexOf(LINK)).as("%s: before Ir a mi panel", path)
                    .isLessThan(header.indexOf("Ir a mi panel"));
            assertThat(count(between(html, "<footer", "</footer>"), LINK)).as("%s footer", path).isEqualTo(1);
        }
    }

    @ParameterizedTest(name = "{0} sees the Gestión action on the dashboard")
    @ValueSource(strings = {"ADMIN", "INSTRUCTOR", "RECEPTIONIST"})
    @DisplayName("the staff roles get a Gestión button in the dashboard content")
    void staffSeesTheDashboardAction(String role) throws Exception {
        String main = HtmlSections.mainOf(page("/dashboard", logIn(role)));

        assertThat(main).containsPattern("class=\"btn btn-primary\"[^>]*href=\"/gestion\"[^>]*>\\s*Gestión\\s*</a>");
        assertThat(count(main, LINK)).isEqualTo(1);
    }

    @Test
    @DisplayName("a student never gets the link, not even hidden, on any page")
    void studentDoesNotSeeTheLink() throws Exception {
        Cookie session = logIn("STUDENT");

        for (String path : PAGES) {
            assertThat(page(path, session)).as(path).doesNotContain("/gestion");
        }
    }

    @Test
    @DisplayName("an anonymous visitor never gets the link, not even hidden, on the public pages")
    void anonymousDoesNotSeeTheLink() throws Exception {
        logIn("STUDENT");

        for (String path : List.of("/", "/contacto")) {
            assertThat(page(path, null)).as(path).doesNotContain("/gestion");
        }
    }
}
