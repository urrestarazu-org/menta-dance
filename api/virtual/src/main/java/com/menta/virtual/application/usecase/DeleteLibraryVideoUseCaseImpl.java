package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.VideoLessonUsage;
import com.menta.virtual.application.exception.VideoInUseException;
import com.menta.virtual.application.port.in.DeleteLibraryVideoUseCase;
import com.menta.virtual.application.port.out.VideoLibraryPort;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.application.port.out.VirtualVideoAuditRepository;
import com.menta.virtual.domain.exception.VideoNotOwnedException;
import com.menta.virtual.domain.model.VirtualLesson;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Default {@link DeleteLibraryVideoUseCase}. Like the rename, deliberately NOT wrapped in a transaction (an external
 * call must not hold a database transaction open).
 *
 * <p>Order: validate, admin only, then the in-use check: any lesson of any course in any status that uses the video
 * rejects with {@link VideoInUseException} before the library is contacted (the DB comparison may ignore case, which
 * errs on the side of not deleting). Then the video is looked up so the audit row can say what was deleted
 * (best effort: a video the library no longer has is still deleted, which the library treats as success), deleted,
 * and audited. The delete is idempotent, so if the audit insert fails after the delete the error is returned and
 * repeating the request writes the row. A lesson could be assigned the video between the check and the delete; there
 * is no lock that spans the database and the library, and the window is a few milliseconds.</p>
 */
@RequiredArgsConstructor
public class DeleteLibraryVideoUseCaseImpl implements DeleteLibraryVideoUseCase {

    static final String ACTION = "DELETE_VIDEO";

    private final VideoLibraryPort library;
    private final VirtualCourseRepository courseRepository;
    private final VirtualModuleRepository moduleRepository;
    private final VirtualLessonRepository lessonRepository;
    private final VirtualVideoAuditRepository auditRepository;

    @Override
    public void delete(String videoId, UUID actingUserId, boolean actingAsAdmin) {
        VideoUsages.requireValidId(videoId);
        if (!actingAsAdmin) {
            throw new VideoNotOwnedException();
        }
        List<VirtualLesson> lessons = lessonRepository.findByVideoId(videoId);
        if (!lessons.isEmpty()) {
            List<VideoLessonUsage> usages = VideoUsages.of(lessons, courseRepository, moduleRepository);
            throw new VideoInUseException(usages);
        }
        Optional<LibraryVideo> current = library.find(videoId);
        library.delete(videoId);
        String snapshot = current
            .map(video -> "videoId=" + videoId + ", title=" + video.title())
            .orElse("videoId=" + videoId + ", missingInLibrary=true");
        auditRepository.append(videoId, actingUserId, ACTION, snapshot, null);
    }
}
