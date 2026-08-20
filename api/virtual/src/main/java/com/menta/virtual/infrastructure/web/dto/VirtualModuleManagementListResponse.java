package com.menta.virtual.infrastructure.web.dto;

import java.util.List;

/** Wire shape wrapping the reordered module list under a {@code modules} key. */
public record VirtualModuleManagementListResponse(List<VirtualModuleManagementResponse> modules) {
}
