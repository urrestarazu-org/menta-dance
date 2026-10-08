package com.menta.physical.infrastructure.demo;

import com.menta.physical.application.port.in.PhysicalCapacityAssignmentPort;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.model.CourseId;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.domain.model.SessionStatus;
import com.menta.physical.infrastructure.qr.QrProperties;
import com.menta.shared.demo.DemoIds;
import com.menta.shared.demo.DemoSeedOrder;
import com.menta.shared.physical.CapacityAssignmentCommand;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Seeds the two demo Physical courses, their regular sessions for the next four weeks, one rolling session
 * about ten minutes in the future, and the student's confirmed seats.
 *
 * <p>Courses are upserted by the fixed ids in {@link DemoIds}. A {@link PhysicalCourse} stores a single weekday,
 * so "Bachata Sensual Presencial" stores {@code TUESDAY} while its sessions are generated on Tuesday and
 * Thursday directly; times are UTC, like the batch session creation. Regular sessions are keyed by
 * {@code (courseId, scheduledAt)}, so only missing slots are created.
 *
 * <p>The rolling session keeps one fixed id: while "now" is still inside its check-in window (taken from
 * {@link QrProperties}, keeping 30 minutes of margin before it closes) it is reused, otherwise it is moved to
 * ten minutes from now. It therefore never accumulates and the student's seat in it survives restarts. QR
 * issuance needs a confirmed seat in the exact session, so the student gets one in the rolling session and in
 * the next regular Sensual session, each guarded by {@code existsConfirmedAssignment}.
 *
 * <p>The runner is intentionally not transactional: the seat assignment opens its own {@code REQUIRES_NEW}
 * transaction and can only see sessions that are already committed.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public final class DemoPhysicalCatalogFixture implements ApplicationRunner, Ordered {

    static final UUID ROLLING_SESSION_ID = UUID.fromString("de000000-0000-4000-8000-0000000003a1");
    static final UUID SEAT_PAYMENT_ID = UUID.fromString("de000000-0000-4000-8000-0000000004a1");

    private static final String PROFESSOR_NAME = "Demo Instructor";
    private static final int DURATION_MINUTES = 60;
    private static final int WEEKS = 4;
    private static final Duration ROLLING_LEAD = Duration.ofMinutes(10);
    private static final Duration ROLLING_CLOSING_MARGIN = Duration.ofMinutes(30);
    private static final LocalTime EVENING = LocalTime.of(19, 0);

    private static final DemoCourse SENSUAL = new DemoCourse(
        DemoIds.PHYSICAL_SENSUAL_COURSE_ID, "Bachata Sensual Presencial",
        "In-person bachata sensual class.", DayOfWeek.TUESDAY, Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
        EVENING, 12);
    private static final DemoCourse TRADITIONAL = new DemoCourse(
        DemoIds.PHYSICAL_TRADITIONAL_COURSE_ID, "Bachata Tradicional Presencial",
        "In-person bachata tradicional class.", DayOfWeek.SATURDAY, Set.of(DayOfWeek.SATURDAY),
        LocalTime.of(11, 0), 15);

    private final PhysicalCourseRepository courseRepository;
    private final PhysicalSessionRepository sessionRepository;
    private final PhysicalCapacityAssignmentRepository assignmentRepository;
    private final PhysicalCapacityAssignmentPort assignmentPort;
    private final Clock clock;
    private final QrProperties qrProperties;

    @Override
    public void run(ApplicationArguments arguments) {
        Instant now = clock.now();
        seedCourse(SENSUAL);
        seedCourse(TRADITIONAL);
        List<PhysicalSession> sensualSessions = seedRegularSessions(SENSUAL, now);
        seedRegularSessions(TRADITIONAL, now);
        seedRollingSession(now);
        seedSeats(sensualSessions);
    }

    @Override
    public int getOrder() {
        return DemoSeedOrder.CONTENT;
    }

    private void seedCourse(DemoCourse course) {
        CourseId id = CourseId.of(course.id());
        if (courseRepository.findById(id).isEmpty()) {
            courseRepository.save(new PhysicalCourse(
                id, course.title(), course.description(), DemoIds.INSTRUCTOR_USER_ID, PROFESSOR_NAME,
                course.courseDay(), course.startTime(), DURATION_MINUTES, PhysicalCourseLevel.BEGINNER,
                course.capacity(), CourseStatus.ACTIVE));
        }
    }

    private List<PhysicalSession> seedRegularSessions(DemoCourse course, Instant now) {
        CourseId id = CourseId.of(course.id());
        List<PhysicalSession> sessions = new ArrayList<>(
            sessionRepository.findManaged(id, now, now.plus(Duration.ofDays(WEEKS * 7L))).stream()
                .filter(session -> !session.getId().getValue().equals(ROLLING_SESSION_ID))
                .toList());
        Set<Instant> taken = Set.copyOf(sessions.stream().map(PhysicalSession::getScheduledAt).toList());
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        List<PhysicalSession> missing = today.datesUntil(today.plusWeeks(WEEKS))
            .filter(date -> course.sessionDays().contains(date.getDayOfWeek()))
            .map(date -> LocalDateTime.of(date, course.startTime()).toInstant(ZoneOffset.UTC))
            .filter(slot -> slot.isAfter(now) && !taken.contains(slot))
            .map(slot -> PhysicalSession.create(id, slot, course.capacity(), null))
            .toList();
        if (!missing.isEmpty()) {
            sessionRepository.saveAll(missing);
            sessions.addAll(missing);
        }
        return sessions;
    }

    private void seedRollingSession(Instant now) {
        SessionId id = SessionId.of(ROLLING_SESSION_ID);
        Instant target = now.plus(ROLLING_LEAD);
        sessionRepository.findById(id).ifPresentOrElse(
            existing -> {
                if (!isReusable(existing.getScheduledAt(), now)) {
                    sessionRepository.save(existing.withSchedule(target));
                }
            },
            () -> sessionRepository.save(new PhysicalSession(
                id, CourseId.of(SENSUAL.id()), target, SENSUAL.capacity(), 0, 0, SessionStatus.SCHEDULED,
                "Demo rolling session")));
    }

    private boolean isReusable(Instant scheduledAt, Instant now) {
        Instant opens = scheduledAt.minus(qrProperties.getSessionWindowBefore());
        Instant closes = scheduledAt.plus(qrProperties.getSessionWindowAfter()).minus(ROLLING_CLOSING_MARGIN);
        return !now.isBefore(opens) && !now.isAfter(closes);
    }

    private void seedSeats(List<PhysicalSession> sensualSessions) {
        ensureSeat(SessionId.of(ROLLING_SESSION_ID));
        sensualSessions.stream().min(Comparator.comparing(PhysicalSession::getScheduledAt))
            .ifPresent(next -> ensureSeat(next.getId()));
    }

    private void ensureSeat(SessionId sessionId) {
        if (!assignmentRepository.existsConfirmedAssignment(sessionId, DemoIds.STUDENT_USER_ID)) {
            assignmentPort.assign(new CapacityAssignmentCommand(
                sessionId.getValue(), DemoIds.STUDENT_USER_ID, SEAT_PAYMENT_ID));
        }
    }

    private record DemoCourse(
        UUID id, String title, String description, DayOfWeek courseDay, Set<DayOfWeek> sessionDays,
        LocalTime startTime, int capacity) {
    }
}
