package dev.lukebemish.larder.orm;

import java.sql.Connection;
import java.sql.SQLException;

public class ModelConnection implements AutoCloseable {
    private final Connection connection;

    public ModelConnection(Connection connection) throws SQLException {
        this.connection = connection;
    }
    
    Connection connection() {
        return this.connection;
    }
    
    private boolean reentrantCheck = false;

    public void transact(SQLConsumer<ModelConnection> consumer) throws SQLException {
        this.transact(c -> {
            consumer.accept(c);
            return null;
        });
    }

    public synchronized <T> T transact(SQLFunction<ModelConnection, T> function) throws SQLException {
        connection.setAutoCommit(false);
        if (reentrantCheck) throw new IllegalStateException("Cannot re-enter a transaction");
        reentrantCheck = true;
        try {
            var out = function.apply(this);
            connection.commit();
            return out;
        } catch (Throwable t) {
            connection.rollback();
            throw t;
        } finally {
            connection.setAutoCommit(true);
            reentrantCheck = false;
        }
    }

    @Override
    public void close() throws SQLException {
        this.connection.close();
    }
}
