package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.CurrentSubscription;
import com.menta.bff.application.dto.SubscriptionCheckout;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient adapter for the authenticated subscription endpoints of the Billing API.
 *
 * <p>Each call sends {@code Authorization: Bearer <token>}. The token only ever travels in that
 * header: it is never put in a log line or an exception message, and neither are request or
 * response bodies. Every outcome that is not a documented answer (404 with another code, 401,
 * 429, 5xx, an unexpected status, an unreadable or incomplete body, a transport error) becomes a
 * {@link BillingApiClient.ServiceUnavailableException}, so callers fail closed.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Component
public class SubscriptionApiAdapter implements SubscriptionApiClient {

    private static final String ME = "/api/v1/billing/subscriptions/me";
    private static final String SUBSCRIPTIONS = "/api/v1/billing/subscriptions";
    private static final String PAYMENT_METHOD = "MERCADO_PAGO";
    private static final String NO_SUBSCRIPTION = "NO_SUBSCRIPTION";
    private static final String ALREADY_ACTIVE = "SUBSCRIPTION_ALREADY_ACTIVE";
    private static final String PLAN_NOT_AVAILABLE = "PLAN_NOT_AVAILABLE";

    private final WebClient webClient;
    private final BillingApiProperties properties;

    public SubscriptionApiAdapter(
            @Qualifier("billingApiWebClient") WebClient webClient, BillingApiProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public CurrentSubscription getCurrentSubscription(String accessToken) {
        try {
            CurrentSubscription subscription = webClient.get().uri(ME)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .exchangeToMono(this::readCurrent)
                    .timeout(properties.getTimeout())
                    .block();
            if (subscription == null) {
                throw unavailable("subscription response has no body");
            }
            return subscription;
        } catch (BillingApiClient.ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Class name only: nothing from the request (headers, body) may reach a log.
            log.error("Billing API current subscription call failed: {}", e.getClass().getSimpleName());
            throw new BillingApiClient.ServiceUnavailableException("Billing API unavailable", e);
        }
    }

    @Override
    public SubscriptionCheckout createSubscriptionCheckout(String accessToken, String planId, String key) {
        try {
            CheckoutWire checkout = webClient.post().uri(SUBSCRIPTIONS)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .bodyValue(new CheckoutRequest(planId, PAYMENT_METHOD, key))
                    .exchangeToMono(this::readCheckout)
                    .timeout(properties.getTimeout())
                    .block();
            if (checkout == null || checkout.paymentId() == null || checkout.checkoutUrl() == null) {
                throw unavailable("checkout response is incomplete");
            }
            return new SubscriptionCheckout(checkout.paymentId(), checkout.checkoutUrl());
        } catch (SubscriptionApiClient.SubscriptionFailureException
                 | BillingApiClient.ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Billing API subscription checkout call failed: {}", e.getClass().getSimpleName());
            throw new BillingApiClient.ServiceUnavailableException("Billing API unavailable", e);
        }
    }

    private Mono<CheckoutWire> readCheckout(ClientResponse response) {
        int status = response.statusCode().value();
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(CheckoutWire.class);
        }
        return response.bodyToMono(ProblemWire.class)
                .onErrorResume(e -> Mono.empty())
                .defaultIfEmpty(new ProblemWire(null, null))
                .flatMap(problem -> Mono.error(mapCheckoutError(status, problem)));
    }

    private static RuntimeException mapCheckoutError(int status, ProblemWire problem) {
        return switch (status) {
            case 400 -> new SubscriptionApiClient.InvalidRequestException("Billing API rejected the request");
            case 409 -> ALREADY_ACTIVE.equals(problem.code())
                    ? new SubscriptionApiClient.SubscriptionAlreadyActiveException(
                            "Student already holds a subscription", problem.currentEndDate())
                    : new SubscriptionApiClient.SubscriptionConflictException("Subscription changed concurrently");
            case 422 -> PLAN_NOT_AVAILABLE.equals(problem.code())
                    ? new SubscriptionApiClient.PlanNotAvailableException("Plan is not available")
                    : new SubscriptionApiClient.InvalidRequestException("Billing API rejected the request");
            default -> unavailable("answered " + status);
        };
    }

    private Mono<CurrentSubscription> readCurrent(ClientResponse response) {
        int status = response.statusCode().value();
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(CurrentWire.class).map(SubscriptionApiAdapter::toDomain);
        }
        if (status == 404) {
            return response.bodyToMono(ProblemWire.class)
                    .onErrorResume(e -> Mono.empty())
                    .defaultIfEmpty(new ProblemWire(null, null))
                    .flatMap(problem -> NO_SUBSCRIPTION.equals(problem.code())
                            ? Mono.just(new CurrentSubscription.None())
                            : Mono.error(unavailable("unexpected 404")));
        }
        return Mono.error(unavailable("answered " + status));
    }

    private static CurrentSubscription toDomain(CurrentWire wire) {
        if (wire.status() == null) {
            throw unavailable("subscription status is missing");
        }
        return switch (wire.status()) {
            case "ACTIVE" -> new CurrentSubscription.Active(required(wire.endDate()));
            case "EXPIRED" -> new CurrentSubscription.Expired(required(wire.endDate()));
            case "PENDING" -> new CurrentSubscription.Pending(required(wire.subscriptionId()));
            default -> throw unavailable("unknown subscription status");
        };
    }

    private static <T> T required(T value) {
        if (value == null) {
            throw unavailable("subscription response is incomplete");
        }
        return value;
    }

    private static BillingApiClient.ServiceUnavailableException unavailable(String reason) {
        return new BillingApiClient.ServiceUnavailableException("Billing API unavailable: " + reason);
    }

    private record CurrentWire(String subscriptionId, String status, Instant endDate) {
    }

    private record CheckoutRequest(String planId, String paymentMethod, String idempotencyKey) {
    }

    private record CheckoutWire(String paymentId, String checkoutUrl) {
    }

    private record ProblemWire(String code, Instant currentEndDate) {
    }
}
