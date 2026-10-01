package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.CheckInCommand;
import com.menta.physical.application.dto.CheckInResult;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.application.port.out.AttendanceRepository;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.application.port.out.QrCredentialSignatureService;
import com.menta.physical.application.port.out.RedisLockPort;
import com.menta.physical.application.usecase.QrCredentialParser.ParsedQrCredential;
import com.menta.physical.domain.exception.CapacityAssignmentRequiredException;
import com.menta.physical.domain.exception.CheckInAlreadyProcessingException;
import com.menta.physical.domain.exception.ExpiredQrCredentialException;
import com.menta.physical.domain.exception.InsufficientRoleException;
import com.menta.physical.domain.exception.InvalidQrCredentialException;
import com.menta.physical.domain.exception.OutsideCheckInWindowException;
import com.menta.physical.domain.exception.SessionCancelledException;
import com.menta.physical.domain.exception.SessionNotActiveException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.exception.StudentNotFoundException;
import com.menta.physical.domain.model.Attendance;
import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.domain.model.SessionStatus;
import com.menta.shared.auth.UserExistencePort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redeems a scanned QR for a confirmed attendance row (US-PHYSICAL-001
 * escenario 2). The eleven-step validation order below is deliberate and
 * load-bearing, not incidental: every rejection reason that can be decided
 * WITHOUT talking to Redis (device authentication, QR shape, session existence,
 * cancellation, window, assignment, idempotent replay) runs first, so a
 * flood of malformed or unauthorized scans can never create Redis lock
 * contention (escenario 4). Only a request that has cleared every one of
 * those gates reaches the two locks in step 9/10.
 */
public class ProcessPhysicalCheckInUseCaseImpl implements ProcessPhysicalCheckInUseCase {

    private static final String QR_LOCK_PREFIX = "checkin:qr:";
    private static final String ATTENDANCE_LOCK_PREFIX = "checkin:attendance:";

    /** #45, US-PHYSICAL-008: only these roles may record a MANUAL check-in (D7 step M1). */
    private static final Set<String> MANUAL_CHECK_IN_ROLES = Set.of("RECEPTIONIST", "ADMIN");

    private final PhysicalSessionRepository sessionRepository;
    private final PhysicalCapacityAssignmentRepository assignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final QrCredentialSignatureService signatureService;
    private final RedisLockPort lockPort;
    private final Clock clock;
    private final PhysicalDeviceAuthenticator deviceAuthenticator;
    private final Duration sessionWindowBefore;
    private final Duration sessionWindowAfter;
    private final Duration lockTtl;
    private final UserExistencePort userExistencePort;

    public ProcessPhysicalCheckInUseCaseImpl(
        PhysicalSessionRepository sessionRepository,
        PhysicalCapacityAssignmentRepository assignmentRepository,
        AttendanceRepository attendanceRepository,
        QrCredentialSignatureService signatureService,
        RedisLockPort lockPort,
        Clock clock,
        PhysicalDeviceAuthenticator deviceAuthenticator,
        Duration sessionWindowBefore,
        Duration sessionWindowAfter,
        Duration lockTtl,
        UserExistencePort userExistencePort
    ) {
        this.sessionRepository = sessionRepository;
        this.assignmentRepository = assignmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.signatureService = signatureService;
        this.lockPort = lockPort;
        this.clock = clock;
        this.deviceAuthenticator = deviceAuthenticator;
        this.sessionWindowBefore = sessionWindowBefore;
        this.sessionWindowAfter = sessionWindowAfter;
        this.lockTtl = lockTtl;
        this.userExistencePort = userExistencePort;
    }

    @Override
    public CheckInResult checkIn(CheckInCommand command) {
        // Step 1: reject an unauthorized reader before it learns anything else. The registry
        // lookup is a database PK read, never Redis, so this gate stays Redis-free.
        DeviceId authenticatedDevice =
            deviceAuthenticator.authenticate(command.deviceId(), command.deviceToken());

        // Step 2: format + type validation, delegated to the helper.
        ParsedQrCredential parsed = QrCredentialParser.parse(command.qrCredentials());

        // Step 3: a token bound for another session is never accepted here.
        if (!parsed.sessionId().equals(command.sessionId())) {
            throw new InvalidQrCredentialException();
        }

        // Step 4: recompute and compare, forward-compatible with real HMAC.
        verifySignature(parsed, command.qrCredentials());

        // Step 5: well-formed and correctly signed, but presented too late.
        if (parsed.expiresAtEpochSeconds() < clock.now().getEpochSecond()) {
            throw new ExpiredQrCredentialException();
        }

        // Step 6: the session must exist, not be cancelled, and "now" must be
        // inside its window. A cancelled session can still carry a confirmed
        // assignment on record (cancellation does not retroactively delete
        // it), so this runs before step 7 would otherwise let a stale
        // assignment grant access to a class that no longer happens.
        PhysicalSession session = sessionRepository.findById(command.sessionId())
            .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new SessionCancelledException();
        }
        verifyWithinCheckInWindow(session);

        // Step 7: a confirmed capacity assignment is a product precondition.
        boolean hasAssignment =
            assignmentRepository.existsConfirmedAssignment(command.sessionId(), parsed.studentId());
        if (!hasAssignment) {
            throw new CapacityAssignmentRequiredException();
        }

        // Step 8: idempotent replay short-circuits before touching Redis at all.
        Optional<Attendance> existing =
            attendanceRepository.findBySessionIdAndUserId(command.sessionId(), parsed.studentId());
        if (existing.isPresent()) {
            return new CheckInResult(AttendanceViewMapper.toView(existing.get()), false);
        }

        // Steps 9-10: both locks must be free. The first lock is intentionally
        // left to expire on its own if step 10 or the INSERT fails afterward
        // (no compare-and-delete compensation in this MVP, see RedisLockPort).
        acquireLockOrThrow(QR_LOCK_PREFIX + parsed.jti());
        acquireLockOrThrow(ATTENDANCE_LOCK_PREFIX + command.sessionId() + ":" + parsed.studentId());

        // Step 11: record the attendance.
        Attendance attendance = Attendance.record(
            command.sessionId(), parsed.studentId(), clock.now(),
            authenticatedDevice.toString(), AttendanceKind.QR
        );
        Attendance saved = attendanceRepository.save(attendance);
        return new CheckInResult(AttendanceViewMapper.toView(saved), true);
    }

    /**
     * #45, US-PHYSICAL-008: receptionist-initiated MANUAL check-in. D7's asserted order — role ->
     * student existence -> confirmed capacity assignment -> not-cancelled -> idempotent read ->
     * single lock -> INSERT. Deliberately shorter and separate from {@link #checkIn}: no device
     * token, no QR credential, no check-in window, and (D8) {@code hasOccurred} is never
     * consulted — only cancellation gates this path, enabling unlimited retroactive backfill.
     */
    @Override
    public CheckInResult checkInManually(CheckInCommand command) {
        // M1: only the front desk (or an admin) may record on another person's behalf.
        if (!command.actor().hasAnyRoleOf(MANUAL_CHECK_IN_ROLES)) {
            throw new InsufficientRoleException();
        }

        // M2: an unknown subject is rejected before any Physical state is read (D7).
        if (!userExistencePort.existsById(command.studentId())) {
            throw new StudentNotFoundException();
        }

        // M3: a confirmed capacity assignment is a product precondition. Deliberately BEFORE the
        // cancellation check — the inverse of the QR flow's step 6/7 order (D7): for MANUAL both
        // are rejections, and D7 fixes which one a multi-violation request receives.
        if (!assignmentRepository.existsConfirmedAssignment(command.sessionId(), command.studentId())) {
            throw new CapacityAssignmentRequiredException();
        }

        // M4: D8 — ONLY cancellation gates MANUAL. hasOccurred(now) is never consulted:
        // retroactive backfill of an elapsed, non-cancelled session is the point of this variant,
        // with no time limit.
        PhysicalSession session = sessionRepository.findById(command.sessionId())
            .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new SessionNotActiveException();
        }

        // M5: idempotent replay short-circuits before touching Redis at all.
        Optional<Attendance> existing =
            attendanceRepository.findBySessionIdAndUserId(command.sessionId(), command.studentId());
        if (existing.isPresent()) {
            return new CheckInResult(AttendanceViewMapper.toView(existing.get()), false);
        }

        // M6: ONE lock, the same key shape and TTL the QR flow uses (D1, C6).
        acquireLockOrThrow(ATTENDANCE_LOCK_PREFIX + command.sessionId() + ":" + command.studentId());

        // M7: device_id carries the acting person's own userId (D2).
        Attendance attendance = Attendance.record(
            command.sessionId(), command.studentId(), clock.now(),
            command.actor().userId().toString(), AttendanceKind.MANUAL
        );
        return new CheckInResult(AttendanceViewMapper.toView(attendanceRepository.save(attendance)), true);
    }

    private void acquireLockOrThrow(String key) {
        lockPort.acquireIfAbsent(key, lockTtl).orElseThrow(CheckInAlreadyProcessingException::new);
    }

    private void verifyWithinCheckInWindow(PhysicalSession session) {
        Instant now = clock.now();
        Instant windowStart = session.getScheduledAt().minus(sessionWindowBefore);
        Instant windowEnd = session.getScheduledAt().plus(sessionWindowAfter);
        if (now.isBefore(windowStart) || now.isAfter(windowEnd)) {
            throw new OutsideCheckInWindowException();
        }
    }

    private void verifySignature(ParsedQrCredential parsed, String original) {
        String recomputed = signatureService.sign(
            parsed.studentId().toString(),
            parsed.sessionId().toString(),
            parsed.jti(),
            parsed.expiresAtEpochSeconds()
        );
        if (!constantTimeEquals(recomputed, original)) {
            throw new InvalidQrCredentialException();
        }
    }

    /** Constant-time comparison: a reader must never learn a secret via response-time. */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
