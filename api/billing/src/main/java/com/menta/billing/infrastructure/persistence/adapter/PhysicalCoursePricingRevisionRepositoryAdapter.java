package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.PhysicalCoursePricingRevisionRepository;
import com.menta.billing.infrastructure.persistence.entity.PhysicalCoursePricingRevisionJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PhysicalCoursePricingRevisionJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link PhysicalCoursePricingRevisionRepository}. */
@Component
public class PhysicalCoursePricingRevisionRepositoryAdapter implements PhysicalCoursePricingRevisionRepository {

    private final PhysicalCoursePricingRevisionJpaRepository revisionRepository;

    public PhysicalCoursePricingRevisionRepositoryAdapter(
        PhysicalCoursePricingRevisionJpaRepository revisionRepository
    ) {
        this.revisionRepository = revisionRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void append(
        String courseId, UUID actorId, String reason, int version, String previousValue, String newValue,
        Instant occurredAt
    ) {
        revisionRepository.save(new PhysicalCoursePricingRevisionJpaEntity(
            UUID.randomUUID(), courseId, actorId, reason, version, previousValue, newValue, occurredAt
        ));
    }
}
