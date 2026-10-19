package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.physical.application.usecase.QrCredentialParser.ParsedQrCredential;
import com.menta.physical.domain.exception.InvalidQrCredentialException;
import com.menta.physical.domain.model.SessionId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QrCredentialParserTest {

    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final SessionId SESSION_ID = SessionId.generate();

    private static String validToken() {
        return "qr:" + STUDENT_ID + ":" + SESSION_ID + ":jti-1:1700000000";
    }

    @Test
    void parses_every_claim_from_a_well_formed_token() {
        ParsedQrCredential parsed = QrCredentialParser.parse(validToken());

        assertThat(parsed.studentId()).isEqualTo(STUDENT_ID);
        assertThat(parsed.sessionId()).isEqualTo(SESSION_ID);
        assertThat(parsed.jti()).isEqualTo("jti-1");
        assertThat(parsed.expiresAtEpochSeconds()).isEqualTo(1700000000L);
    }

    @Test
    void rejects_a_null_token() {
        assertThatThrownBy(() -> QrCredentialParser.parse(null))
            .isInstanceOf(InvalidQrCredentialException.class);
    }

    @Test
    void rejects_a_token_missing_the_qr_prefix() {
        String withoutPrefix = validToken().substring("qr:".length());

        assertThatThrownBy(() -> QrCredentialParser.parse(withoutPrefix))
            .isInstanceOf(InvalidQrCredentialException.class);
    }

    @Test
    void rejects_a_token_with_too_few_segments() {
        String token = "qr:" + STUDENT_ID + ":" + SESSION_ID + ":jti-1";

        assertThatThrownBy(() -> QrCredentialParser.parse(token))
            .isInstanceOf(InvalidQrCredentialException.class);
    }

    @Test
    void rejects_a_token_with_too_many_segments() {
        assertThatThrownBy(() -> QrCredentialParser.parse(validToken() + ":extra"))
            .isInstanceOf(InvalidQrCredentialException.class);
    }

    @Test
    void rejects_a_malformed_student_id() {
        String token = "qr:not-a-uuid:" + SESSION_ID + ":jti-1:1700000000";

        assertThatThrownBy(() -> QrCredentialParser.parse(token))
            .isInstanceOf(InvalidQrCredentialException.class);
    }

    @Test
    void rejects_a_malformed_session_id() {
        String token = "qr:" + STUDENT_ID + ":not-a-uuid:jti-1:1700000000";

        assertThatThrownBy(() -> QrCredentialParser.parse(token))
            .isInstanceOf(InvalidQrCredentialException.class);
    }

    @Test
    void rejects_a_blank_jti() {
        String token = "qr:" + STUDENT_ID + ":" + SESSION_ID + "::1700000000";

        assertThatThrownBy(() -> QrCredentialParser.parse(token))
            .isInstanceOf(InvalidQrCredentialException.class);
    }

    @Test
    void rejects_a_non_numeric_expiration() {
        String token = "qr:" + STUDENT_ID + ":" + SESSION_ID + ":jti-1:soon";

        assertThatThrownBy(() -> QrCredentialParser.parse(token))
            .isInstanceOf(InvalidQrCredentialException.class);
    }
}
