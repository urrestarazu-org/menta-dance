package com.menta.bff.application.usecase;

import java.util.List;

/**
 * Use case for the in-person class list page.
 */
public interface GetClassesViewUseCase {

    /**
     * Lists the in-person courses, in catalog order.
     *
     * @return one card per physical course
     * @throws com.menta.bff.application.port.out.PhysicalCatalogApiClient.ServiceUnavailableException
     *         if the catalog is unreachable
     */
    List<ClassCard> execute();
}
