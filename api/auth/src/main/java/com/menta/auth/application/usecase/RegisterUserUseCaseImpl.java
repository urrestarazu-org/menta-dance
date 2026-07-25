package com.menta.auth.application.usecase;

import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.dto.UserResult;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;

/**
 * Implementation of RegisterUserUseCase.
 * Contains application logic without framework dependencies.
 */
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoderPort passwordEncoder;

    public RegisterUserUseCaseImpl(
        UserRepository userRepository,
        PasswordEncoderPort passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserResult register(RegisterUserCommand command) {
        // Validate email
        Email email = Email.of(command.email());

        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("User with email " + email + " already exists");
        }

        // Hash password
        String passwordHash = passwordEncoder.encode(command.password());

        // Create domain entity
        User user = User.create(email, passwordHash, command.role());

        // Persist
        User savedUser = userRepository.save(user);

        // Map to result DTO
        return new UserResult(
            savedUser.getId().toString(),
            savedUser.getEmail().getValue(),
            savedUser.getRole(),
            savedUser.getStatus(),
            savedUser.getCreatedAt()
        );
    }
}
