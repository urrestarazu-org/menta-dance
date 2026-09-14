package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.PhysicalPurchaseCheckoutResult;

/** Entry port for {@code POST /api/v1/billing/physical/purchases} (#41, US-PHYSICAL-004). */
public interface CreatePhysicalPurchaseCheckoutUseCase {

    PhysicalPurchaseCheckoutResult create(CreatePhysicalPurchaseCheckoutCommand command);
}
