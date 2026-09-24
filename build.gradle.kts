plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "dev.havoc"
version = "2.0.1"
description = "Virtual spawners for Paper 1.21.x - every screen is a chest menu"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
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

/**
 * Files that older versions shipped and this one does not.
 *
 * Updating by extracting a release zip over an existing checkout cannot delete anything, so a class
 * that was removed stays behind and breaks the build against the code that replaced it. Rather than
 * failing with "cannot find symbol" in a file nobody remembers, this says exactly what to delete.
 */
val removedSources = listOf(
    "src/main/java/dev/havoc/spawners/ui/UiMode.java",
    "src/main/java/dev/havoc/spawners/ui/UiPreferences.java"
)

val checkStaleSources by tasks.registering {
    // Captured at configuration time so the check stays configuration-cache safe.
    val root = layout.projectDirectory
    val paths = removedSources
    doLast {
        val stale = paths.filter { root.file(it).asFile.exists() }
        if (stale.isNotEmpty()) {
            throw GradleException(
                "Leftover files from an older version are still present and will not compile.\n" +
                    "Delete them and build again:\n" +
                    stale.joinToString("\n") { "  rm $it" }
            )
        }
    }
}

tasks {
    compileJava {
        dependsOn(checkStaleSources)
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
        relocate("org.mariadb.jdbc", "dev.havoc.spawners.libs.mariadb")
        relocate("org.slf4j", "dev.havoc.spawners.libs.slf4j")
        // org.sqlite is deliberately NOT relocated: sqlite-jdbc loads a native library whose JNI
        // entry points are hard-coded to "Java_org_sqlite_core_NativeDB_*". Renaming the package
        // makes those symbols unresolvable and every connection dies with UnsatisfiedLinkError.
        // Paper gives each plugin its own class loader, so leaving it in place is safe.

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
