package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.PaymentProofRepository;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentProof;
import com.menta.billing.infrastructure.persistence.entity.PaymentProofJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.PaymentProofJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PaymentProofJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-registers as a {@code @Component}, mirroring {@code PaymentRepositoryAdapter} — no
 * explicit {@code @Bean} wiring needed in {@code BillingConfiguration} (#31, US-BILLING-003,
 * design C5).
 *
 * <p>{@link #save} replaces an existing proof for the same payment by deleting the prior row
 * before inserting the new one. A plain {@code jpaRepository.save(newEntity)} would instead
 * attempt an INSERT keyed on the new proof's own primary key (every {@link
 * PaymentProof#create} birth generates a fresh {@code PaymentProofId}) and collide with the
 * table's {@code UNIQUE KEY} on {@code payment_id} rather than overwrite it. The explicit
 * delete-then-insert keeps the net row keyed on the newest proof's own id, never resurrects
 * the old primary key, and never leaves two rows for one payment.</p>
 */
@Component
public class PaymentProofRepositoryAdapter implements PaymentProofRepository {

    private final PaymentProofJpaRepository jpaRepository;

    public PaymentProofRepositoryAdapter(PaymentProofJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentProof save(PaymentProof proof) {
        jpaRepository.findByPaymentId(proof.getPaymentId().getValue()).ifPresent(existing -> {
            jpaRepository.delete(existing);
            jpaRepository.flush();
        });
        PaymentProofJpaEntity saved = jpaRepository.save(PaymentProofJpaMapper.toEntity(proof));
        return PaymentProofJpaMapper.toDomain(saved);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<PaymentProof> findByPaymentId(PaymentId paymentId) {
        return jpaRepository.findByPaymentId(paymentId.getValue()).map(PaymentProofJpaMapper::toDomain);
    }
}
