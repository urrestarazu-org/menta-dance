package com.menta.billing.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.exception.BankTransferRateLimitedException;
import com.menta.billing.domain.exception.BillingDegradedException;
import com.menta.billing.domain.exception.NoSubscriptionException;
import com.menta.billing.domain.exception.UserNotFoundException;
import com.menta.billing.infrastructure.web.dto.CurrentSubscriptionResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Unit coverage for {@link SubscriptionExceptionHandler} mappings that are not already exercised
 * through a live controller route (US-BILLING-012, D8/A14) — mirrors {@code
 * PhysicalCourseQuoteExceptionHandlerTest}'s direct-instantiation pattern.
 */
class SubscriptionExceptionHandlerTest {

    private final SubscriptionExceptionHandler handler = new SubscriptionExceptionHandler();

    @Test
    void maps_user_not_found_to_404() {
        ResponseEntity<ProblemDetail> response = handler.userNotFound(new UserNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void optimisticLock() {
        ResponseEntity<ProblemDetail> response = handler.optimisticLockConflict(
            new ObjectOptimisticLockingFailureException(
                com.menta.billing.domain.model.Subscription.class, java.util.UUID.randomUUID()
            )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("SUBSCRIPTION_CONFLICT");
    }

    /** US-BILLING-004, D2: distinct code and shape from {@code subscriptionNotFound}'s 404. */
    @Test
    void maps_no_subscription_to_404_with_a_plans_hint() {
        ResponseEntity<ProblemDetail> response = handler.noSubscription(new NoSubscriptionException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("NO_SUBSCRIPTION");
        assertThat(response.getBody().getProperties().get("plansUrl")).isEqualTo(CurrentSubscriptionResponse.PLANS_URL);
    }

    /** US-BILLING-003, design C7: a spent daily budget maps to 429 with Retry-After, never a 500. */
    @Test
    void maps_bank_transfer_rate_limited_to_429_with_retry_after() {
        ResponseEntity<ProblemDetail> response =
            handler.bankTransferRateLimited(new BankTransferRateLimitedException(Duration.ofMinutes(30)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1800");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("BANK_TRANSFER_RATE_LIMITED");
    }

    /** US-BILLING-003, design C7: an unreachable Redis fails closed with 503, never silently unlimited. */
    @Test
    void maps_billing_degraded_to_503_with_retry_after() {
        ResponseEntity<ProblemDetail> response =
            handler.degraded(new BillingDegradedException(new RuntimeException("connection refused")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().getProperties().get("code")).isEqualTo("BILLING_DEGRADED");
    }
}
