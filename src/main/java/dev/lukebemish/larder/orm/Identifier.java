package dev.lukebemish.larder.orm;

import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;

public final class Identifier<T extends Model> {
    final Object[] args;
    final Class<T> clazz;

    Identifier(Object[] args, Class<T> clazz) {
        this.args = args;
        this.clazz = clazz;
    }

    public interface Template<T extends Model> {}

    @PolymorphicSignature("$template")
    public native static <T extends Model, S extends Template<T>> S template(Identifier<T> identifier);

    @PolymorphicSignature("$template")
    public native static <T extends Model, S extends Template<T>> S template(T value);

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
        return new Identifier<>(args.toArray(), clazz);
    }

    private static <T extends Model, P extends Template<T>> Identifier<T> ofImpl(Representation<T> representation, Class<T> clazz, P template) {
        var args = new Object[representation.templateFunctions.size()];
        for (int i = 0; i < args.length; i++) {
            var getter = representation.templateFunctions.get(i);
            args[i] = getter.apply(cast(template));
        }
        return new Identifier<>(args, clazz);
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

    public static CallSite $template(MethodHandles.Lookup lookup, String name, MethodType descriptor) throws NoSuchMethodException, IllegalAccessException {
        var paramType = descriptor.parameterType(0);
        var returnTypeTemplateImpl = descriptor.returnType();

        if (!returnTypeTemplateImpl.isRecord()) {
            throw new IllegalArgumentException("Identifier.template only works with record template types with constructors taking their ID arguments in order");
        }

        Class<? extends Model> modelType;
        boolean fromIdentifier;
        if (Model.class.isAssignableFrom(paramType)) {
            fromIdentifier = false;
            //noinspection unchecked
            modelType = (Class<? extends Model>) paramType;

            var representation = Representation.locate(modelType);
            var applyHandle = MethodHandles.lookup().findVirtual(Function.class, "apply", MethodType.methodType(Object.class, Object.class));
            var encodeHandles = representation.idFields.stream().map(it -> applyHandle.bindTo(it.encoder)).toArray(MethodHandle[]::new); // Model -> Field

            return fieldsToTemplateConstruction(lookup, descriptor, paramType, returnTypeTemplateImpl, encodeHandles);

        } else if (Identifier.class.isAssignableFrom(paramType)) {
            var encodeHandles = IntStream.range(0, returnTypeTemplateImpl.getRecordComponents().length)
                .mapToObj(idx -> {
                    try {
                        // Identifier -> Object[]
                        var getArgs = MethodHandles.lookup().findGetter(Identifier.class, "args", Object[].class);
                        // Object[], int -> Object
                        var arrayElementGetter = MethodHandles.arrayElementGetter(Object[].class);
                        // Object[] -> Object
                        var thisElement = MethodHandles.insertArguments(arrayElementGetter, 1, idx);
                        // Identifier -> Object
                        return MethodHandles.filterArguments(thisElement, 0, getArgs);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }).toArray(MethodHandle[]::new);

            return fieldsToTemplateConstruction(lookup, descriptor, paramType, returnTypeTemplateImpl, encodeHandles);
        } else {
            throw new IllegalArgumentException("Cannot handle descriptor "+descriptor);
        }
    }

    private static CallSite fieldsToTemplateConstruction(MethodHandles.Lookup lookup, MethodType descriptor, Class<?> paramType, Class<?> returnTypeTemplateImpl, MethodHandle[] encodeHandles) throws NoSuchMethodException, IllegalAccessException {
        var ctor = lookup.findConstructor(returnTypeTemplateImpl, MethodType.methodType(
            void.class,
            Arrays.stream(returnTypeTemplateImpl.getRecordComponents()).map(RecordComponent::getType).toArray(Class[]::new)
        ));

        var filteredEncodeHandles = new MethodHandle[encodeHandles.length];
        for (int i = 0; i < encodeHandles.length; i++) {
            var componentType = ctor.type().parameterType(i);
            filteredEncodeHandles[i] = encodeHandles[i].asType(MethodType.methodType(componentType, encodeHandles[i].type().parameterArray()));
        }

        var handle = MethodHandles.filterArguments(ctor, 0, filteredEncodeHandles); // Model, Model, Model, ... -> Template
        for (int i = 1; i < encodeHandles.length; i++) {
            handle = MethodHandles.foldArguments(handle, MethodHandles.identity(paramType));
        }

        return new ConstantCallSite(handle.asType(descriptor));
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Identifier<?> that)) return false;
        return Objects.deepEquals(args, that.args) && Objects.equals(clazz, that.clazz);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(args), clazz);
    }

    @Override
    public String toString() {
        return "Identifier{" +
            "args=" + Arrays.toString(args) +
            ", clazz=" + clazz +
            '}';
    }
}
