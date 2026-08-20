package com.menta.virtual.infrastructure.persistence.repository;

import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VirtualLessonJpaRepository extends JpaRepository<VirtualLessonJpaEntity, UUID> {

    @Query(
        "SELECT l.courseId AS courseId, COUNT(l) AS lessonCount, "
            + "COALESCE(SUM(l.durationMinutes), 0) AS totalDurationMinutes "
            + "FROM VirtualLessonJpaEntity l WHERE l.courseId IN :courseIds GROUP BY l.courseId"
    )
    List<LessonAggregateProjection> aggregateByCourseIdIn(@Param("courseIds") List<UUID> courseIds);

    /** Management view (US-VIRTUAL-006) — every lesson of a module, ordered for display. */
    List<VirtualLessonJpaEntity> findByModuleIdOrderByDisplayOrderAsc(UUID moduleId);

    /** Every lesson across a course's modules — used for publish-completeness validation. */
    List<VirtualLessonJpaEntity> findByCourseId(UUID courseId);

    long countByModuleId(UUID moduleId);
}
