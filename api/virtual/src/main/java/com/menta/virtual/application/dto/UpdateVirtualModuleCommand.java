package com.menta.virtual.application.dto;

import java.util.Optional;

public record UpdateVirtualModuleCommand(Optional<String> title, Optional<Integer> order, Optional<Boolean> preview) {

    public UpdateVirtualModuleCommand(Optional<String> title, Optional<Integer> order) {
        this(title, order, Optional.empty());
    }
}
