package com.menta.bff.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the canonical address of the client that originated a request
 * (ADR-0035, NGINX → BFF hop).
 *
 * <p>NGINX applies {@code proxy_set_header X-Real-IP $remote_addr}, which
 * overwrites any value the caller supplied. The header is therefore a
 * trustworthy, already-sanitised single address — but only when the request
 * genuinely arrived through NGINX. This class enforces that condition: the
 * header is read only when the immediate peer sits inside a configured trusted
 * range, and is ignored otherwise.</p>
 *
 * <p>Getting this wrong is not a small bug. If an untrusted caller could set
 * its own origin, it would present a different identity on every request and
 * a per-origin budget would stop meaning anything.</p>
 *
 * <p>Deliberately never reads {@code X-Forwarded-For}: parsing proxy chains is
 * exactly the ambiguity {@code X-Real-IP} lets us avoid here.</p>
 */
@Component
public class ClientOriginResolver {

    private static final String REAL_IP_HEADER = "X-Real-IP";

    private final String trustedProxyCidrs;

    public ClientOriginResolver(
        @Value("${menta.trusted-proxy-cidrs:${auth.activation.trusted-proxy-cidrs:172.16.0.0/12}}")
        String trustedProxyCidrs
    ) {
        this.trustedProxyCidrs = trustedProxyCidrs == null ? "" : trustedProxyCidrs;
    }

    /**
     * @return the originating client address, or the immediate peer's address
     *     when no trustworthy origin can be established.
     */
    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!isTrustedProxy(peer)) {
            return peer;
        }
        String realIp = request.getHeader(REAL_IP_HEADER);
        if (realIp == null || realIp.isBlank()) {
            return peer;
        }
        return realIp.trim();
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
            if (candidate.length != network.length
                || prefixLength < 0
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
            // A malformed entry must never widen trust.
            return false;
        }
    }
}
