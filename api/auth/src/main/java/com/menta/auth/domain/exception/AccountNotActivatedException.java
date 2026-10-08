package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;
import java.util.UUID;

/**
 * Thrown when login is attempted with the CORRECT password against an account still in
 * PENDING_ACTIVATION status.
 *
 * <p>The password is verified before this is raised, so only someone who already knows the
 * credentials learns the account state (no account enumeration). The login flow MUST NOT emit
 * any outbox event and MUST NOT issue tokens; the caller is expected to map this to HTTP 403.
 */
public class AccountNotActivatedException extends BusinessException {

    private static final String ERROR_CODE = "ACCOUNT_NOT_ACTIVATED";

    public AccountNotActivatedException(UUID userId) {
        super(ERROR_CODE, "User account is not activated: " + userId);
    }
}
