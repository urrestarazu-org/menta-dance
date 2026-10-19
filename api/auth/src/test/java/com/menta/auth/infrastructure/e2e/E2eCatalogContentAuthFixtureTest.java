package com.menta.auth.infrastructure.e2e;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class E2eCatalogContentAuthFixtureTest {

    @Test
    void creates_the_local_administrator_only_when_missing() throws Exception {
        Environment environment = mock(Environment.class);
        UserRepository users = mock(UserRepository.class);
        PasswordEncoderPort passwords = mock(PasswordEncoderPort.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"e2e-catalog-content"});
        when(users.findByEmail(Email.of(E2eCatalogContentAuthFixture.ADMIN_EMAIL))).thenReturn(Optional.empty());
        when(passwords.encode(E2eCatalogContentAuthFixture.ADMIN_PASSWORD)).thenReturn("hash");

        new E2eCatalogContentAuthFixture(environment, users, passwords).run(mock());

        verify(users).save(any());
    }

    @Test
    void is_idempotent_when_the_administrator_already_exists() throws Exception {
        Environment environment = mock(Environment.class);
        UserRepository users = mock(UserRepository.class);
        PasswordEncoderPort passwords = mock(PasswordEncoderPort.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"e2e-catalog-content"});
        when(users.findByEmail(Email.of(E2eCatalogContentAuthFixture.ADMIN_EMAIL))).thenReturn(Optional.of(mock()));

        new E2eCatalogContentAuthFixture(environment, users, passwords).run(mock());

        verify(users, never()).save(any());
        verify(passwords, never()).encode(any());
    }

    @Test
    void rejects_a_production_profile_before_writing_fixture_data() {
        Environment environment = mock(Environment.class);
        UserRepository users = mock(UserRepository.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"e2e-catalog-content", "production"});

        E2eCatalogContentAuthFixture fixture =
            new E2eCatalogContentAuthFixture(environment, users, mock(PasswordEncoderPort.class));

        assertThatThrownBy(() -> fixture.run(mock()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cannot run in a production profile");
        verify(users, never()).findByEmail(any());
    }
}
