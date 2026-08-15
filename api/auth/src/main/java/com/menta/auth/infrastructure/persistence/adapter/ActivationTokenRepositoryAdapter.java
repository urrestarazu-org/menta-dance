package com.menta.auth.infrastructure.persistence.adapter;

import com.menta.auth.application.port.out.ActivationTokenRepository;
import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.entity.ActivationTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.mapper.ActivationTokenJpaMapper;
import com.menta.auth.infrastructure.persistence.repository.ActivationTokenJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for the activation-token persistence port. */
@Component
public class ActivationTokenRepositoryAdapter implements ActivationTokenRepository {

    private final ActivationTokenJpaRepository repository;

    public ActivationTokenRepositoryAdapter(ActivationTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public ActivationToken save(ActivationToken token) {
        return saveEntity(ActivationTokenJpaMapper.toJpaEntity(token));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public ActivationToken save(ActivationToken token, DeliveryEnvelope envelope) {
        return saveEntity(ActivationTokenJpaMapper.toJpaEntity(token, envelope));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<ActivationToken> findByHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(ActivationTokenJpaMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean consumeIfActive(ActivationToken token, Instant now) {
        return repository.consumeIfActive(token.getId(), now, now) == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void clearDeliveryEnvelope(UUID tokenId) {
        repository.clearDeliveryEnvelope(tokenId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void invalidateActiveByUserId(UserId userId, Instant now) {
        repository.invalidateActiveByUserId(userId.getValue(), now);
    }

    private ActivationToken saveEntity(ActivationTokenJpaEntity entity) {
        return ActivationTokenJpaMapper.toDomain(repository.save(entity));
    }
}
