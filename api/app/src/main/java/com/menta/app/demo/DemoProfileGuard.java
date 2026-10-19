package com.menta.app.demo;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Aborts application startup when the {@code demo} profile is combined with a production-like profile.
 *
 * <p>The check runs in the constructor, so a violation fails context refresh before any runner can seed data.
 * The bean is deliberately not conditional on a profile: a typo in a profile name cannot switch it off.
 * Profile names are matched case-insensitively.
 */
@Component
public final class DemoProfileGuard {

    private static final String DEMO_PROFILE = "demo";
    private static final Set<String> PRODUCTION_LIKE_PROFILES = Set.of("prod", "production", "staging");

    /**
     * Validates the active profiles.
     *
     * @param environment source of the active profiles
     * @throws IllegalStateException when {@code demo} is active together with a production-like profile
     */
    public DemoProfileGuard(Environment environment) {
        Set<String> active = Set.copyOf(Arrays.stream(environment.getActiveProfiles())
            .map(profile -> profile.toLowerCase(Locale.ROOT))
            .toList());
        if (active.contains(DEMO_PROFILE) && active.stream().anyMatch(PRODUCTION_LIKE_PROFILES::contains)) {
            throw new IllegalStateException(
                "The 'demo' profile cannot be combined with a production-like profile (prod, production, staging)");
        }
    }
}
