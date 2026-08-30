package com.menta.billing.infrastructure.e2e;

import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanPaymentMethodJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PlanJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanPaymentMethodJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/** Seeds the Billing-owned plan required by the local Mercado Pago E2E journey. */
@Component
@Profile("e2e-mercadopago")
public final class E2eMercadoPagoBillingFixture implements ApplicationRunner, Ordered {

    public static final UUID PLAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000128");
    private final PlanJpaRepository planRepository;
    private final PlanPaymentMethodJpaRepository paymentMethodRepository;

    public E2eMercadoPagoBillingFixture(
        PlanJpaRepository planRepository, PlanPaymentMethodJpaRepository paymentMethodRepository
    ) {
        this.planRepository = planRepository;
        this.paymentMethodRepository = paymentMethodRepository;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (planRepository.existsById(PLAN_ID)) {
            return;
        }
        Instant now = Instant.now();
        planRepository.save(new PlanJpaEntity(
            PLAN_ID, "E2E Mercado Pago Plan", "Local deterministic checkout fixture.",
            new BigDecimal("1000.00"), "ARS", 30, false, PlanStatus.ACTIVE,
            "Local E2E terms.", "Local E2E cancellation policy.", now, now
        ));
        paymentMethodRepository.save(new PlanPaymentMethodJpaEntity(PLAN_ID, "MERCADO_PAGO"));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
