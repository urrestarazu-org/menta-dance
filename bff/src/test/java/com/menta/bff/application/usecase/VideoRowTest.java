package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.application.dto.ManagedVideo;
import com.menta.bff.application.dto.VideoLesson;
import com.menta.bff.application.dto.VideoStatus;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("VideoRow")
class VideoRowTest {

    private static final VideoLesson LESSON =
            new VideoLesson("c-1", "Tango", "m-1", "Base", "l-1", "Abrazo");

    private static ManagedVideo video(VideoStatus status, boolean mp4, Integer seconds, String thumbnail) {
        return new ManagedVideo("v-1", "Abrazo", status, seconds, thumbnail, mp4, List.of("720p"), false,
                List.of(LESSON));
    }

    static Stream<Arguments> statuses() {
        return Stream.of(
                Arguments.of(VideoStatus.FINISHED, true, "Con MP4", "success", null),
                Arguments.of(VideoStatus.FINISHED, false, "Sin MP4", "warning",
                        "No se va a reproducir con la configuración actual del CDN."),
                Arguments.of(VideoStatus.UPLOADED, false, "Procesando", "warning", null),
                Arguments.of(VideoStatus.PROCESSING, true, "Procesando", "warning", null),
                Arguments.of(VideoStatus.TRANSCODING, false, "Procesando", "warning", null),
                Arguments.of(VideoStatus.JIT_SEGMENTING, false, "Procesando", "warning", null),
                Arguments.of(VideoStatus.JIT_PLAYLISTS_CREATED, false, "Procesando", "warning", null),
                Arguments.of(VideoStatus.CREATED, false, "Esperando el archivo", "neutral", null),
                Arguments.of(VideoStatus.ERROR, false, "Con error", "danger", null),
                Arguments.of(VideoStatus.UPLOAD_FAILED, false, "Con error", "danger", "Falló la subida del archivo."),
                Arguments.of(VideoStatus.UNKNOWN, false, "Sin dato", "neutral", null));
    }

    @ParameterizedTest(name = "{0} with MP4={1} is worded {2}")
    @MethodSource("statuses")
    @DisplayName("the state is worded as text with a kind for the badge, and only says what it knows")
    void wordsTheState(VideoStatus status, boolean mp4, String label, String kind, String note) {
        VideoRow row = VideoRow.of(video(status, mp4, 90, null));

        assertThat(row.statusLabel()).isEqualTo(label);
        assertThat(row.statusKind()).isEqualTo(kind);
        assertThat(row.statusNote()).isEqualTo(note);
        assertThat(row.missingInLibrary()).isFalse();
        assertThat(row.manageable()).isTrue();
    }

    @Test
    @DisplayName("a video that no longer exists says so, keeps its lessons and cannot be renamed or deleted")
    void missingInLibrary() {
        VideoRow row = VideoRow.of(new ManagedVideo("v-9", null, VideoStatus.UNKNOWN, null, null, false, List.of(),
                true, List.of(LESSON)));

        assertThat(row.statusLabel()).isEqualTo("Ya no existe");
        assertThat(row.statusKind()).isEqualTo("danger");
        assertThat(row.statusNote()).isEqualTo("Una lección usa este video, pero la biblioteca ya no lo tiene.");
        assertThat(row.title()).isEqualTo("Sin título");
        assertThat(row.untitled()).isTrue();
        assertThat(row.videoId()).isEqualTo("v-9");
        assertThat(row.missingInLibrary()).isTrue();
        assertThat(row.manageable()).isFalse();
        assertThat(row.lessons()).containsExactly(LESSON);
        assertThat(row.inUse()).isTrue();
    }

    @Test
    @DisplayName("a video no lesson uses is not in use; the title is the library's, and a blank one is 'Sin título'")
    void titleAndUse() {
        VideoRow plain = VideoRow.of(new ManagedVideo("v-1", "Mi video", VideoStatus.FINISHED, 5, null, true,
                List.of(), false, List.of()));
        VideoRow blank = VideoRow.of(new ManagedVideo("v-2", "   ", VideoStatus.FINISHED, 5, null, true,
                List.of(), false, List.of()));

        assertThat(plain.title()).isEqualTo("Mi video");
        assertThat(plain.untitled()).isFalse();
        assertThat(blank.untitled()).isTrue();
        assertThat(plain.inUse()).isFalse();
        assertThat(blank.title()).isEqualTo("Sin título");
    }

    @Test
    @DisplayName("the administrator may rename any video the library has; the instructor only one an own lesson uses")
    void renameIsOfferedByRole() {
        VideoRow used = VideoRow.of(video(VideoStatus.FINISHED, true, 5, null));
        VideoRow unused = VideoRow.of(new ManagedVideo("v-2", "Suelto", VideoStatus.FINISHED, 5, null, true,
                List.of(), false, List.of()));
        VideoRow missing = VideoRow.of(new ManagedVideo("v-9", null, VideoStatus.UNKNOWN, null, null, false,
                List.of(), true, List.of(LESSON)));

        assertThat(used.renameableBy(false)).isTrue();
        assertThat(unused.renameableBy(false)).isFalse();
        assertThat(unused.renameableBy(true)).isTrue();
        assertThat(missing.renameableBy(true)).isFalse();
        assertThat(missing.renameableBy(false)).isFalse();
    }

    static Stream<Arguments> durations() {
        return Stream.of(
                Arguments.of(5, "0:05"),
                Arguments.of(65, "1:05"),
                Arguments.of(600, "10:00"),
                Arguments.of(3599, "59:59"),
                Arguments.of(3600, "1:00:00"),
                Arguments.of(3725, "1:02:05"),
                Arguments.of(0, "Sin dato"),
                Arguments.of(-3, "Sin dato"),
                Arguments.of(null, "Sin dato"));
    }

    @ParameterizedTest(name = "{0} s is {1}")
    @MethodSource("durations")
    @DisplayName("the length reads as minutes and seconds, or hours, and 'Sin dato' when there is none")
    void durationLabel(Integer seconds, String label) {
        assertThat(VideoRow.of(video(VideoStatus.FINISHED, true, seconds, null)).durationLabel()).isEqualTo(label);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://vz-1.b-cdn.net/abc/thumbnail.jpg", "HTTPS://cdn.example/t.png"})
    @DisplayName("an https thumbnail is kept as the API gave it")
    void keepsAnHttpsThumbnail(String url) {
        VideoRow row = VideoRow.of(video(VideoStatus.FINISHED, true, 5, url));

        assertThat(row.thumbnailUrl()).isEqualTo(url);
        assertThat(row.hasThumbnail()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "http://cdn.example/t.png", "javascript:alert(1)", "data:image/png;base64,AAAA",
        "//cdn.example/t.png", "/relative/t.png", "https://", "https:///t.png", "https://cdn .example/t.png",
        "https://cdn.example/t.png\" onerror=\"x", "ftp://cdn.example/t.png"})
    @DisplayName("anything that is not an absolute https URL is dropped: the screen shows the placeholder")
    void dropsAnythingButHttps(String url) {
        VideoRow row = VideoRow.of(video(VideoStatus.FINISHED, true, 5, url));

        assertThat(row.thumbnailUrl()).isNull();
        assertThat(row.hasThumbnail()).isFalse();
    }

    @Test
    @DisplayName("an https URL longer than 2048 characters is dropped")
    void dropsAnAbsurdlyLongThumbnail() {
        String url = "https://cdn.example/" + "a".repeat(2100);

        assertThat(VideoRow.of(video(VideoStatus.FINISHED, true, 5, url)).thumbnailUrl()).isNull();
    }

    @Test
    @DisplayName("a missing video never shows a thumbnail, even if the API sent one")
    void missingHasNoThumbnail() {
        VideoRow row = VideoRow.of(new ManagedVideo("v-9", null, VideoStatus.UNKNOWN, null,
                "https://cdn.example/t.png", false, List.of(), true, List.of()));

        assertThat(row.hasThumbnail()).isFalse();
    }
}
