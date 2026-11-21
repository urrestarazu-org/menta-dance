package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.PaymentProof;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException;
import com.menta.bff.application.port.out.AdminBillingApiClient.ServiceUnavailableException;
import com.menta.bff.application.usecase.GetPaymentProofUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * The transfer proof of a payment, {@code GET /gestion/pagos/{paymentId}/comprobante} (#370): the bytes, served
 * by the BFF so the browser never sees the API's signed link, its token or any other URL of the API. Access is
 * restricted to {@code ADMIN} in {@code BffSecurityConfig}; the Billing API makes the real decision with the
 * session's access token.
 *
 * <p>A proof is sensitive: it is never cached, shown inline with the type declared when it was uploaded (one of
 * the three allowed ones, checked by the adapter), and with {@code nosniff} so the browser does not reinterpret
 * it. No {@code Content-Security-Policy: sandbox} is added: it would stop the browser's PDF viewer. A payment or
 * proof that does not exist is a 404 and any other failure a 503, both without a body: nothing from the API's
 * answer reaches the browser.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ManagementPaymentProofController {

    private final GetPaymentProofUseCase getPaymentProofUseCase;

    @GetMapping("/gestion/pagos/{paymentId}/comprobante")
    public ResponseEntity<byte[]> proof(@PathVariable String paymentId, HttpServletRequest request) {
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        try {
            PaymentProof proof = getPaymentProofUseCase.execute(accessToken, paymentId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(proof.contentType()))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(proof.content());
        } catch (PaymentRefusalException e) {
            return empty(e.getCode() == PaymentRefusalException.Code.PAYMENT_NOT_FOUND
                    || e.getCode() == PaymentRefusalException.Code.PROOF_NOT_FOUND
                    ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE);
        } catch (ServiceUnavailableException e) {
            log.warn("Payment proof unavailable: {}", e.getMessage());
            return empty(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static ResponseEntity<byte[]> empty(HttpStatus status) {
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
