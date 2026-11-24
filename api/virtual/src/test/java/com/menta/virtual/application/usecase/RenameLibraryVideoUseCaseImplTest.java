package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoStatus;
import com.menta.virtual.application.dto.ManagedVideo;
import com.menta.virtual.application.dto.VideoLessonUsage;
import com.menta.virtual.application.port.out.VideoLibraryPort;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.application.port.out.VirtualVideoAuditRepository;
import com.menta.virtual.domain.exception.VideoLibraryUnavailableException;
import com.menta.virtual.domain.exception.VideoNotFoundException;
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

class RenameLibraryVideoUseCaseImplTest {

    private final VideoLibraryPort library = mock(VideoLibraryPort.class);
    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualVideoAuditRepository auditRepository = mock(VirtualVideoAuditRepository.class);
    private final RenameLibraryVideoUseCaseImpl useCase = new RenameLibraryVideoUseCaseImpl(
        library, courseRepository, moduleRepository, lessonRepository, auditRepository
    );

    private final UUID adminId = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();
    private final UUID otherInstructorId = UUID.randomUUID();

    private static LibraryVideo video(String id, String title) {
        return new LibraryVideo(id, title, LibraryVideoStatus.FINISHED, 60, "https://cdn/" + id + ".jpg", true,
            List.of("720p"));
    }

    private record Seed(VirtualCourse course, VirtualModule module, VirtualLesson lesson) {
    }

    /** One course of {@code professorId} with one module and one lesson that uses {@code videoId}. */
    private Seed seed(UUID professorId, String courseTitle, String videoId) {
        VirtualCourse course = VirtualCourse.create(
            courseTitle, "short", "long", professorId, "https://img", CourseCategory.of("tango"),
            CourseLevel.BEGINNER
        );
        VirtualModule module = VirtualModule.create(course.getId(), "Módulo " + courseTitle, 0);
        VirtualLesson lesson = VirtualLesson.create(
            module.getId(), course.getId(), "Lección " + courseTitle, "d", videoId, 5, false, 0
        );
        return new Seed(course, module, lesson);
    }

    private void stubUsages(String videoId, Seed... seeds) {
        when(lessonRepository.findByVideoId(videoId)).thenReturn(
            java.util.Arrays.stream(seeds).map(Seed::lesson).toList()
        );
        when(courseRepository.findByIds(any())).thenReturn(java.util.Arrays.stream(seeds).map(Seed::course).toList());
        when(moduleRepository.findByIds(any())).thenReturn(java.util.Arrays.stream(seeds).map(Seed::module).toList());
    }

    private static VideoLessonUsage usageOf(Seed seed) {
        return new VideoLessonUsage(
            seed.course().getId().getValue().toString(), seed.course().getTitle(),
            seed.module().getId().getValue().toString(), seed.module().getTitle(),
            seed.lesson().getId().getValue().toString(), seed.lesson().getTitle()
        );
    }

    // --- ADMIN ------------------------------------------------------------

    @Test
    void the_admin_renames_a_video_that_no_lesson_uses_and_the_rename_is_audited_after_it() {
        when(lessonRepository.findByVideoId("v-1")).thenReturn(List.of());
        when(library.find("v-1")).thenReturn(Optional.of(video("v-1", "Viejo")));
        when(library.rename("v-1", "Nuevo")).thenReturn(true);

        ManagedVideo result = useCase.rename("v-1", "Nuevo", adminId, true);

        assertThat(result.videoId()).isEqualTo("v-1");
        assertThat(result.video()).isEqualTo(video("v-1", "Nuevo"));
        assertThat(result.lessons()).isEmpty();
        var order = inOrder(library, auditRepository);
        order.verify(library).rename("v-1", "Nuevo");
        order.verify(auditRepository).append("v-1", adminId, "RENAME_VIDEO", "title=Viejo", "title=Nuevo");
    }

    @Test
    void the_admin_gets_every_lesson_that_uses_the_video_whoever_owns_the_course() {
        Seed mine = seed(instructorId, "Tango", "v-1");
        Seed theirs = seed(otherInstructorId, "Vals", "v-1");
        stubUsages("v-1", mine, theirs);
        when(library.find("v-1")).thenReturn(Optional.of(video("v-1", "Viejo")));
        when(library.rename("v-1", "Nuevo")).thenReturn(true);

        ManagedVideo result = useCase.rename("v-1", "Nuevo", adminId, true);

        assertThat(result.lessons()).containsExactlyInAnyOrder(usageOf(mine), usageOf(theirs));
    }

    @Test
    void a_video_the_library_does_not_have_is_not_found_for_the_admin_and_nothing_is_written() {
        when(lessonRepository.findByVideoId("v-1")).thenReturn(List.of());
        when(library.find("v-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.rename("v-1", "Nuevo", adminId, true))
            .isInstanceOf(VideoNotFoundException.class)
            .extracting("errorCode").isEqualTo("VIDEO_NOT_FOUND");

        verify(library, never()).rename(any(), any());
        verifyNoInteractions(auditRepository);
    }

    @Test
    void a_video_that_vanishes_between_the_lookup_and_the_rename_is_not_found_and_not_audited() {
        when(lessonRepository.findByVideoId("v-1")).thenReturn(List.of());
        when(library.find("v-1")).thenReturn(Optional.of(video("v-1", "Viejo")));
        when(library.rename("v-1", "Nuevo")).thenReturn(false);

        assertThatThrownBy(() -> useCase.rename("v-1", "Nuevo", adminId, true))
            .isInstanceOf(VideoNotFoundException.class);

        verifyNoInteractions(auditRepository);
    }

    // --- INSTRUCTOR -------------------------------------------------------

    @Test
    void an_instructor_renames_a_video_used_by_their_own_course_and_only_sees_their_own_lessons() {
        Seed mine = seed(instructorId, "Tango", "v-1");
        Seed theirs = seed(otherInstructorId, "Vals", "v-1");
        stubUsages("v-1", mine, theirs);
        when(library.find("v-1")).thenReturn(Optional.of(video("v-1", "Viejo")));
        when(library.rename("v-1", "Nuevo")).thenReturn(true);

        ManagedVideo result = useCase.rename("v-1", "Nuevo", instructorId, false);

        assertThat(result.video().title()).isEqualTo("Nuevo");
        assertThat(result.lessons()).containsExactly(usageOf(mine));
        verify(auditRepository).append("v-1", instructorId, "RENAME_VIDEO", "title=Viejo", "title=Nuevo");
    }

    @Test
    void an_instructor_cannot_rename_a_video_used_only_by_someone_elses_course_and_the_library_is_not_asked() {
        Seed theirs = seed(otherInstructorId, "Vals", "v-1");
        stubUsages("v-1", theirs);

        assertThatThrownBy(() -> useCase.rename("v-1", "Nuevo", instructorId, false))
            .isInstanceOf(VideoNotOwnedException.class)
            .extracting("errorCode").isEqualTo("VIDEO_NOT_OWNED");

        verifyNoInteractions(library, auditRepository);
    }

    @Test
    void an_instructor_cannot_rename_a_video_no_lesson_uses_even_if_the_library_has_it() {
        when(lessonRepository.findByVideoId("v-1")).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.rename("v-1", "Nuevo", instructorId, false))
            .isInstanceOf(VideoNotOwnedException.class);

        verifyNoInteractions(library, auditRepository);
    }

    @Test
    void ownership_compares_the_id_exactly_even_when_the_database_matches_it_ignoring_case() {
        Seed mine = seed(instructorId, "Tango", "V-1");
        stubUsages("v-1", mine);

        assertThatThrownBy(() -> useCase.rename("v-1", "Nuevo", instructorId, false))
            .isInstanceOf(VideoNotOwnedException.class);

        verifyNoInteractions(library);
    }

    @Test
    void an_instructor_whose_video_the_library_no_longer_has_gets_not_found() {
        Seed mine = seed(instructorId, "Tango", "v-1");
        stubUsages("v-1", mine);
        when(library.find("v-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.rename("v-1", "Nuevo", instructorId, false))
            .isInstanceOf(VideoNotFoundException.class);

        verifyNoInteractions(auditRepository);
    }

    // --- validation and failures -----------------------------------------

    @Test
    void the_title_is_stripped_before_it_is_sent_and_audited() {
        when(lessonRepository.findByVideoId("v-1")).thenReturn(List.of());
        when(library.find("v-1")).thenReturn(Optional.of(video("v-1", "Viejo")));
        when(library.rename("v-1", "Nuevo")).thenReturn(true);

        ManagedVideo result = useCase.rename("v-1", "  Nuevo \n", adminId, true);

        assertThat(result.video().title()).isEqualTo("Nuevo");
        verify(library).rename("v-1", "Nuevo");
        verify(auditRepository).append("v-1", adminId, "RENAME_VIDEO", "title=Viejo", "title=Nuevo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t"})
    void a_blank_title_is_rejected_before_anything_is_touched(String title) {
        assertThatThrownBy(() -> useCase.rename("v-1", title, adminId, true))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(library, lessonRepository, auditRepository);
    }

    @Test
    void a_missing_title_is_rejected_before_anything_is_touched() {
        assertThatThrownBy(() -> useCase.rename("v-1", null, adminId, true))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(library, lessonRepository, auditRepository);
    }

    @Test
    void a_title_over_the_maximum_is_rejected_and_the_maximum_itself_is_accepted() {
        when(lessonRepository.findByVideoId("v-1")).thenReturn(List.of());
        when(library.find("v-1")).thenReturn(Optional.of(video("v-1", "Viejo")));
        when(library.rename(eq("v-1"), any())).thenReturn(true);
        String longest = "a".repeat(RenameLibraryVideoUseCaseImpl.MAX_TITLE_LENGTH);

        assertThat(useCase.rename("v-1", longest, adminId, true).video().title()).isEqualTo(longest);
        assertThatThrownBy(() -> useCase.rename("v-1", longest + "a", adminId, true))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ".", "..", "a/b", "a b", "a?x", "a%2Fb"})
    void an_id_that_is_not_a_plain_identifier_is_rejected_before_anything_is_touched(String videoId) {
        assertThatThrownBy(() -> useCase.rename(videoId, "Nuevo", adminId, true))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(library, lessonRepository, auditRepository);
    }

    @Test
    void an_unavailable_library_on_the_lookup_writes_nothing() {
        when(lessonRepository.findByVideoId("v-1")).thenReturn(List.of());
        when(library.find("v-1")).thenThrow(new VideoLibraryUnavailableException("down"));

        assertThatThrownBy(() -> useCase.rename("v-1", "Nuevo", adminId, true))
            .isInstanceOf(VideoLibraryUnavailableException.class);

        verify(library, never()).rename(any(), any());
        verifyNoInteractions(auditRepository);
    }

    @Test
    void an_unavailable_library_on_the_rename_is_not_audited() {
        when(lessonRepository.findByVideoId("v-1")).thenReturn(List.of());
        when(library.find("v-1")).thenReturn(Optional.of(video("v-1", "Viejo")));
        doThrow(new VideoLibraryUnavailableException("down")).when(library).rename("v-1", "Nuevo");

        assertThatThrownBy(() -> useCase.rename("v-1", "Nuevo", adminId, true))
            .isInstanceOf(VideoLibraryUnavailableException.class);

        verifyNoInteractions(auditRepository);
    }
}
