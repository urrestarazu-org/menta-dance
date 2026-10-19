package com.menta.bff.application.usecase;

/**
 * Use case for the public list of physical classes.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface GetClassesViewUseCase {

    /**
     * Builds the list of physical classes. Never throws on upstream failure: it
     * degrades to a view flagged {@link ClassesView#unavailable()}.
     *
     * @return the classes view
     */
    ClassesView execute();
}
