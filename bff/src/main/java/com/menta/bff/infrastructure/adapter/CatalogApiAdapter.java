package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.CatalogCourse;
import com.menta.bff.application.dto.PhysicalCourseDetail;
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.domain.model.CourseModality;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient adapter for the upstream public catalog list.
 *
 * <p>Implements {@link CatalogApiClient} against api:app's {@code GET
 * /api/v1/catalog/courses} using the same WebClient and properties as {@link
 * VirtualApiAdapter} (same upstream, same base URL). The call is anonymous by
 * construction: no token is ever attached. Every failure — transport, timeout,
 * non-2xx, unusable body — collapses into {@link ServiceUnavailableException}
 * because the home page degrades identically for all of them, and the upstream
 * body is never propagated.
 * </p>
 *
 * <p>Uses an explicit constructor because {@code @Qualifier("virtualApiWebClient")}
 * cannot be carried by Lombok's {@code @RequiredArgsConstructor}.
 * </p>
 */
@Slf4j
@Component
public class CatalogApiAdapter implements CatalogApiClient {

    private static final String CATALOG_ENDPOINT = "/api/v1/catalog/courses";
    private static final String COURSE_ENDPOINT = "/api/v1/catalog/courses/{courseId}";
    private static final int NOT_FOUND = 404;

    private final WebClient webClient;
    private final VirtualApiProperties virtualApiProperties;

    public CatalogApiAdapter(
            @Qualifier("virtualApiWebClient") WebClient webClient,
            VirtualApiProperties virtualApiProperties) {
        this.webClient = webClient;
        this.virtualApiProperties = virtualApiProperties;
    }

    @Override
    public List<CatalogCourse> listCourses() {
        log.debug("Calling Catalog API course list endpoint");

        CatalogListWireResponse wire;
        try {
            wire = webClient.get()
                    .uri(CATALOG_ENDPOINT)
                    .retrieve()
                    .bodyToMono(CatalogListWireResponse.class)
                    .timeout(virtualApiProperties.getTimeout())
                    .block();
        } catch (Exception e) {
            log.error("Catalog API course list call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Catalog API unavailable: " + e.getMessage(), e);
        }

        if (wire == null || wire.courses() == null) {
            throw new ServiceUnavailableException("Catalog API returned an unusable response body");
        }
        return wire.courses().stream()
                .map(CatalogCourseWire::toCatalogCourse)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public PhysicalCourseDetail getPhysicalCourse(String courseId) {
        log.debug("Calling Catalog API physical course detail endpoint");

        PhysicalDetailWire wire;
        try {
            wire = webClient.get()
                    .uri(COURSE_ENDPOINT, courseId)
                    .retrieve()
                    .onStatus(status -> status.value() == NOT_FOUND,
                            response -> Mono.error(new NotFoundException("Catalog course not found")))
                    .bodyToMono(PhysicalDetailWire.class)
                    .timeout(virtualApiProperties.getTimeout())
                    .block();
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Catalog API course detail call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Catalog API unavailable: " + e.getMessage(), e);
        }

        if (wire == null) {
            throw new ServiceUnavailableException("Catalog API returned an unusable response body");
        }
        // The upstream answers either modality for any id; only a physical shape is a class.
        if (wire.physical() == null || !"PHYSICAL".equals(wire.modality())) {
            throw new NotFoundException("Catalog course is not a physical course");
        }
        return wire.toDetail();
    }

    /** Wire shape for the {@code {"courses": [...]}} envelope. */
    private record CatalogListWireResponse(List<CatalogCourseWire> courses) {
    }

    /**
     * Wire shape for one entry. The modality-specific {@code virtual} block is
     * left unbound (the home page needs nothing from it); only the professor
     * schedule fields are read from the {@code physical} block.
     */
    private record CatalogCourseWire(
            String courseId,
            String modality,
            String title,
            String level,
            PhysicalBlockWire physical) {

        /** Empty when id or modality is missing/unknown, so one odd entry cannot break the page. */
        private Optional<CatalogCourse> toCatalogCourse() {
            if (courseId == null || courseId.isBlank()) {
                return Optional.empty();
            }
            return CourseModality.fromFilter(modality)
                    .map(parsed -> new CatalogCourse(
                            courseId, parsed, title, level,
                            physical == null ? null : physical.professorName(),
                            physical == null ? null : physical.dayOfWeek(),
                            physical == null ? null : physical.startTime()));
        }
    }

    private record PhysicalBlockWire(String professorName, String dayOfWeek, String startTime) {
    }

    /** Wire shape of the physical course detail. */
    private record PhysicalDetailWire(
            String courseId, String modality, String title, String level, PhysicalDetailBlockWire physical) {

        private PhysicalCourseDetail toDetail() {
            List<PhysicalCourseDetail.Session> sessions = physical.sessions() == null
                    ? List.of()
                    : physical.sessions().stream()
                            .filter(s -> s.scheduledAt() != null && s.availableSpots() != null)
                            .map(s -> new PhysicalCourseDetail.Session(
                                    s.sessionId(), s.scheduledAt(), Objects.requireNonNullElse(s.capacity(), 0),
                                    s.availableSpots()))
                            .toList();
            return new PhysicalCourseDetail(
                    courseId, title, level, physical.professorName(), physical.dayOfWeek(),
                    physical.startTime(), Objects.requireNonNullElse(physical.capacity(), 0), sessions);
        }
    }

    private record PhysicalDetailBlockWire(
            String professorName, String dayOfWeek, String startTime, Integer capacity,
            List<SessionWire> sessions) {
    }

    private record SessionWire(String sessionId, Instant scheduledAt, Integer capacity, Integer availableSpots) {
    }
}
