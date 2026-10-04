package com.menta.app.catalog;

import com.menta.physical.application.dto.PhysicalCourseSummary;

/** Modality-specific block for a {@code PHYSICAL} catalog course. */
public record PhysicalCatalogBlock(String professorName, String dayOfWeek, String startTime, int capacity) {

    /**
     * Projects the in-person fields of a course summary into this block.
     *
     * <p>Fields shared with virtual courses (id, title, level) are deliberately left out because
     * they live at the top level of the catalog response.</p>
     *
     * @param summary the physical course summary supplied by the physical module
     * @return the block carrying the professor, weekly slot and capacity
     */
    public static PhysicalCatalogBlock from(PhysicalCourseSummary summary) {
        return new PhysicalCatalogBlock(
            summary.professorName(), summary.dayOfWeek(), summary.startTime(), summary.capacity()
        );
    }
}
