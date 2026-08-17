package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.application.dto.RequestPasswordResetCommand;
import com.menta.auth.application.dto.ResetPasswordCommand;
import com.menta.auth.application.port.in.RequestPasswordResetUseCase;
import com.menta.auth.application.port.in.ResetPasswordUseCase;
import com.menta.auth.infrastructure.web.dto.ForgotPasswordRequest;
import com.menta.auth.infrastructure.web.dto.ResetPasswordRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the forgot-password / reset-password use-case ports (US-AUTH-005/006). */
@RestController
@RequestMapping("/api/v1/auth")
@PublicPasswordResetEndpoint
public class PasswordResetController {

    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final ClientFingerprint clientFingerprint;

    public PasswordResetController(
        RequestPasswordResetUseCase requestPasswordResetUseCase,
        ResetPasswordUseCase resetPasswordUseCase,
        ClientFingerprint clientFingerprint
    ) {
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
        this.clientFingerprint = clientFingerprint;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
        @Valid @RequestBody ForgotPasswordRequest request,
        HttpServletRequest servletRequest
    ) {
        requestPasswordResetUseCase.request(new RequestPasswordResetCommand(
            request.email(), clientFingerprint.from(servletRequest)
        ));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
        @Valid @RequestBody ResetPasswordRequest request,
        HttpServletRequest servletRequest
    ) {
        resetPasswordUseCase.reset(new ResetPasswordCommand(
            request.token(), request.newPassword(), clientFingerprint.from(servletRequest)
        ));
        return ResponseEntity.ok().build();
    }
}
