package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.AccessQrView;
import com.menta.physical.application.port.in.IssuePhysicalAccessQrUseCase;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.application.port.out.QrCredentialSignatureService;
import com.menta.physical.domain.exception.CapacityAssignmentRequiredException;
import com.menta.physical.domain.exception.OutsideCheckInWindowException;
import com.menta.physical.domain.exception.SessionCancelledException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.domain.model.SessionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Issues an ephemeral, placeholder-signed access QR for a student who
 * already holds a confirmed assignment for the target session
 * (US-PHYSICAL-001 escenario 1).
 *
 * <p>{@code refreshAfterSeconds} is a constant here, not derived from {@code
 * credentialTtl} — the MVP tells the student app to re-issue every 30
 * seconds regardless of how long a single token remains valid (see {@link
 * AccessQrView}'s Javadoc). Both values are configured independently in
 * {@code app.qr.*} and this constant.</p>
 *
 * <p>Escenario 1's precondition is "una sesión dentro de su ventana de
 * check-in" — issuance is gated on the same window redemption checks
 * later, using the same {@code app.qr.session-window-*} bounds. Without
 * this, a student could hold a valid-looking QR for a session far outside
 * any check-in window, only to have it rejected at the door — a confusing
 * UX and a needless credential to have handed out at all.</p>
 */
public class IssuePhysicalAccessQrUseCaseImpl implements IssuePhysicalAccessQrUseCase {

    private static final int REFRESH_AFTER_SECONDS = 30;

    private final PhysicalSessionRepository sessionRepository;
    private final PhysicalCapacityAssignmentRepository assignmentRepository;
    private final QrCredentialSignatureService signatureService;
    private final Clock clock;
    private final Duration credentialTtl;
    private final Duration sessionWindowBefore;
    private final Duration sessionWindowAfter;

    public IssuePhysicalAccessQrUseCaseImpl(
        PhysicalSessionRepository sessionRepository,
        PhysicalCapacityAssignmentRepository assignmentRepository,
        QrCredentialSignatureService signatureService,
        Clock clock,
        Duration credentialTtl,
        Duration sessionWindowBefore,
        Duration sessionWindowAfter
    ) {
        this.sessionRepository = sessionRepository;
        this.assignmentRepository = assignmentRepository;
        this.signatureService = signatureService;
        this.clock = clock;
        this.credentialTtl = credentialTtl;
        this.sessionWindowBefore = sessionWindowBefore;
        this.sessionWindowAfter = sessionWindowAfter;
    }

    @Override
    public AccessQrView issue(String sessionId, UUID studentId) {
        SessionId id = SessionId.of(sessionId);
        PhysicalSession session =
            sessionRepository.findById(id).orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new SessionCancelledException();
        }
        verifyWithinCheckInWindow(session);
        if (!assignmentRepository.existsConfirmedAssignment(id, studentId)) {
            throw new CapacityAssignmentRequiredException();
        }
        String jti = UUID.randomUUID().toString();
        Instant expiresAt = clock.now().plus(credentialTtl);
        String qrCredentials = signatureService.sign(
            studentId.toString(), id.toString(), jti, expiresAt.getEpochSecond()
        );
        return new AccessQrView(qrCredentials, expiresAt, REFRESH_AFTER_SECONDS);
    }

    private void verifyWithinCheckInWindow(PhysicalSession session) {
        Instant now = clock.now();
        Instant windowStart = session.getScheduledAt().minus(sessionWindowBefore);
        Instant windowEnd = session.getScheduledAt().plus(sessionWindowAfter);
        if (now.isBefore(windowStart) || now.isAfter(windowEnd)) {
            throw new OutsideCheckInWindowException();
        }
    }
}
