plugins {
    kotlin("jvm") version "2.1.0"
}

group = "testapi"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        kotlin.srcDirs("testapi")
    }
    test {
        kotlin.srcDirs("testapi")
    }
}

dependencies {
    // JAX-RS
    implementation("org.glassfish.jersey.containers:jersey-container-grizzly2-http:3.1.9")
    implementation("org.glassfish.jersey.inject:jersey-hk2:3.1.9")
    implementation("org.glassfish.jersey.media:jersey-media-json-jackson:3.1.9")
    implementation("org.glassfish.jersey.media:jersey-media-multipart:3.1.9")
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    // OpenAPI/Swagger annotations
    implementation("io.swagger.core.v3:swagger-annotations:2.2.25")

    // Validation
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")

    // MicroProfile REST Client
    implementation("org.eclipse.microprofile.rest.client:microprofile-rest-client-api:3.0.1")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}
