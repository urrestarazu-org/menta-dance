package com.menta.virtual.application.dto;

import java.util.List;

/** {@code moduleIdsInOrder} must be exactly the course's current module set, in the new desired order. */
public record ReorderVirtualModulesCommand(List<String> moduleIdsInOrder) {
}
