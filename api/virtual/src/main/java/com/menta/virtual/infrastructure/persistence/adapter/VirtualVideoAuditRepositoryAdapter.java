package com.menta.virtual.infrastructure.persistence.adapter;

import com.menta.virtual.application.port.out.Clock;
import com.menta.virtual.application.port.out.VirtualVideoAuditRepository;
import com.menta.virtual.infrastructure.persistence.entity.VirtualVideoAuditJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualVideoAuditJpaRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for {@link VirtualVideoAuditRepository} (#841). The instant comes from the module's {@link Clock}
 * port (ADR-0048), not from {@code Instant.now()}.
 */
@Component
@RequiredArgsConstructor
public class VirtualVideoAuditRepositoryAdapter implements VirtualVideoAuditRepository {

    private final VirtualVideoAuditJpaRepository auditRepository;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void append(String videoId, UUID actorId, String action, String previousValue, String newValue) {
        auditRepository.save(new VirtualVideoAuditJpaEntity(
            UUID.randomUUID(), videoId, actorId, action, previousValue, newValue, clock.now()
        ));
    }
}
