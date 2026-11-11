package com.menta.auth.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when a role change would leave the system without any active administrator.
 */
public class LastAdminCannotBeDemotedException extends BusinessException {

    private static final String ERROR_CODE = "LAST_ADMIN_CANNOT_BE_DEMOTED";

    public LastAdminCannotBeDemotedException() {
        super(ERROR_CODE, "The last active administrator cannot lose the ADMIN role");
    }
}
