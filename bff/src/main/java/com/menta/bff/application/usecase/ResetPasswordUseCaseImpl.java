package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.AuthApiClient;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Implementation of {@link ResetPasswordUseCase}.
 *
 * <p>The API issues URL-safe Base64 tokens, so anything else cannot be a real token and is
 * rejected here instead of being forwarded.
 * </p>
 */
public class ResetPasswordUseCaseImpl implements ResetPasswordUseCase {

    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,256}");

    private final AuthApiClient authApiClient;

    /**
     * Constructor for dependency injection.
     *
     * @param authApiClient HTTP client for Auth API
     */
    public ResetPasswordUseCaseImpl(AuthApiClient authApiClient) {
        this.authApiClient = Objects.requireNonNull(authApiClient, "authApiClient cannot be null");
    }

    @Override
    public void execute(String token, String newPassword) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            throw new AuthApiClient.InvalidResetTokenException("Malformed password reset token");
        }
        authApiClient.resetPassword(token, Objects.requireNonNull(newPassword, "newPassword cannot be null"));
    }
}
