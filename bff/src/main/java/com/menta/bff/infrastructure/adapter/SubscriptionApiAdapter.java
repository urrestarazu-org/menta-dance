package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.CancelledSubscription;
import com.menta.bff.application.dto.CurrentSubscription;
import com.menta.bff.application.dto.SubscriptionCheckout;
import com.menta.bff.application.dto.SubscriptionHistoryEntry;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import com.menta.bff.infrastructure.config.BillingApiProperties;
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
    private static final String HISTORY = "/api/v1/billing/subscriptions/me/history";
    private static final String SUBSCRIPTIONS = "/api/v1/billing/subscriptions";
    private static final String PAYMENT_METHOD = "MERCADO_PAGO";
    private static final String NO_SUBSCRIPTION = "NO_SUBSCRIPTION";
    private static final String NOT_CANCELLABLE = "SUBSCRIPTION_NOT_FOUND";
    private static final String CANCEL_CONFLICT = "SUBSCRIPTION_CONFLICT";
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
    public List<SubscriptionHistoryEntry> listSubscriptionHistory(String accessToken) {
        try {
            HistoryWire[] wire = webClient.get().uri(HISTORY)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .exchangeToMono(SubscriptionApiAdapter::readHistory)
                    .timeout(properties.getTimeout())
                    .block();
            if (wire == null) {
                throw unavailable("subscription history has no body");
            }
            return Arrays.stream(wire).map(SubscriptionApiAdapter::toEntry).toList();
        } catch (BillingApiClient.ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Billing API subscription history call failed: {}", e.getClass().getSimpleName());
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

    @Override
    public CancelledSubscription cancelSubscription(String accessToken) {
        try {
            CancelWire wire = webClient.delete().uri(ME)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .exchangeToMono(SubscriptionApiAdapter::readCancel)
                    .timeout(properties.getTimeout())
                    .block();
            if (wire == null || wire.accessEndsAt() == null) {
                throw unavailable("cancel response is incomplete");
            }
            return new CancelledSubscription(wire.accessEndsAt());
        } catch (SubscriptionApiClient.SubscriptionFailureException
                 | BillingApiClient.ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Billing API subscription cancel call failed: {}", e.getClass().getSimpleName());
            throw new BillingApiClient.ServiceUnavailableException("Billing API unavailable", e);
        }
    }

    private static Mono<CancelWire> readCancel(ClientResponse response) {
        int status = response.statusCode().value();
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(CancelWire.class);
        }
        return response.bodyToMono(ProblemWire.class)
                .onErrorResume(e -> Mono.empty())
                .defaultIfEmpty(new ProblemWire(null, null))
                .flatMap(problem -> Mono.error(mapCancelError(status, problem)));
    }

    private static RuntimeException mapCancelError(int status, ProblemWire problem) {
        if (status == 404 && NOT_CANCELLABLE.equals(problem.code())) {
            return new SubscriptionApiClient.NoCancellableSubscriptionException("No cancellable subscription");
        }
        if (status == 409 && CANCEL_CONFLICT.equals(problem.code())) {
            return new SubscriptionApiClient.SubscriptionConflictException("Subscription changed concurrently");
        }
        return unavailable("answered " + status);
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

    private static Mono<HistoryWire[]> readHistory(ClientResponse response) {
        if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(HistoryWire[].class);
        }
        return response.releaseBody()
                .then(Mono.error(unavailable("answered " + response.statusCode().value())));
    }

    private static SubscriptionHistoryEntry toEntry(HistoryWire wire) {
        HistoryWire item = required(wire);
        return new SubscriptionHistoryEntry(
                required(item.planId()),
                ApiEnums.parse(SubscriptionHistoryEntry.Status.class, required(item.status())),
                item.startDate(), item.endDate(),
                ApiEnums.parse(SubscriptionHistoryEntry.Type.class, required(item.type())));
    }

    private static CurrentSubscription toDomain(CurrentWire wire) {
        if (wire.status() == null) {
            throw unavailable("subscription status is missing");
        }
        return switch (wire.status()) {
            case "ACTIVE" -> new CurrentSubscription.Active(wire.planId(), required(wire.endDate()),
                    wire.daysRemaining(), Boolean.TRUE.equals(wire.expiringSoon()));
            case "EXPIRED" -> new CurrentSubscription.Expired(wire.planId(), required(wire.endDate()));
            case "PENDING" -> new CurrentSubscription.Pending(required(wire.subscriptionId()), wire.planId());
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

    /**
     * The fields of {@code GET /me} the BFF reads; the rest of the body ({@code startDate}, {@code
     * checkoutUrl}, {@code plansUrl}, anything added later) is ignored. Only {@code status} and the
     * fields the state cannot exist without are required; the others are enrichment.
     */
    private record CurrentWire(
            String subscriptionId, String planId, String status, Instant endDate,
            Long daysRemaining, Boolean expiringSoon) {
    }

    /**
     * The fields of a history item the BFF reads; {@code id} and anything added later are ignored.
     * The dates are {@code null} while the subscription is pending payment, so they are optional.
     */
    private record HistoryWire(String planId, String status, Instant startDate, Instant endDate, String type) {
    }

    /** The only field of the cancel answer the BFF reads; the rest of the body is ignored. */
    private record CancelWire(Instant accessEndsAt) {
    }

    private record CheckoutRequest(String planId, String paymentMethod, String idempotencyKey) {
    }

    private record CheckoutWire(String paymentId, String checkoutUrl) {
    }

    private record ProblemWire(String code, Instant currentEndDate) {
    }
}
