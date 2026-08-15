package com.menta.auth.domain.exception;

/** Signals an existing public registration without carrying any account identifier. */
public class DuplicateRegistrationException extends IllegalArgumentException {

    public DuplicateRegistrationException() {
        super("A registration already exists");
    }
}
