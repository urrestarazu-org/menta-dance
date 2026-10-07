package com.menta.shared.demo;

import java.util.UUID;

/**
 * Fixed identifiers of the demo seed that cross module boundaries: the four role users and the four courses.
 *
 * <p>Identifiers private to one module (modules, lessons, plans, sessions, devices) stay in that module's
 * fixture. This class holds no logic.
 */
public final class DemoIds {

    public static final UUID ADMIN_USER_ID = UUID.fromString("de000000-0000-4000-8000-000000000001");
    public static final UUID INSTRUCTOR_USER_ID = UUID.fromString("de000000-0000-4000-8000-000000000002");
    public static final UUID STUDENT_USER_ID = UUID.fromString("de000000-0000-4000-8000-000000000003");
    public static final UUID RECEPTIONIST_USER_ID = UUID.fromString("de000000-0000-4000-8000-000000000004");

    public static final UUID VIRTUAL_SENSUAL_COURSE_ID = UUID.fromString("de000000-0000-4000-8000-0000000000a1");
    public static final UUID VIRTUAL_TRADITIONAL_COURSE_ID =
        UUID.fromString("de000000-0000-4000-8000-0000000000a2");
    public static final UUID PHYSICAL_SENSUAL_COURSE_ID = UUID.fromString("de000000-0000-4000-8000-0000000000b1");
    public static final UUID PHYSICAL_TRADITIONAL_COURSE_ID =
        UUID.fromString("de000000-0000-4000-8000-0000000000b2");

    private DemoIds() {
    }
}
