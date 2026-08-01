package dev.lukebemish.larder.schema;

import dev.lukebemish.larder.orm.DatabasePrimitiveType;
import dev.lukebemish.larder.orm.Identifier;
import dev.lukebemish.larder.orm.Model;
import dev.lukebemish.larder.orm.Partial;
import dev.lukebemish.larder.orm.Representation;

public record TokenNamespace(Identifier<AccessToken> source, String value) implements Model.OneToMany<AccessToken, String> {
    public static final Partial<TokenNamespace, ByToken> BY_TOKEN = new Partial<>("by_token");
    public record ByToken(Identifier<AccessToken> source) implements BySource<AccessToken, TokenNamespace, ByToken> {
        @Override
        public Partial<TokenNamespace, ByToken> type() {
            return BY_TOKEN;
        }
    }

    public static final Representation<TokenNamespace> REPRESENTATION = Representation.build(
        Representation.referenceField("token", () -> AccessToken.REPRESENTATION, TokenNamespace::source),
        Representation.field("namespace", DatabasePrimitiveType.VARCHAR, TokenNamespace::value),
        (it, source, value) -> {
            it.partialSource(BY_TOKEN);

            return it.build("tokennamespaces", result -> new TokenNamespace(
                source.get(result),
                value.get(result)
            ));
    });
}
