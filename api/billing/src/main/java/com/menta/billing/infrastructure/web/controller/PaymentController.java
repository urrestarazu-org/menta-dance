package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.PaymentProofUpload;
import com.menta.billing.application.dto.SubmitPaymentProofCommand;
import com.menta.billing.application.port.in.SubmitPaymentProofUseCase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP adapter for the bank-transfer payment proof upload (#31, US-BILLING-003).
 *
 * <p>{@code SecurityConfig} gates {@code POST .../proof} with a dedicated {@code .authenticated()}
 * matcher. The owning user is read from the token and never from the body or a request parameter
 * — the same {@code actingUserId} pattern {@code SubscriptionController} uses (design C8). {@code
 * GET /{paymentId}} is added in P3d.</p>
 */
@RestController
@RequestMapping("/api/v1/billing/payments")
@PaymentEndpoint
public class PaymentController {

    private final SubmitPaymentProofUseCase submitPaymentProofUseCase;

    public PaymentController(SubmitPaymentProofUseCase submitPaymentProofUseCase) {
        this.submitPaymentProofUseCase = submitPaymentProofUseCase;
    }

    @PostMapping("/{paymentId}/proof")
    public ResponseEntity<Void> submitProof(
        @PathVariable String paymentId, @RequestParam("file") MultipartFile file, Authentication authentication
    ) {
        submitPaymentProofUseCase.submit(new SubmitPaymentProofCommand(
            paymentId, actingUserId(authentication), toUpload(file)
        ));
        return ResponseEntity.ok().build();
    }

    private static PaymentProofUpload toUpload(MultipartFile file) {
        try {
            return new PaymentProofUpload(
                file.getContentType(), file.getOriginalFilename(), file.getSize(), file.getBytes()
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the uploaded payment proof", e);
        }
    }

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
