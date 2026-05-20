package com.example.demo.security;

import com.example.demo.exception.UnauthorizedException;
import com.example.demo.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtTokenProvider {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    private final Clock clock;
    private final byte[] secret;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final String issuer;

    @Autowired
    public JwtTokenProvider(
            @Value("${jwt.secret:change-this-secret-for-local-development}") String secret,
            @Value("${jwt.access-ttl-seconds:900}") long accessTtlSeconds,
            @Value("${jwt.refresh-ttl-seconds:604800}") long refreshTtlSeconds,
            @Value("${jwt.issuer:demo-api}") String issuer
    ) {
        this(Clock.systemUTC(), secret, accessTtlSeconds, refreshTtlSeconds, issuer);
    }

    JwtTokenProvider(Clock clock, String secret, long accessTtlSeconds, long refreshTtlSeconds, String issuer) {
        this.clock = clock;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.issuer = issuer;
    }

    public String createAccessToken(User user, Long sessionId) {
        return createToken(user, ACCESS_TYPE, sessionId, UUID.randomUUID().toString(), accessTtlSeconds);
    }

    public RefreshTokenData createRefreshToken(User user, Long sessionId) {
        String tokenId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now(clock).plusSeconds(refreshTtlSeconds);
        String token = createToken(user, REFRESH_TYPE, sessionId, tokenId, refreshTtlSeconds);
        return new RefreshTokenData(token, tokenId, expiresAt);
    }

    public JwtClaims validateAccessToken(String token) {
        JwtClaims claims = validateToken(token);
        if (!ACCESS_TYPE.equals(claims.type())) {
            throw new UnauthorizedException("Access token is required");
        }
        return claims;
    }

    public JwtClaims validateRefreshToken(String token) {
        JwtClaims claims = validateToken(token);
        if (!REFRESH_TYPE.equals(claims.type())) {
            throw new UnauthorizedException("Refresh token is required");
        }
        return claims;
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }

    private String createToken(User user, String type, Long sessionId, String tokenId, long ttlSeconds) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        User.Role role = user.getRole() == null ? User.Role.USER : user.getRole();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", user.getUsername());
        payload.put("uid", user.getId());
        payload.put("username", user.getUsername());
        payload.put("role", role.name());
        payload.put("type", type);
        payload.put("sid", sessionId);
        payload.put("jti", tokenId);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String unsignedToken = base64Url(toJson(header).getBytes(StandardCharsets.UTF_8))
                + "." + base64Url(toJson(payload).getBytes(StandardCharsets.UTF_8));
        return unsignedToken + "." + sign(unsignedToken);
    }

    private JwtClaims validateToken(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw new UnauthorizedException("Invalid token");
        }
        String unsignedToken = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsignedToken), parts[2])) {
            throw new UnauthorizedException("Invalid token signature");
        }

        Map<String, Object> payload = fromJson(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        String tokenIssuer = asString(payload.get("iss"));
        if (!issuer.equals(tokenIssuer)) {
            throw new UnauthorizedException("Invalid token issuer");
        }

        Instant expiresAt = Instant.ofEpochSecond(asLong(payload.get("exp")));
        if (!expiresAt.isAfter(Instant.now(clock))) {
            throw new UnauthorizedException("Token has expired");
        }

        return new JwtClaims(
                asLong(payload.get("uid")),
                asString(payload.get("sub")),
                asString(payload.get("role")),
                asString(payload.get("type")),
                asLong(payload.get("sid")),
                asString(payload.get("jti")),
                expiresAt
        );
    }

    private String toJson(Map<String, Object> value) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escapeJson(entry.getKey())).append('"').append(':');
            if (entry.getValue() instanceof Number number) {
                json.append(number);
            } else {
                json.append('"').append(escapeJson(String.valueOf(entry.getValue()))).append('"');
            }
        }
        return json.append('}').toString();
    }

    private Map<String, Object> fromJson(String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != '{' || value.charAt(value.length() - 1) != '}') {
            throw new UnauthorizedException("Invalid token payload");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        String body = value.substring(1, value.length() - 1);
        int index = 0;
        while (index < body.length()) {
            ParsedString key = readJsonString(body, index);
            index = skipWhitespace(body, key.nextIndex());
            if (index >= body.length() || body.charAt(index) != ':') {
                throw new UnauthorizedException("Invalid token payload");
            }
            index = skipWhitespace(body, index + 1);
            Object parsedValue;
            if (index < body.length() && body.charAt(index) == '"') {
                ParsedString stringValue = readJsonString(body, index);
                parsedValue = stringValue.value();
                index = stringValue.nextIndex();
            } else {
                int start = index;
                while (index < body.length() && body.charAt(index) != ',') {
                    index++;
                }
                try {
                    parsedValue = Long.parseLong(body.substring(start, index).trim());
                } catch (NumberFormatException exception) {
                    throw new UnauthorizedException("Invalid token payload");
                }
            }
            result.put(key.value(), parsedValue);
            index = skipWhitespace(body, index);
            if (index < body.length()) {
                if (body.charAt(index) != ',') {
                    throw new UnauthorizedException("Invalid token payload");
                }
                index = skipWhitespace(body, index + 1);
            }
        }
        return result;
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return base64Url(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot sign JWT", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        if (expectedBytes.length != actualBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < expectedBytes.length; i++) {
            result |= expectedBytes[i] ^ actualBytes[i];
        }
        return result == 0;
    }

    private String asString(Object value) {
        if (value == null) {
            throw new UnauthorizedException("Required token claim is missing");
        }
        return String.valueOf(value);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(asString(value));
        } catch (NumberFormatException exception) {
            throw new UnauthorizedException("Invalid token claim");
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private ParsedString readJsonString(String value, int index) {
        if (index >= value.length() || value.charAt(index) != '"') {
            throw new UnauthorizedException("Invalid token payload");
        }
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = index + 1; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                result.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                return new ParsedString(result.toString(), i + 1);
            } else {
                result.append(current);
            }
        }
        throw new UnauthorizedException("Invalid token payload");
    }

    private int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    public record JwtClaims(Long userId, String subject, String role, String type, Long sessionId, String tokenId, Instant expiresAt) {
    }

    public record RefreshTokenData(String token, String tokenId, Instant expiresAt) {
    }

    private record ParsedString(String value, int nextIndex) {
    }
}
