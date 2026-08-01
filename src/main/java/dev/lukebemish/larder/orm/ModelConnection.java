package dev.lukebemish.larder.orm;

import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
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
        return (Representation<T>) located.computeIfAbsent(clazz, k -> Representation.expensiveLocate((Class<? extends Model>) k));
    }

    @PolymorphicSignature("$select")
    public native <T extends Model, V extends Partial.Value<T, V>> List<T> select(Partial.Value<T, V> value) throws SQLException;

    public <T extends Model> List<T> select(Representation<T> representation) throws SQLException {
        return representation.select(this);
    }

    public <T extends Model.Object> T select(Identifier<T> identifier) throws SQLException {
        return Representation.select(locate(identifier.clazz), this, identifier);
    }

    @PolymorphicSignature("$located")
    public native  <T extends Model> void update(T value) throws SQLException;

    @PolymorphicSignature("$located")
    public native  <T extends Model> void delete(T value) throws SQLException;

    @PolymorphicSignature("$delete")
    public native <T extends Model, V extends Partial.Value<T, V>> void delete(Partial.Value<T, V> value) throws SQLException;

    public <T extends Model.Object> void delete(Identifier<T> identifier) throws SQLException {
        Representation.delete(locate(identifier.clazz), this, identifier);
    }

    @PolymorphicSignature("$located")
    public native  <T extends Model> void insert(T value) throws SQLException;

    public <T extends Model.Object> Optional<T> find(Identifier<T> identifier) throws SQLException {
        return Representation.find(locate(identifier.clazz), this, identifier);
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
        var handle = MethodHandles.lookup().unreflect(Representation.class.getDeclaredMethod(name, Representation.class, ModelConnection.class, Model.class));
        var representation = Representation.expensiveLocate(modelType);
        return new ConstantCallSite(handle.bindTo(representation).asType(descriptor));
    }

    public static CallSite $select(MethodHandles.Lookup lookup, String name, MethodType descriptor) throws NoSuchMethodException, IllegalAccessException {
        var partialValueType = descriptor.parameterType(1);
        var handle = MethodHandles.lookup().findVirtual(Representation.class, "select", MethodType.methodType(List.class, ModelConnection.class, Partial.Value.class));
        Class<? extends Model> foundModelType = findModelType(partialValueType);
        return new ConstantCallSite(handle.bindTo(Representation.expensiveLocate(foundModelType)).asType(descriptor));
    }

    public static CallSite $delete(MethodHandles.Lookup lookup, String name, MethodType descriptor) throws NoSuchMethodException, IllegalAccessException {
        var partialValueType = descriptor.parameterType(1);
        var handle = MethodHandles.lookup().findVirtual(Representation.class, "delete", MethodType.methodType(void.class, ModelConnection.class, Partial.Value.class));
        Class<? extends Model> foundModelType = findModelType(partialValueType);
        return new ConstantCallSite(handle.bindTo(Representation.expensiveLocate(foundModelType)).asType(descriptor));
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Class<? extends Model> findModelTypeFromPartial(Class<?> partialValueType, Type[] typeArgs, Class<?>[] typeArgsValues) {
        for (var inter : partialValueType.getGenericInterfaces()) {
            if (inter instanceof ParameterizedType parameterizedType) {
                if (Partial.Value.class.equals(parameterizedType.getRawType())) {
                    if (parameterizedType.getActualTypeArguments()[0] instanceof Class<?> clazz) {
                        if (clazz.equals(void.class)) {
                            return null;
                        }
                        return (Class<? extends Model>) clazz;
                    }
                    for (int i = 0; i < typeArgs.length; i++) {
                        var arg = typeArgs[i];
                        if (parameterizedType.getActualTypeArguments()[0].equals(arg)) {
                            return (Class<? extends Model>) typeArgsValues[i];
                        }
                    }
                } else if (Partial.Value.class.isAssignableFrom((Class<?>) parameterizedType.getRawType())) {
                    var newTypeArgs = ((Class<?>) parameterizedType.getRawType()).getTypeParameters();
                    var newTypeArgsValues = parameterizedType.getActualTypeArguments();
                    var newTypeArgsFilled = new Class<?>[newTypeArgsValues.length];
                    outer: for (int i = 0; i < newTypeArgsFilled.length; i++) {
                        var value = newTypeArgsValues[i];
                        if (value instanceof Class<?> clazz) {
                            newTypeArgsFilled[i] = clazz;
                            continue;
                        }
                        for (int j = 0; j < typeArgs.length; j++) {
                            if (typeArgs[j].equals(value)) {
                                newTypeArgsFilled[i] = typeArgsValues[j];
                                continue outer;
                            }
                        }
                    }
                    return findModelTypeFromPartial((Class<?>) parameterizedType.getRawType(), newTypeArgs, newTypeArgsFilled);
                }
            }
        }
        return null;
    }

    private static Class<? extends Model> findModelType(Class<?> partialValueType) {
        Class<? extends Model> foundModelType = findModelTypeFromPartial(partialValueType, new Type[0], new Class<?>[0]);
        if (foundModelType == null) {
            throw new IllegalArgumentException("Partial value type "+ partialValueType +" does not directly implement Partial.Value, or cannot infer model type from partial value type parameter");
        }
        return foundModelType;
    }
}
