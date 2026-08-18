package com.menta.billing.infrastructure.persistence.mapper;

import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;

/** Flattens {@link PaymentStatus}'s sealed hierarchy into entity columns and back — see the entity's Javadoc. */
public final class PaymentJpaMapper {

    private PaymentJpaMapper() {
    }

    public static Payment toDomain(PaymentJpaEntity entity) {
        return new Payment(
            PaymentId.of(entity.getId()),
            entity.getProviderPaymentId(),
            Money.of(entity.getExpectedAmount(), entity.getExpectedCurrency()),
            entity.getExpectedExternalReference(),
            entity.getExpectedMerchantAccountId(),
            toTarget(entity),
            toStatus(entity),
            entity.getCreatedAt()
        );
    }

    public static PaymentJpaEntity toEntity(Payment payment) {
        StatusColumns statusColumns = toStatusColumns(payment.getStatus());
        TargetColumns targetColumns = toTargetColumns(payment.getTarget());
        return new PaymentJpaEntity(
            payment.getId().getValue(),
            payment.getProviderPaymentId(),
            payment.getExpectedAmount().getAmount(),
            payment.getExpectedAmount().getCurrency(),
            payment.getExpectedExternalReference(),
            payment.getExpectedMerchantAccountId(),
            targetColumns.modality(),
            targetColumns.reference(),
            statusColumns.type(),
            statusColumns.reason(),
            statusColumns.changedAt(),
            payment.getCreatedAt()
        );
    }

    private static PaymentTarget toTarget(PaymentJpaEntity entity) {
        return "PHYSICAL".equals(entity.getTargetModality())
            ? new PaymentTarget.Physical(entity.getTargetReference())
            : new PaymentTarget.Virtual(entity.getTargetReference());
    }

    private static TargetColumns toTargetColumns(PaymentTarget target) {
        return switch (target) {
            case PaymentTarget.Physical physical -> new TargetColumns("PHYSICAL", physical.sessionId());
            case PaymentTarget.Virtual virtual -> new TargetColumns("VIRTUAL", virtual.courseId());
        };
    }

    private static PaymentStatus toStatus(PaymentJpaEntity entity) {
        return switch (entity.getStatusType()) {
            case "AWAITING_PROVIDER" -> new PaymentStatus.AwaitingProvider();
            case "AWAITING_MANUAL_VERIFICATION" -> new PaymentStatus.AwaitingManualVerification();
            case "RECONCILIATION_REQUIRED" -> new PaymentStatus.ReconciliationRequired(entity.getStatusReason());
            case "COMPLETED" -> new PaymentStatus.Completed(entity.getStatusChangedAt());
            case "REJECTED" -> new PaymentStatus.Rejected(entity.getStatusChangedAt());
            case "CANCELLED" -> new PaymentStatus.Cancelled(entity.getStatusChangedAt());
            case "EXPIRED" -> new PaymentStatus.Expired(entity.getStatusChangedAt());
            default -> throw new IllegalStateException("Unknown status_type: " + entity.getStatusType());
        };
    }

    private static StatusColumns toStatusColumns(PaymentStatus status) {
        return switch (status) {
            case PaymentStatus.AwaitingProvider ignored -> new StatusColumns("AWAITING_PROVIDER", null, null);
            case PaymentStatus.AwaitingManualVerification ignored ->
                new StatusColumns("AWAITING_MANUAL_VERIFICATION", null, null);
            case PaymentStatus.ReconciliationRequired reconciliationRequired ->
                new StatusColumns("RECONCILIATION_REQUIRED", reconciliationRequired.reason(), null);
            case PaymentStatus.Completed completed ->
                new StatusColumns("COMPLETED", null, completed.confirmedAt());
            case PaymentStatus.Rejected rejected -> new StatusColumns("REJECTED", null, rejected.rejectedAt());
            case PaymentStatus.Cancelled cancelled -> new StatusColumns("CANCELLED", null, cancelled.cancelledAt());
            case PaymentStatus.Expired expired -> new StatusColumns("EXPIRED", null, expired.expiredAt());
        };
    }

    private record TargetColumns(String modality, String reference) {
    }

    private record StatusColumns(String type, String reason, java.time.Instant changedAt) {
    }
}
