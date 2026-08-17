package dev.lukebemish.larder;

import dev.lukebemish.larder.api.AccessTokenApi;
import dev.lukebemish.larder.api.AccessTokenRequest;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.schema.AccessToken;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.TokenNamespace;
import dev.lukebemish.larder.schema.TokenRepository;
import dev.lukebemish.larder.schema.User;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.openapi.OpenApiSecurity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;

import static dev.lukebemish.larder.Api.authenticatedUser;
import static dev.lukebemish.larder.Api.canPublishToNamespace;
import static dev.lukebemish.larder.Api.connection;
import static dev.lukebemish.larder.Api.isValidNamespace;
import static dev.lukebemish.larder.Api.self;

final class ApiTokens {
    private static final Duration MAXIMUM_KEY_LIFETIME = Duration.of(60, ChronoUnit.DAYS);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @OpenApi(
        path = "/dashboard/api/tokens",
        methods = HttpMethod.GET,
        summary = "List access tokens",
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = AccessTokenApi[].class))
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    static void listTokens(Context context) throws SQLException {
        var userId = authenticatedUser(context);
        connection(context).transact(c -> {
            var tokens = new ArrayList<AccessTokenApi>();
            for (var token : c.select(new AccessToken.ByOwner(userId))) {
                var namespaces = new ArrayList<String>();
                var repositories = new ArrayList<String>();
                for (var namespace : c.select(new TokenNamespace.ByToken(Identifier.of(token)))) {
                    namespaces.add(namespace.value());
                }
                for (var repository : c.select(new TokenRepository.ByToken(Identifier.of(token)))) {
                    var repo = c.select(repository.value());
                    repositories.add(repo.name());
                }
                tokens.add(new AccessTokenApi(
                    token.humanName(),
                    token.key(),
                    null,
                    namespaces,
                    repositories,
                    token.canPublish(),
                    token.expiry().toInstant(ZoneOffset.ofHours(0))
                ));
            }
            context.json(tokens);
        });
    }

    @OpenApi(
        path = "/dashboard/api/tokens/{id}",
        methods = HttpMethod.DELETE,
        pathParams = @OpenApiParam(name = "id"),
        summary = "Revoke access token",
        responses = {
            @OpenApiResponse(status = "204", description = "Token revoked")
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    static void revokeToken(Context context) throws SQLException {
        var userId = authenticatedUser(context);
        var tokenId = new AccessToken.ByKey(context.pathParam("id"));
        connection(context).transact(c -> {
            var token = c.select(tokenId);
            if (token.isEmpty() || !token.getFirst().owner().id().equals(userId.id())) {
                throw new NotFoundResponse("Token not found");
            }
            c.delete(Identifier.of(token.getFirst()));
            context.status(HttpStatus.NO_CONTENT);
        });
    }

    @OpenApi(
        path = "/dashboard/api/tokens",
        methods = HttpMethod.POST,
        summary = "Create access token",
        requestBody = @OpenApiRequestBody(
            content = @OpenApiContent(
                from = AccessTokenRequest.class
            )
        ),
        responses = {
            @OpenApiResponse(status = "200", content = @OpenApiContent(from = AccessTokenApi.class))
        },
        security = @OpenApiSecurity(name = "dashboardCookie"),
        tags = {"Dashboard"}
    )
    static void issueToken(Context context) throws SQLException {
        var tokenRequest = context.bodyAsClass(AccessTokenRequest.class);
        self(context, tokenRequest.user());
        connection(context).transact(c -> {
            var user = Identifier.of(User.REPRESENTATION, tokenRequest.user());

            for (var namespace : tokenRequest.namespaces()) {
                if (!isValidNamespace(namespace)) {
                    throw new BadRequestResponse("Invalid namespace: "+namespace);
                }
                if (tokenRequest.canPublish() && !canPublishToNamespace(c, user, namespace)) {
                    throw new ForbiddenResponse("No permission to publish to namespace: "+namespace);
                }
            }

            var repositoryIds = new ArrayList<Identifier<Repository>>();
            for (var repository : tokenRequest.repositories()) {
                var repo = c.select(new Repository.ByName(repository));
                if (repo.isEmpty()) {
                    throw new BadRequestResponse("Not a repository: "+repository);
                }
                repositoryIds.add(Identifier.of(repo.getFirst()));
            }

            if (tokenRequest.lifetime().compareTo(MAXIMUM_KEY_LIFETIME) > 0) {
                throw new BadRequestResponse("Expiry time is too far in the future: "+tokenRequest.lifetime());
            }

            var key = new byte[24];
            var salt = new byte[12];
            var token = new byte[96];
            SECURE_RANDOM.nextBytes(key);
            SECURE_RANDOM.nextBytes(salt);
            SECURE_RANDOM.nextBytes(token);

            byte[] hash;
            try {
                var digest = MessageDigest.getInstance("SHA-512");
                digest.update(salt);
                digest.update(token);
                hash = digest.digest();
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            var keyString = Base64.getUrlEncoder().encodeToString(key);
            var tokenString = Base64.getUrlEncoder().encodeToString(token);

            var accessToken = new AccessToken(
                UUID.randomUUID(),
                keyString,
                salt,
                hash,
                tokenRequest.name(),
                user,
                LocalDateTime.ofInstant(
                    Instant.now().plus(tokenRequest.lifetime()),
                    ZoneOffset.ofHours(0)
                ),
                tokenRequest.canPublish()
            );
            c.insert(accessToken);
            var tokenId = Identifier.of(accessToken);
            for (var namespace : tokenRequest.namespaces()) {
                c.insert(new TokenNamespace(tokenId, namespace));
            }
            for (var id : repositoryIds) {
                c.insert(new TokenRepository(tokenId, id));
            }
            context.json(new AccessTokenApi(
                tokenRequest.name(),
                keyString,
                tokenString,
                tokenRequest.namespaces(),
                tokenRequest.repositories(),
                tokenRequest.canPublish(),
                accessToken.expiry().toInstant(ZoneOffset.ofHours(0))
            ));
        });
    }
}
