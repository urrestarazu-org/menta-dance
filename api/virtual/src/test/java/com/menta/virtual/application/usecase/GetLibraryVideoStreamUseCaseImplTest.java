package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoStatus;
import com.menta.virtual.application.dto.LibraryVideoStreamView;
import com.menta.virtual.application.exception.VideoNotPlayableException;
import com.menta.virtual.application.exception.VideoNotPlayableException.Reason;
import com.menta.virtual.application.port.out.BunnyNetSignatureService;
import com.menta.virtual.application.port.out.Clock;
import com.menta.virtual.application.port.out.VideoLibraryPort;
import com.menta.virtual.domain.exception.VideoLibraryUnavailableException;
import com.menta.virtual.domain.exception.VideoNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GetLibraryVideoStreamUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-11-28T12:00:00Z");
    private static final String SIGNED_URL = "https://cdn.example/guid-1/play_720p.mp4?token=SECRET&expires=1";

    private final VideoLibraryPort library = mock(VideoLibraryPort.class);
    private final BunnyNetSignatureService signatureService = mock(BunnyNetSignatureService.class);
    private final Clock clock = () -> NOW;
    private final GetLibraryVideoStreamUseCaseImpl useCase =
        new GetLibraryVideoStreamUseCaseImpl(library, signatureService, clock);

    private static LibraryVideo video(LibraryVideoStatus status, boolean hasMp4Fallback) {
        return new LibraryVideo("guid-1", "Tango básico", status, 90, "https://cdn/t.jpg", hasMp4Fallback,
            List.of("720p"));
    }

    @Test
    @DisplayName("un video terminado y con MP4 se firma con el mismo TTL que el streaming público")
    void signs_a_finished_video_with_an_mp4_copy_for_the_same_ttl_as_the_student_stream() {
        when(library.find("guid-1")).thenReturn(Optional.of(video(LibraryVideoStatus.FINISHED, true)));
        long expectedExpiry = NOW.plus(GetPublicLessonStreamUseCaseImpl.SIGNED_URL_TTL).getEpochSecond();
        when(signatureService.generateSignedUrl("guid-1", expectedExpiry)).thenReturn(SIGNED_URL);

        LibraryVideoStreamView view = useCase.get("guid-1");

        assertThat(view.streamUrl()).isEqualTo(SIGNED_URL);
        assertThat(view.expiresAt()).isEqualTo(NOW.plus(GetPublicLessonStreamUseCaseImpl.SIGNED_URL_TTL));
        assertThat(view.type()).isEqualTo(GetPublicLessonStreamUseCaseImpl.STREAM_TYPE);
        assertThat(view.videoId()).isEqualTo("guid-1");
        assertThat(view.title()).isEqualTo("Tango básico");
        verify(signatureService).generateSignedUrl("guid-1", expectedExpiry);
    }

    @Test
    @DisplayName("el TTL es de 4 horas, como el del alumno")
    void the_ttl_is_four_hours() {
        assertThat(GetPublicLessonStreamUseCaseImpl.SIGNED_URL_TTL).isEqualTo(java.time.Duration.ofHours(4));
        when(library.find("guid-1")).thenReturn(Optional.of(video(LibraryVideoStatus.FINISHED, true)));
        when(signatureService.generateSignedUrl("guid-1", NOW.plusSeconds(4 * 3600).getEpochSecond()))
            .thenReturn(SIGNED_URL);

        assertThat(useCase.get("guid-1").expiresAt()).isEqualTo(Instant.parse("2026-11-28T16:00:00Z"));
    }

    @Test
    @DisplayName("la vista no imprime la URL firmada")
    void the_view_never_prints_the_signed_url() {
        when(library.find("guid-1")).thenReturn(Optional.of(video(LibraryVideoStatus.FINISHED, true)));
        when(signatureService.generateSignedUrl("guid-1", NOW.plus(GetPublicLessonStreamUseCaseImpl.SIGNED_URL_TTL)
            .getEpochSecond())).thenReturn(SIGNED_URL);

        assertThat(useCase.get("guid-1").toString()).doesNotContain("SECRET").doesNotContain("https://");
    }

    @Test
    @DisplayName("un video que la biblioteca no tiene responde VIDEO_NOT_FOUND y no se firma nada")
    void an_unknown_video_is_not_found_and_nothing_is_signed() {
        when(library.find("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get("nope")).isInstanceOf(VideoNotFoundException.class);

        verifyNoInteractions(signatureService);
    }

    @ParameterizedTest(name = "{0} con MP4={1} no se reproduce: {2}")
    @CsvSource({
        "FINISHED, false, NO_MP4_COPY",
        "CREATED, true, WAITING_FOR_FILE",
        "UPLOADED, true, PROCESSING",
        "PROCESSING, true, PROCESSING",
        "TRANSCODING, false, PROCESSING",
        "JIT_SEGMENTING, true, PROCESSING",
        "JIT_PLAYLISTS_CREATED, true, PROCESSING",
        "ERROR, true, FAILED",
        "UPLOAD_FAILED, false, FAILED",
        "UNKNOWN, true, UNKNOWN_STATE"
    })
    @DisplayName("un video que todavía no se puede reproducir responde VIDEO_NOT_PLAYABLE con el motivo")
    void a_video_that_cannot_play_now_is_not_playable_with_the_reason_and_nothing_is_signed(
        LibraryVideoStatus status, boolean hasMp4Fallback, Reason reason
    ) {
        when(library.find("guid-1")).thenReturn(Optional.of(video(status, hasMp4Fallback)));

        assertThatThrownBy(() -> useCase.get("guid-1"))
            .isInstanceOfSatisfying(VideoNotPlayableException.class, e -> assertThat(e.reason()).isEqualTo(reason))
            .hasMessageNotContaining("guid-1");

        verifyNoInteractions(signatureService);
    }

    @Test
    @DisplayName("si la biblioteca no responde se propaga VIDEO_LIBRARY_UNAVAILABLE y no se firma nada")
    void an_unavailable_library_propagates_and_nothing_is_signed() {
        when(library.find("guid-1")).thenThrow(new VideoLibraryUnavailableException("down"));

        assertThatThrownBy(() -> useCase.get("guid-1")).isInstanceOf(VideoLibraryUnavailableException.class);

        verifyNoInteractions(signatureService);
    }

    @ParameterizedTest(name = "el id [{0}] no es válido")
    @ValueSource(strings = {"", " ", "a/b", "a..b", "a b", "a%2Fb", "..", "id?x=1"})
    @DisplayName("un id que no es un identificador simple se rechaza sin tocar la biblioteca")
    void an_invalid_id_is_rejected_before_the_library_is_contacted(String videoId) {
        assertThatThrownBy(() -> useCase.get(videoId)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(library, signatureService);
    }

    @Test
    @DisplayName("un id nulo se rechaza sin tocar la biblioteca")
    void a_null_id_is_rejected() {
        assertThatThrownBy(() -> useCase.get(null)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(library, signatureService);
    }
}
