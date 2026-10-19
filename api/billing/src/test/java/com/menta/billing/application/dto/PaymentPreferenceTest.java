package com.menta.billing.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.billing.domain.model.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentPreferenceTest {

    private static final Money AMOUNT = Money.of(BigDecimal.TEN, "ARS");

    @Test
    void a_request_carries_our_reference_the_title_and_the_amount() {
        PaymentPreferenceRequest request = new PaymentPreferenceRequest("SUB-1", "Plan Mensual", AMOUNT);

        assertThat(request.externalReference()).isEqualTo("SUB-1");
        assertThat(request.title()).isEqualTo("Plan Mensual");
        assertThat(request.amount()).isEqualTo(AMOUNT);
    }

    /** Handing the provider a blank reference would leave the later webhook nothing to correlate against. */
    @Test
    void a_request_refuses_a_missing_external_reference_title_or_amount() {
        assertThatThrownBy(() -> new PaymentPreferenceRequest(null, "Plan", AMOUNT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentPreferenceRequest(" ", "Plan", AMOUNT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentPreferenceRequest("SUB-1", null, AMOUNT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentPreferenceRequest("SUB-1", " ", AMOUNT))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentPreferenceRequest("SUB-1", "Plan", null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void a_result_carries_the_preference_id_and_its_checkout_url() {
        PaymentPreferenceResult result = new PaymentPreferenceResult("pref-1", "https://mp.example/pref-1");

        assertThat(result.preferenceId()).isEqualTo("pref-1");
        assertThat(result.checkoutUrl()).isEqualTo("https://mp.example/pref-1");
    }

    @Test
    void a_result_refuses_a_missing_preference_id_or_url() {
        assertThatThrownBy(() -> new PaymentPreferenceResult(null, "https://mp.example/pref-1"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PaymentPreferenceResult("pref-1", null))
            .isInstanceOf(NullPointerException.class);
    }
}
