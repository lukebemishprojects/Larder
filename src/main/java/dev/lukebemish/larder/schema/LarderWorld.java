package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Representation;
import dev.lukebemish.larder.orm.World;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;

class LarderWorld implements World {
    // Representation<?>[] array here is replaced by the ORM post-processor to give a list of all model representations in the ORM package
    public static final LarderWorld INSTANCE = new LarderWorld(new Representation<?>[0]);

    private final SequencedSet<Representation<? extends Model>> representationsInOrder;
    private final Map<Class<? extends Model.Object>, SequencedSet<Representation<? extends Model.Dependent<?>>>> representationDependents;

    private LarderWorld(Representation<?>[] representations) {
        var sorted = new LinkedHashSet<Representation<?>>();
        var working = new HashSet<Representation<?>>();

        var dependentMap = new IdentityHashMap<Class<? extends Model.Object>, SequencedSet<Representation<? extends Model.Dependent<?>>>>();

        for (var repr : representations) {
            addSorted(sorted, repr, working);

            var host = Representation.host(repr);
            if (host != null) {
                dependentMap.computeIfAbsent(Representation.representedType(host), _ -> new LinkedHashSet<>())
                    .add((Representation<? extends Model.Dependent<?>>) repr);
            }
        }
        this.representationsInOrder = Collections.unmodifiableSequencedSet(sorted);
        this.representationDependents = dependentMap;
    }

    private void addSorted(SequencedSet<Representation<?>> added, Representation<?> toAdd, Set<Representation<?>> adding) {
        if (adding.contains(toAdd)) {
            throw new IllegalStateException("Detected circular representation relationship");
        }
        if (added.contains(toAdd)) {
            return;
        }
        adding.add(toAdd);
        for (var dependency : toAdd.references()) {
            addSorted(added, dependency, adding);
        }
        added.add(toAdd);
        adding.remove(toAdd);
    }

    @Override
    public SequencedSet<Representation<? extends Model>> types() {
        return representationsInOrder;
    }

    @Override
    public SequencedSet<Representation<? extends Model.Dependent<?>>> dependents(Representation<? extends Model.Object> object) {
        return Collections.unmodifiableSequencedSet(representationDependents.get(Representation.representedType(object)));
    }
}
