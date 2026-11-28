package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.ManagedVideo;
import com.menta.bff.application.dto.VideoLesson;
import com.menta.bff.application.dto.VideoListQuery;
import com.menta.bff.application.dto.VideoPage;
import com.menta.bff.application.dto.VideoStatus;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException.Code;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Video management use cases")
class VideoUseCasesTest {

    private static final String TOKEN = "access-token-abc";
    private static final String VIDEO_ID = "3f2a9c1e-7b4d-4e6a-8c10-2d5e9f0a1b3c";
    private static final VideoLesson LESSON = new VideoLesson("c-1", "Tango", "m-1", "Base", "l-1", "Abrazo");
    private static final ManagedVideo VIDEO = new ManagedVideo(VIDEO_ID, "Abrazo", VideoStatus.FINISHED, 65, null,
            true, List.of("720p"), false, List.of(LESSON));

    @Mock
    private VirtualVideoManagementApiClient client;

    private ListManagedVideosUseCase list;
    private RenameVideoUseCase rename;
    private DeleteVideoUseCase delete;

    @BeforeEach
    void setUp() {
        list = new ListManagedVideosUseCaseImpl(client);
        rename = new RenameVideoUseCaseImpl(client);
        delete = new DeleteVideoUseCaseImpl(client);
    }

    private static VideoPage page(int index, int totalPages, long total, ManagedVideo... videos) {
        return new VideoPage(List.of(videos), index, 20, total, totalPages);
    }

    // ---- list ----

    @Test
    @DisplayName("lists with the token, a fixed page size and no search, and words the rows")
    void listsFirstPage() {
        when(client.listVideos(TOKEN, new VideoListQuery(null, 0, 20))).thenReturn(page(0, 1, 1, VIDEO));

        VideosListView view = list.execute(TOKEN, null, null);

        assertThat(view.rows()).hasSize(1);
        assertThat(view.rows().get(0).title()).isEqualTo("Abrazo");
        assertThat(view.rows().get(0).durationLabel()).isEqualTo("1:05");
        assertThat(view.search()).isNull();
        assertThat(view.unavailable()).isFalse();
        assertThat(view.empty()).isFalse();
        assertThat(view.paginated()).isFalse();
        assertThat(view.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("trims the search, treats a blank one as none and a negative page as the first")
    void normalizesTheQuery() {
        when(client.listVideos(TOKEN, new VideoListQuery("tango", 0, 20))).thenReturn(page(0, 1, 1, VIDEO));

        assertThat(list.execute(TOKEN, "  tango  ", -4).search()).isEqualTo("tango");
        verify(client).listVideos(TOKEN, new VideoListQuery("tango", 0, 20));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("a blank search is no filter")
    void blankSearchIsNoFilter(String search) {
        when(client.listVideos(TOKEN, new VideoListQuery(null, 0, 20))).thenReturn(page(0, 0, 0));

        VideosListView view = list.execute(TOKEN, search, 0);

        assertThat(view.search()).isNull();
        assertThat(view.empty()).isTrue();
    }

    @Test
    @DisplayName("cuts a long search to 100 characters, without splitting a surrogate pair")
    void cutsALongSearch() {
        String longText = "a".repeat(99) + "😀" + "b".repeat(20);
        when(client.listVideos(any(), any())).thenReturn(page(0, 0, 0));
        ArgumentCaptor<VideoListQuery> query = ArgumentCaptor.forClass(VideoListQuery.class);

        list.execute(TOKEN, longText, 0);
        list.execute(TOKEN, "b".repeat(300), 0);

        verify(client, times(2)).listVideos(any(), query.capture());
        assertThat(query.getAllValues().get(0).search()).isEqualTo("a".repeat(99));
        assertThat(query.getAllValues().get(1).search()).hasSize(100);
    }

    @Test
    @DisplayName("a page past the end shows the last one")
    void pastTheEndShowsTheLast() {
        when(client.listVideos(TOKEN, new VideoListQuery(null, 9, 20))).thenReturn(page(9, 3, 41));
        when(client.listVideos(TOKEN, new VideoListQuery(null, 2, 20))).thenReturn(page(2, 3, 41, VIDEO));

        VideosListView view = list.execute(TOKEN, null, 9);

        assertThat(view.page()).isEqualTo(2);
        assertThat(view.rows()).hasSize(1);
        assertThat(view.hasPrevious()).isTrue();
        assertThat(view.hasNext()).isFalse();
        assertThat(view.previousPage()).isEqualTo(1);
        assertThat(view.pageNumber()).isEqualTo(3);
        assertThat(view.paginated()).isTrue();
    }

    @Test
    @DisplayName("an empty first page is not asked again")
    void emptyFirstPageIsFinal() {
        when(client.listVideos(TOKEN, new VideoListQuery("zzz", 0, 20))).thenReturn(page(0, 0, 0));

        VideosListView view = list.execute(TOKEN, "zzz", 0);

        assertThat(view.empty()).isTrue();
        verify(client).listVideos(TOKEN, new VideoListQuery("zzz", 0, 20));
    }

    @Test
    @DisplayName("the next page of the first of three exists")
    void nextPage() {
        when(client.listVideos(TOKEN, new VideoListQuery(null, 0, 20))).thenReturn(page(0, 3, 41, VIDEO));

        VideosListView view = list.execute(TOKEN, null, 0);

        assertThat(view.hasNext()).isTrue();
        assertThat(view.hasPrevious()).isFalse();
        assertThat(view.nextPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("the degraded view keeps the search and has no rows")
    void unavailableView() {
        VideosListView view = VideosListView.unavailableView("tango", false);

        assertThat(view.unavailable()).isTrue();
        assertThat(view.empty()).isFalse();
        assertThat(view.rows()).isEmpty();
        assertThat(view.search()).isEqualTo("tango");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("without an access token the view is unavailable, keeps the search and the API is not called")
    void listWithoutToken(String token) {
        VideosListView view = list.execute(token, " tango ", 0);

        assertThat(view.unavailable()).isTrue();
        assertThat(view.libraryDown()).isFalse();
        assertThat(view.search()).isEqualTo("tango");
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("a library that does not answer is an unavailable view that says so")
    void libraryDown() {
        when(client.listVideos(any(), any())).thenThrow(new VideoManagementException(Code.VIDEO_LIBRARY_UNAVAILABLE));

        VideosListView view = list.execute(TOKEN, "x", 0);

        assertThat(view.unavailable()).isTrue();
        assertThat(view.libraryDown()).isTrue();
        assertThat(view.search()).isEqualTo("x");
        assertThat(view.rows()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Code.class, names = {"INVALID_REQUEST", "VIDEO_NOT_OWNED", "VIDEO_NOT_FOUND", "VIDEO_IN_USE"})
    @DisplayName("any other refusal is an unavailable view")
    void otherRefusalsAreUnavailable(Code code) {
        when(client.listVideos(any(), any())).thenThrow(new VideoManagementException(code));

        VideosListView view = list.execute(TOKEN, null, 0);

        assertThat(view.unavailable()).isTrue();
        assertThat(view.libraryDown()).isFalse();
    }

    @Test
    @DisplayName("an outage of the API is an unavailable view")
    void outage() {
        when(client.listVideos(any(), any())).thenThrow(new ServiceUnavailableException("down"));

        assertThat(list.execute(TOKEN, null, 0).unavailable()).isTrue();
    }

    @Test
    @DisplayName("a role the API refuses reaches the caller")
    void accessDeniedPropagates() {
        when(client.listVideos(any(), any())).thenThrow(new VideoManagementException(Code.ACCESS_DENIED));

        assertThatThrownBy(() -> list.execute(TOKEN, null, 0)).isInstanceOfSatisfying(VideoManagementException.class,
                e -> assertThat(e.getCode()).isEqualTo(Code.ACCESS_DENIED));
    }

    // ---- rename ----

    @Test
    @DisplayName("renames with the token and the title without its ends")
    void renames() {
        rename.execute(TOKEN, VIDEO_ID, "  Nuevo título  ");

        verify(client).renameVideo(TOKEN, VIDEO_ID, "Nuevo título");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("a blank title is an invalid request, without calling the API")
    void blankTitle(String title) {
        assertThatThrownBy(() -> rename.execute(TOKEN, VIDEO_ID, title)).isInstanceOfSatisfying(
                VideoManagementException.class, e -> assertThat(e.getCode()).isEqualTo(Code.INVALID_REQUEST));
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("a title over 255 characters is an invalid request, 255 is fine")
    void titleLength() {
        assertThatThrownBy(() -> rename.execute(TOKEN, VIDEO_ID, "a".repeat(256))).isInstanceOfSatisfying(
                VideoManagementException.class, e -> assertThat(e.getCode()).isEqualTo(Code.INVALID_REQUEST));
        verifyNoInteractions(client);

        rename.execute(TOKEN, VIDEO_ID, "a".repeat(255));

        verify(client).renameVideo(TOKEN, VIDEO_ID, "a".repeat(255));
    }

    static Stream<String> invalidIds() {
        return Stream.of(null, "", "a/b", "a b", "a.b", "..", "a?x=1", "a%2Fb", "ñ", "a".repeat(129));
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    @DisplayName("an id that is not a simple identifier is a video that does not exist, without calling the API")
    void invalidIdIsNotFound(String videoId) {
        assertThatThrownBy(() -> rename.execute(TOKEN, videoId, "Título")).isInstanceOfSatisfying(
                VideoManagementException.class, e -> assertThat(e.getCode()).isEqualTo(Code.VIDEO_NOT_FOUND));
        assertThatThrownBy(() -> delete.execute(TOKEN, videoId)).isInstanceOfSatisfying(
                VideoManagementException.class, e -> assertThat(e.getCode()).isEqualTo(Code.VIDEO_NOT_FOUND));
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("without an access token renaming and deleting are unavailable and the API is not called")
    void writesWithoutToken(String token) {
        assertThatThrownBy(() -> rename.execute(token, VIDEO_ID, "Título"))
                .isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> delete.execute(token, VIDEO_ID)).isInstanceOf(ServiceUnavailableException.class);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("a refusal while renaming reaches the caller")
    void renameRefusalPropagates() {
        doThrow(new VideoManagementException(Code.VIDEO_NOT_OWNED)).when(client).renameVideo(any(), any(), any());

        assertThatThrownBy(() -> rename.execute(TOKEN, VIDEO_ID, "Título")).isInstanceOfSatisfying(
                VideoManagementException.class, e -> assertThat(e.getCode()).isEqualTo(Code.VIDEO_NOT_OWNED));
    }

    // ---- delete ----

    @Test
    @DisplayName("deletes with the token and the id")
    void deletes() {
        delete.execute(TOKEN, VIDEO_ID);

        verify(client).deleteVideo(TOKEN, VIDEO_ID);
    }

    @Test
    @DisplayName("a video in use reaches the caller with the lessons that use it")
    void deleteInUsePropagates() {
        doThrow(new VideoManagementException(Code.VIDEO_IN_USE, List.of(LESSON))).when(client)
                .deleteVideo(any(), any());

        assertThatThrownBy(() -> delete.execute(TOKEN, VIDEO_ID)).isInstanceOfSatisfying(
                VideoManagementException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(Code.VIDEO_IN_USE);
                    assertThat(e.getLessons()).containsExactly(LESSON);
                });
    }
}
