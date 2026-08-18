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
    public ResponseEntity<CatalogCourseResponse> get(@PathVariable String courseId) {
        return ResponseEntity.ok(compositionService.getCourse(courseId));
    }
}
