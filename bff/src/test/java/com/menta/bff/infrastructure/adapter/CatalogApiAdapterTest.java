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
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.domain.model.CourseModality;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.time.Duration;
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
}
