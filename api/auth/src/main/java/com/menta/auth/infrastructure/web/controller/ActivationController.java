package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.application.dto.ActivateAccountCommand;
import com.menta.auth.application.dto.ResendActivationCommand;
import com.menta.auth.application.port.in.ActivateAccountUseCase;
import com.menta.auth.application.port.in.ResendActivationUseCase;
import com.menta.auth.infrastructure.web.ProblemDetails;
import com.menta.auth.infrastructure.web.dto.ResendActivationRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the account activation use-case ports. */
@RestController
@RequestMapping("/api/v1/auth")
@PublicActivationEndpoint
public class ActivationController {

    private final ActivateAccountUseCase activateAccountUseCase;
    private final ResendActivationUseCase resendActivationUseCase;
    private final ClientFingerprint clientFingerprint;

    public ActivationController(
        ActivateAccountUseCase activateAccountUseCase,
        ResendActivationUseCase resendActivationUseCase,
        ClientFingerprint clientFingerprint
    ) {
        this.activateAccountUseCase = activateAccountUseCase;
        this.resendActivationUseCase = resendActivationUseCase;
        this.clientFingerprint = clientFingerprint;
    }

    @GetMapping("/activate/{token}")
    public ResponseEntity<Void> activate(@PathVariable String token) {
        activateAccountUseCase.activate(new ActivateAccountCommand(token));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-activation")
    public ResponseEntity<Void> resend(
        @Valid @RequestBody ResendActivationRequest request,
        HttpServletRequest servletRequest
    ) {
        resendActivationUseCase.resend(new ResendActivationCommand(
            request.email(), clientFingerprint.from(servletRequest)
        ));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> handleSemanticResendRejection(IllegalArgumentException exception) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "La solicitud de activación no es válida.", "INVALID_ACTIVATION_REQUEST"
        );
    }
}
