package com.menta.bff.application.dto;

/** Who is looking at the plans page, which decides the subscribe action it offers. */
public enum PlansViewer {
    /** A visitor without a session. */
    ANONYMOUS,
    /** An authenticated student, the only role that can subscribe. */
    STUDENT,
    /** Any other authenticated role. */
    OTHER
}
