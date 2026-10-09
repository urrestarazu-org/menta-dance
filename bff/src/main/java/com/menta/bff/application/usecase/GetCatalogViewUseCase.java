package com.menta.bff.application.usecase;

/**
 * Use case for retrieving the home page's public course catalog.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface GetCatalogViewUseCase {

    /**
     * Builds the catalog view, optionally filtered by modality. Never throws on
     * upstream failure: it degrades to a view flagged {@link CatalogView#unavailable()}.
     *
     * @param modality raw modality filter ({@code virtual} or {@code physical});
     *                 {@code null}, blank or unknown values mean "all"
     * @return the catalog view
     */
    CatalogView execute(String modality);
}
