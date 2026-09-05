package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

public record RoleRepository(Identifier<AccessRole> source, Identifier<Repository> value) implements Model.OneToMany<AccessRole, Identifier<Repository>> {
    public static final Partial<RoleRepository, ByRepository> BY_REPOSITORY = new Partial<>("by_repository");
    public record ByRepository(Identifier<Repository> value) implements ByValue<Identifier<Repository>, RoleRepository, ByRepository> {
        @Override
        public Partial<RoleRepository, ByRepository> type() {
            return BY_REPOSITORY;
        }
    }
    public static final Partial<RoleRepository, ByRole> BY_ROLE = new Partial<>("by_role");
    public record ByRole(Identifier<AccessRole> source) implements BySource<AccessRole, RoleRepository, ByRole> {
        @Override
        public Partial<RoleRepository, ByRole> type() {
            return BY_ROLE;
        }
    }
    public static final Partial<RoleRepository, ByBoth> BY_BOTH = new Partial<>("by_both");
    public record ByBoth(Identifier<AccessRole> source, Identifier<Repository> value) implements ByPair<AccessRole, Identifier<Repository>, RoleRepository, ByBoth> {
        @Override
        public Partial<RoleRepository, ByBoth> type() {
            return BY_BOTH;
        }
    }

    public static final Representation<RoleRepository> REPRESENTATION = Representation.build(
        Representation.referenceField("role", () -> AccessRole.REPRESENTATION, RoleRepository::source),
        Representation.referenceField("repository", () -> Repository.REPRESENTATION, RoleRepository::value),
        (it, source, value) -> {
            it.partialValue(BY_REPOSITORY);
            it.partialSource(BY_ROLE);
            it.partial(BY_BOTH);

            return it.build("rolerepositories", result -> new RoleRepository(
                source.get(result),
                value.get(result)
            ));
        });
}
