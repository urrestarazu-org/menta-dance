package com.menta.bff.infrastructure.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.test.web.servlet.MockMvc;

/** What the video management screens' integration tests share: the JSON of the Virtual API and the login. */
final class VideoApiFixtures {

    static final String API = "/api/v1/admin/virtual/videos";
    static final String VIDEO_ID = "3f2a9c1e-7b4d-4e6a-8c10-2d5e9f0a1b3c";
    static final String COURSE_ID = "6f1c0a52-8d0e-4c7e-9a43-0d6f6a1b2c01";
    static final String MODULE_ID = "0b9a7c1e-52a4-4e47-a0f8-4a2f5f3b6d02";
    static final String LESSON_ID = "9d3e2f10-77a1-4b8c-8e55-1c0f2b7a3d03";
    static final String LESSON_EDIT = "/gestion/cursos-virtuales/" + COURSE_ID + "/modulos/" + MODULE_ID
            + "/lecciones/" + LESSON_ID + "/editar";
    static final String SECRET = "secret-detail";

    private static final ObjectMapper JSON = new ObjectMapper();

    private VideoApiFixtures() {
    }

    private static String json(String value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A lesson that uses a video, as the API words it. */
    static String lesson(String courseTitle, String moduleTitle, String lessonTitle) {
        return ("{\"courseId\":\"%s\",\"courseTitle\":%s,\"moduleId\":\"%s\",\"moduleTitle\":%s,"
                + "\"lessonId\":\"%s\",\"lessonTitle\":%s}").formatted(COURSE_ID, json(courseTitle), MODULE_ID,
                json(moduleTitle), LESSON_ID, json(lessonTitle));
    }

    static String lesson() {
        return lesson("Tango desde cero", "Primeros pasos", "El abrazo");
    }

    /** A video the library has. */
    static String video(String videoId, String title, String status, Integer seconds, String thumbnail,
            boolean mp4, String... lessons) {
        return ("{\"videoId\":%s,\"title\":%s,\"status\":%s,\"durationSeconds\":%s,\"thumbnailUrl\":%s,"
                + "\"hasMp4Fallback\":%b,\"availableResolutions\":[\"360p\"],\"missingInLibrary\":false,"
                + "\"lessons\":[%s]}").formatted(json(videoId), title == null ? "null" : json(title),
                status == null ? "null" : json(status), seconds == null ? "null" : seconds.toString(),
                thumbnail == null ? "null" : json(thumbnail), mp4, String.join(",", lessons));
    }

    /** A video that a lesson uses and the library no longer has. */
    static String missing(String videoId, String... lessons) {
        return ("{\"videoId\":%s,\"title\":null,\"status\":null,\"durationSeconds\":null,\"thumbnailUrl\":null,"
                + "\"hasMp4Fallback\":false,\"availableResolutions\":[],\"missingInLibrary\":true,"
                + "\"lessons\":[%s]}").formatted(json(videoId), String.join(",", lessons));
    }

    static String page(int page, int totalPages, long total, String... videos) {
        return "{\"items\":[%s],\"page\":%d,\"size\":20,\"totalElements\":%d,\"totalPages\":%d}"
                .formatted(String.join(",", videos), page, total, totalPages);
    }

    static String problem(int status, String code) {
        return "{\"status\":%d,\"detail\":\"%s\",\"code\":\"%s\"}".formatted(status, SECRET, code);
    }

    static String inUse(List<String> lessons) {
        return "{\"status\":409,\"detail\":\"%s\",\"code\":\"VIDEO_IN_USE\",\"lessons\":[%s]}"
                .formatted(SECRET, lessons.stream().collect(Collectors.joining(",")));
    }

    static void stub(WireMockServer server, String method, String url, int status, String body) {
        server.stubFor(WireMock.request(method, WireMock.urlPathEqualTo(url)).willReturn(aResponse()
                .withStatus(status).withHeader("Content-Type", "application/json").withBody(body)));
    }

    static Cookie logIn(MockMvc mockMvc, WireMockServer server, String role) throws Exception {
        server.stubFor(WireMock.post(urlEqualTo("/api/v1/auth/login")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withHeader("X-Refresh-Token", "refresh-token-abc")
                .withBody("{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 3600}"
                        .formatted(TestAccessTokens.withRole(role)))));
        Cookie session = mockMvc.perform(post("/login").param("email", "user@example.com")
                        .param("password", "Password1").with(csrf()))
                .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }
}
