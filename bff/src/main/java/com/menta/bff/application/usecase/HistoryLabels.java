package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.OwnPayment;
import com.menta.bff.application.dto.SubscriptionHistoryEntry;

/**
 * Spanish labels and badge weights of the history page. The switches have no default branch on
 * purpose: a new enum constant does not compile until it has a label.
 */
final class HistoryLabels {

    private static final String UNKNOWN_STATUS = "Estado desconocido";

    private HistoryLabels() {
    }

    static String subscriptionStatus(SubscriptionHistoryEntry.Status status) {
        return switch (status) {
            case PENDING -> "Pendiente de pago";
            case ACTIVE -> "Activa";
            case EXPIRED -> "Vencida";
            case CANCELLED -> "Cancelada";
            case UNKNOWN -> UNKNOWN_STATUS;
        };
    }

    static BadgeTone subscriptionTone(SubscriptionHistoryEntry.Status status) {
        return switch (status) {
            case PENDING -> BadgeTone.WARNING;
            case ACTIVE -> BadgeTone.SUCCESS;
            case CANCELLED -> BadgeTone.DANGER;
            case EXPIRED, UNKNOWN -> BadgeTone.NEUTRAL;
        };
    }

    static String paymentStatus(OwnPayment.Status status) {
        return switch (status) {
            case AWAITING_PROVIDER -> "Pendiente de pago";
            case AWAITING_MANUAL_VERIFICATION -> "En verificación";
            case RECONCILIATION_REQUIRED -> "En revisión";
            case COMPLETED -> "Pagado";
            case REJECTED -> "Rechazado";
            case CANCELLED -> "Cancelado";
            case EXPIRED -> "Vencido";
            case UNKNOWN -> UNKNOWN_STATUS;
        };
    }

    static BadgeTone paymentTone(OwnPayment.Status status) {
        return switch (status) {
            case AWAITING_PROVIDER, AWAITING_MANUAL_VERIFICATION, RECONCILIATION_REQUIRED -> BadgeTone.WARNING;
            case COMPLETED -> BadgeTone.SUCCESS;
            case REJECTED -> BadgeTone.DANGER;
            case CANCELLED, EXPIRED, UNKNOWN -> BadgeTone.NEUTRAL;
        };
    }

    static String method(OwnPayment.Method method) {
        return switch (method) {
            case MERCADO_PAGO -> "Mercado Pago";
            case BANK_TRANSFER -> "Transferencia bancaria";
            case UNKNOWN -> "Otro medio de pago";
        };
    }

    /** What the payment bought: the plan name for a subscription, never invented when it is missing. */
    static String concept(OwnPayment payment) {
        return switch (payment.itemType()) {
            case SUBSCRIPTION -> payment.itemName() == null || payment.itemName().isBlank()
                    ? "Suscripción" : payment.itemName();
            case PHYSICAL_COURSE -> "Curso presencial";
            case UNKNOWN -> "Compra";
        };
    }
}
