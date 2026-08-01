package dev.lukebemish.larder.orm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class Partial<T extends Model, V extends Partial.Value<T, V>> {
    final List<Representation.Field<T, ?>> fields = new ArrayList<>();
    final List<Function<V, ?>> valueGetters = new ArrayList<>();
    final String name;

    public Partial(String name) {
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    static <T extends Model, V extends Value<T, V>> V cast(Value<T,V> value) {
        return (V) value;
    }

    <F> void register(Representation.Field<T, F> field, Function<V, F> getter) {
        fields.add(field);
        valueGetters.add(getter);
    }

    public interface Value<T extends Model, V extends Value<T, V>> {
        Partial<T, V> type();
    }
}
