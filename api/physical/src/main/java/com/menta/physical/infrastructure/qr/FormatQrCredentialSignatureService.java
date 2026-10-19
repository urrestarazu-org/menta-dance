package com.menta.physical.infrastructure.qr;

import com.menta.physical.application.port.out.QrCredentialSignatureService;

/**
 * Placeholder {@link QrCredentialSignatureService} implementation for the
 * MVP (US-PHYSICAL-001). Concatenates the four claims with NO HMAC and NO
 * secret key — the "signature" is just the deterministic string every
 * caller can recompute from the same inputs.
 *
 * <p><strong>Esta implementación es un placeholder.</strong> Un follow-up la
 * reemplaza por HMAC real; el contrato del puerto y los casos de uso
 * permanecen sin cambios (mismo patrón que {@code
 * StringFormatBunnyNetSignatureService}).</p>
 */
public class FormatQrCredentialSignatureService implements QrCredentialSignatureService {

    private static final String PREFIX = "qr:";

    @Override
    public String sign(String studentId, String sessionId, String jti, long expiresAtEpochSeconds) {
        return PREFIX + studentId + ":" + sessionId + ":" + jti + ":" + expiresAtEpochSeconds;
    }
}
