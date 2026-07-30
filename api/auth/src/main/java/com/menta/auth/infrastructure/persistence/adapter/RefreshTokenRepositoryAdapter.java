package com.menta.auth.infrastructure.persistence.adapter;

import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.domain.model.RefreshToken;
import com.menta.auth.domain.model.RefreshTokenStatus;
import com.menta.auth.infrastructure.persistence.entity.RefreshTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.mapper.RefreshTokenJpaMapper;
import com.menta.auth.infrastructure.persistence.repository.RefreshTokenJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter implementing RefreshTokenRepository port (ADR-0025, ADR-0027).
 *
 * Every method participates in the caller's transaction so refresh lifecycle
 * mutations and outbox appends share an atomic COMMIT. Default propagation
 * REQUIRED — already-running transactions are joined, fresh transactions
 * reuse the JpaRepository infrastructure.
 *
 * revokeFamily delegates to a JPQL bulk-update that touches ACTIVE|USED rows
 * only; REVOKED rows are preserved (idempotency). The bulk update returns the
 * row count for observability.
 */
@Component
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;

    public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash)
            .map(RefreshTokenJpaMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public RefreshToken save(RefreshToken newRefresh) {
        RefreshTokenJpaEntity entity = RefreshTokenJpaMapper.toJpaEntity(newRefresh);
        RefreshTokenJpaEntity saved = jpaRepository.save(entity);
        return RefreshTokenJpaMapper.toDomain(saved);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void revokeFamily(UUID familyId) {
        jpaRepository.bulkRevokeByFamily(
            RefreshTokenStatus.REVOKED, Instant.now(), familyId
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public List<RefreshToken> findActiveOrUsedByFamily(UUID familyId) {
        return RefreshTokenJpaMapper.toDomainList(
            jpaRepository.findByFamilyIdAndStatusIn(
                familyId,
                List.of(RefreshTokenStatus.ACTIVE, RefreshTokenStatus.USED)
            )
        );
    }
}
