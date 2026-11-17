package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.OwnPaymentEntry;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.infrastructure.persistence.mapper.PaymentJpaMapper;
import com.menta.billing.infrastructure.persistence.projection.OwnPaymentRow;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class PaymentRepositoryAdapterTest {

    private PaymentJpaRepository jpaRepository;
    private PaymentRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(PaymentJpaRepository.class);
        adapter = new PaymentRepositoryAdapter(jpaRepository);
    }

    private static Payment payment() {
        return new Payment(
            PaymentId.generate(), UUID.randomUUID(), "mp-1", Money.of(BigDecimal.TEN, "ARS"), "ext-1",
            "merchant-1", PaymentMethod.MERCADO_PAGO, new PaymentTarget.Physical("session-1"),
            new PaymentStatus.AwaitingProvider(), Instant.now()
        );
    }

    @Test
    void save_maps_domain_to_entity_and_back() {
        Payment payment = payment();
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Payment saved = adapter.save(payment);

        assertThat(saved.getId()).isEqualTo(payment.getId());
    }

    @Test
    void findById_maps_when_present() {
        Payment payment = payment();
        when(jpaRepository.findById(payment.getId().getValue()))
            .thenReturn(Optional.of(PaymentJpaMapper.toEntity(payment)));

        assertThat(adapter.findById(payment.getId())).isPresent();
    }

    @Test
    void findById_empty_when_absent() {
        PaymentId id = PaymentId.generate();
        when(jpaRepository.findById(id.getValue())).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void findByProviderPaymentId_maps_when_present() {
        Payment payment = payment();
        when(jpaRepository.findByProviderPaymentId("mp-1"))
            .thenReturn(Optional.of(PaymentJpaMapper.toEntity(payment)));

        assertThat(adapter.findByProviderPaymentId("mp-1")).isPresent();
    }

    @Test
    void findByProviderPaymentId_empty_when_absent() {
        when(jpaRepository.findByProviderPaymentId("missing")).thenReturn(Optional.empty());

        assertThat(adapter.findByProviderPaymentId("missing")).isEmpty();
    }

    @Test
    void findByExternalReference_maps_when_present() {
        Payment payment = payment();
        when(jpaRepository.findByExpectedExternalReference("ext-1"))
            .thenReturn(Optional.of(PaymentJpaMapper.toEntity(payment)));

        assertThat(adapter.findByExternalReference("ext-1")).isPresent();
    }

    @Test
    void findByExternalReference_empty_when_absent() {
        when(jpaRepository.findByExpectedExternalReference("missing")).thenReturn(Optional.empty());

        assertThat(adapter.findByExternalReference("missing")).isEmpty();
    }

    // --- #767: the student's own payments ------------------------------------------------------

    private static final UUID OWNER = UUID.randomUUID();
    private static final Instant CREATED_AT = Instant.parse("2026-11-10T12:00:00Z");

    private static OwnPaymentRow virtualRow(String paymentMethod) {
        return new OwnPaymentRow(
            UUID.randomUUID(), "COMPLETED", new BigDecimal("15000.00"), "ARS", paymentMethod, "VIRTUAL", "plan-1",
            CREATED_AT, "Plan Mensual", null
        );
    }

    private static OwnPaymentRow physicalRow(String paymentMethod) {
        return new OwnPaymentRow(
            UUID.randomUUID(), "COMPLETED", new BigDecimal("8000.00"), "ARS", paymentMethod,
            "PHYSICAL", "quote-1", CREATED_AT, null, "course-1"
        );
    }

    private OwnPaymentEntry onlyEntryFor(OwnPaymentRow row) {
        when(jpaRepository.findOwnNewestFirst(eq(OWNER), eq(PageRequest.of(0, 20)))).thenReturn(List.of(row));
        List<OwnPaymentEntry> entries = adapter.findOwnNewestFirst(OWNER, 20);
        assertThat(entries).hasSize(1);
        return entries.get(0);
    }

    @Test
    void findOwnNewestFirst_maps_a_subscription_payment_with_its_plan_name() {
        OwnPaymentRow row = virtualRow("MERCADO_PAGO");

        OwnPaymentEntry entry = onlyEntryFor(row);

        assertThat(entry.paymentId()).isEqualTo(row.id().toString());
        assertThat(entry.status()).isEqualTo("COMPLETED");
        assertThat(entry.amount()).isEqualTo(Money.of(new BigDecimal("15000.00"), "ARS"));
        assertThat(entry.createdAt()).isEqualTo(CREATED_AT);
        assertThat(entry.item()).isEqualTo(
            new OwnPaymentEntry.Item(OwnPaymentEntry.ItemType.SUBSCRIPTION, "plan-1", "Plan Mensual")
        );
    }

    @Test
    void findOwnNewestFirst_returns_the_stored_method_of_a_subscription_payment() {
        assertThat(onlyEntryFor(virtualRow("MERCADO_PAGO")).paymentMethod()).isEqualTo(PaymentMethod.MERCADO_PAGO);
        assertThat(onlyEntryFor(virtualRow("BANK_TRANSFER")).paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    @Test
    void findOwnNewestFirst_maps_a_physical_purchase_to_its_course_without_a_name() {
        OwnPaymentEntry entry = onlyEntryFor(physicalRow("BANK_TRANSFER"));

        assertThat(entry.item()).isEqualTo(
            new OwnPaymentEntry.Item(OwnPaymentEntry.ItemType.PHYSICAL_COURSE, "course-1", null)
        );
    }

    @Test
    void findOwnNewestFirst_returns_the_stored_method_of_a_physical_payment() {
        assertThat(onlyEntryFor(physicalRow("BANK_TRANSFER")).paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(onlyEntryFor(physicalRow("MERCADO_PAGO")).paymentMethod()).isEqualTo(PaymentMethod.MERCADO_PAGO);
    }
}
