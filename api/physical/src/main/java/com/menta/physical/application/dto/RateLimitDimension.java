package com.menta.physical.application.dto;

/** The independent budget that refused a QR check-in (ADR-0044). */
public enum RateLimitDimension {

    /** Budget per client IP: stops one origin probing many devices. */
    IP("ip"),

    /** Budget per {@code deviceId}: stops one device, or someone spoofing it, regardless of IP. */
    DEVICE("device");

    private final String tag;

    RateLimitDimension(String tag) {
        this.tag = tag;
    }

    /** Stable lowercase label used for metric tags and log lines. */
    public String tag() {
        return tag;
    }
}
