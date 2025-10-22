plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

subprojects {
    val libs = rootProject.libs

    apply {
        plugin("java-library")
    }

    group = project.property("group") as String
    version = project.property("version") as String
    description = project.property("description") as String

    dependencies {
        compileOnly(files("C:/Development/Minecraft/API/build/libs/API-2.0.jar"))

        compileOnly(libs.bundles.cloudnet)
        annotationProcessor(libs.cloudnet.platform.inject.processor)
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(24)
        }
    }

    tasks.compileJava {
        options.encoding = Charsets.UTF_8.name()
    }
}

dependencies {
    subprojects.forEach() {
        api(project(it.path))
    }
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    shadowJar {
        dependsOn(subprojects.map {
            it.tasks.named("assemble")
        })

        archiveFileName.set("${project.property("name")}-${project.property("version")}.jar")
        minimize()
    }
}