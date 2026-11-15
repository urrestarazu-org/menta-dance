package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.InProgressCourse;
import java.util.List;

/**
 * View model of the "Continuar" section of a student's dashboard (#363).
 *
 * @param courses     courses to continue, most recent activity first (empty when unavailable)
 * @param unavailable whether the courses in progress could not be read right now
 */
public record ContinueLearningView(List<InProgressCourse> courses, boolean unavailable) {

    /** Defensive copy, so the view cannot change after it is built. */
    public ContinueLearningView {
        courses = List.copyOf(courses);
    }

    /** Whether the API answered but the student has no course in progress. */
    public boolean empty() {
        return !unavailable && courses.isEmpty();
    }
}
