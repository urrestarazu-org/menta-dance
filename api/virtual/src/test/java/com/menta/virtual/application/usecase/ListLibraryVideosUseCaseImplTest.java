package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoOrder;
import com.menta.virtual.application.dto.LibraryVideoPage;
import com.menta.virtual.application.dto.LibraryVideoStatus;
import com.menta.virtual.application.dto.ManagedVideo;
import com.menta.virtual.application.dto.ManagedVideoPage;
import com.menta.virtual.application.dto.VideoLessonUsage;
import com.menta.virtual.application.dto.VideoLibraryQuery;
import com.menta.virtual.application.port.out.VideoLibraryPort;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.VideoLibraryUnavailableException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListLibraryVideosUseCaseImplTest {

    private final VideoLibraryPort library = mock(VideoLibraryPort.class);
    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final ListLibraryVideosUseCaseImpl useCase = new ListLibraryVideosUseCaseImpl(
        library, courseRepository, moduleRepository, lessonRepository
    );

    private final UUID instructorId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    private static LibraryVideo video(String id, String title) {
        return new LibraryVideo(id, title, LibraryVideoStatus.FINISHED, 60, "https://cdn/" + id + ".jpg", true,
            List.of("360p", "720p"));
    }

    private static VirtualCourse course(UUID professorId, String title) {
        return VirtualCourse.create(
            title, "short", "long", professorId, "https://img", CourseCategory.of("tango"), CourseLevel.BEGINNER
        );
    }

    private static VideoLibraryQuery query(int page, int size, String search) {
        return new VideoLibraryQuery(page, size, search, LibraryVideoOrder.DATE);
    }

    private static LibraryVideoPage libraryPage(int page, int size, long total, LibraryVideo... videos) {
        return new LibraryVideoPage(List.of(videos), page, size, total);
    }

    // --- ADMIN ------------------------------------------------------------

    private record AdminSeed(VirtualCourse course, VirtualModule module, List<VirtualLesson> lessons) {
    }

    private AdminSeed seedAdmin(UUID professorId, String courseTitle, String... videoIds) {
        VirtualCourse course = course(professorId, courseTitle);
        VirtualModule module = VirtualModule.create(course.getId(), "Módulo " + courseTitle, 0);
        List<VirtualLesson> lessons = new ArrayList<>();
        for (int i = 0; i < videoIds.length; i++) {
            lessons.add(VirtualLesson.create(
                module.getId(), course.getId(), "Lección " + i, "d", videoIds[i], 5, false, i
            ));
        }
        return new AdminSeed(course, module, lessons);
    }

    private void stubAdminDatabase(AdminSeed... seeds) {
        List<VirtualLesson> lessons = new ArrayList<>();
        List<VirtualCourse> courses = new ArrayList<>();
        List<VirtualModule> modules = new ArrayList<>();
        for (AdminSeed seed : seeds) {
            lessons.addAll(seed.lessons());
            courses.add(seed.course());
            modules.add(seed.module());
        }
        when(lessonRepository.findAllWithVideo()).thenReturn(lessons);
        when(courseRepository.findByIds(any())).thenReturn(courses);
        when(moduleRepository.findByIds(any())).thenReturn(modules);
    }

    @Test
    void the_admin_gets_the_whole_library_with_the_lessons_that_use_each_video() {
        AdminSeed seed = seedAdmin(instructorId, "Tango", "v-1");
        stubAdminDatabase(seed);
        when(library.list(any())).thenReturn(libraryPage(0, 1000, 2, video("v-1", "Uno"), video("v-2", "Dos")));

        ManagedVideoPage page = useCase.list(query(0, 20, null), adminId, true);

        assertThat(page.totalItems()).isEqualTo(2);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.items()).extracting(ManagedVideo::videoId).containsExactly("v-1", "v-2");
        assertThat(page.items().get(0).missingInLibrary()).isFalse();
        VirtualLesson lesson = seed.lessons().get(0);
        assertThat(page.items().get(0).lessons()).containsExactly(new VideoLessonUsage(
            seed.course().getId().getValue().toString(), "Tango", seed.module().getId().getValue().toString(),
            "Módulo Tango", lesson.getId().getValue().toString(), "Lección 0"
        ));
        assertThat(page.items().get(1).lessons()).isEmpty();
        verify(library).list(new VideoLibraryQuery(0, 1000, null, LibraryVideoOrder.DATE));
        verify(courseRepository, never()).findByProfessorId(any());
    }

    @Test
    void the_admin_also_sees_a_video_that_a_lesson_uses_but_the_library_no_longer_has() {
        AdminSeed seed = seedAdmin(instructorId, "Tango", "gone", "here");
        stubAdminDatabase(seed);
        when(library.list(any())).thenReturn(libraryPage(0, 1000, 2, video("here", "Acá"), video("unused", "Suelto")));

        ManagedVideoPage page = useCase.list(query(0, 20, null), adminId, true);

        assertThat(page.items()).extracting(ManagedVideo::videoId).containsExactly("here", "unused", "gone");
        assertThat(page.totalItems()).isEqualTo(3);
        ManagedVideo gone = page.items().get(2);
        assertThat(gone.missingInLibrary()).isTrue();
        assertThat(gone.video()).isNull();
        assertThat(gone.lessons()).extracting(VideoLessonUsage::lessonTitle).containsExactly("Lección 0");
        assertThat(gone.lessons()).extracting(VideoLessonUsage::courseTitle).containsExactly("Tango");
        assertThat(gone.lessons()).extracting(VideoLessonUsage::moduleTitle).containsExactly("Módulo Tango");
    }

    @Test
    void the_admin_listing_with_a_search_does_not_flag_the_videos_that_merely_do_not_match() {
        stubAdminDatabase(seedAdmin(instructorId, "Tango", "a", "b"));
        when(library.list(any())).thenReturn(libraryPage(0, 1000, 1, video("a", "Salsa")));

        ManagedVideoPage page = useCase.list(query(0, 20, "salsa"), adminId, true);

        assertThat(page.items()).extracting(ManagedVideo::videoId).containsExactly("a");
        assertThat(page.items().get(0).missingInLibrary()).isFalse();
        verify(library).list(new VideoLibraryQuery(0, 1000, "salsa", LibraryVideoOrder.DATE));
    }

    @Test
    void the_admin_listing_pages_the_union_in_memory_and_passes_the_order_through() {
        stubAdminDatabase(seedAdmin(instructorId, "Tango", "gone-1", "gone-2"));
        List<LibraryVideo> all = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            all.add(video("v" + i, "T" + i));
        }
        when(library.list(any())).thenReturn(new LibraryVideoPage(all, 0, 1000, 10));
        VideoLibraryQuery second = new VideoLibraryQuery(1, 10, null, LibraryVideoOrder.TITLE);

        ManagedVideoPage page = useCase.list(second, adminId, true);

        assertThat(page.items()).extracting(ManagedVideo::videoId).containsExactly("gone-1", "gone-2");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalItems()).isEqualTo(12);
        verify(library).list(new VideoLibraryQuery(0, 1000, null, LibraryVideoOrder.TITLE));
    }

    @Test
    void the_admin_lists_the_library_even_when_no_lesson_uses_a_video() {
        when(lessonRepository.findAllWithVideo()).thenReturn(List.of());
        when(library.list(any())).thenReturn(libraryPage(0, 1000, 1, video("v-1", "Uno")));

        ManagedVideoPage page = useCase.list(query(0, 20, null), adminId, true);

        assertThat(page.items()).extracting(ManagedVideo::videoId).containsExactly("v-1");
        assertThat(page.items().get(0).lessons()).isEmpty();
        verifyNoInteractions(courseRepository, moduleRepository);
    }

    @Test
    void the_admin_listing_walks_every_library_page() {
        stubAdminDatabase(seedAdmin(instructorId, "Tango", "z"));
        List<LibraryVideo> firstChunk = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            firstChunk.add(video("filler-" + i, "F"));
        }
        when(library.list(new VideoLibraryQuery(0, 1000, null, LibraryVideoOrder.DATE)))
            .thenReturn(new LibraryVideoPage(firstChunk, 0, 1000, 1001));
        when(library.list(new VideoLibraryQuery(1, 1000, null, LibraryVideoOrder.DATE)))
            .thenReturn(libraryPage(1, 1000, 1001, video("z", "Z")));

        ManagedVideoPage page = useCase.list(query(100, 20, null), adminId, true);

        assertThat(page.totalItems()).isEqualTo(1001);
        verify(library, org.mockito.Mockito.times(2)).list(any());
    }

    @Test
    void a_library_too_large_to_walk_is_unavailable_for_the_admin_too() {
        when(lessonRepository.findAllWithVideo()).thenReturn(List.of());
        List<LibraryVideo> chunk = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            chunk.add(video("filler-" + i, "F"));
        }
        when(library.list(any())).thenReturn(new LibraryVideoPage(chunk, 0, 1000, 1_000_000));

        assertThatThrownBy(() -> useCase.list(query(0, 20, null), adminId, true))
            .isInstanceOf(VideoLibraryUnavailableException.class);
        verify(library, org.mockito.Mockito.times(ListLibraryVideosUseCaseImpl.MAX_WALK_PAGES)).list(any());
    }

    @Test
    void the_admin_lessons_sharing_a_video_are_ordered_by_course_module_and_lesson() {
        AdminSeed tango = seedAdmin(instructorId, "Tango", "v");
        AdminSeed bachata = seedAdmin(UUID.randomUUID(), "Bachata", "v");
        stubAdminDatabase(tango, bachata);
        when(library.list(any())).thenReturn(libraryPage(0, 1000, 1, video("v", "Uno")));

        ManagedVideoPage page = useCase.list(query(0, 20, null), adminId, true);

        assertThat(page.items().get(0).lessons()).extracting(VideoLessonUsage::courseTitle)
            .containsExactly("Bachata", "Tango");
    }

    @Test
    void a_page_size_above_the_listing_maximum_is_rejected_before_asking_the_library() {
        VideoLibraryQuery tooBig = query(0, ListLibraryVideosUseCaseImpl.MAX_PAGE_SIZE + 1, null);

        assertThatThrownBy(() -> useCase.list(tooBig, adminId, true)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(library);
    }

    // --- INSTRUCTOR -------------------------------------------------------

    private record Seed(VirtualCourse course, VirtualModule module, List<VirtualLesson> lessons) {
    }

    private Seed seedInstructor(String courseTitle, String... videoIds) {
        VirtualCourse course = course(instructorId, courseTitle);
        VirtualModule module = VirtualModule.create(course.getId(), "Módulo", 0);
        List<VirtualLesson> lessons = new ArrayList<>();
        for (int i = 0; i < videoIds.length; i++) {
            lessons.add(VirtualLesson.create(
                module.getId(), course.getId(), "Lección " + i, "d", videoIds[i], 5, false, i
            ));
        }
        when(courseRepository.findByProfessorId(instructorId)).thenReturn(List.of(course));
        when(moduleRepository.findByCourseId(course.getId())).thenReturn(List.of(module));
        when(lessonRepository.findByCourseId(course.getId())).thenReturn(lessons);
        return new Seed(course, module, lessons);
    }

    @Test
    void the_instructor_sees_the_whole_library_but_only_their_own_lessons_in_each_video() {
        seedInstructor("Tango", "mine-1", "mine-2");
        when(library.list(any())).thenReturn(
            libraryPage(0, 1000, 3, video("other", "Ajeno"), video("mine-2", "Dos"), video("mine-1", "Uno"))
        );

        ManagedVideoPage page = useCase.list(query(0, 20, null), instructorId, false);

        assertThat(page.items()).extracting(ManagedVideo::videoId).containsExactly("other", "mine-2", "mine-1");
        assertThat(page.totalItems()).isEqualTo(3);
        assertThat(page.items().get(0).lessons()).isEmpty();
        assertThat(page.items().get(1).lessons()).extracting(VideoLessonUsage::lessonTitle)
            .containsExactly("Lección 1");
        assertThat(page.items().get(2).lessons()).extracting(VideoLessonUsage::lessonTitle)
            .containsExactly("Lección 0");
        verify(lessonRepository, never()).findAllWithVideo();
        verify(courseRepository, never()).findAll();
    }

    @Test
    void the_instructor_sees_a_library_video_that_no_lesson_uses() {
        seedInstructor("Tango", "mine");
        when(library.list(any())).thenReturn(libraryPage(0, 1000, 2, video("free", "Suelto"), video("mine", "Mío")));

        ManagedVideoPage page = useCase.list(query(0, 20, null), instructorId, false);

        assertThat(page.items()).extracting(ManagedVideo::videoId).containsExactly("free", "mine");
        assertThat(page.items().get(0).lessons()).isEmpty();
        assertThat(page.items().get(0).missingInLibrary()).isFalse();
    }

    @Test
    void a_video_used_only_by_another_instructor_appears_without_lessons_and_leaks_no_title() {
        seedInstructor("Tango", "mine");
        when(library.list(any())).thenReturn(
            libraryPage(0, 1000, 2, video("theirs", "Del otro"), video("mine", "Mío"))
        );

        ManagedVideoPage page = useCase.list(query(0, 20, null), instructorId, false);

        ManagedVideo theirs = page.items().get(0);
        assertThat(theirs.videoId()).isEqualTo("theirs");
        assertThat(theirs.lessons()).isEmpty();
        assertThat(page.items()).flatExtracting(ManagedVideo::lessons)
            .extracting(VideoLessonUsage::courseTitle).containsOnly("Tango");
        verify(lessonRepository, never()).findAllWithVideo();
        verify(courseRepository, never()).findByIds(any());
        verify(moduleRepository, never()).findByIds(any());
    }

    @Test
    void the_instructor_never_sees_the_lessons_of_other_courses_that_share_the_video() {
        seedInstructor("Tango", "shared");
        when(library.list(any())).thenReturn(libraryPage(0, 1000, 1, video("shared", "Compartido")));

        ManagedVideoPage page = useCase.list(query(0, 20, null), instructorId, false);

        assertThat(page.items().get(0).lessons()).extracting(VideoLessonUsage::courseTitle).containsExactly("Tango");
        verify(lessonRepository, never()).findAllWithVideo();
    }

    @Test
    void a_lesson_pointing_at_a_video_that_is_not_in_the_library_is_flagged_not_dropped() {
        seedInstructor("Tango", "gone", "here");
        when(library.list(any())).thenReturn(libraryPage(0, 1000, 2, video("here", "Acá"), video("free", "Suelto")));

        ManagedVideoPage page = useCase.list(query(0, 20, null), instructorId, false);

        assertThat(page.items()).extracting(ManagedVideo::videoId).containsExactly("here", "free", "gone");
        ManagedVideo missing = page.items().get(2);
        assertThat(missing.missingInLibrary()).isTrue();
        assertThat(missing.video()).isNull();
        assertThat(missing.lessons()).extracting(VideoLessonUsage::lessonTitle).containsExactly("Lección 0");
        assertThat(page.totalItems()).isEqualTo(3);
    }

    @Test
    void a_video_missing_from_the_library_is_only_reported_for_the_own_lessons() {
        seedInstructor("Tango", "here");
        when(library.list(any())).thenReturn(libraryPage(0, 1000, 1, video("here", "Acá")));

        ManagedVideoPage page = useCase.list(query(0, 20, null), instructorId, false);

        assertThat(page.items()).extracting(ManagedVideo::videoId).containsExactly("here");
        assertThat(page.items()).noneMatch(ManagedVideo::missingInLibrary);
        verify(lessonRepository, never()).findAllWithVideo();
    }

    @Test
    void a_search_lists_the_matching_library_videos_without_flagging_the_own_ones_that_do_not_match() {
        seedInstructor("Tango", "a", "b");
        when(library.list(any())).thenReturn(libraryPage(0, 1000, 2, video("a", "Salsa"), video("c", "Salsa 2")));

        ManagedVideoPage page = useCase.list(query(0, 20, "salsa"), instructorId, false);

        assertThat(page.items()).extracting(ManagedVideo::videoId).containsExactly("a", "c");
        assertThat(page.items()).noneMatch(ManagedVideo::missingInLibrary);
        assertThat(page.items().get(1).lessons()).isEmpty();
        verify(library).list(new VideoLibraryQuery(0, 1000, "salsa", LibraryVideoOrder.DATE));
    }

    @Test
    void the_instructor_listing_passes_the_order_through_and_pages_in_memory() {
        seedInstructor("Tango", "v1");
        List<LibraryVideo> all = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            all.add(video("v" + i, "T" + i));
        }
        when(library.list(any())).thenReturn(new LibraryVideoPage(all, 0, 1000, 12));
        VideoLibraryQuery second = new VideoLibraryQuery(1, 10, null, LibraryVideoOrder.TITLE);

        ManagedVideoPage page = useCase.list(second, instructorId, false);

        assertThat(page.items()).extracting(ManagedVideo::videoId).containsExactly("v11", "v12");
        assertThat(page.items()).allMatch(item -> item.lessons().isEmpty());
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalItems()).isEqualTo(12);
        verify(library).list(new VideoLibraryQuery(0, 1000, null, LibraryVideoOrder.TITLE));
    }

    @Test
    void a_page_past_the_end_is_empty() {
        seedInstructor("Tango", "v1");
        when(library.list(any())).thenReturn(libraryPage(0, 1000, 1, video("v1", "Uno")));

        ManagedVideoPage page = useCase.list(query(5, 10, null), instructorId, false);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isEqualTo(1);
    }

    @Test
    void an_instructor_without_courses_still_sees_the_whole_library_without_lessons() {
        when(courseRepository.findByProfessorId(instructorId)).thenReturn(List.of());
        when(library.list(any())).thenReturn(libraryPage(0, 1000, 2, video("v-1", "Uno"), video("v-2", "Dos")));

        ManagedVideoPage page = useCase.list(query(0, 20, null), instructorId, false);

        assertThat(page.items()).extracting(ManagedVideo::videoId).containsExactly("v-1", "v-2");
        assertThat(page.items()).allMatch(item -> item.lessons().isEmpty());
        assertThat(page.totalItems()).isEqualTo(2);
        verifyNoInteractions(moduleRepository, lessonRepository);
    }

    @Test
    void lessons_without_a_video_do_not_count_as_used_videos_but_the_library_is_still_listed() {
        VirtualCourse course = course(instructorId, "Tango");
        VirtualModule module = VirtualModule.create(course.getId(), "M", 0);
        VirtualLesson stub = VirtualLesson.create(module.getId(), course.getId(), "Sin video", "d", null, 0, false, 0);
        when(courseRepository.findByProfessorId(instructorId)).thenReturn(List.of(course));
        when(moduleRepository.findByCourseId(course.getId())).thenReturn(List.of(module));
        when(lessonRepository.findByCourseId(course.getId())).thenReturn(List.of(stub));
        when(library.list(any())).thenReturn(libraryPage(0, 1000, 1, video("v-1", "Uno")));

        ManagedVideoPage page = useCase.list(query(0, 20, null), instructorId, false);

        assertThat(page.items()).extracting(ManagedVideo::videoId).containsExactly("v-1");
        assertThat(page.items().get(0).lessons()).isEmpty();
    }

    @Test
    void the_instructor_listing_walks_every_library_page_like_the_admin() {
        seedInstructor("Tango", "a");
        List<LibraryVideo> firstChunk = new ArrayList<>();
        firstChunk.add(video("a", "A"));
        for (int i = 0; i < 999; i++) {
            firstChunk.add(video("filler-" + i, "F"));
        }
        when(library.list(new VideoLibraryQuery(0, 1000, null, LibraryVideoOrder.DATE)))
            .thenReturn(new LibraryVideoPage(firstChunk, 0, 1000, 1001));
        when(library.list(new VideoLibraryQuery(1, 1000, null, LibraryVideoOrder.DATE)))
            .thenReturn(libraryPage(1, 1000, 1001, video("z", "Z")));

        ManagedVideoPage page = useCase.list(query(100, 20, null), instructorId, false);

        assertThat(page.totalItems()).isEqualTo(1001);
        verify(library, org.mockito.Mockito.times(2)).list(any());
        verify(library, never()).list(new VideoLibraryQuery(2, 1000, null, LibraryVideoOrder.DATE));
    }

    @Test
    void a_library_too_large_to_walk_is_reported_as_unavailable_instead_of_guessing() {
        seedInstructor("Tango", "never-found");
        List<LibraryVideo> chunk = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            chunk.add(video("filler-" + i, "F"));
        }
        when(library.list(any())).thenReturn(new LibraryVideoPage(chunk, 0, 1000, 1_000_000));

        assertThatThrownBy(() -> useCase.list(query(0, 20, null), instructorId, false))
            .isInstanceOf(VideoLibraryUnavailableException.class);
        verify(library, org.mockito.Mockito.times(ListLibraryVideosUseCaseImpl.MAX_WALK_PAGES)).list(any());
    }

    @Test
    void a_failing_library_propagates_its_unavailable_exception() {
        seedInstructor("Tango", "v1");
        when(library.list(any())).thenThrow(new VideoLibraryUnavailableException("boom"));

        assertThatThrownBy(() -> useCase.list(query(0, 20, null), instructorId, false))
            .isInstanceOf(VideoLibraryUnavailableException.class);
    }
}
