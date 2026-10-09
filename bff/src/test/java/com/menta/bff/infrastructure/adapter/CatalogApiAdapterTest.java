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
import com.menta.bff.application.dto.CatalogCourse;
import com.menta.bff.application.dto.PhysicalCourseDetail;
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.domain.model.CourseModality;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

@WireMockTest
@DisplayName("CatalogApiAdapter")
class CatalogApiAdapterTest {

    private static final String CATALOG_PATH = "/api/v1/catalog/courses";

    private static final String MIXED_BODY =
            """
            {"courses": [
              {"courseId": "v-1", "modality": "VIRTUAL", "title": "Ballet Básico", "level": "BEGINNER",
               "physical": null,
               "virtual": {"shortDescription": "x", "imageUrl": "u", "category": "ballet", "premium": true,
                           "moduleCount": 1, "lessonCount": 2, "totalDurationMinutes": 30}},
              {"courseId": "p-1", "modality": "PHYSICAL", "title": "Salsa en vivo", "level": "ADVANCED",
               "physical": {"professorName": "María García", "dayOfWeek": "MONDAY", "startTime": "18:00",
                            "capacity": 12},
               "virtual": null}
            ]}
            """;

    private String baseUrl;
    private CatalogApiAdapter adapter;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        baseUrl = wmRuntimeInfo.getHttpBaseUrl();
        adapter = adapterWithTimeout(Duration.ofSeconds(5));
    }

    private CatalogApiAdapter adapterWithTimeout(Duration timeout) {
        VirtualApiProperties properties = new VirtualApiProperties();
        properties.setBaseUrl(baseUrl);
        properties.setTimeout(timeout);
        return new CatalogApiAdapter(WebClient.builder().baseUrl(baseUrl).build(), properties);
    }

    private void stubCatalog(int status, String body) {
        stubFor(get(urlEqualTo(CATALOG_PATH))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Test
    @DisplayName("maps a mixed virtual and physical catalog, ignoring modality blocks it does not need")
    void mapsMixedCatalog() {
        stubCatalog(200, MIXED_BODY);

        List<CatalogCourse> result = adapter.listCourses();

        assertThat(result).containsExactly(
                new CatalogCourse("v-1", CourseModality.VIRTUAL, "Ballet Básico", "BEGINNER", null, null, null),
                new CatalogCourse("p-1", CourseModality.PHYSICAL, "Salsa en vivo", "ADVANCED",
                        "María García", "MONDAY", "18:00"));
    }

    @Test
    @DisplayName("returns an empty list for an empty catalog")
    void returnsEmptyListForEmptyCatalog() {
        stubCatalog(200, "{\"courses\": []}");

        assertThat(adapter.listCourses()).isEmpty();
    }

    @Test
    @DisplayName("skips entries without id or with an unknown or missing modality instead of failing the page")
    void skipsEntriesWithUnknownModality() {
        stubCatalog(200,
                """
                {"courses": [
                  {"courseId": "x-1", "modality": "HYBRID", "title": "Futuro", "level": "BEGINNER"},
                  {"courseId": "x-2", "title": "Sin modalidad", "level": "BEGINNER"},
                  {"modality": "VIRTUAL", "title": "Sin id", "level": "BEGINNER"},
                  {"courseId": "v-1", "modality": "VIRTUAL", "title": "Ballet", "level": "BEGINNER"}
                ]}
                """);

        assertThat(adapter.listCourses()).extracting(CatalogCourse::courseId).containsExactly("v-1");
    }

    @Test
    @DisplayName("never sends an Authorization header")
    void neverSendsAuthorizationHeader() {
        stubCatalog(200, MIXED_BODY);

        adapter.listCourses();

        verify(getRequestedFor(urlEqualTo(CATALOG_PATH)).withoutHeader("Authorization"));
    }

    @Test
    @DisplayName("throws ServiceUnavailableException on a 5xx response without leaking the body")
    void throwsOnServerError() {
        stubCatalog(503, "{\"detail\":\"secret upstream detail\"}");

        assertThatThrownBy(() -> adapter.listCourses())
                .isInstanceOf(CatalogApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("throws ServiceUnavailableException on 429 and on 404")
    void throwsOnRateLimitAndNotFound() {
        stubCatalog(429, "{}");
        assertThatThrownBy(() -> adapter.listCourses())
                .isInstanceOf(CatalogApiClient.ServiceUnavailableException.class);

        stubCatalog(404, "{}");
        assertThatThrownBy(() -> adapter.listCourses())
                .isInstanceOf(CatalogApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("throws ServiceUnavailableException when the body has no courses envelope")
    void throwsWhenEnvelopeMissing() {
        stubCatalog(200, "{}");

        assertThatThrownBy(() -> adapter.listCourses())
                .isInstanceOf(CatalogApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("throws ServiceUnavailableException when the body is empty")
    void throwsWhenBodyEmpty() {
        stubFor(get(urlEqualTo(CATALOG_PATH)).willReturn(aResponse().withStatus(200)));

        assertThatThrownBy(() -> adapter.listCourses())
                .isInstanceOf(CatalogApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("throws ServiceUnavailableException when the connection is reset")
    void throwsOnConnectionFault() {
        stubFor(get(urlEqualTo(CATALOG_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> adapter.listCourses())
                .isInstanceOf(CatalogApiClient.ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("throws ServiceUnavailableException when the call times out")
    void throwsOnTimeout() {
        stubFor(get(urlEqualTo(CATALOG_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody(MIXED_BODY)));

        CatalogApiAdapter shortTimeoutAdapter = adapterWithTimeout(Duration.ofMillis(50));

        assertThatThrownBy(shortTimeoutAdapter::listCourses)
                .isInstanceOf(CatalogApiClient.ServiceUnavailableException.class);
    }

    private static final String DETAIL_PATH = "/api/v1/catalog/courses/p-1";

    private static final String PHYSICAL_DETAIL_BODY =
            """
            {"courseId": "p-1", "modality": "PHYSICAL", "title": "Salsa en vivo", "level": "ADVANCED",
             "physical": {"professorName": "María García", "dayOfWeek": "MONDAY", "startTime": "18:00",
                          "capacity": 12,
                          "sessions": [
                            {"sessionId": "s-1", "scheduledAt": "2026-10-12T21:00:00Z", "capacity": 12,
                             "availableSpots": 5},
                            {"sessionId": "s-2", "scheduledAt": "2026-10-19T21:00:00Z", "capacity": 12,
                             "availableSpots": 0}]}}
            """;

    private static final String NO_SESSIONS_BODY =
            """
            {"courseId": "p-1", "modality": "PHYSICAL", "title": "Salsa", "level": "ADVANCED",
             "physical": {"professorName": "A", "dayOfWeek": "MONDAY", "startTime": "18:00"}}
            """;

    private static final String VIRTUAL_SHAPE_BODY =
            """
            {"courseId": "p-1", "title": "Ballet", "level": "BEGINNER", "isPremium": false,
             "modules": [], "stats": {"moduleCount": 0, "lessonCount": 0, "totalDuration": "0m"}}
            """;

    private void stubDetail(int status, String body) {
        stubFor(get(urlEqualTo(DETAIL_PATH))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Test
    @DisplayName("maps a physical course detail with its sessions in upstream order")
    void mapsPhysicalDetail() {
        stubDetail(200, PHYSICAL_DETAIL_BODY);

        PhysicalCourseDetail detail = adapter.getPhysicalCourse("p-1");

        assertThat(detail.courseId()).isEqualTo("p-1");
        assertThat(detail.title()).isEqualTo("Salsa en vivo");
        assertThat(detail.level()).isEqualTo("ADVANCED");
        assertThat(detail.professorName()).isEqualTo("María García");
        assertThat(detail.dayOfWeek()).isEqualTo("MONDAY");
        assertThat(detail.startTime()).isEqualTo("18:00");
        assertThat(detail.sessions()).containsExactly(
                new PhysicalCourseDetail.Session("s-1", Instant.parse("2026-10-12T21:00:00Z"), 12, 5),
                new PhysicalCourseDetail.Session("s-2", Instant.parse("2026-10-19T21:00:00Z"), 12, 0));
        verify(getRequestedFor(urlEqualTo(DETAIL_PATH)).withoutHeader("Authorization"));
    }

    @Test
    @DisplayName("treats a missing sessions array as no sessions")
    void missingSessionsMeansNone() {
        stubDetail(200, NO_SESSIONS_BODY);

        assertThat(adapter.getPhysicalCourse("p-1").sessions()).isEmpty();
    }

    @Test
    @DisplayName("throws NotFoundException on 404 and for a virtual course shape")
    void throwsNotFound() {
        stubDetail(404, "{\"detail\":\"secret upstream detail\"}");
        assertThatThrownBy(() -> adapter.getPhysicalCourse("p-1"))
                .isInstanceOf(CatalogApiClient.NotFoundException.class)
                .hasMessageNotContaining("secret");

        stubDetail(200, VIRTUAL_SHAPE_BODY);
        assertThatThrownBy(() -> adapter.getPhysicalCourse("p-1"))
                .isInstanceOf(CatalogApiClient.NotFoundException.class);
    }

    @Test
    @DisplayName("throws ServiceUnavailableException on 503, an empty body and a connection fault")
    void detailThrowsUnavailable() {
        stubDetail(503, "{\"detail\":\"secret upstream detail\"}");
        assertThatThrownBy(() -> adapter.getPhysicalCourse("p-1"))
                .isInstanceOf(CatalogApiClient.ServiceUnavailableException.class);

        stubFor(get(urlEqualTo(DETAIL_PATH)).willReturn(aResponse().withStatus(200)));
        assertThatThrownBy(() -> adapter.getPhysicalCourse("p-1"))
                .isInstanceOf(CatalogApiClient.ServiceUnavailableException.class);

        stubFor(get(urlEqualTo(DETAIL_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertThatThrownBy(() -> adapter.getPhysicalCourse("p-1"))
                .isInstanceOf(CatalogApiClient.ServiceUnavailableException.class);
    }
}
