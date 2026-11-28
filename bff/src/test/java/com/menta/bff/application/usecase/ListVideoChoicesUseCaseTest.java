package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.ManagedVideo;
import com.menta.bff.application.dto.VideoListQuery;
import com.menta.bff.application.dto.VideoPage;
import com.menta.bff.application.dto.VideoStatus;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException.Code;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListVideoChoicesUseCase")
class ListVideoChoicesUseCaseTest {

    private static final String TOKEN = "access-token-abc";
    private static final VideoListQuery BIGGEST_PAGE = new VideoListQuery(null, 0, 100);
    private static final ManagedVideo ABRAZO = video("v-1", "Abrazo", VideoStatus.FINISHED, true, false);
    private static final ManagedVideo GIRO = video("v-2", "Giro", VideoStatus.PROCESSING, false, false);
    private static final ManagedVideo GONE = video("gone", null, VideoStatus.UNKNOWN, false, true);

    @Mock
    private VirtualVideoManagementApiClient client;

    private ListVideoChoicesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListVideoChoicesUseCaseImpl(client);
    }

    private static ManagedVideo video(String id, String title, VideoStatus status, boolean mp4, boolean missing) {
        return new ManagedVideo(id, title, status, 10, null, mp4, List.of(), missing, List.of());
    }

    private static VideoPage page(int totalPages, long total, ManagedVideo... videos) {
        return new VideoPage(List.of(videos), 0, 100, total, totalPages);
    }

    @Test
    @DisplayName("asks for the biggest page the API allows, with no search, and keeps the API's order")
    void asksForTheBiggestPage() {
        when(client.listVideos(TOKEN, BIGGEST_PAGE)).thenReturn(page(1, 2, GIRO, ABRAZO));

        VideoChoices choices = useCase.execute(TOKEN, null);

        verify(client).listVideos(TOKEN, BIGGEST_PAGE);
        assertThat(choices.options()).extracting(VideoOption::videoId).containsExactly("v-2", "v-1");
        assertThat(choices.unavailable()).isFalse();
        assertThat(choices.truncated()).isFalse();
        assertThat(choices.selected()).isNull();
    }

    @Test
    @DisplayName("says so when the library has more videos than the page shows")
    void truncated() {
        when(client.listVideos(TOKEN, BIGGEST_PAGE)).thenReturn(page(3, 250, ABRAZO));

        VideoChoices choices = useCase.execute(TOKEN, null);

        assertThat(choices.truncated()).isTrue();
        assertThat(choices.totalElements()).isEqualTo(250);
    }

    @Test
    @DisplayName("a video that no longer exists is not offered as a choice")
    void missingVideosAreNotOffered() {
        when(client.listVideos(TOKEN, BIGGEST_PAGE)).thenReturn(page(1, 2, GONE, ABRAZO));

        assertThat(useCase.execute(TOKEN, "").options()).extracting(VideoOption::videoId).containsExactly("v-1");
    }

    @Test
    @DisplayName("the current video is selected when the list has it")
    void selectedInTheList() {
        when(client.listVideos(TOKEN, BIGGEST_PAGE)).thenReturn(page(1, 2, ABRAZO, GIRO));

        VideoChoices choices = useCase.execute(TOKEN, "v-2");

        assertThat(choices.selected().videoId()).isEqualTo("v-2");
        assertThat(choices.options()).extracting(VideoOption::videoId).containsExactly("v-1", "v-2");
    }

    @Test
    @DisplayName("the current video is kept as the first option when the list does not have it")
    void selectedNotInTheList() {
        when(client.listVideos(TOKEN, BIGGEST_PAGE)).thenReturn(page(2, 120, ABRAZO));

        VideoChoices choices = useCase.execute(TOKEN, "old-77");

        assertThat(choices.options()).extracting(VideoOption::videoId).containsExactly("old-77", "v-1");
        assertThat(choices.selected().videoId()).isEqualTo("old-77");
        assertThat(choices.selected().plays()).isFalse();
        assertThat(choices.selected().warning()).contains("No encontramos este video");
    }

    @Test
    @DisplayName("the current video that no longer exists in the library stays as the selection, with its warning")
    void selectedMissing() {
        when(client.listVideos(TOKEN, BIGGEST_PAGE)).thenReturn(page(1, 2, GONE, ABRAZO));

        VideoChoices choices = useCase.execute(TOKEN, "gone");

        assertThat(choices.options()).extracting(VideoOption::videoId).containsExactly("gone", "v-1");
        assertThat(choices.selected().missingInLibrary()).isTrue();
        assertThat(choices.selected().warning()).contains("ya no existe en la biblioteca");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("a blank current video selects nothing")
    void blankSelection(String selected) {
        when(client.listVideos(TOKEN, BIGGEST_PAGE)).thenReturn(page(1, 1, ABRAZO));

        VideoChoices choices = useCase.execute(TOKEN, selected);

        assertThat(choices.selected()).isNull();
        assertThat(choices.options()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(Code.class)
    @DisplayName("any refusal of the API reads as unavailable choices, never as an error")
    void refusalsAreUnavailable(Code code) {
        when(client.listVideos(any(), any())).thenThrow(new VideoManagementException(code));

        VideoChoices choices = useCase.execute(TOKEN, "v-1");

        assertThat(choices.unavailable()).isTrue();
        assertThat(choices.options()).isEmpty();
        assertThat(choices.selected()).isNull();
    }

    @Test
    @DisplayName("an API that does not answer reads as unavailable choices")
    void outageIsUnavailable() {
        when(client.listVideos(any(), any())).thenThrow(new ServiceUnavailableException("down"));

        assertThat(useCase.execute(TOKEN, null).unavailable()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("without an access token the API is not called and the choices are unavailable")
    void noToken(String token) {
        assertThat(useCase.execute(token, null).unavailable()).isTrue();

        verifyNoInteractions(client);
    }
}
