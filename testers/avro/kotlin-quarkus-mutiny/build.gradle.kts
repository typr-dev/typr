plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":foundations-jdbc"))
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    // Quarkus/Smallrye Reactive Messaging
    implementation("io.smallrye.reactive:smallrye-reactive-messaging-api:4.22.0")
    implementation("io.smallrye.reactive:smallrye-reactive-messaging-kafka:4.22.0")

    // Mutiny
    implementation("io.smallrye.reactive:mutiny:2.6.0")

    // Jakarta CDI annotations
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1")
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")

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

tasks.test {
    useJUnit()
}
