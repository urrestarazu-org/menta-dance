package com.menta.app.catalog;

import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.virtual.application.dto.VirtualCourseSummary;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Composes the public catalog (docs/07-CATALOG-API.md, #95) from Physical's
 * and Virtual's already-merged read ports (#40/#46) — no shared table, FK,
 * JOIN or internal HTTP between modules, per ADR-0037.
 */
@Component
public class CatalogCompositionService {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogCompositionService.class);

    /**
     * A single unbounded page is enough today — this is a dance academy
     * catalog, not an e-commerce scale problem (ADR-0037). If real volume
     * ever justifies it, paginating the public endpoint is a change local to
     * this class: neither port's cursor contract needs to change.
     */
    private static final int LIST_PAGE_SIZE = 500;

    private final PhysicalCourseAvailabilityPort physicalPort;
    private final VirtualCourseCatalogPort virtualPort;

    public CatalogCompositionService(
        PhysicalCourseAvailabilityPort physicalPort, VirtualCourseCatalogPort virtualPort
    ) {
        this.physicalPort = physicalPort;
        this.virtualPort = virtualPort;
    }

    public List<CatalogCourseResponse> listCourses() {
        List<CatalogCourseResponse> courses = new ArrayList<>();
        callPort("physical", () -> physicalPort.listCourses(null, LIST_PAGE_SIZE))
            .forEach(summary -> courses.add(CatalogCourseResponse.fromPhysical(summary)));
        callPort("virtual", () -> virtualPort.listPublished(null, LIST_PAGE_SIZE))
            .forEach(summary -> courses.add(CatalogCourseResponse.fromVirtual(summary)));
        return courses;
    }

    /**
     * ADR-0037: resolve the detail by asking both owning modules and keeping
     * whichever answers — sequential calls, not physical thread parallelism.
     * Two indexed PK lookups cost far less than the complexity of a thread
     * pool for what the ADR itself calls a "despreciable" latency trade-off.
     */
    public CatalogCourseResponse getCourse(String courseId) {
        Optional<PhysicalCourseSummary> physical = lookup("physical", () -> physicalPort.findActiveById(courseId));
        Optional<VirtualCourseSummary> virtual = lookup("virtual", () -> virtualPort.findPublishedById(courseId));

        if (physical.isPresent() && virtual.isPresent()) {
            LOG.warn(
                "courseId collision across modalities, should not happen by design: {} exists "
                    + "in both Physical and Virtual — returning Physical's result as a tie-break",
                courseId
            );
            return CatalogCourseResponse.fromPhysical(physical.get());
        }
        if (physical.isPresent()) {
            return CatalogCourseResponse.fromPhysical(physical.get());
        }
        if (virtual.isPresent()) {
            return CatalogCourseResponse.fromVirtual(virtual.get());
        }
        throw new CourseNotFoundException();
    }

    private static <T> List<T> callPort(String moduleName, Supplier<List<T>> query) {
        try {
            return query.get();
        } catch (RuntimeException upstreamFailure) {
            throw new CatalogUpstreamException(moduleName, upstreamFailure);
        }
    }

    private static <T> Optional<T> lookup(String moduleName, Supplier<Optional<T>> query) {
        try {
            return query.get();
        } catch (IllegalArgumentException malformedCourseId) {
            // Not a valid UUID for this module's CourseId — treated as "not found",
            // same non-enumeration discipline as a well-formed but missing id.
            return Optional.empty();
        } catch (RuntimeException upstreamFailure) {
            throw new CatalogUpstreamException(moduleName, upstreamFailure);
        }
    }
}
