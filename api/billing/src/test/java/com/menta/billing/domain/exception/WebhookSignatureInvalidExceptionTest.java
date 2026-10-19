package com.menta.billing.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookSignatureInvalidExceptionTest {

    @Test
    void carries_a_stable_error_code() {
        assertThat(new WebhookSignatureInvalidException().getErrorCode()).isEqualTo("WEBHOOK_SIGNATURE_INVALID");
    }
}
