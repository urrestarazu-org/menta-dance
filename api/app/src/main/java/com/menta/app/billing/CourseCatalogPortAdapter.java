package com.menta.app.billing;

import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.virtual.application.dto.VirtualCourseSummary;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implements Billing's {@link CourseCatalogPort} by composing Virtual's and Physical's entry
 * ports with plain Java calls inside {@code api:app}, never HTTP or a shared schema (ADR-0037,
 * same cross-module composition as {@code CatalogCompositionService}, #107).
 *
 * <p>The lookups are sequential and virtual-first: the batch goes to Virtual, and Physical is
 * consulted only for the ids Virtual did not answer, so a course answerable by both resolves
 * with its virtual title and a request that Virtual fully answers never reaches Physical. That
 * is at most one lookup per module per request, whatever the number of plans or courses.</p>
 *
 * <p>A failing module degrades only its own ids: any {@link RuntimeException} is logged once at
 * WARN with the module and the ids it was asked, and those ids stay unresolved while the other
 * module's names are kept. Malformed ids are skipped by each module and are not a failure. The
 * result is immutable, holds only the resolved ids and is keyed by the exact input string.</p>
 *
 * <p>This class must not run inside a transaction: the modules' repository methods are
 * {@code @Transactional(REQUIRED, readOnly)}, so a module exception caught here would otherwise
 * mark an enclosing transaction rollback-only. The plan use cases are deliberately not
 * transactional.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseCatalogPortAdapter implements CourseCatalogPort {

    private final VirtualCourseCatalogPort virtualCatalog;
    private final PhysicalCourseAvailabilityPort physicalCatalog;

    @Override
    public Map<String, String> courseNames(Collection<String> courseIds) {
        Set<String> ids = distinctIds(courseIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new LinkedHashMap<>();
        lookup("virtual", ids, virtualCatalog::findPublishedByIds)
            .forEach((id, course) -> names.put(id, course.title()));
        Set<String> pending = new LinkedHashSet<>(ids);
        pending.removeAll(names.keySet());
        if (!pending.isEmpty()) {
            lookup("physical", pending, physicalCatalog::findActiveByIds)
                .forEach((id, course) -> names.putIfAbsent(id, course.title()));
        }
        return Map.copyOf(names);
    }

    private static Set<String> distinctIds(Collection<String> courseIds) {
        Objects.requireNonNull(courseIds, "courseIds");
        Set<String> ids = new LinkedHashSet<>();
        for (String courseId : courseIds) {
            if (courseId != null) {
                ids.add(courseId);
            }
        }
        return ids;
    }

    private static <T> Map<String, T> lookup(
        String module, Set<String> ids, Function<Collection<String>, Map<String, T>> query
    ) {
        try {
            return query.apply(ids);
        } catch (RuntimeException failure) {
            log.warn("Course catalog lookup failed; module={}, courseIds={}", module, ids);
            return Map.of();
        }
    }
}
