package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.PhysicalClassDetail;
import com.menta.bff.application.dto.PhysicalClassSummary;
import com.menta.bff.application.port.out.PhysicalCatalogApiClient;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient adapter for the public catalog's in-person courses.
 *
 * <p>Reuses the {@code virtualApiWebClient} bean, which already targets the
 * api:app catalog base URL ({@code menta.api.base-url}); a second client for
 * the same upstream would only duplicate configuration. Never sends an
 * {@code Authorization} header.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer. Uses an explicit
 * constructor because {@code @Qualifier} cannot be carried by Lombok's
 * {@code @RequiredArgsConstructor}, mirroring {@code VirtualApiAdapter}.
 * </p>
 */
@Slf4j
@Component
public class PhysicalCatalogApiAdapter implements PhysicalCatalogApiClient {

    private static final String COURSES_ENDPOINT = "/api/v1/catalog/courses";
    private static final String COURSE_DETAIL_ENDPOINT = "/api/v1/catalog/courses/{courseId}";
    private static final String PHYSICAL_MODALITY = "PHYSICAL";

    private final WebClient webClient;
    private final VirtualApiProperties virtualApiProperties;

    public PhysicalCatalogApiAdapter(
            @Qualifier("virtualApiWebClient") WebClient webClient,
            VirtualApiProperties virtualApiProperties) {
        this.webClient = webClient;
        this.virtualApiProperties = virtualApiProperties;
    }

    @Override
    public List<PhysicalClassSummary> listClasses() {
        log.debug("Calling catalog list endpoint for physical courses");

        CourseListWire wire = call(webClient.get().uri(COURSES_ENDPOINT), CourseListWire.class);
        if (wire.courses() == null) {
            throw new ServiceUnavailableException("Catalog returned no courses array");
        }
        return wire.courses().stream()
                .filter(course -> PHYSICAL_MODALITY.equals(course.modality()) && course.physical() != null)
                .map(CourseWire::toSummary)
                .toList();
    }

    @Override
    public PhysicalClassDetail getClassDetail(String courseId) {
        Objects.requireNonNull(courseId, "courseId cannot be null");

        log.debug("Calling catalog detail endpoint for a physical course");

        DetailWire wire = call(webClient.get().uri(COURSE_DETAIL_ENDPOINT, courseId), DetailWire.class);
        // A virtual id answers 200 with the virtual shape (no modality, no
        // physical block): for this port that course simply does not exist.
        if (!PHYSICAL_MODALITY.equals(wire.modality()) || wire.physical() == null) {
            throw new NotFoundException("Course is not an in-person course");
        }
        return wire.toDetail();
    }

    private <T> T call(WebClient.RequestHeadersSpec<?> request, Class<T> type) {
        try {
            T body = request
                    .exchangeToMono(response -> handle(response, type))
                    .timeout(virtualApiProperties.getTimeout())
                    .block();
            if (body == null) {
                throw new ServiceUnavailableException("Catalog returned an empty body");
            }
            return body;
        } catch (NotFoundException | ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Catalog call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Catalog unavailable: " + e.getMessage(), e);
        }
    }

    private static <T> Mono<T> handle(ClientResponse response, Class<T> type) {
        HttpStatusCode status = response.statusCode();
        if (status.is2xxSuccessful()) {
            return response.bodyToMono(type);
        }
        RuntimeException error = status.value() == 404
                ? new NotFoundException("Course not found: " + status)
                : new ServiceUnavailableException("Catalog call failed: " + status);
        // The problem-detail body is never read: nothing upstream reaches the page.
        return response.releaseBody().then(Mono.error(error));
    }

    private record CourseListWire(List<CourseWire> courses) {
    }

    private record CourseWire(String courseId, String modality, String title, String level, PhysicalWire physical) {

        private PhysicalClassSummary toSummary() {
            return new PhysicalClassSummary(
                    courseId, title, level, physical.professorName(), physical.dayOfWeek(),
                    physical.startTime(), physical.capacity());
        }
    }

    private record PhysicalWire(
            String professorName, String dayOfWeek, String startTime, int capacity, List<SessionWire> sessions) {
    }

    private record SessionWire(String sessionId, Instant scheduledAt, int capacity, int availableSpots) {
    }

    private record DetailWire(String courseId, String modality, String title, String level, PhysicalWire physical) {

        private PhysicalClassDetail toDetail() {
            List<PhysicalClassDetail.Session> sessions = physical.sessions() == null
                    ? List.of()
                    : physical.sessions().stream()
                            .map(session -> new PhysicalClassDetail.Session(
                                    session.sessionId(), session.scheduledAt(), session.capacity(),
                                    Math.max(0, session.availableSpots())))
                            .toList();
            return new PhysicalClassDetail(
                    courseId, title, level, physical.professorName(), physical.dayOfWeek(),
                    physical.startTime(), physical.capacity(), sessions);
        }
    }
}
