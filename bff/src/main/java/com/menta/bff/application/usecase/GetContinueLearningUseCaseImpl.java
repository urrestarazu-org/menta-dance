package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.VirtualApiClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GetContinueLearningUseCase}.
 *
 * <p>Fails closed without propagating, like the subscription card: a request without a token makes
 * no call at all, and an unavailable Virtual API only replaces the section with a notice. Log lines
 * carry the exception class only, so neither the token nor an upstream body can reach them.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class GetContinueLearningUseCaseImpl implements GetContinueLearningUseCase {

    /** How many courses the dashboard lists; the API defaults to ten. */
    static final int DASHBOARD_LIMIT = 5;

    private final VirtualApiClient virtualApiClient;

    @Override
    public ContinueLearningView execute(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return new ContinueLearningView(List.of(), true);
        }
        try {
            return new ContinueLearningView(
                    virtualApiClient.listInProgressCourses(accessToken, DASHBOARD_LIMIT), false);
        } catch (VirtualApiClient.ServiceUnavailableException e) {
            log.warn("Courses in progress unavailable, rendering the dashboard without them: {}",
                    e.getClass().getSimpleName());
            return new ContinueLearningView(List.of(), true);
        }
    }
}
