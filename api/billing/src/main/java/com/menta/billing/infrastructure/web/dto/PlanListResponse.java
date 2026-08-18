package com.menta.billing.infrastructure.web.dto;

import java.util.List;

/** Wire shape wrapping the plans list under a {@code plans} key. */
public record PlanListResponse(List<PlanSummaryResponse> plans) {
}
