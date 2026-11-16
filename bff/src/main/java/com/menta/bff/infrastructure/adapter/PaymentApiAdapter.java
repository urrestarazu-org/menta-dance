package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.OwnPayment;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.PaymentApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient adapter for the student's own payments in the Billing API (#363).
 *
 * <p>The token only ever travels in the {@code Authorization} header: it is never put in a log line
 * or an exception message, and neither are request or response bodies. Every outcome that is not a
 * usable 200 (any other status, an unreadable or incomplete body, a timeout, a transport error)
 * becomes a {@link BillingApiClient.ServiceUnavailableException}, so callers fail closed. A value of
 * an enum-like field this BFF does not know maps to {@code UNKNOWN} instead.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Component
public class PaymentApiAdapter implements PaymentApiClient {

    private static final String PAYMENTS_ME = "/api/v1/billing/payments/me";

    private final WebClient webClient;
    private final BillingApiProperties properties;

    public PaymentApiAdapter(
            @Qualifier("billingApiWebClient") WebClient webClient, BillingApiProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public List<OwnPayment> listOwnPayments(String accessToken, int limit) {
        try {
            PaymentWire[] wire = webClient.get()
                    .uri(uri -> uri.path(PAYMENTS_ME).queryParam("limit", limit).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .exchangeToMono(PaymentApiAdapter::readPayments)
                    .timeout(properties.getTimeout())
                    .block();
            if (wire == null) {
                throw unavailable("payments response has no body");
            }
            return Arrays.stream(wire).map(PaymentApiAdapter::toDomain).toList();
        } catch (BillingApiClient.ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Class name only: nothing from the request (headers, body) may reach a log.
            log.error("Billing API payments call failed: {}", e.getClass().getSimpleName());
            throw new BillingApiClient.ServiceUnavailableException("Billing API unavailable", e);
        }
    }

    private static Mono<PaymentWire[]> readPayments(ClientResponse response) {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(PaymentWire[].class);
        }
        return response.releaseBody()
                .then(Mono.error(unavailable("answered " + response.statusCode().value())));
    }

    private static OwnPayment toDomain(PaymentWire wire) {
        PaymentWire payment = required(wire);
        ItemWire item = required(payment.item());
        return new OwnPayment(
                ApiEnums.parse(OwnPayment.Status.class, required(payment.status())),
                required(payment.amount()), required(payment.currency()),
                ApiEnums.parse(OwnPayment.Method.class, required(payment.paymentMethod())),
                required(payment.createdAt()),
                ApiEnums.parse(OwnPayment.ItemType.class, required(item.type())), item.name());
    }

    private static <T> T required(T value) {
        if (value == null) {
            throw unavailable("payments response is incomplete");
        }
        return value;
    }

    private static BillingApiClient.ServiceUnavailableException unavailable(String reason) {
        return new BillingApiClient.ServiceUnavailableException("Billing API unavailable: " + reason);
    }

    /**
     * The fields of an own payment the history page shows; the rest ({@code paymentId}, anything
     * added later) is ignored. Only {@code item.name} may be absent: it is {@code null} for a
     * physical course.
     */
    private record PaymentWire(
            String status, BigDecimal amount, String currency, String paymentMethod, Instant createdAt,
            ItemWire item) {
    }

    private record ItemWire(String type, String name) {
    }
}
