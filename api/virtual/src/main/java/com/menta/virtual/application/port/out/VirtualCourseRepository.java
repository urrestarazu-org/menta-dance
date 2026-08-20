package com.menta.virtual.application.port.out;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link VirtualCourse}. */
public interface VirtualCourseRepository {

    /**
     * Published courses ordered by {@code id} ascending, cursor-paginated so
     * a caller never forces a full-table scan.
     *
     * @param afterCursor {@code null} for the first page; otherwise the last
     *     {@code CourseId} seen on the previous page — only rows with a
     *     strictly greater id are returned.
     * @param pageSize maximum number of courses to return.
     */
    List<VirtualCourse> findPublished(CourseId afterCursor, int pageSize);

    /**
     * @param courseId the course to look up.
     * @return the course if it exists and is {@code PUBLISHED}; {@code
     *     Optional.empty()} otherwise (not found or not published).
     */
    Optional<VirtualCourse> findPublishedById(CourseId courseId);

    /**
     * Unfiltered by status — management endpoints (US-VIRTUAL-006) must be
     * able to load and edit a {@code DRAFT} or {@code ARCHIVED} course too,
     * unlike the public catalog read path above.
     */
    Optional<VirtualCourse> findById(CourseId courseId);

    /** Every course regardless of status or owner — the ADMIN management view. */
    List<VirtualCourse> findAll();

    /** Every course owned by {@code professorId}, regardless of status — the INSTRUCTOR management view. */
    List<VirtualCourse> findByProfessorId(UUID professorId);

    /** Creates a new course or updates an existing one (matched by {@link VirtualCourse#getId()}). */
    VirtualCourse save(VirtualCourse course);

    /** Deletes a course by id. Callers must have already verified it is {@code DRAFT}. */
    void delete(CourseId courseId);
}
