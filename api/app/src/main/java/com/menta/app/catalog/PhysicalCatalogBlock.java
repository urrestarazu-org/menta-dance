package com.menta.app.catalog;

import com.menta.physical.application.dto.PhysicalCourseSummary;

/** Modality-specific block for a {@code PHYSICAL} catalog course. */
public record PhysicalCatalogBlock(String professorName, String dayOfWeek, String startTime, int capacity) {

    public static PhysicalCatalogBlock from(PhysicalCourseSummary summary) {
        return new PhysicalCatalogBlock(
            summary.professorName(), summary.dayOfWeek(), summary.startTime(), summary.capacity()
        );
    }
}
