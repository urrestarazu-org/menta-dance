package com.menta.virtual.infrastructure.persistence.repository;

import com.menta.virtual.application.port.out.CourseProgressRowProjection;
import com.menta.virtual.infrastructure.persistence.entity.LessonProgressJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonProgressJpaRepository extends JpaRepository<LessonProgressJpaEntity, UUID> {

    Optional<LessonProgressJpaEntity> findByUserIdAndLessonId(UUID userId, UUID lessonId);

    /**
     * Resume ordering MUST stay in native SQL (design.md): MySQL 8.0 orders NULL last under
     * {@code ORDER BY ... DESC}, so a row created only by {@code POST /complete} on a
     * never-played lesson (null {@code position_updated_at}) never wins resume selection over a
     * played one, with no null-handling in this query or in {@code CourseProgressAssembler}.
     */
    @Query(
        value = "SELECT p.lesson_id AS lessonId, m.id AS moduleId, p.position_seconds AS positionSeconds, "
            + "p.completed AS completed, p.position_updated_at AS positionUpdatedAt, "
            + "l.display_order AS lessonOrder, m.display_order AS moduleOrder "
            + "FROM virtual_lesson_progress p "
            + "JOIN virtual_lessons l ON l.id = p.lesson_id "
            + "JOIN virtual_modules m ON m.id = l.module_id "
            + "WHERE p.user_id = :userId AND p.course_id = :courseId "
            + "ORDER BY p.position_updated_at DESC, m.display_order ASC, l.display_order ASC, p.lesson_id ASC",
        nativeQuery = true
    )
    List<CourseProgressRowProjection> findRowsForUserAndCourse(
        @Param("userId") UUID userId, @Param("courseId") UUID courseId
    );
}
