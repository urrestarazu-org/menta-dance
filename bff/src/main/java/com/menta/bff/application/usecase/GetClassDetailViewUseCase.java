package com.menta.bff.application.usecase;

/**
 * Use case for the public page of one physical class. Anonymous visitors can see it; a signed-in
 * student also sees which sessions they already hold.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface GetClassDetailViewUseCase {

    /**
     * Builds the detail of a physical class with its upcoming sessions.
     *
     * @param courseId    course identifier
     * @param accessToken the signed-in student's access token, {@code null} for an anonymous visitor
     * @return the detail view, flagged {@link ClassDetailView#unavailable()} when the catalog is down
     * @throws com.menta.bff.application.port.out.CatalogApiClient.NotFoundException if the id is unknown,
     *         unpublished or not a physical course
     */
    ClassDetailView execute(String courseId, String accessToken);
}
