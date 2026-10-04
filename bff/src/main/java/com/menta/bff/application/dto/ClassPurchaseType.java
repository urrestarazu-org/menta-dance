package com.menta.bff.application.dto;

/**
 * What a student buys of an in-person course, mirroring billing's {@code
 * PurchaseType}: the whole current calendar month, or one single session.
 */
public enum ClassPurchaseType {
    MONTHLY,
    INDIVIDUAL
}
