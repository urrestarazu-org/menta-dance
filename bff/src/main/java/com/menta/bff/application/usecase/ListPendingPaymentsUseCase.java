package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PendingPaymentPage;

/**
 * Use case that lists the payments awaiting manual verification, for the administrator's inbox (#370).
 *
 * <p>Part of Clean Architecture application layer. Failures of the API reach the caller as
 * {@code AdminBillingApiClient.PaymentRefusalException} or
 * {@code AdminBillingApiClient.ServiceUnavailableException}, so the screen words each one.
 * </p>
 */
public interface ListPendingPaymentsUseCase {

    /** Page size when the caller names none. */
    int DEFAULT_SIZE = 20;

    /** Largest page the API accepts: a bigger request is cut here, because the API would answer 400. */
    int MAX_SIZE = 50;

    /**
     * Lists one page, oldest first.
     *
     * @param accessToken caller's access token
     * @param page        zero-based page index; {@code null} or negative reads the first page
     * @param size        page size; {@code null} or below 1 reads {@value #DEFAULT_SIZE}, above
     *                    {@value #MAX_SIZE} reads {@value #MAX_SIZE}
     * @return the page
     */
    PendingPaymentPage execute(String accessToken, Integer page, Integer size);
}
