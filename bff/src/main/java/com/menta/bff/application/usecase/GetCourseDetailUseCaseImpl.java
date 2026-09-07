package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.port.out.VirtualApiClient;

import java.util.Objects;

/**
 * Implementation of {@link GetCourseDetailUseCase}.
 * <p>
 * Thin pass-through with no branching — course detail is access-agnostic
 * (design D2), so the same result renders for every caller. Port exceptions
 * propagate untranslated; the controller decides how each one renders.
 * </p>
 */
public class GetCourseDetailUseCaseImpl implements GetCourseDetailUseCase {

    private final VirtualApiClient virtualApiClient;

    /**
     * Constructor for dependency injection.
     *
     * @param virtualApiClient HTTP client for the Virtual/Catalog API
     */
    public GetCourseDetailUseCaseImpl(VirtualApiClient virtualApiClient) {
        this.virtualApiClient = Objects.requireNonNull(virtualApiClient, "virtualApiClient cannot be null");
    }

    @Override
    public CourseDetail execute(String courseId) {
        Objects.requireNonNull(courseId, "courseId cannot be null");
        return virtualApiClient.getCourseDetail(courseId);
    }
}
