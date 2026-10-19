package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.AuthApiClient;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Implementation of {@link AccountActivationUseCase}.
 *
 * <p>The API issues URL-safe Base64 tokens, so anything else cannot be a real token. Such
 * input is rejected here instead of being forwarded, which also keeps arbitrary text out of
 * the path of the API call.
 * </p>
 */
public class AccountActivationUseCaseImpl implements AccountActivationUseCase {

    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,256}");

    private final AuthApiClient authApiClient;

    /**
     * Constructor for dependency injection.
     *
     * @param authApiClient HTTP client for Auth API
     */
    public AccountActivationUseCaseImpl(AuthApiClient authApiClient) {
        this.authApiClient = Objects.requireNonNull(authApiClient, "authApiClient cannot be null");
    }

    @Override
    public boolean isTokenValid(String token) {
        if (!hasTokenFormat(token)) {
            return false;
        }
        try {
            authApiClient.validateActivationToken(token);
            return true;
        } catch (AuthApiClient.InvalidActivationTokenException e) {
            return false;
        }
    }

    @Override
    public void activate(String token) {
        if (!hasTokenFormat(token)) {
            throw new AuthApiClient.InvalidActivationTokenException("Malformed activation token");
        }
        authApiClient.activate(token);
    }

    private static boolean hasTokenFormat(String token) {
        return token != null && TOKEN_FORMAT.matcher(token).matches();
    }
}
