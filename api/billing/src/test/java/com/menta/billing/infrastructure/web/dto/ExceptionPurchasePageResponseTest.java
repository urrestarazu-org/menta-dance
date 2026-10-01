package com.menta.billing.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.application.dto.ExceptionPurchaseItem;
import com.menta.billing.application.dto.ExceptionPurchasePage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Plain JUnit coverage for {@link ExceptionPurchasePageResponse#from} (#237, design C5). */
class ExceptionPurchasePageResponseTest {

    @Test
    void from_maps_every_field_from_the_application_page() {
        UUID purchaseId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        ExceptionPurchaseItem item = new ExceptionPurchaseItem(
            purchaseId, paymentId, userId, "PHYSICAL", "quote-1", new BigDecimal("100.00"), "ARS",
            createdAt, List.of("session-1", "session-2")
        );
        ExceptionPurchasePage page = new ExceptionPurchasePage(List.of(item), 0, 20, 1, 1);

        ExceptionPurchasePageResponse response = ExceptionPurchasePageResponse.from(page);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        ExceptionPurchaseItem mapped = response.items().get(0);
        assertThat(mapped.purchaseId()).isEqualTo(purchaseId);
        assertThat(mapped.paymentId()).isEqualTo(paymentId);
        assertThat(mapped.userId()).isEqualTo(userId);
        assertThat(mapped.targetModality()).isEqualTo("PHYSICAL");
        assertThat(mapped.targetReference()).isEqualTo("quote-1");
        assertThat(mapped.amount()).isEqualByComparingTo("100.00");
        assertThat(mapped.currency()).isEqualTo("ARS");
        assertThat(mapped.createdAt()).isEqualTo(createdAt);
        assertThat(mapped.physicalSessionIds()).containsExactly("session-1", "session-2");
    }

    @Test
    void from_survives_an_empty_session_array_for_a_zero_session_row() {
        ExceptionPurchaseItem zeroSessionItem = new ExceptionPurchaseItem(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PHYSICAL", "quote-2",
            new BigDecimal("50.00"), "ARS", Instant.parse("2026-01-02T10:00:00Z"), List.of()
        );
        ExceptionPurchasePage page = new ExceptionPurchasePage(List.of(zeroSessionItem), 0, 20, 1, 1);

        ExceptionPurchasePageResponse response = ExceptionPurchasePageResponse.from(page);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).physicalSessionIds()).isEmpty();
    }
}
