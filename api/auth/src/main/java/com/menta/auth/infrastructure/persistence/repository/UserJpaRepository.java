package com.menta.auth.infrastructure.persistence.repository;

import com.menta.auth.infrastructure.persistence.entity.UserJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for User.
 * Infrastructure layer - uses Spring annotations.
 */
@Repository
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

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
}
