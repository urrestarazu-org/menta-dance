package com.menta.bff.application.usecase;

import java.util.List;

/**
 * The instructors an administrator can assign a course to.
 *
 * @param options     the instructors of the first page the Auth API returns
 * @param truncated   whether there are more instructors than the ones listed
 * @param unavailable whether the instructors could not be obtained
 */
public record InstructorOptions(List<Option> options, boolean truncated, boolean unavailable) {

    /**
     * One instructor to choose.
     *
     * @param id   account identifier
     * @param name what the Physical API stores as the professor's name and the select shows
     */
    public record Option(String id, String name) {
    }

    /** The degraded answer while the Auth API is down: nobody to choose. */
    public static InstructorOptions unavailableOptions() {
        return new InstructorOptions(List.of(), false, true);
    }

    /** Whether the id belongs to one of the listed instructors. */
    public boolean contains(String id) {
        return options.stream().anyMatch(option -> option.id().equalsIgnoreCase(id));
    }

    /** Name of the listed instructor with that id, or {@code null} when it is not listed. */
    public String nameOf(String id) {
        return options.stream().filter(option -> option.id().equalsIgnoreCase(id))
                .map(Option::name).findFirst().orElse(null);
    }
}
