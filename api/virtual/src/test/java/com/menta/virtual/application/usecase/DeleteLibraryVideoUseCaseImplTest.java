package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoStatus;
import com.menta.virtual.application.dto.VideoLessonUsage;
import com.menta.virtual.application.exception.VideoInUseException;
import com.menta.virtual.application.port.out.VideoLibraryPort;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.application.port.out.VirtualVideoAuditRepository;
import com.menta.virtual.domain.exception.VideoLibraryUnavailableException;
import com.menta.virtual.domain.exception.VideoNotOwnedException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeleteLibraryVideoUseCaseImplTest {

    private final VideoLibraryPort library = mock(VideoLibraryPort.class);
    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualVideoAuditRepository auditRepository = mock(VirtualVideoAuditRepository.class);
    private final DeleteLibraryVideoUseCaseImpl useCase = new DeleteLibraryVideoUseCaseImpl(
        library, courseRepository, moduleRepository, lessonRepository, auditRepository
    );

    private final UUID adminId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();

    private static LibraryVideo video(String id, String title) {
        return new LibraryVideo(id, title, LibraryVideoStatus.FINISHED, 60, null, true, List.of("720p"));
    }

    @Test
    void the_admin_deletes_a_video_no_lesson_uses_and_the_delete_is_audited_after_it() {
        when(lessonRepository.findByVideoId("v-1")).thenReturn(List.of());
        when(library.find("v-1")).thenReturn(Optional.of(video("v-1", "Tango básico")));

        useCase.delete("v-1", adminId, true);

        var order = inOrder(library, auditRepository);
        order.verify(library).delete("v-1");
        order.verify(auditRepository).append("v-1", adminId, "DELETE_VIDEO", "videoId=v-1, title=Tango básico", null);
    }

    @Test
    void a_video_the_library_no_longer_has_is_still_deleted_and_audited_without_a_title() {
        when(lessonRepository.findByVideoId("v-1")).thenReturn(List.of());
        when(library.find("v-1")).thenReturn(Optional.empty());

        useCase.delete("v-1", adminId, true);

        verify(library).delete("v-1");
        verify(auditRepository).append("v-1", adminId, "DELETE_VIDEO", "videoId=v-1, missingInLibrary=true", null);
    }

    @Test
    void a_video_that_a_lesson_uses_is_rejected_with_its_lessons_and_neither_the_library_nor_the_audit_is_touched() {
        VirtualCourse course = VirtualCourse.create(
            "Tango", "short", "long", instructorId, "https://img", CourseCategory.of("tango"), CourseLevel.BEGINNER
        );
        VirtualModule module = VirtualModule.create(course.getId(), "Módulo 1", 0);
        VirtualLesson lesson = VirtualLesson.create(
            module.getId(), course.getId(), "Lección 1", "d", "v-1", 5, false, 0
        );
        when(lessonRepository.findByVideoId("v-1")).thenReturn(List.of(lesson));
        when(courseRepository.findByIds(any())).thenReturn(List.of(course));
        when(moduleRepository.findByIds(any())).thenReturn(List.of(module));

        assertThatThrownBy(() -> useCase.delete("v-1", adminId, true))
            .isInstanceOf(VideoInUseException.class)
            .satisfies(failure -> {
                VideoInUseException inUse = (VideoInUseException) failure;
                assertThat(inUse.getErrorCode()).isEqualTo("VIDEO_IN_USE");
                assertThat(inUse.lessons()).containsExactly(new VideoLessonUsage(
                    course.getId().getValue().toString(), "Tango", module.getId().getValue().toString(), "Módulo 1",
                    lesson.getId().getValue().toString(), "Lección 1"
                ));
            });

        verifyNoInteractions(library, auditRepository);
    }

    @Test
    void an_instructor_can_never_delete_and_nothing_is_read() {
        assertThatThrownBy(() -> useCase.delete("v-1", instructorId, false))
            .isInstanceOf(VideoNotOwnedException.class);

        verifyNoInteractions(library, lessonRepository, auditRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ".", "..", "a/b", "a b", "a?x", "a%2Fb"})
    void an_id_that_is_not_a_plain_identifier_is_rejected_before_anything_is_touched(String videoId) {
        assertThatThrownBy(() -> useCase.delete(videoId, adminId, true))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(library, lessonRepository, auditRepository);
    }

    @Test
    void an_unavailable_library_on_the_lookup_deletes_and_audits_nothing() {
        when(lessonRepository.findByVideoId("v-1")).thenReturn(List.of());
        when(library.find("v-1")).thenThrow(new VideoLibraryUnavailableException("down"));

        assertThatThrownBy(() -> useCase.delete("v-1", adminId, true))
            .isInstanceOf(VideoLibraryUnavailableException.class);

        verify(library, never()).delete(any());
        verifyNoInteractions(auditRepository);
    }

    @Test
    void an_unavailable_library_on_the_delete_is_not_audited() {
        when(lessonRepository.findByVideoId("v-1")).thenReturn(List.of());
        when(library.find("v-1")).thenReturn(Optional.of(video("v-1", "Viejo")));
        doThrow(new VideoLibraryUnavailableException("down")).when(library).delete("v-1");

        assertThatThrownBy(() -> useCase.delete("v-1", adminId, true))
            .isInstanceOf(VideoLibraryUnavailableException.class);

        verifyNoInteractions(auditRepository);
    }
}
