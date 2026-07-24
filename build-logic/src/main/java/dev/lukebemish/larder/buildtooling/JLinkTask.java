package dev.lukebemish.larder.buildtooling;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

public abstract class JLinkTask extends DefaultTask {
    @Inject
    public JLinkTask() {
        getModulePath().from(
            getJavaCompiler().map(it -> it.getMetadata().getInstallationPath().dir("jmods"))
        );
    }

    @Nested
    public abstract Property<JavaCompiler> getJavaCompiler();

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    protected Provider<Directory> getJavaInstallation() {
        return getJavaCompiler().map(it -> it.getMetadata().getInstallationPath());
    }

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getModulePath();

    @Input
    public abstract Property<String> getMainModule();

    @Input
    public abstract Property<String> getImageName();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Internal
    public abstract DirectoryProperty getDestinationDirectory();

    @OutputDirectory
    public Provider<Directory> getBundleDirectory() {
        return getDestinationDirectory().zip(getImageName(), Directory::dir);
    }

    @Input
    public abstract ListProperty<String> getAddModules();

    @TaskAction
    public void run() {
        getFileSystemOperations().delete(spec -> {
            spec.delete(getBundleDirectory().get());
        });

        getExecOperations().exec(spec -> {
            spec.executable(ConventionPlugin.getBinaryPath(getJavaCompiler().get(), "jlink"));
            spec.args(
                "--output", getBundleDirectory().get().getAsFile().getAbsolutePath(),
                "--module-path", getModulePath().getAsPath(),
                "--add-modules", getMainModule().get(),

                "--generate-cds-archive", "--no-man-pages", "--no-header-files", "--bind-services", "--compress=zip-9",

                "--add-modules", String.join(",", getAddModules().get()),
                "--add-options=--add-modules="+String.join(",", getAddModules().get())
            );
        });
    }
}
