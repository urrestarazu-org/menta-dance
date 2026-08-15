package com.menta.auth.infrastructure.persistence.mapper;

import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.entity.ActivationTokenJpaEntity;

/** Keeps JPA delivery-envelope details outside the activation domain aggregate. */
public final class ActivationTokenJpaMapper {

    private ActivationTokenJpaMapper() { }

    public static ActivationTokenJpaEntity toJpaEntity(ActivationToken token) {
        return toJpaEntity(token, null);
    }

    public static ActivationTokenJpaEntity toJpaEntity(
        ActivationToken token, DeliveryEnvelope envelope
    ) {
        if (token == null) {
            return null;
        }
        return new ActivationTokenJpaEntity(
            token.getId(), token.getUserId().getValue(), token.getTokenHash(),
            envelope == null ? null : envelope.getCiphertext(),
            envelope == null ? null : envelope.getNonce(),
            envelope == null ? null : (short) envelope.getKeyVersion(),
            token.getExpiresAt(), token.getCreatedAt(), token.getUsedAt(), token.getInvalidatedAt()
        );
    }

    public static ActivationToken toDomain(ActivationTokenJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return ActivationToken.reconstitute(
            entity.getId(), UserId.of(entity.getUserId()), entity.getTokenHash(),
            entity.getExpiresAt(), entity.getCreatedAt(), entity.getUsedAt(), entity.getInvalidatedAt()
        );
    }
}
