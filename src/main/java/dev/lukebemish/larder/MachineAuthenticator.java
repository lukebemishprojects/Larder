package dev.lukebemish.larder;

import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.ModelConnection;
import dev.lukebemish.larder.schema.AccessRole;
import dev.lukebemish.larder.schema.AccessToken;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RoleNamespace;
import dev.lukebemish.larder.schema.RoleRepository;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

final class MachineAuthenticator {
    private MachineAuthenticator() {}

    public static Identifier<AccessRole> machineRole(Context context, ModelConnection connection) throws SQLException {
        var role = maybeMachineRole(context, connection);
        if (role.isEmpty()) {
            throw new UnauthorizedResponse();
        }
        return role.get();
    }

    public static Optional<Identifier<AccessRole>> maybeMachineRole(Context context, ModelConnection connection) throws SQLException {
        var authHeader = context.header("Authorization");
        if (authHeader == null) {
            return Optional.empty();
        }
        var parts = authHeader.split(" ");
        if (parts.length != 2) {
            throw new UnauthorizedResponse();
        }
        // Both "basic" and "bearer" are allowed here to match quirks of central portal publishing -- actual auth may be either
        if (!parts[0].equals("Bearer") && !parts[0].equals("Basic")) {
            throw new UnauthorizedResponse();
        }

        if (parts[1].contains(".")) {
            // In the future, this will handle JWT auth
            // for now... welp, it's definitely not a basic auth string!
            throw new UnauthorizedResponse();
        }

        var decodedContent = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        var basicParts = decodedContent.split(":", 2);
        if (basicParts.length != 2) {
            throw new UnauthorizedResponse();
        }

        var tokens = connection.select(new AccessToken.ByKey(basicParts[0]));
        if (tokens.isEmpty()) {
            throw new UnauthorizedResponse();
        }

        var token = tokens.getFirst();
        var hash = ApiTokens.hashToken(token.salt(), Base64.getUrlDecoder().decode(basicParts[1]));

        if (!Arrays.equals(hash, token.hash())) {
            throw new UnauthorizedResponse();
        }

        return Optional.of(token.role());
    }

    // Should be called *after* validating repository, as relevant
    public static void validateRolePublish(Identifier<AccessRole> role, ModelConnection connection) throws SQLException {
        if (!connection.select(role).canPublish()) {
            throw new ForbiddenResponse("This role cannot publish");
        }
    }

    public static void validateRole(Identifier<AccessRole> role, Identifier<Repository> repository, ModelConnection connection) throws SQLException {
        var roleRepository = connection.select(new RoleRepository.ByBoth(role, repository));
        if (roleRepository.isEmpty()) {
            throw new NotFoundResponse();
        }
    }

    public static void validateRole(Identifier<AccessRole> role, String namespace, ModelConnection connection) throws SQLException {
        var roleNamespaces = connection.select(new RoleNamespace.ByRole(role));
        var anyValid = false;
        for (var maybeNamespace : roleNamespaces) {
            if (maybeNamespace.value().equals(namespace) || namespace.startsWith(maybeNamespace.value() + ".")) {
                anyValid = true;
                break;
            }
        }
        if (!anyValid) {
            throw new NotFoundResponse();
        }
    }
}
