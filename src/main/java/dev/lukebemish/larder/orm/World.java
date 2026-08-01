package dev.lukebemish.larder.orm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.SequencedSet;

public interface World {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface BelongsTo {
        Class<? extends World> value();
    }

    SequencedSet<Representation<? extends Model>> types();
    SequencedSet<Representation<? extends Model.Dependent<?>>> dependents(Representation<? extends Model.Object> object);
}
