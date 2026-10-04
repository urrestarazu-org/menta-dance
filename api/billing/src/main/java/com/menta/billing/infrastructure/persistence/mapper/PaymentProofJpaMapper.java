package com.menta.billing.infrastructure.persistence.mapper;

import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentProof;
import com.menta.billing.domain.model.PaymentProofId;
import com.menta.billing.infrastructure.persistence.entity.PaymentProofJpaEntity;

/** Flattens {@link PaymentProof} to {@link PaymentProofJpaEntity} columns and back. */
public final class PaymentProofJpaMapper {

    private PaymentProofJpaMapper() {
    }

    /** Rebuilds the domain proof from a stored row, wrapping the raw ids in their value objects. */
    public static PaymentProof toDomain(PaymentProofJpaEntity entity) {
        return new PaymentProof(
            PaymentProofId.of(entity.getId()),
            PaymentId.of(entity.getPaymentId()),
            entity.getStorageKey(),
            entity.getOriginalFilename(),
            entity.getContentType(),
            entity.getSizeBytes(),
            entity.getUploadedAt()
        );
    }

    /**
     * Flattens the domain proof into a row, unwrapping the ids and copying the storage key and file
     * metadata as they are.
     */
    public static PaymentProofJpaEntity toEntity(PaymentProof proof) {
        return new PaymentProofJpaEntity(
            proof.getId().getValue(),
            proof.getPaymentId().getValue(),
            proof.getStorageKey(),
            proof.getOriginalFilename(),
            proof.getContentType(),
            proof.getSizeBytes(),
            proof.getUploadedAt()
        );
    }
}
