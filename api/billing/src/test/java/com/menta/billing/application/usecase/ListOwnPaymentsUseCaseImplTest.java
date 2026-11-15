package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.OwnPaymentEntry;
import com.menta.billing.application.port.in.ListOwnPaymentsUseCase;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.exception.InvalidPaymentsLimitException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ListOwnPaymentsUseCaseImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    private PaymentRepository paymentRepository;
    private ListOwnPaymentsUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        useCase = new ListOwnPaymentsUseCaseImpl(paymentRepository);
    }

    private static OwnPaymentEntry entry(Instant createdAt) {
        return new OwnPaymentEntry(
            UUID.randomUUID().toString(), "COMPLETED", Money.of(BigDecimal.TEN, "ARS"), PaymentMethod.MERCADO_PAGO,
            createdAt, new OwnPaymentEntry.Item(OwnPaymentEntry.ItemType.SUBSCRIPTION, "plan-1", "Plan Mensual")
        );
    }

    @Test
    void returns_the_repository_rows_in_the_order_it_gives_them() {
        OwnPaymentEntry newest = entry(NOW);
        OwnPaymentEntry oldest = entry(NOW.minusSeconds(3600));
        when(paymentRepository.findOwnNewestFirst(USER_ID, 20)).thenReturn(List.of(newest, oldest));

        assertThat(useCase.list(USER_ID, 20)).containsExactly(newest, oldest);
    }

    @Test
    void a_user_without_payments_gets_an_empty_list() {
        when(paymentRepository.findOwnNewestFirst(USER_ID, 20)).thenReturn(List.of());

        assertThat(useCase.list(USER_ID, 20)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, ListOwnPaymentsUseCase.MAX_LIMIT})
    void accepts_both_ends_of_the_limit_range(int limit) {
        when(paymentRepository.findOwnNewestFirst(USER_ID, limit)).thenReturn(List.of());

        assertThat(useCase.list(USER_ID, limit)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, ListOwnPaymentsUseCase.MAX_LIMIT + 1})
    void rejects_a_limit_outside_the_range_without_querying(int limit) {
        assertThatThrownBy(() -> useCase.list(USER_ID, limit))
            .isInstanceOf(InvalidPaymentsLimitException.class)
            .extracting("errorCode").isEqualTo("INVALID_REQUEST");
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void the_default_limit_is_twenty_and_the_maximum_is_one_hundred() {
        assertThat(ListOwnPaymentsUseCase.DEFAULT_LIMIT).isEqualTo(20);
        assertThat(ListOwnPaymentsUseCase.MAX_LIMIT).isEqualTo(100);
    }
}
