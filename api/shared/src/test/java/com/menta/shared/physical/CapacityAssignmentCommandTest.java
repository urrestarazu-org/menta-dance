package com.menta.shared.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.shared.physical.CapacityAssignmentCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * RED-GREEN: every assertion references {@link CapacityAssignmentCommand}, the
 * cross-module record that physical's {@code AssignCapacityUseCase} consumes
 * from {@code api:app}'s outbox handler (design §4.3).
 *
 * <p>The command carries no JPA / JSON / Spring annotation — it is plain
 * Java shared across modules — so this test asserts both the constructor
 * contract and the absence of accidental persistence decoration (Covered by
 * Checkstyle + the {@code api:shared} module's own lack of JPA on the
 * compile-time classpath is what guarantees the second half).</p>
 */
class CapacityAssignmentCommandTest {

    private static final UUID SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STUDENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PAYMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    void builds_a_command_with_verbatim_uuids() {
        CapacityAssignmentCommand command = new CapacityAssignmentCommand(
            SESSION_ID, STUDENT_ID, PAYMENT_ID
        );

        assertThat(command.sessionId()).isEqualTo(SESSION_ID);
        assertThat(command.studentId()).isEqualTo(STUDENT_ID);
        assertThat(command.paymentId()).isEqualTo(PAYMENT_ID);
    }

    @Test
    void rejects_null_session_id() {
        assertThatThrownBy(() -> new CapacityAssignmentCommand(
            null, STUDENT_ID, PAYMENT_ID
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_null_student_id() {
        assertThatThrownBy(() -> new CapacityAssignmentCommand(
            SESSION_ID, null, PAYMENT_ID
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_null_payment_id() {
        assertThatThrownBy(() -> new CapacityAssignmentCommand(
            SESSION_ID, STUDENT_ID, null
        )).isInstanceOf(NullPointerException.class);
    }
}
