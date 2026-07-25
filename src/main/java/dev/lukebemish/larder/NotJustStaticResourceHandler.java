package dev.lukebemish.larder;

import io.javalin.compression.CompressionStrategy;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.ResourceHandler;
import io.javalin.http.staticfiles.StaticFileConfig;
import io.javalin.jetty.JettyResourceHandler;
import io.javalin.security.RouteRole;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Set;

import static dev.lukebemish.larder.Larder.isHtml;
import static dev.lukebemish.larder.Larder.isJson;

final class NotJustStaticResourceHandler implements ResourceHandler {
    private final ResourceHandler delegate = new JettyResourceHandler();

    private final Indices indices;

    NotJustStaticResourceHandler(Indices indices) {
        this.indices = indices;
    }

    @Override
    public void init(CompressionStrategy compressionStrategy) {
        delegate.init(compressionStrategy);
        ResourceHandler.super.init(compressionStrategy);
    }

    @Override
    public boolean canHandle(Context context) {
        if (delegate.canHandle(context)) {
            return true;
        }

        if (isHtml(context) || isJson(context)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean handle(Context context) {
        if (delegate.handle(context)) {
            return true;
        }

        try {
            var path = normalizePath(context.path());
            if (path.equals("/")) {
                indices.listRepositories(context);
                return true;
            }
            var firstSlash = path.indexOf('/', 1);
            var repositoryName = firstSlash == -1 ? path.substring(1) : path.substring(1, firstSlash);
            var rest = firstSlash == -1 ? "/" : path.substring(firstSlash);
            indices.listAt(context, rest, repositoryName, firstSlash == -1);

            return true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean addStaticFileConfig(StaticFileConfig config) {
        return delegate.addStaticFileConfig(config);
    }

    @Override
    public Set<RouteRole> resourceRouteRoles(Context ctx) {
        return delegate.resourceRouteRoles(ctx);
    }

    private String normalizePath(String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }
}
