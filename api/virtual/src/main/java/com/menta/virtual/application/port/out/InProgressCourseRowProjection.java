package com.menta.virtual.application.port.out;

import java.util.UUID;

/**
 * One progress row of a student across every published course, joined with the course and lesson
 * titles (#765). It extends {@link CourseProgressRowProjection} so the rows of one course feed
 * {@code CourseProgressAssembler} unchanged: completed count, percentage and resume lesson keep
 * the exact definition of the per-course progress endpoint.
 *
 * <p>Rows arrive ordered by {@code position_updated_at DESC, course_id ASC, module.display_order
 * ASC, lesson.display_order ASC, lesson_id ASC}. Restricted to one course this is the per-course
 * resume order, and the first row of each course is its most recent activity, so the order in
 * which courses first appear is the most-recent-activity-first order of the listing.</p>
 */
public interface InProgressCourseRowProjection extends CourseProgressRowProjection {

    /** Returns the id of the course the row belongs to. */
    UUID getCourseId();

    /** Returns the title of the course. */
    String getCourseTitle();

    /** Returns the title of the lesson the row describes. */
    String getLessonTitle();
}
