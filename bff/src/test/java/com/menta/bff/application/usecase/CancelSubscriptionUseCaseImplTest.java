package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.menta.bff.application.dto.CancelledSubscription;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import com.menta.bff.application.usecase.CancelSubscriptionResult.Outcome;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/** Unit tests for {@link CancelSubscriptionUseCaseImpl} (#363): the student cancels their own subscription. */
@ExtendWith(MockitoExtension.class)
@DisplayName("CancelSubscriptionUseCase")
class CancelSubscriptionUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";

    @Mock
    private SubscriptionApiClient subscriptionApiClient;

    private CancelSubscriptionUseCase useCase;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        useCase = new CancelSubscriptionUseCaseImpl(
                subscriptionApiClient, ZoneId.of("America/Argentina/Buenos_Aires"));
        logger = (Logger) LoggerFactory.getLogger(CancelSubscriptionUseCaseImpl.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @ParameterizedTest(name = "a request without a usable token ({0}) is unavailable and makes no call")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void withoutToken(String token) {
        assertThat(useCase.execute(token)).isEqualTo(CancelSubscriptionResult.of(Outcome.UNAVAILABLE));
        verifyNoInteractions(subscriptionApiClient);
    }

    @Test
    @DisplayName("a cancelled subscription reports the end of the access as dd/MM/yyyy in the display zone")
    void cancelled() {
        // 2026-12-01T01:30Z is still 30/11 in Buenos Aires.
        when(subscriptionApiClient.cancelSubscription(TOKEN))
                .thenReturn(new CancelledSubscription(Instant.parse("2026-12-01T01:30:00Z")));

        assertThat(useCase.execute(TOKEN))
                .isEqualTo(new CancelSubscriptionResult(Outcome.CANCELLED, "30/11/2026"));
    }

    @Test
    @DisplayName("nothing to cancel is its own outcome")
    void nothingToCancel() {
        when(subscriptionApiClient.cancelSubscription(TOKEN))
                .thenThrow(new SubscriptionApiClient.NoCancellableSubscriptionException("none"));

        assertThat(useCase.execute(TOKEN)).isEqualTo(CancelSubscriptionResult.of(Outcome.NOTHING_TO_CANCEL));
    }

    @Test
    @DisplayName("a conflict is its own outcome, so the student can retry")
    void conflict() {
        when(subscriptionApiClient.cancelSubscription(TOKEN))
                .thenThrow(new SubscriptionApiClient.SubscriptionConflictException("conflict"));

        assertThat(useCase.execute(TOKEN)).isEqualTo(CancelSubscriptionResult.of(Outcome.CONFLICT));
    }

    @Test
    @DisplayName("an unavailable Billing API is the unavailable outcome and the log never carries the token")
    void unavailable() {
        when(subscriptionApiClient.cancelSubscription(TOKEN))
                .thenThrow(new BillingApiClient.ServiceUnavailableException("down " + TOKEN));

        assertThat(useCase.execute(TOKEN)).isEqualTo(CancelSubscriptionResult.of(Outcome.UNAVAILABLE));
        assertThat(logs.list).anySatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN))
                .noneMatch(event -> event.getFormattedMessage().contains(TOKEN));
    }
}
