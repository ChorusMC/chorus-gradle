package net.fabricmc.loom.bootstrap;

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.plugins.PluginAware;
import org.gradle.util.GradleVersion;

/**
 * This bootstrap is compiled against a minimal gradle API and java 8, this allows us to show a nice error to users who run on unsupported configurations.
 */
@SuppressWarnings("unused")
public class LoomGradlePluginBootstrap implements Plugin<PluginAware> {
	private static final int MIN_SUPPORTED_MAJOR_GRADLE_VERSION = 7;
	private static final int MIN_SUPPORTED_MAJOR_JAVA_VERSION = 16;

	private static final String pluginClassName = "net.fabricmc.loom.LoomGradlePlugin";

	@Override
	public void apply(PluginAware project) {
		List<String> errors = new ArrayList<>();

		if (!isValidGradleRuntime()) {
			errors.add(String.format("Outdated Gradle version (%s). Gradle %d or higher is required.", getMajorGradleVersion(), MIN_SUPPORTED_MAJOR_GRADLE_VERSION));
		}

		if (!isValidJavaRuntime()) {
			errors.add(String.format("Outdated Java version (%s). Java %d or higher is required.", JavaVersion.current().getMajorVersion(), MIN_SUPPORTED_MAJOR_JAVA_VERSION));
		}

		if (!errors.isEmpty()) {
			throw new UnsupportedOperationException(String.join("\n\n", errors));
		}

		getActivePlugin().apply(project);
	}

	private static boolean isValidJavaRuntime() {
		return JavaVersion.current().isCompatibleWith(JavaVersion.toVersion(MIN_SUPPORTED_MAJOR_JAVA_VERSION));
	}

	private static boolean isValidGradleRuntime() {
		return getMajorGradleVersion() >= MIN_SUPPORTED_MAJOR_GRADLE_VERSION;
	}

	private static int getMajorGradleVersion() {
		String version = GradleVersion.current().getVersion();
		return Integer.parseInt(version.substring(0, version.indexOf(".")));
	}

	BootstrappedPlugin getActivePlugin() {
		try {
			return (BootstrappedPlugin) Class.forName(pluginClassName).getConstructor().newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Failed to bootstrap loom", e);
		}
	}
}