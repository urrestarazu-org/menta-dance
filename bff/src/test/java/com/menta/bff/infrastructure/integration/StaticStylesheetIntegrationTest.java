package com.menta.bff.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

        assertThat(css).contains(".bg-brand-action").contains("--color-brand-deep");
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
}
