package com.menta.app.catalog;

import java.util.List;

/** Wire shape wrapping the combined catalog under a {@code courses} key. */
public record CatalogListResponse(List<CatalogCourseResponse> courses) {
}
