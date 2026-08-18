package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.api.PackageType;
import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;
import io.javalin.openapi.OpenApiIgnore;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.function.Function;

public record Package(Identifier<Repository> source, ModuleComponentIdentifier value, LocalDateTime timestamp) implements Model.OneToMany<Repository, Package.ModuleComponentIdentifier> {
    public static final Partial<Package, ByRepository> BY_REPOSITORY = new Partial<>("by_repository");
    public record ByRepository(Identifier<Repository> repository) implements Partial.Value<Package, ByRepository> {
        @Override
        public Partial<Package, ByRepository> type() {
            return BY_REPOSITORY;
        }
    }

    public record ModuleComponentIdentifier(
        PackageType type, String group, String name, String version
    ) {
        public static <T extends Model> Function<Representation.Builder<T>, Representation.FieldLike<T, ModuleComponentIdentifier>> representation(Function<T, ModuleComponentIdentifier> partial) {
            return builder -> builder.grouped("identifier", partial, it -> {
                var type = it.field("type", DatabasePrimitiveType.SMALL_INT, id -> (short) id.type().ordinal());
                var group = it.field("group", DatabasePrimitiveType.VARCHAR, ModuleComponentIdentifier::group);
                var name = it.field("name", DatabasePrimitiveType.VARCHAR, ModuleComponentIdentifier::name);
                var version = it.field("version", DatabasePrimitiveType.VARCHAR, ModuleComponentIdentifier::version);

                return it.build(result -> new ModuleComponentIdentifier(
                    PackageType.values()[type.get(result)],
                    group.get(result),
                    name.get(result),
                    version.get(result)
                ));
            });
        }
    }

    @OpenApiIgnore
    public URI pURL() {
        return URI.create(String.format(
            "pkg:%s/%s/%s@%s",
            value().type.name().toLowerCase(Locale.ROOT),
            URLEncoder.encode(value().group, StandardCharsets.UTF_8),
            URLEncoder.encode(value().name, StandardCharsets.UTF_8),
            URLEncoder.encode(value().version, StandardCharsets.UTF_8)
        ));
    }

    public static final Representation<Package> REPRESENTATION = Representation.build(
        Representation.referenceField("repository", () -> Repository.REPRESENTATION, Package::source),
        ModuleComponentIdentifier.representation(Package::value),
        (it, source, value) -> {
            var timestamp = it.field("timestamp", DatabasePrimitiveType.TIMESTAMP, Package::timestamp);

            it.partial(BY_REPOSITORY, source, ByRepository::repository);

            return it.build("packages", result -> new Package(
                source.get(result),
                value.get(result),
                timestamp.get(result)
            ));
        }
    );
}
