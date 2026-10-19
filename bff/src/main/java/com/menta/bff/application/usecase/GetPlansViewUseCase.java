package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PlansView;
import com.menta.bff.application.dto.PlansViewer;

/**
 * Use case for retrieving the plans page's rendering data.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface GetPlansViewUseCase {

    /**
     * Composes the shared plan catalog with the offer for the viewer.
     *
     * <p>The student's subscription is read only for {@link PlansViewer#STUDENT}, with the
     * caller's token. Any failure propagates untranslated: a catalog failure and, for a student,
     * a failure to verify the subscription state both end in the shared error view, so no
     * checkout can start while the state is unknown.
     * </p>
     *
     * @param viewer      who is looking at the page
     * @param accessToken caller's access token; only read for a student
     * @return the plans and the offer
     * @throws com.menta.bff.application.port.out.BillingApiClient.NotFoundException
     *         if the upstream endpoint reports no plans found (404)
     * @throws com.menta.bff.application.port.out.BillingApiClient.ServiceUnavailableException
     *         if the catalog or the subscription state is unavailable
     */
    PlansView execute(PlansViewer viewer, String accessToken);
}
