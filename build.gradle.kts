plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.5.0"
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
}

dependencies {
    paperweightDevelopmentBundle("io.papermc.paper:dev-bundle:26.2.build.60-beta")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    processResources {
        filteringCharset = "UTF-8"
    }
    jar {
        archiveBaseName.set("betteradmin")
        from(sourceSets.main.get().resources.srcDirs) {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }
}
