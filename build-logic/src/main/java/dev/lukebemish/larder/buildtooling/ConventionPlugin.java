package dev.lukebemish.larder.buildtooling;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.RegularFile;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.inject.Inject;

public abstract class ConventionPlugin implements Plugin<Project> {
    public static final Attribute<String> MODULARIZED = Attribute.of("dev.lukebemish.larder.buildtooling.modularized", String.class);

    @Inject
    public ConventionPlugin() {}

    @Inject
    protected abstract JavaToolchainService getJavaToolchainService();

    @Override
    public void apply(Project project) {
        project.getDependencies().getArtifactTypes().named("jar", type -> {
            type.getAttributes().attribute(MODULARIZED, "<NA>");
        });

        var modularizeExt = project.getExtensions().create("modularize", ModularizeExtension.class);

        project.getPluginManager().apply("java-base");
        project.getExtensions().getByType(SourceSetContainer.class).configureEach(sourceSet -> {
            var runtimeClasspath = project.getConfigurations().named(sourceSet.getRuntimeClasspathConfigurationName(), config -> {
                config.getAttributes().attribute(MODULARIZED, sourceSet.getName());
            });

            project.getConfigurations().named(sourceSet.getCompileClasspathConfigurationName(), config -> {
                config.getAttributes().attribute(MODULARIZED, sourceSet.getName());
            });

            project.getDependencies().registerTransform(ModularizeTransform.class, spec -> {
                var compiler = getJavaToolchainService().compilerFor(toolchain -> {
                    toolchain.getLanguageVersion().set(JavaLanguageVersion.of(25));
                });
                spec.getParameters().getLanguageVersion().set(compiler.map(it -> it.getMetadata().getLanguageVersion()));
                spec.getParameters().getJar().set(compiler.map(it -> getBinaryPath(it, "jar")));
                spec.getParameters().getJdeps().set(compiler.map(it -> getBinaryPath(it, "jdeps")));
                spec.getParameters().getJavac().set(compiler.map(JavaCompiler::getExecutablePath));
                spec.getParameters().getDependencies().from(runtimeClasspath.get().getIncoming().artifactView(view -> {
                    view.getAttributes().attribute(MODULARIZED, "<NA>");
                }).getFiles());
                spec.getParameters().getRequires().set(modularizeExt.getRequires());
                spec.getParameters().getUses().set(modularizeExt.getUses());

                spec.getFrom()
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
                    .attribute(MODULARIZED, "<NA>");

                spec.getTo()
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
                    .attribute(MODULARIZED, sourceSet.getName());
            });
        });
    }

    static RegularFile getBinaryPath(JavaCompiler compiler, String tool) {
        var compilerFile = compiler.getExecutablePath().getAsFile().getName();
        return compiler.getMetadata().getInstallationPath().dir("bin").file(compilerFile.endsWith(".exe") ? tool + ".exe" : tool);
    }
}
