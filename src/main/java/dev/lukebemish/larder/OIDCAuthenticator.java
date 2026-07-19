package dev.lukebemish.larder;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.uuid.Generators;
import com.github.scribejava.apis.openid.OpenIdJsonTokenExtractor;
import com.github.scribejava.apis.openid.OpenIdOAuth2AccessToken;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.TokenExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.AccessTokenRequestParams;
import com.github.scribejava.core.oauth.OAuth20Service;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.schema.User;
import dev.lukebemish.larder.utils.ExpiringValue;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.Jwks;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

final class OIDCAuthenticator {
    private static final Logger logger = LoggerFactory.getLogger(OIDCAuthenticator.class);

    private static final JsonFactory factory = new JsonFactory();
    private static final ObjectMapper mapper = new ObjectMapper(factory);

    private final Key hmacKey;
    private final Key aesKey;
    private final OAuth20Service oauth2service;
    private final Function<String, String> authRedirectGenerator;
    private final AtomicInteger alive = new AtomicInteger();
    private final SecureRandom secureRandom = new SecureRandom();

    private final String clientSecret;
    private final String clientId;

    private final JwtParser idJwtParser;

    private final String redirectUrl;
    private final String userInfoEndpoint;

    private final OIDCProviderApi oidcProviderApi;

    private static class OIDCProviderApi extends DefaultApi20 {
        private final String accessTokenEndpoint;
        private final String authorizationBaseUrl;

        private OIDCProviderApi(String accessTokenEndpoint, String authorizationBaseUrl) {
            this.accessTokenEndpoint = accessTokenEndpoint;
            this.authorizationBaseUrl = authorizationBaseUrl;
        }

        @Override
        public String getAccessTokenEndpoint() {
            return accessTokenEndpoint;
        }

        @Override
        protected String getAuthorizationBaseUrl() {
            return authorizationBaseUrl;
        }

        @Override
        public TokenExtractor<OAuth2AccessToken> getAccessTokenExtractor() {
            return OpenIdJsonTokenExtractor.instance();
        }
    }

    public OIDCAuthenticator(String issuer, String clientId, String clientSecret, String host) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;

        try {
            this.hmacKey = KeyGenerator.getInstance("HmacSHA256").generateKey();
            this.aesKey = KeyGenerator.getInstance("AES").generateKey();

            this.alive.set(secureRandom.nextInt());

            var wellKnownUrl = new URI(issuer + (issuer.endsWith("/") ? "" : "/") + ".well-known/openid-configuration").toURL();
            try (var config = wellKnownUrl.openStream()) {
                var configTree = mapper.readTree(config);
                var authorization = configTree.get("authorization_endpoint").asText();
                var tokenEndpoint = configTree.get("token_endpoint").asText();
                this.oidcProviderApi = new OIDCProviderApi(tokenEndpoint, authorization);

                this.redirectUrl = String.format("%s/login", host);

                var jwksUri = new URI(configTree.get("jwks_uri").asText()).toURL();
                var jwkSet = new ExpiringValue<>(() -> {
                    try (var is = jwksUri.openStream()) {
                        return Jwks.setParser()
                            .build()
                            .parse(is);
                    }
                }, Duration.of(1, ChronoUnit.DAYS));

                this.idJwtParser = Jwts.parser()
                    .requireAudience(clientId)
                    .requireIssuer(issuer)
                    .keyLocator(header -> {
                        try {
                            var kid = header.get("kid");
                            if (kid == null) {
                                return null;
                            }
                            var foundKey = jwkSet.get().getKeys().stream()
                                .filter(k -> k.getId().equals(kid))
                                .findFirst();
                            if (foundKey.isEmpty()) {
                                // Are there newer keys?
                                jwkSet.reset();
                                foundKey = jwkSet.get().getKeys().stream()
                                    .filter(k -> k.getId().equals(kid))
                                    .findFirst();
                            }
                            // Fail, invalid signature
                            return foundKey.<Key>map(Jwk::toKey).orElse(null);
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .build();

                this.userInfoEndpoint = configTree.get("userinfo_endpoint").asText();

                this.oauth2service = new ServiceBuilder(clientId)
                    .apiSecret(clientSecret)
                    .build(oidcProviderApi);

                this.authRedirectGenerator = target -> {
                    String state;
                    try {
                        var cipher = Cipher.getInstance("AES");
                        cipher.init(Cipher.ENCRYPT_MODE, this.aesKey);
                        var jwt = redirectJwt(target);
                        state = Base64.getUrlEncoder().encodeToString(cipher.doFinal(jwt.getBytes(StandardCharsets.UTF_8)));
                    } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException |
                             BadPaddingException | InvalidKeyException e) {
                        throw new RuntimeException(e);
                    }

                    return this.oidcProviderApi.getAuthorizationUrl(
                        "code",
                        clientId,
                        redirectUrl,
                        "openid",
                        state,
                        Map.of()
                    );
                };
            }
        } catch (URISyntaxException | IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void fillLoginRedirect(Context context) {
        var template = context.appData(Larder.TEMPLATE_ENGINE_KEY).getTemplate("auth-page.html");
        var writer = new StringWriter();
        try {
            template.evaluate(writer, Map.of(
                "redirect", getAuthUrl(context.fullUrl())
            ), Locale.ROOT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        context.html(writer.toString());
    }

    public void handleLoginRedirect(Context context) {
        var code = context.queryParam("code");
        var state = context.queryParam("state");
        if (code == null || state == null) {
            throw new BadRequestResponse("Authentication failed");
        }
        String redirectJwt;
        try {
            var cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, aesKey);
            redirectJwt = new String(cipher.doFinal(Base64.getUrlDecoder().decode(state)), StandardCharsets.UTF_8);
        } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException | BadPaddingException |
                 InvalidKeyException e) {
            throw new RuntimeException(e);
        }

        var jwtJson = validateJwt(redirectJwt);
        if (jwtJson == null) {
            System.out.println(redirectJwt);
            throw new BadRequestResponse("Authentication failed");
        }

        var destination = jwtJson.get("destination").asText();

        try {
            var token = (OpenIdOAuth2AccessToken) oauth2service.getAccessToken(new AccessTokenRequestParams(code)
                .addExtraParameter("redirect_uri", this.redirectUrl));

            var idToken = token.getOpenIdToken();

            Jws<Claims> idTokenJwt;
            try {
                idTokenJwt = idJwtParser.parseSignedClaims(idToken);
            } catch (JwtException e) {
                throw new BadRequestResponse("Authentication failed");
            }

            var sub = idTokenJwt.getPayload().getSubject();
            var accessToken = token.getAccessToken();

            var authType = token.getTokenType();
            if (!authType.toLowerCase(Locale.ROOT).equals("bearer")) {
                // Must be bearer auth
                logger.warn("OIDC provider gave non-bearer-auth access token of type '"+authType+"'; cannot use it to authenticate!");
                throw new BadRequestResponse("Authentication failed");
            }

            var userInfoRequest = new OAuthRequest(Verb.GET, userInfoEndpoint);
            userInfoRequest.addHeader("Authorization", "Bearer "+accessToken);
            userInfoRequest.addQuerystringParameter("schema", "openid");
            var userInfo = oauth2service.execute(userInfoRequest);

            var userInfoJson = mapper.readTree(userInfo.getBody());
            var userInfoSub = userInfoJson.get("sub").asText();
            if (!userInfoSub.equals(sub)) {
                // These don't match... so we need to error
                logger.warn("OIDC provider gave non-matching 'sub' from identity token and userinfo; cannot use it to authenticate!");
                throw new BadRequestResponse("Authentication failed");
            }
            var email = userInfoJson.get("email");
            if (email == null) {
                logger.warn("OIDC provider failed to provide email in userinfo; cannot use it to authenticate!");
                throw new BadRequestResponse("Authentication failed");
            }

            // Deterministically generated from the sub
            var userUUID = Generators.nameBasedGenerator(ApiMethods.UUID_ISS).generate(sub);

            // We have enough info to get / query the user now...
            context.appData(Larder.CONNECTION_KEY).transact(c -> {
                var user = new User(email.asText(), userUUID);
                var userId = Identifier.of(user);
                var existing = c.find(userId);
                if (existing.isPresent()) {
                    if (!existing.get().equals(user)) {
                        c.update(user);
                    }
                } else {
                    c.insert(user);
                }
            });

            // Finally, make the session JWT token and add as a cookie
            // This requires knowing roles
            var userJwt = userJwt(userUUID, Set.of(Role.Builtin.ADMIN, Role.Builtin.USER) /* TODO: implement */);
            context.cookie(SESSION_TOKEN_COOKIE, userJwt);

            context.redirect(destination);
        } catch (IOException | SQLException | InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void invalidateAllSessions() {
        alive.set(secureRandom.nextInt());
    }

    private static final String JWT_HEADER = Base64.getUrlEncoder().encodeToString("{\"typ\":\"JWT\",\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));

    private String userJwt(UUID userId, Set<Role> roles) {
        ObjectNode node = mapper.createObjectNode();
        node.put("user", userId.toString());
        var rolesNode = mapper.createArrayNode();
        for (var role : roles) {
            rolesNode.add(role.unique());
        }
        node.set("roles", rolesNode);
        return jwt(node);
    }

    private String redirectJwt(String destination) {
        ObjectNode node = mapper.createObjectNode();
        node.put("destination", destination);
        return jwt(node);
    }

    private String jwt(ObjectNode bodyJson) {
        var expiration = Instant.now().plus(10, ChronoUnit.MINUTES).getEpochSecond();
        bodyJson.put("exp", expiration);
        bodyJson.put("alive", alive.get());
        var bodyString = bodyJson.toString();
        var body = Base64.getUrlEncoder().encodeToString(bodyString.getBytes(StandardCharsets.UTF_8));
        try {
            var hmac = Mac.getInstance("HmacSHA256");
            hmac.init(this.hmacKey);
            var signature = Base64.getUrlEncoder().encodeToString(hmac.doFinal(bodyString.getBytes(StandardCharsets.UTF_8)));

            return String.format("%s.%s.%s", JWT_HEADER, body, signature);
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String SESSION_TOKEN_COOKIE = "session_token";

    public Set<Role> userRoles(Context context) {
        if (context.attribute(Larder.AUTH_INFO_KEY) instanceof Larder.AuthInfo authInfo) {
            return authInfo.roles();
        }
        var sessionJwt = context.cookie(SESSION_TOKEN_COOKIE);
        if (sessionJwt != null) {
            var info = parseUserJwt(context, sessionJwt);
            if (info != null) {
                context.attribute(Larder.AUTH_INFO_KEY, info);
                return info.roles();
            }
        }

        return Set.of();
    }

    private String getAuthUrl(String target) {
        return authRedirectGenerator.apply(target);
    }

    private @Nullable JsonNode validateJwt(String jwt) {
        var parts = jwt.split("\\.");
        if (parts.length == 3) {
            String body;
            byte[] signature;
            try {
                body = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                signature = Base64.getUrlDecoder().decode(parts[2]);
            } catch (IllegalArgumentException e) {
                // Not Base64
                return null;
            }
            try {
                var hmac = Mac.getInstance("HmacSHA256");
                hmac.init(this.hmacKey);
                var recreatedSignature = hmac.doFinal(body.getBytes(StandardCharsets.UTF_8));
                if (!Arrays.equals(signature, recreatedSignature)) {
                    // Not our session token!
                    return null;
                }
                var bodyJson = mapper.readTree(body);
                if (bodyJson.get("alive").asInt() != alive.get()) {
                    // The token has been invalidated
                    return null;
                }
                var expiration = Instant.ofEpochSecond(bodyJson.get("exp").asLong());
                var now = Instant.now();
                if (expiration.isBefore(now)) {
                    // Token has expired
                    return null;
                }
                return bodyJson;
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException(e);
            } catch (JsonProcessingException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    private Larder.@Nullable AuthInfo parseUserJwt(Context context, String sessionJwt) {
        var bodyJson = validateJwt(sessionJwt);
        if (bodyJson == null) {
            return null;
        }
        var userUUID = UUID.fromString(bodyJson.get("user").asText());
        var roles = bodyJson.get("roles").valueStream().map(JsonNode::asText).<Role>map(roleText -> {
            try {
                return Role.Builtin.valueOf(roleText.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());
        var expiration = Instant.ofEpochSecond(bodyJson.get("exp").asLong());
        var now = Instant.now();
        if (expiration.isAfter(now.plus(5, ChronoUnit.MINUTES))) {
            // Token can be refreshed
            var newToken = userJwt(userUUID, roles);
            context.cookie(SESSION_TOKEN_COOKIE, newToken);
        }
        var userId = new User.Id(userUUID);
        return new Larder.AuthInfo(userId, roles);
    }
}
