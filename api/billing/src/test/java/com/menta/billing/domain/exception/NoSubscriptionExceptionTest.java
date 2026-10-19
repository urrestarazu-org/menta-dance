package com.menta.billing.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoSubscriptionExceptionTest {

    @Test
    void carries_a_stable_error_code_distinct_from_subscription_not_found() {
        NoSubscriptionException exception = new NoSubscriptionException();

        assertThat(exception.getErrorCode()).isEqualTo("NO_SUBSCRIPTION");
        assertThat(exception.getErrorCode())
            .isNotEqualTo(new SubscriptionNotFoundException().getErrorCode());
    }
}
