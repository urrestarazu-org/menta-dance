package com.menta.app.catalog;

import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.virtual.application.dto.VirtualCourseSummary;

/**
 * Wire shape for one catalog course. {@code physical}/{@code virtual} are
 * mutually exclusive — exactly one is non-null, matching the course's single
 * modality (docs/07-CATALOG-API.md). Only the fields genuinely common to
 * both source DTOs sit at the top level; everything else that exists on one
 * modality but not the other lives in its own nested block instead of being
 * padded with nulls to fake a shared shape.
 */
public record CatalogCourseResponse(
    String courseId,
    CourseModality modality,
    String title,
    String level,
    PhysicalCatalogBlock physical,
    VirtualCatalogBlock virtual
) {

    /**
     * Builds the response for a physical course.
     *
     * <p>Only the {@code physical} block is populated and {@code virtual} stays {@code null},
     * preserving the exactly-one-block invariant of this record.</p>
     *
     * @param summary the physical course summary supplied by the physical module
     * @return the catalog entry with modality {@code PHYSICAL}
     */
    public static CatalogCourseResponse fromPhysical(PhysicalCourseSummary summary) {
        return new CatalogCourseResponse(
            summary.courseId(), CourseModality.PHYSICAL, summary.title(), summary.level(),
            PhysicalCatalogBlock.from(summary), null
        );
    }

    /**
     * Builds the response for a virtual course.
     *
     * <p>Only the {@code virtual} block is populated and {@code physical} stays {@code null},
     * preserving the exactly-one-block invariant of this record.</p>
     *
     * @param summary the virtual course summary supplied by the virtual module
     * @return the catalog entry with modality {@code VIRTUAL}
     */
    public static CatalogCourseResponse fromVirtual(VirtualCourseSummary summary) {
        return new CatalogCourseResponse(
            summary.courseId(), CourseModality.VIRTUAL, summary.title(), summary.level(),
            null, VirtualCatalogBlock.from(summary)
        );
    }
}
