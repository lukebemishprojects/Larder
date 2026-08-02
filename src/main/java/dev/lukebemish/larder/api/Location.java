package dev.lukebemish.larder.api;

import dev.lukebemish.larder.utils.Enums;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

import java.io.Serializable;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;

import static java.lang.constant.ConstantDescs.*;

/**
 * The (singular) implementation of this type is generated at runtime from a map of names to locations stored in the
 * json file pointed to by {@code System.getenv("LARDER_FILESYSTEM_BACKEND_LOCATIONS")}. If no such json file is
 * provided, then the implementation is empty.
 * <p>
 * <em>Why</em> is this done this way instead of using a record class with an array of values or something? Well... I
 * figured I'd end up having to do something like this more than once, and can abstract this out when I do; plus, it
 * saves implementing a bunch of serialization logic, valueOf, or the like.
 * <p>
 * This type is designed such that it is impossible to create an instance of it other than those defined through the
 * provided file; other locations simply cannot be represented.
 */
@JsonSerialize(using = LocationSerializer.class)
@JsonDeserialize(using = LocationDeserializer.class)
@Enums.EnumIsh
public sealed interface Location<L extends Location<L>> extends Comparable<L>, Constable, Serializable permits Locations {
    String name();
    int ordinal();
    Path location();

    static Location<?>[] values() {
        LocationsInitializer.checkFailure();
        try {
            return (Location<?>[]) LocationsInitializer.VALUES.invoke();
        } catch (Throwable e) {
            throw e instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(e);
        }
    }

    static Location<?> valueOf(String string) {
        LocationsInitializer.checkFailure();
        try {
            return (Location<?>) LocationsInitializer.VALUE_OF.invoke(string);
        } catch (Throwable e) {
            throw e instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(e);
        }
    }
}

final class LocationsInitializer {
    private static final ClassDesc CD_Paths = Paths.class.describeConstable().orElseThrow();
    private static final ClassDesc CD_Path = Path.class.describeConstable().orElseThrow();
    static final Class<Locations> LOCATIONS;
    static final MethodHandle VALUES;
    static final MethodHandle VALUE_OF;

    static final @Nullable RuntimeException LOCATIONS_FAILURE;

    static void checkFailure() {
        if (LOCATIONS_FAILURE != null) {
            throw LOCATIONS_FAILURE;
        }
    }

    static {
        var locations = new LinkedHashMap<String, String>();
        var locationsFileLocation = System.getenv("LARDER_FILESYSTEM_BACKEND_LOCATIONS");
        RuntimeException locationsFailure = null;
        try {
            if (locationsFileLocation != null) {
                var mapper = new JsonMapper();
                var locationTree = mapper.readValue(Paths.get(locationsFileLocation), new TypeReference<LinkedHashMap<String, String>>() {
                });
                locations.putAll(locationTree);
            }
        } catch (Throwable e) {
            locationsFailure = e instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(e);
        }
        LOCATIONS_FAILURE = locationsFailure;

        var self = ClassDesc.of("dev.lukebemish.larder.api.Locations");
        var bytes = ClassFile.of().build(self, clazz -> {
            clazz.withFlags(AccessFlag.ENUM, AccessFlag.FINAL);
            clazz.withSuperclass(CD_Enum);
            clazz.withInterfaceSymbols(Location.class.describeConstable().orElseThrow());

            clazz.withField("location", Path.class.describeConstable().orElseThrow(), f -> f.withFlags(AccessFlag.PRIVATE, AccessFlag.FINAL));
            clazz.withField("$VALUES", self.arrayType(), f -> f.withFlags(AccessFlag.PRIVATE, AccessFlag.FINAL, AccessFlag.SYNTHETIC, AccessFlag.STATIC));

            for (var entry : locations.entrySet()) {
                clazz.withField(entry.getKey(), self, f -> f.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.STATIC));
            }

            clazz.withMethod("values", MethodTypeDesc.of(self.arrayType()), ClassFile.ACC_STATIC | ClassFile.ACC_PUBLIC, method -> {
                method.withCode(code -> {
                    code.getstatic(self, "$VALUES", self.arrayType());
                    code.invokevirtual(self.arrayType(), "clone", MethodTypeDesc.of(CD_Object));
                    code.checkcast(self.arrayType());
                    code.areturn();
                });
            });

            clazz.withMethod("valueOf", MethodTypeDesc.of(self, CD_String), ClassFile.ACC_STATIC | ClassFile.ACC_PUBLIC, method -> {
                method.withCode(code -> {
                    code.loadConstant(self);
                    code.aload(0);
                    code.invokestatic(CD_Enum, "valueOf", MethodTypeDesc.of(CD_Enum, CD_Class, CD_String));
                    code.checkcast(self);
                    code.areturn();
                });
            });

            var mtdInit = MethodTypeDesc.of(CD_void, CD_String, CD_int, CD_String);
            clazz.withMethod("<init>", mtdInit, ClassFile.ACC_PRIVATE, method -> {
                method.withCode(code -> {
                    code.aload(0);
                    code.aload(1);
                    code.iload(2);
                    code.invokespecial(CD_Enum, "<init>", MethodTypeDesc.of(CD_void, CD_String, CD_int));
                    code.aload(0);
                    code.aload(3);
                    code.loadConstant(0);
                    code.anewarray(CD_String);
                    code.invokestatic(CD_Paths, "get", MethodTypeDesc.of(CD_Path, CD_String, CD_String.arrayType()));
                    code.putfield(self, "location", CD_Path);
                    code.return_();
                });
            });

            clazz.withMethod("location", MethodTypeDesc.of(CD_Path), ClassFile.ACC_FINAL | ClassFile.ACC_PUBLIC, method -> {
                method.withCode(code -> {
                    code.aload(0);
                    code.getfield(self, "location", CD_Path);
                    code.areturn();
                });
            });

            clazz.withMethod("<clinit>", MTD_void, ClassFile.ACC_STATIC, method -> {
                method.withCode(code -> {
                    code.loadConstant(locations.size());
                    code.anewarray(self);
                    code.astore(1);
                    int i = 0;
                    for (var entry : locations.entrySet()) {
                        code.aload(1);
                        code.loadConstant(i);
                        code.new_(self);
                        code.dup();
                        code.loadConstant(entry.getKey());
                        code.loadConstant(i);
                        code.loadConstant(entry.getValue());
                        code.invokespecial(self, "<init>", mtdInit);
                        code.dup();
                        code.putstatic(self, entry.getKey(), self);
                        code.aastore();
                        i++;
                    }
                    code.aload(1);
                    code.putstatic(self, "$VALUES", self.arrayType());
                    code.return_();
                });
            });
        });

        try {
            //noinspection unchecked
            LOCATIONS = (Class<Locations>) MethodHandles.lookup().defineClass(bytes);

            VALUES = MethodHandles.lookup().findStatic(LOCATIONS, "values", MethodType.methodType(LOCATIONS.arrayType()))
                .asType(MethodType.methodType(Location[].class));
            VALUE_OF = MethodHandles.lookup().findStatic(LOCATIONS, "valueOf", MethodType.methodType(LOCATIONS, String.class))
                .asType(MethodType.methodType(Location.class, String.class));
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}

final class LocationDeserializer extends ValueDeserializer<Location<?>> {
    @Override
    public Location<?> deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        LocationsInitializer.checkFailure();
        var string = ctxt.readValue(p, String.class);
        Location<?> location = Enums.tryValueOf(string);
        if (location == null) {
            throw UnrecognizedPropertyException.from(p, LocationsInitializer.LOCATIONS, string, Arrays.stream(Location.values()).<Object>map(Location::name).toList());
        }
        return location;
    }
}

final class LocationSerializer extends ValueSerializer<Location<?>> {
    @Override
    public void serialize(Location value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
        LocationsInitializer.checkFailure();
        gen.writeString(value.name());
    }
}

enum Locations implements Location<Locations> {
    ;
    private final Path location;

    Locations(String location) {
        this.location = Paths.get(location);
    }

    @Override
    public Path location() {
        return location;
    }
}
