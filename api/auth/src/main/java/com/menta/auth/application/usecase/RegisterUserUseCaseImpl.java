package com.menta.auth.application.usecase;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.dto.UserResult;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.application.port.out.ActivationDeliveryCipher;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.ActivationTokenGenerator;
import com.menta.auth.application.port.out.ActivationTokenHasher;
import com.menta.auth.application.port.out.ActivationTokenRepository;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.domain.crypto.Sha256Hex;
import com.menta.auth.domain.exception.ActivationRateLimitedException;
import com.menta.auth.domain.exception.DuplicateRegistrationException;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;
import java.time.Duration;
import java.time.Instant;

/**
 * Implementation of RegisterUserUseCase.
 *
 * <p>Persists the user (PENDING_ACTIVATION), issues and hashes an opaque
 * activation token, encrypts its delivery envelope, and appends exactly one
 * durable outbox event — all as application logic without framework
 * dependencies. Atomicity across these writes is NOT provided here: the
 * caller MUST wrap this use case with a transactional decorator (see
 * {@code TransactionalRegisterUserUseCase}), mirroring the login/logout/
 * refresh pattern (auth-account-activation spec: "Registro pendiente y
 * entrega durable").</p>
 */
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final ActivationTokenRepository activationTokenRepository;
    private final ActivationTokenGenerator activationTokenGenerator;
    private final ActivationTokenHasher activationTokenHasher;
    private final ActivationDeliveryCipher activationDeliveryCipher;
    private final ActivationRateLimitPort activationRateLimitPort;
    private final OutboxAppender outboxAppender;
    private final Clock clock;
    /** TTL for a freshly-issued activation token (design.md decision #3; injected, default 24h). */
    private final Duration activationTokenTtl;

    public RegisterUserUseCaseImpl(
        UserRepository userRepository,
        PasswordEncoderPort passwordEncoder,
        ActivationTokenRepository activationTokenRepository,
        ActivationTokenGenerator activationTokenGenerator,
        ActivationTokenHasher activationTokenHasher,
        ActivationDeliveryCipher activationDeliveryCipher,
        ActivationRateLimitPort activationRateLimitPort,
        OutboxAppender outboxAppender,
        Clock clock,
        Duration activationTokenTtl
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.activationTokenRepository = activationTokenRepository;
        this.activationTokenGenerator = activationTokenGenerator;
        this.activationTokenHasher = activationTokenHasher;
        this.activationDeliveryCipher = activationDeliveryCipher;
        this.activationRateLimitPort = activationRateLimitPort;
        this.outboxAppender = outboxAppender;
        this.clock = clock;
        this.activationTokenTtl = activationTokenTtl;
    }

    @Override
    public UserResult register(RegisterUserCommand command) {
        Role role = publicRole(command.role());
        Email email = Email.of(command.email());

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
        User user = User.register(email, passwordHash, role);
        User savedUser = userRepository.save(user);

        String rawActivationToken = activationTokenGenerator.generate();
        String tokenHash = activationTokenHasher.hash(rawActivationToken);
        Instant now = clock.now();
        ActivationToken activationToken = ActivationToken.issue(
            savedUser.getId(), tokenHash, now.plus(activationTokenTtl), now
        );

        DeliveryEnvelope deliveryEnvelope = activationDeliveryCipher.encrypt(
            savedUser.getEmail().getValue() + "|" + rawActivationToken
        );
        ActivationToken savedToken = activationTokenRepository.save(activationToken, deliveryEnvelope);

        outboxAppender.append(
            AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED,
            savedToken.getId().toString(),
            "{\"activationTokenId\":\"" + savedToken.getId() + "\"}"
        );

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
