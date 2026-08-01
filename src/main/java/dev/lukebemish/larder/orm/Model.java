package dev.lukebemish.larder.orm;

import java.util.UUID;

public sealed interface Model {
    non-sealed interface Object extends Model {
        UUID id();
    }

    sealed interface Dependent<T extends Model.Object> extends Model {}

    non-sealed interface OneToMany<A extends Model.Object, B> extends Dependent<A> {
        interface BySource<S extends Model.Object, M extends OneToMany<S, ?>, V extends BySource<S, M, V>> extends Partial.Value<M, V> {
            Identifier<S> source();
        }
        interface ByValue<T, M extends OneToMany<?, T>, V extends ByValue<T, M, V>> extends Partial.Value<M, V> {
            T value();
        }
        interface ByPair<S extends Model.Object, T, M extends OneToMany<S, T>, V extends ByPair<S, T, M, V>> extends Partial.Value<M, V> {
            Identifier<S> source();
            T value();
        }
    }

    non-sealed interface Extension<O extends Model.Object> extends Dependent<O> {
        Identifier<O> id();

        interface ByHost<O extends Model.Object, M extends Extension<O>, V extends ByHost<O, M, V>> extends Partial.Value<M, V> {
            Identifier<O> id();
        }
    }
}
