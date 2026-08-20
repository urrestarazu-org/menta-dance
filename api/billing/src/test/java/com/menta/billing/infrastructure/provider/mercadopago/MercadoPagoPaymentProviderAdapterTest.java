package com.menta.billing.infrastructure.provider.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.billing.application.dto.ProviderPaymentResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real HTTP path against a local {@link HttpServer} instead of
 * mocking {@code RestClient} — the adapter builds its own client internally
 * (ADR-0038), so there is no injectable seam to mock without changing
 * production code.
 */
class MercadoPagoPaymentProviderAdapterTest {

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

    @Test
    void fetchPayment_parses_a_successful_response() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/payments/123", exchange -> respond(
            exchange, 200,
            "{\"status\":\"approved\",\"transaction_amount\":19.99,\"currency_id\":\"ARS\","
                + "\"external_reference\":\"ext-1\",\"collector_id\":555}"
        ));
        server.start();
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        MercadoPagoPaymentProviderAdapter adapter =
            new MercadoPagoPaymentProviderAdapter(baseUrl, "test-token", 2000, 3000);

        ProviderPaymentResult result = adapter.fetchPayment("123");

        assertThat(result.providerStatus()).isEqualTo("approved");
        assertThat(result.amount().getAmount()).isEqualByComparingTo("19.99");
        assertThat(result.amount().getCurrency()).isEqualTo("ARS");
        assertThat(result.externalReference()).isEqualTo("ext-1");
        assertThat(result.merchantAccountId()).isEqualTo("555");
    }

    @Test
    void fetchPayment_throws_when_the_provider_returns_an_empty_body() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/payments/empty", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        MercadoPagoPaymentProviderAdapter adapter =
            new MercadoPagoPaymentProviderAdapter(baseUrl, "test-token", 2000, 3000);

        assertThatThrownBy(() -> adapter.fetchPayment("empty"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("empty payment body");
    }

    @Test
    void fetchPayment_propagates_provider_server_errors() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/payments/boom", exchange -> respond(exchange, 500, "{\"message\":\"boom\"}"));
        server.start();
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        MercadoPagoPaymentProviderAdapter adapter =
            new MercadoPagoPaymentProviderAdapter(baseUrl, "test-token", 2000, 3000);

        assertThatThrownBy(() -> adapter.fetchPayment("boom")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void fetchPayment_propagates_client_errors() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/payments/missing", exchange -> respond(exchange, 404, "{\"message\":\"not found\"}"));
        server.start();
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        MercadoPagoPaymentProviderAdapter adapter =
            new MercadoPagoPaymentProviderAdapter(baseUrl, "test-token", 2000, 3000);

        assertThatThrownBy(() -> adapter.fetchPayment("missing")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void fetchPayment_propagates_read_timeouts() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/payments/slow", exchange -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{}");
        });
        server.start();
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        MercadoPagoPaymentProviderAdapter adapter =
            new MercadoPagoPaymentProviderAdapter(baseUrl, "test-token", 2000, 100);

        assertThatThrownBy(() -> adapter.fetchPayment("slow")).isInstanceOf(RuntimeException.class);
    }
}
