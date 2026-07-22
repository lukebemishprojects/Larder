package dev.lukebemish.larder.buildtooling;

import org.gradle.api.provider.MapProperty;

import java.util.List;

public abstract class ModularizeExtension {
    public abstract MapProperty<String, List<String>> getRequires();
    public abstract MapProperty<String, List<String>> getUses();
}
