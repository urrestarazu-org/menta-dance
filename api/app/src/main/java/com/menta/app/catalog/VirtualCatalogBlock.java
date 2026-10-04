package com.menta.app.catalog;

import com.menta.virtual.application.dto.VirtualCourseSummary;

/** Modality-specific block for a {@code VIRTUAL} catalog course. */
public record VirtualCatalogBlock(
    String shortDescription,
    String imageUrl,
    String category,
    boolean premium,
    int moduleCount,
    int lessonCount,
    int totalDurationMinutes
) {

    /**
     * Projects the virtual-only fields of a course summary into this block.
     *
     * <p>Fields shared with physical courses (id, title, level) are deliberately left out because
     * they live at the top level of the catalog response.</p>
     *
     * @param summary the virtual course summary supplied by the virtual module
     * @return the block carrying the descriptive, category and content-size fields
     */
    public static VirtualCatalogBlock from(VirtualCourseSummary summary) {
        return new VirtualCatalogBlock(
            summary.shortDescription(), summary.imageUrl(), summary.category(), summary.premium(),
            summary.moduleCount(), summary.lessonCount(), summary.totalDurationMinutes()
        );
    }
}
