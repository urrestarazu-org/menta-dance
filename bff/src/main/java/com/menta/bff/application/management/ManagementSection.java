package com.menta.bff.application.management;

import java.util.Set;

/**
 * One entry of the management area's navigation.
 *
 * @param key stable identifier of the section
 * @param label text shown in the navigation (UI copy, Spanish)
 * @param path application path the entry links to
 * @param roles roles allowed to see the entry, without the {@code ROLE_} prefix
 * @param enabled whether the section has a screen yet; a disabled section is never offered, so the
 *     navigation never shows a dead link
 */
public record ManagementSection(String key, String label, String path, Set<String> roles, boolean enabled) {

    public ManagementSection {
        roles = Set.copyOf(roles);
    }
}
