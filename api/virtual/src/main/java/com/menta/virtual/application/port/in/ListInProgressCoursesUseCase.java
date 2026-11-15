package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.InProgressCourseView;
import java.util.List;
import java.util.UUID;

/** The courses a student is taking, with the lesson to continue each one (#765). */
public interface ListInProgressCoursesUseCase {

    /** Number of courses returned when the caller does not choose one. */
    int DEFAULT_LIMIT = 10;

    /** Largest number of courses a caller may ask for. */
    int MAX_LIMIT = 50;

    /**
     * Returns at most {@code limit} courses of the user, most recent activity first. A course is
     * listed only when it is published, the user has progress in at least one of its lessons, not
     * every lesson is completed and the user currently has access to it (the same rule as the
     * per-course progress). A {@code limit} outside {@code [1, MAX_LIMIT]} fails with {@code
     * InvalidInProgressCoursesLimitException}.
     */
    List<InProgressCourseView> list(UUID actingUserId, int limit);
}
