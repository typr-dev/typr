plugins {
    kotlin("jvm") version "2.0.20"
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":typo-dsl-kotlin"))
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("org.duckdb:duckdb_jdbc:1.1.3")
    testImplementation("junit:junit:4.13.2")
}

sourceSets {
    main {
        kotlin {
            srcDir("generated-and-checked-in")
        }
    }
    test {
        kotlin {
            srcDir("src/kotlin")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        allWarningsAsErrors.set(false)
    }
}

tasks.test {
    // Set working directory to project root so db/duckdb paths resolve correctly
    workingDir = rootProject.projectDir
}
