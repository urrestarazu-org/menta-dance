package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.port.in.CreatePlanUseCase;
import com.menta.billing.infrastructure.web.dto.CreatePlanRequest;
import com.menta.billing.infrastructure.web.dto.PlanDetailResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * HTTP adapter for plan administration (#762): an admin creates a billing plan.
 *
 * <p>{@code SecurityConfig}'s existing generic {@code /api/v1/admin/**} rule already restricts
 * this path to {@code ROLE_ADMIN} — no new matcher is needed, pinned by {@code
 * SecurityConfigTest}. {@link #requireAdmin(Authentication)} is a second, independent
 * defense-in-depth check, the same shape {@code PurchaseAdminController} uses.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/billing/plans")
@PlanAdminEndpoint
@RequiredArgsConstructor
public class PlanAdminController {

    private final CreatePlanUseCase createPlanUseCase;

    /** Answers {@code 201} with the same body as the public plan detail; the id is server-generated. */
    @PostMapping
    public ResponseEntity<PlanDetailResponse> create(
        @Valid @RequestBody CreatePlanRequest request, Authentication authentication
    ) {
        requireAdmin(authentication);
        PlanDetailResponse body = PlanDetailResponse.from(
            createPlanUseCase.create(request.toCommand(UUID.fromString(authentication.getName())))
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    private static void requireAdmin(Authentication authentication) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
        if (!admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
