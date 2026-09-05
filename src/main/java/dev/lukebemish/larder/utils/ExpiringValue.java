package dev.lukebemish.larder.utils;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

public class ExpiringValue<R, T extends Throwable> {
    public ExpiringValue(ExceptionalSupplier<R, T> callback, Duration lifetime) {
        this.callback = callback;
        this.lifetime = lifetime;
    }

    private record State<T>(T value, long expirationTime) {}

    private @Nullable State<R> value;
    private final ExceptionalSupplier<R, T> callback;
    private final Duration lifetime;

    public synchronized R get() throws T {
        if (value == null || Instant.ofEpochSecond(value.expirationTime).isBefore(Instant.now())) {
            value = new State<>(callback.get(), Instant.now().plus(lifetime).getEpochSecond());
        }
        return value.value;
    }

    public synchronized void reset() {
        this.value = null;
    }
}
