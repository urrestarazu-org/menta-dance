package com.menta.bff.application.usecase;

/** Visual weight of a status badge; the template turns it into a {@code badge-*} class. */
public enum BadgeTone {
    SUCCESS("badge-success"),
    WARNING("badge-warning"),
    DANGER("badge-danger"),
    NEUTRAL("badge-neutral");

    private final String cssClass;

    BadgeTone(String cssClass) {
        this.cssClass = cssClass;
    }

    /** The stylesheet class of the badge. */
    public String cssClass() {
        return cssClass;
    }
}
