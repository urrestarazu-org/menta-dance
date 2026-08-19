package com.menta.physical.infrastructure.persistence.repository;

import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhysicalCourseJpaRepository extends JpaRepository<PhysicalCourseJpaEntity, UUID> {

    /**
     * Cursor-paginated: {@code cursor == null} means "first page", otherwise
     * only rows with a strictly greater {@code id} are returned — avoids the
     * OFFSET performance cliff of page-number pagination on a growing table.
     */
    @Query(
        "SELECT c FROM PhysicalCourseJpaEntity c "
            + "WHERE c.status = :status AND (:cursor IS NULL OR c.id > :cursor) "
            + "ORDER BY c.id ASC"
    )
    List<PhysicalCourseJpaEntity> findByStatusAfterCursor(
        @Param("status") CourseStatus status, @Param("cursor") UUID cursor, Pageable pageable
    );

    /** Management view for an INSTRUCTOR (US-PHYSICAL-005) — every status, not just ACTIVE. */
    List<PhysicalCourseJpaEntity> findByProfessorId(UUID professorId);
}
