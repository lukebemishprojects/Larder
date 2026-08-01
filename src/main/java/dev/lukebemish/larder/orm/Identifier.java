package dev.lukebemish.larder.orm;

import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.UUID;

public final class Identifier<T extends Model.Object> {
    final UUID id;
    final Class<T> clazz;

    Identifier(UUID id, Class<T> clazz) {
        this.id = id;
        this.clazz = clazz;
    }

    public UUID id() {
        return this.id;
    }

    @PolymorphicSignature("$of")
    public native static <T extends Model.Object> Identifier<T> of(T value);

    public static <T extends Model.Object> Identifier<T> of(Representation<T> representation, T value) {
        return ofImpl(representation, representation.clazz, value);
    }

    public static <T extends Model.Object> Identifier<T> of(Representation<T> representation, UUID id) {
        return new Identifier<>(id, representation.clazz);
    }

    static <T extends Model.Object> Identifier<T> ofImpl(Representation<T> representation, Class<T> clazz, T value) {
        return new Identifier<>(value.id(), clazz);
    }

    public static CallSite $of(MethodHandles.Lookup lookup, String name, MethodType descriptor) throws NoSuchMethodException, IllegalAccessException {
        var paramType = descriptor.parameterType(0);
        MethodHandle handle;
        Class<? extends Model.Object> modelType;
        if (Model.Object.class.isAssignableFrom(paramType)) {
            handle = MethodHandles.lookup().findStatic(Identifier.class, "ofImpl", MethodType.methodType(Identifier.class, Representation.class, Class.class, Model.Object.class));
            //noinspection unchecked
            modelType = (Class<? extends Model.Object>) paramType;
        } else {
            throw new IllegalArgumentException("Cannot handle descriptor "+descriptor);
        }
        var representation = Representation.expensiveLocate(modelType);
        return new ConstantCallSite(MethodHandles.insertArguments(handle, 0, representation, modelType).asType(descriptor));
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Identifier<?> that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(clazz, that.clazz);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, clazz);
    }

    @Override
    public String toString() {
        return "Identifier{" +
            "id=" + id +
            ", clazz=" + clazz +
            '}';
    }
}
