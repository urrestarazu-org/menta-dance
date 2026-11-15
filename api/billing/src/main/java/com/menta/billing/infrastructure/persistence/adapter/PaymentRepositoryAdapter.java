package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.dto.OwnPaymentEntry;
import com.menta.billing.application.dto.PendingVerificationItem;
import com.menta.billing.application.dto.PendingVerificationPage;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.usecase.CreateBankTransferPhysicalPurchaseUseCaseImpl;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.PaymentJpaMapper;
import com.menta.billing.infrastructure.persistence.projection.OwnPaymentRow;
import com.menta.billing.infrastructure.persistence.projection.PendingVerificationRow;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for {@link PaymentRepository}, mapping through {@code PaymentJpaMapper} so the domain
 * never sees entities.
 *
 * <p>Writes require an existing transaction ({@code MANDATORY}), while reads join one or open a
 * read-only one. The admin inbox query pages through a projection that carries a correlated proof
 * count, and the expiry candidate query is capped by the {@code limit} argument.
 *
 * <p>The student's own payments (#767) derive the payment method, because {@code billing_payments}
 * does not store it. Both signals are written once at checkout and never changed afterwards:
 * <ul>
 *   <li>a physical payment is a bank transfer when its external reference starts with {@link
 *       CreateBankTransferPhysicalPurchaseUseCaseImpl#EXTERNAL_REFERENCE_PREFIX}, and Checkout Pro
 *       otherwise ({@code "PHY-"});</li>
 *   <li>a subscription payment is Checkout Pro when its subscription has a provider preference: the
 *       Mercado Pago checkout always stores one in the same transaction that creates the payment,
 *       and the bank-transfer checkout never does. Both rails share the {@code "SUB-"} reference,
 *       so the reference alone cannot tell them apart.</li>
 * </ul>
 */
@Component
public class PaymentRepositoryAdapter implements PaymentRepository {

    /** {@code PaymentJpaMapper}'s modality for {@code PaymentTarget.Physical}. */
    private static final String PHYSICAL_MODALITY = "PHYSICAL";

    private final PaymentJpaRepository jpaRepository;

    public PaymentRepositoryAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment save(Payment payment) {
        PaymentJpaEntity saved = jpaRepository.save(PaymentJpaMapper.toEntity(payment));
        return PaymentJpaMapper.toDomain(saved);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<Payment> findById(PaymentId id) {
        return jpaRepository.findById(id.getValue()).map(PaymentJpaMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<Payment> findByProviderPaymentId(String providerPaymentId) {
        return jpaRepository.findByProviderPaymentId(providerPaymentId).map(PaymentJpaMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<Payment> findByExternalReference(String externalReference) {
        return jpaRepository.findByExpectedExternalReference(externalReference).map(PaymentJpaMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<UUID> findExpirableBankTransferIds(Instant createdBefore, int limit) {
        return jpaRepository.findExpirableBankTransferIds(createdBefore, PageRequest.of(0, limit));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public PendingVerificationPage findAwaitingManualVerification(int page, int size) {
        Page<PendingVerificationRow> jpaPage = jpaRepository.findByStatusTypeOrderByCreatedAt(
            "AWAITING_MANUAL_VERIFICATION", PageRequest.of(page, size)
        );
        List<PendingVerificationItem> items = jpaPage.getContent().stream().map(row -> new PendingVerificationItem(
            row.id(), row.userId(), row.targetModality(), row.targetReference(), row.expectedAmount(),
            row.expectedCurrency(), row.statusType(), row.proofCount() > 0, row.createdAt()
        )).toList();
        return new PendingVerificationPage(
            items, page, size, jpaPage.getTotalElements(), jpaPage.getTotalPages()
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<OwnPaymentEntry> findOwnNewestFirst(UUID userId, int limit) {
        return jpaRepository.findOwnNewestFirst(userId, PageRequest.of(0, limit)).stream()
            .map(PaymentRepositoryAdapter::toOwnPaymentEntry)
            .toList();
    }

    private static OwnPaymentEntry toOwnPaymentEntry(OwnPaymentRow row) {
        boolean physical = PHYSICAL_MODALITY.equals(row.targetModality());
        OwnPaymentEntry.Item item = physical
            ? new OwnPaymentEntry.Item(OwnPaymentEntry.ItemType.PHYSICAL_COURSE, row.courseId(), null)
            : new OwnPaymentEntry.Item(OwnPaymentEntry.ItemType.SUBSCRIPTION, row.targetReference(), row.planName());
        return new OwnPaymentEntry(
            row.id().toString(), row.statusType(), Money.of(row.expectedAmount(), row.expectedCurrency()),
            physical ? physicalPaymentMethod(row) : subscriptionPaymentMethod(row), row.createdAt(), item
        );
    }

    private static PaymentMethod physicalPaymentMethod(OwnPaymentRow row) {
        return row.expectedExternalReference().startsWith(
            CreateBankTransferPhysicalPurchaseUseCaseImpl.EXTERNAL_REFERENCE_PREFIX
        ) ? PaymentMethod.BANK_TRANSFER : PaymentMethod.MERCADO_PAGO;
    }

    private static PaymentMethod subscriptionPaymentMethod(OwnPaymentRow row) {
        return row.providerPreferenceId() == null ? PaymentMethod.BANK_TRANSFER : PaymentMethod.MERCADO_PAGO;
    }
}
