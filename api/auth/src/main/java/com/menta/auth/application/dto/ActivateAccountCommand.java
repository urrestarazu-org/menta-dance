package com.menta.auth.application.dto;

/**
 * Activate-account use case input.
 * Pure DTO — carried from the HTTP layer (PR3) to the application boundary.
 */
public record ActivateAccountCommand(String rawToken) {
}
