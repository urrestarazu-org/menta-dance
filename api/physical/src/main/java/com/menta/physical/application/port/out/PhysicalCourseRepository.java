package com.menta.physical.application.port.out;

import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.PhysicalCourse;
import java.util.List;

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
}
