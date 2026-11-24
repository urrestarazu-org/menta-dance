package com.menta.virtual.application.dto;

import java.util.List;

/**
 * A video of the library.
 *
 * @param videoId the library's identifier (guid), the value lessons store as {@code videoId}
 * @param durationSeconds length of the video in seconds
 * @param thumbnailUrl absolute URL of the thumbnail, {@code null} when the library does not report one
 * @param hasMp4Fallback whether the MP4 files the CDN serves exist for this video
 * @param availableResolutions resolution labels already encoded, such as {@code 360p}
 */
public record LibraryVideo(
    String videoId,
    String title,
    LibraryVideoStatus status,
    int durationSeconds,
    String thumbnailUrl,
    boolean hasMp4Fallback,
    List<String> availableResolutions
) {

    public LibraryVideo {
        availableResolutions = availableResolutions == null ? List.of() : List.copyOf(availableResolutions);
    }
}
