package dev.lukebemish.larder.orm;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;

import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;

@JsonSerialize(using = Identifier.IdentifierSerializer.class)
public final class Identifier<T extends Model> {
    final Object[] args;
    private final Representation<T> representation;
    final Class<T> clazz;

    Identifier(Object[] args, Representation<T> representation, Class<T> clazz) {
        this.args = args;
        this.representation = representation;
        this.clazz = clazz;
    }

    public interface Template<T extends Model> {}

    @SuppressWarnings("unchecked")
    static <T extends Model, S extends Template<T>> S cast(Template<T> value) {
        return (S) value;
    }

    @PolymorphicSignature("$of")
    public native static <T extends Model> Identifier<T> of(T value);

    @PolymorphicSignature("$of")
    public native static <T extends Model, P extends Template<T>> Identifier<T> of(P template);

    public static <T extends Model> Identifier<T> of(Representation<T> representation, T value) {
        return ofImpl(representation, representation.clazz, value);
    }

    public static <T extends Model, P extends Template<T>> Identifier<T> of(Representation<T> representation, P template) {
        return ofImpl(representation, representation.clazz, template);
    }

    static <T extends Model> Identifier<T> ofImpl(Representation<T> representation, Class<T> clazz, T value) {
        var args = new ArrayList<>();
        var idFields = representation.idFields;
        if (idFields.isEmpty()) {
            throw new IllegalArgumentException("Cannot reference non-identified table "+representation.tableName);
        }
        for (var f : idFields) {
            args.add(f.encoder.apply(value));
        }
        return new Identifier<>(args.toArray(), representation, clazz);
    }

    private static <T extends Model, P extends Template<T>> Identifier<T> ofImpl(Representation<T> representation, Class<T> clazz, P template) {
        var args = new Object[representation.templateFunctions.size()];
        for (int i = 0; i < args.length; i++) {
            var getter = representation.templateFunctions.get(i);
            args[i] = getter.apply(cast(template));
        }
        return new Identifier<>(args, representation, clazz);
    }

    public static CallSite $of(MethodHandles.Lookup lookup, String name, MethodType descriptor) throws NoSuchMethodException, IllegalAccessException {
        var paramType = descriptor.parameterType(0);
        MethodHandle handle;
        Class<? extends Model> modelType;
        if (Model.class.isAssignableFrom(paramType)) {
            handle = MethodHandles.lookup().findStatic(Identifier.class, "ofImpl", MethodType.methodType(Identifier.class, Representation.class, Class.class, Model.class));
            //noinspection unchecked
            modelType = (Class<? extends Model>) paramType;
        } else if (Template.class.isAssignableFrom(paramType)) {
            handle = MethodHandles.lookup().findStatic(Identifier.class, "ofImpl", MethodType.methodType(Identifier.class, Representation.class, Class.class, Template.class));
            Class<?> foundModelType = null;
            for (var genericInterface : paramType.getGenericInterfaces()) {
                if (genericInterface instanceof ParameterizedType parameterizedType) {
                    if (Template.class.equals(parameterizedType.getRawType())) {
                        if (parameterizedType.getActualTypeArguments()[0] instanceof Class<?> clazz) {
                            foundModelType = clazz;
                            break;
                        }
                    }
                }
            }
            if (foundModelType == null) {
                throw new IllegalArgumentException("Template type "+paramType+" does not directly implement Identifier.Template, or cannot infer model type from template type parameter");
            }
            //noinspection unchecked
            modelType = (Class<? extends Model>) foundModelType;
        } else {
            throw new IllegalArgumentException("Cannot handle descriptor "+descriptor);
        }
        var representation = Representation.locate(modelType);
        return new ConstantCallSite(MethodHandles.insertArguments(handle, 0, representation, modelType).asType(descriptor));
    }

    final static class IdentifierSerializer extends JsonSerializer<Identifier<?>> {
        @Override
        public void serialize(Identifier<?> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value.args.length == 1) {
                serializers.defaultSerializeValue(value.args[0], gen);
            } else {
                for (int i = 0; i < value.args.length; i++) {
                    var field = value.representation.idFields.get(i).name;
                    serializers.defaultSerializeField(field, value.args[i], gen);
                }
            }
        }
    }
}
