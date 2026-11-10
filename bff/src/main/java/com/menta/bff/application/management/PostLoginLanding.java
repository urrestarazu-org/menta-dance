package com.menta.bff.application.management;

import java.util.Collection;

/** Decides where a user lands after signing in, by role. */
public final class PostLoginLanding {

    /** Home of the management area, for the staff. */
    public static final String MANAGEMENT_HOME = "/gestion";

    /** Home of every other user. */
    public static final String DASHBOARD = "/dashboard";

    private PostLoginLanding() {
    }

    /**
     * Resolves the path a user lands on after signing in.
     *
     * @param roles the user's roles, with or without the {@code ROLE_} prefix; may be {@code null}
     * @return the management home for staff, the dashboard for a student and for any other role
     */
    public static String pathFor(Collection<String> roles) {
        return ManagementRoles.isStaff(roles) ? MANAGEMENT_HOME : DASHBOARD;
    }
}
