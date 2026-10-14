package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * Covers the account-related pages migrated to the shared layout (#536, slice 3a):
 * each one renders the skip link, the main landmark and the stylesheet while
 * keeping its own form, action and links.
 */
@DisplayName("Account views layout Integration Tests")
class AccountViewsLayoutIntegrationTest extends BaseIntegrationTest {

    private static final String TOKEN = "layout-token-abc";

    @ParameterizedTest(name = "{0} renderiza el layout común")
    @ValueSource(strings = {
        "/reenviar-activacion",
        "/recuperar-contrasena",
        "/restablecer-contrasena/" + TOKEN,
        "/registro/pendiente",
        "/recuperar-contrasena/enviado",
        "/cuenta-activada"})
    @DisplayName("las páginas de cuenta incluyen skip link, un único main, header, footer y stylesheet")
    void accountPagesRenderTheCommonLayout(String path) throws Exception {
        assertCommonLayout(render(path));
    }

    @Test
    @DisplayName("/activar/{token} usa el layout y conserva el formulario con el token")
    void activateKeepsItsForm() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo("/api/v1/auth/activate/" + TOKEN))
                .willReturn(aResponse().withStatus(204)));

        String html = render("/activar/" + TOKEN);

        assertCommonLayout(html);
        assertThat(html)
                .contains("action=\"/activar\"")
                .contains("name=\"_csrf\"")
                .contains("name=\"token\"")
                .contains("value=\"" + TOKEN + "\"")
                .contains("<title>Activar cuenta - Menta Dance</title>");
    }

    @Test
    @DisplayName("/activar/{token} con enlace inválido muestra un alert con role=alert y los enlaces de salida")
    void activateInvalidTokenShowsAlert() throws Exception {
        WIRE_MOCK_SERVER.stubFor(WireMock.get(urlEqualTo("/api/v1/auth/activate/" + TOKEN))
                .willReturn(aResponse().withStatus(400)));

        String html = mockMvc.perform(get("/activar/" + TOKEN))
                .andReturn().getResponse().getContentAsString();

        assertCommonLayout(html);
        assertThat(html)
                .contains("role=\"alert\"")
                .contains("El enlace de activación es inválido")
                .contains("href=\"/reenviar-activacion\"")
                .contains("href=\"/login\"")
                .doesNotContain("action=\"/activar\"");
    }

    @Test
    @DisplayName("/reenviar-activacion conserva formulario, CSRF y campo email")
    void resendKeepsItsForm() throws Exception {
        assertThat(render("/reenviar-activacion"))
                .contains("action=\"/reenviar-activacion\"")
                .contains("name=\"_csrf\"")
                .contains("name=\"email\"")
                .contains("href=\"/login\"")
                .contains("<title>Reenviar activación - Menta Dance</title>");
    }

    @Test
    @DisplayName("/recuperar-contrasena conserva formulario, CSRF y campo email")
    void requestResetKeepsItsForm() throws Exception {
        assertThat(render("/recuperar-contrasena"))
                .contains("action=\"/recuperar-contrasena\"")
                .contains("name=\"_csrf\"")
                .contains("name=\"email\"")
                .contains("href=\"/login\"")
                .contains("<title>Recuperar contraseña - Menta Dance</title>");
    }

    @Test
    @DisplayName("/restablecer-contrasena/{token} conserva formulario, token oculto y ambos campos de contraseña")
    void resetKeepsItsForm() throws Exception {
        assertThat(render("/restablecer-contrasena/" + TOKEN))
                .contains("action=\"/restablecer-contrasena\"")
                .contains("name=\"_csrf\"")
                .contains("name=\"token\"")
                .contains("value=\"" + TOKEN + "\"")
                .contains("name=\"password\"")
                .contains("name=\"passwordConfirmation\"")
                .contains("Al menos 8 caracteres, una mayúscula y un número.")
                .contains("<title>Nueva contraseña - Menta Dance</title>");
    }

    @Test
    @DisplayName("las páginas informativas conservan su texto y enlaces")
    void noticePagesKeepTheirLinks() throws Exception {
        assertThat(render("/registro/pendiente"))
                .contains("<h1")
                .contains("Revisá tu correo")
                .contains("href=\"/reenviar-activacion\"")
                .contains("href=\"/login\"");
        assertThat(render("/recuperar-contrasena/enviado"))
                .contains("El enlace vence en una hora.")
                .contains("href=\"/recuperar-contrasena\"")
                .contains("href=\"/login\"");
        assertThat(render("/cuenta-activada"))
                .contains("Tu cuenta fue activada")
                .contains("href=\"/login\"");
    }

    @Test
    @DisplayName("/error renderiza el layout, muestra status y no filtra detalles internos")
    void errorPageRendersLayoutWithoutLeaks() throws Exception {
        String html = mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION,
                                new IllegalStateException("secret-internal-detail")))
                .andReturn().getResponse().getContentAsString();

        assertCommonLayout(html);
        assertThat(html)
                .contains("<title>Error - Menta Dance</title>")
                .contains("No encontramos la página que buscás.")
                .contains("Código: 404")
                .contains("role=\"alert\"")
                .contains("Volver al inicio")
                .doesNotContain("Go to Home")
                .doesNotContain("Not Found")
                .contains("href=\"/\"")
                .doesNotContain("secret-internal-detail")
                .doesNotContain("IllegalStateException")
                .doesNotContain("at com.menta");
    }

    @Test
    @DisplayName("/registro con errores marca cada campo aria-invalid y lo vincula a su mensaje con aria-describedby")
    void registrationErrorsAreLinkedToTheirFields() throws Exception {
        String html = mockMvc.perform(post("/registro").with(csrf())
                        .param("email", "not-an-email").param("password", "x"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .containsPattern(
                        "<input[^>]*id=\"email\"[^>]*aria-invalid=\"true\"[^>]*aria-describedby=\"email-help\"")
                .containsPattern("<input[^>]*id=\"password\"[^>]*aria-invalid=\"true\""
                        + "[^>]*aria-describedby=\"password-help\"")
                .contains("id=\"email-help\"")
                .contains("id=\"password-help\"");
    }

    private static void assertCommonLayout(String html) {
        assertThat(html)
                .contains("href=\"#main\"")
                .contains("Saltar al contenido")
                .contains("<header")
                .contains("<footer")
                .containsPattern("<link[^>]*rel=\"stylesheet\"[^>]*href=\"/css/app-[0-9a-f]{32}\\.css\"")
                .contains("lang=\"es\"");
        assertThat(html.split("<main", -1)).as("single main landmark").hasSize(2);
        assertThat(html).contains("<main id=\"main\" tabindex=\"-1\"");
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
