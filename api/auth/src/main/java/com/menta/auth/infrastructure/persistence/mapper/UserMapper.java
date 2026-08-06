package com.menta.auth.infrastructure.persistence.mapper;

import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.entity.UserJpaEntity;
import com.menta.shared.domain.vo.Email;

/**
 * Mapper between User domain entity and UserJpaEntity.
 * Manual mapping to avoid framework dependencies in domain.
 */
public final class UserMapper {

    private UserMapper() {
        // Utility class - prevent instantiation
    }

    public static UserJpaEntity toJpaEntity(User user) {
        if (user == null) {
            return null;
        }

        return new UserJpaEntity(
            user.getId().getValue(),
            user.getEmail().getValue(),
            user.getPasswordHash(),
            user.getRole(),
            user.getStatus(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            user.getTokenVersion()
        );
    }

    public static User toDomain(UserJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        return User.rehydrate(
            UserId.of(entity.getId()),
            Email.of(entity.getEmail()),
            entity.getPasswordHash(),
            entity.getRole(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getTokenVersion()
        );
    }
}
