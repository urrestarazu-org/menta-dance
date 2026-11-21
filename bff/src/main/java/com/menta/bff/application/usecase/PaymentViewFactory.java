package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PaymentModality;
import com.menta.bff.application.dto.PaymentStatusType;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;

/** Words a payment for the administrator's screens: the formatting lives here, not in controller or template. */
@RequiredArgsConstructor
final class PaymentViewFactory {

    private static final Locale AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter MOMENT_FORMAT =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", AR);
    private static final String NO_DATA = "Sin dato";
    private static final int SHORT_ID_LENGTH = 8;

    private final ZoneId displayZone;

    PaymentVerificationView of(String paymentId, String userId, PaymentModality modality, BigDecimal amount,
            String currency, PaymentStatusType status, boolean hasProof, Instant createdAt) {
        return new PaymentVerificationView(paymentId, shortId(userId), amount(amount, currency), createdAt,
                MOMENT_FORMAT.format(createdAt.atZone(displayZone)), "Transferencia", modality(modality), status,
                status(status), hasProof);
    }

    private static String shortId(String userId) {
        if (userId == null || userId.isBlank()) {
            return NO_DATA;
        }
        return userId.length() > SHORT_ID_LENGTH ? userId.substring(0, SHORT_ID_LENGTH) : userId;
    }

    private static String amount(BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getNumberInstance(AR);
        format.setMinimumFractionDigits(2);
        return format.format(amount) + " " + currency;
    }

    private static String modality(PaymentModality modality) {
        return switch (modality) {
            case PHYSICAL -> "Presencial";
            case VIRTUAL -> "Virtual";
            case UNKNOWN -> NO_DATA;
        };
    }

    private static String status(PaymentStatusType status) {
        return switch (status) {
            case AWAITING_PROVIDER -> "Esperando al proveedor de pago";
            case AWAITING_MANUAL_VERIFICATION -> "Pendiente de verificación";
            case RECONCILIATION_REQUIRED -> "Requiere conciliación";
            case COMPLETED -> "Aprobado";
            case REJECTED -> "Rechazado";
            case CANCELLED -> "Cancelado";
            case EXPIRED -> "Vencido";
            case UNKNOWN -> NO_DATA;
        };
    }
}
