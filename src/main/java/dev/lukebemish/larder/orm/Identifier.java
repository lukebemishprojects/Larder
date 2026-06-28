package dev.lukebemish.larder.orm;

import java.util.ArrayList;

public final class Identifier<T extends Model> {
    final Object[] args;

    Identifier(Object[] args) {
        this.args = args;
    }
    
    public Identifier(T value, Representation<T> representation) {
        var args = new ArrayList<>();
        var idFields = representation.idFields;
        if (idFields.isEmpty()) {
            throw new IllegalArgumentException("Cannot reference non-identified table "+representation.tableName);
        }
        for (var f : idFields) {
            args.add(f.encoder.apply(value));
        }
        this(args.toArray());
    }
}
