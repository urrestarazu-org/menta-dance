package com.menta.virtual.infrastructure.cdn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import com.menta.virtual.application.port.out.BunnyNetSignatureService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Coverage for {@link ProbingBunnyNetSignatureService}. A local {@link HttpServer} on a loopback ephemeral port stands
 * in for the CDN; the signed URL is always {@code https://vz-example.b-cdn.net/...}, so the package-private
 * constructor rewrites it to the fake server (production keeps the URL untouched). All hosts, ids and tokens are fake.
 */
class ProbingBunnyNetSignatureServiceTest {

    private static final String SIGNED_URL =
        "https://vz-example.b-cdn.net/vid-1/play_720p.mp4?token=HS256-fake-token&expires=1700000000";
    private static final String PROBE_PREFIX = "Bunny.net CDN probe: videoId=vid-1 file=play_720p.mp4 status=";

    private HttpServer server;
    private int status;
    private volatile CountDownLatch holdResponse;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> referers = new CopyOnWriteArrayList<>();
    private final AtomicInteger delegateCalls = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        status = 200;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            String referer = exchange.getRequestHeaders().getFirst("Referer");
            referers.add(referer == null ? "<none>" : referer);
            CountDownLatch hold = holdResponse;
            if (hold != null) {
                try {
                    hold.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (status >= 300 && status < 400) {
                exchange.getResponseHeaders().add("Location", "/elsewhere");
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private BunnyNetSignatureService delegate() {
        return (videoId, expiry) -> {
            delegateCalls.incrementAndGet();
            return SIGNED_URL;
        };
    }

    private UnaryOperator<String> toFakeServer() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return url -> url.replace("https://vz-example.b-cdn.net", base);
    }

    private ProbingBunnyNetSignatureService probing(UnaryOperator<String> mapper, Duration timeout) {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz-example.b-cdn.net");
        return new ProbingBunnyNetSignatureService(delegate(), properties, mapper, timeout);
    }

    private ProbingBunnyNetSignatureService probing() {
        return probing(toFakeServer(), Duration.ofSeconds(5));
    }

    private ProbingBunnyNetSignatureService probingWithReferer(String referer) {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz-example.b-cdn.net");
        properties.setDiagnosticProbeReferer(referer);
        return new ProbingBunnyNetSignatureService(delegate(), properties, toFakeServer(), Duration.ofSeconds(5));
    }

    private static void awaitProbeLine(CapturedLogs logs) {
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
            () -> assertThat(logs.text()).containsAnyOf("CDN probe: videoId=vid-1 file=play_720p.mp4 status=",
                "CDN probe failed"));
    }

    @Test
    void a_2xx_answer_is_logged_at_info_with_status_and_elapsed_time_and_no_hint() {
        status = 200;
        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            String url = probing().generateSignedUrl("vid-1", 1_700_000_000L);

            awaitProbeLine(logs);
            assertThat(url).isEqualTo(SIGNED_URL);
            assertThat(logs.messages(Level.INFO)).singleElement().asString()
                .startsWith(PROBE_PREFIX + "200 in ").endsWith("ms");
            assertThat(logs.messages(Level.WARN)).isEmpty();
            assertThat(requests).singleElement().asString()
                .startsWith("HEAD /vid-1/play_720p.mp4?token=HS256-fake-token&expires=1700000000");
        }
    }

    @Test
    void a_403_logs_the_token_and_pull_zone_restriction_hint() {
        status = 403;
        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            String url = probing().generateSignedUrl("vid-1", 1_700_000_000L);

            awaitProbeLine(logs);
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(logs.messages(Level.WARN)).hasSize(1));
            assertThat(url).isEqualTo(SIGNED_URL);
            assertThat(logs.messages(Level.INFO)).singleElement().asString().startsWith(PROBE_PREFIX + "403 in ");
            assertThat(logs.messages(Level.WARN)).singleElement().asString()
                .contains("token rejected")
                .contains("wrong key")
                .contains("Token authentication key")
                .contains("Referer")
                .contains("Block direct URL file access")
                .contains("allowed domains")
                .contains("APP_CDN_BUNNYNET_DIAGNOSTICPROBEREFERER=http://localhost:8080/");
        }
    }

    @Test
    void the_probe_sends_no_referer_by_default() {
        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            probing().generateSignedUrl("vid-1", 1_700_000_000L);

            awaitProbeLine(logs);
            assertThat(referers).containsExactly("<none>");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void a_blank_probe_referer_sends_no_referer(String blank) {
        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            probingWithReferer(blank).generateSignedUrl("vid-1", 1_700_000_000L);

            awaitProbeLine(logs);
            assertThat(referers).containsExactly("<none>");
        }
    }

    @Test
    void a_configured_probe_referer_is_sent_as_the_referer_header_and_the_result_is_unchanged() {
        status = 403;
        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            String url = probingWithReferer("  http://localhost:8080/ ").generateSignedUrl("vid-1", 1_700_000_000L);

            awaitProbeLine(logs);
            assertThat(url).isEqualTo(SIGNED_URL);
            assertThat(referers).containsExactly("http://localhost:8080/");
            assertThat(requests).singleElement().asString().startsWith("HEAD /vid-1/play_720p.mp4?");
            assertThat(logs.messages(Level.INFO)).singleElement().asString().startsWith(PROBE_PREFIX + "403 in ");
        }
    }

    @Test
    void the_probe_with_a_referer_still_never_logs_the_url_or_any_token() {
        status = 403;
        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            probingWithReferer("http://localhost:8080/").generateSignedUrl("vid-1", 1_700_000_000L);

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(logs.events()).hasSize(2));
            assertThat(logs.text())
                .doesNotContain("token=")
                .doesNotContain("HS256")
                .doesNotContain("expires=")
                .doesNotContain("b-cdn.net")
                .doesNotContain("127.0.0.1");
        }
    }

    @Test
    void a_404_logs_the_mp4_fallback_and_file_name_hint() {
        status = 404;
        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            probing().generateSignedUrl("vid-1", 1_700_000_000L);

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(logs.messages(Level.WARN)).hasSize(1));
            assertThat(logs.messages(Level.INFO)).singleElement().asString().startsWith(PROBE_PREFIX + "404 in ");
            assertThat(logs.messages(Level.WARN)).singleElement().asString()
                .contains("file not found")
                .contains("MP4 Fallback")
                .contains("APP_CDN_BUNNYNET_FILENAME");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 302})
    void any_other_non_2xx_status_logs_a_generic_hint_and_redirects_are_not_followed(int other) {
        status = other;
        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            probing().generateSignedUrl("vid-1", 1_700_000_000L);

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(logs.messages(Level.WARN)).hasSize(1));
            assertThat(logs.messages(Level.INFO)).singleElement().asString()
                .startsWith(PROBE_PREFIX + other + " in ");
            assertThat(logs.messages(Level.WARN)).singleElement().asString()
                .contains("unexpected status " + other)
                .contains("Network tab");
            assertThat(requests).hasSize(1);
        }
    }

    @Test
    void the_signed_url_is_returned_while_the_probe_response_is_still_pending() {
        holdResponse = new CountDownLatch(1);
        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            String url = probing().generateSignedUrl("vid-1", 1_700_000_000L);

            assertThat(url).isEqualTo(SIGNED_URL);
            assertThat(logs.events()).isEmpty();
            holdResponse.countDown();
            awaitProbeLine(logs);
        }
    }

    @Test
    void an_unreachable_host_is_swallowed_and_logged_with_the_exception_class_only() {
        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            String url = probing(u -> "http://127.0.0.1:1/vid-1/play_720p.mp4?token=HS256-fake-token",
                Duration.ofSeconds(5)).generateSignedUrl("vid-1", 1_700_000_000L);

            awaitProbeLine(logs);
            assertThat(url).isEqualTo(SIGNED_URL);
            assertThat(logs.messages(Level.INFO)).isEmpty();
            assertThat(logs.messages(Level.WARN)).singleElement().asString()
                .startsWith("Bunny.net CDN probe failed: videoId=vid-1 file=play_720p.mp4 error=ConnectException")
                .doesNotContain("http")
                .doesNotContain("token")
                .doesNotContain("127.0.0.1");
        }
    }

    @Test
    void a_timeout_is_swallowed_and_logged_without_the_url() {
        holdResponse = new CountDownLatch(1);
        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            String url = probing(toFakeServer(), Duration.ofMillis(200)).generateSignedUrl("vid-1", 1_700_000_000L);

            awaitProbeLine(logs);
            holdResponse.countDown();
            assertThat(url).isEqualTo(SIGNED_URL);
            assertThat(logs.messages(Level.WARN)).singleElement().asString()
                .contains("error=HttpTimeoutException")
                .doesNotContain("http:")
                .doesNotContain("token");
        }
    }

    @Test
    void a_malformed_target_is_swallowed_and_never_echoed() {
        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            String url = probing(u -> "not a uri ?token=HS256-fake-token", Duration.ofSeconds(5))
                .generateSignedUrl("vid-1", 1_700_000_000L);

            awaitProbeLine(logs);
            assertThat(url).isEqualTo(SIGNED_URL);
            assertThat(logs.messages(Level.WARN)).singleElement().asString()
                .contains("error=IllegalArgumentException")
                .doesNotContain("not a uri")
                .doesNotContain("token");
        }
    }

    @Test
    void a_failing_delegate_propagates_and_nothing_is_probed() {
        BunnyNetProperties properties = new BunnyNetProperties();
        BunnyNetSignatureService failing = (videoId, expiry) -> {
            throw new IllegalArgumentException("boom");
        };
        ProbingBunnyNetSignatureService service =
            new ProbingBunnyNetSignatureService(failing, properties, toFakeServer(), Duration.ofSeconds(5));

        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            assertThatThrownBy(() -> service.generateSignedUrl("vid-1", 1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("boom");

            assertThat(requests).isEmpty();
            assertThat(logs.events()).isEmpty();
        }
    }

    @Test
    void the_probe_never_logs_the_url_or_any_token() {
        status = 403;
        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            probing().generateSignedUrl("vid-1", 1_700_000_000L);

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(logs.events()).hasSize(2));
            assertThat(logs.text())
                .doesNotContain("token=")
                .doesNotContain("HS256")
                .doesNotContain("expires=")
                .doesNotContain("https")
                .doesNotContain("127.0.0.1")
                .doesNotContain("vz-example")
                .doesNotContain("b-cdn.net");
        }
    }

    @Test
    void the_public_constructor_probes_the_signed_url_as_is_without_affecting_the_result() {
        // Production constructor: identity URL mapping, 5 s timeout. ".invalid" never resolves (RFC 2606), so no
        // real host is contacted; the probe fails in the background and the URL still comes back unchanged.
        String unresolvable = "https://vz-example.invalid/vid-1/play_720p.mp4?token=HS256-fake-token&expires=1";
        BunnyNetSignatureService service =
            new ProbingBunnyNetSignatureService((videoId, expiry) -> unresolvable, new BunnyNetProperties());

        try (CapturedLogs logs = CapturedLogs.of(ProbingBunnyNetSignatureService.class)) {
            assertThat(service.generateSignedUrl("vid-1", 1_700_000_000L)).isEqualTo(unresolvable);

            awaitProbeLine(logs);
            assertThat(logs.text()).doesNotContain("token").doesNotContain("https").doesNotContain("invalid");
        }
    }
}
