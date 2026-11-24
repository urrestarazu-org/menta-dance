package com.menta.virtual.infrastructure.cdn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoPage;
import com.menta.virtual.application.dto.LibraryVideoStatus;
import com.menta.virtual.application.dto.VideoLibraryQuery;
import com.menta.virtual.application.port.out.VideoLibraryPort;
import com.menta.virtual.domain.exception.VideoLibraryUnavailableException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Real {@link VideoLibraryPort}: talks to the library through Bunny Stream's management API (ADR-0049):
 * {@code GET /library/{libraryId}/videos}, and per video {@code GET}, {@code POST} (rename, with a JSON
 * {@code title}; Bunny updates with POST, not PUT) and {@code DELETE} on {@code /library/{libraryId}/videos/{videoId}}.
 * A {@code 404} means "no such video": empty for {@code find}, {@code false} for {@code rename} and success for
 * {@code delete} (the video is already gone; whether Bunny deletes idempotently is not documented, #841).
 *
 * <p>Connect and read timeouts come from {@link BunnyNetProperties} (same defaults as the Mercado Pago adapter) and
 * nothing retries: a failure surfaces as {@link VideoLibraryUnavailableException}. Its message names the operation
 * and, when there is one, the HTTP status; Bunny's response body is never copied into a message, a log line or the
 * cause, because Spring's own exception messages embed it. The {@code AccessKey} header is a client default and is
 * never logged.</p>
 */
public class BunnyStreamVideoLibrary implements VideoLibraryPort {

    private static final Logger LOG = LoggerFactory.getLogger(BunnyStreamVideoLibrary.class);

    /**
     * What a video id may look like in a write: Bunny's ids are GUIDs. Anything else (a {@code .} or {@code ..}
     * segment, a slash, an encoded one) is refused before a request that mutates is built from it.
     */
    private static final Pattern WRITABLE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final RestClient restClient;
    private final String libraryId;

    public BunnyStreamVideoLibrary(BunnyNetProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getApiConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getApiReadTimeoutMs());
        this.libraryId = properties.getVideoLibraryId().strip();
        this.restClient = RestClient.builder()
            .baseUrl(properties.getApiBaseUrl().strip())
            .requestFactory(requestFactory)
            .defaultHeader("AccessKey", properties.getApiKey())
            .build();
    }

    @Override
    public LibraryVideoPage list(VideoLibraryQuery query) {
        BunnyPage response = call("list", false, () -> restClient.get()
            .uri(builder -> {
                builder.path("/library/{libraryId}/videos")
                    .queryParam("page", query.page() + 1)
                    .queryParam("itemsPerPage", query.size())
                    .queryParam("orderBy", query.order().wireValue());
                if (query.search() != null) {
                    builder.queryParam("search", "{search}");
                    return builder.build(libraryId, query.search());
                }
                return builder.build(libraryId);
            })
            .retrieve()
            .body(BunnyPage.class));
        if (response == null) {
            throw unavailable("list", "empty body", null);
        }
        List<LibraryVideo> items = response.items() == null
            ? List.of()
            : response.items().stream().filter(video -> video.guid() != null).map(BunnyVideo::toVideo).toList();
        return new LibraryVideoPage(items, query.page(), query.size(), response.totalItems());
    }

    @Override
    public Optional<LibraryVideo> find(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            return Optional.empty();
        }
        try {
            BunnyVideo response = call("find", true, () -> restClient.get()
                .uri("/library/{libraryId}/videos/{videoId}", libraryId, videoId)
                .retrieve()
                .body(BunnyVideo.class));
            if (response == null || response.guid() == null) {
                throw unavailable("find", "empty body", null);
            }
            return Optional.of(response.toVideo());
        } catch (NotFoundInLibrary notFound) {
            return Optional.empty();
        }
    }

    @Override
    public boolean rename(String videoId, String title) {
        requireWritableId(videoId);
        try {
            call("rename", true, () -> restClient.post()
                .uri("/library/{libraryId}/videos/{videoId}", libraryId, videoId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RenameRequest(title))
                .retrieve()
                .toBodilessEntity());
            return true;
        } catch (NotFoundInLibrary notFound) {
            return false;
        }
    }

    @Override
    public void delete(String videoId) {
        requireWritableId(videoId);
        try {
            call("delete", true, () -> restClient.delete()
                .uri("/library/{libraryId}/videos/{videoId}", libraryId, videoId)
                .retrieve()
                .toBodilessEntity());
        } catch (NotFoundInLibrary notFound) {
            LOG.info("Bunny Stream library delete: the video was already gone (status 404)");
        }
    }

    private static void requireWritableId(String videoId) {
        if (videoId == null || !WRITABLE_ID.matcher(videoId).matches()) {
            throw new IllegalArgumentException("Not a valid video id");
        }
    }

    /**
     * Runs one request. A {@code 404} is signalled as {@link NotFoundInLibrary} when {@code notFoundIsAbsence}
     * (the operation targets one video), and is an ordinary failure otherwise.
     */
    private <T> T call(String operation, boolean notFoundIsAbsence, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException.NotFound e) {
            if (notFoundIsAbsence) {
                throw new NotFoundInLibrary();
            }
            throw unavailable(operation, "status 404", e);
        } catch (RestClientResponseException e) {
            throw unavailable(operation, "status " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw unavailable(operation, "no usable response (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    private static VideoLibraryUnavailableException unavailable(String operation, String reason, Exception cause) {
        // Spring's exception messages embed the response body, so neither the message nor the cause is kept.
        String message = "Bunny Stream library " + operation + " failed: " + reason;
        LOG.warn(message);
        return new VideoLibraryUnavailableException(message);
    }

    /** Internal signal for "the library has no such video"; never leaves this class. */
    private static final class NotFoundInLibrary extends RuntimeException {
        private static final long serialVersionUID = 1L;

        NotFoundInLibrary() {
            super(null, null, false, false);
        }
    }

    private record RenameRequest(String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BunnyPage(long totalItems, List<BunnyVideo> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BunnyVideo(
        String guid, String title, Integer status, Integer length, String thumbnailUrl,
        @JsonProperty("hasMP4Fallback") Boolean hasMp4Fallback, String availableResolutions
    ) {

        LibraryVideo toVideo() {
            return new LibraryVideo(
                guid,
                Objects.requireNonNullElse(title, ""),
                statusOf(status),
                length == null ? 0 : length,
                thumbnailUrl,
                Boolean.TRUE.equals(hasMp4Fallback),
                resolutionsOf(availableResolutions)
            );
        }

        private static LibraryVideoStatus statusOf(Integer code) {
            LibraryVideoStatus[] known = LibraryVideoStatus.values();
            // The enum lists the library's documented codes 0..8 in order, then UNKNOWN.
            return code == null || code < 0 || code >= known.length - 1 ? LibraryVideoStatus.UNKNOWN : known[code];
        }

        private static List<String> resolutionsOf(String commaSeparated) {
            if (commaSeparated == null || commaSeparated.isBlank()) {
                return List.of();
            }
            return Arrays.stream(commaSeparated.split(","))
                .map(String::strip)
                .filter(label -> !label.isEmpty())
                .toList();
        }
    }
}
