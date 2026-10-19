package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.PhysicalCoursePricingResult;
import com.menta.billing.application.dto.UpdatePhysicalCoursePricingCommand;
import com.menta.billing.application.port.in.GetPhysicalCoursePricingUseCase;
import com.menta.billing.application.port.in.UpdatePhysicalCoursePricingUseCase;
import com.menta.billing.infrastructure.web.dto.PhysicalCoursePricingResponse;
import com.menta.billing.infrastructure.web.dto.UpdatePhysicalCoursePricingRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for physical course pricing (US-BILLING-009). {@code
 * SecurityConfig} permits {@code GET} to everyone and restricts {@code PUT}
 * to ADMIN/INSTRUCTOR; this class never checks the role itself for that
 * coarse gate — only ownership of the specific course, which {@link
 * UpdatePhysicalCoursePricingUseCase} enforces via the cross-module
 * ownership port.
 */
@RestController
@RequestMapping("/api/v1/billing/physical/courses/{courseId}/pricing")
@PhysicalPricingEndpoint
public class PhysicalCoursePricingController {

    private final GetPhysicalCoursePricingUseCase getPhysicalCoursePricingUseCase;
    private final UpdatePhysicalCoursePricingUseCase updatePhysicalCoursePricingUseCase;

    public PhysicalCoursePricingController(
        GetPhysicalCoursePricingUseCase getPhysicalCoursePricingUseCase,
        UpdatePhysicalCoursePricingUseCase updatePhysicalCoursePricingUseCase
    ) {
        this.getPhysicalCoursePricingUseCase = getPhysicalCoursePricingUseCase;
        this.updatePhysicalCoursePricingUseCase = updatePhysicalCoursePricingUseCase;
    }

    @GetMapping
    public ResponseEntity<PhysicalCoursePricingResponse> get(@PathVariable String courseId) {
        PhysicalCoursePricingResult result = getPhysicalCoursePricingUseCase.getPricing(courseId);
        return ResponseEntity.ok(PhysicalCoursePricingResponse.from(result));
    }

    @PutMapping
    public ResponseEntity<PhysicalCoursePricingResponse> update(
        @PathVariable String courseId, @Valid @RequestBody UpdatePhysicalCoursePricingRequest request,
        Authentication authentication
    ) {
        UpdatePhysicalCoursePricingCommand command = new UpdatePhysicalCoursePricingCommand(
            request.monthlyPrice(), request.currencyOrDefault(), request.individualSurchargePercent(),
            request.reason()
        );
        PhysicalCoursePricingResult result = updatePhysicalCoursePricingUseCase.update(
            courseId, command, actingUserId(authentication), isAdmin(authentication)
        );
        return ResponseEntity.ok(PhysicalCoursePricingResponse.from(result));
    }

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    }
}
