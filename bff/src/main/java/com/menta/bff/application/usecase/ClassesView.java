package com.menta.bff.application.usecase;

import java.util.List;

/**
 * View model for the public physical classes list.
 *
 * @param classes     cards to render, in upstream order (empty when unavailable)
 * @param unavailable whether the upstream catalog could not be obtained
 */
public record ClassesView(List<ClassCard> classes, boolean unavailable) {

    /** The degraded view shown while the catalog is down. */
    public static ClassesView unavailableView() {
        return new ClassesView(List.of(), true);
    }

    /** Whether the catalog answered but has no physical class to show. */
    public boolean empty() {
        return !unavailable && classes.isEmpty();
    }
}
