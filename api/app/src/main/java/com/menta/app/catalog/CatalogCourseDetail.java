package com.menta.app.catalog;

/**
 * Wire shape of {@code GET /api/v1/catalog/courses/{courseId}}: exactly one of
 * the two modality-specific detail records (#107). Serialized through the
 * runtime record class, with no type discriminator, so the virtual body stays
 * unchanged and the physical body tells itself apart through {@code modality}.
 */
public sealed interface CatalogCourseDetail
    permits CatalogCourseDetailResponse, CatalogPhysicalCourseDetailResponse {
}
