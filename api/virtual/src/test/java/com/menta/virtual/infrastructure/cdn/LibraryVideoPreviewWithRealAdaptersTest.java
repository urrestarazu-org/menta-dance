package com.menta.virtual.infrastructure.cdn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.virtual.application.dto.LibraryVideoStreamView;
import com.menta.virtual.application.exception.VideoNotPlayableException;
import com.menta.virtual.application.exception.VideoNotPlayableException.Reason;
import com.menta.virtual.application.usecase.GetLibraryVideoStreamUseCaseImpl;
import com.menta.virtual.domain.exception.VideoLibraryUnavailableException;
import com.menta.virtual.domain.exception.VideoNotFoundException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The preview use case (#864) wired to the real {@link BunnyStreamVideoLibrary} (against a local fake HTTP server, no
 * network) and the real {@link TokenAuthBunnyNetSignatureService}: what the staff preview does end to end, and that
 * neither Bunny's body nor the signed URL escapes into an exception message or a log line.
 */
class LibraryVideoPreviewWithRealAdaptersTest {

    private static final String GUID = "11111111-2222-3333-4444-555555555555";
    private static final String LEAKED_BODY = "secret-internal-detail-from-bunny";
    private static final String SECURITY_KEY = "preview-security-key-never-in-output";
    private static final Instant NOW = Instant.parse("2026-11-28T12:00:00Z");

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static String videoJson(int status, boolean mp4) {
        return "{\"guid\":\"" + GUID + "\",\"title\":\"Tango básico\",\"status\":" + status + ",\"length\":125,"
            + "\"thumbnailUrl\":null,\"hasMP4Fallback\":" + mp4 + ",\"availableResolutions\":\"720p\"}";
    }

    private GetLibraryVideoStreamUseCaseImpl useCaseWhereBunnyAnswers(int httpStatus, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(httpStatus, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });
        server.start();
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz-test.b-cdn.net");
        properties.setVideoLibraryId("4242");
        properties.setSecurityKey(SECURITY_KEY);
        properties.setApiKey("preview-library-api-key");
        properties.setApiBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.setApiConnectTimeoutMs(500);
        properties.setApiReadTimeoutMs(500);
        return new GetLibraryVideoStreamUseCaseImpl(
            new BunnyStreamVideoLibrary(properties), new TokenAuthBunnyNetSignatureService(properties), () -> NOW
        );
    }

    @Test
    @DisplayName("un video terminado con MP4 se firma con el token de la CDN y vence a las 4 horas")
    void a_finished_video_with_an_mp4_copy_is_signed_with_the_cdn_token_for_four_hours() throws IOException {
        GetLibraryVideoStreamUseCaseImpl useCase = useCaseWhereBunnyAnswers(200, videoJson(4, true));

        LibraryVideoStreamView view = useCase.get(GUID);

        long expires = NOW.plusSeconds(4 * 3600).getEpochSecond();
        assertThat(view.streamUrl()).startsWith("https://vz-test.b-cdn.net/" + GUID + "/play_720p.mp4?token=HS256-")
            .endsWith("&expires=" + expires);
        assertThat(view.expiresAt()).isEqualTo(Instant.parse("2026-11-28T16:00:00Z"));
        assertThat(view.title()).isEqualTo("Tango básico");
    }

    @Test
    @DisplayName("ni la URL firmada, ni el token, ni la clave aparecen en ningún log")
    void the_signed_url_the_token_and_the_key_never_reach_a_log() throws IOException {
        GetLibraryVideoStreamUseCaseImpl useCase = useCaseWhereBunnyAnswers(200, videoJson(4, true));

        try (CapturedLogs logs = CapturedLogs.of(org.slf4j.Logger.ROOT_LOGGER_NAME)) {
            LibraryVideoStreamView view = useCase.get(GUID);

            String token = view.streamUrl().substring(view.streamUrl().indexOf("token=") + "token=".length(),
                view.streamUrl().indexOf("&expires="));
            assertThat(logs.text())
                .doesNotContain(view.streamUrl())
                .doesNotContain(token)
                .doesNotContain("token=")
                .doesNotContain("https://vz-test.b-cdn.net")
                .doesNotContain(SECURITY_KEY)
                .doesNotContain("preview-library-api-key");
        }
    }

    @Test
    @DisplayName("un video sin MP4 de respaldo no se firma")
    void a_finished_video_without_an_mp4_copy_is_not_playable() throws IOException {
        GetLibraryVideoStreamUseCaseImpl useCase = useCaseWhereBunnyAnswers(200, videoJson(4, false));

        assertThatThrownBy(() -> useCase.get(GUID))
            .isInstanceOfSatisfying(VideoNotPlayableException.class, e -> assertThat(e.reason())
                .isEqualTo(Reason.NO_MP4_COPY));
    }

    @Test
    @DisplayName("un video que Bunny todavía procesa no se firma")
    void a_video_the_library_is_still_encoding_is_not_playable() throws IOException {
        GetLibraryVideoStreamUseCaseImpl useCase = useCaseWhereBunnyAnswers(200, videoJson(3, true));

        assertThatThrownBy(() -> useCase.get(GUID))
            .isInstanceOfSatisfying(VideoNotPlayableException.class, e -> assertThat(e.reason())
                .isEqualTo(Reason.PROCESSING));
    }

    @Test
    @DisplayName("un 404 de Bunny es VIDEO_NOT_FOUND")
    void a_404_from_the_library_is_a_video_not_found() throws IOException {
        GetLibraryVideoStreamUseCaseImpl useCase = useCaseWhereBunnyAnswers(404, "");

        assertThatThrownBy(() -> useCase.get(GUID)).isInstanceOf(VideoNotFoundException.class);
    }

    @Test
    @DisplayName("un error de Bunny es VIDEO_LIBRARY_UNAVAILABLE y no filtra su cuerpo")
    void an_error_from_the_library_is_unavailable_without_its_body() throws IOException {
        GetLibraryVideoStreamUseCaseImpl useCase = useCaseWhereBunnyAnswers(500, LEAKED_BODY);

        try (CapturedLogs logs = CapturedLogs.of(org.slf4j.Logger.ROOT_LOGGER_NAME)) {
            assertThatThrownBy(() -> useCase.get(GUID))
                .isInstanceOf(VideoLibraryUnavailableException.class)
                .hasMessageNotContaining(LEAKED_BODY)
                .hasNoCause();
            assertThat(logs.text()).doesNotContain(LEAKED_BODY);
        }
    }
}
