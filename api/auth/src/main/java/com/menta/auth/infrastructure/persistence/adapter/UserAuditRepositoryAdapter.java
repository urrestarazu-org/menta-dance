package com.menta.auth.infrastructure.persistence.adapter;

import com.menta.auth.application.port.out.UserAuditRepository;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.infrastructure.persistence.entity.UserAuditJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.UserAuditJpaRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for the user audit trail (#378), mirroring the physical device audit adapter: it joins the caller's
 * transaction and never decides the timestamp, which the application passes in.
 */
@Component
@RequiredArgsConstructor
public class UserAuditRepositoryAdapter implements UserAuditRepository {

    private final UserAuditJpaRepository auditRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void append(
        UserId userId, UUID actorId, String action, String previousValue, String newValue, Instant occurredAt
    ) {
        auditRepository.save(new UserAuditJpaEntity(
            UUID.randomUUID(), userId.getValue(), actorId, action, previousValue, newValue, occurredAt
        ));
    }
}
