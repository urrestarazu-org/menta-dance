package com.menta.bff.application.usecase;

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
    int DEFAULT_SIZE = ManualPaymentSupport.DEFAULT_SIZE;

    /** Largest page the API accepts: a bigger request is cut here, because the API would answer 400. */
    int MAX_SIZE = ManualPaymentSupport.MAX_SIZE;

    /**
     * Lists one page, oldest first.
     *
     * @param accessToken caller's access token
     * @param page        zero-based page index; {@code null} or negative reads the first page
     * @param size        page size; {@code null} or below 1 reads {@value #DEFAULT_SIZE}, above
     *                    {@value #MAX_SIZE} reads {@value #MAX_SIZE}
     * @return the page, with every row already worded for the screen
     */
    PendingPaymentsView execute(String accessToken, Integer page, Integer size);
}
