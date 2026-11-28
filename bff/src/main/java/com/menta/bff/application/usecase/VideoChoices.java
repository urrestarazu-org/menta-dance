package com.menta.bff.application.usecase;

import java.util.List;

/**
 * The videos the lesson form offers to pick (#843).
 *
 * @param options       what the selector lists, in the API's order; always contains {@code selected} when there is one
 * @param selected      the option of the lesson's current (or just submitted) video, {@code null} for none
 * @param totalElements videos the library has, across every page
 * @param truncated     whether the library has more videos than {@code options} shows, so the rest can only be pasted
 * @param unavailable   whether the list could not be obtained (the form is still saved with a pasted identifier)
 */
public record VideoChoices(
        List<VideoOption> options, VideoOption selected, long totalElements, boolean truncated,
        boolean unavailable) {

    /** The degraded view: no list, nothing known about the current video. */
    public static VideoChoices unavailableChoices() {
        return new VideoChoices(List.of(), null, 0, false, true);
    }

    /** Whether the selector can be shown: the API answered. */
    public boolean available() {
        return !unavailable;
    }

    /** Whether the form has a video chosen. */
    public boolean hasSelection() {
        return selected != null;
    }

    /** The identifier of the selected option, {@code null} when there is none. */
    public String selectedId() {
        return selected == null ? null : selected.videoId();
    }
}
