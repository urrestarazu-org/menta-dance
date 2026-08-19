package com.menta.virtual.application.dto;

import java.util.Optional;

public record UpdateVirtualModuleCommand(Optional<String> title, Optional<Integer> order) {
}
