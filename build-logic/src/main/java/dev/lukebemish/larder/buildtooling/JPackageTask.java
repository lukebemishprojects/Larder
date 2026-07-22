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

public abstract class JPackageTask extends DefaultTask {
    @Inject
    public JPackageTask() {}

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

    @Input
    public abstract Property<String> getImageVersion();

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
            spec.executable(ConventionPlugin.getBinaryPath(getJavaCompiler().get(), "jpackage"));
            spec.args(
                "--type", "app-image",
                "-d", getDestinationDirectory().get().getAsFile().getAbsolutePath(),
                "-n", getImageName().get(),
                "--app-version", getImageVersion().get(),
                "-p", getModulePath().getAsPath(),
                "-p", getJavaCompiler().get().getMetadata().getInstallationPath().dir("jmods").getAsFile().getAbsolutePath(),
                "-m", getMainModule().get(),

                // By default, would have "--strip-debug"; modified to keep debug info
                // Also needs "--bind-services" or service impls won't be bundled
                "--jlink-options", "--strip-native-commands --no-man-pages --no-header-files --bind-services --compress=zip-9",

                // Must be specified both for jlink and for launching the application
                "--add-modules", String.join(",", getAddModules().get()),
                "--java-options", "--add-modules="+String.join(",", getAddModules().get())
            );
            for (var mod : getAddModules().get()) {
                spec.args(
                    "--add-modules", mod
                );
            }
        });
    }
}
