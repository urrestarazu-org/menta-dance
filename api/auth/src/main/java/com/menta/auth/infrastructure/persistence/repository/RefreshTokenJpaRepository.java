package com.menta.auth.infrastructure.persistence.repository;

import com.menta.auth.domain.model.RefreshTokenStatus;
import com.menta.auth.infrastructure.persistence.entity.RefreshTokenJpaEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for auth_refresh_tokens.
 *
 * The bulk update and the ACTIVE|USED finder are derived queries that
 * map naturally to the (family_id, status) index declared in V2 DDL. The
 * find-by-hash query uses the UNIQUE index on token_hash.
 */
@Repository
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    List<RefreshTokenJpaEntity> findByFamilyIdAndStatusIn(
        UUID familyId, Collection<RefreshTokenStatus> statuses
    );

    /**
     * Bulk-marks every refresh in the given family whose status is in
     * ACTIVE|USED as REVOKED. Returns the number of rows updated so the
     * adapter can log/observe the blast radius of a compromise.
     *
     * revokedAt comes from the application clock rather than JPQL
     * CURRENT_TIMESTAMP (portable across MySQL/H2 + mimics the per-row
     * semantics when this slice uses H2 spring.test profile).
     */
    @Modifying
    @Query("""
        UPDATE RefreshTokenJpaEntity r
           SET r.status = :status,
               r.revokedAt = :revokedAt
         WHERE r.familyId = :familyId
           AND r.status IN (com.menta.auth.domain.model.RefreshTokenStatus.ACTIVE,
                            com.menta.auth.domain.model.RefreshTokenStatus.USED)
        """)
    int bulkRevokeByFamily(
        @Param("status") RefreshTokenStatus status,
        @Param("revokedAt") Instant revokedAt,
        @Param("familyId") UUID familyId
    );

    /**
     * Bulk-marks every refresh owned by this user whose status is in
     * ACTIVE|USED as REVOKED, across every family — not just one (US-AUTH-006:
     * a password reset must close every session, on every device).
     */
    @Modifying
    @Query("""
        UPDATE RefreshTokenJpaEntity r
           SET r.status = :status,
               r.revokedAt = :revokedAt
         WHERE r.userId = :userId
           AND r.status IN (com.menta.auth.domain.model.RefreshTokenStatus.ACTIVE,
                            com.menta.auth.domain.model.RefreshTokenStatus.USED)
        """)
    int bulkRevokeByUser(
        @Param("status") RefreshTokenStatus status,
        @Param("revokedAt") Instant revokedAt,
        @Param("userId") UUID userId
    );
}
