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
import com.menta.physical.domain.exception.InvalidDeviceTokenException;
import com.menta.physical.domain.exception.InvalidQrCredentialException;
import com.menta.physical.domain.exception.OutsideCheckInWindowException;
import com.menta.physical.domain.exception.SessionCancelledException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.model.Attendance;
import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.domain.model.SessionStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Redeems a scanned QR for a confirmed attendance row (US-PHYSICAL-001
 * escenario 2). The eleven-step validation order below is deliberate and
 * load-bearing, not incidental: every rejection reason that can be decided
 * WITHOUT talking to Redis (device token, QR shape, session existence,
 * cancellation, window, assignment, idempotent replay) runs first, so a
 * flood of malformed or unauthorized scans can never create Redis lock
 * contention (escenario 4). Only a request that has cleared every one of
 * those gates reaches the two locks in step 9/10.
 */
public class ProcessPhysicalCheckInUseCaseImpl implements ProcessPhysicalCheckInUseCase {

    private static final String QR_LOCK_PREFIX = "checkin:qr:";
    private static final String ATTENDANCE_LOCK_PREFIX = "checkin:attendance:";

    private final PhysicalSessionRepository sessionRepository;
    private final PhysicalCapacityAssignmentRepository assignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final QrCredentialSignatureService signatureService;
    private final RedisLockPort lockPort;
    private final Clock clock;
    private final String deviceToken;
    private final Duration sessionWindowBefore;
    private final Duration sessionWindowAfter;
    private final Duration lockTtl;

    public ProcessPhysicalCheckInUseCaseImpl(
        PhysicalSessionRepository sessionRepository,
        PhysicalCapacityAssignmentRepository assignmentRepository,
        AttendanceRepository attendanceRepository,
        QrCredentialSignatureService signatureService,
        RedisLockPort lockPort,
        Clock clock,
        String deviceToken,
        Duration sessionWindowBefore,
        Duration sessionWindowAfter,
        Duration lockTtl
    ) {
        this.sessionRepository = sessionRepository;
        this.assignmentRepository = assignmentRepository;
        this.attendanceRepository = attendanceRepository;
        this.signatureService = signatureService;
        this.lockPort = lockPort;
        this.clock = clock;
        this.deviceToken = deviceToken;
        this.sessionWindowBefore = sessionWindowBefore;
        this.sessionWindowAfter = sessionWindowAfter;
        this.lockTtl = lockTtl;
    }

    @Override
    public CheckInResult checkIn(CheckInCommand command) {
        // Step 1: reject an unauthorized reader before it learns anything else.
        verifyDeviceToken(command.deviceToken());

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
            command.deviceId(), AttendanceKind.QR
        );
        Attendance saved = attendanceRepository.save(attendance);
        return new CheckInResult(AttendanceViewMapper.toView(saved), true);
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

    private void verifyDeviceToken(String provided) {
        if (provided == null || !constantTimeEquals(provided, deviceToken)) {
            throw new InvalidDeviceTokenException();
        }
    }

    /** Constant-time comparison: a reader must never learn a secret via response-time. */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)
        );
    }
}
