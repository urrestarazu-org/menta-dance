package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVideo;
import com.menta.bff.application.dto.VideoStatus;

/**
 * A video the lesson form offers to pick (#843), worded for the screen. The label of the option carries the title,
 * the state and whether the video has an MP4 copy in words, so the choice is informed before saving and nothing
 * depends on a colour; {@code warning} is the sentence the form shows when the video will not play.
 *
 * <p>Whether a video plays follows ADR-0049 (decision 8) and the list screen: only a finished video with an MP4
 * fallback plays with the current CDN configuration. A video that is still being processed will play later, and the
 * warning says so.
 * </p>
 *
 * @param videoId          identifier (guid) of the video, the value the form submits
 * @param title            title in the library, {@code "Sin título"} when it has none
 * @param hasMp4Fallback   whether the video has an MP4 copy
 * @param missingInLibrary whether a lesson uses the video but the library no longer has it
 * @param plays            whether the video will play as it is now; {@code false} for every case with a warning
 * @param warning          why it will not play, {@code null} when it does
 * @param thumbnailUrl     an {@code https} thumbnail, {@code null} when there is none or it is not safe to render
 * @param statusText       state and MP4 copy in words, e.g. {@code "Listo, con MP4"}
 * @param label            the whole text of the option
 */
public record VideoOption(
        String videoId, String title, boolean hasMp4Fallback, boolean missingInLibrary, boolean plays,
        String warning, String thumbnailUrl, String statusText, String label) {

    private static final String NO_TITLE = "Sin título";
    private static final String WILL_NOT_PLAY = "no se va a reproducir";
    private static final String NOT_YET = "todavía no se reproduce";

    private static final Words PROCESSING = new Words("Procesando", NOT_YET,
            "Este video todavía se está procesando, así que no se va a reproducir hasta que termine.");

    /** What a state says: its name, the short note after the label and the warning (both null if it plays). */
    private record Words(String state, String shortNote, String warning) {
    }

    /** Words a video of the list. */
    static VideoOption of(ManagedVideo video) {
        VideoRow row = VideoRow.of(video);
        if (video.missingInLibrary()) {
            return new VideoOption(video.videoId(), NO_TITLE, false, true, false,
                    "Este video ya no existe en la biblioteca, así que no se va a reproducir. Elegí otro.", null,
                    "ya no existe en la biblioteca",
                    "Video " + video.videoId() + " — ya no existe en la biblioteca (" + WILL_NOT_PLAY + ")");
        }
        Words words = wordsOf(video);
        String statusText = words.state() + ", " + (video.hasMp4Fallback() ? "con MP4" : "sin MP4");
        String name = row.untitled() ? NO_TITLE + " (" + video.videoId() + ")" : row.title();
        String label = name + " — " + statusText + (words.shortNote() == null ? "" : " (" + words.shortNote() + ")");
        return new VideoOption(video.videoId(), row.title(), video.hasMp4Fallback(), false,
                words.warning() == null, words.warning(), row.thumbnailUrl(), statusText, label);
    }

    /** An identifier the list does not have (typed by hand, or older than the page): nothing is known about it. */
    static VideoOption notInList(String videoId) {
        return new VideoOption(videoId, NO_TITLE, false, false, false,
                "No encontramos este video en la lista de la biblioteca, así que no podemos asegurar que se "
                        + "reproduzca.", null, "no está en la lista",
                "Video " + videoId + " — no está en la lista (no sabemos si se reproduce)");
    }

    private static Words wordsOf(ManagedVideo video) {
        VideoStatus status = video.status() == null ? VideoStatus.UNKNOWN : video.status();
        return switch (status) {
            case FINISHED -> video.hasMp4Fallback() ? new Words("Listo", null, null) : new Words("Listo", WILL_NOT_PLAY,
                    "Este video no se va a reproducir con la configuración actual del CDN: no tiene MP4 de respaldo.");
            case UPLOADED, PROCESSING, TRANSCODING, JIT_SEGMENTING, JIT_PLAYLISTS_CREATED -> PROCESSING;
            case CREATED -> new Words("Esperando el archivo", NOT_YET,
                    "Este video todavía no recibió el archivo, así que no se va a reproducir hasta que se suba.");
            case ERROR, UPLOAD_FAILED -> new Words("Con error", WILL_NOT_PLAY,
                    "Este video tiene un error, así que no se va a reproducir.");
            case UNKNOWN -> new Words("Estado desconocido", "no sabemos si se reproduce",
                    "No sabemos el estado de este video, así que no podemos asegurar que se reproduzca.");
        };
    }
}
