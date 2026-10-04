package com.menta.virtual.infrastructure.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Request body for reordering the modules of a course: the non-empty list of module ids in the desired order. */
public record ReorderVirtualModulesRequest(@NotEmpty List<String> moduleIds) {
}
