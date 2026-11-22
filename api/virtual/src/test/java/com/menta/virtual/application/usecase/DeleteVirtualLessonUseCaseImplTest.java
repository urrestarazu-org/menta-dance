package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.domain.exception.CourseNotOwnedException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualLesson;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class DeleteVirtualLessonUseCaseImplTest {

    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final LessonProgressRepository progressRepository = mock(LessonProgressRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);
    private final DeleteVirtualLessonUseCaseImpl useCase = new DeleteVirtualLessonUseCaseImpl(
        lessonRepository, courseRepository, progressRepository, auditRepository
    );

    private final CourseId courseId = CourseId.generate();
    private final ModuleId moduleId = ModuleId.generate();
    private final UUID ownerId = UUID.randomUUID();

    private void givenCourse(CourseStatus status) {
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(new VirtualCourse(
            courseId, "t", "s", "d", ownerId, "i", CourseCategory.of("tango"), CourseLevel.BEGINNER, false,
            status, 0, 0, 0
        )));
    }

    private VirtualLesson lesson(String title, int order) {
        return VirtualLesson.create(moduleId, courseId, title, "d", "v", 10, false, order);
    }

    private void givenStored(VirtualLesson lesson) {
        when(lessonRepository.findById(lesson.getId())).thenReturn(Optional.of(lesson));
    }

    @Test
    void an_unknown_lesson_fails_with_lesson_not_found_and_deletes_nothing() {
        LessonId unknown = LessonId.generate();
        when(lessonRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.delete(unknown.toString(), ownerId, true))
            .isInstanceOf(LessonNotFoundException.class);

        verify(lessonRepository, never()).delete(any());
        verifyNoInteractions(progressRepository, auditRepository);
    }

    @Test
    void a_malformed_lesson_id_fails_before_touching_any_repository() {
        assertThatThrownBy(() -> useCase.delete("not-a-uuid", ownerId, true))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(lessonRepository, courseRepository, progressRepository, auditRepository);
    }

    @Test
    void an_instructor_deleting_a_lesson_of_a_course_they_do_not_own_is_rejected_and_deletes_nothing() {
        givenCourse(CourseStatus.DRAFT);
        VirtualLesson target = lesson("L1", 0);
        givenStored(target);

        assertThatThrownBy(() -> useCase.delete(target.getId().toString(), UUID.randomUUID(), false))
            .isInstanceOf(CourseNotOwnedException.class);

        verify(lessonRepository, never()).delete(any());
        verify(lessonRepository, never()).save(any());
        verifyNoInteractions(progressRepository, auditRepository);
    }

    @Test
    void the_owning_instructor_deletes_the_lesson() {
        givenCourse(CourseStatus.DRAFT);
        VirtualLesson target = lesson("L1", 0);
        givenStored(target);

        useCase.delete(target.getId().toString(), ownerId, false);

        verify(lessonRepository).delete(target.getId());
    }

    @Test
    void an_admin_deletes_a_lesson_of_any_course() {
        givenCourse(CourseStatus.DRAFT);
        VirtualLesson target = lesson("L1", 0);
        givenStored(target);

        useCase.delete(target.getId().toString(), UUID.randomUUID(), true);

        verify(lessonRepository).delete(target.getId());
    }

    @Test
    void a_lesson_of_a_published_course_can_be_deleted() {
        givenCourse(CourseStatus.PUBLISHED);
        VirtualLesson target = lesson("L1", 0);
        givenStored(target);

        useCase.delete(target.getId().toString(), ownerId, false);

        verify(lessonRepository).delete(target.getId());
    }

    @Test
    void the_student_progress_of_the_lesson_is_deleted_before_the_lesson_itself() {
        // virtual_lesson_progress.lesson_id is a RESTRICT FK (V19): the lesson row cannot go first.
        givenCourse(CourseStatus.PUBLISHED);
        VirtualLesson target = lesson("L1", 0);
        givenStored(target);

        useCase.delete(target.getId().toString(), ownerId, false);

        InOrder inOrder = inOrder(progressRepository, lessonRepository);
        inOrder.verify(progressRepository).deleteByLessonId(target.getId());
        inOrder.verify(lessonRepository).delete(target.getId());
    }

    @Test
    void the_remaining_lessons_of_the_module_are_renumbered_contiguously() {
        givenCourse(CourseStatus.DRAFT);
        VirtualLesson first = lesson("L0", 0);
        VirtualLesson target = lesson("L1", 1);
        VirtualLesson third = lesson("L2", 2);
        VirtualLesson fourth = lesson("L3", 3);
        givenStored(target);
        when(lessonRepository.findByModuleId(moduleId)).thenReturn(List.of(first, third, fourth));

        useCase.delete(target.getId().toString(), ownerId, false);

        ArgumentCaptor<VirtualLesson> saved = ArgumentCaptor.forClass(VirtualLesson.class);
        verify(lessonRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(VirtualLesson::getId)
            .containsExactly(third.getId(), fourth.getId());
        assertThat(saved.getAllValues()).extracting(VirtualLesson::getOrder).containsExactly(1, 2);
    }

    @Test
    void renumbering_closes_gaps_that_already_existed_and_ignores_the_deleted_lesson_if_still_listed() {
        givenCourse(CourseStatus.DRAFT);
        VirtualLesson target = lesson("L0", 0);
        VirtualLesson second = lesson("L5", 5);
        VirtualLesson third = lesson("L9", 9);
        givenStored(target);
        when(lessonRepository.findByModuleId(moduleId)).thenReturn(List.of(target, second, third));

        useCase.delete(target.getId().toString(), ownerId, false);

        ArgumentCaptor<VirtualLesson> saved = ArgumentCaptor.forClass(VirtualLesson.class);
        verify(lessonRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(VirtualLesson::getId)
            .containsExactly(second.getId(), third.getId());
        assertThat(saved.getAllValues()).extracting(VirtualLesson::getOrder).containsExactly(0, 1);
    }

    @Test
    void deleting_the_last_lesson_saves_nothing_else() {
        givenCourse(CourseStatus.DRAFT);
        VirtualLesson first = lesson("L0", 0);
        VirtualLesson target = lesson("L1", 1);
        givenStored(target);
        when(lessonRepository.findByModuleId(moduleId)).thenReturn(List.of(first));

        useCase.delete(target.getId().toString(), ownerId, false);

        verify(lessonRepository, never()).save(any());
    }

    @Test
    void appends_a_delete_lesson_audit_row_with_the_old_snapshot_and_the_deleted_progress_count() {
        givenCourse(CourseStatus.PUBLISHED);
        VirtualLesson target = lesson("Sacadas", 0);
        givenStored(target);
        when(progressRepository.deleteByLessonId(target.getId())).thenReturn(3L);

        useCase.delete(target.getId().toString(), ownerId, false);

        ArgumentCaptor<String> before = ArgumentCaptor.forClass(String.class);
        verify(auditRepository).append(eq(courseId), eq(ownerId), eq("DELETE_LESSON"), before.capture(), isNull());
        assertThat(before.getValue())
            .contains("lessonId=" + target.getId())
            .contains("moduleId=" + moduleId)
            .contains("title=Sacadas")
            .contains("order=0")
            .contains("deletedProgressRows=3");
        verify(auditRepository, times(1)).append(any(), any(), anyString(), any(), any());
    }
}
