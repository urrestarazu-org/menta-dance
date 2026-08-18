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

    public static VirtualCatalogBlock from(VirtualCourseSummary summary) {
        return new VirtualCatalogBlock(
            summary.shortDescription(), summary.imageUrl(), summary.category(), summary.premium(),
            summary.moduleCount(), summary.lessonCount(), summary.totalDurationMinutes()
        );
    }
}
