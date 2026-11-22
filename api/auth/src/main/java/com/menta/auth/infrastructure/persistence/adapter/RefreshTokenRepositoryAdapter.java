package com.menta.auth.infrastructure.persistence.adapter;

import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.domain.model.RefreshToken;
import com.menta.auth.domain.model.RefreshTokenStatus;
import com.menta.auth.infrastructure.persistence.entity.RefreshTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.mapper.RefreshTokenJpaMapper;
import com.menta.auth.infrastructure.persistence.repository.RefreshTokenJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter implementing RefreshTokenRepository port (ADR-0025, ADR-0027).
 *
 * <p>Every method participates in the caller's transaction so refresh lifecycle
 * mutations and outbox appends share an atomic COMMIT. Default propagation
 * REQUIRED — already-running transactions are joined, fresh transactions
 * reuse the JpaRepository infrastructure.
 *
 * <p>revokeFamily delegates to a JPQL bulk-update that touches ACTIVE|USED rows
 * only; REVOKED rows are preserved (idempotency). The bulk update returns the
 * row count for observability.
 *
 * <p>The revocation instant is read from the injected {@link Clock} here rather than passed through the
 * repository port: the port stays unchanged and the adapter is the only place that stamps the bulk update.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;
    private final Clock clock;

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
            RefreshTokenStatus.REVOKED, clock.now(), familyId
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void revokeAllByUserId(com.menta.auth.domain.model.UserId userId) {
        jpaRepository.bulkRevokeByUser(
            RefreshTokenStatus.REVOKED, clock.now(), userId.getValue()
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
