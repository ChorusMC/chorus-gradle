package net.fabricmc.loom.util;

import java.io.File;

public final class FileNameUtils {

    private FileNameUtils() {
        throw new IllegalStateException("Tried to initialize: FileUtils but this is a Utility class.");
    }

    public static String appendToNameBeForeExtension(final File file, final String toAppend) {
        final String inputName = file.getName();

        return appendToNameBeForeExtension(inputName, toAppend);
    }

    public static String appendToNameBeForeExtension(final String inputName, final String toAppend) {
        final String withoutExtension = inputName.substring(0, inputName.length() - 4);
        final String extension = inputName.substring(inputName.length() - 4);

        return String.format("%s%s%s", withoutExtension, toAppend, extension);
    }
}
