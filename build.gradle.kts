plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-SNAPSHOT"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(20))
    }
}

group = "com.betteradmin"
version = "1.0.0+26.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.papermc.io/repository/maven-snapshots/")
}

dependencies {
    paperweightDevelopmentBundle("io.papermc.paper:dev-bundle:26.2.build.60-beta@zip")
}

val releaseVersion: String? by project
val mcTarget: String? by project
val gameVersionRange: String? by project

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    processResources {
        filteringCharset = "UTF-8"
    }
    jar {
        archiveBaseName.set("betteradmin")
        archiveVersion.set("")
        archiveFileName.set(
            when {
                !releaseVersion.isNullOrBlank() && !gameVersionRange.isNullOrBlank() ->
                    "betteradmin-${releaseVersion}-${gameVersionRange}.jar"
                !releaseVersion.isNullOrBlank() && !mcTarget.isNullOrBlank() ->
                    "betteradmin-${releaseVersion}-${mcTarget}.jar"
                else ->
                    "betteradmin.jar"
            }
        )
        from(sourceSets.main.get().resources.srcDirs) {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }
}
