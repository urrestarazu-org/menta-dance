package com.menta.bff.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Legal and contact pages (#650): public, one per exact path, inside the shared layout, linked from the
 * footer, and without any contact detail baked into the repository.
 */
@DisplayName("Legal pages Integration Tests")
class LegalPagesIntegrationTest extends BaseIntegrationTest {

    private static final Pattern H1 = Pattern.compile("<h1[ >]");

    private String page(String path) throws Exception {
        return mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @ParameterizedTest(name = "GET {0} es público y muestra {1}")
    @CsvSource({
        "/terminos-condiciones, Términos y condiciones",
        "/politica-privacidad, Política de privacidad",
        "/politica-cookies, Política de cookies",
        "/politica-devolucion, Política de devolución",
        "/propiedad-intelectual, Propiedad intelectual",
        "/accesibilidad, Accesibilidad",
        "/contacto, Contacto"
    })
    @DisplayName("cada página responde 200 sin sesión, con un único h1 y el título en la pestaña")
    void pageIsPublicAndTitled(String path, String title) throws Exception {
        String html = page(path);

        assertThat(html).contains("<title>" + title + " - Menta Dance</title>");
        assertThat(HtmlSections.mainOf(html)).contains(">" + title + "</h1>");
        assertThat(H1.matcher(html).results().count()).isEqualTo(1);
        assertThat(html).doesNotContain(" style=\"").doesNotContain("<style");
    }

    @ParameterizedTest(name = "{0} usa el layout compartido")
    @ValueSource(strings = {
        "/terminos-condiciones", "/politica-privacidad", "/politica-cookies", "/politica-devolucion",
        "/propiedad-intelectual", "/accesibilidad", "/contacto"
    })
    @DisplayName("lleva el header, el footer y el enlace para saltar al contenido")
    void pageUsesTheSharedLayout(String path) throws Exception {
        String html = page(path);

        assertThat(html).contains("class=\"site-header\"").contains("class=\"site-footer\"");
        assertThat(html).contains("href=\"#main\"");
        assertThat(html.split("<main", -1)).hasSize(2);
    }

    @ParameterizedTest(name = "{0} avisa que el texto está pendiente de revisión")
    @ValueSource(strings = {
        "/terminos-condiciones", "/politica-privacidad", "/politica-cookies", "/politica-devolucion",
        "/propiedad-intelectual", "/accesibilidad"
    })
    @DisplayName("los textos legales llevan el aviso de revisión pendiente")
    void legalTextCarriesTheReviewNotice(String path) throws Exception {
        assertThat(HtmlSections.mainOf(page(path))).contains("pendiente de revisión");
    }

    @Test
    @DisplayName("el footer enlaza las siete páginas en una navegación 'Información legal'")
    void footerLinksEveryLegalPage() throws Exception {
        String html = page("/");
        String footer = html.substring(html.indexOf("<footer"));

        assertThat(footer).contains("aria-label=\"Información legal\"");
        for (String path : new String[] {
            "/terminos-condiciones", "/politica-privacidad", "/politica-cookies", "/politica-devolucion",
            "/propiedad-intelectual", "/accesibilidad", "/contacto"
        }) {
            assertThat(footer).contains("href=\"" + path + "\"");
        }
    }

    @Test
    @DisplayName("la página de contacto no inventa datos cuando no hay configuración")
    void contactWithoutConfigurationShowsNoDetails() throws Exception {
        String main = HtmlSections.mainOf(page("/contacto"));

        assertThat(main).doesNotContain("mailto:").doesNotContain("tel:");
        assertThat(main).contains("Datos de contacto pendientes");
    }

    @Test
    @DisplayName("el permiso público es solo GET y por ruta exacta")
    void permitIsGetOnlyAndExact() throws Exception {
        mockMvc.perform(post("/terminos-condiciones")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/terminos-condiciones/otra")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/contacto/otra")).andExpect(status().is3xxRedirection());
    }
}
