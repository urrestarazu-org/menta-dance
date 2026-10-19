package com.menta.auth.infrastructure.persistence.adapter;

import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.application.port.out.PasswordResetTokenRepository;
import com.menta.auth.domain.model.PasswordResetToken;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.mapper.PasswordResetTokenJpaMapper;
import com.menta.auth.infrastructure.persistence.repository.PasswordResetTokenJpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for the password-reset-token persistence port. */
@Component
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository repository;

    public PasswordResetTokenRepositoryAdapter(PasswordResetTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public PasswordResetToken save(PasswordResetToken token, DeliveryEnvelope envelope) {
        return PasswordResetTokenJpaMapper.toDomain(
            repository.save(PasswordResetTokenJpaMapper.toJpaEntity(token, envelope))
        );
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Optional<PasswordResetToken> findByHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(PasswordResetTokenJpaMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean consumeIfActive(PasswordResetToken token, Instant now) {
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
}
