package com.menta.bff.domain.membership;

/** State of a price row according to today's date. */
public enum PriceRowState {

    /** The price in force today: it can be paid. */
    CURRENT,

    /** A future price: shown locked, without a pay button. */
    LATER,

    /** A price whose deadline has passed: shown struck through as a lost benefit. */
    EXPIRED
}
