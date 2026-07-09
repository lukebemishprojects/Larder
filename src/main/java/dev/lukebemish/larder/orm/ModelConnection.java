package dev.lukebemish.larder.orm;

import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ModelConnection {
    private final Connection connection;

    public ModelConnection(Connection connection) throws SQLException {
        this.connection = connection;
    }

    Connection connection() {
        return this.connection;
    }

    private boolean reentrantCheck = false;

    public void transact(SQLConsumer<ModelConnection> consumer) throws SQLException {
        this.transact(c -> {
            consumer.accept(c);
            return null;
        });
    }

    public synchronized <T> T transact(SQLFunction<ModelConnection, T> function) throws SQLException {
        connection.setAutoCommit(false);
        if (reentrantCheck) throw new IllegalStateException("Cannot re-enter a transaction");
        reentrantCheck = true;
        try {
            var out = function.apply(this);
            connection.commit();
            return out;
        } catch (Throwable t) {
            connection.rollback();
            throw t;
        } finally {
            connection.setAutoCommit(true);
            reentrantCheck = false;
        }
    }

    private final Map<Class<? extends Model>, Representation<?>> located = Collections.synchronizedMap(new IdentityHashMap<>());

    @SuppressWarnings("unchecked")
    private <T extends Model> Representation<T> locate(Class<T> clazz) {
        return (Representation<T>) located.computeIfAbsent(clazz, k -> Representation.locate((Class<? extends Model>) k));
    }

    @PolymorphicSignature("$select")
    public native <T extends Model, V extends Partial.Value<T, V>> List<T> select(Partial.Value<T, V> value) throws SQLException;

    public <T extends Model> List<T> select(Representation<T> representation) throws SQLException {
        return representation.select(this);
    }

    public <T extends Model> T select(Identifier<T> identifier) throws SQLException {
        return locate(identifier.clazz).select(this, identifier);
    }

    @PolymorphicSignature("$located")
    public native  <T extends Model> void update(T value) throws SQLException;

    @PolymorphicSignature("$located")
    public native  <T extends Model> void delete(T value) throws SQLException;

    public <T extends Model> void delete(Identifier<T> identifier) throws SQLException {
        locate(identifier.clazz).delete(this, identifier);
    }

    @PolymorphicSignature("$located")
    public native  <T extends Model> void insert(T value) throws SQLException;

    public <T extends Model> Optional<T> find(Identifier<T> identifier) throws SQLException {
        return locate(identifier.clazz).find(this, identifier);
    }

    public void migrate(Migrations migrations, int targetVersion) throws SQLException {
        Representation.migrate(this, migrations, targetVersion);
    }

    public void closeConnection() throws SQLException {
        this.connection.close();
    }

    @SuppressWarnings("unchecked")
    public static CallSite $located(MethodHandles.Lookup lookup, String name, MethodType descriptor) throws NoSuchMethodException, IllegalAccessException {
        Class<? extends Model> modelType = (Class<? extends Model>) descriptor.parameterType(1);
        var handle = MethodHandles.lookup().unreflect(Representation.class.getDeclaredMethod(name, ModelConnection.class, Model.class));
        var representation = Representation.locate(modelType);
        return new ConstantCallSite(handle.bindTo(representation).asType(descriptor));
    }

    @SuppressWarnings("unchecked")
    public static CallSite $select(MethodHandles.Lookup lookup, String name, MethodType descriptor) throws NoSuchMethodException, IllegalAccessException {
        var partialValueType = descriptor.parameterType(1);
        var handle = MethodHandles.lookup().findVirtual(Representation.class, "select", MethodType.methodType(List.class, ModelConnection.class, Partial.Value.class));
        Class<? extends Model> foundModelType = null;
        for (var inter : partialValueType.getGenericInterfaces()) {
            if (inter instanceof ParameterizedType parameterizedType) {
                if (Partial.Value.class.equals(parameterizedType.getRawType())) {
                    if (parameterizedType.getActualTypeArguments()[0] instanceof Class<?> clazz) {
                        foundModelType = (Class<? extends Model>) clazz;
                        break;
                    }
                }
            }
        }
        if (foundModelType == null) {
            throw new IllegalArgumentException("Partial value type "+partialValueType+" does not directly implement Partial.Value, or cannot infer model type from partial value type parameter");
        }
        return new ConstantCallSite(handle.bindTo(Representation.locate(foundModelType)).asType(descriptor));
    }
}
