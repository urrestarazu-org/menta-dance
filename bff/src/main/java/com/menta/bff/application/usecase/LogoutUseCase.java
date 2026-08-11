package com.menta.bff.application.usecase;

/**
 * Use case for user logout flow.
 * <p>
 * Orchestrates the logout process:
 * 1. Load refresh token from session (if exists)
 * 2. Revoke refresh token in Auth API (best effort)
 * 3. Clear server-side session
 * </p>
 * <p>
 * IMPORTANT: Logout is fail-open. If Auth API revocation fails,
 * we still clear the local session to prevent user lockout.
 * This is safe because:
 * - Access tokens expire in 15 minutes
 * - Refresh tokens can only be used once (rotation)
 * - User's session is cleared, so they can't make new requests
 * </p>
 * <p>
 * Part of Clean Architecture application layer.
 * </p>
 */
public interface LogoutUseCase {

    /**
     * Executes logout flow.
     * <p>
     * Attempts to revoke refresh token in Auth API, then clears session.
     * Never throws exceptions - logout is idempotent and fail-open.
     * </p>
     * <p>
     * Idempotent: Calling multiple times is safe, even if session is already cleared.
     * </p>
     */
    void execute();
}
