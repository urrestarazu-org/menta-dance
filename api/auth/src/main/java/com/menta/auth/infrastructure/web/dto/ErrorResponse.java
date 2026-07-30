package com.menta.auth.infrastructure.web.dto;

/**
 * Generic error body for the auth endpoints. Keeps the wire shape stable
 * while the controller fans out four distinct domain exceptions.
 */
public record ErrorResponse(String code, String message) {
}
