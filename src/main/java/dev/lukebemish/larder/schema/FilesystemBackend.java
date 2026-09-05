package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.Backend;
import dev.lukebemish.larder.api.Location;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;
import dev.lukebemish.larder.utils.ExceptionalSupplier;
import dev.lukebemish.polymorphicsignatures.utilities.EnumUtils;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public record FilesystemBackend(
    Identifier<RepositoryBackend> id,
    Optional<Location<?>> location
) implements Model.Extension<RepositoryBackend>, Backend<FilesystemBackendConfiguration, FilesystemBackend> {
    public static final Partial<FilesystemBackend, FilesystemBackend.ById> BY_ID = new Partial<>("by_id");
    public record ById(Identifier<RepositoryBackend> id) implements Model.Extension.ByHost<RepositoryBackend, FilesystemBackend, FilesystemBackend.ById> {
        @Override
        public Partial<FilesystemBackend, FilesystemBackend.ById> type() {
            return BY_ID;
        }
    }

    public static final Representation<FilesystemBackend> REPRESENTATION = Representation.build(() -> RepositoryBackend.REPRESENTATION, (it, id) -> {
        var location = it.optionalField("location", DatabasePrimitiveType.VARCHAR, backend -> backend.location().map(Location::name));
        it.partial(BY_ID);
        return it.build("filesystembackends", result -> new FilesystemBackend(
            id.get(result),
            location.get(result).flatMap(name -> Optional.ofNullable(EnumUtils.tryValueOf(name)))
        ));
    });

    @Override
    public @Nullable ExceptionalSupplier<InputStream, IOException> readFile(FilesystemBackendConfiguration config, String relativePath) throws IOException {
        var targetPath = findTargetPath(config, relativePath);

        if (Files.exists(targetPath)) {
            return () -> Files.newInputStream(targetPath);
        }

        return null;
    }

    @Override
    public OutputStream writeFile(FilesystemBackendConfiguration config, String relativePath) throws IOException {
        var targetPath = findTargetPath(config, relativePath);

        Files.createDirectories(targetPath.getParent());
        return Files.newOutputStream(targetPath);
    }

    private Path findTargetPath(FilesystemBackendConfiguration config, String relativePath) {
        if (location.isEmpty()) {
            throw new IllegalStateException("Backend "+id.id()+" no longer has a location defined but attempted filesystem operations");
        }

        var locationPath = location.get().location().normalize();
        var prefixPath = locationPath.resolve(Paths.get(config.prefix())).normalize();
        if (!prefixPath.startsWith(locationPath)) {
            throw new IllegalStateException("Prefix "+ config.prefix()+" traverses outside of the location path");
        }
        var targetPath = prefixPath.resolve(Paths.get(relativePath)).normalize();
        if (!targetPath.startsWith(prefixPath)) {
            throw new IllegalStateException("Relative path "+ relativePath +" traverses outside of the location+prefix path");
        }

        return targetPath;
    }
}
