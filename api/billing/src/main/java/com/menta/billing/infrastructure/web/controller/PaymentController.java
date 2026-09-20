package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.dto.PaymentProofUpload;
import com.menta.billing.application.dto.SubmitPaymentProofCommand;
import com.menta.billing.application.port.in.GetPaymentUseCase;
import com.menta.billing.application.port.in.SubmitPaymentProofUseCase;
import com.menta.billing.infrastructure.web.dto.PaymentStatusResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * HTTP adapter for bank-transfer payments: proof upload and status read (#31, US-BILLING-003).
 *
 * <p>{@code SecurityConfig} gates both {@code POST .../proof} and {@code GET /{paymentId}} with a
 * dedicated {@code .authenticated()} matcher each. The owning user is always read from the token
 * and never from the body, a query parameter or the path — the same {@code actingUserId} pattern
 * {@code SubscriptionController} uses (design C8).</p>
 */
@RestController
@RequestMapping("/api/v1/billing/payments")
@PaymentEndpoint
public class PaymentController {

    private final SubmitPaymentProofUseCase submitPaymentProofUseCase;
    private final GetPaymentUseCase getPaymentUseCase;

    public PaymentController(SubmitPaymentProofUseCase submitPaymentProofUseCase, GetPaymentUseCase getPaymentUseCase) {
        this.submitPaymentProofUseCase = submitPaymentProofUseCase;
        this.getPaymentUseCase = getPaymentUseCase;
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

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentStatusResponse> getPayment(
        @PathVariable String paymentId, Authentication authentication
    ) {
        return ResponseEntity.ok(PaymentStatusResponse.from(
            getPaymentUseCase.getPayment(paymentId, actingUserId(authentication))
        ));
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
