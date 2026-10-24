package com.menta.bff.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Covers the shared Thymeleaf layout (#536, slice 2): every adopting page gets
 * the skip link, the main landmark, the compiled stylesheet and the brand
 * header/footer, without losing its own form.
 */
@DisplayName("Page layout Integration Tests")
class PageLayoutIntegrationTest extends BaseIntegrationTest {

    @ParameterizedTest(name = "{0} renderiza el layout común")
    @ValueSource(strings = {"/login", "/registro"})
    @DisplayName("las páginas adoptantes incluyen skip link, landmark main, header, footer y stylesheet")
    void adoptingPagesRenderTheCommonLayout(String path) throws Exception {
        String html = render(path);

        assertThat(html)
                .contains("href=\"#main\"")
                .contains("Saltar al contenido")
                .contains("<main id=\"main\"")
                .contains("<header")
                .contains("<footer")
                .containsPattern("<link[^>]*rel=\"stylesheet\"[^>]*href=\"/css/app-[0-9a-f]{32}\\.css\"")
                .contains("lang=\"es\"");
    }

    @Test
    @DisplayName("/login conserva el formulario, el CSRF y los campos email y password")
    void loginKeepsItsForm() throws Exception {
        String html = render("/login");

        assertThat(html)
                .contains("action=\"/login\"")
                .contains("name=\"_csrf\"")
                .contains("name=\"email\"")
                .contains("name=\"password\"")
                .contains("href=\"/recuperar-contrasena\"")
                .contains("<title>Iniciar Sesión - Menta Dance</title>");
    }

    @Test
    @DisplayName("/login invita a crear una cuenta con un enlace a /registro dentro del contenido")
    void loginLinksToRegistration() throws Exception {
        String main = HtmlSections.mainOf(render("/login"));

        assertThat(main)
                .contains("¿No tenés una cuenta?")
                .containsPattern("<a[^>]*href=\"/registro\"[^>]*>Registrate aquí</a>");
    }

    @Test
    @DisplayName("/registro conserva el formulario, el CSRF y los campos de datos personales, email y password")
    void registerKeepsItsForm() throws Exception {
        String html = render("/registro");

        assertThat(html)
                .contains("action=\"/registro\"")
                .contains("name=\"_csrf\"")
                .contains("name=\"firstName\"")
                .contains("name=\"lastName\"")
                .contains("name=\"email\"")
                .contains("name=\"phone\"")
                .contains("name=\"birthDate\"")
                .contains("name=\"password\"")
                .contains("href=\"/reenviar-activacion\"")
                .contains("<title>Crear cuenta - Menta Dance</title>");
    }

    @ParameterizedTest(name = "{0} usa la tarjeta de dos paneles")
    @ValueSource(strings = {"/login", "/registro", "/recuperar-contrasena", "/reenviar-activacion"})
    @DisplayName("las pantallas de cuenta muestran un panel de marca con logo junto al formulario")
    void accountPagesUseTheTwoPanelCard(String path) throws Exception {
        String html = render(path);

        assertThat(html).contains("auth-card").contains("auth-aside");
        String aside = html.substring(html.indexOf("auth-aside"), html.indexOf("auth-body"));
        assertThat(aside).contains("/images/brand/menta-m.png").contains("Menta Dance");
        assertThat(html.indexOf("<form")).as("the form sits in the second panel")
                .isGreaterThan(html.indexOf("auth-body"));
        assertThat(html.split("<h1", -1)).as("single h1").hasSize(2);
    }

    @Test
    @DisplayName("el login y el registro tienen cada uno su mensaje de bienvenida en el panel")
    void loginAndRegisterHaveTheirOwnWelcome() throws Exception {
        assertThat(render("/login")).contains("Iniciá sesión para continuar");
        assertThat(render("/registro")).contains("Creá tu cuenta para empezar a bailar");
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
