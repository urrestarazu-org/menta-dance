package com.menta.bff.application.management;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Decides which management sections a user is offered.
 *
 * <p>Pure application logic, with no framework dependency. The catalog is injected, so the production
 * list lives in configuration and tests can build their own.
 * </p>
 */
public class ManagementNavigation {

    private final List<ManagementSection> sections;

    public ManagementNavigation(List<ManagementSection> sections) {
        this.sections = List.copyOf(sections);
    }

    /**
     * Sections the user can open: enabled ones whose roles intersect the user's, in the declared order.
     *
     * @param roles the user's roles, with or without the {@code ROLE_} prefix; may be {@code null}
     * @return an unmodifiable list, empty when no section applies
     */
    public List<ManagementSection> sectionsFor(Collection<String> roles) {
        Set<String> userRoles = ManagementRoles.normalize(roles);
        return sections.stream()
                .filter(ManagementSection::enabled)
                .filter(section -> section.roles().stream().anyMatch(userRoles::contains))
                .toList();
    }
}
