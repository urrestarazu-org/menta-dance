package com.menta.auth.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletResponse;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ActivationRequestLoggingFilterTest {

    @Test
    void access_log_redacts_activation_tokens_and_never_logs_other_sensitive_request_values() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ActivationRequestLoggingFilter.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            String token = "raw-activation-token";
            String tokenHash = "a".repeat(64);
            String email = "person@example.com";
            String password = "CorrectHorseBatteryStaple";
            String ciphertext = "encrypted-delivery-envelope";
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/activate/" + token);
            request.setQueryString(
                "tokenHash=" + tokenHash + "&email=" + email + "&password=" + password + "&ciphertext=" + ciphertext
            );

            new ActivationRequestLoggingFilter().doFilter(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> ((HttpServletResponse) ignoredResponse).setStatus(204));

            String logs = events.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
            assertThat(logs).contains("/api/v1/auth/activate/[REDACTED]");
            assertThat(logs)
                .doesNotContain(token)
                .doesNotContain(tokenHash)
                .doesNotContain(email)
                .doesNotContain(password)
                .doesNotContain(ciphertext);
        } finally {
            logger.detachAppender(events);
        }
    }
}
