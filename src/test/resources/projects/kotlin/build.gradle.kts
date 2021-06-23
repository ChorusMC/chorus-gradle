import java.util.Properties

plugins {
	kotlin("jvm") version "1.4.31"
	id("fabric-loom")
}

java {
	sourceCompatibility = JavaVersion.VERSION_1_8
	targetCompatibility = JavaVersion.VERSION_1_8
}

version = "0.0.1"

dependencies {
	minecraft(group = "com.mojang", name = "minecraft", version = "1.16.5")
	mappings(group = "net.fabricmc", name = "yarn", version = "1.16.5+build.5", classifier = "v2")
	modImplementation("net.fabricmc:fabric-loader:0.11.2")
	modImplementation(group = "net.fabricmc", name = "fabric-language-kotlin", version = "1.5.0+kotlin.1.4.31")
}