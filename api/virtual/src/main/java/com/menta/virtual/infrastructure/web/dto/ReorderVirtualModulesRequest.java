package com.menta.virtual.infrastructure.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReorderVirtualModulesRequest(@NotEmpty List<String> moduleIds) {
}
