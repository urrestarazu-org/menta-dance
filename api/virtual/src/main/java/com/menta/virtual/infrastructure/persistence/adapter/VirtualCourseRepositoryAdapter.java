package com.menta.virtual.infrastructure.persistence.adapter;

import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.mapper.VirtualCourseJpaMapper;
import com.menta.virtual.infrastructure.persistence.repository.LessonAggregateProjection;
import com.menta.virtual.infrastructure.persistence.repository.ModuleCountProjection;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for {@link VirtualCourseRepository}.
 *
 * <p>Three flat queries (courses, then module counts, then lesson
 * aggregates), joined in memory — deliberately no JPA relationship between
 * courses/modules/lessons: it would tempt lazy loading and N+1 queries for
 * what is, at this scale, one bounded batch fetch per page (mirrors
 * {@code billing}'s {@code PlanRepositoryAdapter}).</p>
 */
@Component
public class VirtualCourseRepositoryAdapter implements VirtualCourseRepository {

    private final VirtualCourseJpaRepository courseRepository;
    private final VirtualModuleJpaRepository moduleRepository;
    private final VirtualLessonJpaRepository lessonRepository;

    public VirtualCourseRepositoryAdapter(
        VirtualCourseJpaRepository courseRepository,
        VirtualModuleJpaRepository moduleRepository,
        VirtualLessonJpaRepository lessonRepository
    ) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<VirtualCourse> findPublished(CourseId afterCursor, int pageSize) {
        UUID cursor = afterCursor == null ? null : afterCursor.getValue();
        List<VirtualCourseJpaEntity> courses = courseRepository.findByStatusAfterCursor(
            CourseStatus.PUBLISHED, cursor, PageRequest.of(0, pageSize)
        );
        if (courses.isEmpty()) {
            return List.of();
        }

        List<UUID> courseIds = courses.stream().map(VirtualCourseJpaEntity::getId).toList();
        Map<UUID, Long> moduleCounts = moduleRepository.countByCourseIdIn(courseIds).stream()
            .collect(java.util.stream.Collectors.toMap(
                ModuleCountProjection::getCourseId, ModuleCountProjection::getModuleCount
            ));
        Map<UUID, LessonAggregateProjection> lessonAggregates = lessonRepository
            .aggregateByCourseIdIn(courseIds).stream()
            .collect(java.util.stream.Collectors.toMap(LessonAggregateProjection::getCourseId, a -> a));

        return courses.stream()
            .map(course -> VirtualCourseJpaMapper.toDomain(
                course,
                moduleCounts.getOrDefault(course.getId(), 0L).intValue(),
                lessonAggregateOf(lessonAggregates, course.getId(), LessonAggregateProjection::getLessonCount),
                lessonAggregateOf(
                    lessonAggregates, course.getId(), LessonAggregateProjection::getTotalDurationMinutes
                )
            ))
            .toList();
    }

    private static int lessonAggregateOf(
        Map<UUID, LessonAggregateProjection> aggregates,
        UUID courseId,
        java.util.function.Function<LessonAggregateProjection, Long> extractor
    ) {
        LessonAggregateProjection aggregate = aggregates.get(courseId);
        return aggregate == null ? 0 : extractor.apply(aggregate).intValue();
    }
}
