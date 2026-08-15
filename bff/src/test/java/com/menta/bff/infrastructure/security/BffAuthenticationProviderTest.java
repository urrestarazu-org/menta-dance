package com.menta.bff.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.menta.bff.application.dto.LoginCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.usecase.LoginUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * The provider is the entry point Spring Security calls on form login. It must
 * orchestrate nothing itself: the login flow — call the Auth API, then persist
 * the token pair in the server-side session — already lives in
 * {@link LoginUseCase}, and duplicating it here means two places can drift on
 * how tokens are custodied.
 */
@ExtendWith(MockitoExtension.class)
class BffAuthenticationProviderTest {

    private static final String EMAIL = "student@example.com";
    private static final String PASSWORD = "SecurePass123!";

    @Mock private LoginUseCase loginUseCase;
    @InjectMocks private BffAuthenticationProvider provider;

    private static Authentication formLogin() {
        return new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD);
    }

    @Test
    @DisplayName("Delegates the whole login flow to the use case")
    void delegates_credentials_to_the_login_use_case() {
        provider.authenticate(formLogin());

        ArgumentCaptor<LoginCommand> captor = ArgumentCaptor.forClass(LoginCommand.class);
        verify(loginUseCase).execute(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo(EMAIL);
        assertThat(captor.getValue().password()).isEqualTo(PASSWORD);
    }

    @Test
    void returns_an_authenticated_principal_named_by_email() {
        Authentication result = provider.authenticate(formLogin());

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo(EMAIL);
        assertThat(result.getAuthorities()).extracting(Object::toString).contains("ROLE_USER");
    }

    @Test
    void never_carries_the_password_into_the_security_context() {
        // The returned Authentication is stored in the session-backed security
        // context; keeping credentials on it would persist the password well
        // past the request that supplied it.
        Authentication result = provider.authenticate(formLogin());

        assertThat(result.getCredentials()).isNull();
    }

    @Test
    void maps_invalid_credentials_to_bad_credentials() {
        doThrow(new AuthApiClient.AuthenticationException("401"))
            .when(loginUseCase).execute(any(LoginCommand.class));

        assertThatThrownBy(() -> provider.authenticate(formLogin()))
            .isInstanceOf(BadCredentialsException.class)
            .hasMessage("Invalid email or password");
    }

    @Test
    void maps_an_unavailable_auth_api_to_an_internal_service_error() {
        // Distinct from bad credentials on purpose: telling a user their
        // password is wrong when the Auth API is simply down sends them to
        // reset a credential that was never the problem.
        doThrow(new AuthApiClient.ServiceUnavailableException("503"))
            .when(loginUseCase).execute(any(LoginCommand.class));

        assertThatThrownBy(() -> provider.authenticate(formLogin()))
            .isInstanceOf(InternalAuthenticationServiceException.class);
    }

    @Test
    void supports_the_form_login_authentication_token() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
    }

    // -- ADR-0035: trusted client origin propagation -----------------------

    @Test
    @DisplayName("Carries the captured client origin into the login command")
    void forwards_the_client_origin_captured_by_the_details_source() {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD);
        authentication.setDetails(new ClientAuthenticationDetails("203.0.113.9"));

        provider.authenticate(authentication);

        ArgumentCaptor<LoginCommand> captor = ArgumentCaptor.forClass(LoginCommand.class);
        verify(loginUseCase).execute(captor.capture());
        assertThat(captor.getValue().clientAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    void degrades_to_no_origin_when_details_are_absent() {
        // A programmatic authentication never passes through the filter that
        // builds the details. Sending no origin makes the Auth API observe the
        // BFF as the peer — the pre-ADR-0035 behaviour, and strictly safer
        // than forwarding a value we cannot vouch for.
        provider.authenticate(formLogin());

        ArgumentCaptor<LoginCommand> captor = ArgumentCaptor.forClass(LoginCommand.class);
        verify(loginUseCase).execute(captor.capture());
        assertThat(captor.getValue().clientAddress()).isNull();
    }

    @Test
    void ignores_details_of_an_unexpected_type() {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD);
        authentication.setDetails("192.168.1.1");

        provider.authenticate(authentication);

        ArgumentCaptor<LoginCommand> captor = ArgumentCaptor.forClass(LoginCommand.class);
        verify(loginUseCase).execute(captor.capture());
        assertThat(captor.getValue().clientAddress()).isNull();
    }
}
