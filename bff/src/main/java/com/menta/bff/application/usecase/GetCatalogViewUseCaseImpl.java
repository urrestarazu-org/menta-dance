package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.domain.model.CourseModality;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GetCatalogViewUseCase}.
 *
 * <p>The upstream list has no modality parameter, so the filter is applied
 * here. Unlike the plans page, an upstream failure does not propagate: the home
 * page is the landing page and must still render with a degraded notice.
 * </p>
 */
@Slf4j
public class GetCatalogViewUseCaseImpl implements GetCatalogViewUseCase {

    private final CatalogApiClient catalogApiClient;

    public GetCatalogViewUseCaseImpl(CatalogApiClient catalogApiClient) {
        this.catalogApiClient = Objects.requireNonNull(catalogApiClient, "catalogApiClient cannot be null");
    }

    @Override
    public CatalogView execute(String modality) {
        CourseModality selected = CourseModality.fromFilter(modality).orElse(null);
        try {
            List<CatalogCard> cards = catalogApiClient.listCourses().stream()
                    .filter(course -> selected == null || course.modality() == selected)
                    .map(CatalogCard::from)
                    .toList();
            return new CatalogView(cards, selected, false);
        } catch (CatalogApiClient.ServiceUnavailableException e) {
            log.warn("Catalog unavailable, rendering degraded home page: {}", e.getMessage());
            return new CatalogView(List.of(), selected, true);
        }
    }
}
