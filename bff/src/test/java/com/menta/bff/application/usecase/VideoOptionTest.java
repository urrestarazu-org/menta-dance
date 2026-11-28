package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.application.dto.ManagedVideo;
import com.menta.bff.application.dto.VideoStatus;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("VideoOption")
class VideoOptionTest {

    private static ManagedVideo video(String title, VideoStatus status, boolean mp4) {
        return new ManagedVideo("v-1", title, status, 65, "https://cdn.example/t.jpg", mp4, List.of("720p"), false,
                List.of());
    }

    static Stream<Arguments> states() {
        return Stream.of(
                Arguments.of(VideoStatus.FINISHED, true, "Abrazo — Listo, con MP4", null),
                Arguments.of(VideoStatus.FINISHED, false, "Abrazo — Listo, sin MP4 (no se va a reproducir)",
                        "Este video no se va a reproducir con la configuración actual del CDN: no tiene MP4 de "
                                + "respaldo."),
                Arguments.of(VideoStatus.PROCESSING, false, "Abrazo — Procesando, sin MP4 (todavía no se reproduce)",
                        "Este video todavía se está procesando, así que no se va a reproducir hasta que termine."),
                Arguments.of(VideoStatus.TRANSCODING, true, "Abrazo — Procesando, con MP4 (todavía no se reproduce)",
                        "Este video todavía se está procesando, así que no se va a reproducir hasta que termine."),
                Arguments.of(VideoStatus.UPLOADED, false, "Abrazo — Procesando, sin MP4 (todavía no se reproduce)",
                        "Este video todavía se está procesando, así que no se va a reproducir hasta que termine."),
                Arguments.of(VideoStatus.CREATED, false,
                        "Abrazo — Esperando el archivo, sin MP4 (todavía no se reproduce)",
                        "Este video todavía no recibió el archivo, así que no se va a reproducir hasta que se suba."),
                Arguments.of(VideoStatus.ERROR, false, "Abrazo — Con error, sin MP4 (no se va a reproducir)",
                        "Este video tiene un error, así que no se va a reproducir."),
                Arguments.of(VideoStatus.UPLOAD_FAILED, false, "Abrazo — Con error, sin MP4 (no se va a reproducir)",
                        "Este video tiene un error, así que no se va a reproducir."),
                Arguments.of(VideoStatus.UNKNOWN, false,
                        "Abrazo — Estado desconocido, sin MP4 (no sabemos si se reproduce)",
                        "No sabemos el estado de este video, así que no podemos asegurar que se reproduzca."));
    }

    @ParameterizedTest(name = "{0} (MP4 {1})")
    @MethodSource("states")
    @DisplayName("every option says its state and whether it has an MP4 in words, and warns when it will not play")
    void wordsTheOption(VideoStatus status, boolean mp4, String label, String warning) {
        VideoOption option = VideoOption.of(video("Abrazo", status, mp4));

        assertThat(option.label()).isEqualTo(label);
        assertThat(option.warning()).isEqualTo(warning);
        assertThat(option.plays()).isEqualTo(warning == null);
        assertThat(option.videoId()).isEqualTo("v-1");
        assertThat(option.hasMp4Fallback()).isEqualTo(mp4);
        assertThat(option.missingInLibrary()).isFalse();
    }

    @Test
    @DisplayName("the title is the library's; an untitled video shows its identifier so two of them can be told apart")
    void untitled() {
        VideoOption option = VideoOption.of(video("  ", VideoStatus.FINISHED, true));

        assertThat(option.title()).isEqualTo("Sin título");
        assertThat(option.label()).isEqualTo("Sin título (v-1) — Listo, con MP4");
    }

    @Test
    @DisplayName("a video that no longer exists says so and never plays, whatever else the API says about it")
    void missing() {
        VideoOption option = VideoOption.of(new ManagedVideo("gone-1", null, VideoStatus.FINISHED, 9,
                "https://cdn.example/t.jpg", true, List.of(), true, List.of()));

        assertThat(option.missingInLibrary()).isTrue();
        assertThat(option.plays()).isFalse();
        assertThat(option.label()).isEqualTo("Video gone-1 — ya no existe en la biblioteca (no se va a reproducir)");
        assertThat(option.warning()).isEqualTo(
                "Este video ya no existe en la biblioteca, así que no se va a reproducir. Elegí otro.");
        assertThat(option.thumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("the thumbnail is the https one of the API, and only that")
    void thumbnail() {
        assertThat(VideoOption.of(video("Abrazo", VideoStatus.FINISHED, true)).thumbnailUrl())
                .isEqualTo("https://cdn.example/t.jpg");
        assertThat(VideoOption.of(new ManagedVideo("v-1", "A", VideoStatus.FINISHED, 1, "http://cdn.example/t.jpg",
                true, List.of(), false, List.of())).thumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("an identifier the list does not have is an option that cannot say whether it plays")
    void notInTheList() {
        VideoOption option = VideoOption.notInList("typed-9");

        assertThat(option.videoId()).isEqualTo("typed-9");
        assertThat(option.label()).isEqualTo("Video typed-9 — no está en la lista (no sabemos si se reproduce)");
        assertThat(option.warning()).isEqualTo(
                "No encontramos este video en la lista de la biblioteca, así que no podemos asegurar que se "
                        + "reproduzca.");
        assertThat(option.plays()).isFalse();
        assertThat(option.missingInLibrary()).isFalse();
        assertThat(option.thumbnailUrl()).isNull();
    }
}
