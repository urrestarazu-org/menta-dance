package com.menta.billing.infrastructure.catalog;

import com.menta.billing.application.port.out.CourseCatalogPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Compile-boundary placeholder for {@link CourseCatalogPort}.
 *
 * // TODO(#40/#46): replace with an adapter that calls Virtual/Physical's
 * real course-catalog ports once those issues land.
 *
 * <p>Callers must not let this propagate as a request failure — see {@code
 * PlanCourseResolver} in the application layer, which catches any failure
 * from this port and degrades to a {@code null} course name instead.</p>
 */
@Component
public class NotImplementedCourseCatalogPort implements CourseCatalogPort {

    private static final String MESSAGE =
        "CourseCatalogPort adapter not implemented yet — see issues #40/#46";

    @Override
    public Optional<String> courseName(String courseId) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
