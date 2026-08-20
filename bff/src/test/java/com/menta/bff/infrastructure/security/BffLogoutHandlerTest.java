package com.menta.bff.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.menta.bff.application.usecase.LogoutUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * This handler must be fail-open: Auth API revocation failing must never
 * block the local session invalidation that Spring Security performs next.
 */
@ExtendWith(MockitoExtension.class)
class BffLogoutHandlerTest {

    @Mock private LogoutUseCase logoutUseCase;
    @InjectMocks private BffLogoutHandler handler;

    private static Authentication authentication() {
        return new UsernamePasswordAuthenticationToken("student@example.com", "SecurePass123!");
    }

    @Test
    @DisplayName("delegates revocation to the use case")
    void delegates_revocation_to_the_use_case() {
        handler.logout(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication());

        verify(logoutUseCase).execute();
    }

    @Test
    @DisplayName("swallows a use case failure instead of propagating it (fail-open)")
    void swallows_a_use_case_failure_instead_of_propagating_it() {
        doThrow(new RuntimeException("Auth API unavailable")).when(logoutUseCase).execute();

        assertThatCode(() ->
                handler.logout(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication()))
                .doesNotThrowAnyException();
    }
}
