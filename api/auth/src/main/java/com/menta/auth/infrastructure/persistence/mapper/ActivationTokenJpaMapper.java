package com.menta.auth.infrastructure.persistence.mapper;

import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.entity.ActivationTokenJpaEntity;

/** Keeps JPA delivery-envelope details outside the activation domain aggregate. */
public final class ActivationTokenJpaMapper {

    private ActivationTokenJpaMapper() {}

    /**
     * Maps the token to its JPA entity without delivery material.
     *
     * <p>The delivery columns of the resulting entity are null, so saving it over an existing row clears them.
     *
     * @return the entity, or null when {@code token} is null
     */
    public static ActivationTokenJpaEntity toJpaEntity(ActivationToken token) {
        return toJpaEntity(token, null);
    }

    /**
     * Maps the token to its JPA entity together with the encrypted delivery material.
     *
     * @param token the aggregate to map
     * @param envelope the delivery material to store in the same row; null leaves the delivery columns empty
     * @return the entity, or null when {@code token} is null
     */
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

    /**
     * Rebuilds the domain token from a row.
     *
     * <p>The delivery envelope is deliberately not read back: it only travels from the use case to storage and
     * to the delivery handler.
     *
     * @return the token, or null when {@code entity} is null
     */
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
