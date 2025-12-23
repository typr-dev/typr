plugins {
    kotlin("jvm") version "2.0.20"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":typo-dsl-kotlin"))
    implementation(project(":typo-runtime-java"))
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    implementation("org.postgresql:postgresql:42.7.3")

    testImplementation("junit:junit:4.13.2")
}

sourceSets {
    main {
        kotlin {
            srcDir("generated-and-checked-in")
            srcDir("src/kotlin")
        }
    }
    test {
        kotlin {
            srcDir("src/test/kotlin")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        allWarningsAsErrors.set(false)
        freeCompilerArgs.add("-Xnested-type-aliases")
    }
}

tasks.test {
    useJUnit()
}
