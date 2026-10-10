package com.menta.bff.infrastructure.adapter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.menta.bff.application.dto.PhysicalCheckout;
import com.menta.bff.application.dto.PhysicalPurchaseType;
import com.menta.bff.application.dto.PhysicalQuote;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient adapter for the authenticated physical-class purchase endpoints of
 * the Billing API (quote and Mercado Pago checkout).
 *
 * <p>Each call sends {@code Authorization: Bearer <token>}. The token only ever
 * travels in that header: it is never put in a log line or an exception message,
 * and neither are request or response bodies. Failures follow the other
 * adapters: the documented business statuses become typed exceptions, a 404 a
 * {@link BillingApiClient.NotFoundException}, and every other outcome (429, 5xx,
 * 401, an unexpected status, an unreadable body, a transport error) a
 * {@link BillingApiClient.ServiceUnavailableException}.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Component
public class PhysicalPurchaseApiAdapter implements PhysicalPurchaseApiClient {

    private static final String QUOTES = "/api/v1/billing/physical/quotes";
    private static final String PURCHASES = "/api/v1/billing/physical/purchases";
    private static final String PAYMENT_METHOD = "MERCADO_PAGO";
    private static final String NO_SCHEDULED_SESSIONS = "NO_SCHEDULED_SESSIONS";

    private final WebClient webClient;
    private final BillingApiProperties properties;

    public PhysicalPurchaseApiAdapter(
            @Qualifier("billingApiWebClient") WebClient webClient, BillingApiProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public PhysicalQuote createQuote(
            String accessToken, String courseId, PhysicalPurchaseType purchaseType, String selectedSessionId) {
        QuoteWire quote = execute("quote",
                webClient.post().uri(QUOTES).header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .bodyValue(new QuoteRequest(courseId, purchaseType.name(), selectedSessionId)),
                QuoteWire.class);
        if (quote.id() == null || quote.amount() == null || quote.expiresAt() == null) {
            throw unavailable("quote response is incomplete");
        }
        return new PhysicalQuote(quote.id(), purchaseType, quote.scheduledSessionCount(),
                quote.selectedSessionId(), quote.amount(), quote.currency(),
                "UNAVAILABLE".equals(quote.availability()), quote.expiresAt());
    }

    @Override
    public PhysicalCheckout createCheckout(String accessToken, String quoteId, String idempotencyKey) {
        CheckoutWire checkout = execute("checkout",
                webClient.post().uri(PURCHASES).header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .bodyValue(new CheckoutRequest(quoteId, PAYMENT_METHOD, idempotencyKey)),
                CheckoutWire.class);
        if (checkout.paymentId() == null || checkout.checkoutUrl() == null) {
            throw unavailable("checkout response is incomplete");
        }
        return new PhysicalCheckout(checkout.paymentId(), checkout.checkoutUrl());
    }

    private <T> T execute(String operation, WebClient.RequestHeadersSpec<?> request, Class<T> bodyType) {
        try {
            T body = request.exchangeToMono(response -> handle(response, bodyType))
                    .timeout(properties.getTimeout())
                    .block();
            if (body == null) {
                throw unavailable(operation + " response has no body");
            }
            return body;
        } catch (PurchaseFailureException | BillingApiClient.NotFoundException
                 | BillingApiClient.ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Class name only: nothing from the request (headers, body) may reach a log.
            log.error("Billing API {} call failed: {}", operation, e.getClass().getSimpleName());
            throw new BillingApiClient.ServiceUnavailableException("Billing API unavailable", e);
        }
    }

    private <T> Mono<T> handle(ClientResponse response, Class<T> bodyType) {
        HttpStatusCode status = response.statusCode();
        if (status.is2xxSuccessful()) {
            return response.bodyToMono(bodyType);
        }
        Long retryAfter = parseRetryAfter(response.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        return response.bodyToMono(ProblemWire.class)
                .onErrorResume(e -> Mono.empty())
                .defaultIfEmpty(new ProblemWire(null))
                .flatMap(problem -> Mono.error(mapError(status.value(), problem.code(), retryAfter)));
    }

    private RuntimeException mapError(int status, String code, Long retryAfter) {
        return switch (status) {
            case 400 -> new InvalidRequestException("Billing API rejected the request");
            case 409 -> new CapacityUnavailableException("No capacity for the quote");
            case 410 -> new QuoteExpiredException("Quote expired or unknown");
            case 404 -> new BillingApiClient.NotFoundException("Billing resource not found");
            case 422 -> NO_SCHEDULED_SESSIONS.equals(code)
                    ? new NoScheduledSessionsException("No scheduled sessions in the quoted period")
                    : new InvalidRequestException("Billing API rejected the request");
            default -> new BillingApiClient.ServiceUnavailableException(
                    "Billing API answered " + status, retryAfter);
        };
    }

    private static BillingApiClient.ServiceUnavailableException unavailable(String reason) {
        return new BillingApiClient.ServiceUnavailableException("Billing API unavailable: " + reason);
    }

    private static Long parseRetryAfter(String header) {
        if (header == null) {
            return null;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record QuoteRequest(String courseId, String purchaseType, String selectedSessionId) {
    }

    private record CheckoutRequest(String quoteId, String paymentMethod, String idempotencyKey) {
    }

    private record QuoteWire(
            String id,
            int scheduledSessionCount,
            String selectedSessionId,
            BigDecimal amount,
            String currency,
            String availability,
            Instant expiresAt) {
    }

    private record CheckoutWire(String paymentId, String checkoutUrl) {
    }

    private record ProblemWire(String code) {
    }
}
