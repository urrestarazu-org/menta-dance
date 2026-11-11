package com.menta.auth.application.usecase;

import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.dto.UserResult;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.domain.crypto.Sha256Hex;
import com.menta.auth.domain.exception.ActivationRateLimitedException;
import com.menta.auth.domain.exception.DuplicateRegistrationException;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserProfile;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;
import java.time.Instant;

/**
 * Implementation of RegisterUserUseCase.
 *
 * <p>Persists the user (PENDING_ACTIVATION) and issues the activation token and
 * its durable outbox event through {@link ActivationTokenIssuer} — all as
 * application logic without framework dependencies. Atomicity across these writes is NOT provided here: the
 * caller MUST wrap this use case with a transactional decorator (see
 * {@code TransactionalRegisterUserUseCase}), mirroring the login/logout/
 * refresh pattern (auth-account-activation spec: "Registro pendiente y
 * entrega durable").</p>
 */
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final ActivationRateLimitPort activationRateLimitPort;
    private final ActivationTokenIssuer activationTokenIssuer;
    private final Clock clock;

    public RegisterUserUseCaseImpl(
        UserRepository userRepository,
        PasswordEncoderPort passwordEncoder,
        ActivationRateLimitPort activationRateLimitPort,
        ActivationTokenIssuer activationTokenIssuer,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.activationRateLimitPort = activationRateLimitPort;
        this.activationTokenIssuer = activationTokenIssuer;
        this.clock = clock;
    }

    @Override
    public UserResult register(RegisterUserCommand command) {
        Role role = publicRole(command.role());
        Email email = Email.of(command.email());
        UserProfile profile = new UserProfile(
            command.firstName(), command.lastName(), command.phone(), command.birthDate()
        );

        RateLimitDecision decision = activationRateLimitPort.consume(
            emailFingerprint(email), command.clientFingerprint()
        );
        if (!decision.isAllowed()) {
            throw new ActivationRateLimitedException(decision.getRetryAfter());
        }

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateRegistrationException();
        }

        String passwordHash = passwordEncoder.encode(command.password());
        User user = User.register(email, passwordHash, role, profile);
        User savedUser = userRepository.save(user);

        activationTokenIssuer.issueFor(savedUser, clock.now());

        return new UserResult(
            savedUser.getId().toString(),
            savedUser.getEmail().getValue(),
            savedUser.getRole(),
            savedUser.getStatus(),
            savedUser.getCreatedAt()
        );
    }

    private Role publicRole(Role requestedRole) {
        if (requestedRole == null || requestedRole == Role.STUDENT) {
            return Role.STUDENT;
        }
        throw new IllegalArgumentException("Public registration only supports the STUDENT role");
    }

    /**
     * Non-reversible SHA-256 fingerprint of the email, derived here (not by
     * infrastructure) because the raw email is already available on the
     * command; only the client fingerprint requires HTTP-layer derivation
     * (design.md "Puertos principales").
     */
    private String emailFingerprint(Email email) {
        return Sha256Hex.hash(email.getValue());
    }
}
