package com.menta.billing.infrastructure.persistence.adapter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.menta.billing.infrastructure.persistence.entity.PhysicalCoursePricingRevisionJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PhysicalCoursePricingRevisionJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PhysicalCoursePricingRevisionRepositoryAdapterTest {

    @Test
    void append_saves_a_new_revision_row() {
        PhysicalCoursePricingRevisionJpaRepository jpaRepository =
            mock(PhysicalCoursePricingRevisionJpaRepository.class);
        PhysicalCoursePricingRevisionRepositoryAdapter adapter =
            new PhysicalCoursePricingRevisionRepositoryAdapter(jpaRepository);
        UUID actorId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        adapter.append("course-1", actorId, "motivo", 2, "previous", "new", occurredAt);

        verify(jpaRepository).save(any(PhysicalCoursePricingRevisionJpaEntity.class));
    }
}
