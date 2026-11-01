package com.menta.bff.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every page of the site shares one header and one footer. Thymeleaf resolves {@code fragments/site :: header}
 * as a fragment name <em>and</em> as an element name, so another {@code <header>} or {@code <footer>} element
 * in the same template file would be inserted too (#685).
 */
@DisplayName("Site chrome Integration Tests")
class SiteChromeIntegrationTest extends BaseIntegrationTest {

    private static long count(String text, String fragment) {
        return Pattern.compile(Pattern.quote(fragment)).matcher(text).results().count();
    }

    @ParameterizedTest(name = "{0} renders exactly one header and one footer")
    @ValueSource(strings = {"/login", "/terminos-condiciones", "/politica-privacidad", "/contacto"})
    @DisplayName("a regular page renders exactly one header and one footer, with the full navigation")
    void regularPagesHaveOneHeaderAndOneFooter(String path) throws Exception {
        String html = mockMvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(count(html, "<header")).as("headers in %s", path).isEqualTo(1);
        assertThat(count(html, "<footer")).as("footers in %s", path).isEqualTo(1);
        assertThat(html).contains("Clases presenciales").contains("Explorar");
    }

    @Test
    @DisplayName("the login page also has a single copy of the legal links")
    void legalLinksAppearOnce() throws Exception {
        String html = mockMvc.perform(get("/login")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(count(html, "Información legal")).isEqualTo(2);
    }
}
