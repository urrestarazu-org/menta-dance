package com.menta.bff.application.usecase;

import java.util.List;

/**
 * View model of the list of virtual courses the caller manages.
 *
 * @param rows        courses in the order the API gave them (empty when unavailable)
 * @param unavailable whether the courses could not be obtained
 */
public record ManagedVirtualCoursesView(List<ManagedVirtualCourseRow> rows, boolean unavailable) {

    /** The degraded view shown while the Virtual API is down: there are no rows. */
    public static ManagedVirtualCoursesView unavailableView() {
        return new ManagedVirtualCoursesView(List.of(), true);
    }

    /** Whether the API answered but the caller manages no course. */
    public boolean empty() {
        return !unavailable && rows.isEmpty();
    }
}
