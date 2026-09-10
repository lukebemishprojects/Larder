package dev.lukebemish.larder;

import io.pebbletemplates.pebble.loader.Loader;
import io.pebbletemplates.pebble.utils.PathUtils;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.function.Predicate;

public class ModuleLoader implements Loader<String> {
    private @Nullable String prefix;
    private @Nullable String suffix;
    private @Nullable Predicate<String> predicate;
    private String charset = "UTF-8";
    private final Class<?> context;

    public ModuleLoader(Class<?> context) {
        this.context = context;
    }

    @Override
    public @Nullable Reader getReader(String cacheKey) {
        if (predicate != null && !predicate.test(cacheKey)) {
            return null;
        }
        var location = locate(cacheKey);
        var is = context.getResourceAsStream(location);
        if (is == null) {
            return null;
        }
        try {
            return new BufferedReader(new InputStreamReader(is, this.charset));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setCharset(String charset) {
        this.charset = charset;
    }

    @Override
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public void setPredicate(Predicate<String> predicate) {
        this.predicate = predicate;
    }

    @Override
    public @Nullable String resolveRelativePath(@Nullable String relativePath, String anchorPath) {
        return PathUtils.resolveRelativePath(relativePath, anchorPath, '/');
    }

    @Override
    public String createCacheKey(String templateName) {
        return templateName;
    }

    @Override
    public boolean resourceExists(String templateName) {
        if (predicate != null && !predicate.test(templateName)) {
            return false;
        }
        return this.context.getResource(locate(templateName)) != null;
    }

    private String locate(String templateName) {
        StringBuilder path = new StringBuilder(128);
        if (this.prefix != null) {
            path.append(this.prefix);
            if (!this.prefix.endsWith("/")) {
                path.append("/");
            }
        }
        path.append(templateName);
        if (this.suffix != null) {
            path.append(this.suffix);
        }
        return path.toString();
    }
}
