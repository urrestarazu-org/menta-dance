package com.menta.auth.infrastructure.e2e;

import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;
import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Creates the local-only administrator required by the catalog/content E2E
 * journey. It is intentionally infrastructure wiring: Auth remains owner of
 * its aggregate and no other module reaches its persistence.
 */
@Component
@Profile("e2e-catalog-content")
public final class E2eCatalogContentAuthFixture implements ApplicationRunner, Ordered {

    public static final String ADMIN_EMAIL = "catalog.e2e.admin@menta.local";
    public static final String ADMIN_PASSWORD = "CatalogE2eAdmin123!";
    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production", "staging");

    private final Environment environment;
    private final UserRepository userRepository;
    private final PasswordEncoderPort passwordEncoder;

    public E2eCatalogContentAuthFixture(
        Environment environment, UserRepository userRepository, PasswordEncoderPort passwordEncoder
    ) {
        this.environment = environment;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        rejectProductionProfiles();
        Email email = Email.of(ADMIN_EMAIL);
        if (userRepository.findByEmail(email).isEmpty()) {
            userRepository.save(User.create(email, passwordEncoder.encode(ADMIN_PASSWORD), Role.ADMIN));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private void rejectProductionProfiles() {
        boolean productionActive = Arrays.stream(environment.getActiveProfiles())
            .map(String::toLowerCase)
            .anyMatch(PRODUCTION_PROFILES::contains);
        if (productionActive) {
            throw new IllegalStateException("E2E catalog/content fixtures cannot run in a production profile");
        }
    }
}
