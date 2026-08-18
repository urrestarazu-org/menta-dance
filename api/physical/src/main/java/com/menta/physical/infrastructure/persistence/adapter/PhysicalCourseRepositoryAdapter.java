package com.menta.physical.infrastructure.persistence.adapter;

import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.infrastructure.persistence.mapper.PhysicalCourseJpaMapper;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link PhysicalCourseRepository}. */
@Component
public class PhysicalCourseRepositoryAdapter implements PhysicalCourseRepository {

    private final PhysicalCourseJpaRepository courseRepository;

    public PhysicalCourseRepositoryAdapter(PhysicalCourseJpaRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<PhysicalCourse> findActive(CourseId afterCursor, int pageSize) {
        UUID cursor = afterCursor == null ? null : afterCursor.getValue();
        return courseRepository
            .findByStatusAfterCursor(CourseStatus.ACTIVE, cursor, PageRequest.of(0, pageSize))
            .stream()
            .map(PhysicalCourseJpaMapper::toDomain)
            .toList();
    }
}
