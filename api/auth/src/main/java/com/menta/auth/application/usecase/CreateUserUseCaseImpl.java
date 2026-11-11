package com.menta.auth.application.usecase;

import com.menta.auth.application.dto.CreateUserCommand;
import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.application.port.in.CreateUserUseCase;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.port.out.UserAuditRepository;
import com.menta.auth.domain.exception.EmailAlreadyRegisteredException;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserProfile;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link CreateUserUseCase}.
 *
 * <p>The account is born pending with a random, unusable password hash that is different for every account and
 * never disclosed, and marked as still needing its holder's own password. The activation email goes out through
 * {@link ActivationTokenIssuer}, the same path as public registration, and the creation is audited. Nothing here
 * is atomic on its own: the caller MUST wrap this use case in a transaction so the account, the token, the outbox
 * event and the audit entry commit together.
 */
@RequiredArgsConstructor
public class CreateUserUseCaseImpl implements CreateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final ActivationTokenIssuer activationTokenIssuer;
    private final UserAuditRepository userAuditRepository;
    private final Clock clock;

    @Override
    public UserSummary create(CreateUserCommand command) {
        Role role = staffRole(command.role());
        Email email = Email.of(command.email());
        UserProfile profile = new UserProfile(command.firstName(), command.lastName(), null, null);

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User saved = userRepository.save(User.invite(email, passwordEncoder.encodeUnusable(), role, profile));

        Instant now = clock.now();
        activationTokenIssuer.issueFor(saved, now);
        userAuditRepository.append(
            saved.getId(), command.actorId(), UserAuditRepository.USER_CREATED, null, role.name(), now
        );
        return UserSummary.from(saved);
    }

    /** Students register themselves; an administrator creates only the staff roles. */
    private static Role staffRole(Role requestedRole) {
        if (requestedRole == Role.STUDENT) {
            throw new IllegalArgumentException("An administrator cannot create a STUDENT account");
        }
        return requestedRole;
    }
}
