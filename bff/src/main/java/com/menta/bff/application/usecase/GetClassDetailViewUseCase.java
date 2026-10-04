package com.menta.bff.application.usecase;

/**
 * Use case for the in-person class page: course data, live session
 * availability and the published price.
 */
public interface GetClassDetailViewUseCase {

    /**
     * Assembles the class page.
     *
     * @param courseId course identifier
     * @return the page data; {@code pricing} is {@code null} when no price is published
     * @throws com.menta.bff.application.port.out.PhysicalCatalogApiClient.NotFoundException
     *         if the id is not an active in-person course
     * @throws com.menta.bff.application.port.out.PhysicalCatalogApiClient.ServiceUnavailableException
     *         if the catalog is unreachable
     * @throws com.menta.bff.application.port.out.PhysicalPurchaseApiClient.ServiceUnavailableException
     *         if billing is unreachable
     */
    ClassDetailView execute(String courseId);
}
