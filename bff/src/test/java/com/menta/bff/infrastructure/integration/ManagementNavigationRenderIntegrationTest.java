package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.menta.bff.infrastructure.integration.HtmlSections.mainOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.menta.bff.application.management.ManagementNavigation;
import com.menta.bff.application.management.ManagementSection;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The layout asks the {@code managementNavigation} bean for the sections of the signed-in roles and
 * renders what it returns (#354). The catalog is stubbed with enabled sections, which the production
 * list does not have yet.
 */
@DisplayName("Management navigation rendering Integration Tests")
class ManagementNavigationRenderIntegrationTest extends BaseIntegrationTest {

    @MockitoBean(name = "managementNavigation")
    private ManagementNavigation navigation;

    private Cookie logIn(String role) throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(TestAccessTokens.withRole(role)))));
        return mockMvc.perform(post("/login").param("email", "user@example.com")
                .param("password", "Password1").with(csrf())).andReturn().getResponse().getCookie("SESSION");
    }

    @Test
    @DisplayName("renders a link per section returned for the user's authorities, after Inicio")
    void rendersTheSectionsOfTheUsersRoles() throws Exception {
        when(navigation.sectionsFor(argThat(roles -> roles != null && roles.contains("ROLE_INSTRUCTOR"))))
                .thenReturn(List.of(
                        new ManagementSection("content", "Contenido", "/gestion/contenido",
                                Set.of("INSTRUCTOR"), true),
                        new ManagementSection("sessions", "Sesiones", "/gestion/sesiones",
                                Set.of("INSTRUCTOR"), true)));
        Cookie session = logIn("INSTRUCTOR");

        String main = mainOf(mockMvc.perform(get("/gestion").cookie(session))
                .andReturn().getResponse().getContentAsString());

        assertThat(main).containsPattern("<a[^>]*href=\"/gestion/contenido\"[^>]*>\\s*Contenido\\s*</a>")
                .containsPattern("<a[^>]*href=\"/gestion/sesiones\"[^>]*>\\s*Sesiones\\s*</a>");
        assertThat(main.indexOf("Inicio")).isLessThan(main.indexOf("Contenido"));
        assertThat(main.indexOf("Contenido")).isLessThan(main.indexOf("Sesiones"));
        assertThat(main).doesNotContainPattern("aria-current=\"page\"[^>]*>\\s*Contenido");
    }
}
