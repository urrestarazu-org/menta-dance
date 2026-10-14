package com.menta.bff.application.dto;

import java.util.List;

/**
 * Rendering data of the plans page.
 *
 * @param plans plan catalog, in upstream order and identical for every viewer
 * @param offer what is offered to the current viewer
 */
public record PlansView(List<PlanSummary> plans, PlansOffer offer) {
}
