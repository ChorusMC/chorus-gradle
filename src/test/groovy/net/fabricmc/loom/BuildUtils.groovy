package net.fabricmc.loom

/**
 * Created by Mitchell Skaggs on 6/12/2019.
 */
static String genBuildFile() {
	"""
plugins {
	id 'fabric-loom'
	id 'maven-publish'
}
sourceCompatibility = JavaVersion.VERSION_1_8
targetCompatibility = JavaVersion.VERSION_1_8

archivesBaseName = project.archives_base_name
version = project.mod_version
group = project.maven_group


minecraft {
}

dependencies {
	//to change the versions see the gradle.properties file
	minecraft "com.mojang:minecraft:\${project.minecraft_version}"
	mappings "net.fabricmc:yarn:\${project.yarn_mappings}"
	compile "net.fabricmc:fabric-loader:\${project.loader_version}"

	// Fabric API. This is technically optional, but you probably want it anyway.
	compile "net.fabricmc.fabric-api:fabric-api:\${project.fabric_version}"

	// PSA: Some older mods, compiled on Loom 0.2.1, might have outdated Maven POMs.
	// You may need to force-disable transitiveness on them.
}

processResources {
	inputs.property "version", project.version

	from(sourceSets.main.resources.srcDirs) {
		include "fabric.mod.json"
		expand "version": project.version
	}

	from(sourceSets.main.resources.srcDirs) {
		exclude "fabric.mod.json"
	}
}

// ensure that the encoding is set to UTF-8, no matter what the system default is
// this fixes some edge cases with special characters not displaying correctly
// see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
tasks.withType(JavaCompile) {
	options.encoding = "UTF-8"
}

// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
// if it is present.
// If you remove this task, sources will not be generated.
task sourcesJar(type: Jar, dependsOn: classes) {
	classifier = "sources"
	from sourceSets.main.allSource
}

jar {
	from "LICENSE"
}

// configure the maven publication
publishing {
	publications {
		mavenJava(MavenPublication) {
			// add all the jars that should be included when publishing to maven
			artifact(jar) {
				builtBy remapJar
			}
			artifact(sourcesJar) {
				builtBy remapSourcesJar
			}
		}
	}

	// select the repositories you want to publish to
	repositories {
		// uncomment to publish to the local maven
		// mavenLocal()
	}
}
"""
}

static String genPropsFile(String mcVersion, String yarnVersion, String loaderVersion, String fabricVersion, boolean caching = true, boolean parallel = true) {
	"""
org.gradle.caching=$caching
org.gradle.parallel=$parallel

# Fabric Properties
# check these on https://fabricmc.net/use
minecraft_version=$mcVersion
yarn_mappings=$yarnVersion
loader_version=$loaderVersion

# Mod Properties
mod_version = 1.0.0
maven_group = net.fabricmc
archives_base_name = fabric-example-mod

# Dependencies
# currently not on the main fabric site, check on the maven: https://maven.fabricmc.net/net/fabricmc/fabric
fabric_version=$fabricVersion
"""
}

static String genSettingsFile(String name) {
	"""
rootProject.name = '$name'
"""
}

static String genModJsonFile() {
	"""
{
  "schemaVersion": 1,
  "id": "modid",
  "version": "\${version}",

  "name": "Example Mod",
  "description": "This is an example description! Tell everyone what your mod is about!",
  "authors": [
    "Me!"
  ],
  "contact": {
    "homepage": "https://fabricmc.net/",
    "sources": "https://github.com/FabricMC/fabric-example-mod"
  },

  "license": "CC0-1.0",

  "environment": "*",
  "entrypoints": {
    "main": [
      "net.fabricmc.example.ExampleMod"
    ]
  },
  "mixins": [
    "modid.mixins.json"
  ],

  "depends": {
    "fabricloader": ">=0.4.0",
    "fabric": "*"
  },
  "suggests": {
    "flamingo": "*"
  }
}
"""
}

static String genModJavaFile() {
	"""
package net.fabricmc.example;

import net.fabricmc.api.ModInitializer;

public class ExampleMod implements ModInitializer {
	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		System.out.println("Hello Fabric world!");
	}
}
"""
}

static String genModMixinsJsonFile() {
	"""
{
  "required": true,
  "package": "net.fabricmc.example.mixin",
  "compatibilityLevel": "JAVA_8",
  "mixins": [
  ],
  "client": [
    "ExampleMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
"""
}

static String genModMixinsJavaFile() {
	"""
package net.fabricmc.example.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class ExampleMixin {
	@Inject(at = @At("HEAD"), method = "init()V")
	private void init(CallbackInfo info) {
		System.out.println("This line is printed by an example mod mixin!");
	}
}
"""
}
