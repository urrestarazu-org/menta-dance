package com.menta.bff.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.bff.application.port.out.AccessTokenClaimsReader;
import java.util.Base64;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads the {@code role} claim from the payload of an Auth API JWT.
 *
 * <p>The signature is not verified here, deliberately: the BFF has no signing
 * key, and the token reaches it straight from the Auth API's login response,
 * the same channel the BFF already trusts for the token itself. The role only
 * shapes the web session; every API call is still authorized by the API
 * against the verified token.</p>
 *
 * <p>The role must look like an enum constant name. Anything else fails the
 * login instead of becoming an unexpected authority.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAccessTokenClaimsReader implements AccessTokenClaimsReader {

    private static final String ROLE_CLAIM = "role";
    private static final Pattern ROLE_NAME = Pattern.compile("[A-Z][A-Z_]*");

    private final ObjectMapper objectMapper;

    @Override
    public String roleOf(String accessToken) {
        if (accessToken == null) {
            throw new UnreadableTokenException("Access token is missing");
        }
        String[] parts = accessToken.split("\\.", -1);
        if (parts.length != 3) {
            throw new UnreadableTokenException("Access token is not a compact JWT");
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        } catch (Exception e) {
            throw new UnreadableTokenException("Access token payload is not readable JSON", e);
        }

        JsonNode role = payload == null ? null : payload.get(ROLE_CLAIM);
        if (role == null || !role.isTextual() || !ROLE_NAME.matcher(role.asText()).matches()) {
            throw new UnreadableTokenException("Access token carries no valid role claim");
        }
        return role.asText();
    }
}
