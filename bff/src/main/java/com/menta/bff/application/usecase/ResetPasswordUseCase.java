package com.menta.bff.application.usecase;

/**
 * Use case for choosing a new password from the link received by email.
 *
 * <p>The token and the password are credentials: implementations never log them and never
 * echo them in a failure.
 * </p>
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface ResetPasswordUseCase {

    /**
     * Consumes the token and sets the new password.
     *
     * @param token       Opaque token taken from the link
     * @param newPassword The new raw password
     * @throws com.menta.bff.application.port.out.AuthApiClient.InvalidResetTokenException if the token is
     *     malformed, unknown, expired or already used
     * @throws com.menta.bff.application.port.out.AuthApiClient.PasswordRejectedException if the API refuses
     *     the password (weak, or equal to the current one)
     * @throws com.menta.bff.application.port.out.AuthApiClient.InvalidRequestException if the API rejects the
     *     input (400)
     * @throws com.menta.bff.application.port.out.AuthApiClient.RateLimitedException if the API rate limit was
     *     hit (429)
     * @throws com.menta.bff.application.port.out.AuthApiClient.ServiceUnavailableException if the Auth API is
     *     unavailable
     */
    void execute(String token, String newPassword);
}
