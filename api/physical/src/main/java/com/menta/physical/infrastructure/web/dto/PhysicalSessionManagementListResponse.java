package com.menta.physical.infrastructure.web.dto;

import java.util.List;

public record PhysicalSessionManagementListResponse(List<PhysicalSessionManagementResponse> sessions) {
}
