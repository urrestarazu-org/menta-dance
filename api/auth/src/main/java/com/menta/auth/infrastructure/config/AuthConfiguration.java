package com.menta.auth.infrastructure.config;

import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.usecase.RegisterUserUseCaseImpl;
import com.menta.auth.domain.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Spring configuration for Auth module.
 * Infrastructure layer - wires up use cases and adapters.
 */
@Configuration
public class AuthConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RegisterUserUseCase registerUserUseCase(
        UserRepository userRepository,
        PasswordEncoderPort passwordEncoder
    ) {
        return new RegisterUserUseCaseImpl(userRepository, passwordEncoder);
    }
}
