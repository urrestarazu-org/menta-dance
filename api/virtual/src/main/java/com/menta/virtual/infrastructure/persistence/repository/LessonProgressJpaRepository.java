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
     * Resume ordering MUST stay in the query (design.md): MySQL 8.0 orders NULL last under
     * {@code ORDER BY ... DESC}, so a row created only by {@code POST /complete} on a
     * never-played lesson (null {@code position_updated_at}) never wins resume selection over a
     * played one, with no null-handling in this query or in {@code CourseProgressAssembler}.
     *
     * <p>Deliberately JPQL, NOT a native query: {@code virtual_lessons}/{@code virtual_modules}
     * are joined ad hoc (JPA 2.1 {@code ON}-clause join between otherwise-unassociated entities —
     * this module never maps a JPA relationship between them, see {@code
     * VirtualCourseRepositoryAdapter}'s Javadoc) so Hibernate's own {@code UUID}/{@code Instant}
     * conversions apply to the projected columns. A native query here returns raw JDBC {@code
     * byte[]} for every {@code BINARY(16)} column, which Spring Data's interface-projection
     * factory cannot convert to {@code UUID} on its own ({@code
     * LessonProgressRepositoryAdapterProjectionTest} caught this against real MySQL — it never
     * surfaced against Mockito).</p>
     */
    @Query(
        "SELECT p.lessonId AS lessonId, m.id AS moduleId, p.positionSeconds AS positionSeconds, "
            + "p.completed AS completed, p.positionUpdatedAt AS positionUpdatedAt, "
            + "l.displayOrder AS lessonOrder, m.displayOrder AS moduleOrder "
            + "FROM LessonProgressJpaEntity p "
            + "JOIN VirtualLessonJpaEntity l ON l.id = p.lessonId "
            + "JOIN VirtualModuleJpaEntity m ON m.id = l.moduleId "
            + "WHERE p.userId = :userId AND p.courseId = :courseId "
            + "ORDER BY p.positionUpdatedAt DESC, m.displayOrder ASC, l.displayOrder ASC, p.lessonId ASC"
    )
    List<CourseProgressRowProjection> findRowsForUserAndCourse(
        @Param("userId") UUID userId, @Param("courseId") UUID courseId
    );
}
