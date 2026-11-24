package com.menta.virtual.infrastructure.persistence.repository;

import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository of the stored lessons. */
public interface VirtualLessonJpaRepository extends JpaRepository<VirtualLessonJpaEntity, UUID> {

    /**
     * Aggregates lesson count and total duration for the given courses in one grouped query; a
     * course without lessons is absent from the result.
     */
    @Query(
        "SELECT l.courseId AS courseId, COUNT(l) AS lessonCount, "
            + "COALESCE(SUM(l.durationMinutes), 0) AS totalDurationMinutes "
            + "FROM VirtualLessonJpaEntity l WHERE l.courseId IN :courseIds GROUP BY l.courseId"
    )
    List<LessonAggregateProjection> aggregateByCourseIdIn(@Param("courseIds") List<UUID> courseIds);

    /**
     * Same aggregate as {@link #aggregateByCourseIdIn} over the lessons with a video only: what students are
     * shown and counted (#851). A course whose lessons all lack a video is absent from the result.
     */
    @Query(
        "SELECT l.courseId AS courseId, COUNT(l) AS lessonCount, "
            + "COALESCE(SUM(l.durationMinutes), 0) AS totalDurationMinutes "
            + "FROM VirtualLessonJpaEntity l WHERE l.courseId IN :courseIds AND " + LessonJpql.HAS_VIDEO
            + " GROUP BY l.courseId"
    )
    List<LessonAggregateProjection> aggregateWithVideoByCourseIdIn(@Param("courseIds") List<UUID> courseIds);

    /**
     * Counts, per course, the modules that have at least one lesson with a video — the only modules students
     * see (#851); a course without any is absent from the result.
     */
    @Query(
        "SELECT l.courseId AS courseId, COUNT(DISTINCT l.moduleId) AS moduleCount "
            + "FROM VirtualLessonJpaEntity l WHERE l.courseId IN :courseIds AND " + LessonJpql.HAS_VIDEO
            + " GROUP BY l.courseId"
    )
    List<ModuleCountProjection> countModulesWithVideoByCourseIdIn(@Param("courseIds") List<UUID> courseIds);

    /** Management view (US-VIRTUAL-006) — every lesson of a module, ordered for display. */
    List<VirtualLessonJpaEntity> findByModuleIdOrderByDisplayOrderAsc(UUID moduleId);

    /** Every lesson across a course's modules — used for publish-completeness validation. */
    List<VirtualLessonJpaEntity> findByCourseId(UUID courseId);

    /** Every lesson that has a video (not null and not blank), across all courses. */
    @Query("SELECT l FROM VirtualLessonJpaEntity l WHERE " + LessonJpql.HAS_VIDEO)
    List<VirtualLessonJpaEntity> findAllWithVideo();

    /** Counts the lessons of a module. */
    long countByModuleId(UUID moduleId);

    /** Live denominator for the course-progress aggregate (US-VIRTUAL-005, Slice 3), lessons with video only (#851). */
    @Query("SELECT COUNT(l) FROM VirtualLessonJpaEntity l WHERE l.courseId = :courseId AND " + LessonJpql.HAS_VIDEO)
    long countWithVideoByCourseId(@Param("courseId") UUID courseId);

    /** Deletes every lesson of the course. */
    void deleteByCourseId(UUID courseId);
}
