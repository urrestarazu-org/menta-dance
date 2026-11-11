package com.menta.auth.infrastructure.persistence.repository;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.infrastructure.persistence.entity.UserJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for User.
 * Infrastructure layer - uses Spring annotations.
 */
@Repository
public interface UserJpaRepository
    extends JpaRepository<UserJpaEntity, UUID>, JpaSpecificationExecutor<UserJpaEntity> {

    /**
     * Finds a user by its stored email value.
     *
     * @return the user, or empty when none has that email
     */
    Optional<UserJpaEntity> findByEmail(String email);

    /**
     * Tells whether a user with the stored email value exists.
     *
     * @return true when a row with that email exists
     */
    boolean existsByEmail(String email);

    /**
     * Reads a user with {@code SELECT ... FOR UPDATE}. Needs an active transaction.
     *
     * @return the user as currently stored, or empty when none has that id
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserJpaEntity u where u.id = :id")
    Optional<UserJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Locks, in id order, every user with the given role and status ({@code SELECT ... ORDER BY id FOR UPDATE}).
     * The fixed order makes concurrent callers queue up instead of deadlocking. Needs an active transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserJpaEntity u where u.role = :role and u.status = :status order by u.id")
    List<UserJpaEntity> lockAllByRoleAndStatusOrderById(
        @Param("role") Role role, @Param("status") UserStatus status
    );
}
