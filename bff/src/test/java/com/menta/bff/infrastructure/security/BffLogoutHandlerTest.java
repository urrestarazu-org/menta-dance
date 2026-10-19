package com.menta.bff.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.menta.bff.application.usecase.LogoutOutcome;
import com.menta.bff.application.usecase.LogoutUseCase;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * This handler must be fail-open: Auth API revocation failing must never
 * block the local session invalidation that Spring Security performs next,
 * and it must never claim a revocation that did not happen.
 */
@ExtendWith(MockitoExtension.class)
class BffLogoutHandlerTest {

    @Mock private LogoutUseCase logoutUseCase;
    @InjectMocks private BffLogoutHandler handler;

    private Logger handlerLogger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void attachAppender() {
        handlerLogger = (Logger) LoggerFactory.getLogger(BffLogoutHandler.class);
        logs = new ListAppender<>();
        logs.start();
        handlerLogger.addAppender(logs);
    }

    @AfterEach
    void detachAppender() {
        handlerLogger.detachAppender(logs);
    }

    private static Authentication authentication() {
        return new UsernamePasswordAuthenticationToken("student@example.com", "SecurePass123!");
    }

    private void logout() {
        handler.logout(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication());
    }

    private List<ILoggingEvent> events(Level level) {
        return logs.list.stream().filter(event -> event.getLevel() == level).toList();
    }

    @Test
    @DisplayName("delegates revocation to the use case")
    void delegates_revocation_to_the_use_case() {
        when(logoutUseCase.execute()).thenReturn(LogoutOutcome.REVOKED);

        logout();

        verify(logoutUseCase).execute();
    }

    @Test
    @DisplayName("logs INFO que el refresh token fue revocado solo cuando el resultado es REVOKED")
    void logs_revoked_only_when_the_refresh_token_was_revoked() {
        when(logoutUseCase.execute()).thenReturn(LogoutOutcome.REVOKED);

        logout();

        assertThat(events(Level.INFO))
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("refresh token revoked"));
        assertThat(events(Level.WARN)).isEmpty();
    }

    @Test
    @DisplayName("logs WARN y no afirma la revocación cuando el resultado es REVOCATION_FAILED")
    void warns_and_does_not_claim_revocation_when_revocation_failed() {
        when(logoutUseCase.execute()).thenReturn(LogoutOutcome.REVOCATION_FAILED);

        logout();

        assertThat(events(Level.WARN))
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("revocation failed") && message.contains("cleared"));
        assertThat(logs.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("refresh token revoked"));
    }

    @Test
    @DisplayName("no afirma la revocación cuando no había sesión (NO_SESSION)")
    void does_not_claim_revocation_when_there_was_no_session() {
        when(logoutUseCase.execute()).thenReturn(LogoutOutcome.NO_SESSION);

        logout();

        assertThat(logs.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("refresh token revoked"));
        assertThat(events(Level.WARN)).isEmpty();
    }

    @Test
    @DisplayName("swallows a use case failure instead of propagating it (fail-open)")
    void swallows_a_use_case_failure_instead_of_propagating_it() {
        doThrow(new RuntimeException("Auth API unavailable")).when(logoutUseCase).execute();

        assertThatCode(this::logout).doesNotThrowAnyException();

        assertThat(logs.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("refresh token revoked"));
    }
}
