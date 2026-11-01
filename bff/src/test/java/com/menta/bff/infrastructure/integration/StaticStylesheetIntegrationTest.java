package com.menta.bff.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * Covers the compiled Tailwind stylesheet end to end (#536): it is built by
 * Gradle, reachable anonymously, loaded from the shared head fragment and
 * versioned by content so the 30-day immutable NGINX cache cannot serve a
 * stale copy.
 */
@DisplayName("Static stylesheet Integration Tests")
class StaticStylesheetIntegrationTest extends BaseIntegrationTest {

    private static final Pattern STYLESHEET_LINK =
            Pattern.compile("<link[^>]*rel=\"stylesheet\"[^>]*href=\"(/css/app-[0-9a-f]{32}\\.css)\"");

    @Test
    @DisplayName("GET /css/app.css es público y se sirve como text/css con tokens y utilidades de marca")
    void compiledStylesheetIsServedAnonymously() throws Exception {
        String css = mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/css")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(css).contains(".btn-primary").contains("--color-brand-deep");
    }

    @Test
    @DisplayName("la hoja compilada incluye las clases de componente de @layer components")
    void compiledStylesheetContainsComponentClasses() throws Exception {
        String css = mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(css)
                .contains(".btn-primary")
                .contains(".btn-secondary")
                .contains(".field-input")
                .contains(".field-error")
                .contains(".card")
                .contains(".badge-success")
                .contains(".badge-warning")
                .contains(".badge-danger")
                .contains(".badge-neutral")
                .contains(".container-page")
                .contains(".alert-error")
                .contains(".alert-success")
                .contains(".alert-info");
    }

    @Test
    @DisplayName("la hoja compilada incluye el realce al pasar el mouse y lo apaga con movimiento reducido")
    void compiledStylesheetContainsHoverFeedback() throws Exception {
        String css = mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(css).contains(".hover-lift:hover").contains(".hover-pop:hover").contains(".animate-pay-ring")
                .contains("@keyframes pay-ring");
        // Each moving effect is switched off for people who ask for less motion (WCAG 2.3.3).
        for (String effect : new String[] {".hover-lift", ".hover-pop"}) {
            assertThat(Pattern.compile("@media \\(prefers-reduced-motion:\\s*reduce\\)\\{[^@]*"
                    + Pattern.quote(effect) + ":hover").matcher(css).find())
                    .withFailMessage("%s has no reduced-motion override in the compiled stylesheet", effect)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("/login enlaza una versión del stylesheet con hash de contenido y esa URL responde 200")
    void loginLinksTheVersionedStylesheet() throws Exception {
        String html = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Matcher link = STYLESHEET_LINK.matcher(html);
        assertThat(link.find())
                .withFailMessage("The rendered /login page has no content-hashed /css/app-<hash>.css link")
                .isTrue();

        mockMvc.perform(get(link.group(1)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/css")));
    }

    @Test
    @DisplayName("el permiso público de /css no abre otras rutas estáticas ni verbos que mutan")
    void cssPermitDoesNotWidenAccess() throws Exception {
        mockMvc.perform(post("/css/app.css"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection());
    }

    @ParameterizedTest(name = "GET /fonts/poppins-latin-{0}-normal.woff2")
    @ValueSource(ints = {400, 500, 600, 700})
    @DisplayName("las fuentes Poppins self-hosted se sirven anónimas como font/woff2")
    void poppinsFontsAreServedAnonymously(int weight) throws Exception {
        byte[] body = mockMvc.perform(get("/fonts/poppins-latin-" + weight + "-normal.woff2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("font/woff2")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        // A real woff2 file starts with the "wOF2" magic number.
        assertThat(new String(body, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("wOF2");
    }

    @Test
    @DisplayName("app.css declara @font-face de Poppins con font-display swap y URLs locales")
    void stylesheetDeclaresSelfHostedPoppins() throws Exception {
        String css = mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(css)
                .contains("@font-face")
                .contains("font-family:Poppins")
                .contains("font-display:swap")
                .contains("/fonts/poppins-latin-400-normal.woff2")
                .contains("/fonts/poppins-latin-700-normal.woff2")
                .doesNotContain("fonts.googleapis.com")
                .doesNotContain("fonts.gstatic.com");
    }

    @Test
    @DisplayName("el permiso público de /fonts es GET-only y no abre verbos que mutan")
    void fontsPermitDoesNotWidenAccess() throws Exception {
        mockMvc.perform(post("/fonts/poppins-latin-400-normal.woff2"))
                .andExpect(status().is4xxClientError());
    }

    @ParameterizedTest(name = "GET {0}")
    @ValueSource(strings = {"/images/brand/menta-m.png", "/images/hero/salon.jpg", "/images/hero/salon-960.jpg"})
    @DisplayName("las imágenes de marca y del hero se sirven anónimas")
    void brandImagesAreServedAnonymously(String path) throws Exception {
        byte[] body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType(
                        path.endsWith(".png") ? "image/png" : "image/jpeg")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(body).isNotEmpty();
    }

    @Test
    @DisplayName("el permiso público de /images es GET-only y no abre verbos que mutan")
    void imagesPermitDoesNotWidenAccess() throws Exception {
        mockMvc.perform(post("/images/brand/menta-m.png"))
                .andExpect(status().is4xxClientError());
    }
}
