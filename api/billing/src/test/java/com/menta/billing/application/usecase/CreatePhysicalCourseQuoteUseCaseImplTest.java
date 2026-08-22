package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.CreatePhysicalCourseQuoteCommand;
import com.menta.billing.application.dto.PhysicalCourseQuoteResult;
import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort;
import com.menta.billing.application.port.out.PhysicalCoursePricingRepository;
import com.menta.billing.application.port.out.PhysicalCourseQuoteRepository;
import com.menta.billing.domain.exception.IndividualSurchargeTooSmallException;
import com.menta.billing.domain.exception.NoScheduledSessionsException;
import com.menta.billing.domain.exception.PhysicalCoursePricingNotFoundException;
import com.menta.billing.domain.exception.PhysicalSessionNotFoundException;
import com.menta.billing.domain.exception.SelectedSessionNotAllowedException;
import com.menta.billing.domain.exception.SelectedSessionRequiredException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PhysicalCoursePricing;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import com.menta.billing.domain.model.PurchaseType;
import com.menta.billing.domain.model.QuoteAvailability;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreatePhysicalCourseQuoteUseCaseImplTest {

    private static final String COURSE_ID = "course-1";
    // September 2026 has 30 days, October 2026 has 31 -- a normative pair per the DoD.
    private static final Instant SEPTEMBER_NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final Instant OCTOBER_NOW = Instant.parse("2026-10-15T12:00:00Z");

    private PhysicalCoursePricingRepository pricingRepository;
    private PhysicalCourseAvailabilityPort availabilityPort;
    private PhysicalCourseQuoteRepository quoteRepository;
    private Clock clock;
    private CreatePhysicalCourseQuoteUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        pricingRepository = mock(PhysicalCoursePricingRepository.class);
        availabilityPort = mock(PhysicalCourseAvailabilityPort.class);
        quoteRepository = mock(PhysicalCourseQuoteRepository.class);
        clock = mock(Clock.class);
        useCase = new CreatePhysicalCourseQuoteUseCaseImpl(pricingRepository, availabilityPort, quoteRepository, clock);
        when(quoteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static PhysicalCoursePricing pricing(String monthlyPrice, String surcharge) {
        return PhysicalCoursePricing.reconstitute(
            COURSE_ID, Money.of(new BigDecimal(monthlyPrice), "ARS"), new BigDecimal(surcharge), 1,
            Instant.parse("2026-01-01T00:00:00Z")
        );
    }

    private static ScheduledSessionSnapshot session(String id, String scheduledAt, int availableSpots) {
        return new ScheduledSessionSnapshot(id, Instant.parse(scheduledAt), availableSpots);
    }

    // ---- Escenario 1: MONTHLY ----

    @Test
    void monthly_quote_persists_the_monthly_amount_with_no_selected_session() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(pricing("150.00", "10")));
        when(availabilityPort.findScheduledSessions(
            eq(COURSE_ID), eq(Instant.parse("2026-09-01T00:00:00Z")), eq(Instant.parse("2026-10-01T00:00:00Z"))
        )).thenReturn(List.of(
            session("s1", "2026-09-03T20:00:00Z", 5),
            session("s2", "2026-09-10T20:00:00Z", 0)
        ));

        PhysicalCourseQuoteResult result =
            useCase.create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.MONTHLY, null));

        assertThat(result.purchaseType()).isEqualTo(PurchaseType.MONTHLY);
        assertThat(result.amount()).isEqualByComparingTo("150.00");
        assertThat(result.scheduledSessionCount()).isEqualTo(2);
        assertThat(result.selectedSessionId()).isNull();
        assertThat(result.availability()).isEqualTo(QuoteAvailability.AVAILABLE);
    }

    @Test
    void monthly_quote_reports_unavailable_when_no_session_has_spots() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(pricing("150.00", "10")));
        when(availabilityPort.findScheduledSessions(eq(COURSE_ID), any(), any())).thenReturn(List.of(
            session("s1", "2026-09-03T20:00:00Z", 0)
        ));

        PhysicalCourseQuoteResult result =
            useCase.create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.MONTHLY, null));

        assertThat(result.availability()).isEqualTo(QuoteAvailability.UNAVAILABLE);
    }

    @Test
    void september_and_october_2026_both_resolve_to_their_own_calendar_month_bounds() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW).thenReturn(OCTOBER_NOW);
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(pricing("150.00", "10")));
        when(availabilityPort.findScheduledSessions(
            eq(COURSE_ID), eq(Instant.parse("2026-09-01T00:00:00Z")), eq(Instant.parse("2026-10-01T00:00:00Z"))
        )).thenReturn(List.of(session("s1", "2026-09-03T20:00:00Z", 5)));
        when(availabilityPort.findScheduledSessions(
            eq(COURSE_ID), eq(Instant.parse("2026-10-01T00:00:00Z")), eq(Instant.parse("2026-11-01T00:00:00Z"))
        )).thenReturn(List.of(
            session("s2", "2026-10-03T20:00:00Z", 5), session("s3", "2026-10-20T20:00:00Z", 5)
        ));

        PhysicalCourseQuoteResult september =
            useCase.create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.MONTHLY, null));
        PhysicalCourseQuoteResult october =
            useCase.create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.MONTHLY, null));

        assertThat(september.scheduledSessionCount()).isEqualTo(1);
        assertThat(october.scheduledSessionCount()).isEqualTo(2);
    }

    @Test
    void monthly_quote_with_a_selected_session_id_is_rejected() {
        assertThatThrownBy(() ->
            useCase.create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.MONTHLY, "session-1"))
        ).isInstanceOf(SelectedSessionNotAllowedException.class);
        verifyNoInteractions(pricingRepository, availabilityPort, quoteRepository);
    }

    // ---- Escenario 2: INDIVIDUAL ----

    @Test
    void individual_quote_persists_a_single_session_id_but_keeps_the_period_session_count_as_divisor() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(pricing("100.00", "10")));
        when(availabilityPort.findScheduledSessions(
            eq(COURSE_ID), eq(Instant.parse("2026-08-01T00:00:00Z")), eq(Instant.parse("2026-11-01T00:00:00Z"))
        )).thenReturn(List.of(
            session("s1", "2026-09-03T20:00:00Z", 5),
            session("s2", "2026-09-10T20:00:00Z", 5),
            session("s3", "2026-09-17T20:00:00Z", 0)
        ));

        PhysicalCourseQuoteResult result =
            useCase.create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.INDIVIDUAL, "s3"));

        assertThat(result.purchaseType()).isEqualTo(PurchaseType.INDIVIDUAL);
        assertThat(result.selectedSessionId()).isEqualTo("s3");
        assertThat(result.scheduledSessionCount()).isEqualTo(3);
        // 100.00 / 3 sessions = 33.3333...; +10% = 36.666...; rounds to 36.67
        assertThat(result.amount()).isEqualByComparingTo("36.67");
        assertThat(result.availability()).isEqualTo(QuoteAvailability.UNAVAILABLE);
    }

    @Test
    void individual_quote_missing_selected_session_id_is_rejected_before_touching_any_port() {
        assertThatThrownBy(() ->
            useCase.create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.INDIVIDUAL, null))
        ).isInstanceOf(SelectedSessionRequiredException.class);
        verifyNoInteractions(pricingRepository, availabilityPort, quoteRepository);
    }

    @Test
    void individual_quote_for_a_session_belonging_to_a_different_month_uses_that_sessions_own_period() {
        // Quoting on Oct 15 for a session actually scheduled in September (near a month
        // boundary) must use September's own session count, not October's.
        when(clock.now()).thenReturn(OCTOBER_NOW);
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(pricing("100.00", "10")));
        when(availabilityPort.findScheduledSessions(
            eq(COURSE_ID), eq(Instant.parse("2026-09-01T00:00:00Z")), eq(Instant.parse("2026-12-01T00:00:00Z"))
        )).thenReturn(List.of(
            session("sSep", "2026-09-28T20:00:00Z", 5),
            session("sOct1", "2026-10-05T20:00:00Z", 5),
            session("sOct2", "2026-10-12T20:00:00Z", 5)
        ));

        PhysicalCourseQuoteResult result =
            useCase.create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.INDIVIDUAL, "sSep"));

        assertThat(result.scheduledSessionCount()).isEqualTo(1);
    }

    @Test
    void individual_quote_for_an_unknown_selected_session_returns_not_found() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(pricing("100.00", "10")));
        when(availabilityPort.findScheduledSessions(eq(COURSE_ID), any(), any())).thenReturn(List.of(
            session("s1", "2026-09-03T20:00:00Z", 5)
        ));

        assertThatThrownBy(() ->
            useCase.create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.INDIVIDUAL, "unknown"))
        ).isInstanceOf(PhysicalSessionNotFoundException.class);
        verifyNoInteractions(quoteRepository);
    }

    @Test
    void individual_quote_with_a_surcharge_invisible_after_rounding_is_rejected_and_nothing_is_persisted() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(pricing("10.00", "0.5")));
        when(availabilityPort.findScheduledSessions(eq(COURSE_ID), any(), any())).thenReturn(
            java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> session("s" + i, "2026-09-01T00:00:0" + (i % 10) + "Z", 5))
                .toList()
        );

        assertThatThrownBy(() ->
            useCase.create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.INDIVIDUAL, "s0"))
        ).isInstanceOf(IndividualSurchargeTooSmallException.class);
        verifyNoInteractions(quoteRepository);
    }

    // ---- Escenario 3: sin sesiones ----

    @Test
    void monthly_quote_with_no_scheduled_sessions_is_rejected_and_creates_no_quote() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(pricing("150.00", "10")));
        when(availabilityPort.findScheduledSessions(eq(COURSE_ID), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() ->
            useCase.create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.MONTHLY, null))
        ).isInstanceOf(NoScheduledSessionsException.class);
        verifyNoInteractions(quoteRepository);
    }

    // ---- Escenario 4: sin cupo proyectado ----

    @Test
    void individual_quote_reports_unavailable_when_the_selected_session_has_no_spots() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(pricing("100.00", "10")));
        when(availabilityPort.findScheduledSessions(eq(COURSE_ID), any(), any())).thenReturn(List.of(
            session("s1", "2026-09-03T20:00:00Z", 0)
        ));

        PhysicalCourseQuoteResult result =
            useCase.create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.INDIVIDUAL, "s1"));

        assertThat(result.availability()).isEqualTo(QuoteAvailability.UNAVAILABLE);
    }

    // ---- Pricing not found ----

    @Test
    void a_course_with_no_published_pricing_is_rejected_before_touching_availability_or_the_repository() {
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            useCase.create(new CreatePhysicalCourseQuoteCommand(COURSE_ID, PurchaseType.MONTHLY, null))
        ).isInstanceOf(PhysicalCoursePricingNotFoundException.class);
        verifyNoInteractions(availabilityPort, quoteRepository);
    }
}
