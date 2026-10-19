package com.menta.virtual.application.dto;

import java.util.Optional;

/**
 * Partial module update: an empty {@link Optional} leaves the corresponding field unchanged. The
 * two-argument constructor leaves {@code preview} unchanged.
 */
public record UpdateVirtualModuleCommand(Optional<String> title, Optional<Integer> order, Optional<Boolean> preview) {

    public UpdateVirtualModuleCommand(Optional<String> title, Optional<Integer> order) {
        this(title, order, Optional.empty());
    }
}
