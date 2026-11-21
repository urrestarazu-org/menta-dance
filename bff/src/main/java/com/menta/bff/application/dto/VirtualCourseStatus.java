package com.menta.bff.application.dto;

/** Lifecycle of a virtual course. {@code UNKNOWN} stands for a value this BFF does not know yet. */
public enum VirtualCourseStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED,
    UNKNOWN
}
