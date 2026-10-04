package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.ClassCheckout;
import com.menta.bff.application.dto.ClassPaymentMethod;
import com.menta.bff.application.dto.ClassPricing;
import com.menta.bff.application.dto.ClassPurchaseType;
import com.menta.bff.application.dto.ClassQuote;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient adapter for billing's in-person class pricing, quote and
 * checkout endpoints.
 *
 * <p>Reuses the {@code billingApiWebClient} bean and its timeout. Unlike
 * {@code BillingApiAdapter} nothing here is cached: pricing is read once per
 * class page, and quotes and checkouts are per-caller writes. Problem-detail
 * bodies are never read — each status maps to a typed exception and the
 * page shows its own fixed copy.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Component
public class PhysicalPurchaseApiAdapter implements PhysicalPurchaseApiClient {

    private static final String PRICING_ENDPOINT = "/api/v1/billing/physical/courses/{courseId}/pricing";
    private static final String QUOTES_ENDPOINT = "/api/v1/billing/physical/quotes";
    private static final String PURCHASES_ENDPOINT = "/api/v1/billing/physical/purchases";
    private static final String AVAILABLE = "AVAILABLE";

    private final WebClient webClient;
    private final BillingApiProperties billingApiProperties;

    public PhysicalPurchaseApiAdapter(
            @Qualifier("billingApiWebClient") WebClient webClient,
            BillingApiProperties billingApiProperties) {
        this.webClient = webClient;
        this.billingApiProperties = billingApiProperties;
    }

    @Override
    public Optional<ClassPricing> findPricing(String courseId) {
        Objects.requireNonNull(courseId, "courseId cannot be null");

        log.debug("Calling billing physical pricing endpoint");

        PricingWire wire = call(
                webClient.get().uri(PRICING_ENDPOINT, courseId),
                PricingWire.class,
                status -> status == 404 ? new PricingNotPublished() : unavailable(status));
        if (wire == null) {
            return Optional.empty();
        }
        return Optional.of(new ClassPricing(wire.monthlyPrice(), wire.currency(), wire.individualSurchargePercent()));
    }

    @Override
    public ClassQuote createQuote(
            String courseId, ClassPurchaseType purchaseType, String sessionId, String accessToken) {
        Objects.requireNonNull(courseId, "courseId cannot be null");
        Objects.requireNonNull(purchaseType, "purchaseType cannot be null");
        Objects.requireNonNull(accessToken, "accessToken cannot be null");

        log.debug("Calling billing physical quote endpoint");

        QuoteWire wire = call(
                webClient.post().uri(QUOTES_ENDPOINT)
                        .headers(headers -> headers.setBearerAuth(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new QuoteRequestWire(courseId, purchaseType.name(), sessionId)),
                QuoteWire.class,
                status -> switch (status) {
                    case 400, 404, 422 -> new QuoteRejectedException("Quote rejected: " + status);
                    default -> unavailable(status);
                });
        return wire.toQuote();
    }

    @Override
    public ClassCheckout checkout(
            String quoteId, ClassPaymentMethod paymentMethod, String idempotencyKey, String accessToken) {
        Objects.requireNonNull(quoteId, "quoteId cannot be null");
        Objects.requireNonNull(paymentMethod, "paymentMethod cannot be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        Objects.requireNonNull(accessToken, "accessToken cannot be null");

        log.debug("Calling billing physical purchase endpoint");

        CheckoutWire wire = call(
                webClient.post().uri(PURCHASES_ENDPOINT)
                        .headers(headers -> headers.setBearerAuth(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new CheckoutRequestWire(quoteId, paymentMethod.name(), idempotencyKey)),
                CheckoutWire.class,
                status -> switch (status) {
                    case 409 -> new CapacityUnavailableException("No capacity left: " + status);
                    case 410 -> new QuoteExpiredException("Quote expired: " + status);
                    case 400 -> new QuoteRejectedException("Checkout rejected: " + status);
                    default -> unavailable(status);
                });
        return wire.toCheckout();
    }

    /**
     * Runs one call and maps a non-2xx status through {@code errors}. Returns
     * {@code null} only for the pricing call's "not published" 404, which is
     * an expected outcome rather than a failure.
     */
    private <T> T call(WebClient.RequestHeadersSpec<?> request, Class<T> type, IntFunction<RuntimeException> errors) {
        try {
            T body = request
                    .exchangeToMono(response -> handle(response, type, errors))
                    .timeout(billingApiProperties.getTimeout())
                    .block();
            if (body == null) {
                throw new ServiceUnavailableException("Billing returned an empty body");
            }
            return body;
        } catch (PricingNotPublished e) {
            return null;
        } catch (QuoteRejectedException | CapacityUnavailableException | QuoteExpiredException
                 | ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Billing physical call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Billing unavailable: " + e.getMessage(), e);
        }
    }

    private static <T> Mono<T> handle(
            ClientResponse response, Class<T> type, IntFunction<RuntimeException> errors) {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(type);
        }
        return response.releaseBody().then(Mono.error(errors.apply(response.statusCode().value())));
    }

    private static RuntimeException unavailable(int status) {
        return new ServiceUnavailableException("Billing call failed: " + status);
    }

    /** Internal signal for the pricing 404; never leaves this adapter. */
    private static final class PricingNotPublished extends RuntimeException {
        private PricingNotPublished() {
            super("Pricing not published", null, false, false);
        }
    }

    private record PricingWire(BigDecimal monthlyPrice, String currency, BigDecimal individualSurchargePercent) {
    }

    private record QuoteRequestWire(String courseId, String purchaseType, String selectedSessionId) {
    }

    private record QuoteWire(
            String id, String courseId, String purchaseType, String selectedSessionId, BigDecimal amount,
            String currency, String availability, Instant expiresAt) {

        private ClassQuote toQuote() {
            return new ClassQuote(
                    id, courseId, ClassPurchaseType.valueOf(purchaseType), selectedSessionId, amount, currency,
                    AVAILABLE.equals(availability), expiresAt);
        }
    }

    private record CheckoutRequestWire(String quoteId, String paymentMethod, String idempotencyKey) {
    }

    private record CheckoutWire(
            String paymentId, String status, String checkoutUrl, BankTransferWire bankTransferInstructions) {

        private ClassCheckout toCheckout() {
            BankTransferWire bank = bankTransferInstructions;
            ClassCheckout.BankTransfer bankTransfer = bank == null || bank.amount() == null
                    ? null
                    : new ClassCheckout.BankTransfer(
                            bank.cbu(), bank.alias(), bank.holder(), bank.cuit(), bank.amount().amount(),
                            bank.amount().currency(), bank.reference());
            return new ClassCheckout(paymentId, status, checkoutUrl, bankTransfer);
        }
    }

    private record BankTransferWire(
            String cbu, String alias, String holder, String cuit, MoneyWire amount, String reference) {
    }

    private record MoneyWire(BigDecimal amount, String currency) {
    }
}
