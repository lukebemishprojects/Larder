package dev.lukebemish.larder.orm;

import java.sql.SQLException;

@FunctionalInterface
public interface SQLFunction<A, B> {
    B apply(A value) throws SQLException;
}
