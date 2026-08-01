package dev.lukebemish.larder.utils;

import dev.lukebemish.polymorphicsignatures.PolymorphicSignature;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class Enums {
    private Enums() {}

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface EnumIsh {}

    @PolymorphicSignature("$valueOf")
    public native static <T> T valueOf(String value) throws IllegalArgumentException;

    @PolymorphicSignature("$valueOf")
    public native static <T> @Nullable T tryValueOf(String value);

    public static CallSite $valueOf(MethodHandles.Lookup lookup, String name, MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
        var targetType = methodType.returnType();
        if (!Enum.class.isAssignableFrom(targetType) && !targetType.isAnnotationPresent(EnumIsh.class)) {
            throw new IllegalArgumentException("Not enum-ish: "+targetType);
        }
        var valueOf = lookup.findStatic(targetType, "valueOf", MethodType.methodType(targetType, String.class));
        if (name.equals("valueOf")) {
            return new ConstantCallSite(valueOf);
        }
        var constantNull = MethodHandles.dropArguments(
            MethodHandles.constant(targetType, null),
            0,
            IllegalArgumentException.class,
            String.class
        );
        return new ConstantCallSite(MethodHandles.catchException(valueOf, IllegalArgumentException.class, constantNull));
    }
}
