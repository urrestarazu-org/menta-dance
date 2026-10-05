package com.menta.physical.infrastructure.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Derives the opaque, bounded rate-limit keys for the QR check-in (ADR-0044): a fingerprint of
 * the client IP and a fingerprint of the {@code deviceId}. Raw values never reach a Redis key.
 *
 * <p>Physical's own copy of the trusted-proxy criterion of ADR-0035 (modules do not share
 * internals, ADR-0020): {@code X-Forwarded-For} is honoured only when the immediate peer is a
 * trusted proxy, and then only its <em>last</em> entry, the one appended by the proxy itself.</p>
 *
 * <p>Registered under an explicit bean name because {@code :api:auth} ships a component whose
 * default name would be identical.</p>
 */
@Component("physicalClientFingerprint")
final class PhysicalClientFingerprint {

    private static final String SHA_256 = "SHA-256";
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final String trustedProxyCidrs;

    /** Falls back to the auth module's property so one value serves both in the common case. */
    PhysicalClientFingerprint(
        @Value("${physical.checkin.trusted-proxy-cidrs:${auth.trusted-proxy-cidrs:172.16.0.0/12}}")
        String trustedProxyCidrs
    ) {
        this.trustedProxyCidrs = trustedProxyCidrs;
    }

    /** Fingerprint of the originating client address. */
    String client(HttpServletRequest request) {
        return sha256Hex(sourceAddress(request));
    }

    /** Fingerprint of the submitted device id, which {@code CheckInRequest} already bounds. */
    String device(String deviceId) {
        return sha256Hex(deviceId);
    }

    private String sourceAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }
        String forwardedFor = request.getHeader(FORWARDED_FOR);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }
        String[] values = forwardedFor.split(",");
        if (values.length == 0) {
            return remoteAddress;
        }
        String address = values[values.length - 1].trim();
        return address.isBlank() ? remoteAddress : address;
    }

    private boolean isTrustedProxy(String address) {
        for (String cidr : trustedProxyCidrs.split(",")) {
            if (matchesCidr(address, cidr.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCidr(String address, String cidr) {
        if (cidr.isEmpty() || !cidr.contains("/")) {
            return false;
        }
        try {
            String[] parts = cidr.split("/", 2);
            byte[] candidate = InetAddress.getByName(address).getAddress();
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            int prefixLength = Integer.parseInt(parts[1]);
            if (candidate.length != network.length || prefixLength < 0
                || prefixLength > candidate.length * 8) {
                return false;
            }
            for (int bit = 0; bit < prefixLength; bit++) {
                int mask = 1 << (7 - bit % 8);
                if ((candidate[bit / 8] & mask) != (network[bit / 8] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException | NumberFormatException exception) {
            return false;
        }
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256)
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is mandatory on every JDK (JCA spec); unreachable in practice.
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }
}
