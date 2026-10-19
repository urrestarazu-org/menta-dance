package com.menta.bff.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("PurchaseFulfillment")
class PurchaseFulfillmentTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "PENDING_FULFILLMENT, PENDING_FULFILLMENT", "ASSIGNED, ASSIGNED",
        "EXCEPTION, EXCEPTION", "RESOLVED, RESOLVED"})
    @DisplayName("known upstream values map to their constant")
    void knownValues(String upstream, PurchaseFulfillment expected) {
        assertThat(PurchaseFulfillment.fromUpstream(upstream)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null and unknown values read as null so today's rendering applies")
    void unknownIsNull() {
        assertThat(PurchaseFulfillment.fromUpstream(null)).isNull();
        assertThat(PurchaseFulfillment.fromUpstream("SOMETHING_NEW")).isNull();
    }
}
