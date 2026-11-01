package com.menta.bff.application.usecase;

/**
 * Use case that builds the temporary membership landing (#685).
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface GetMembershipLandingViewUseCase {

    /**
     * Builds the price table for today's date (Argentina time).
     *
     * @return the landing view
     */
    MembershipLandingView execute();
}
