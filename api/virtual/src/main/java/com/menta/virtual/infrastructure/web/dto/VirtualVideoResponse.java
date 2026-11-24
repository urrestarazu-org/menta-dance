package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.ManagedVideo;
import com.menta.virtual.application.dto.VideoLessonUsage;
import java.util.List;

/**
 * One video of {@code GET /api/v1/admin/virtual/videos}. When {@code missingInLibrary} is {@code true} a lesson
 * points at this id but the library has no such video: {@code title}, {@code status}, {@code durationSeconds} and
 * {@code thumbnailUrl} are then {@code null}, {@code hasMp4Fallback} is {@code false} and the resolutions are empty.
 */
public record VirtualVideoResponse(
    String videoId,
    String title,
    String status,
    Integer durationSeconds,
    String thumbnailUrl,
    boolean hasMp4Fallback,
    List<String> availableResolutions,
    boolean missingInLibrary,
    List<VideoLessonResponse> lessons
) {

    /** A lesson that uses the video, with its course and module. */
    public record VideoLessonResponse(
        String courseId, String courseTitle, String moduleId, String moduleTitle, String lessonId, String lessonTitle
    ) {

        static VideoLessonResponse from(VideoLessonUsage usage) {
            return new VideoLessonResponse(
                usage.courseId(), usage.courseTitle(), usage.moduleId(), usage.moduleTitle(), usage.lessonId(),
                usage.lessonTitle()
            );
        }
    }

    /** Maps a managed video; a video missing in the library keeps only its id and its lessons. */
    public static VirtualVideoResponse from(ManagedVideo managed) {
        List<VideoLessonResponse> lessons = managed.lessons().stream().map(VideoLessonResponse::from).toList();
        LibraryVideo video = managed.video();
        if (video == null) {
            return new VirtualVideoResponse(managed.videoId(), null, null, null, null, false, List.of(), true, lessons);
        }
        return new VirtualVideoResponse(
            managed.videoId(), video.title(), video.status().name(), video.durationSeconds(), video.thumbnailUrl(),
            video.hasMp4Fallback(), video.availableResolutions(), false, lessons
        );
    }
}
