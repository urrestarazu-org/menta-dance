package com.menta.billing.infrastructure.web.controller;

import com.menta.billing.application.port.out.PaymentProofRepository;
import com.menta.billing.application.port.out.PaymentProofStoragePort;
import com.menta.billing.domain.exception.InvalidProofAccessTokenException;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentProof;
import com.menta.billing.infrastructure.proof.ProofAccessTokenVerifier;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Token-authenticated proof file serving (#33, US-BILLING-005, design D1/C5/C6). Deliberately
 * <strong>not</strong> on {@code PaymentAdminController}: the caller is a browser following a
 * signed URL with no {@code Authorization} header, so this path must not sit under {@code
 * /api/v1/admin/**}. {@code SecurityConfig}'s matching {@code permitAll} rule is the credential
 * decision, not an absence of one — the HMAC token in {@code ?token=} IS the credential.
 *
 * <p>Spec/design deviation (tasks.md "Deviations from design" #2): an <em>absent</em> token
 * returns {@code 401} via an explicit branch here, before {@link ProofAccessTokenVerifier} is ever
 * invoked. A token that is present but malformed/tampered/expired — or valid for a different
 * payment — still collapses into the same indistinguishable {@code 403} via {@link
 * InvalidProofAccessTokenException}, preserving design C2's anti-oracle property.</p>
 */
@RestController
@PaymentEndpoint
public class PaymentProofController {

    private final ProofAccessTokenVerifier tokenVerifier;
    private final PaymentProofRepository proofRepository;
    private final PaymentProofStoragePort storagePort;

    public PaymentProofController(
        ProofAccessTokenVerifier tokenVerifier, PaymentProofRepository proofRepository,
        PaymentProofStoragePort storagePort
    ) {
        this.tokenVerifier = tokenVerifier;
        this.proofRepository = proofRepository;
        this.storagePort = storagePort;
    }

    @GetMapping("/api/v1/billing/payments/{paymentId}/proof")
    public ResponseEntity<byte[]> serve(
        @PathVariable String paymentId, @RequestParam(value = "token", required = false) String token
    ) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        PaymentId signedPaymentId = tokenVerifier.verify(token, Instant.now());
        if (!signedPaymentId.equals(PaymentId.of(paymentId))) {
            throw new InvalidProofAccessTokenException();
        }
        PaymentProof proof = proofRepository.findByPaymentId(signedPaymentId)
            .orElseThrow(InvalidProofAccessTokenException::new);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(proof.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(storagePort.read(proof.getStorageKey()));
    }
}
