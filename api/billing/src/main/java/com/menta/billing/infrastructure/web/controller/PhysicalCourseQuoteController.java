package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.CreatePhysicalCourseQuoteCommand;
import com.menta.billing.application.dto.PhysicalCourseQuoteResult;
import com.menta.billing.application.port.in.CreatePhysicalCourseQuoteUseCase;
import com.menta.billing.infrastructure.web.dto.CreatePhysicalCourseQuoteRequest;
import com.menta.billing.infrastructure.web.dto.PhysicalCourseQuoteResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for physical course quotes (US-BILLING-006). Requires any
 * authenticated user — {@code SecurityConfig} gates this path with {@code
 * .authenticated()}; no specific role is required and the quote never
 * records who requested it (linking a quote to a user is a later checkout
 * story, out of scope here).
 */
@RestController
@RequestMapping("/api/v1/billing/physical/quotes")
@PhysicalQuoteEndpoint
public class PhysicalCourseQuoteController {

    private final CreatePhysicalCourseQuoteUseCase createPhysicalCourseQuoteUseCase;

    public PhysicalCourseQuoteController(CreatePhysicalCourseQuoteUseCase createPhysicalCourseQuoteUseCase) {
        this.createPhysicalCourseQuoteUseCase = createPhysicalCourseQuoteUseCase;
    }

    @PostMapping
    public ResponseEntity<PhysicalCourseQuoteResponse> create(
        @Valid @RequestBody CreatePhysicalCourseQuoteRequest request
    ) {
        CreatePhysicalCourseQuoteCommand command = new CreatePhysicalCourseQuoteCommand(
            request.courseId(), request.purchaseType(), request.selectedSessionId()
        );
        PhysicalCourseQuoteResult result = createPhysicalCourseQuoteUseCase.create(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(PhysicalCourseQuoteResponse.from(result));
    }
}
