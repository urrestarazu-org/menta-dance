package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.ManagedVideo;
import com.menta.virtual.application.dto.VideoLessonUsage;
import com.menta.virtual.application.port.in.RenameLibraryVideoUseCase;
import com.menta.virtual.application.port.out.VideoLibraryPort;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.application.port.out.VirtualVideoAuditRepository;
import com.menta.virtual.domain.exception.VideoNotFoundException;
import com.menta.virtual.domain.exception.VideoNotOwnedException;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualLesson;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Default {@link RenameLibraryVideoUseCase}. Deliberately NOT wrapped in a transaction: the work is one call to the
 * library (up to its timeouts) between two single-statement reads/writes, and a database transaction held open
 * across an external call is worse than the gap it would close.
 *
 * <p>Order: validate, resolve who may act (an instructor needs a lesson of their own course that uses the video;
 * the courses decide, never the library), check the library has the video, rename, then audit. The audit row is
 * written after the rename, so a failed rename leaves no trace of something that did not happen; if the audit
 * insert itself fails the rename stands and the error is returned, and renaming again to the same title repairs
 * the trail.</p>
 */
@RequiredArgsConstructor
public class RenameLibraryVideoUseCaseImpl implements RenameLibraryVideoUseCase {

    static final String ACTION = "RENAME_VIDEO";

    private final VideoLibraryPort library;
    private final VirtualCourseRepository courseRepository;
    private final VirtualModuleRepository moduleRepository;
    private final VirtualLessonRepository lessonRepository;
    private final VirtualVideoAuditRepository auditRepository;

    @Override
    public ManagedVideo rename(String videoId, String title, UUID actingUserId, boolean actingAsAdmin) {
        VideoUsages.requireValidId(videoId);
        String newTitle = requireValidTitle(title);
        List<VirtualLesson> lessons = lessonRepository.findByVideoId(videoId).stream()
            // The database may match ignoring case; the id is only the same video when it is exactly the same.
            .filter(lesson -> videoId.equals(lesson.getVideoId()))
            .toList();
        if (!actingAsAdmin) {
            lessons = ownLessons(lessons, actingUserId);
            if (lessons.isEmpty()) {
                throw new VideoNotOwnedException();
            }
        }
        LibraryVideo current = library.find(videoId).orElseThrow(VideoNotFoundException::new);
        if (!library.rename(videoId, newTitle)) {
            throw new VideoNotFoundException();
        }
        auditRepository.append(videoId, actingUserId, ACTION, "title=" + current.title(), "title=" + newTitle);
        List<VideoLessonUsage> usages = VideoUsages.of(lessons, courseRepository, moduleRepository);
        return new ManagedVideo(videoId, withTitle(current, newTitle), usages);
    }

    private List<VirtualLesson> ownLessons(List<VirtualLesson> lessons, UUID instructorId) {
        if (lessons.isEmpty()) {
            return lessons;
        }
        Map<CourseId, Boolean> owned = new HashMap<>();
        for (VirtualCourse course
            : courseRepository.findByIds(lessons.stream().map(VirtualLesson::getCourseId).distinct().toList())) {
            owned.put(course.getId(), course.isOwnedBy(instructorId));
        }
        return lessons.stream().filter(lesson -> owned.getOrDefault(lesson.getCourseId(), false)).toList();
    }

    private static String requireValidTitle(String title) {
        String stripped = title == null ? "" : title.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (stripped.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title must not exceed " + MAX_TITLE_LENGTH + " characters");
        }
        return stripped;
    }

    private static LibraryVideo withTitle(LibraryVideo video, String title) {
        return new LibraryVideo(
            video.videoId(), title, video.status(), video.durationSeconds(), video.thumbnailUrl(),
            video.hasMp4Fallback(), video.availableResolutions()
        );
    }
}
