package com.menta.physical.application.dto;

import java.time.Instant;

/**
 * @param expiresAt optional, inert metadata in this change (D2) — no enforcement occurs anywhere
 *     until the Escenario 5 follow-up consumes it.
 */
public record RegisterPhysicalDeviceCommand(String name, String location, Instant expiresAt) {
}
