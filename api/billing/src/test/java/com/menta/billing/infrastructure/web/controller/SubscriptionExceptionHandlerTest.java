package com.menta.billing.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Unit coverage for {@link SubscriptionExceptionHandler} mappings that are not already exercised
 * through a live controller route (US-BILLING-012, D8) — mirrors {@code
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
}
