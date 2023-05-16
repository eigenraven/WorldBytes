plugins {
	id("fabric-loom") version "1.2-SNAPSHOT"
	id("io.github.juuxel.loom-quiltflower") version "1.8.0"
	id("com.diffplug.spotless") version "6.18.0"
	id("me.champeau.jmh") version "0.7.1"
	`maven-publish`
}

// Access gradle properties
val minecraft_version: String by project
val yarn_mappings: String by project
val loader_version: String by project
val mod_version: String by project
val maven_group: String by project
val archives_base_name: String by project
val fabric_version: String by project

version = mod_version
group = maven_group

spotless {
	java {
		target("src/*/java/**/*.java")

		importOrder()
		removeUnusedImports()
		palantirJavaFormat()
		formatAnnotations()
	}
}

repositories {
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${minecraft_version}")
	mappings(loom.officialMojangMappings())
	modImplementation("net.fabricmc:fabric-loader:${loader_version}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	modImplementation("net.fabricmc.fabric-api:fabric-api:${fabric_version}")

	// Uncomment the following line to enable the deprecated Fabric API modules. 
	// These are included in the Fabric API production distribution and allow you to update your mod to the latest modules at a later more convenient time.

	// modImplementation "net.fabricmc.fabric-api:fabric-api-deprecated:${project.fabric_version}"

	jmh("org.openjdk.jmh:jmh-core:1.36")
	jmh("org.openjdk.jmh:jmh-generator-annprocess:1.36")
	jmh("org.ow2.asm:asm:9.4")
}

base {
	archivesName.set(archives_base_name)
}

tasks.processResources {
	inputs.property("version", project.version)

	filesMatching("fabric.mod.json") {
		expand(mapOf("version" to project.version))
	}
}

tasks.withType<JavaCompile>().configureEach {
	// Minecraft 1.18 (1.18-pre2) upwards uses Java 17.
	options.release.set(17)
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	toolchain {
		languageVersion.set(JavaLanguageVersion.of(17))
		vendor.set(JvmVendorSpec.AZUL)
	}
}

tasks.jar {
	from("LICENSE") {
		rename { "${it}_${base.archivesName.get()}"}
	}
}

tasks.jmhJar {
	exclude("oshi.properties", "oshi.architecture.properties")
}

jmh {
	benchmarkMode.set(listOf("avgt"))
	warmupIterations.set(2)
	warmup.set("2s")
	iterations.set(5)
	timeOnIteration.set("2s")
	timeUnit.set("ns")
	fork.set(2)
	zip64.set(true)
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
	}
}
