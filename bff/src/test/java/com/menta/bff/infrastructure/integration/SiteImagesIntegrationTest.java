package com.menta.bff.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.RequestDispatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/**
 * The brand photos, the favicon, the animated logo and {@code robots.txt} (#654): every
 * page declares the icons, every image a page references is served anonymously, photos
 * carry their size and a text alternative, and the video respects reduced motion.
 */
@DisplayName("Site images Integration Tests")
class SiteImagesIntegrationTest extends BaseIntegrationTest {

    private static final Pattern IMG_TAG = Pattern.compile("<img\\b[^>]*>");
    private static final Pattern SRC_URL = Pattern.compile("\\b(?:src|srcset|poster)=\"([^\"]+)\"");
    private static final Pattern URL_IN_SET = Pattern.compile("(/images/[^\\s,\"]+)");

    @ParameterizedTest(name = "{0} declara el favicon y el ícono de iOS")
    @ValueSource(strings = {"/", "/login", "/registro", "/contacto", "/terminos-condiciones"})
    @DisplayName("todas las páginas públicas declaran los íconos en el head compartido")
    void pagesDeclareTheIcons(String path) throws Exception {
        String html = render(path);

        assertThat(html)
                .containsPattern("<link[^>]*rel=\"icon\"[^>]*href=\"/favicon\\.ico\"")
                .containsPattern("<link[^>]*rel=\"apple-touch-icon\""
                        + "[^>]*href=\"/images/brand/apple-touch-icon\\.png\"");
    }

    @Test
    @DisplayName("la página de error también declara los íconos")
    void errorPageDeclaresTheIcons() throws Exception {
        assertThat(renderError()).containsPattern("<link[^>]*rel=\"icon\"[^>]*href=\"/favicon\\.ico\"");
    }

    @Test
    @DisplayName("GET /favicon.ico es público y es un ícono real")
    void faviconIsServedAnonymously() throws Exception {
        byte[] body = mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // An .ico file starts with reserved=0, type=1 (icon).
        assertThat(body).hasSizeGreaterThan(4).startsWith(0, 0, 1, 0);
    }

    @Test
    @DisplayName("GET /robots.txt es público, es de este sitio y no apunta a dominios ajenos")
    void robotsIsServedAnonymouslyAndBelongsToThisSite() throws Exception {
        String robots = mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andReturn().getResponse().getContentAsString();

        assertThat(robots)
                .contains("User-agent: *")
                .contains("Disallow: /dashboard")
                .contains("Disallow: /actuator/")
                .doesNotContain("Sitemap")
                .doesNotContain("http")
                .doesNotContain("mentavirtual")
                .doesNotContain("Virtual Academy")
                .doesNotContain("/admin/")
                .doesNotContain("/users/")
                .doesNotContain("/forgot-password");
    }

    @Test
    @DisplayName("el permiso público de favicon y robots es GET-only")
    void rootFilesPermitDoesNotWidenAccess() throws Exception {
        mockMvc.perform(post("/favicon.ico")).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/robots.txt")).andExpect(status().is4xxClientError());
    }

    @ParameterizedTest(name = "{0} sirve todas las imágenes que referencia")
    @ValueSource(strings = {"/", "/login", "/registro", "/contacto"})
    @DisplayName("cada imagen, srcset y póster que referencia una página existe y se sirve anónimo")
    void everyReferencedImageIsServed(String path) throws Exception {
        for (String url : imageUrls(render(path))) {
            byte[] body = mockMvc.perform(get(url)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(body).as(url).isNotEmpty();
        }
    }

    @Test
    @DisplayName("el inicio muestra tres fotos de bailarines con tamaño, texto alternativo y carga diferida")
    void homeShowsTheDancersGallery() throws Exception {
        String html = render("/");
        String section = section(html, "gallery-title");

        assertThat(html).containsPattern("<h2[^>]*id=\"gallery-title\"[^>]*>\\s*\\S");
        List<String> images = tagsIn(section);
        assertThat(images).hasSize(3);
        assertThat(images).allSatisfy(img -> assertThat(img)
                .containsPattern("alt=\"[^\"]{15,}\"")
                .containsPattern("width=\"\\d+\"")
                .containsPattern("height=\"\\d+\"")
                .contains("loading=\"lazy\"")
                .containsPattern("srcset=\"[^\"]*480w[^\"]*800w[^\"]*\"")
                .contains("sizes=\""));
        assertThat(section).doesNotContain("style=");
    }

    @Test
    @DisplayName("el inicio invita a las clases presenciales con la foto del salón y un enlace a /classes")
    void homeShowsTheStudioSection() throws Exception {
        String section = section(render("/"), "studio-title");

        assertThat(section)
                .containsPattern("<h2[^>]*id=\"studio-title\"")
                .containsPattern("<img[^>]*src=\"/images/home/salon[^\"]*\"[^>]*alt=\"[^\"]{15,}\"")
                .containsPattern("<a[^>]*href=\"/classes\"");
    }

    @Test
    @DisplayName("el hero usa fotos sin marcas de agua: la hoja de estilos apunta a las versiones limpias")
    void heroUsesTheCleanPhotos() throws Exception {
        String css = mockMvc.perform(get("/css/app.css")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(css).contains("/images/hero/salon.jpg").contains("/images/hero/salon-960.jpg");
    }

    @Test
    @DisplayName("la página de error muestra el logo animado en video liviano y deja el logo quieto de respaldo")
    void errorPageShowsTheAnimatedLogo() throws Exception {
        String html = renderError();
        Matcher video = Pattern.compile("<video\\b[^>]*>.*?</video>", Pattern.DOTALL).matcher(html);

        assertThat(video.find()).as("the error page has a <video>").isTrue();
        assertThat(video.group())
                .contains("autoplay").contains("muted").contains("loop").contains("playsinline")
                .contains("aria-hidden=\"true\"")
                .containsPattern("poster=\"/images/brand/menta-wordmark\\.png\"")
                .containsPattern("<source[^>]*src=\"/images/brand/menta-animado\\.mp4\"[^>]*type=\"video/mp4\"")
                .doesNotContain("controls");
        assertThat(html).containsPattern("<img[^>]*class=\"[^\"]*brand-still[^\"]*\"[^>]*alt=\"Menta Dance\"");
    }

    @Test
    @DisplayName("el video se sirve anónimo como mp4 y la hoja de estilos lo oculta con movimiento reducido")
    void videoIsServedAndReducedMotionHidesIt() throws Exception {
        byte[] body = mockMvc.perform(get("/images/brand/menta-animado.mp4"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("video/mp4")))
                .andReturn().getResponse().getContentAsByteArray();
        String css = mockMvc.perform(get("/css/app.css")).andReturn().getResponse().getContentAsString();

        // An MP4 carries the "ftyp" box right after its 4-byte size.
        assertThat(new String(body, 4, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("ftyp");
        assertThat(css).contains("prefers-reduced-motion").contains(".brand-motion").contains(".brand-still");
    }

    private String render(String path) throws Exception {
        return mockMvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String renderError() throws Exception {
        return mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andReturn().getResponse().getContentAsString();
    }

    /** The markup of the section labelled by {@code headingId}, up to the next {@code </section>}. */
    private static String section(String html, String headingId) {
        int start = html.indexOf("aria-labelledby=\"" + headingId + "\"");
        assertThat(start).as("a section labelled by %s", headingId).isNotNegative();
        return html.substring(start, html.indexOf("</section>", start));
    }

    private static List<String> tagsIn(String html) {
        List<String> tags = new ArrayList<>();
        Matcher matcher = IMG_TAG.matcher(html);
        while (matcher.find()) {
            tags.add(matcher.group());
        }
        return tags;
    }

    /** Every {@code /images/...} URL in an {@code src}, {@code srcset} or {@code poster} attribute. */
    private static List<String> imageUrls(String html) {
        List<String> urls = new ArrayList<>();
        Matcher attributes = SRC_URL.matcher(html);
        while (attributes.find()) {
            Matcher url = URL_IN_SET.matcher(attributes.group(1));
            while (url.find()) {
                urls.add(url.group(1));
            }
        }
        return urls;
    }
}
