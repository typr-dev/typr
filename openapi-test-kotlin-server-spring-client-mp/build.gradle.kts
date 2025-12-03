plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
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
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web:3.4.0")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

    // OpenAPI/Swagger annotations
    implementation("io.swagger.core.v3:swagger-annotations:2.2.25")

    // Validation
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")

    // JAX-RS for MicroProfile client
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")

    // MicroProfile REST Client
    implementation("org.eclipse.microprofile.rest.client:microprofile-rest-client-api:3.0.1")

    // Jersey multipart for file uploads
    implementation("org.glassfish.jersey.media:jersey-media-multipart:3.1.9")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.4.0")
}

tasks.test {
    useJUnitPlatform()
}
