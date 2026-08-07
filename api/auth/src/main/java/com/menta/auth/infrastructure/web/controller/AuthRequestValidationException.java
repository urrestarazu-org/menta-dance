package com.menta.auth.infrastructure.web.controller;

/** Signals invalid HTTP input without retaining or exposing sensitive headers. */
final class AuthRequestValidationException extends RuntimeException {

    AuthRequestValidationException() {
        super("Invalid authentication request");
    }
}
