package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.DashboardView;

/**
 * Use case for the subscription card of a student's dashboard (#363).
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface GetDashboardViewUseCase {

    /**
     * Reads the student's current subscription with the caller's token and builds its card.
     *
     * <p>Never fails because of the Billing API: an unverifiable subscription becomes {@link
     * DashboardView.Unavailable}, and a plan name that cannot be resolved from the public catalog
     * degrades to a generic label, so the rest of the dashboard always renders. Only call it for a
     * student; other roles have no subscription card.
     * </p>
     *
     * @param accessToken the student's access token; never logged
     * @return the card to render
     */
    DashboardView execute(String accessToken);
}
