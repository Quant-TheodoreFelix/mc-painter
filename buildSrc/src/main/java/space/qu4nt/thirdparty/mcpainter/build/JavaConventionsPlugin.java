package space.qu4nt.thirdparty.mcpainter.build;

import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

public final class JavaConventionsPlugin implements Plugin<Project> {

    private static final int JAVA_VERSION = 26;

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(JAVA_VERSION));

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.getOptions().setEncoding("UTF-8");
            task.getOptions().getCompilerArgs().addAll(List.of("-Xlint:all", "-Werror"));
        });

        VersionCatalog libs = project.getExtensions()
                .getByType(VersionCatalogsExtension.class)
                .named("libs");
        DependencyHandler deps = project.getDependencies();
        deps.add("testImplementation", deps.platform(libs.findLibrary("junit-bom").orElseThrow().get()));
        deps.add("testImplementation", libs.findLibrary("junit-jupiter").orElseThrow());
        deps.add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").orElseThrow());

        project.getTasks().withType(Test.class).configureEach(Test::useJUnitPlatform);
    }
}
