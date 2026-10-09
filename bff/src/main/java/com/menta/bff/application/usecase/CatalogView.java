package com.menta.bff.application.usecase;

import com.menta.bff.domain.model.CourseModality;
import java.util.List;

/**
 * View model for the home page catalog.
 *
 * @param courses     cards to render, in upstream order (empty when unavailable)
 * @param selected    active modality filter, {@code null} for "all"
 * @param unavailable whether the upstream catalog could not be obtained
 */
public record CatalogView(List<CatalogCard> courses, CourseModality selected, boolean unavailable) {

    /** Whether the catalog answered but has nothing to show. */
    public boolean empty() {
        return !unavailable && courses.isEmpty();
    }
}
