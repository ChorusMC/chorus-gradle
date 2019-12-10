package net.fabricmc.loom.ide.idea.classloading;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import com.google.common.collect.Maps;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.jetbrains.plugins.gradle.model.internal.DummyModel;

public final class ClassLoadingHandler {

    private ClassLoadingHandler() {
        throw new IllegalStateException("Tried to initialize: ClassLoadingHandler but this is a Utility class.");
    }

    private static ClassLoader ideaCustomClassLoader = null;

    public static void setupClassLoading(ToolingModelBuilderRegistry toolingModelBuilderRegistry, Project project)
    {
        if (ideaCustomClassLoader != null)
            return;

        try {
            ideaCustomClassLoader = toolingModelBuilderRegistry.getBuilder(DummyModel.class.getName()).buildAll(DummyModel.class.getName(), project).getClass().getClassLoader();
        } catch (Exception ex)
        {
            ideaCustomClassLoader = null;
        }
    }

    private static final Map<Class<?>, Class<?>> loadedTargetClasses = Maps.newConcurrentMap();
    public static Class<?> loadClassFrom(Class<?> classToLoad)
    {
        if (ideaCustomClassLoader == null)
            throw new IllegalStateException("Can not create idea based objects without classloader!");

        return loadedTargetClasses.computeIfAbsent(classToLoad, (clz) -> {
            try {
                return ideaCustomClassLoader.loadClass(clz.getName());
            }
            catch (ClassNotFoundException e) {
                throw new IllegalStateException("Could not create external object instance. Class not found. IDEA has changed!", e);
            }
        });
    }
    
    public static Object createObject(Class<?> normalClassLoadedObjectToLoad)
    {
        try {
            return loadClassFrom(normalClassLoadedObjectToLoad).newInstance();
        }
        catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not create external object. No constructor available.", e);
        }
        catch (InstantiationException e) {
            throw new IllegalStateException("Could not create external object. An exception occurred during instantiation.", e);
        }
    }
    
    public static Object createObjectWithParameter(Class<?> normalClassLoadedObjectToLoad, Class<?> normalClassLoadedParameterToLoad, Object parameter)
    {
        if (ideaCustomClassLoader == null)
            throw new IllegalStateException("Can not create idea based objects without classloader!");

        try {
            final Class<?> customLoadedParameterClass = loadClassFrom(normalClassLoadedParameterToLoad);
            return loadClassFrom(normalClassLoadedObjectToLoad).getConstructor(customLoadedParameterClass).newInstance(parameter);
        }
        catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not create external object. No constructor available.", e);
        }
        catch (InstantiationException e) {
            throw new IllegalStateException("Could not create external object. An exception occurred during instantiation.", e);
        }
        catch (NoSuchMethodException e) {
            throw new IllegalStateException("Could not create external object. No constructor with an Externalobject as parameter is accessible.", e);
        }
        catch (InvocationTargetException e) {
            throw new IllegalStateException("Could not create external object. Could not successfully invoke the copy constructor.", e );
        }
    }
}
