package com.menta.virtual.application.dto;

import java.util.Optional;

/**
 * Input of the module-creation use case for an existing course; the two-argument constructor
 * defaults {@code preview} to {@code false}.
 *
 * @param order empty means "append at the end".
 */
public record CreateVirtualModuleCommand(String title, Optional<Integer> order, boolean preview) {

    public CreateVirtualModuleCommand(String title, Optional<Integer> order) {
        this(title, order, false);
    }
}
