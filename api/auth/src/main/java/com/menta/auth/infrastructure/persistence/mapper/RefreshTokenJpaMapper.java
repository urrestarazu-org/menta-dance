package com.menta.auth.infrastructure.persistence.mapper;

import com.menta.auth.domain.model.RefreshToken;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.entity.RefreshTokenJpaEntity;
import java.util.List;

/**
 * Manual mapper between RefreshTokenJpaEntity and the domain RefreshToken
 * aggregate (ADR-0021 — domain layer has no JPA knowledge).
 *
 * <p>Forward direction (domain → entity) only carries fields the constructor
 * accepts. Reverse direction (entity → domain) uses the explicit
 * RefreshToken.reconstitute factory so the load path can hydrate a row that
 * already carries any status / rotatedAt / revokedAt combination.
 */
public final class RefreshTokenJpaMapper {

    private RefreshTokenJpaMapper() {
        // Utility class — prevent instantiation.
    }

    /**
     * Maps the token, with its status and rotation/revocation timestamps, to its JPA entity.
     *
     * @return the entity, or null when {@code token} is null
     */
    public static RefreshTokenJpaEntity toJpaEntity(RefreshToken token) {
        if (token == null) {
            return null;
        }
        return new RefreshTokenJpaEntity(
            token.getId(),
            token.getFamilyId(),
            token.getTokenHash(),
            token.getUserId().getValue(),
            token.getStatus(),
            token.getTokenVersion(),
            token.getExpiresAt(),
            token.getCreatedAt(),
            token.getRotatedAt(),
            token.getRevokedAt()
        );
    }

    /**
     * Rebuilds the domain token from a row, whatever status it is in.
     *
     * @return the token, or null when {@code entity} is null
     */
    public static RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return RefreshToken.reconstitute(
            entity.getId(),
            entity.getFamilyId(),
            entity.getTokenHash(),
            UserId.of(entity.getUserId()),
            entity.getStatus(),
            entity.getTokenVersion(),
            entity.getExpiresAt(),
            entity.getCreatedAt(),
            entity.getRotatedAt(),
            entity.getRevokedAt()
        );
    }

    /**
     * Maps every row of the list, preserving order.
     *
     * @return the tokens, or an empty list when {@code entities} is null
     */
    public static List<RefreshToken> toDomainList(List<RefreshTokenJpaEntity> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(RefreshTokenJpaMapper::toDomain).toList();
    }
}
