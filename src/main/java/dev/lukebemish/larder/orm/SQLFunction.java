package dev.lukebemish.larder.orm;

import org.jspecify.annotations.Nullable;

import java.sql.SQLException;

@FunctionalInterface
public interface SQLFunction<A, B extends @Nullable Object> {
    B apply(A value) throws SQLException;
}
