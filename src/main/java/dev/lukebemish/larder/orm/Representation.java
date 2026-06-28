package dev.lukebemish.larder.orm;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
    private Representation(String tableName, List<Field<T, ?>> fields, SQLFunction<Result, T> reconstructor, List<Field<T, ?>> idFields, Object schemaKey) {
        this.tableName = tableName;
        this.fields = fields;
        this.reconstructor = reconstructor;
        this.idFields = idFields;
        this.schemaKey = schemaKey;
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
        private final Object schemaKey;
        
        private ReferenceField(String name, Function<T, Identifier<F>> encoder, Supplier<Representation<F>> referenceRepresentation, Object schemaKey) {
            super(name, encoder, schemaKey);
            this.referenceRepresentation = referenceRepresentation;
            this.schemaKey = schemaKey;
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
            return new Identifier<>(args.toArray());
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
        return stmt.execute();
    }

    private static int executeUpdate(ModelConnection connection, String query, SQLConsumer<PreparedStatement> action) throws SQLException {
        var stmt = connection.connection().prepareStatement(query);
        return stmt.executeUpdate();
    }

    private static ResultSet executeQuery(ModelConnection connection, String query, SQLConsumer<PreparedStatement> action) throws SQLException {
        var stmt = connection.connection().prepareStatement(query);
        return stmt.executeQuery();
    }
    
    private T read(ResultSet resultSet) throws SQLException {
        var map = new IdentityHashMap<Field<?, ?>, Integer>();
        int offset = 0;
        for (var f : fields) {
            map.put(f, offset);
            offset += f.size();
        }
        var result = new Result(resultSet, schemaKey, map);
        return reconstructor.apply(result);
    }

    private void writeFull(PreparedStatement statement, T value) throws SQLException {
        int offset = 0;
        for (var f : fields) {
            f.writeEncode(offset, statement, value);
            offset += f.size();
        }
    }

    private void writeIdentifier(PreparedStatement statement, Identifier<T> value) throws SQLException {
        int offset = 0;
        if (value.args.length != fields.size()) {
            throw new IllegalArgumentException("Wrong identifier format "+value+" for table "+tableName);
        }
        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i);
            var v = value.args[i];
            writeField(offset,  statement, f, v);
            offset += f.size();
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

    public List<T> select(ModelConnection connection) throws SQLException {
        try (var result = executeQuery(
                connection,
                String.format(
                        "SELECT (%s) FROM %s",
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

    public T select(ModelConnection connection, Identifier<T> identifier) throws SQLException {
        return find(connection, identifier).orElseThrow(() -> new NoSuchElementException("Element "+identifier+" does not exist in database!"));
    }

    public Optional<T> find(ModelConnection connection, Identifier<T> identifier) throws SQLException {
        try (var result = executeQuery(
                connection,
                String.format(
                        "SELECT (%s) FROM %s WHERE %s",
                        idFields.stream()
                                .flatMap(f -> f.columns(f.name).stream())
                                .collect(Collectors.joining(", ")),
                        tableName,
                        idFields.stream()
                                .flatMap(f -> f.columns(f.name).stream())
                                .map(f -> f + " = ?")
                                .collect(Collectors.joining(", "))
                ),
                statement -> writeIdentifier(statement, identifier))) {
            if (!result.next()) {
                return Optional.empty();
            }
            return Optional.of(read(result));
        }
    }
    
    public static void migrate(ModelConnection connection, Migrations migrations, int targetVersion) throws SQLException {
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
            var currentVersion = currentVersionResult.getInt(0);
            if (targetVersion < currentVersion) {
                for (int i = currentVersion; i > targetVersion; i--) {
                    var migration = migrations.downgrades.get(i-1);
                    executeUpdate(c, migration, _ -> {});
                    final var v = i;
                    executeUpdate(c, "DELETE FROM migrations WHERE version = ?;", p -> p.setInt(0, v));
                }
            } else if (targetVersion > currentVersion) {
                for (int i = currentVersion + 1; i <= targetVersion; i++) {
                    var migration = migrations.upgrades.get(i-1);
                    executeUpdate(c, migration, _ -> {});
                    final var v = i;
                    executeUpdate(c, "INSERT INTO migrations (version) VALUES (?);", p -> p.setInt(0, v));
                }
            }
        });
    }
    
    public static final class Builder<T extends Model> {
        private final Object schemaKey = new Object();
        private final List<Field<T, ?>> fields = new ArrayList<>();
        private final List<Field<T, ?>> idFields = new ArrayList<>();
        
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
            idFields.add(field);
        }
        
        public Representation<T> build(String tableName, SQLFunction<Result, T> reconstructor) {
            return new Representation<>(
                    tableName.toLowerCase(Locale.ROOT),
                    fields,
                    reconstructor,
                    idFields,
                    schemaKey);
        }
    }
}
