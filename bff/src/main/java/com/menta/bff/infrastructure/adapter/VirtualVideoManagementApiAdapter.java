package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.ManagedVideo;
import com.menta.bff.application.dto.VideoLesson;
import com.menta.bff.application.dto.VideoListQuery;
import com.menta.bff.application.dto.VideoPage;
import com.menta.bff.application.dto.VideoStatus;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

/**
 * WebClient adapter for the management of the videos of the library (#842): the calls of the Virtual API under
 * {@code /api/v1/admin/virtual/videos}, as {@code virtual-v1.yaml} describes them. It never talks to Bunny.
 *
 * <p>The token only ever travels in the {@code Authorization} header; it, identifiers, titles, search texts and
 * response bodies never reach a log or an exception message. A refusal with one of the expected codes (400, 403, 404,
 * 409 or 503) becomes a {@link VirtualVideoManagementApiClient.VideoManagementException}; every other outcome becomes
 * a {@link VirtualVideoManagementApiClient.ServiceUnavailableException}. The only fields read from an error body are
 * the {@code code} and, for {@code VIDEO_IN_USE}, the {@code lessons}: never the {@code detail}. Uses an explicit
 * constructor because {@code @Qualifier} cannot be carried by Lombok's {@code @RequiredArgsConstructor}.
 * </p>
 */
@Slf4j
@Component
public class VirtualVideoManagementApiAdapter implements VirtualVideoManagementApiClient {

    private static final String VIDEOS_ENDPOINT = "/api/v1/admin/virtual/videos";
    private static final String VIDEO_ENDPOINT = VIDEOS_ENDPOINT + "/{videoId}";
    private static final Set<Integer> REFUSAL_STATUSES = Set.of(400, 403, 404, 409, 503);

    private final WebClient webClient;
    private final VirtualApiProperties properties;

    public VirtualVideoManagementApiAdapter(
            @Qualifier("virtualApiWebClient") WebClient webClient, VirtualApiProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public VideoPage listVideos(String accessToken, VideoListQuery query) {
        return toPage(call("list videos", webClient.get().uri(uriBuilder -> listUri(uriBuilder, query)), accessToken,
                200, PageWire.class));
    }

    @Override
    public void renameVideo(String accessToken, String videoId, String title) {
        call("rename video", webClient.put().uri(VIDEO_ENDPOINT, videoId).bodyValue(new TitleWire(title)),
                accessToken, 200, Void.class);
    }

    @Override
    public void deleteVideo(String accessToken, String videoId) {
        call("delete video", webClient.delete().uri(VIDEO_ENDPOINT, videoId), accessToken, 204, Void.class);
    }

    /** The search is a variable, so whatever it holds travels as one encoded value. */
    private static URI listUri(UriBuilder uriBuilder, VideoListQuery query) {
        Map<String, Object> variables = new HashMap<>();
        uriBuilder.path(VIDEOS_ENDPOINT).queryParam("page", "{page}").queryParam("size", "{size}");
        variables.put("page", query.page());
        variables.put("size", query.size());
        if (query.search() != null) {
            uriBuilder.queryParam("search", "{search}");
            variables.put("search", query.search());
        }
        return uriBuilder.build(variables);
    }

    /**
     * Sends the request with the bearer token and reads the answer: {@code okStatus} is the success, the expected
     * refusals are typed, everything else is unavailable. With {@code Void} as wire type the body is not read and
     * the result is {@code null}; with any other type a missing body is unavailable.
     */
    private <W> W call(String operation, WebClient.RequestHeadersSpec<?> request, String accessToken, int okStatus,
            Class<W> wireType) {
        try {
            W wire = request
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .exchangeToMono(response -> read(response, okStatus, wireType))
                    .timeout(properties.getTimeout())
                    .block();
            if (wire == null && wireType != Void.class) {
                throw unavailable(operation + " answered without a body");
            }
            return wire;
        } catch (VideoManagementException | ServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // Class name only: nothing from the request (headers, body) or the response may reach a log.
            log.warn("Virtual API {} call failed: {}", operation, e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Virtual API unavailable", e);
        }
    }

    /** Only {@code okStatus} is success; a refusal needs its code, any other error body is released unread. */
    private static <W> Mono<W> read(ClientResponse response, int okStatus, Class<W> wireType) {
        int status = response.statusCode().value();
        if (status == okStatus) {
            return response.bodyToMono(wireType);
        }
        if (REFUSAL_STATUSES.contains(status)) {
            return response.bodyToMono(ProblemWire.class)
                    .flatMap(problem -> Mono.<W>error(refusal(status, problem)))
                    .switchIfEmpty(Mono.error(unavailable("answered " + status + " without a body")));
        }
        return response.releaseBody().then(Mono.error(unavailable("answered " + status)));
    }

    private static RuntimeException refusal(int status, ProblemWire problem) {
        for (VideoManagementException.Code code : VideoManagementException.Code.values()) {
            if (code.name().equals(problem.code())) {
                return code == VideoManagementException.Code.VIDEO_IN_USE
                        ? new VideoManagementException(code, lessons(problem.lessons()))
                        : new VideoManagementException(code);
            }
        }
        return unavailable("answered " + status);
    }

    /** The lessons of a {@code VIDEO_IN_USE}: only the complete ones, a list that is missing is no lessons. */
    private static List<VideoLesson> lessons(List<LessonWire> lessons) {
        return lessons == null ? List.of() : lessons.stream().filter(VirtualVideoManagementApiAdapter::complete)
                .map(VirtualVideoManagementApiAdapter::toLesson).toList();
    }

    private static boolean complete(LessonWire lesson) {
        return lesson != null && lesson.courseId() != null && lesson.courseTitle() != null
                && lesson.moduleId() != null && lesson.moduleTitle() != null && lesson.lessonId() != null
                && lesson.lessonTitle() != null;
    }

    private static VideoLesson toLesson(LessonWire lesson) {
        return new VideoLesson(lesson.courseId(), lesson.courseTitle(), lesson.moduleId(), lesson.moduleTitle(),
                lesson.lessonId(), lesson.lessonTitle());
    }

    private static VideoPage toPage(PageWire wire) {
        if (wire.items() == null || wire.page() == null || wire.size() == null || wire.totalElements() == null
                || wire.totalPages() == null) {
            throw unavailable("videos page is incomplete");
        }
        return new VideoPage(wire.items().stream().map(VirtualVideoManagementApiAdapter::toVideo).toList(),
                wire.page(), wire.size(), wire.totalElements(), wire.totalPages());
    }

    private static ManagedVideo toVideo(VideoWire item) {
        if (item == null || item.videoId() == null || item.hasMp4Fallback() == null
                || item.missingInLibrary() == null || item.lessons() == null || !item.lessons().stream()
                        .allMatch(VirtualVideoManagementApiAdapter::complete)) {
            throw unavailable("video is incomplete");
        }
        return new ManagedVideo(item.videoId(), item.title(),
                item.status() == null ? VideoStatus.UNKNOWN : ApiEnums.parse(VideoStatus.class, item.status()),
                item.durationSeconds(), item.thumbnailUrl(), item.hasMp4Fallback(),
                item.availableResolutions() == null ? List.of()
                        : item.availableResolutions().stream().filter(Objects::nonNull).toList(),
                item.missingInLibrary(),
                item.lessons().stream().map(VirtualVideoManagementApiAdapter::toLesson).toList());
    }

    private static ServiceUnavailableException unavailable(String reason) {
        return new ServiceUnavailableException("Virtual API unavailable: " + reason);
    }

    /** {@code VirtualVideoPageResponse}. */
    private record PageWire(
            List<VideoWire> items, Integer page, Integer size, Long totalElements, Integer totalPages) {
    }

    /** {@code VirtualVideoResponse}; title, status, length and thumbnail are nullable. */
    private record VideoWire(
            String videoId, String title, String status, Integer durationSeconds, String thumbnailUrl,
            Boolean hasMp4Fallback, List<String> availableResolutions, Boolean missingInLibrary,
            List<LessonWire> lessons) {
    }

    /** {@code VirtualVideoLesson}. */
    private record LessonWire(
            String courseId, String courseTitle, String moduleId, String moduleTitle, String lessonId,
            String lessonTitle) {
    }

    /** {@code RenameVirtualVideoRequest}. */
    private record TitleWire(String title) {
    }

    /** The only fields read from an error body. */
    private record ProblemWire(String code, List<LessonWire> lessons) {
    }
}
