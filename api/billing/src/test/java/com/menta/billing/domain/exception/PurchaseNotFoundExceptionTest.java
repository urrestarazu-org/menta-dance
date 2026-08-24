package com.menta.billing.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.FulfillmentStatus;
import org.junit.jupiter.api.Test;

class PurchaseNotFoundExceptionTest {

    @Test
    void exposes_the_stable_error_code_for_a_missing_purchase() {
        PurchaseNotFoundException exception = new PurchaseNotFoundException();

        assertThat(exception.getErrorCode()).isEqualTo("PURCHASE_NOT_FOUND");
        assertThat(exception).hasMessage("No purchase found for the supplied paymentId");
    }

    @Test
    void includes_the_assumed_status_in_the_diagnostic_message() {
        PurchaseNotFoundException exception = new PurchaseNotFoundException(
            FulfillmentStatus.EXCEPTION
        );

        assertThat(exception.getErrorCode()).isEqualTo("PURCHASE_NOT_FOUND");
        assertThat(exception).hasMessage(
            "No purchase found for the supplied paymentId (assumed status=EXCEPTION)"
        );
    }
}
