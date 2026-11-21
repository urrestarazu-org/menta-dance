package com.menta.bff.application.usecase;

/**
 * Use case that lists the purchases stranded in {@code EXCEPTION}, for the administrator (#370).
 *
 * <p>Part of Clean Architecture application layer. Failures of the API reach the caller as
 * {@code AdminBillingApiClient.PaymentRefusalException} or
 * {@code AdminBillingApiClient.ServiceUnavailableException}, so the screen words each one.
 * </p>
 */
public interface ListExceptionPurchasesUseCase {

    /**
     * Lists one page, oldest first.
     *
     * @param accessToken caller's access token
     * @param page        zero-based page index; {@code null} or negative reads the first page
     * @param size        page size; {@code null} or below 1 reads 20, above 50 reads 50 (the API rejects more)
     * @return the page, with every row already worded for the screen
     */
    ExceptionPurchasesView execute(String accessToken, Integer page, Integer size);
}
