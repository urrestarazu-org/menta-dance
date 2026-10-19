package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * The target {@code userId} of a trial grant does not reference an existing user
 * (US-BILLING-012, design.md A5/D8). This check runs before the plan-availability check ({@code
 * 422}) and the already-in-force check ({@code 409}), so it wins whenever multiple problems
 * coexist.
 */
public class UserNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "USER_NOT_FOUND";

    public UserNotFoundException() {
        super(ERROR_CODE, "No user was found for the requested userId");
    }
}
