package com.menta.bff.application.usecase;

/**
 * Use case for the student cancelling their own subscription (#363).
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface CancelSubscriptionUseCase {

    /**
     * Cancels the renewal of the student's own active subscription with the caller's token.
     *
     * <p>Never throws because of the Billing API: every failure is an {@link CancelSubscriptionResult.Outcome}.
     * A missing token makes no call at all. Only call it for a student.
     * </p>
     *
     * @param accessToken the student's access token, or {@code null}/blank when the request has none; never logged
     * @return how the attempt ended
     */
    CancelSubscriptionResult execute(String accessToken);
}
