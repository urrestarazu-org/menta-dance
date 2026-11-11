package com.menta.bff.application.dto;

/**
 * What the holder of a valid activation token still has to provide to activate the account.
 *
 * <p>Accounts created by an administrator are born without a usable password, so their holder
 * must choose one while activating. Accounts that registered themselves already have theirs.
 * </p>
 */
public enum ActivationRequirement {

    /** The account already has its password: activating takes only the confirmation. */
    NONE,

    /** The account was created by an administrator: the holder must choose a password. */
    PASSWORD
}
