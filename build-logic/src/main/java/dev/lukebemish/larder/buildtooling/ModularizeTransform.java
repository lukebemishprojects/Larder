package dev.lukebemish.larder.buildtooling;

import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

@CacheableTransform
public abstract class ModularizeTransform implements TransformAction<ModularizeTransform.Parameters> {
    public interface Parameters extends TransformParameters {
        @Input
        Property<JavaLanguageVersion> getLanguageVersion();

        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        RegularFileProperty getJdeps();

        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        RegularFileProperty getJar();

        @InputFile
        @PathSensitive(PathSensitivity.NONE)
        RegularFileProperty getJavac();

        @InputFiles
        @Classpath
        ConfigurableFileCollection getDependencies();

        @Input
        MapProperty<String, List<String>> getRequires();

        @Input
        MapProperty<String, List<String>> getUses();
    }

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Inject
    public ModularizeTransform() {}

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Override
    public void transform(TransformOutputs outputs) {
        var inputFile = getInputArtifact().get().getAsFile();
        try (var input = new JarFile(inputFile, false, ZipFile.OPEN_READ, Runtime.Version.parse(getParameters().getLanguageVersion().get().toString()))) {
            if (input.getJarEntry("module-info.class") != null) {
                // Already modular
                outputs.file(getInputArtifact());
                return;
            }
            var packages = new LinkedHashSet<String>();
            var entriesIter = input.entries();
            while (entriesIter.hasMoreElements()) {
                var entry = entriesIter.nextElement();
                if (entry.getName().endsWith(".class")) {
                    var lastIdx = entry.getName().lastIndexOf('/');
                    if (lastIdx != -1) {
                        var packageName = entry.getName().substring(0, lastIdx).replace('/', '.');
                        if (SourceVersion.isName(packageName)) {
                            packages.add(packageName);
                        }
                    }
                }
            }
            var multiRelease = input.isMultiRelease();
            var outFile = outputs.file(inputFile.getName());
            getFileSystemOperations().copy(copy -> {
                copy.into(outFile.getParentFile());
                copy.from(inputFile);
            });
            getExecOperations().exec(exec -> {
                exec.executable(getParameters().getJdeps().get());
                exec.workingDir(outFile.getParentFile());
                if (multiRelease) {
                    exec.args("--multi-release", input.getVersion().feature() >= 9 ? input.getVersion().feature() : "base");
                } else {
                    exec.args("--multi-release", getParameters().getLanguageVersion().get().asInt());
                }
                exec.args("--ignore-missing-deps");
                if (!getParameters().getDependencies().isEmpty()) {
                    exec.args("--module-path=" + getParameters().getDependencies().getAsPath());
                }
                exec.args("--generate-open-module", outFile.getParentFile().getAbsolutePath(), outFile.getAbsolutePath());
            });
            // Find module name
            var moduleDir = Objects.requireNonNull(outFile.getParentFile().listFiles(File::isDirectory))[0];

            File moduleJavaPath;
            if (multiRelease) {
                moduleJavaPath = moduleDir.toPath().resolve("versions").resolve(""+input.getVersion().feature()).resolve("module-info.java").toFile();
            } else {
                moduleJavaPath = moduleDir.toPath().resolve("versions").resolve(getParameters().getLanguageVersion().get().toString()).resolve("module-info.java").toFile();
            }

            // Patch module java path with exports
            String originalModuleJava;
            try (var is = new FileInputStream(moduleJavaPath)) {
                originalModuleJava = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            var lastIdx = originalModuleJava.lastIndexOf('}');
            String newModuleJava = originalModuleJava.substring(0, lastIdx) + packages.stream()
                .map(pkg -> "    exports "+pkg+";\n")
                .collect(Collectors.joining()) + getParameters().getRequires().get().getOrDefault(moduleDir.getName(), List.of()).stream()
                .map(entry -> "    requires "+entry+";\n")
                .collect(Collectors.joining()) + getParameters().getUses().get().getOrDefault(moduleDir.getName(), List.of()).stream()
                .map(entry -> "    uses "+entry+";\n")
                .collect(Collectors.joining()) + originalModuleJava.substring(lastIdx);
            try (var os = new FileOutputStream(moduleJavaPath)) {
                os.write(newModuleJava.getBytes(StandardCharsets.UTF_8));
            }

            getExecOperations().exec(exec -> {
                exec.executable(getParameters().getJavac().get());
                exec.workingDir(outFile.getParentFile());

                if (!getParameters().getDependencies().isEmpty()) {
                    exec.args("--module-path=" + getParameters().getDependencies().getAsPath());
                }
                exec.args("--patch-module", moduleDir.getName()+"="+outFile.getAbsolutePath(), moduleJavaPath.getAbsolutePath());
            });
            getExecOperations().exec(exec -> {
                exec.executable(getParameters().getJar().get());
                exec.workingDir(outFile.getParentFile());
                File inputDir;
                if (multiRelease) {
                    inputDir = moduleDir.toPath().resolve("versions").resolve(""+input.getVersion().feature()).toFile();
                } else {
                    inputDir = moduleDir.toPath().resolve("versions").resolve(getParameters().getLanguageVersion().get().toString()).toFile();
                }
                exec.args("-u", "--date=1980-02-01T00:00:00Z", "-f", outFile.getAbsolutePath(), "-C", inputDir.getAbsolutePath(), "module-info.class");
            });
            getFileSystemOperations().delete(delete -> {
                delete.delete(moduleDir);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
