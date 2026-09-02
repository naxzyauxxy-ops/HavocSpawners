plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "dev.havoc"
version = "1.0.0"
description = "Dialog-driven virtual spawners for Paper 1.21.x"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    // Paper 1.21.6+ is required: the Dialog API (io.papermc.paper.registry.data.dialog)
    // does not exist before that version.
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")

    // Optional integration - never required at runtime.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { exclude(group = "org.bukkit") }

    // Shaded runtime libraries (relocated below).
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.2")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.slf4j:slf4j-nop:2.0.16")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        val props = mapOf(
            "version" to project.version.toString(),
            "description" to (project.description ?: "")
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("HavocSpawners-${project.version}.jar")

        relocate("com.zaxxer.hikari", "dev.havoc.spawners.libs.hikari")
        relocate("org.sqlite", "dev.havoc.spawners.libs.sqlite")
        relocate("org.mariadb.jdbc", "dev.havoc.spawners.libs.mariadb")
        relocate("org.slf4j", "dev.havoc.spawners.libs.slf4j")

        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("META-INF/maven/**")
        exclude("META-INF/LICENSE*", "META-INF/NOTICE*")
        mergeServiceFiles()
        // No minimize(): both JDBC drivers are loaded reflectively and would be stripped.
    }

    build {
        dependsOn(shadowJar)
    }

    jar {
        enabled = false
    }
}
