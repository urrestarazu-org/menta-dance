package com.menta.bff.application.dto;

/** What a payment buys. {@code UNKNOWN} stands for a value this BFF does not know yet. */
public enum PaymentModality {
    PHYSICAL,
    VIRTUAL,
    UNKNOWN
}
