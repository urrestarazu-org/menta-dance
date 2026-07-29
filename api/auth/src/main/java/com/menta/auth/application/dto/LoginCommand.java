package com.menta.auth.application.dto;

/**
 * Login use case input.
 * Pure DTO — carried from the HTTP layer (PR3) to the application boundary.
 */
public record LoginCommand(String email, String password) {
}
