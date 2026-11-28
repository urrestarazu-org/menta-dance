package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.ManagedVideo;
import com.menta.bff.application.dto.VideoLesson;
import com.menta.bff.application.dto.VideoListQuery;
import com.menta.bff.application.dto.VideoPage;
import com.menta.bff.application.dto.VideoStatus;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException.Code;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("VirtualVideoManagementApiAdapter")
class VirtualVideoManagementApiAdapterTest {

    private static final String VIDEOS_PATH = "/api/v1/admin/virtual/videos";
    private static final String VIDEO_ID = "3f2a9c1e-7b4d-4e6a-8c10-2d5e9f0a1b3c";
    private static final String VIDEO_PATH = VIDEOS_PATH + "/" + VIDEO_ID;
    private static final String TOKEN = "access-token-abc";
    private static final String SECRET = "secret-detail";
    private static final VideoListQuery QUERY = new VideoListQuery(null, 0, 20);

    private static final String LESSON_JSON =
            """
            {"courseId": "c-1", "courseTitle": "Tango", "moduleId": "m-1", "moduleTitle": "Base",
             "lessonId": "l-1", "lessonTitle": "Abrazo"}
            """;
    private static final String VIDEO_JSON =
            """
            {"videoId": "3f2a9c1e-7b4d-4e6a-8c10-2d5e9f0a1b3c", "title": "Abrazo", "status": "FINISHED",
             "durationSeconds": 65, "thumbnailUrl": "https://cdn.example/t.jpg", "hasMp4Fallback": true,
             "availableResolutions": ["360p", "720p"], "missingInLibrary": false, "lessons": [%s]}
            """.formatted(LESSON_JSON);
    private static final String MISSING_JSON =
            """
            {"videoId": "gone-1", "title": null, "status": null, "durationSeconds": null, "thumbnailUrl": null,
             "hasMp4Fallback": false, "availableResolutions": [], "missingInLibrary": true, "lessons": [%s]}
            """.formatted(LESSON_JSON);
    private static final String PAGE_JSON = "{\"items\": [" + VIDEO_JSON + ", " + MISSING_JSON
            + "], \"page\": 0, \"size\": 20, \"totalElements\": 2, \"totalPages\": 1}";

    private static final VideoLesson LESSON = new VideoLesson("c-1", "Tango", "m-1", "Base", "l-1", "Abrazo");

    private VirtualVideoManagementApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        adapter = adapterWith(wireMock, Duration.ofSeconds(10));
    }

    private static VirtualVideoManagementApiAdapter adapterWith(WireMockRuntimeInfo wireMock, Duration timeout) {
        VirtualApiProperties properties = new VirtualApiProperties();
        properties.setBaseUrl(wireMock.getHttpBaseUrl());
        properties.setTimeout(timeout);
        return new VirtualVideoManagementApiAdapter(
                WebClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build(), properties);
    }

    private static void stub(String method, String path, int status, String body) {
        stubFor(request(method, urlPathEqualTo(path)).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }

    private static String problem(String code) {
        return "{\"status\":400,\"code\":\"%s\",\"detail\":\"%s\"}".formatted(code, SECRET);
    }

    /** Removes the field from the first object of the JSON, as text. */
    private static String without(String json, String field) {
        return json.replaceFirst("\"" + field + "\":\\s*(\"[^\"]*\"|true|false|null|\\d+|\\[[^\\]]*\\]),?", "");
    }

    // ---- list ----

    @Test
    @DisplayName("listVideos sends GET with the Bearer, the page, the size and no search, and maps the page")
    void listVideos() {
        stub("GET", VIDEOS_PATH, 200, PAGE_JSON);

        VideoPage page = adapter.listVideos(TOKEN, QUERY);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.items()).containsExactly(
                new ManagedVideo(VIDEO_ID, "Abrazo", VideoStatus.FINISHED, 65, "https://cdn.example/t.jpg", true,
                        List.of("360p", "720p"), false, List.of(LESSON)),
                new ManagedVideo("gone-1", null, VideoStatus.UNKNOWN, null, null, false, List.of(), true,
                        List.of(LESSON)));
        verify(getRequestedFor(urlEqualTo(VIDEOS_PATH + "?page=0&size=20"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    @DisplayName("listVideos sends the search encoded as one value, whatever it holds")
    void listVideosEncodesTheSearch() {
        stub("GET", VIDEOS_PATH, 200, PAGE_JSON);

        adapter.listVideos(TOKEN, new VideoListQuery("a&b=c {x} ñ#", 3, 20));

        verify(getRequestedFor(urlPathEqualTo(VIDEOS_PATH)).withQueryParam("search", equalTo("a&b=c {x} ñ#"))
                .withQueryParam("page", equalTo("3")).withQueryParam("size", equalTo("20")));
    }

    @Test
    @DisplayName("a status this BFF does not know maps to UNKNOWN, and resolutions may be absent")
    void unknownStatusAndAbsentResolutions() {
        stub("GET", VIDEOS_PATH, 200, "{\"items\": [" + without(VIDEO_JSON, "availableResolutions")
                .replace("FINISHED", "SOMETHING_NEW") + "], \"page\": 0, \"size\": 20, \"totalElements\": 1, "
                + "\"totalPages\": 1}");

        ManagedVideo video = adapter.listVideos(TOKEN, QUERY).items().get(0);

        assertThat(video.status()).isEqualTo(VideoStatus.UNKNOWN);
        assertThat(video.availableResolutions()).isEmpty();
    }

    @Test
    @DisplayName("an empty page maps to no items and its totals")
    void emptyPage() {
        stub("GET", VIDEOS_PATH, 200, "{\"items\": [], \"page\": 4, \"size\": 20, \"totalElements\": 0, "
                + "\"totalPages\": 0}");

        VideoPage page = adapter.listVideos(TOKEN, QUERY);

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(4);
    }

    static Stream<Arguments> incompleteLists() {
        String page = "{\"items\": [%s], \"page\": 0, \"size\": 20, \"totalElements\": 1, \"totalPages\": 1}";
        return Stream.of(
                Arguments.of("no items", "{\"page\": 0, \"size\": 20, \"totalElements\": 0, \"totalPages\": 0}"),
                Arguments.of("no page", "{\"items\": [], \"size\": 20, \"totalElements\": 0, \"totalPages\": 0}"),
                Arguments.of("no size", "{\"items\": [], \"page\": 0, \"totalElements\": 0, \"totalPages\": 0}"),
                Arguments.of("no total", "{\"items\": [], \"page\": 0, \"size\": 20, \"totalPages\": 0}"),
                Arguments.of("no pages", "{\"items\": [], \"page\": 0, \"size\": 20, \"totalElements\": 0}"),
                Arguments.of("null item", page.formatted("null")),
                Arguments.of("no videoId", page.formatted(without(VIDEO_JSON, "videoId"))),
                Arguments.of("no mp4 flag", page.formatted(without(VIDEO_JSON, "hasMp4Fallback"))),
                Arguments.of("no missing flag", page.formatted(without(VIDEO_JSON, "missingInLibrary"))),
                Arguments.of("no lessons", page.formatted(without(VIDEO_JSON, "lessons").replace(",\n", ""))),
                Arguments.of("lesson without a title", page.formatted(VIDEO_JSON.replace(
                        "\"lessonTitle\": \"Abrazo\"", "\"lessonTitle\": null"))),
                Arguments.of("null lesson", page.formatted(VIDEO_JSON.replace(LESSON_JSON.strip(), "null"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incompleteLists")
    @DisplayName("an answer that lacks something the screen needs is unavailable")
    void incompleteListIsUnavailable(String name, String body) {
        stub("GET", VIDEOS_PATH, 200, body);

        assertThatThrownBy(() -> adapter.listVideos(TOKEN, QUERY)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("an answer that is not JSON, or is empty, is unavailable")
    void unreadableListIsUnavailable() {
        stub("GET", VIDEOS_PATH, 200, "<html>");
        assertThatThrownBy(() -> adapter.listVideos(TOKEN, QUERY)).isInstanceOf(ServiceUnavailableException.class);

        stub("GET", VIDEOS_PATH, 200, "");
        assertThatThrownBy(() -> adapter.listVideos(TOKEN, QUERY)).isInstanceOf(ServiceUnavailableException.class);
    }

    // ---- rename ----

    @Test
    @DisplayName("renameVideo sends PUT with the title only and the Bearer, and does not read the answer")
    void renameVideo() {
        stub("PUT", VIDEO_PATH, 200, "not even json");

        adapter.renameVideo(TOKEN, VIDEO_ID, "Nuevo \"título\"");

        verify(putRequestedFor(urlPathEqualTo(VIDEO_PATH)).withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withRequestBody(equalToJson("{\"title\": \"Nuevo \\\"título\\\"\"}", false, false)));
    }

    @Test
    @DisplayName("only a 200 renames: any other 2xx is unavailable")
    void renameNeedsExactly200() {
        stub("PUT", VIDEO_PATH, 204, "");

        assertThatThrownBy(() -> adapter.renameVideo(TOKEN, VIDEO_ID, "Título"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    // ---- delete ----

    @Test
    @DisplayName("deleteVideo sends DELETE with the Bearer and needs exactly a 204")
    void deleteVideo() {
        stub("DELETE", VIDEO_PATH, 204, "");

        adapter.deleteVideo(TOKEN, VIDEO_ID);

        verify(deleteRequestedFor(urlPathEqualTo(VIDEO_PATH)).withHeader("Authorization", equalTo("Bearer " + TOKEN)));
        stub("DELETE", VIDEO_PATH, 200, "");
        assertThatThrownBy(() -> adapter.deleteVideo(TOKEN, VIDEO_ID))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("the 409 VIDEO_IN_USE carries the lessons that use the video, and drops the incomplete ones")
    void deleteInUse() {
        stub("DELETE", VIDEO_PATH, 409, "{\"status\":409,\"code\":\"VIDEO_IN_USE\",\"detail\":\"" + SECRET
                + "\",\"lessons\":[" + LESSON_JSON + ",{\"lessonId\":\"l-2\"}, null]}");

        assertThatThrownBy(() -> adapter.deleteVideo(TOKEN, VIDEO_ID)).isInstanceOfSatisfying(
                VideoManagementException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(Code.VIDEO_IN_USE);
                    assertThat(e.getLessons()).containsExactly(LESSON);
                    assertThat(e.getMessage()).doesNotContain(SECRET).doesNotContain(VIDEO_ID);
                });
    }

    @Test
    @DisplayName("a VIDEO_IN_USE without the lessons still says so, with none")
    void deleteInUseWithoutLessons() {
        stub("DELETE", VIDEO_PATH, 409, problem("VIDEO_IN_USE"));

        assertThatThrownBy(() -> adapter.deleteVideo(TOKEN, VIDEO_ID)).isInstanceOfSatisfying(
                VideoManagementException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(Code.VIDEO_IN_USE);
                    assertThat(e.getLessons()).isEmpty();
                });
    }

    // ---- every call ----

    private record Operation(String name, String method, String path, int okStatus, String okBody,
            Consumer<VirtualVideoManagementApiAdapter> call) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static List<Operation> operations() {
        return List.of(
                new Operation("listVideos", "GET", VIDEOS_PATH, 200, PAGE_JSON, a -> a.listVideos(TOKEN, QUERY)),
                new Operation("renameVideo", "PUT", VIDEO_PATH, 200, "{}",
                        a -> a.renameVideo(TOKEN, VIDEO_ID, "Título")),
                new Operation("deleteVideo", "DELETE", VIDEO_PATH, 204, "", a -> a.deleteVideo(TOKEN, VIDEO_ID)));
    }

    static Stream<Arguments> refusals() {
        return operations().stream().flatMap(operation -> Stream.of(
                Arguments.of(operation, 400, "INVALID_REQUEST", Code.INVALID_REQUEST),
                Arguments.of(operation, 403, "ACCESS_DENIED", Code.ACCESS_DENIED),
                Arguments.of(operation, 403, "VIDEO_NOT_OWNED", Code.VIDEO_NOT_OWNED),
                Arguments.of(operation, 404, "VIDEO_NOT_FOUND", Code.VIDEO_NOT_FOUND),
                Arguments.of(operation, 409, "VIDEO_IN_USE", Code.VIDEO_IN_USE),
                Arguments.of(operation, 503, "VIDEO_LIBRARY_UNAVAILABLE", Code.VIDEO_LIBRARY_UNAVAILABLE)));
    }

    @ParameterizedTest(name = "{0}: {1} {2}")
    @MethodSource("refusals")
    @DisplayName("a refusal with a known code is typed by its code, and nothing of its body is kept")
    void refusalIsTyped(Operation operation, int status, String code, Code expected) {
        stub(operation.method(), operation.path(), status, problem(code));

        assertThatThrownBy(() -> operation.call().accept(adapter)).isInstanceOfSatisfying(
                VideoManagementException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(expected);
                    assertThat(e.getMessage()).doesNotContain(SECRET);
                });
    }

    static Stream<Arguments> unavailables() {
        return operations().stream().flatMap(operation -> Stream.of(
                Arguments.of(operation, 401, problem("ACCESS_DENIED")),
                Arguments.of(operation, 400, problem("SOMETHING_ELSE")),
                Arguments.of(operation, 400, "{\"status\":400}"),
                Arguments.of(operation, 404, ""),
                Arguments.of(operation, 409, "not json"),
                Arguments.of(operation, 429, ""),
                Arguments.of(operation, 500, problem("VIDEO_NOT_FOUND")),
                Arguments.of(operation, 503, ""),
                Arguments.of(operation, 503, problem("SOMETHING_ELSE"))));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("unavailables")
    @DisplayName("any other status, an unknown code or no body is unavailable, without anything of the body")
    void otherOutcomesAreUnavailable(Operation operation, int status, String body) {
        stub(operation.method(), operation.path(), status, body);

        assertThatThrownBy(() -> operation.call().accept(adapter)).isInstanceOf(ServiceUnavailableException.class)
                .hasMessageNotContaining(SECRET).hasMessageNotContaining(TOKEN);
    }

    @ParameterizedTest
    @MethodSource("operationsOnly")
    @DisplayName("a dropped connection is unavailable")
    void connectionFault(Operation operation) {
        stubFor(request(operation.method(), urlPathEqualTo(operation.path()))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> operation.call().accept(adapter)).isInstanceOf(ServiceUnavailableException.class)
                .hasMessageNotContaining(TOKEN);
    }

    static Stream<Operation> operationsOnly() {
        return operations().stream();
    }

    @ParameterizedTest
    @MethodSource("operationsOnly")
    @DisplayName("a slow answer times out as unavailable")
    void timeout(Operation operation, WireMockRuntimeInfo wireMock) {
        stubFor(request(operation.method(), urlPathEqualTo(operation.path())).willReturn(aResponse()
                .withStatus(operation.okStatus()).withFixedDelay(1500).withBody(operation.okBody())));
        VirtualVideoManagementApiAdapter quick = adapterWith(wireMock, Duration.ofMillis(300));

        assertThatThrownBy(() -> operation.call().accept(quick)).isInstanceOf(ServiceUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    @DisplayName("nothing is sent but to the videos endpoints")
    void onlyTheVideosEndpoints(String method) {
        operations().stream().filter(operation -> operation.method().equals(method)).forEach(operation -> {
            stub(operation.method(), operation.path(), operation.okStatus(), operation.okBody());
            operation.call().accept(adapter);
        });

        verify(anyRequestedFor(urlPathMatching(VIDEOS_PATH + "(/[^/]+)?")));
        verify(1, anyRequestedFor(urlPathMatching(".*")));
    }
}
