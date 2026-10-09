package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.CatalogCourse;
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.domain.model.CourseModality;
import com.menta.bff.infrastructure.config.VirtualApiProperties;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

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

    /** Wire shape for the {@code {"courses": [...]}} envelope. */
    private record CatalogListWireResponse(List<CatalogCourseWire> courses) {
    }

    /**
     * Wire shape for one entry. The modality-specific {@code virtual} block is
     * left unbound (the home page needs nothing from it); only the professor
     * name is read from the {@code physical} block.
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
                            courseId, parsed, title, level, physical == null ? null : physical.professorName()));
        }
    }

    private record PhysicalBlockWire(String professorName) {
    }
}
