package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoOrder;
import com.menta.virtual.application.dto.LibraryVideoStatus;
import com.menta.virtual.application.dto.ManagedVideo;
import com.menta.virtual.application.dto.ManagedVideoPage;
import com.menta.virtual.application.dto.VideoLessonUsage;
import com.menta.virtual.application.dto.VideoLibraryQuery;
import com.menta.virtual.application.exception.VideoInUseException;
import com.menta.virtual.application.port.in.DeleteLibraryVideoUseCase;
import com.menta.virtual.application.port.in.ListLibraryVideosUseCase;
import com.menta.virtual.application.port.in.RenameLibraryVideoUseCase;
import com.menta.virtual.domain.exception.VideoLibraryUnavailableException;
import com.menta.virtual.domain.exception.VideoNotFoundException;
import com.menta.virtual.domain.exception.VideoNotOwnedException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VirtualVideoAdminControllerTest {

    private static final String URL = "/api/v1/admin/virtual/videos";

    private ListLibraryVideosUseCase useCase;
    private RenameLibraryVideoUseCase renameUseCase;
    private DeleteLibraryVideoUseCase deleteUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(ListLibraryVideosUseCase.class);
        renameUseCase = mock(RenameLibraryVideoUseCase.class);
        deleteUseCase = mock(DeleteLibraryVideoUseCase.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new VirtualVideoAdminController(useCase, renameUseCase, deleteUseCase))
            .setControllerAdvice(new VirtualRequestExceptionHandler(), new VirtualCourseExceptionHandler())
            .build();
    }

    private static Authentication authOf(UUID userId, String role) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private static ManagedVideoPage pageWithOneVideoAndOneMissing() {
        LibraryVideo video = new LibraryVideo("guid-1", "Tango", LibraryVideoStatus.FINISHED, 125,
            "https://cdn/t.jpg", true, List.of("360p", "720p"));
        VideoLessonUsage usage = new VideoLessonUsage("c-1", "Curso", "m-1", "Módulo", "l-1", "Lección");
        return new ManagedVideoPage(List.of(
            new ManagedVideo("guid-1", video, List.of(usage)),
            new ManagedVideo("gone", null, List.of(usage))
        ), 1, 10, 12);
    }

    @Test
    void lists_the_videos_with_the_paging_the_lessons_and_the_missing_flag() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(useCase.list(any(), eq(adminId), eq(true))).thenReturn(pageWithOneVideoAndOneMissing());

        mockMvc.perform(get(URL).principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(12))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items[0].videoId").value("guid-1"))
            .andExpect(jsonPath("$.items[0].title").value("Tango"))
            .andExpect(jsonPath("$.items[0].status").value("FINISHED"))
            .andExpect(jsonPath("$.items[0].durationSeconds").value(125))
            .andExpect(jsonPath("$.items[0].thumbnailUrl").value("https://cdn/t.jpg"))
            .andExpect(jsonPath("$.items[0].hasMp4Fallback").value(true))
            .andExpect(jsonPath("$.items[0].availableResolutions[1]").value("720p"))
            .andExpect(jsonPath("$.items[0].missingInLibrary").value(false))
            .andExpect(jsonPath("$.items[0].lessons[0].courseTitle").value("Curso"))
            .andExpect(jsonPath("$.items[0].lessons[0].moduleTitle").value("Módulo"))
            .andExpect(jsonPath("$.items[0].lessons[0].lessonTitle").value("Lección"))
            .andExpect(jsonPath("$.items[1].videoId").value("gone"))
            .andExpect(jsonPath("$.items[1].missingInLibrary").value(true))
            .andExpect(jsonPath("$.items[1].title").value((Object) null))
            .andExpect(jsonPath("$.items[1].status").value((Object) null))
            .andExpect(jsonPath("$.items[1].durationSeconds").value((Object) null))
            .andExpect(jsonPath("$.items[1].thumbnailUrl").value((Object) null))
            .andExpect(jsonPath("$.items[1].hasMp4Fallback").value(false))
            .andExpect(jsonPath("$.items[1].availableResolutions").isEmpty());
    }

    @Test
    void the_instructor_is_passed_as_a_non_admin_and_the_defaults_apply() throws Exception {
        UUID instructorId = UUID.randomUUID();
        when(useCase.list(any(), eq(instructorId), eq(false)))
            .thenReturn(new ManagedVideoPage(List.of(), 0, 20, 0));

        mockMvc.perform(get(URL).principal(authOf(instructorId, "INSTRUCTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.totalPages").value(0));

        ArgumentCaptor<VideoLibraryQuery> query = ArgumentCaptor.forClass(VideoLibraryQuery.class);
        verify(useCase).list(query.capture(), eq(instructorId), eq(false));
        assertThat(query.getValue())
            .isEqualTo(new VideoLibraryQuery(
                0, ListLibraryVideosUseCase.DEFAULT_PAGE_SIZE, null, LibraryVideoOrder.DATE
            ));
    }

    @Test
    void passes_the_page_the_size_the_search_and_the_order_through() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(useCase.list(any(), any(), eq(true))).thenReturn(new ManagedVideoPage(List.of(), 3, 50, 0));

        mockMvc.perform(get(URL).param("page", "3").param("size", "50").param("search", " tango ")
                .param("orderBy", "TITLE").principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isOk());

        ArgumentCaptor<VideoLibraryQuery> query = ArgumentCaptor.forClass(VideoLibraryQuery.class);
        verify(useCase).list(query.capture(), eq(adminId), eq(true));
        assertThat(query.getValue()).isEqualTo(new VideoLibraryQuery(3, 50, "tango", LibraryVideoOrder.TITLE));
    }

    @Test
    void an_unsupported_order_a_bad_size_and_a_non_numeric_page_answer_400_invalid_request() throws Exception {
        for (String[] bad : new String[][] {{"orderBy", "size"}, {"size", "5"}, {"page", "-1"},
            {"page", "abc"}, {"size", "x"}}) {
            mockMvc.perform(get(URL).param(bad[0], bad[1]).principal(authOf(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void a_use_case_rejection_of_the_size_is_a_400_too() throws Exception {
        when(useCase.list(any(), any(), eq(true))).thenThrow(new IllegalArgumentException("size"));

        mockMvc.perform(get(URL).param("size", "100").principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void an_unavailable_library_answers_503_without_leaking_the_reason() throws Exception {
        when(useCase.list(any(), any(), eq(true)))
            .thenThrow(new VideoLibraryUnavailableException("Bunny Stream library list failed: status 401"));

        mockMvc.perform(get(URL).principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("VIDEO_LIBRARY_UNAVAILABLE"))
            .andExpect(jsonPath("$.detail")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("401"))));
    }

    // --- rename (#841) ----------------------------------------------------

    private static final String ONE = URL + "/guid-1";

    @Test
    void renames_a_video_and_answers_it_with_its_new_title_and_lessons() throws Exception {
        UUID instructorId = UUID.randomUUID();
        LibraryVideo renamed = new LibraryVideo("guid-1", "Nuevo", LibraryVideoStatus.FINISHED, 125,
            "https://cdn/t.jpg", true, List.of("720p"));
        VideoLessonUsage usage = new VideoLessonUsage("c-1", "Curso", "m-1", "Módulo", "l-1", "Lección");
        when(renameUseCase.rename("guid-1", "Nuevo", instructorId, false))
            .thenReturn(new ManagedVideo("guid-1", renamed, List.of(usage)));

        mockMvc.perform(put(ONE).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Nuevo\"}")
                .principal(authOf(instructorId, "INSTRUCTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.videoId").value("guid-1"))
            .andExpect(jsonPath("$.title").value("Nuevo"))
            .andExpect(jsonPath("$.status").value("FINISHED"))
            .andExpect(jsonPath("$.missingInLibrary").value(false))
            .andExpect(jsonPath("$.lessons[0].lessonTitle").value("Lección"));
    }

    @Test
    void the_admin_is_passed_as_an_admin_when_renaming() throws Exception {
        UUID adminId = UUID.randomUUID();
        LibraryVideo renamed =
            new LibraryVideo("guid-1", "Nuevo", LibraryVideoStatus.FINISHED, 1, null, true, List.of());
        when(renameUseCase.rename("guid-1", "Nuevo", adminId, true))
            .thenReturn(new ManagedVideo("guid-1", renamed, List.of()));

        mockMvc.perform(put(ONE).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Nuevo\"}")
                .principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isOk());

        verify(renameUseCase).rename("guid-1", "Nuevo", adminId, true);
    }

    @Test
    void a_blank_missing_too_long_or_unreadable_title_is_a_400_invalid_request_and_the_use_case_is_not_called()
        throws Exception {
        String tooLong = "a".repeat(RenameLibraryVideoUseCase.MAX_TITLE_LENGTH + 1);
        for (String body : new String[] {
            "{\"title\":\"\"}", "{\"title\":\"   \"}", "{}", "{\"title\":null}",
            "{\"title\":\"" + tooLong + "\"}", "not json", ""}) {
            mockMvc.perform(put(ONE).contentType(MediaType.APPLICATION_JSON).content(body)
                    .principal(authOf(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(renameUseCase);
    }

    @Test
    void renaming_with_an_invalid_id_answers_400_invalid_request() throws Exception {
        when(renameUseCase.rename(any(), any(), any(), eq(true))).thenThrow(new IllegalArgumentException("id"));

        mockMvc.perform(put(URL + "/a..b").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}")
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void renaming_an_unknown_video_answers_404_video_not_found() throws Exception {
        when(renameUseCase.rename(any(), any(), any(), eq(true))).thenThrow(new VideoNotFoundException());

        mockMvc.perform(put(ONE).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}")
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VIDEO_NOT_FOUND"));
    }

    @Test
    void renaming_a_video_the_instructor_does_not_own_answers_403_video_not_owned() throws Exception {
        when(renameUseCase.rename(any(), any(), any(), eq(false))).thenThrow(new VideoNotOwnedException());

        mockMvc.perform(put(ONE).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}")
                .principal(authOf(UUID.randomUUID(), "INSTRUCTOR")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("VIDEO_NOT_OWNED"));
    }

    @Test
    void renaming_with_an_unavailable_library_answers_503_without_leaking_the_reason() throws Exception {
        when(renameUseCase.rename(any(), any(), any(), eq(true)))
            .thenThrow(new VideoLibraryUnavailableException("Bunny Stream library rename failed: status 401"));

        mockMvc.perform(put(ONE).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}")
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("VIDEO_LIBRARY_UNAVAILABLE"))
            .andExpect(jsonPath("$.detail")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("401"))));
    }

    // --- delete (#841) ----------------------------------------------------

    @Test
    void deletes_a_video_with_204_and_no_body() throws Exception {
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(delete(ONE).principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(deleteUseCase).delete("guid-1", adminId, true);
    }

    @Test
    void deleting_a_video_a_lesson_uses_answers_409_video_in_use_with_the_lessons_and_nothing_else() throws Exception {
        VideoLessonUsage first = new VideoLessonUsage("c-1", "Curso 1", "m-1", "Módulo 1", "l-1", "Lección 1");
        VideoLessonUsage second = new VideoLessonUsage("c-2", "Curso 2", "m-2", "Módulo 2", "l-2", "Lección 2");
        doThrow(new VideoInUseException(List.of(first, second))).when(deleteUseCase).delete(any(), any(), eq(true));

        mockMvc.perform(delete(ONE).principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VIDEO_IN_USE"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("lecciones")))
            .andExpect(jsonPath("$.lessons.length()").value(2))
            .andExpect(jsonPath("$.lessons[0].courseId").value("c-1"))
            .andExpect(jsonPath("$.lessons[0].courseTitle").value("Curso 1"))
            .andExpect(jsonPath("$.lessons[0].moduleId").value("m-1"))
            .andExpect(jsonPath("$.lessons[0].moduleTitle").value("Módulo 1"))
            .andExpect(jsonPath("$.lessons[0].lessonId").value("l-1"))
            .andExpect(jsonPath("$.lessons[0].lessonTitle").value("Lección 1"))
            .andExpect(jsonPath("$.lessons[1].lessonTitle").value("Lección 2"));
    }

    @Test
    void deleting_as_an_instructor_answers_403_video_not_owned() throws Exception {
        doThrow(new VideoNotOwnedException()).when(deleteUseCase).delete(any(), any(), eq(false));

        mockMvc.perform(delete(ONE).principal(authOf(UUID.randomUUID(), "INSTRUCTOR")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("VIDEO_NOT_OWNED"));
    }

    @Test
    void deleting_with_an_invalid_id_answers_400_invalid_request() throws Exception {
        doThrow(new IllegalArgumentException("id")).when(deleteUseCase).delete(any(), any(), eq(true));

        mockMvc.perform(delete(URL + "/a..b").principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void deleting_with_an_unavailable_library_answers_503() throws Exception {
        doThrow(new VideoLibraryUnavailableException("down")).when(deleteUseCase).delete(any(), any(), eq(true));

        mockMvc.perform(delete(ONE).principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("VIDEO_LIBRARY_UNAVAILABLE"));
    }
}
