package dev.lukebemish.larder.utils;

@FunctionalInterface
public interface ExceptionalSupplier<R, T extends Throwable> {
    R get() throws T;
}
