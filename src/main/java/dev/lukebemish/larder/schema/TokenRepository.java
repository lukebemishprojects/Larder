package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

public record TokenRepository(Identifier<AccessToken> source, Identifier<Repository> value) implements Model.OneToMany<AccessToken, Identifier<Repository>> {
    public static final Partial<TokenRepository, ByRepository> BY_REPOSITORY = new Partial<>("by_repository");
    public record ByRepository(Identifier<Repository> value) implements ByValue<Identifier<Repository>, TokenRepository, ByRepository> {
        @Override
        public Partial<TokenRepository, ByRepository> type() {
            return BY_REPOSITORY;
        }
    }
    public static final Partial<TokenRepository, ByToken> BY_TOKEN = new Partial<>("by_token");
    public record ByToken(Identifier<AccessToken> source) implements BySource<AccessToken, TokenRepository, ByToken> {
        @Override
        public Partial<TokenRepository, ByToken> type() {
            return BY_TOKEN;
        }
    }

    public static final Representation<TokenRepository> REPRESENTATION = Representation.build(
        Representation.referenceField("token", () -> AccessToken.REPRESENTATION, TokenRepository::source),
        Representation.referenceField("repository", () -> Repository.REPRESENTATION, TokenRepository::value),
        (it, source, value) -> {
            it.partialValue(BY_REPOSITORY);
            it.partialSource(BY_TOKEN);

            return it.build("tokenrepositories", result -> new TokenRepository(
                source.get(result),
                value.get(result)
            ));
        });
}
