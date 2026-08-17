package dev.lukebemish.larder.orm;

import dev.lukebemish.polymorphicsignatures.Bootstrap;
import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.functions.Function3;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.SequencedSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class Representation<T extends Model> {
    private Representation(String tableName, List<Field<T, ?>> fields, SQLFunction<Result, T> reconstructor, List<Field<T, ?>> idFields, Object schemaKey, Class<T> clazz, List<Field<T,?>> uniqueFields, SequencedSet<Partial<T, ?>> partials) {
        this.tableName = tableName;
        this.fields = fields;
        this.reconstructor = reconstructor;
        this.idFields = idFields;
        this.schemaKey = schemaKey;
        this.clazz = clazz;
        this.uniqueFields = uniqueFields;
        this.partials = partials;
    }

    public SequencedSet<Representation<?>> references() {
        var set = new LinkedHashSet<Representation<?>>();
        for (var f : fields) {
            set.addAll(f.references());
        }
        set.remove(this);
        return set;
    }

    public static class Result {
        private final ResultSet resultSet;
        private final Object schemaKey;
        private final Map<Field<?, ?>, Integer> indexMap;

        private Result(ResultSet resultSet, Object schemaKey, Map<Field<?, ?>, Integer> indexMap) {
            this.resultSet = resultSet;
            this.schemaKey = schemaKey;
            this.indexMap = indexMap;
        }
    }

    final String tableName;
    private final List<Field<T, ?>> fields;
    private final SQLFunction<Result, T> reconstructor;
    final List<Field<T, ?>> idFields;
    private final Object schemaKey;
    final Class<T> clazz;
    private final List<Field<T,?>> uniqueFields;
    private final SequencedSet<Partial<T, ?>> partials;
    private final AtomicReference<@Nullable World> world = new AtomicReference<>();

    public abstract static sealed class FieldLike<T extends Model, F> {
        public abstract F get(Result result) throws SQLException;
        abstract SequencedCollection<Field<T, ?>> fields();
        abstract SequencedCollection<? extends Function<?, ?>> fieldTypes(); // FieldLike type -> field type
    }

    public static final class FieldGroup<T extends Model, G> extends FieldLike<T, G> {
        private final SQLFunction<Result, G> reconstructor;
        private final SequencedCollection<Field<T, ?>> fields;
        private final SequencedCollection<Function<?, ?>> fieldTypes;

        public FieldGroup(SQLFunction<Result, G> reconstructor, SequencedCollection<Field<T, ?>> fields, SequencedCollection<Function<?, ?>> fieldTypes) {
            this.reconstructor = reconstructor;
            this.fields = fields;
            this.fieldTypes = fieldTypes;
        }

        @Override
        public G get(Result result) throws SQLException {
            return reconstructor.apply(result);
        }

        @Override
        SequencedCollection<Function<?, ?>> fieldTypes() {
            return fieldTypes;
        }

        @Override
        SequencedCollection<Field<T, ?>> fields() {
            return fields;
        }
    }

    public sealed abstract static class Field<T extends Model, F> extends FieldLike<T, F> {
        protected final String name;
        final Function<T, F> encoder;
        private final Object schemaKey;

        protected Field(String name, Function<T, F> encoder, Object schemaKey) {
            this.name = name;
            this.encoder = encoder;
            this.schemaKey = schemaKey;
        }

        abstract List<String> definitionSchema(String name);
        abstract List<String> uniqueSchema(String name);
        abstract List<String> constraintSchema(String name);
        abstract F get(ResultSet result, int startAt) throws SQLException;
        List<Representation<?>> references() {
            return List.of();
        }

        @Override
        SequencedCollection<Field<T, ?>> fields() {
            return List.of(this);
        }

        @Override
        SequencedCollection<Function<F, F>> fieldTypes() {
            return List.of(Function.identity());
        }

        @Override
        public F get(Result result) throws SQLException {
            if (result.schemaKey != this.schemaKey) {
                throw new IllegalArgumentException("Attempted to use field to decode result of wrong schema");
            }
            return get(result.resultSet, result.indexMap.get(this));
        }

        abstract void write(int offset, PreparedStatement statement, F value) throws SQLException;
        void writeEncode(int offset, PreparedStatement statement, T value) throws SQLException {
            write(offset, statement, encoder.apply(value));
        }
    }

    public static final class ReferenceField<T extends Model, F extends Model.Object> extends Field<T, Identifier<F>> {
        private final Supplier<Representation<F>> referenceRepresentation;

        private ReferenceField(String name, Function<T, Identifier<F>> encoder, Supplier<Representation<F>> referenceRepresentation, Object schemaKey) {
            super(name, encoder, schemaKey);
            this.referenceRepresentation = referenceRepresentation;
        }

        @Override
        List<Representation<?>> references() {
            return List.of(referenceRepresentation.get());
        }

        @Override
        List<String> definitionSchema(String name) {
            return List.of(
                String.format("%s %s NOT NULL", name, DatabasePrimitiveType.UUID.typeString())
            );
        }

        @Override
        List<String> constraintSchema(String name) {
            return List.of(String.format(
                "FOREIGN KEY (%s) REFERENCES %s (%s)",
                name, referenceRepresentation.get().tableName, "id"
            ));
        }

        @Override
        List<String> uniqueSchema(String name) {
            return List.of(
                String.format("UNIQUE (%s)", name)
            );
        }

        @Override
        Identifier<F> get(ResultSet resultSet, int startAt) throws SQLException {
            var uuid = DatabasePrimitiveType.UUID.get(resultSet, startAt);
            return new Identifier<>(Objects.requireNonNull(uuid), referenceRepresentation.get().clazz);
        }

        @Override
        void write(int offset, PreparedStatement statement, Identifier<F> value) throws SQLException {
            DatabasePrimitiveType.UUID.set(statement, offset, value.id);
        }
    }

    public static final class OptionalReferenceField<T extends Model, F extends Model.Object> extends Field<T, Optional<Identifier<F>>> {
        private final Supplier<Representation<F>> referenceRepresentation;

        private OptionalReferenceField(String name, Function<T, Optional<Identifier<F>>> encoder, Supplier<Representation<F>> referenceRepresentation, Object schemaKey) {
            super(name, encoder, schemaKey);
            this.referenceRepresentation = referenceRepresentation;
        }

        @Override
        List<Representation<?>> references() {
            return List.of(referenceRepresentation.get());
        }

        @Override
        List<String> definitionSchema(String name) {
            return List.of(
                String.format("%s %s", name, DatabasePrimitiveType.UUID.typeString())
            );
        }

        @Override
        List<String> constraintSchema(String name) {
            return List.of(String.format(
                "FOREIGN KEY (%s) REFERENCES %s (%s)",
                name, referenceRepresentation.get().tableName, "id"
            ));
        }

        @Override
        List<String> uniqueSchema(String name) {
            return List.of(
                String.format("UNIQUE (%s)", name)
            );
        }

        @Override
        Optional<Identifier<F>> get(ResultSet resultSet, int startAt) throws SQLException {
            var uuid = DatabasePrimitiveType.UUID.get(resultSet, startAt);
            if (resultSet.wasNull()) {
                return Optional.empty();
            }
            return Optional.of(new Identifier<>(Objects.requireNonNull(uuid), referenceRepresentation.get().clazz));
        }

        @Override
        void write(int offset, PreparedStatement statement, Optional<Identifier<F>> value) throws SQLException {
            if (value.isPresent()) {
                DatabasePrimitiveType.UUID.set(statement, offset, value.get().id);
            } else {
                statement.setNull(offset, DatabasePrimitiveType.UUID.type());
            }
        }
    }

    private World world() {
        return Objects.requireNonNull(world.updateAndGet(old -> {
            if (old != null) return old;
            var worldImpl = Objects.requireNonNull(clazz.getAnnotation(World.BelongsTo.class), String.format("Model class '%s' has no @World.BelongsTo annotation", clazz)).value();
            for (var field : worldImpl.getFields()) {
                if (World.class.isAssignableFrom(field.getType()) && field.accessFlags().contains(AccessFlag.STATIC) && field.accessFlags().contains(AccessFlag.FINAL)) {
                    try {
                        return (World) MethodHandles.privateLookupIn(worldImpl, MethodHandles.lookup()).unreflectGetter(field).invoke();
                    } catch (Throwable e) {
                        throw e instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(e);
                    }
                }
            }
            throw new IllegalStateException(String.format(
                "World impl class '%s', referenced from '%s', has no public static instance field",
                worldImpl, clazz
            ));
        }));
    }

    @SuppressWarnings("unchecked")
    private static <S> void writeField(int offset, PreparedStatement statement, Field<?, S> field, Object value) throws SQLException {
        field.write(offset, statement, (S) value);
    }

    public static final class RequiredField<T extends Model, F> extends Field<T, F> {
        private final DatabasePrimitiveType<F> primitiveType;

        private RequiredField(String name, Function<T, F> encoder, DatabasePrimitiveType<F> primitiveType, Object schemaKey) {
            super(name, encoder, schemaKey);
            this.primitiveType = primitiveType;
        }

        @Override
        F get(ResultSet resultSet, int startAt) throws SQLException {
            return Objects.requireNonNull(primitiveType.get(resultSet, startAt));
        }

        @Override
        void write(int offset, PreparedStatement statement, F value) throws SQLException {
            primitiveType.set(statement, offset, value);
        }

        @Override
        List<String> definitionSchema(String name) {
            return List.of(
                    String.format("%s %s NOT NULL", name, primitiveType.typeString())
            );
        }

        @Override
        List<String> uniqueSchema(String name) {
            return List.of(
                String.format("UNIQUE (%s)", name)
            );
        }

        @Override
        List<String> constraintSchema(String name) {
            return List.of();
        }
    }

    public static final class OptionalField<T extends Model, F> extends Field<T, Optional<F>> {
        private final DatabasePrimitiveType<F> primitiveType;

        private OptionalField(String name, Function<T, Optional<F>> encoder, DatabasePrimitiveType<F> primitiveType, Object schemaKey) {
            super(name, encoder, schemaKey);
            this.primitiveType = primitiveType;
        }

        @Override
        Optional<F> get(ResultSet resultSet, int startAt) throws SQLException {
            var value = primitiveType.get(resultSet, startAt);
            if (resultSet.wasNull()) {
                return Optional.empty();
            }
            return Optional.of(Objects.requireNonNull(value));
        }

        @Override
        void write(int offset, PreparedStatement statement, Optional<F> value) throws SQLException {
            if (value.isPresent()) {
                primitiveType.set(statement, offset, value.get());
            } else {
                statement.setNull(offset, primitiveType.type());
            }
        }

        @Override
        List<String> definitionSchema(String name) {
            return List.of(
                    String.format("%s %s", name, primitiveType.typeString())
            );
        }

        @Override
        List<String> uniqueSchema(String name) {
            return List.of(
                String.format("UNIQUE (%s)", name)
            );
        }

        @Override
        List<String> constraintSchema(String name) {
            return List.of();
        }
    }

    @PolymorphicSignature("$build")
    public native static <T extends Model.Object> Representation<T> build(
        Function2<? super IdentityBuilder<T>, Field<T, UUID>, Representation<T>> function
    );

    private static <T extends Model.Object> Representation<T> _build(
        Function2<? super IdentityBuilder<T>, Field<T, UUID>, Representation<T>> function
    ) {
        var builder = new IdentityBuilder<T>();
        var id = builder.field("id", DatabasePrimitiveType.UUID, Model.Object::id);
        builder.idField(id);
        return function.invoke(builder, id);
    }

    @PolymorphicSignature("$build")
    public native static <O extends Model.Object, T extends Model.Extension<O>> Representation<T> build(
        Supplier<Representation<O>> host,
        Function2<? super ExtensionBuilder<O, T>, Field<T, Identifier<O>>, Representation<T>> function
    );

    private static <O extends Model.Object, T extends Model.Extension<O>> Representation<T> _build(
        Supplier<Representation<O>> host,
        Function2<? super ExtensionBuilder<O, T>, Field<T, Identifier<O>>, Representation<T>> function
    ) {
        var builder = new ExtensionBuilder<O, T>();
        var id = builder.referenceField("id", host, Model.Extension::id);
        builder.idField(id);
        return function.invoke(builder, id);
    }

    @PolymorphicSignature("$build")
    public native static <S extends Model.Object, V, T extends Model.OneToMany<S, V>> Representation<T> build(
        Function<? super RelationBuilder<S, V, T>, ? extends Field<T, Identifier<S>>> source,
        Function<? super RelationBuilder<S, V, T>, ? extends FieldLike<T, V>> target,
        Function3<? super RelationBuilder<S, V, T>, Field<T, Identifier<S>>, FieldLike<T, V>, Representation<T>> function
    );

    private static <S extends Model.Object, V, T extends Model.OneToMany<S, V>> Representation<T> _build(
        Function<? super RelationBuilder<S, V, T>, ? extends Field<T, Identifier<S>>> source,
        Function<? super RelationBuilder<S, V, T>, ? extends FieldLike<T, V>> target,
        Function3<? super RelationBuilder<S, V, T>, Field<T, Identifier<S>>, FieldLike<T, V>, Representation<T>> function
    ) {
        var builder = new RelationBuilder<S, V, T>();
        var sField = source.apply(builder);
        var tField = target.apply(builder);
        builder.init(sField, tField);
        return function.invoke(builder, sField, tField);
    }

    private static boolean execute(ModelConnection connection, String query, SQLConsumer<PreparedStatement> action) throws SQLException {
        var stmt = connection.connection().prepareStatement(query);
        action.accept(stmt);
        return stmt.execute();
    }

    private static int executeUpdate(ModelConnection connection, String query, SQLConsumer<PreparedStatement> action) throws SQLException {
        var stmt = connection.connection().prepareStatement(query);
        action.accept(stmt);
        return stmt.executeUpdate();
    }

    private static ResultSet executeQuery(ModelConnection connection, String query, SQLConsumer<PreparedStatement> action) throws SQLException {
        var stmt = connection.connection().prepareStatement(query);
        action.accept(stmt);
        return stmt.executeQuery();
    }

    private T read(ResultSet resultSet) throws SQLException {
        var map = new IdentityHashMap<Field<?, ?>, Integer>();
        int offset = 1;
        for (var f : fields) {
            map.put(f, offset);
            offset += 1;
        }
        var result = new Result(resultSet, schemaKey, map);
        return reconstructor.apply(result);
    }

    private int writeFull(int offset, PreparedStatement statement, T value) throws SQLException {
        for (var f : fields) {
            f.writeEncode(offset, statement, value);
            offset += 1;
        }
        return offset;
    }

    private static <R extends Model.Object> void writeIdentifier(int offset, PreparedStatement statement, Identifier<R> value) throws SQLException {
        DatabasePrimitiveType.UUID.set(statement, offset, value.id);
    }

    private <V extends Partial.Value<T, V>> void writePartial(int offset, PreparedStatement statement, Partial.Value<T, V> value) throws SQLException {
        for (int i = 0; i < value.type().fields.size(); i++) {
            var f = value.type().fields.get(i);
            var v = value.type().valueGetters.get(i).apply(Partial.cast(value));
            writeField(offset,  statement, f, v);
            offset += 1;
        }
    }

    public String schema() {
        List<String> parts = new ArrayList<>();
        fields.stream()
                .flatMap(f -> f.definitionSchema(f.name).stream())
                .forEach(parts::add);
        if (!idFields.isEmpty()) {
            parts.add(String.format("PRIMARY KEY (%s)", idFields.stream().map(f -> f.name).collect(Collectors.joining(", "))));
        }
        uniqueFields.stream()
            .flatMap(f -> f.uniqueSchema(f.name).stream())
            .forEach(parts::add);
        fields.stream()
                .flatMap(f -> f.constraintSchema(f.name).stream())
                .forEach(parts::add);
        var statements = new ArrayList<String>();
        statements.add(String.format("""
                CREATE TABLE IF NOT EXISTS %s (%s
                );
                """,
            tableName,
            parts.stream().map(s -> "\n    "+s).collect(Collectors.joining(","))
        ));
        partials.stream()
            .filter(p -> p.fields.size() != idFields.size() || !idFields.containsAll(p.fields))
            .map(it -> String.format("""
                    CREATE INDEX %s ON %s (%s);
                    """,
                tableName+"_"+it.name,
                tableName,
                it.fields.stream()
                    .map(f -> f.name)
                    .collect(Collectors.joining(", "))
                )
            )
            .forEach(statements::add);
        return String.join("\n", statements);
    }

    List<T> select(ModelConnection connection) throws SQLException {
        try (var result = executeQuery(
                connection,
                String.format(
                        "SELECT %s FROM %s;",
                        fields.stream()
                                .map(f -> f.name)
                                .collect(Collectors.joining(", ")),
                        tableName
                ),
                _ -> {})) {
            var models = new ArrayList<T>();
            while (result.next()) {
                models.add(read(result));
            }
            return models;
        }
    }

    <V extends Partial.Value<T, V>> List<T> select(ModelConnection connection, Partial.Value<T, V> value) throws SQLException {
        try (var result = executeQuery(
            connection,
            String.format(
                "SELECT %s FROM %s WHERE %s;",
                fields.stream()
                    .map(f -> f.name)
                    .collect(Collectors.joining(", ")),
                tableName,
                value.type().fields.stream()
                    .map(f -> f.name)
                    .map(f -> f + " = ?")
                    .collect(Collectors.joining(" AND "))
            ),
            statement -> writePartial(1, statement, value))) {
            var models = new ArrayList<T>();
            while (result.next()) {
                models.add(read(result));
            }
            return models;
        }
    }

    static <T extends Model.Object> T select(Representation<T> representation, ModelConnection connection, Identifier<T> identifier) throws SQLException {
        return find(representation, connection, identifier).orElseThrow(() -> new NoSuchElementException("Element "+identifier+" does not exist in database!"));
    }

    static <T extends Model> void update(Representation<T> representation, ModelConnection connection, T value) throws SQLException {
        executeUpdate(
            connection,
            String.format(
                "UPDATE %s SET %s WHERE %s;",
                representation.tableName,
                representation.fields.stream()
                    .map(f -> f.name)
                    .map(f -> f + " = ?")
                    .collect(Collectors.joining(", ")),
                representation.idFields.stream()
                    .map(f -> f.name)
                    .map(f -> f + " = ?")
                    .collect(Collectors.joining(" AND "))
            ),
            statement -> {
                var offset = representation.writeFull(1, statement, value);
                writeIdentifier(representation, offset, statement, value);
            }
        );
    }

    @SuppressWarnings("unchecked")
    static <T extends Model> void delete(Representation<T> representation, ModelConnection connection, T value) throws SQLException {
        switch (value) {
            case Model.Object object -> delete(representation, connection, new Identifier<>(object.id(), (Class<? extends Model.Object>) representation.clazz));
            case Model.OneToMany<?, ?> oneToMany -> _delete(representation, connection, oneToMany);
            case Model.Extension<?> extension -> delete(representation, connection, extension.id());
        }
    }

    private static <T extends Model.OneToMany<?, ?>> void _delete(Representation<?> representation, ModelConnection connection, T value) throws SQLException {
        @SuppressWarnings("unchecked") var reprCast = (Representation<T>) representation;
        executeUpdate(
            connection,
            String.format(
                "DELETE FROM %s WHERE %s;",
                representation.tableName,
                representation.idFields.stream()
                    .map(f -> f.name)
                    .map(f -> f + " = ?")
                    .collect(Collectors.joining(" AND "))
            ),
            statement -> writeOneToMany(reprCast, 1, statement, value)
        );
    }

    private static <T extends Model> void writeIdentifier(Representation<T> representation, int offset, PreparedStatement statement, T value) throws SQLException {
        for (var f : representation.idFields) {
            writeField(offset, statement, f, f.encoder.apply(value));
            offset += 1;
        }
    }

    private static <T extends Model.OneToMany<?, ?>> void writeOneToMany(Representation<T> representation, int offset, PreparedStatement statement, T value) throws SQLException {
        var sourceF = representation.idFields.getFirst();
        var valueFs = representation.idFields.stream().skip(1).toList();
        writeField(offset, statement, sourceF, sourceF.encoder.apply(value));
        offset += 1;
        for (var f : valueFs) {
            writeField(offset, statement, f, f.encoder.apply(value));
            offset += 1;
        }
    }

    static <T extends Model> void delete(Representation<T> representation, ModelConnection connection, Identifier<?> identifier) throws SQLException {
        // Technically not _that_ safe, but good for extension models
        if (identifier.clazz.equals(representation.clazz)) {
            deleteDependents((Representation<? extends Model.Object>) representation, connection, identifier);
        }
        executeUpdate(
            connection,
            String.format(
                "DELETE FROM %s WHERE %s = ?;",
                representation.tableName,
                representation.idFields.getFirst().name
            ),
            statement -> writeIdentifier(1, statement, identifier)
        );
    }

    private static <T extends Model.Object> void deleteDependents(Representation<T> representation, ModelConnection connection, Identifier<?> identifier) throws SQLException {
        var dependents = representation.world().dependents(representation);

        for (var repr : dependents) {
            delete(repr, connection, identifier); // luckily, the identifier is always the first `idField`!
        }
    }

    static <T extends Model.OneToMany<?, ?>> void delete(Representation<T> representation, ModelConnection connection, T value) throws SQLException {
        if (value instanceof Model.Object objectModel) {
            @SuppressWarnings("unchecked")
            var objectRepresentation = (Representation<? extends Model.Object>) representation;
            deleteDependents(objectRepresentation, connection, Identifier.of(objectRepresentation, objectModel.id()));
        }
        executeUpdate(
            connection,
            String.format(
                "DELETE FROM %s WHERE %s;",
                representation.tableName,
                representation.fields.stream()
                    .map(f -> f.name)
                    .map(f -> f + " = ?")
                    .collect(Collectors.joining(" AND "))
            ),
            statement -> representation.writeFull(1, statement, value)
        );
    }

    <V extends Partial.Value<T, V>> void delete(ModelConnection connection, Partial.Value<T, V> value) throws SQLException {
        // TODO: safely delete dependents when deleting by partial
        executeUpdate(
            connection,
            String.format(
                "DELETE FROM %s WHERE %s;",
                tableName,
                value.type().fields.stream()
                    .map(f -> f.name)
                    .map(f -> f + " = ?")
                    .collect(Collectors.joining(" AND "))
            ),
            statement -> writePartial(1, statement, value)
        );
    }

    static <T extends Model> void insert(Representation<T> representation, ModelConnection connection, T value) throws SQLException {
        executeUpdate(
            connection,
            String.format(
                "INSERT INTO %s (%s) VALUES (%s);",
                representation.tableName,
                representation.fields.stream()
                    .map(f -> f.name)
                    .collect(Collectors.joining(", ")),
                representation.fields.stream()
                    .map(f -> f.name)
                    .map(f -> "?")
                    .collect(Collectors.joining(", "))
            ),
            statement -> representation.writeFull(1, statement, value)
        );
    }

    static <T extends Model.Object> Optional<T> find(Representation<T> representation, ModelConnection connection, Identifier<T> identifier) throws SQLException {
        try (var result = executeQuery(
                connection,
                String.format(
                        "SELECT %s FROM %s WHERE %s;",
                        representation.fields.stream()
                                .map(f -> f.name)
                                .collect(Collectors.joining(", ")),
                        representation.tableName,
                        representation.idFields.stream()
                                .map(f -> f.name)
                                .map(f -> f + " = ?")
                                .collect(Collectors.joining(" AND "))
                ),
                statement -> writeIdentifier(1, statement, identifier))) {
            if (!result.next()) {
                return Optional.empty();
            }
            return Optional.of(representation.read(result));
        }
    }

    static void migrate(ModelConnection connection, Migrations migrations, int targetVersion) throws SQLException {
        if (targetVersion > migrations.maxVersion() || targetVersion < 0) {
            throw new IllegalArgumentException("Invalid version "+targetVersion);
        }
        connection.transact(c -> {
            executeUpdate(c, """
                    CREATE TABLE IF NOT EXISTS migrations (
                        version INTEGER PRIMARY KEY
                    );
                    """, _ -> {});
            try (var currentVersionResult = executeQuery(c, """
                    SELECT MAX(version) FROM migrations""", _ -> {})) {
                var currentVersion = currentVersionResult.next() ? currentVersionResult.getInt(1) : 0;
                if (targetVersion < currentVersion) {
                    for (int i = currentVersion; i > targetVersion; i--) {
                        var migration = migrations.downgrades.get(i - 1);
                        executeUpdate(c, migration, _ -> {
                        });
                        final var v = i;
                        executeUpdate(c, "DELETE FROM migrations WHERE version = ?;", p -> p.setInt(1, v));
                    }
                } else if (targetVersion > currentVersion) {
                    for (int i = currentVersion + 1; i <= targetVersion; i++) {
                        var migration = migrations.upgrades.get(i - 1);
                        executeUpdate(c, migration, _ -> {
                        });
                        final var v = i;
                        executeUpdate(c, "INSERT INTO migrations (version) VALUES (?);", p -> p.setInt(1, v));
                    }
                }
            }
        });
    }

    public abstract sealed static class Builder<T extends Model> {
        protected final Object schemaKey = new Object();
        protected final List<Field<T, ?>> fields = new ArrayList<>();
        protected final List<Field<T, ?>> idFields = new ArrayList<>();
        protected final List<Field<T, ?>> uniqueFields = new ArrayList<>();
        protected final SequencedSet<Partial<T, ?>> partials = new LinkedHashSet<>();

        protected Builder() {}

        private Representation<T> build(String tableName, SQLFunction<Result, T> reconstructor, Class<T> clazz) {
            if (!clazz.accessFlags().contains(AccessFlag.FINAL)) {
                throw new IllegalArgumentException("Representations may only be built for final model types!");
            }
            return new Representation<>(
                tableName.toLowerCase(Locale.ROOT),
                fields,
                reconstructor,
                idFields,
                schemaKey,
                clazz,
                uniqueFields,
                partials
            );
        }

        public static CallSite $build(MethodHandles.Lookup lookup, String name, MethodType descriptor, @Bootstrap.Caller Class<? extends Model> receiver) throws NoSuchMethodException, IllegalAccessException {
            var handle = MethodHandles.lookup().findVirtual(Builder.class, "build", MethodType.methodType(Representation.class, String.class, SQLFunction.class, Class.class));
            if (!Model.class.isAssignableFrom(receiver)) {
                throw new IllegalArgumentException("This method uses the caller class to build a representation. It does not work outside of the appropriate Model class");
            }
            return new ConstantCallSite(MethodHandles.insertArguments(handle, 3, receiver).asType(descriptor));
        }

        public <F> OptionalField<T, F> optionalField(String name, DatabasePrimitiveType<F> primitiveType, Function<T, Optional<F>> encoder) {
            var field = new OptionalField<>(name, encoder, primitiveType, schemaKey);
            fields.add(field);
            return field;
        }

        public <F> RequiredField<T, F> field(String name, DatabasePrimitiveType<F> primitiveType, Function<T, F> encoder) {
            var field = new RequiredField<>(name, encoder, primitiveType, schemaKey);
            fields.add(field);
            return field;
        }

        public <F extends Model.Object> ReferenceField<T, F> referenceField(String name, Supplier<Representation<F>> reference, Function<T, Identifier<F>> encoder) {
            var field = new ReferenceField<>(name, encoder, reference, schemaKey);
            fields.add(field);
            return field;
        }

        public <F extends Model.Object> OptionalReferenceField<T, F> optionalReferenceField(String name, Supplier<Representation<F>> reference, Function<T, Optional<Identifier<F>>> encoder) {
            var field = new OptionalReferenceField<>(name, encoder, reference, schemaKey);
            fields.add(field);
            return field;
        }

        public <G> FieldGroup<T, G> grouped(String name, Function<T, G> partial, Function<GroupedFieldBuilder<T, G>, FieldGroup<T, G>> builder) {
            var groupedBuilder = new GroupedFieldBuilder<>(this, partial, name);
            return builder.apply(groupedBuilder);
        }

        public <F, V extends Partial.Value<T, V>> void partial(Partial<T, V> partial, FieldLike<T, F> field, Function<V, F> getter, int... idxs) {
            partials.add(partial);
            var fieldIter = field.fields().iterator();
            var fieldTypeIter = field.fieldTypes().iterator();
            int i = 0;
            int j = 0;
            Arrays.sort(idxs);
            while (fieldIter.hasNext()) {
                var f = fieldIter.next();
                var fieldType = fieldTypeIter.next();
                if (idxs.length == 0 || (j < idxs.length && i == idxs[j])) {
                    partialValue(partial, getter, f, fieldType);
                    j++;
                }
                i++;
            }
        }

        private static <F, V, T extends Model, P extends Partial.Value<T, P>> void partialValue(Partial<T, P> partial, Function<P, V> valueFromPartialValue, Field<T, F> field, Function<?, ?> fieldType) {
            @SuppressWarnings("unchecked") var fieldTypeSpecific = (Function<V, F>) fieldType;
            partial.register(field, p -> fieldTypeSpecific.apply(valueFromPartialValue.apply(p)));
        }

        public void unique(Field<T, ?> field) {
            uniqueFields.add(field);
        }

        protected void idField(Field<T, ?> field) {
            idFields.add(field);
        }

        public static final class GroupedFieldBuilder<T extends Model, G> {
            private final Builder<T> delegate;
            private final Function<T, G> partial;
            private final String namePrefix;
            private final List<Field<T, ?>> fields = new ArrayList<>();
            private final List<Function<?, ?>> fieldTypes = new ArrayList<>(); // grouped type -> field type

            private GroupedFieldBuilder(Builder<T> delegate, Function<T, G> partial, String namePrefix) {
                this.delegate = delegate;
                this.partial = partial;
                this.namePrefix = namePrefix;
            }

            public <F> OptionalField<T, F> optionalField(String name, DatabasePrimitiveType<F> primitiveType, Function<G, Optional<F>> encoder) {
                var f = delegate.optionalField(namePrefix + "_" + name, primitiveType, partial.andThen(encoder));
                fields.add(f);
                fieldTypes.add(encoder);
                return f;
            }

            public <F> Field<T, F> field(String name, DatabasePrimitiveType<F> primitiveType, Function<G, F> encoder) {
                var f = delegate.field(namePrefix + "_" + name, primitiveType, partial.andThen(encoder));
                fields.add(f);
                fieldTypes.add(encoder);
                return f;
            }

            public <F extends Model.Object> ReferenceField<T, F> referenceField(String name, Supplier<Representation<F>> reference, Function<G, Identifier<F>> encoder) {
                var f = delegate.referenceField(namePrefix + "_" + name, reference, partial.andThen(encoder));
                fields.add(f);
                fieldTypes.add(encoder);
                return f;
            }

            public <F extends Model.Object> OptionalReferenceField<T, F> optionalReferenceField(String name, Supplier<Representation<F>> reference, Function<G, Optional<Identifier<F>>> encoder) {
                var f = delegate.optionalReferenceField(namePrefix + "_" + name, reference, partial.andThen(encoder));
                fields.add(f);
                fieldTypes.add(encoder);
                return f;
            }

            public FieldGroup<T, G> build(SQLFunction<Result, G> reconstructor) {
                return new FieldGroup<>(reconstructor, fields, fieldTypes);
            }
        }
    }

    public static final class IdentityBuilder<T extends Model.Object> extends Builder<T> {
        private IdentityBuilder() {}

        @PolymorphicSignature(value = "$build", clazz = Builder.class)
        public native Representation<T> build(String tableName, SQLFunction<Result, T> reconstructor);
    }

    public static final class ExtensionBuilder<O extends Model.Object, T extends Model.Extension<O>> extends Builder<T> {
        private ExtensionBuilder() {}

        @SuppressWarnings("unchecked")
        public <P extends Model.Extension.ByHost<O, T, P>> void partial(Partial<T, P> partial) {
            partial(partial, (Field<T, Identifier<O>>) idFields.getFirst(), Model.Extension.ByHost::id);
        }

        @PolymorphicSignature(value = "$build", clazz = Builder.class)
        public native Representation<T> build(String tableName, SQLFunction<Result, T> reconstructor);
    }

    public static final class RelationBuilder<S extends Model.Object, V, T extends Model.OneToMany<S, V>> extends Builder<T> {
        private RelationBuilder() {}

        private @Nullable Field<T, Identifier<S>> source;
        private @Nullable FieldLike<T, V> value;

        private void init(Field<T, Identifier<S>> source, FieldLike<T, V> value) {
            this.idField(source);
            for (var f : value.fields()) {
                this.idField(f);
            }
            this.source = source;
            this.value = value;
        }

        public <P extends Model.OneToMany.BySource<S, T, P>> void partialSource(Partial<T, P> partial) {
            partial(partial, Objects.requireNonNull(source), Model.OneToMany.BySource::source);
        }

        public <P extends Model.OneToMany.ByValue<V, T, P>> void partialValue(Partial<T, P> partial) {
            partial(partial, Objects.requireNonNull(value), Model.OneToMany.ByValue::value);
        }

        public <P extends Model.OneToMany.ByPair<S, V, T, P>> void partial(Partial<T, P> partial) {
            partial(partial, Objects.requireNonNull(source), Model.OneToMany.ByPair::source);
            partial(partial, Objects.requireNonNull(value), Model.OneToMany.ByPair::value);
        }

        @PolymorphicSignature(value = "$build", clazz = Builder.class)
        public native Representation<T> build(String tableName, SQLFunction<Result, T> reconstructor);
    }

    public static <T extends Model, F> Function<Builder<T>, RequiredField<T, F>> field(String name, DatabasePrimitiveType<F> primitiveType, Function<T, F> encoder) {
        return it -> it.field(name, primitiveType, encoder);
    }

    public static <T extends Model, F extends Model.Object> Function<Builder<T>, ReferenceField<T, F>> referenceField(String name, Supplier<Representation<F>> reference, Function<T, Identifier<F>> encoder) {
        return it -> it.referenceField(name, reference, encoder);
    }

    public static <T extends Model, F extends Model.Object> Function<Builder<T>, OptionalReferenceField<T, F>> optionalReferenceField(String name, Supplier<Representation<F>> reference, Function<T, Optional<Identifier<F>>> encoder) {
        return it -> it.optionalReferenceField(name, reference, encoder);
    }

    public static <T extends Model, F> Function<Builder<T>, OptionalField<T, F>> optionalField(String name, DatabasePrimitiveType<F> primitiveType, Function<T, Optional<F>> encoder) {
        return it -> it.optionalField(name, primitiveType, encoder);
    }

    public static <T extends Model, G> Function<Builder<T>, FieldGroup<T, G>> grouped(String name, Function<T, G> partial, Function<Builder.GroupedFieldBuilder<T, G>, FieldGroup<T, G>> builder) {
        return it -> it.grouped(name, partial, builder);
    }

    public static <T extends Model> Class<T> representedType(Representation<T> representation) {
        return representation.clazz;
    }

    @SuppressWarnings("unchecked")
    public static <O extends Model.Object> @Nullable Representation<O> host(Representation<?> representation) {
        if (!Model.Dependent.class.isAssignableFrom(representation.clazz)) {
            return null;
        }
        if (!representation.idFields.isEmpty()) {
            if (representation.idFields.getFirst() instanceof Representation.ReferenceField<?,?> referenceField) {
                return (Representation<O>) referenceField.referenceRepresentation.get();
            }
        }
        throw new IllegalStateException("Could not locate host model for "+representation.clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Model> Representation<T> expensiveLocate(Class<T> clazz) {
        java.lang.reflect.Field target = null;
        for (var field : clazz.getFields()) {
            if (Representation.class.isAssignableFrom(field.getType()) && field.accessFlags().contains(AccessFlag.STATIC) && field.accessFlags().contains(AccessFlag.FINAL)) {
                if (field.getGenericType() instanceof ParameterizedType parameterizedType) {
                    if (!parameterizedType.getActualTypeArguments()[0].equals(clazz)) {
                        continue;
                    }
                }
                target = field;
                break;
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("Class " + clazz + " does not have a representation field");
        }
        try {
            return (Representation<T>) target.get(null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T extends Model> Representation<T> validate(Class<T> type, Representation<T> representation) {
        if (!representation.clazz.equals(type)) {
            throw new IllegalStateException("Representations must be built in the class they represent, and stored in a static final field");
        }
        return representation;
    }

    public static CallSite $build(MethodHandles.Lookup lookup, String name, MethodType methodType, @Bootstrap.Receiver Method receiving, @Bootstrap.Caller Class<?> calling) throws NoSuchMethodException, IllegalAccessException {
        var target = MethodHandles.lookup().findStatic(Representation.class, "_"+receiving.getName(), MethodType.methodType(
            receiving.getReturnType(), receiving.getParameterTypes()
        ));
        var validate = MethodHandles.lookup().findStatic(Representation.class, "validate", MethodType.methodType(
            Representation.class, Class.class, Representation.class
        ));
        return new ConstantCallSite(MethodHandles.collectArguments(
            MethodHandles.insertArguments(validate, 0, calling),
            0,
            target
        ));
    }
}
