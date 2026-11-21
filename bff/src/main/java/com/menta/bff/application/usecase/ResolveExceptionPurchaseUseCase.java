package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PurchaseResolution;

/**
 * Use case that closes a purchase stranded in {@code EXCEPTION} with an auditable note (#370).
 *
 * <p>Part of Clean Architecture application layer. Whether the purchase may be closed is decided by the API:
 * the BFF only sends the action. Repeating the same decision with the same note is idempotent there.
 * </p>
 */
public interface ResolveExceptionPurchaseUseCase {

    /**
     * Resolves the purchase.
     *
     * @param accessToken caller's access token
     * @param purchaseId  identifier of the purchase as it came in the URL
     * @param resolution  how it is closed
     * @param note        why, as submitted; it is trimmed and may not be blank nor longer than 500 characters
     * @throws com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException with
     *         {@code PURCHASE_NOT_FOUND} when the id is not a UUID and {@code INVALID_REQUEST} when the
     *         resolution or the note is unusable, both without calling the API, or any refusal of the API
     */
    void execute(String accessToken, String purchaseId, PurchaseResolution resolution, String note);
}
