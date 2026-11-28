package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVideo;
import com.menta.bff.application.dto.VideoLesson;
import com.menta.bff.application.dto.VideoStatus;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * A video in the management list, worded for the screen (#842).
 *
 * <p>The state is always text with a {@code statusKind} (the badge colour, {@code success}, {@code warning},
 * {@code danger} or {@code neutral}) that only reinforces it. The thumbnail is the API's URL and is kept only when it
 * is an absolute {@code https} one; the BFF never builds a URL of the library.
 * </p>
 *
 * @param videoId          identifier (guid) of the video
 * @param title            title in the library, {@code "Sin título"} when it has none
 * @param untitled         whether {@code title} is that stand-in and not a real title of the library
 * @param statusLabel      what a person reads about the state of the video
 * @param statusKind       badge kind of the state
 * @param statusNote       one more sentence about the state, {@code null} when there is nothing to add
 * @param durationLabel    length as {@code m:ss} or {@code h:mm:ss}, {@code "Sin dato"} when unknown
 * @param thumbnailUrl     an {@code https} thumbnail, {@code null} when there is none or it is not safe to render
 * @param missingInLibrary whether a lesson uses the video but the library no longer has it
 * @param lessons          the lessons that use the video
 * @param manageable       whether the screen offers to rename or delete it: only a video the library still has
 */
public record VideoRow(
        String videoId, String title, boolean untitled, String statusLabel, String statusKind, String statusNote,
        String durationLabel,
        String thumbnailUrl, boolean missingInLibrary, List<VideoLesson> lessons, boolean manageable) {

    private static final String NO_DATA = "Sin dato";
    private static final String NO_TITLE = "Sin título";
    private static final int MAX_THUMBNAIL_LENGTH = 2048;
    private static final int SECONDS_PER_HOUR = 3600;
    private static final int SECONDS_PER_MINUTE = 60;

    /** Words a video. Which actions apply is only a hint for the screen: the API decides each one. */
    static VideoRow of(ManagedVideo video) {
        boolean untitled = video.title() == null || video.title().isBlank();
        boolean missing = video.missingInLibrary();
        State state = missing ? new State("Ya no existe", "danger",
                "Una lección usa este video, pero la biblioteca ya no lo tiene.") : stateOf(video);
        return new VideoRow(video.videoId(), untitled ? NO_TITLE : video.title(), untitled, state.label(),
                state.kind(), state.note(), durationLabel(video.durationSeconds()),
                missing ? null : https(video.thumbnailUrl()), missing, List.copyOf(video.lessons()), !missing);
    }

    /** Whether the video has a thumbnail the screen may render. */
    public boolean hasThumbnail() {
        return thumbnailUrl != null;
    }

    /**
     * Whether the screen offers to rename the video to that role (#843): the administrator any video the library
     * still has, the instructor only one that at least one of the own lessons uses ({@code lessons} holds only those
     * for an instructor). A hint for the screen: the API decides and answers {@code 403 VIDEO_NOT_OWNED}.
     */
    public boolean renameableBy(boolean administrator) {
        return manageable && (administrator || inUse());
    }

    /** Whether at least one lesson uses the video. */
    public boolean inUse() {
        return !lessons.isEmpty();
    }

    private record State(String label, String kind, String note) {
    }

    private static final State PROCESSING_STATE = new State("Procesando", "warning", null);

    private static State stateOf(ManagedVideo video) {
        VideoStatus status = video.status() == null ? VideoStatus.UNKNOWN : video.status();
        return switch (status) {
            case FINISHED -> finished(video.hasMp4Fallback());
            case UPLOADED, PROCESSING, TRANSCODING, JIT_SEGMENTING, JIT_PLAYLISTS_CREATED -> PROCESSING_STATE;
            case CREATED -> new State("Esperando el archivo", "neutral", null);
            case ERROR -> new State("Con error", "danger", null);
            case UPLOAD_FAILED -> new State("Con error", "danger", "Falló la subida del archivo.");
            case UNKNOWN -> new State(NO_DATA, "neutral", null);
        };
    }

    private static State finished(boolean hasMp4Fallback) {
        return hasMp4Fallback
                ? new State("Con MP4", "success", null)
                : new State("Sin MP4", "warning", "No se va a reproducir con la configuración actual del CDN.");
    }

    private static String durationLabel(Integer seconds) {
        if (seconds == null || seconds <= 0) {
            return NO_DATA;
        }
        int hours = seconds / SECONDS_PER_HOUR;
        int minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE;
        int rest = seconds % SECONDS_PER_MINUTE;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, rest)
                : String.format(Locale.ROOT, "%d:%02d", minutes, rest);
    }

    private static boolean unsafeInUrl(int character) {
        return Character.isWhitespace(character) || "\"<>'".indexOf(character) >= 0;
    }

    /** The URL when it is an absolute {@code https} one with a host, no blanks and a sane length; else {@code null}. */
    private static String https(String url) {
        if (url == null || url.isBlank() || url.length() > MAX_THUMBNAIL_LENGTH
                || url.chars().anyMatch(VideoRow::unsafeInUrl)) {
            return null;
        }
        try {
            URI uri = new URI(url);
            boolean secure = "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
            return secure ? url : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
