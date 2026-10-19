package com.menta.physical.application.dto;

/**
 * Returned by exactly two in-ports, {@code register} and {@code rotate} (#44, US-PHYSICAL-007,
 * design C2, D4). Nothing persists this type; {@code rawSecret} exists only on this return path
 * and is unreachable after the HTTP response is written.
 */
public record PhysicalDeviceSecretResult(PhysicalDeviceView device, String rawSecret) {
}
