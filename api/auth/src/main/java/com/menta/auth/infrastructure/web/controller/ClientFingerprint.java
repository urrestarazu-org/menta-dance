package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.domain.crypto.Sha256Hex;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Derives an opaque, non-reversible rate-limit key from the directly connected client. */
@Component
final class ClientFingerprint {

    private final String trustedProxyCidrs;

    ClientFingerprint(@Value("${auth.activation.trusted-proxy-cidrs:172.16.0.0/12}") String trustedProxyCidrs) {
        this.trustedProxyCidrs = trustedProxyCidrs;
    }

    String from(HttpServletRequest request) {
        return Sha256Hex.hash(sourceAddress(request, trustedProxyCidrs));
    }

    static String sourceAddress(HttpServletRequest request, String trustedProxyCidrs) {
        String remoteAddress = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddress, trustedProxyCidrs)) {
            return remoteAddress;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }
        String[] values = forwardedFor.split(",");
        String address = values[values.length - 1].trim();
        return address.isBlank() ? remoteAddress : address;
    }

    private static boolean isTrustedProxy(String address, String cidrs) {
        for (String cidr : cidrs.split(",")) {
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
            if (candidate.length != network.length || prefixLength < 0 || prefixLength > candidate.length * 8) {
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
}
