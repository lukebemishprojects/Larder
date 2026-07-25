package dev.lukebemish.larder;

import dev.lukebemish.larder.api.UserCapability;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RepositoryIndex;
import dev.lukebemish.larder.api.IndexEntry;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.ContentType;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static dev.lukebemish.larder.Larder.isHtml;

public class Indices {
    private final PebbleTemplate indexTemplate;

    Indices(Larder app) {
        this.indexTemplate = app.templateEngine.getTemplate("index-page.html");
    }

    private String fillIndex(String path, List<IndexEntry> entries) {
        var writer = new StringWriter();
        try {
            indexTemplate.evaluate(writer, Map.of(
                "path", path,
                "entries", entries
            ), Locale.ROOT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return writer.toString();
    }

    @OpenApi(
        path = "/{repository}/{path}",
        pathParams = {
            @OpenApiParam(
                name = "repository"
            ),
            @OpenApiParam(
                name = "path",
                allowEmptyValue = true
            )
        },
        methods = HttpMethod.GET,
        summary = "Get index of location in a repository",
        responses = {
            @OpenApiResponse(
                status = "200",
                content = @OpenApiContent(mimeType = ContentType.HTML),
                description = "A browsable HTML index"
            ),
            @OpenApiResponse(
                status = "200",
                content = @OpenApiContent(from = IndexEntry[].class, mimeType = ContentType.JSON),
                description = "Index of location in the repository"
            ),
            @OpenApiResponse(
                status = "404",
                description = "Repository or path not found"
            )
        }
    )
    public void listAt(Context context, String path, String repositoryName, boolean allowEmpty) throws SQLException {
        var repositoryId = Identifier.of(new Repository.Id(repositoryName));
        var parent = Identifier.of(new RepositoryIndex.Id(repositoryId, path, "../"));
        var matching = context.appData(Larder.CONNECTION_KEY).transact(c -> {
            var index = c.find(parent);
            if (index.isEmpty()) {
                if (allowEmpty) {
                    var repository = c.find(repositoryId);
                    if (repository.isEmpty()) {
                        throw new NotFoundResponse();
                    }
                    var newIndex = new RepositoryIndex(
                        repositoryId,
                        path,
                        "../",
                        true,
                        Optional.empty(),
                        Optional.empty(),
                        -1
                    );
                    c.insert(newIndex);
                } else {
                    throw new NotFoundResponse();
                }
            }
            return new ArrayList<>(context.appData(Larder.CONNECTION_KEY).select(new RepositoryIndex.ByRepositoryAndPath(
                repositoryId, path
            )));
        });
        matching.sort(
            Comparator.<RepositoryIndex, Integer>comparing(idx -> idx.name().equals("../") ? 0 : (idx.isDirectory() ? 1 : 2))
                .thenComparing(RepositoryIndex::name)
        );
        var entries = matching.stream()
            .map(idx -> new IndexEntry(
                idx.name(),
                idx.lastModified(),
                idx.size(),
                idx.isDirectory()
            ))
            .toList();
        if (isHtml(context)) {
            context.html(fillIndex("/" + repositoryName + path, entries));
        } else {
            context.json(entries);
        }
    }

    @OpenApi(
        path = "/",
        methods = HttpMethod.GET,
        summary = "Get index of available repositories",
        responses = {
            @OpenApiResponse(
                status = "200",
                content = @OpenApiContent(mimeType = ContentType.HTML),
                description = "A browsable HTML index"
            ),
            @OpenApiResponse(
                status = "200",
                content = @OpenApiContent(from = IndexEntry[].class, mimeType = ContentType.JSON),
                description = "Index of available repositories"
            )
        }
    )
    public void listRepositories(Context context) throws SQLException {
        var repos = new ArrayList<>(context.appData(Larder.CONNECTION_KEY).select(Repository.REPRESENTATION));
        repos.sort(Comparator.comparing(Repository::name));
        var entries = repos.stream()
            .map(repo -> new IndexEntry(repo.name() + "/", Optional.empty(), Optional.empty(), true))
            .toList();
        if (isHtml(context)) {
            context.html(fillIndex("/", entries));
        } else {
            context.json(entries);
        }
    }
}
