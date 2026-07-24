package dev.lukebemish.larder;

import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.schema.Repository;
import dev.lukebemish.larder.schema.RepositoryIndex;
import dev.lukebemish.larder.template.IndexEntry;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
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

    private static final List<String> HUMAN_UNITS = List.of(
        "K", "M", "G", "T", "P", "E", "Z", "Y"
    );

    private static String humanSize(long byteSize) {
        var threshold = 1000;

        if (Math.abs(byteSize) < threshold) {
            return Long.toString(byteSize);
        }

        var ofUnit = (double) byteSize;
        var u = 0;

        do {
            ofUnit /= threshold;
            u++;
        } while (Math.round(ofUnit * 10) / 10 >= threshold && u != HUMAN_UNITS.size());

        return DECIMAL_FORMATTER.format(ofUnit)+HUMAN_UNITS.get(u - 1);
    }

    private static final DecimalFormat DECIMAL_FORMATTER = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.ROOT));
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

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
                idx.lastModified().map(DATE_TIME_FORMATTER::format).orElse(""),
                idx.size().map(Indices::humanSize).orElse("")
            ))
            .toList();
        context.html(fillIndex("/"+repositoryName+path, entries));
    }

    public void listRepositories(Context context) throws SQLException {
        var repos = new ArrayList<>(context.appData(Larder.CONNECTION_KEY).select(Repository.REPRESENTATION));
        repos.sort(Comparator.comparing(Repository::name));
        context.html(fillIndex("/", repos.stream()
            .map(repo -> new IndexEntry(repo.name()+"/", "", ""))
            .toList()
        ));
    }
}
