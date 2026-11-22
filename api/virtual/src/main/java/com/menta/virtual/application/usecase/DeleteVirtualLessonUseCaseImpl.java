package com.menta.virtual.application.usecase;

import com.menta.virtual.application.port.in.DeleteVirtualLessonUseCase;
import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.VirtualLesson;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Default {@link DeleteVirtualLessonUseCase} (#830), wrapped by its transactional decorator in {@code
 * VirtualConfiguration}, so the progress delete, the lesson delete, the renumbering and the audit row
 * commit together.
 *
 * <p>Progress is deleted in cascade rather than rejecting the delete: {@code virtual_lesson_progress} is
 * the only table that references a lesson (RESTRICT FK, V19), it holds playback position and completion
 * only — no payment or audit data — and a course percentage already uses the live lesson count as
 * denominator, so removing both the lesson and its rows keeps every student's percentage consistent.
 * The rows go first because of that FK.</p>
 *
 * <p>The course status is deliberately not checked. Deleting a whole course requires {@code DRAFT}
 * because a published course may have subscribers; deleting one lesson of a published course is an
 * ordinary content edit, like the {@code PUT} on the same lesson, which never checks the status either.
 * Publish validation is not re-run: as with an edit, a published course can end up without a complete
 * lesson, and it stays {@code PUBLISHED} until someone unpublishes it.</p>
 */
@RequiredArgsConstructor
public class DeleteVirtualLessonUseCaseImpl implements DeleteVirtualLessonUseCase {

    private final VirtualLessonRepository lessonRepository;
    private final VirtualCourseRepository courseRepository;
    private final LessonProgressRepository progressRepository;
    private final VirtualCourseAuditRepository auditRepository;

    @Override
    public void delete(String lessonId, UUID actingUserId, boolean actingAsAdmin) {
        VirtualLesson lesson = LessonOwnershipGuard.resolveOwnedLesson(
            lessonRepository, courseRepository, LessonId.of(lessonId), actingUserId, actingAsAdmin
        );

        long deletedProgressRows = progressRepository.deleteByLessonId(lesson.getId());
        lessonRepository.delete(lesson.getId());
        renumberRemainingLessons(lesson);

        auditRepository.append(
            lesson.getCourseId(), actingUserId, "DELETE_LESSON", auditSnapshot(lesson, deletedProgressRows), null
        );
    }

    /**
     * Rewrites the {@code order} of the lessons left in the module to 0..n-1, keeping their relative order,
     * so the swap-based reordering of the clients never meets a gap. Only the lessons whose order changes
     * are saved; the deleted lesson is skipped even if the read still returns it.
     */
    private void renumberRemainingLessons(VirtualLesson deleted) {
        List<VirtualLesson> remaining = lessonRepository.findByModuleId(deleted.getModuleId()).stream()
            .filter(lesson -> !lesson.getId().equals(deleted.getId()))
            .toList();
        for (int order = 0; order < remaining.size(); order++) {
            VirtualLesson lesson = remaining.get(order);
            if (lesson.getOrder() != order) {
                lessonRepository.save(lesson.withOrder(order));
            }
        }
    }

    private static String auditSnapshot(VirtualLesson lesson, long deletedProgressRows) {
        return "lessonId=" + lesson.getId() + ", moduleId=" + lesson.getModuleId() + ", title=" + lesson.getTitle()
            + ", videoId=" + lesson.getVideoId() + ", order=" + lesson.getOrder()
            + ", deletedProgressRows=" + deletedProgressRows;
    }
}
