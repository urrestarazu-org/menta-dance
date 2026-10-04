package com.menta.bff.infrastructure.adapter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.menta.bff.application.dto.PhysicalClassDetail;
import com.menta.bff.application.dto.PhysicalClassSummary;
import com.menta.bff.application.port.out.PhysicalCatalogApiClient;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("PhysicalCatalogApiAdapter")
class PhysicalCatalogApiAdapterTest {

    private static final String LIST_PATH = "/api/v1/catalog/courses";
    private static final String DETAIL_PATH = "/api/v1/catalog/courses/course-p";

    private PhysicalCatalogApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        VirtualApiProperties properties = new VirtualApiProperties();
        properties.setBaseUrl(wmRuntimeInfo.getHttpBaseUrl());
        properties.setTimeout(Duration.ofSeconds(2));
        adapter = new PhysicalCatalogApiAdapter(
                WebClient.builder().baseUrl(wmRuntimeInfo.getHttpBaseUrl()).build(), properties);
    }

    private static void stubJson(String path, int status, String body) {
        stubFor(get(urlEqualTo(path)).willReturn(aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    @Test
    @DisplayName("lists only PHYSICAL courses, in upstream order, with no Authorization header")
    void listClasses_keepsOnlyPhysicalCourses() {
        stubJson(LIST_PATH, 200,
                """
                {"courses": [
                  {"courseId": "course-v", "modality": "VIRTUAL", "title": "Ballet online", "level": "BEGINNER",
                   "physical": null, "virtual": {"description": "x"}},
                  {"courseId": "course-p", "modality": "PHYSICAL", "title": "Salsa inicial", "level": "BEGINNER",
                   "physical": {"professorName": "María", "dayOfWeek": "TUESDAY", "startTime": "19:00",
                                "capacity": 20},
                   "virtual": null},
                  {"courseId": "course-q", "modality": "PHYSICAL", "title": "Tango", "level": "ADVANCED",
                   "physical": {"professorName": "Juan", "dayOfWeek": "FRIDAY", "startTime": "20:30",
                                "capacity": 12},
                   "virtual": null}
                ]}
                """);

        List<PhysicalClassSummary> classes = adapter.listClasses();

        assertThat(classes).containsExactly(
                new PhysicalClassSummary("course-p", "Salsa inicial", "BEGINNER", "María", "TUESDAY", "19:00", 20),
                new PhysicalClassSummary("course-q", "Tango", "ADVANCED", "Juan", "FRIDAY", "20:30", 12));
        verify(getRequestedFor(urlEqualTo(LIST_PATH)).withoutHeader("Authorization"));
    }

    @Test
    @DisplayName("a list body without a courses array is a service failure")
    void listClasses_withoutCoursesArray_isUnavailable() {
        stubJson(LIST_PATH, 200, "{}");

        assertThatThrownBy(() -> adapter.listClasses())
                .isInstanceOf(PhysicalCatalogApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a 503 on the list maps to ServiceUnavailableException")
    void listClasses_on503_isUnavailable() {
        stubJson(LIST_PATH, 503, "{\"title\":\"down\"}");

        assertThatThrownBy(() -> adapter.listClasses())
                .isInstanceOf(PhysicalCatalogApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("maps the physical detail and its sessions, flooring negative availability at zero")
    void getClassDetail_mapsSessions() {
        stubJson(DETAIL_PATH, 200,
                """
                {"courseId": "course-p", "modality": "PHYSICAL", "title": "Salsa inicial", "level": "BEGINNER",
                 "physical": {"professorName": "María", "dayOfWeek": "TUESDAY", "startTime": "19:00",
                              "capacity": 20,
                              "sessions": [
                                {"sessionId": "s-1", "scheduledAt": "2026-10-06T22:00:00Z", "capacity": 20,
                                 "availableSpots": 17},
                                {"sessionId": "s-2", "scheduledAt": "2026-10-13T22:00:00Z", "capacity": 20,
                                 "availableSpots": -1}
                              ]}}
                """);

        PhysicalClassDetail detail = adapter.getClassDetail("course-p");

        assertThat(detail.title()).isEqualTo("Salsa inicial");
        assertThat(detail.professorName()).isEqualTo("María");
        assertThat(detail.capacity()).isEqualTo(20);
        assertThat(detail.sessions()).containsExactly(
                new PhysicalClassDetail.Session("s-1", Instant.parse("2026-10-06T22:00:00Z"), 20, 17),
                new PhysicalClassDetail.Session("s-2", Instant.parse("2026-10-13T22:00:00Z"), 20, 0));
        verify(getRequestedFor(urlEqualTo(DETAIL_PATH)).withoutHeader("Authorization"));
    }

    @Test
    @DisplayName("a detail with no sessions array yields an empty session list")
    void getClassDetail_withoutSessions_isEmpty() {
        stubJson(DETAIL_PATH, 200,
                """
                {"courseId": "course-p", "modality": "PHYSICAL", "title": "Salsa", "level": "BEGINNER",
                 "physical": {"professorName": "María", "dayOfWeek": "TUESDAY", "startTime": "19:00",
                              "capacity": 20}}
                """);

        assertThat(adapter.getClassDetail("course-p").sessions()).isEmpty();
    }

    @Test
    @DisplayName("a virtual course body is reported as not found")
    void getClassDetail_virtualBody_isNotFound() {
        stubJson(DETAIL_PATH, 200,
                """
                {"courseId": "course-p", "title": "Ballet", "modules": [], "isPremium": true}
                """);

        assertThatThrownBy(() -> adapter.getClassDetail("course-p"))
                .isInstanceOf(PhysicalCatalogApiClient.NotFoundException.class);
    }

    @Test
    @DisplayName("a 404 maps to NotFoundException")
    void getClassDetail_on404_isNotFound() {
        stubJson(DETAIL_PATH, 404, "{\"title\":\"Course not found\"}");

        assertThatThrownBy(() -> adapter.getClassDetail("course-p"))
                .isInstanceOf(PhysicalCatalogApiClient.NotFoundException.class);
    }

    @Test
    @DisplayName("a 429 maps to ServiceUnavailableException")
    void getClassDetail_on429_isUnavailable() {
        stubJson(DETAIL_PATH, 429, "{}");

        assertThatThrownBy(() -> adapter.getClassDetail("course-p"))
                .isInstanceOf(PhysicalCatalogApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("a dropped connection maps to ServiceUnavailableException")
    void getClassDetail_onConnectionReset_isUnavailable() {
        stubFor(get(urlEqualTo(DETAIL_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> adapter.getClassDetail("course-p"))
                .isInstanceOf(PhysicalCatalogApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("an empty 200 body maps to ServiceUnavailableException")
    void getClassDetail_onEmptyBody_isUnavailable() {
        stubFor(get(urlEqualTo(DETAIL_PATH)).willReturn(aResponse().withStatus(200)));

        assertThatThrownBy(() -> adapter.getClassDetail("course-p"))
                .isInstanceOf(PhysicalCatalogApiClient.ServiceUnavailableException.class);
    }
}
