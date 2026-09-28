package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.dto.PendingVerificationItem;
import com.menta.billing.application.dto.PendingVerificationPage;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.PaymentJpaMapper;
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

@Component
public class PaymentRepositoryAdapter implements PaymentRepository {

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
}
