package com.menta.app.catalog;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only public read of courses for students/visitors
 * (docs/07-CATALOG-API.md, #95) — composed from Physical's and Virtual's
 * ports by {@link CatalogCompositionService}. The first HTTP endpoint owned
 * directly by {@code api:app} outside of {@code auth}.
 */
@RestController
@RequestMapping("/api/v1/catalog/courses")
@PublicCatalogEndpoint
public class CatalogController {

    private final CatalogCompositionService compositionService;

    public CatalogController(CatalogCompositionService compositionService) {
        this.compositionService = compositionService;
    }

    @GetMapping
    public ResponseEntity<CatalogListResponse> list() {
        return ResponseEntity.ok(new CatalogListResponse(compositionService.listCourses()));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CatalogCourseDetail> get(@PathVariable String courseId) {
        // #47/#107: rich detail for the public course page — the virtual shape
        // for a virtual id, the physical shape (course data plus upcoming
        // sessions) for a physical id; anything else is the standard 404 problem.
        return ResponseEntity.ok(compositionService.getCourseDetail(courseId));
    }
}
