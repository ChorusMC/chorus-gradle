package net.fabricmc.loom.ide;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.ProjectState;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.plugins.ide.internal.tooling.GradleProjectBuilder;
import org.gradle.plugins.ide.internal.tooling.IdeaModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.jetbrains.plugins.gradle.tooling.internal.ExtraModelBuilder;

import net.fabricmc.loom.ide.gradle.idea.IdeaResolvingIdeaModelBuilder;
import net.fabricmc.loom.ide.idea.IdeaLoomExternalProjectModelBuilder;
import net.fabricmc.loom.ide.idea.classloading.ClassLoadingHandler;

/**
 * Adds a single extra model builder instance for each root of ToolingModelBuilderRegistry hierarchy<br>
 * Thread safe.
 */
public class ToolingModelBuilderRegistryProcessor {

    public static void attach(Project project)
    {
        project.afterEvaluate(ToolingModelBuilderRegistryProcessor::afterEvaluate);
    }

    public static void process(Project project, ToolingModelBuilderRegistry registry, ServiceRegistry serviceRegistry) {
        ClassLoadingHandler.setupClassLoading(registry, project);
        final List<ToolingModelBuilder> builders = getFieldValue(registry, "builders");
        if (builders == null) {
            return;
        }

        if (builders.stream().anyMatch(builder -> builder instanceof IdeaLoomExternalProjectModelBuilder))
            return;

        final GradleProjectBuilder gradleProjectBuilder =
                        (GradleProjectBuilder) builders.stream().filter(builder -> builder instanceof GradleProjectBuilder).findFirst().orElse(new GradleProjectBuilder());
        builders.removeIf(builder -> builder instanceof IdeaModelBuilder);
        builders.add(new IdeaResolvingIdeaModelBuilder(gradleProjectBuilder, serviceRegistry));
        builders.add(new IdeaLoomExternalProjectModelBuilder());
    }

    public static void afterEvaluate(Project project) {
        final ToolingModelBuilderRegistry registry = ((ProjectInternal)project).getServices().get(ToolingModelBuilderRegistry.class);
        process(project, registry, ((ProjectInternal) project).getServices());
    }

    private static <T> T getFieldValue(final Object object, final String fieldName) {
        try {
            final Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(object);
        }
        catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
            return null;
        }
    }
}
