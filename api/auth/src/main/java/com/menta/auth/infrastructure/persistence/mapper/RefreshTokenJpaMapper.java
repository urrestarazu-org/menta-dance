package com.menta.auth.infrastructure.persistence.mapper;

import com.menta.auth.domain.model.RefreshToken;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.entity.RefreshTokenJpaEntity;

import java.util.List;

/**
 * Manual mapper between RefreshTokenJpaEntity and the domain RefreshToken
 * aggregate (ADR-0021 — domain layer has no JPA knowledge).
 *
 * Forward direction (domain → entity) only carries fields the constructor
 * accepts. Reverse direction (entity → domain) uses the explicit
 * RefreshToken.reconstitute factory so the load path can hydrate a row that
 * already carries any status / rotatedAt / revokedAt combination.
 */
public final class RefreshTokenJpaMapper {

    private RefreshTokenJpaMapper() {
        // Utility class — prevent instantiation.
    }

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

    public static List<RefreshToken> toDomainList(List<RefreshTokenJpaEntity> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(RefreshTokenJpaMapper::toDomain).toList();
    }
}
