package com.menta.bff.application.usecase;

/**
 * Use case for the subscriptions section of the student's history page (#363).
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface GetSubscriptionHistoryUseCase {

    /**
     * Reads the student's subscriptions with the caller's token.
     *
     * <p>Never fails because of the Billing API: a missing token or any upstream failure becomes a
     * view flagged {@link SubscriptionHistoryView#unavailable()}, so the payments section always
     * renders. Only call it for a student.
     * </p>
     *
     * @param accessToken the student's access token, or {@code null}/blank when the request has none;
     *                    never logged
     * @return the section to render
     */
    SubscriptionHistoryView execute(String accessToken);
}
