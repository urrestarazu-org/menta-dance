package com.menta.bff.application.dto;

import java.util.List;

/**
 * A video of the library as the management API reports it ({@code VirtualVideoResponse}).
 *
 * @param videoId              identifier (guid) of the video, the {@code videoId} of the lessons that use it
 * @param title                title in the library; {@code null} when the video no longer exists there
 * @param status               processing state; {@link VideoStatus#UNKNOWN} when there is none
 * @param durationSeconds      length in seconds; {@code null} when the library does not say
 * @param thumbnailUrl         thumbnail as the API gave it, not checked yet; {@code null} when there is none
 * @param hasMp4Fallback       whether the video has an MP4 copy (without it, it does not play with the CDN's
 *                             current configuration)
 * @param availableResolutions labels of the resolutions already encoded
 * @param missingInLibrary     whether a lesson uses this identifier but the library no longer has the video
 * @param lessons              the lessons that use the video (the caller's own for an instructor)
 */
public record ManagedVideo(
        String videoId, String title, VideoStatus status, Integer durationSeconds, String thumbnailUrl,
        boolean hasMp4Fallback, List<String> availableResolutions, boolean missingInLibrary,
        List<VideoLesson> lessons) {
}
