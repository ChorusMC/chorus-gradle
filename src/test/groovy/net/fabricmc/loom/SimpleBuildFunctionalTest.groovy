package net.fabricmc.loom

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import static net.fabricmc.loom.BuildUtils.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

/**
 * Created by Mitchell Skaggs on 6/10/2019.
 */
class SimpleBuildFunctionalTest extends Specification {
	@Rule
	TemporaryFolder testProjectDir = new TemporaryFolder()
	File settingsFile
	File buildFile
	File propsFile
	File modJsonFile
	File modExampleFile

	def setup() {
		settingsFile = testProjectDir.newFile('settings.gradle')
		buildFile = testProjectDir.newFile('build.gradle')
		propsFile = testProjectDir.newFile('gradle.properties')

		testProjectDir.newFolder("src", "main", "resources")
		modJsonFile = testProjectDir.newFile('src/main/resources/fabric.mod.json')

		testProjectDir.newFolder("src", "main", "java", "net", "fabricmc", "example")
		modExampleFile = testProjectDir.newFile("src/main/java/net/fabricmc/example/ExampleMod.java")
	}

	@Unroll
	def "simple build succeeds using Minecraft #mcVersion"() {
		given:
		settingsFile << genSettingsFile("simple-build-functional-test")
		propsFile << genPropsFile(mcVersion, yarnVersion, loaderVersion, fabricVersion)
		buildFile << genBuildFile()
		modJsonFile << genModJsonFile()
		modExampleFile << genModJavaFile()

		when:
		def result = GradleRunner.create()
				.withProjectDir(testProjectDir.root)
				.withArguments('build',"--stacktrace")
				.withPluginClasspath()
				.withGradleVersion("4.9")
				.withDebug(true)
				.build()

		then:
		result.task(":build").outcome == SUCCESS

		where:
		mcVersion | yarnVersion       | loaderVersion     | fabricVersion
		'1.14.4'    | '1.14.4+build.local:v2'   | '0.6.2+build.166' | '0.3.2+build.230-1.15'
//		'1.14.1'  | '1.14.1+build.10' | '0.4.8+build.155' | '0.3.0+build.184'
//		'1.14.2'  | '1.14.2+build.7'  | '0.4.8+build.155' | '0.3.0+build.184'
	}
}
