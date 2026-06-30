package dev.lukebemish.larder.orm;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.ArrayList;

@JsonSerialize(using = Identifier.IdentifierSerializer.class)
public final class Identifier<T extends Model> {
    final Object[] args;
    private final Representation<T> representation;

    Identifier(Object[] args, Representation<T> representation) {
        this.args = args;
        this.representation = representation;
    }

    public interface Template<T extends Model> {
        default Identifier<T> make(Representation<T> representation) {
            var args = new Object[representation.templateFunctions.size()];
            for (int i = 0; i < args.length; i++) {
                var getter = representation.templateFunctions.get(i);
                args[i] = getter.apply(cast(this));
            }
            return new Identifier<>(args, representation);
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Model, S extends Template<T>> S cast(Template<T> value) {
        return (S) value;
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
        this(args.toArray(), representation);
    }

    final static class IdentifierSerializer extends JsonSerializer<Identifier<?>> {
        @Override
        public void serialize(Identifier<?> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value.args.length == 1) {
                serializers.defaultSerializeValue(value.args[0], gen);
            } else {
                for (int i = 0; i < value.args.length; i++) {
                    var field = value.representation.idFields.get(i).name;
                    serializers.defaultSerializeField(field, value.args[i], gen);
                }
            }
        }
    }
}
