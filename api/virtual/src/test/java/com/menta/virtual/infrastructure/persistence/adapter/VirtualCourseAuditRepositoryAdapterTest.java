package com.menta.virtual.infrastructure.persistence.adapter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseAuditJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseAuditJpaRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualCourseAuditRepositoryAdapterTest {

    private final VirtualCourseAuditJpaRepository jpaRepository = mock(VirtualCourseAuditJpaRepository.class);
    private final VirtualCourseAuditRepositoryAdapter adapter =
        new VirtualCourseAuditRepositoryAdapter(jpaRepository);

    @Test
    void appends_a_row_with_the_given_fields() {
        CourseId courseId = CourseId.generate();
        UUID actorId = UUID.randomUUID();

        adapter.append(courseId, actorId, "CREATE_COURSE", null, "title=t");

        verify(jpaRepository).save(any(VirtualCourseAuditJpaEntity.class));
    }
}
