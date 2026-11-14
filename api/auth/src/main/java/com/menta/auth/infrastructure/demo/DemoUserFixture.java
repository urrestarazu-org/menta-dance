package com.menta.auth.infrastructure.demo;

import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserProfile;
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
 * it. Users it creates are flagged as demo, so no outgoing email is addressed to them. The instructor also gets the
 * profile "Profesor Demo", so clients can show the name of the account that teaches the demo courses; the other
 * three users have no profile. An existing user is never modified, with one exception: an existing instructor
 * without a profile (seeded before #749) gets that profile; one that already has a profile keeps it, whatever its
 * names. Even then its password is not reset and its flag is left as is (a demo database seeded before V28
 * must be recreated, or flagged by hand). If the email exists with a different id, startup aborts because the
 * database was not created by the demo seed. Passwords come from the
 * {@code demo.users.*-password} properties (see {@code application-demo.yml}); no outbox event or email is
 * produced. The runner is intentionally not transactional.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public final class DemoUserFixture implements ApplicationRunner, Ordered {

    /** The demo instructor's name; built through the domain record, so it passes the same validation. */
    private static final UserProfile INSTRUCTOR_PROFILE = new UserProfile("Profesor", "Demo", null, null);

    private static final List<DemoUser> DEMO_USERS = List.of(
        new DemoUser(DemoIds.ADMIN_USER_ID, "admin@menta.dance", Role.ADMIN, "demo.users.admin-password", null),
        new DemoUser(
            DemoIds.INSTRUCTOR_USER_ID,
            "profesor@menta.dance",
            Role.INSTRUCTOR,
            "demo.users.instructor-password",
            INSTRUCTOR_PROFILE),
        new DemoUser(
            DemoIds.STUDENT_USER_ID, "alumno@menta.dance", Role.STUDENT, "demo.users.student-password", null),
        new DemoUser(
            DemoIds.RECEPTIONIST_USER_ID,
            "recepcion@menta.dance",
            Role.RECEPTIONIST,
            "demo.users.receptionist-password",
            null));

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
            addMissingProfile(existing.get(), demoUser.profile());
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
            1L,
            demoUser.profile(),
            true));
    }

    /**
     * Gives an existing user the demo profile only when it has none; everything else, password and demo flag
     * included, is kept as stored. A profile someone already has, even with other names, is never overwritten.
     */
    private void addMissingProfile(User user, UserProfile profile) {
        if (profile == null || user.getProfile().isPresent()) {
            return;
        }
        userRepository.save(User.rehydrate(
            user.getId(),
            user.getEmail(),
            user.getPasswordHash(),
            user.getRole(),
            user.getStatus(),
            user.getCreatedAt(),
            LocalDateTime.now(),
            user.getTokenVersion(),
            profile,
            user.isDemo(),
            user.isMustSetPassword()));
    }

    /** A demo account; {@code profile} is null for the users that have none. */
    private record DemoUser(UUID id, String email, Role role, String passwordProperty, UserProfile profile) {
    }
}
