package dev.lukebemish.larder.orm;

import org.jspecify.annotations.Nullable;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.function.Function;

public sealed interface DatabasePrimitiveType<T> {
    int type();
    String typeString();
    @Nullable T get(ResultSet resultSet, int index) throws SQLException;
    void set(PreparedStatement statement, int index, T value) throws SQLException;

    DatabasePrimitiveType<Short> SMALL_INT = new DatabasePrimitiveTypeImpl<>("smallint", Types.SMALLINT, ResultSet::getShort, PreparedStatement::setShort);
    DatabasePrimitiveType<Integer> INTEGER = new DatabasePrimitiveTypeImpl<>("integer", Types.INTEGER, ResultSet::getInt, PreparedStatement::setInt);
    DatabasePrimitiveType<Long> BIGINT = new DatabasePrimitiveTypeImpl<>("bigint", Types.BIGINT, ResultSet::getLong, PreparedStatement::setLong);
    DatabasePrimitiveType<Float> REAL = new DatabasePrimitiveTypeImpl<>("real", Types.REAL, ResultSet::getFloat, PreparedStatement::setFloat);
    DatabasePrimitiveType<Double> DOUBLE = new DatabasePrimitiveTypeImpl<>("double precision", Types.DOUBLE, ResultSet::getDouble, PreparedStatement::setDouble);
    DatabasePrimitiveType<String> VARCHAR = new DatabasePrimitiveTypeImpl<>("varchar", Types.VARCHAR, ResultSet::getString, PreparedStatement::setString);
    DatabasePrimitiveType<Boolean> BOOLEAN = new DatabasePrimitiveTypeImpl<>("boolean", Types.BOOLEAN, ResultSet::getBoolean, PreparedStatement::setBoolean);
    DatabasePrimitiveType<LocalDateTime> TIMESTAMP = new DatabasePrimitiveTypeImpl<>("timestamp", Types.TIMESTAMP, ((DatabasePrimitiveTypeImpl.Getter<Timestamp>) ResultSet::getTimestamp).map(Timestamp::toLocalDateTime), ((DatabasePrimitiveTypeImpl.Setter<Timestamp>) PreparedStatement::setTimestamp).comap(Timestamp::valueOf));
    DatabasePrimitiveType<LocalDate> DATE = new DatabasePrimitiveTypeImpl<>("date", Types.DATE, ((DatabasePrimitiveTypeImpl.Getter<Date>) ResultSet::getDate).map(Date::toLocalDate), ((DatabasePrimitiveTypeImpl.Setter<Date>) PreparedStatement::setDate).comap(Date::valueOf));
    DatabasePrimitiveType<LocalTime> TIME = new DatabasePrimitiveTypeImpl<>("time", Types.TIME, ((DatabasePrimitiveTypeImpl.Getter<Time>) ResultSet::getTime).map(Time::toLocalTime), ((DatabasePrimitiveTypeImpl.Setter<Time>) PreparedStatement::setTime).comap(Time::valueOf));
    DatabasePrimitiveType<UUID> UUID = new DatabasePrimitiveTypeImpl<>("uuid", Types.OTHER, (result, idx) -> (UUID) result.getObject(idx), PreparedStatement::setObject);
    DatabasePrimitiveType<byte[]> BYTEA = new DatabasePrimitiveTypeImpl<>("bytea", Types.OTHER, ResultSet::getBytes, PreparedStatement::setBytes);

    static DatabasePrimitiveType<String> ofChar(int n) {
        return new DatabasePrimitiveTypeImpl<>("char("+n+")", Types.CHAR, ResultSet::getString, PreparedStatement::setString);
    }
}

record DatabasePrimitiveTypeImpl<T>(String typeString, int type, Getter<T> getter, Setter<T> setter) implements DatabasePrimitiveType<T> {
    @Override
    public @Nullable T get(ResultSet resultSet, int index) throws SQLException {
        return getter.get(resultSet, index);
    }

    @Override
    public void set(PreparedStatement statement, int index, T value) throws SQLException {
        setter.set(statement, index, value);
    }

    @FunctionalInterface
    interface Getter<T> {
        @Nullable T get(ResultSet resultSet, int index) throws SQLException;
        default <S> Getter<S> map(Function<T, S> function) {
            return (resultSet, idx) -> {
                var original = this.get(resultSet, idx);
                if (original == null) {
                    return null;
                }
                return function.apply(original);
            };
        }
    }

    @FunctionalInterface
    interface Setter<T> {
        void set(PreparedStatement statement, int index, T value) throws SQLException;
        default <S> Setter<S> comap(Function<S, T> function) {
            return (statement, idx, value) -> this.set(statement, idx, function.apply(value));
        }
    }
}
