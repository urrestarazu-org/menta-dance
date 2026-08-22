package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.CreatePhysicalCourseQuoteCommand;
import com.menta.billing.application.dto.PhysicalCourseQuoteResult;
import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import com.menta.billing.application.port.in.CreatePhysicalCourseQuoteUseCase;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort;
import com.menta.billing.application.port.out.PhysicalCoursePricingRepository;
import com.menta.billing.application.port.out.PhysicalCourseQuoteRepository;
import com.menta.billing.domain.exception.NoScheduledSessionsException;
import com.menta.billing.domain.exception.PhysicalCoursePricingNotFoundException;
import com.menta.billing.domain.exception.PhysicalSessionNotFoundException;
import com.menta.billing.domain.exception.SelectedSessionNotAllowedException;
import com.menta.billing.domain.exception.SelectedSessionRequiredException;
import com.menta.billing.domain.model.PhysicalCoursePricing;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import com.menta.billing.domain.model.PurchaseType;
import com.menta.billing.domain.model.QuoteAvailability;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Implementation of {@link CreatePhysicalCourseQuoteUseCase} (US-BILLING-006).
 *
 * <p>Periods are calendar months computed against {@link ZoneOffset#UTC} —
 * the same zone Physical's own session-scheduling use cases already use
 * ({@code BatchCreatePhysicalSessionsUseCaseImpl}, {@code
 * CreatePhysicalSessionUseCaseImpl}, {@code UpdatePhysicalSessionUseCaseImpl}).
 * No project-wide {@code ZoneId} bean/convention exists beyond that, so UTC
 * was reused rather than introducing a second one. The client never supplies
 * a period or "now" — {@link Clock} fixes the authoritative instant.</p>
 *
 * <p>For MONTHLY (escenario 1), the period is simply the calendar month
 * containing "now". For INDIVIDUAL (escenario 2), the period that determines
 * {@code scheduledSessionCount} is the calendar month containing the
 * <em>selected session's</em> {@code scheduledAt} — which may differ from
 * "now"'s month near a month boundary. Since {@link
 * PhysicalCourseAvailabilityPort} only supports a bounded range query (no
 * find-by-id), the session is first located within a three-calendar-month
 * window (previous/current/next relative to "now") — wide enough to always
 * contain a session quoted "near a month change" — and the exact period is
 * then derived from its own {@code scheduledAt} and used to filter that same
 * already-fetched window down to the sessions that truly belong to it.</p>
 */
public class CreatePhysicalCourseQuoteUseCaseImpl implements CreatePhysicalCourseQuoteUseCase {

    private final PhysicalCoursePricingRepository pricingRepository;
    private final PhysicalCourseAvailabilityPort availabilityPort;
    private final PhysicalCourseQuoteRepository quoteRepository;
    private final Clock clock;

    public CreatePhysicalCourseQuoteUseCaseImpl(
        PhysicalCoursePricingRepository pricingRepository, PhysicalCourseAvailabilityPort availabilityPort,
        PhysicalCourseQuoteRepository quoteRepository, Clock clock
    ) {
        this.pricingRepository = pricingRepository;
        this.availabilityPort = availabilityPort;
        this.quoteRepository = quoteRepository;
        this.clock = clock;
    }

    @Override
    public PhysicalCourseQuoteResult create(CreatePhysicalCourseQuoteCommand command) {
        validateShape(command);

        PhysicalCoursePricing pricing = pricingRepository.findByCourseId(command.courseId())
            .orElseThrow(PhysicalCoursePricingNotFoundException::new);

        Instant now = clock.now();
        PhysicalCourseQuote quote = command.purchaseType() == PurchaseType.MONTHLY
            ? buildMonthly(command, pricing, now)
            : buildIndividual(command, pricing, now);

        return PhysicalCourseQuoteResultMapper.toResult(quoteRepository.save(quote));
    }

    private static void validateShape(CreatePhysicalCourseQuoteCommand command) {
        boolean hasSelectedSession = command.selectedSessionId() != null && !command.selectedSessionId().isBlank();
        if (command.purchaseType() == PurchaseType.INDIVIDUAL && !hasSelectedSession) {
            throw new SelectedSessionRequiredException();
        }
        if (command.purchaseType() == PurchaseType.MONTHLY && hasSelectedSession) {
            throw new SelectedSessionNotAllowedException();
        }
    }

    private PhysicalCourseQuote buildMonthly(
        CreatePhysicalCourseQuoteCommand command, PhysicalCoursePricing pricing, Instant now
    ) {
        Instant periodStart = calendarMonthStart(now);
        Instant periodEnd = addMonths(periodStart, 1);

        List<ScheduledSessionSnapshot> sessions =
            availabilityPort.findScheduledSessions(command.courseId(), periodStart, periodEnd);
        if (sessions.isEmpty()) {
            throw new NoScheduledSessionsException();
        }

        QuoteAvailability availability = sessions.stream().anyMatch(session -> session.availableSpots() > 0)
            ? QuoteAvailability.AVAILABLE
            : QuoteAvailability.UNAVAILABLE;

        return PhysicalCourseQuote.monthly(command.courseId(), pricing, sessions.size(), availability, now);
    }

    private PhysicalCourseQuote buildIndividual(
        CreatePhysicalCourseQuoteCommand command, PhysicalCoursePricing pricing, Instant now
    ) {
        Instant currentMonthStart = calendarMonthStart(now);
        Instant broadStart = addMonths(currentMonthStart, -1);
        Instant broadEnd = addMonths(currentMonthStart, 2);

        List<ScheduledSessionSnapshot> broadWindowSessions =
            availabilityPort.findScheduledSessions(command.courseId(), broadStart, broadEnd);

        ScheduledSessionSnapshot selectedSession = broadWindowSessions.stream()
            .filter(session -> session.sessionId().equals(command.selectedSessionId()))
            .findFirst()
            .orElseThrow(PhysicalSessionNotFoundException::new);

        Instant periodStart = calendarMonthStart(selectedSession.scheduledAt());
        Instant periodEnd = addMonths(periodStart, 1);
        long scheduledSessionCount = broadWindowSessions.stream()
            .filter(session -> !session.scheduledAt().isBefore(periodStart) && session.scheduledAt().isBefore(periodEnd))
            .count();

        QuoteAvailability availability =
            selectedSession.availableSpots() > 0 ? QuoteAvailability.AVAILABLE : QuoteAvailability.UNAVAILABLE;

        return PhysicalCourseQuote.individual(
            command.courseId(), pricing, (int) scheduledSessionCount, command.selectedSessionId(), availability, now
        );
    }

    private static Instant calendarMonthStart(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant addMonths(Instant monthStart, long months) {
        return monthStart.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
    }
}
