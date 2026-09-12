package com.menta.billing.infrastructure.persistence.mapper;

import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.infrastructure.persistence.entity.PurchaseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PurchaseSessionJpaEntity;
import java.util.List;
import java.util.stream.IntStream;

/** Manual mapping between {@link PurchaseJpaEntity}/{@link PurchaseSessionJpaEntity} and the domain {@link Purchase}. */
public final class PurchaseJpaMapper {

    private PurchaseJpaMapper() {
    }

    /** {@code physicalSessionIds} must already be ordered by {@code position} (design A2). */
    public static Purchase toDomain(PurchaseJpaEntity entity, List<String> physicalSessionIds) {
        return new Purchase(
            entity.getId(), PaymentId.of(entity.getPaymentId()), physicalSessionIds,
            FulfillmentStatus.valueOf(entity.getStatus())
        );
    }

    public static PurchaseJpaEntity toEntity(Purchase purchase) {
        return new PurchaseJpaEntity(purchase.getId(), purchase.getPaymentId().getValue(), purchase.getStatus().name());
    }

    /** One ordered child row per session, {@code position} = index in the domain list (design A1/A2). */
    public static List<PurchaseSessionJpaEntity> toSessionEntities(Purchase purchase) {
        List<String> sessionIds = purchase.getPhysicalSessionIds();
        return IntStream.range(0, sessionIds.size())
            .mapToObj(position -> new PurchaseSessionJpaEntity(purchase.getId(), position, sessionIds.get(position)))
            .toList();
    }
}
