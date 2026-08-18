package com.menta.virtual.application.port.out;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.List;
import java.util.Optional;

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
}
