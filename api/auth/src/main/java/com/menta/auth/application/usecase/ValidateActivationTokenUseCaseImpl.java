package com.menta.auth.application.usecase;

import com.menta.auth.application.dto.ActivateAccountCommand;
import com.menta.auth.application.port.in.ValidateActivationTokenUseCase;
import com.menta.auth.application.port.out.ActivationTokenHasher;
import com.menta.auth.application.port.out.ActivationTokenRepository;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.domain.exception.ActivationTokenInvalidException;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.ActivationTokenStatus;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;

/**
 * Read-only validation for an account activation token.
 *
 * <p>This use case deliberately performs no writes. It verifies the same
 * preconditions needed by activation so a safe HTTP method can check a link
 * without consuming its credential or changing account state.</p>
 */
public class ValidateActivationTokenUseCaseImpl implements ValidateActivationTokenUseCase {

    private final ActivationTokenRepository activationTokenRepository;
    private final ActivationTokenHasher activationTokenHasher;
    private final UserRepository userRepository;
    private final Clock clock;

    public ValidateActivationTokenUseCaseImpl(
        ActivationTokenRepository activationTokenRepository,
        ActivationTokenHasher activationTokenHasher,
        UserRepository userRepository,
        Clock clock
    ) {
        this.activationTokenRepository = activationTokenRepository;
        this.activationTokenHasher = activationTokenHasher;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    public void validate(ActivateAccountCommand command) {
        String tokenHash = activationTokenHasher.hash(command.rawToken());
        ActivationToken token = activationTokenRepository.findByHash(tokenHash)
            .orElseThrow(ActivationTokenInvalidException::new);

        if (token.statusAt(clock.now()) != ActivationTokenStatus.ACTIVE) {
            throw new ActivationTokenInvalidException();
        }

        if (userRepository.findById(token.getUserId())
            .map(user -> user.getStatus() == UserStatus.PENDING_ACTIVATION)
            .orElse(false)) {
            return;
        }

        throw new ActivationTokenInvalidException();
    }
}
