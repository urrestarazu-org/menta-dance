package com.menta.billing.infrastructure.persistence.mapper;

import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.domain.model.PurchaseResolution;
import com.menta.billing.domain.model.PurchaseResolutionType;
import com.menta.billing.infrastructure.persistence.entity.PurchaseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PurchaseSessionJpaEntity;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Manual mapping between {@link PurchaseJpaEntity}/{@link PurchaseSessionJpaEntity} and the domain {@link Purchase}.
 */
public final class PurchaseJpaMapper {

    private PurchaseJpaMapper() {
    }

    /** {@code physicalSessionIds} must already be ordered by {@code position} (design A2). */
    public static Purchase toDomain(PurchaseJpaEntity entity, List<String> physicalSessionIds) {
        PurchaseResolution resolution = entity.getResolution() == null ? null : new PurchaseResolution(
            PurchaseResolutionType.valueOf(entity.getResolution()), entity.getResolutionNote(),
            entity.getResolvedBy(), entity.getResolvedAt()
        );
        return new Purchase(
            entity.getId(), PaymentId.of(entity.getPaymentId()), physicalSessionIds,
            FulfillmentStatus.valueOf(entity.getStatus()), resolution
        );
    }

    /**
     * Flattens the purchase into its row.
     *
     * <p>The row holds the id, the payment id, the status name and the resolution record, if any; the
     * session ids are written separately through {@link #toSessionEntities}.
     */
    public static PurchaseJpaEntity toEntity(Purchase purchase) {
        PurchaseResolution resolution = purchase.getResolution().orElse(null);
        return new PurchaseJpaEntity(
            purchase.getId(), purchase.getPaymentId().getValue(), purchase.getStatus().name(),
            resolution == null ? null : resolution.type().name(),
            resolution == null ? null : resolution.note(),
            resolution == null ? null : resolution.resolvedBy(),
            resolution == null ? null : resolution.resolvedAt()
        );
    }

    /** One ordered child row per session, {@code position} = index in the domain list (design A1/A2). */
    public static List<PurchaseSessionJpaEntity> toSessionEntities(Purchase purchase) {
        List<String> sessionIds = purchase.getPhysicalSessionIds();
        return IntStream.range(0, sessionIds.size())
            .mapToObj(position -> new PurchaseSessionJpaEntity(purchase.getId(), position, sessionIds.get(position)))
            .toList();
    }
}
