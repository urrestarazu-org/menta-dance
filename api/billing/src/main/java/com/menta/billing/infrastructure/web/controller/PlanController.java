package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.PlanDetailResult;
import com.menta.billing.application.dto.PlanSummaryResult;
import com.menta.billing.application.port.in.GetPlanUseCase;
import com.menta.billing.application.port.in.ListPlansUseCase;
import com.menta.billing.infrastructure.web.dto.PlanDetailResponse;
import com.menta.billing.infrastructure.web.dto.PlanListResponse;
import com.menta.billing.infrastructure.web.dto.PlanSummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the public plans use-case ports (US-BILLING-001). */
@RestController
@RequestMapping("/api/v1/billing/plans")
@PublicBillingEndpoint
public class PlanController {

    private final ListPlansUseCase listPlansUseCase;
    private final GetPlanUseCase getPlanUseCase;
    private final ClientFingerprint clientFingerprint;

    public PlanController(
        ListPlansUseCase listPlansUseCase, GetPlanUseCase getPlanUseCase, ClientFingerprint clientFingerprint
    ) {
        this.listPlansUseCase = listPlansUseCase;
        this.getPlanUseCase = getPlanUseCase;
        this.clientFingerprint = clientFingerprint;
    }

    @GetMapping
    public ResponseEntity<PlanListResponse> list(HttpServletRequest servletRequest) {
        java.util.List<PlanSummaryResult> results =
            listPlansUseCase.listActivePlans(clientFingerprint.from(servletRequest));
        return ResponseEntity.ok(new PlanListResponse(results.stream().map(PlanSummaryResponse::from).toList()));
    }

    @GetMapping("/{planId}")
    public ResponseEntity<PlanDetailResponse> get(
        @PathVariable String planId, HttpServletRequest servletRequest
    ) {
        PlanDetailResult result = getPlanUseCase.getPlan(planId, clientFingerprint.from(servletRequest));
        return ResponseEntity.ok(PlanDetailResponse.from(result));
    }
}
