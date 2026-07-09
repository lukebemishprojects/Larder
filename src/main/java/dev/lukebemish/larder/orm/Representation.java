package dev.lukebemish.larder.orm;

import dev.lukebemish.polymorphicsignatures.Bootstrap;
import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.ParameterizedType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class Representation<T extends Model> {
    private Representation(String tableName, List<Field<T, ?>> fields, SQLFunction<Result, T> reconstructor, List<Field<T, ?>> idFields, Object schemaKey, List<Function<? extends Identifier.Template<T>, ?>> templateFunctions, Class<T> clazz) {
        this.tableName = tableName;
        this.fields = fields;
        this.reconstructor = reconstructor;
        this.idFields = idFields;
        this.schemaKey = schemaKey;
        this.templateFunctions = templateFunctions;
        this.clazz = clazz;
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
    final List<Function<? extends Identifier.Template<T>, ?>> templateFunctions;
    final Class<T> clazz;

    public sealed abstract static class Field<T extends Model, F> {
        protected final String name;
        final Function<T, F> encoder;
        private final Object schemaKey;

        protected Field(String name, Function<T, F> encoder, Object schemaKey) {
            this.name = name;
            this.encoder = encoder;
            this.schemaKey = schemaKey;
        }

        abstract List<String> definitionSchema(String name);
        abstract List<String> constraintSchema(String name, boolean includeForeign);
        abstract F get(ResultSet result, int startAt) throws SQLException;
        abstract int size();
        List<String> columns(String thisName) {
            return List.of(thisName);
        }

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

    public static final class ReferenceField<T extends Model, F extends Model> extends Field<T, Identifier<F>> {
        private final Supplier<Representation<F>> referenceRepresentation;

        private ReferenceField(String name, Function<T, Identifier<F>> encoder, Supplier<Representation<F>> referenceRepresentation, Object schemaKey) {
            super(name, encoder, schemaKey);
            this.referenceRepresentation = referenceRepresentation;
        }

        @Override
        List<String> definitionSchema(String name) {
            var idFields = getIdFields();
            if (idFields.size() == 1) {
                return idFields.getFirst().definitionSchema(name);
            } else {
                var out = new ArrayList<String>();
                for (var field : idFields) {
                    out.addAll(field.definitionSchema(name+"_"+field.name));
                }
                return out;
            }
        }

        @Override
        List<String> columns(String thisName) {
            if (getIdFields().size() == 1 || getIdFields().getFirst().columns(thisName).size() == 1) {
                return super.columns(thisName);
            } else {
                return getIdFields().stream()
                        .flatMap(f -> f.columns(thisName).stream())
                        .map(n -> name + "_" + n)
                        .toList();
            }
        }

        @Override
        List<String> constraintSchema(String name, boolean includeForeign) {
            var idFields = getIdFields();
            var out = new ArrayList<String>();
            if (idFields.size() == 1) {
                out.addAll(idFields.getFirst().constraintSchema(name, false));
            } else {
                for (var field : idFields) {
                    out.addAll(field.constraintSchema(name+"_"+field.name, false));
                }
            }
            if (includeForeign) {
                // If this is false, a higher level has alreayd consumed all the relevant fields as foreign keys
                out.add(String.format(
                        "FOREIGN KEY (%s) REFERENCES %s (%s)",
                        String.join(", ", columns(name)),
                        referenceRepresentation.get().tableName,
                        getIdFields().stream()
                                .flatMap(f -> f.columns(f.name).stream())
                                .collect(Collectors.joining(", "))
                ));
            }
            return out;
        }

        @Override
        Identifier<F> get(ResultSet resultSet, int startAt) throws SQLException {
            var args = new ArrayList<>();
            var idFields = getIdFields();
            for (var f : idFields) {
                args.add(f.get(resultSet, startAt));
                startAt += f.size();
            }
            return new Identifier<>(args.toArray(), referenceRepresentation.get(), referenceRepresentation.get().clazz);
        }

        private List<Field<F, ?>> getIdFields() {
            var idFields = referenceRepresentation.get().idFields;
            if (idFields.isEmpty()) {
                throw new IllegalArgumentException("Cannot reference non-identified table "+referenceRepresentation.get().tableName);
            }
            return idFields;
        }

        @Override
        int size() {
            var size = 0;
            for (var f : getIdFields()) {
                size += f.size();
            }
            return size;
        }

        @Override
        void write(int offset, PreparedStatement statement, Identifier<F> value) throws SQLException {
            var args = value.args;
            var fields = getIdFields();
            if (fields.size() != args.length) {
                throw new IllegalArgumentException("Wrong identifier format "+value+" for field "+name);
            }
            for (int i = 0; i < args.length; i++) {
                var arg = args[i];
                var f = fields.get(i);
                writeField(offset, statement, f, arg);
                offset += f.size();
            }
        }
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
        int size() {
            return 1;
        }

        @Override
        List<String> definitionSchema(String name) {
            return List.of(
                    String.format("%s %s NOT NULL", name, primitiveType.typeString())
            );
        }

        @Override
        List<String> constraintSchema(String name, boolean includeForeign) {
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
        int size() {
            return 1;
        }

        @Override
        List<String> definitionSchema(String name) {
            return List.of(
                    String.format("%s %s", name, primitiveType.typeString())
            );
        }

        @Override
        List<String> constraintSchema(String name, boolean includeForeign) {
            return List.of();
        }
    }

    public static <T extends Model> Representation<T> build(Function<Builder<T>, Representation<T>> function) {
        var builder = new Builder<T>();
        return function.apply(builder);
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
            offset += f.size();
        }
        var result = new Result(resultSet, schemaKey, map);
        return reconstructor.apply(result);
    }

    private int writeFull(int offset, PreparedStatement statement, T value) throws SQLException {
        for (var f : fields) {
            f.writeEncode(offset, statement, value);
            offset += f.size();
        }
        return offset;
    }

    private int writeIdentifier(int offset, PreparedStatement statement, Identifier<T> value) throws SQLException {
        if (value.args.length != idFields.size()) {
            throw new IllegalArgumentException("Wrong identifier format "+value+" for table "+tableName);
        }
        for (int i = 0; i < idFields.size(); i++) {
            var f = idFields.get(i);
            var v = value.args[i];
            writeField(offset,  statement, f, v);
            offset += f.size();
        }
        return offset;
    }

    private <V extends Partial.Value<T, V>> int writePartial(int offset, PreparedStatement statement, Partial.Value<T, V> value) throws SQLException {
        for (int i = 0; i < value.type().fields.size(); i++) {
            var f = value.type().fields.get(i);
            var v = value.type().valueGetters.get(i).apply(Partial.cast(value));
            writeField(offset,  statement, f, v);
            offset += f.size();
        }
        return offset;
    }

    public String schema() {
        List<String> parts = new ArrayList<>();
        fields.stream()
                .flatMap(f -> f.definitionSchema(f.name).stream())
                .forEach(parts::add);
        if (!idFields.isEmpty()) {
            parts.add(String.format("PRIMARY KEY (%s)", idFields.stream().map(f -> f.name).collect(Collectors.joining(", "))));
        }
        fields.stream()
                .flatMap(f -> f.constraintSchema(f.name, true).stream())
                .forEach(parts::add);
        return String.format("""
                CREATE TABLE IF NOT EXISTS %s (%s
                );
                """,
                tableName,
                parts.stream().map(s -> "\n    "+s).collect(Collectors.joining(","))
        );
    }

    List<T> select(ModelConnection connection) throws SQLException {
        try (var result = executeQuery(
                connection,
                String.format(
                        "SELECT %s FROM %s;",
                        fields.stream()
                                .flatMap(f -> f.columns(f.name).stream())
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
                    .flatMap(f -> f.columns(f.name).stream())
                    .collect(Collectors.joining(", ")),
                tableName,
                value.type().fields.stream()
                    .flatMap(f -> f.columns(f.name).stream())
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

    T select(ModelConnection connection, Identifier<T> identifier) throws SQLException {
        return find(connection, identifier).orElseThrow(() -> new NoSuchElementException("Element "+identifier+" does not exist in database!"));
    }

    void update(ModelConnection connection, T value) throws SQLException {
        executeUpdate(
            connection,
            String.format(
                "UPDATE %s SET %s WHERE %s;",
                tableName,
                fields.stream()
                    .flatMap(f -> f.columns(f.name).stream())
                    .map(f -> f + " = ?")
                    .collect(Collectors.joining(", ")),
                idFields.stream()
                    .flatMap(f -> f.columns(f.name).stream())
                    .map(f -> f + " = ?")
                    .collect(Collectors.joining(" AND "))
            ),
            statement -> {
                var offset = writeFull(1, statement, value);
                writeIdentifier(offset, statement, Identifier.of(this, value));
            }
        );
    }

    void delete(ModelConnection connection, T value) throws SQLException {
        delete(connection, Identifier.of(this, value));
    }

    void delete(ModelConnection connection, Identifier<T> identifier) throws SQLException {
        executeUpdate(
            connection,
            String.format(
                "DELETE FROM %s WHERE %s;",
                tableName,
                idFields.stream()
                    .flatMap(f -> f.columns(f.name).stream())
                    .map(f -> f + " = ?")
                    .collect(Collectors.joining(" AND "))
            ),
            statement -> writeIdentifier(1, statement, identifier)
        );
    }

    void insert(ModelConnection connection, T value) throws SQLException {
        executeUpdate(
            connection,
            String.format(
                "INSERT INTO %s (%s) VALUES (%s);",
                tableName,
                fields.stream()
                    .flatMap(f -> f.columns(f.name).stream())
                    .collect(Collectors.joining(", ")),
                fields.stream()
                    .flatMap(f -> f.columns(f.name).stream())
                    .map(f -> "?")
                    .collect(Collectors.joining(", "))
            ),
            statement -> writeFull(1, statement, value)
        );
    }

    Optional<T> find(ModelConnection connection, Identifier<T> identifier) throws SQLException {
        try (var result = executeQuery(
                connection,
                String.format(
                        "SELECT %s FROM %s WHERE %s;",
                        fields.stream()
                                .flatMap(f -> f.columns(f.name).stream())
                                .collect(Collectors.joining(", ")),
                        tableName,
                        idFields.stream()
                                .flatMap(f -> f.columns(f.name).stream())
                                .map(f -> f + " = ?")
                                .collect(Collectors.joining(" AND "))
                ),
                statement -> writeIdentifier(1, statement, identifier))) {
            if (!result.next()) {
                return Optional.empty();
            }
            return Optional.of(read(result));
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
            var currentVersionResult = executeQuery(c, """
                    SELECT MAX(version) FROM migrations""", _ -> {});
            var currentVersion = currentVersionResult.next() ? currentVersionResult.getInt(1) : 0;
            if (targetVersion < currentVersion) {
                for (int i = currentVersion; i > targetVersion; i--) {
                    var migration = migrations.downgrades.get(i-1);
                    executeUpdate(c, migration, _ -> {});
                    final var v = i;
                    executeUpdate(c, "DELETE FROM migrations WHERE version = ?;", p -> p.setInt(1, v));
                }
            } else if (targetVersion > currentVersion) {
                for (int i = currentVersion + 1; i <= targetVersion; i++) {
                    var migration = migrations.upgrades.get(i-1);
                    executeUpdate(c, migration, _ -> {});
                    final var v = i;
                    executeUpdate(c, "INSERT INTO migrations (version) VALUES (?);", p -> p.setInt(1, v));
                }
            }
        });
    }

    public static final class Builder<T extends Model> {
        private final Object schemaKey = new Object();
        private final List<Field<T, ?>> fields = new ArrayList<>();
        private final List<Field<T, ?>> idFields = new ArrayList<>();
        private final List<Function<? extends Identifier.Template<T>, ?>> templateFunctions = new ArrayList<>();

        private Builder() {}

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

        public <F extends Model> ReferenceField<T, F> referenceField(String name, Supplier<Representation<F>> reference, Function<T, Identifier<F>> encoder) {
            var field = new ReferenceField<>(name, encoder, reference, schemaKey);
            fields.add(field);
            return field;
        }

        public void id(Field<T, ?> field) {
            if (templateFunctions.size() != 0) {
                throw new IllegalStateException("ID fields mut be all template or non-template");
            }
            idFields.add(field);
        }
        public <P extends Identifier.Template<T>, F> void id(Field<T, F> field, Function<P, F> function) {
            if (idFields.size() != templateFunctions.size()) {
                throw new IllegalStateException("ID fields mut be all template or non-template");
            }
            idFields.add(field);
            templateFunctions.add(function);
        }

        public <F, V extends Partial.Value<T, V>> void partial(Partial<T, V> partial, Field<T, F> field, Function<V, F> getter) {
            partial.register(field, getter);
        }

        @PolymorphicSignature("$build")
        // Uses the caller model to build
        public native Representation<T> build(String tableName, SQLFunction<Result, T> reconstructor);

        public Representation<T> build(String tableName, SQLFunction<Result, T> reconstructor, Class<T> clazz) {
            if (!clazz.accessFlags().contains(AccessFlag.FINAL)) {
                throw new IllegalArgumentException("Representations may only be built for final model types!");
            }
            return new Representation<>(
                    tableName.toLowerCase(Locale.ROOT),
                    fields,
                    reconstructor,
                    idFields,
                    schemaKey,
                    templateFunctions,
                    clazz
            );
        }

        public static CallSite $build(MethodHandles.Lookup lookup, String name, MethodType descriptor, @Bootstrap.Caller Class<? extends Model> receiver) throws NoSuchMethodException, IllegalAccessException {
            var handle = MethodHandles.lookup().findVirtual(Builder.class, "build", MethodType.methodType(Representation.class, String.class, SQLFunction.class, Class.class));
            if (!Model.class.isAssignableFrom(receiver)) {
                throw new IllegalArgumentException("This method uses the caller class to build a representation. It does not work outside of the appropriate Model class");
            }
            return new ConstantCallSite(MethodHandles.insertArguments(handle, 3, receiver).asType(descriptor));
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Model> Representation<T> locate(Class<T> clazz) {
        java.lang.reflect.Field target = null;
        for (var field : clazz.getFields()) {
            if (Representation.class.isAssignableFrom(field.getType())) {
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
}
