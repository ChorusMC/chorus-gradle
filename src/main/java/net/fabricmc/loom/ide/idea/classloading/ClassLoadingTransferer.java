package net.fabricmc.loom.ide.idea.classloading;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.gradle.internal.impldep.org.apache.commons.lang.SerializationUtils;
import org.jetbrains.plugins.gradle.model.DefaultExternalProject;
import org.jetbrains.plugins.gradle.tooling.serialization.ExternalProjectSerializationService;

public final class ClassLoadingTransferer {

    private ClassLoadingTransferer() {
        throw new IllegalStateException("Tried to initialize: ClassLoadingTransferer but this is a Utility class.");
    }

    public static Object transferToOtherClassLoader(DefaultExternalProject project) throws IOException, InvocationTargetException, IllegalAccessException {
        final ExternalProjectSerializationService gradleSidedSerializer = new ExternalProjectSerializationService();
        final Class<?> ideaSidedSerializerClass = ClassLoadingHandler.loadClassFrom(ExternalProjectSerializationService.class);
        final Object ideaSidedSerializer = ClassLoadingHandler.createObject(ExternalProjectSerializationService.class);

        final byte[] written = gradleSidedSerializer.write(project, null);
        final Method readMethod = Arrays.stream(ideaSidedSerializerClass.getMethods()).filter(method -> method.getName().equals("read")).findFirst().orElseThrow(() -> new IllegalStateException("Failed to find reader on idea sided deserializer."));
        return readMethod.invoke(ideaSidedSerializer, written, null);
    }
}
