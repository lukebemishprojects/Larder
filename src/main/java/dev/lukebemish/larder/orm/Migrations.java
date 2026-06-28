package dev.lukebemish.larder.orm;

import java.util.ArrayList;
import java.util.List;

public class Migrations {
    final List<String> upgrades;
    final List<String> downgrades;

    private Migrations(List<String> upgrades, List<String> downgrades) {
        this.upgrades = upgrades;
        this.downgrades = downgrades;
    }
    
    int maxVersion() {
        return upgrades.size();
    }

    public static class Builder {
        final List<String> upgrades = new ArrayList<>();
        final List<String> downgrades = new ArrayList<>();
        
        public Builder upgrade(int toVersion, String migration) {
            if (toVersion != upgrades.size() + 1) {
                throw new IllegalArgumentException("Unexpected version: " + toVersion);
            }
            upgrades.add(migration);
            return this;
        }
        
        public Builder downgrade(int fromVersion, String migration) {
            if  (fromVersion != downgrades.size() + 1) {
                throw new IllegalArgumentException("Unexpected version: " + fromVersion);
            }
            downgrades.add(migration);
            return this;
        }
        
        public Migrations build() {
            if (upgrades.size() != downgrades.size()) {
                throw new IllegalArgumentException("Expected same number of upgrades and downgrades");
            }
            return new Migrations(List.copyOf(upgrades), List.copyOf(downgrades));
        }
    }
}
