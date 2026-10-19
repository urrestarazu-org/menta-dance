package com.menta.billing.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookTimestampExpiredExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        assertThat(new WebhookTimestampExpiredException().getErrorCode()).isEqualTo("WEBHOOK_TIMESTAMP_EXPIRED");
    }
}
