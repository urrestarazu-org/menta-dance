package com.menta.billing.infrastructure.provider.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.PaymentPreferenceResult;
import com.menta.billing.domain.model.Money;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real HTTP path against a local {@link HttpServer}, mirroring
 * {@link MercadoPagoPaymentProviderAdapterTest} — the adapter builds its own
 * {@code RestClient} internally, so there is no injectable seam to mock
 * without changing production code.
 */
class MercadoPagoPaymentPreferenceAdapterTest {

    private static final Money AMOUNT = Money.of(new BigDecimal("15000.00"), "ARS");

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
        throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String startServer(String responseBody, int status, AtomicReference<String> capturedBody)
        throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/checkout/preferences", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, status, responseBody);
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static MercadoPagoPaymentPreferenceAdapter adapter(String baseUrl, String backUrl, String notifyUrl) {
        return new MercadoPagoPaymentPreferenceAdapter(baseUrl, "test-token", 2000, 3000, backUrl, notifyUrl);
    }

    @Test
    void createPreference_returns_the_preference_id_and_its_init_point() throws IOException {
        AtomicReference<String> captured = new AtomicReference<>();
        String baseUrl = startServer(
            "{\"id\":\"pref-123\",\"init_point\":\"https://mp.example/checkout/pref-123\"}", 200, captured
        );

        PaymentPreferenceResult result = adapter(baseUrl, "", "")
            .createPreference(new PaymentPreferenceRequest("SUB-1", "Plan Mensual", AMOUNT));

        assertThat(result.preferenceId()).isEqualTo("pref-123");
        assertThat(result.checkoutUrl()).isEqualTo("https://mp.example/checkout/pref-123");
    }

    /** Our reference must travel to the provider — it is what the eventual webhook correlates against. */
    @Test
    void createPreference_sends_our_external_reference_and_the_item_price() throws IOException {
        AtomicReference<String> captured = new AtomicReference<>();
        String baseUrl = startServer(
            "{\"id\":\"pref-123\",\"init_point\":\"https://mp.example/checkout/pref-123\"}", 200, captured
        );

        adapter(baseUrl, "https://menta.dance/callback", "https://menta.dance/webhook")
            .createPreference(new PaymentPreferenceRequest("SUB-1", "Plan Mensual", AMOUNT));

        assertThat(captured.get()).contains("\"external_reference\":\"SUB-1\"");
        assertThat(captured.get()).contains("\"unit_price\":15000.00");
        assertThat(captured.get()).contains("\"currency_id\":\"ARS\"");
        assertThat(captured.get()).contains("\"title\":\"Plan Mensual\"");
        assertThat(captured.get()).contains("\"notification_url\":\"https://menta.dance/webhook\"");
        assertThat(captured.get()).contains("\"success\":\"https://menta.dance/callback\"");
    }

    @Test
    void createPreference_omits_unconfigured_urls_instead_of_sending_empty_strings() throws IOException {
        AtomicReference<String> captured = new AtomicReference<>();
        String baseUrl = startServer(
            "{\"id\":\"pref-123\",\"init_point\":\"https://mp.example/checkout/pref-123\"}", 200, captured
        );

        adapter(baseUrl, "  ", null)
            .createPreference(new PaymentPreferenceRequest("SUB-1", "Plan Mensual", AMOUNT));

        assertThat(captured.get()).contains("\"back_urls\":null");
        assertThat(captured.get()).contains("\"notification_url\":null");
    }

    @Test
    void createPreference_throws_when_the_provider_returns_an_incomplete_body() throws IOException {
        AtomicReference<String> captured = new AtomicReference<>();
        String baseUrl = startServer("{\"id\":\"pref-123\"}", 200, captured);
        MercadoPagoPaymentPreferenceAdapter adapter = adapter(baseUrl, "", "");
        PaymentPreferenceRequest request = new PaymentPreferenceRequest("SUB-1", "Plan Mensual", AMOUNT);

        assertThatThrownBy(() -> adapter.createPreference(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("incomplete preference body");
    }

    @Test
    void createPreference_throws_when_the_provider_returns_an_empty_body() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/checkout/preferences", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        MercadoPagoPaymentPreferenceAdapter adapter =
            adapter("http://localhost:" + server.getAddress().getPort(), "", "");
        PaymentPreferenceRequest request = new PaymentPreferenceRequest("SUB-1", "Plan Mensual", AMOUNT);

        assertThatThrownBy(() -> adapter.createPreference(request))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("incomplete preference body");
    }

    @Test
    void createPreference_propagates_provider_errors_instead_of_retrying() throws IOException {
        AtomicReference<String> captured = new AtomicReference<>();
        String baseUrl = startServer("{\"message\":\"boom\"}", 500, captured);
        MercadoPagoPaymentPreferenceAdapter adapter = adapter(baseUrl, "", "");
        PaymentPreferenceRequest request = new PaymentPreferenceRequest("SUB-1", "Plan Mensual", AMOUNT);

        assertThatThrownBy(() -> adapter.createPreference(request)).isInstanceOf(RuntimeException.class);
    }
}
