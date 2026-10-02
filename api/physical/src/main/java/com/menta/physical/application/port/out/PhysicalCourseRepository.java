package com.menta.physical.application.port.out;

import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalCourse;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link PhysicalCourse}. */
public interface PhysicalCourseRepository {

    /**
     * Active courses ordered by {@code id} ascending, cursor-paginated so a
     * caller never forces a full-table scan.
     *
     * @param afterCursor {@code null} for the first page; otherwise the last
     *     {@code CourseId} seen on the previous page — only rows with a
     *     strictly greater id are returned.
     * @param pageSize maximum number of courses to return.
     */
    List<PhysicalCourse> findActive(CourseId afterCursor, int pageSize);

    /**
     * @param courseId the course to look up.
     * @return the course if it exists and is {@code ACTIVE}; {@code
     *     Optional.empty()} otherwise (not found or inactive).
     */
    Optional<PhysicalCourse> findActiveById(CourseId courseId);

    /**
     * Batch counterpart of {@link #findActiveById(CourseId)}: same visibility,
     * resolved with a single query regardless of how many ids are requested.
     *
     * @param courseIds the courses to look up; an empty collection performs
     *     no query.
     * @return the requested courses that exist and are {@code ACTIVE}, in no
     *     particular order; unknown and inactive ids are omitted without
     *     distinguishing them.
     */
    List<PhysicalCourse> findActiveByIds(Collection<CourseId> courseIds);

    /**
     * Unfiltered by status — management endpoints (US-PHYSICAL-005) must be
     * able to load and edit an {@code INACTIVE} course too, unlike the
     * public catalog read path above.
     */
    Optional<PhysicalCourse> findById(CourseId courseId);

    /** Every course regardless of status or owner — the ADMIN management view. */
    List<PhysicalCourse> findAll();

    /** Every course owned by {@code professorId}, regardless of status — the INSTRUCTOR management view. */
    List<PhysicalCourse> findByProfessorId(UUID professorId);

    /** Creates a new course or updates an existing one (matched by {@link PhysicalCourse#getId()}). */
    PhysicalCourse save(PhysicalCourse course);
}
