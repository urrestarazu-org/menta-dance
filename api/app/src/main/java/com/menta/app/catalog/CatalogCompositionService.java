package com.menta.app.catalog;

import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.dto.PhysicalSessionAvailability;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.virtual.application.dto.VirtualCourseDetailView;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Composes the public catalog (docs/07-CATALOG-API.md, #95, #47, #107) from
 * Physical's and Virtual's already-merged read ports (#40/#46) — no shared
 * table, FK, JOIN or internal HTTP between modules, per ADR-0037.
 */
@Component
public class CatalogCompositionService {

    /**
     * A single unbounded page is enough today — this is a dance academy
     * catalog, not an e-commerce scale problem (ADR-0037). If real volume
     * ever justifies it, paginating the public endpoint is a change local to
     * this class: neither port's cursor contract needs to change.
     */
    private static final int LIST_PAGE_SIZE = 500;

    /** Hard cap on the sessions of a physical detail; the earliest ones are kept (#107, D7). */
    private static final int MAX_DETAIL_SESSIONS = 100;

    /** Upper bound of {@code catalog.physical.sessions.window-days}, to bound a public read. */
    private static final int MAX_SESSION_WINDOW_DAYS = 90;

    private final PhysicalCourseAvailabilityPort physicalPort;
    private final VirtualCourseCatalogPort virtualPort;
    private final Clock clock;
    private final Duration sessionWindow;

    /**
     * Wires the ports and the clock of the composition.
     *
     * @param sessionWindowDays forward window of the physical detail's
     *     sessions, from {@code 1} to {@code 90} days; anything else stops the
     *     application at startup.
     */
    public CatalogCompositionService(
        PhysicalCourseAvailabilityPort physicalPort,
        VirtualCourseCatalogPort virtualPort,
        Clock clock,
        @Value("${catalog.physical.sessions.window-days:30}") int sessionWindowDays
    ) {
        if (sessionWindowDays < 1 || sessionWindowDays > MAX_SESSION_WINDOW_DAYS) {
            throw new IllegalArgumentException(
                "catalog.physical.sessions.window-days must be between 1 and "
                    + MAX_SESSION_WINDOW_DAYS + " but was " + sessionWindowDays
            );
        }
        this.physicalPort = physicalPort;
        this.virtualPort = virtualPort;
        this.clock = clock;
        this.sessionWindow = Duration.ofDays(sessionWindowDays);
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
     * Public detail composition (#47, #107): virtual first, physical second.
     *
     * <p>A published virtual course answers the unchanged virtual detail and
     * never consults the physical module, so a virtual failure stays a 503
     * without a physical call. Only when virtual finds nothing is the id
     * looked up as an active physical course; its upcoming sessions then come
     * from {@code [now, now + window)}, ascending as the port guarantees,
     * truncated to the earliest {@value #MAX_DETAIL_SESSIONS}.</p>
     *
     * <p>Missing, inactive and malformed ids collapse into
     * {@link CourseNotFoundException} on both branches (the same
     * non-enumeration discipline as #47 scenarios 3 and 4). The physical
     * course lookup and the sessions lookup are all-or-nothing: either failing
     * is a {@link CatalogUpstreamException}, never a partial detail.</p>
     *
     * <p>Callers must not assume any instructor block is present on the
     * virtual detail — only {@code professorId} is persisted on
     * {@code VirtualCourse}.</p>
     */
    public CatalogCourseDetail getCourseDetail(String courseId) {
        Optional<VirtualCourseDetailView> virtual =
            lookup("virtual", () -> virtualPort.findPublishedDetailById(courseId));
        if (virtual.isPresent()) {
            return CatalogCourseDetailResponse.fromVirtual(virtual.get());
        }
        PhysicalCourseSummary course =
            lookup("physical", () -> physicalPort.findActiveById(courseId))
                .orElseThrow(CourseNotFoundException::new);
        Instant from = clock.instant();
        Instant to = from.plus(sessionWindow);
        List<PhysicalSessionAvailability> sessions =
            callPort("physical", () -> physicalPort.listSessions(courseId, from, to));
        return CatalogPhysicalCourseDetailResponse.from(
            course, sessions.stream().limit(MAX_DETAIL_SESSIONS).toList()
        );
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
