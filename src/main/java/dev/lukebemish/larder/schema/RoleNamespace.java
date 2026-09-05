package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

public record RoleNamespace(Identifier<AccessRole> source, String value) implements Model.OneToMany<AccessRole, String> {
    public static final Partial<RoleNamespace, ByRole> BY_ROLE = new Partial<>("by_role");
    public record ByRole(Identifier<AccessRole> source) implements BySource<AccessRole, RoleNamespace, ByRole> {
        @Override
        public Partial<RoleNamespace, ByRole> type() {
            return BY_ROLE;
        }
    }

    public static final Representation<RoleNamespace> REPRESENTATION = Representation.build(
        Representation.referenceField("role", () -> AccessRole.REPRESENTATION, RoleNamespace::source),
        Representation.field("namespace", DatabasePrimitiveType.VARCHAR, RoleNamespace::value),
        (it, source, value) -> {
            it.partialSource(BY_ROLE);

            return it.build("rolenamespaces", result -> new RoleNamespace(
                source.get(result),
                value.get(result)
            ));
    });
}
