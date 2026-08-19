package com.menta.virtual.application.dto;

import java.util.Optional;

/** @param order empty means "append at the end". */
public record CreateVirtualModuleCommand(String title, Optional<Integer> order) {
}
