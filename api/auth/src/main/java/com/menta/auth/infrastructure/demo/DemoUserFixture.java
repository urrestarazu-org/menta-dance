package com.menta.auth.infrastructure.demo;

import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.demo.DemoIds;
import com.menta.shared.demo.DemoSeedOrder;
import com.menta.shared.domain.vo.Email;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Seeds the four demo role users (admin, instructor, student, receptionist) as ACTIVE accounts.
 *
 * <p>Each user is upserted by email and keeps a fixed id from {@link DemoIds}, so other modules can refer to
 * it. An existing user is never modified: its password is not reset. If the email exists with a different id,
 * startup aborts because the database was not created by the demo seed. Passwords come from the
 * {@code demo.users.*-password} properties (see {@code application-demo.yml}); no outbox event or email is
 * produced. The runner is intentionally not transactional.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public final class DemoUserFixture implements ApplicationRunner, Ordered {

    private static final List<DemoUser> DEMO_USERS = List.of(
        new DemoUser(DemoIds.ADMIN_USER_ID, "admin@menta.dance", Role.ADMIN, "demo.users.admin-password"),
        new DemoUser(
            DemoIds.INSTRUCTOR_USER_ID, "profesor@menta.dance", Role.INSTRUCTOR, "demo.users.instructor-password"),
        new DemoUser(DemoIds.STUDENT_USER_ID, "alumno@menta.dance", Role.STUDENT, "demo.users.student-password"),
        new DemoUser(
            DemoIds.RECEPTIONIST_USER_ID,
            "recepcion@menta.dance",
            Role.RECEPTIONIST,
            "demo.users.receptionist-password"));

    private final UserRepository userRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments arguments) {
        DEMO_USERS.forEach(this::seed);
    }

    @Override
    public int getOrder() {
        return DemoSeedOrder.AUTH;
    }

    private void seed(DemoUser demoUser) {
        Email email = Email.of(demoUser.email());
        var existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            if (!existing.get().getId().getValue().equals(demoUser.id())) {
                throw new IllegalStateException("User " + demoUser.email()
                    + " already exists with a different id; recreate the local database to seed the demo");
            }
            return;
        }
        String password = environment.getRequiredProperty(demoUser.passwordProperty());
        LocalDateTime now = LocalDateTime.now();
        userRepository.save(User.rehydrate(
            UserId.of(demoUser.id()),
            email,
            passwordEncoder.encode(password),
            demoUser.role(),
            UserStatus.ACTIVE,
            now,
            now,
            1L));
    }

    private record DemoUser(UUID id, String email, Role role, String passwordProperty) {
    }
}
