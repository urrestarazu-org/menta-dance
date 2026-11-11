package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ActivationRequirement;
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
    public ActivationRequirement checkToken(String token) {
        return authApiClient.validateActivationToken(requireTokenFormat(token));
    }

    @Override
    public void activate(String token) {
        authApiClient.activate(requireTokenFormat(token));
    }

    @Override
    public void activate(String token, String password) {
        Objects.requireNonNull(password, "password cannot be null");
        authApiClient.activate(requireTokenFormat(token), password);
    }

    private static String requireTokenFormat(String token) {
        if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
            throw new AuthApiClient.InvalidActivationTokenException("Malformed activation token");
        }
        return token;
    }
}
