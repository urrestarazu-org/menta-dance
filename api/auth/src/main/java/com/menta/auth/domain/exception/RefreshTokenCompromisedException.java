package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a refresh token is detected as compromised: it was presented in
 * USED, ROTATED, ROTATED-USED, REVOKED state, or its token_version no longer
 * matches the user. Indicates the entire family must be revoked and the user
 * tokenVersion bumped.
 */
public class RefreshTokenCompromisedException extends BusinessException {

    private static final String ERROR_CODE = "REFRESH_TOKEN_COMPROMISED";

    private static final String GENERIC_MESSAGE = "Refresh token rejected";

    public RefreshTokenCompromisedException() {
        super(ERROR_CODE, GENERIC_MESSAGE);
    }
}
