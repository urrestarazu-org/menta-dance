package com.menta.virtual.infrastructure.cdn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoOrder;
import com.menta.virtual.application.dto.LibraryVideoPage;
import com.menta.virtual.application.dto.LibraryVideoStatus;
import com.menta.virtual.application.dto.VideoLibraryQuery;
import com.menta.virtual.domain.exception.VideoLibraryUnavailableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Exercises the real HTTP path of {@link BunnyStreamVideoLibrary} against a local {@link HttpServer}; no real
 * network is involved (same approach as the Mercado Pago adapters' tests).
 */
class BunnyStreamVideoLibraryTest {

    private static final String API_KEY = "test-library-api-key-xyz";
    private static final String LEAKED_BODY = "secret-internal-detail-from-bunny";

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> accessKeys = new CopyOnWriteArrayList<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private BunnyStreamVideoLibrary libraryAnswering(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI().getRawPath()
                + (exchange.getRequestURI().getRawQuery() == null ? "" : "?" + exchange.getRequestURI().getRawQuery()));
            accessKeys.add(String.valueOf(exchange.getRequestHeaders().getFirst("AccessKey")));
            respond(exchange, status, body);
        });
        server.start();
        return new BunnyStreamVideoLibrary(properties("http://localhost:" + server.getAddress().getPort()));
    }

    private static BunnyNetProperties properties(String baseUrl) {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz-test.b-cdn.net");
        properties.setVideoLibraryId("4242");
        properties.setSecurityKey("security-key-never-used-here");
        properties.setApiKey(API_KEY);
        properties.setApiBaseUrl(baseUrl);
        properties.setApiConnectTimeoutMs(500);
        properties.setApiReadTimeoutMs(500);
        return properties;
    }

    private static final String VIDEO_JSON = "{\"videoLibraryId\":4242,"
        + "\"guid\":\"11111111-2222-3333-4444-555555555555\",\"title\":\"Tango básico\","
        + "\"status\":4,\"length\":125,\"thumbnailUrl\":\"https://vz-test.b-cdn.net/1111/thumbnail.jpg\","
        + "\"hasMP4Fallback\":true,\"availableResolutions\":\"360p, 720p,1080p\",\"views\":10,"
        + "\"unknownField\":{\"a\":1}}";

    // --- list -------------------------------------------------------------

    @Test
    void list_sends_the_one_based_page_the_size_the_search_and_the_order_with_the_library_key() throws IOException {
        BunnyStreamVideoLibrary library = libraryAnswering(200,
            "{\"totalItems\":41,\"currentPage\":3,\"itemsPerPage\":20,\"items\":[" + VIDEO_JSON + "]}");

        LibraryVideoPage page = library.list(new VideoLibraryQuery(2, 20, "tango básico", LibraryVideoOrder.TITLE));

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0)).startsWith("/library/4242/videos?")
            .contains("page=3").contains("itemsPerPage=20").contains("orderBy=title")
            .contains("search=tango%20b%C3%A1sico");
        assertThat(accessKeys).containsExactly(API_KEY);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalItems()).isEqualTo(41);
        assertThat(page.items()).containsExactly(new LibraryVideo(
            "11111111-2222-3333-4444-555555555555", "Tango básico", LibraryVideoStatus.FINISHED, 125,
            "https://vz-test.b-cdn.net/1111/thumbnail.jpg", true, List.of("360p", "720p", "1080p")
        ));
    }

    @Test
    void list_without_a_search_does_not_send_the_search_parameter() throws IOException {
        BunnyStreamVideoLibrary library = libraryAnswering(200, "{\"totalItems\":0,\"items\":[]}");

        LibraryVideoPage page = library.list(new VideoLibraryQuery(0, 10, null, null));

        assertThat(requests.get(0)).contains("page=1").contains("orderBy=date").doesNotContain("search");
        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
    }

    @Test
    void list_tolerates_null_items_missing_fields_unknown_status_and_skips_items_without_id() throws IOException {
        BunnyStreamVideoLibrary library = libraryAnswering(200, "{\"totalItems\":3,\"items\":["
            + "{\"guid\":\"a\",\"title\":null,\"status\":99,\"length\":0,\"availableResolutions\":\"\"},"
            + "{\"title\":\"sin guid\",\"status\":4},"
            + "{\"guid\":\"b\",\"status\":2,\"hasMP4Fallback\":null,\"thumbnailUrl\":null,"
            + "\"availableResolutions\":null}]}");

        LibraryVideoPage page = library.list(new VideoLibraryQuery(0, 10, null, null));

        assertThat(page.items()).containsExactly(
            new LibraryVideo("a", "", LibraryVideoStatus.UNKNOWN, 0, null, false, List.of()),
            new LibraryVideo("b", "", LibraryVideoStatus.PROCESSING, 0, null, false, List.of())
        );
        assertThat(library.list(new VideoLibraryQuery(0, 10, null, null)).items()).hasSize(2);
    }

    @Test
    void list_with_a_null_items_array_is_an_empty_page() throws IOException {
        BunnyStreamVideoLibrary library = libraryAnswering(200, "{\"totalItems\":0,\"items\":null}");

        assertThat(library.list(new VideoLibraryQuery(0, 10, null, null)).items()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8})
    void every_documented_status_code_maps_to_its_own_status(int code) throws IOException {
        BunnyStreamVideoLibrary library = libraryAnswering(200,
            "{\"totalItems\":1,\"items\":[{\"guid\":\"a\",\"status\":" + code + "}]}");

        LibraryVideoStatus status = library.list(new VideoLibraryQuery(0, 10, null, null)).items().get(0).status();

        assertThat(status).isNotEqualTo(LibraryVideoStatus.UNKNOWN);
        assertThat(status.ordinal()).isEqualTo(code);
    }

    // --- find -------------------------------------------------------------

    @Test
    void find_returns_the_video() throws IOException {
        BunnyStreamVideoLibrary library = libraryAnswering(200, VIDEO_JSON);

        Optional<LibraryVideo> video = library.find("11111111-2222-3333-4444-555555555555");

        assertThat(requests).containsExactly("/library/4242/videos/11111111-2222-3333-4444-555555555555");
        assertThat(accessKeys).containsExactly(API_KEY);
        assertThat(video).isPresent();
        assertThat(video.get().title()).isEqualTo("Tango básico");
    }

    @Test
    void find_answers_empty_when_the_library_has_no_such_video() throws IOException {
        BunnyStreamVideoLibrary library = libraryAnswering(404, "");

        assertThat(library.find("gone")).isEmpty();
    }

    @Test
    void find_encodes_the_id_as_a_single_path_segment() throws IOException {
        BunnyStreamVideoLibrary library = libraryAnswering(404, "");

        library.find("../other/1?x=y");

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0)).startsWith("/library/4242/videos/").doesNotContain("?x=y")
            .doesNotContain("/other/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void find_with_a_blank_id_is_empty_without_calling_the_library(String blank) throws IOException {
        BunnyStreamVideoLibrary library = libraryAnswering(200, VIDEO_JSON);

        assertThat(library.find(blank)).isEmpty();
        assertThat(requests).isEmpty();
    }

    @Test
    void find_with_an_empty_body_is_reported_as_unavailable() throws IOException {
        BunnyStreamVideoLibrary library = libraryAnswering(200, "");

        assertThatThrownBy(() -> library.find("x")).isInstanceOf(VideoLibraryUnavailableException.class);
    }

    // --- errors -----------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500, 502, 503})
    void an_error_status_is_mapped_without_the_body_or_the_key(int status) throws IOException {
        BunnyStreamVideoLibrary library = libraryAnswering(status, "{\"Message\":\"" + LEAKED_BODY + "\"}");

        assertThatThrownBy(() -> library.list(new VideoLibraryQuery(0, 10, null, null)))
            .isInstanceOf(VideoLibraryUnavailableException.class)
            .hasMessageContaining(String.valueOf(status))
            .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(LEAKED_BODY).doesNotContain(API_KEY))
            .satisfies(failure -> assertThat(String.valueOf(failure.getCause())).doesNotContain(LEAKED_BODY));
        assertThatThrownBy(() -> library.find("x"))
            .isInstanceOf(VideoLibraryUnavailableException.class)
            .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(LEAKED_BODY).doesNotContain(API_KEY));
    }

    @Test
    void a_body_that_is_not_json_is_reported_as_unavailable_without_echoing_it() throws IOException {
        BunnyStreamVideoLibrary library = libraryAnswering(200, "<html>" + LEAKED_BODY + "</html>");

        assertThatThrownBy(() -> library.list(new VideoLibraryQuery(0, 10, null, null)))
            .isInstanceOf(VideoLibraryUnavailableException.class)
            .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(LEAKED_BODY));
    }

    @Test
    void a_slow_library_is_cut_by_the_read_timeout() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(2000);
                respond(exchange, 200, "{}");
            } catch (InterruptedException | IOException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        BunnyStreamVideoLibrary library =
            new BunnyStreamVideoLibrary(properties("http://localhost:" + server.getAddress().getPort()));

        long started = System.nanoTime();
        assertThatThrownBy(() -> library.list(new VideoLibraryQuery(0, 10, null, null)))
            .isInstanceOf(VideoLibraryUnavailableException.class);
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(1900);
    }

    @Test
    void an_unreachable_library_is_reported_as_unavailable() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        int closedPort = server.getAddress().getPort();
        server.stop(0);
        server = null;
        BunnyStreamVideoLibrary library = new BunnyStreamVideoLibrary(properties("http://localhost:" + closedPort));

        assertThatThrownBy(() -> library.find("x")).isInstanceOf(VideoLibraryUnavailableException.class);
    }

    @Test
    void the_adapter_never_renders_the_key() throws IOException {
        BunnyStreamVideoLibrary library = libraryAnswering(200, "{}");

        assertThat(library.toString()).doesNotContain(API_KEY);
        assertThat(new ArrayList<>(accessKeys)).isEmpty();
    }
}
