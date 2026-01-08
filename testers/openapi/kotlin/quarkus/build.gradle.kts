plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        kotlin {
            srcDirs("testapi/api", "testapi/model")
        }
    }
    test {
        kotlin {
            srcDirs("src/test/kotlin")
        }
    }
}

dependencies {
    implementation(project(":foundations-jdbc"))
    implementation("org.glassfish.jersey.containers:jersey-container-grizzly2-http:3.1.9")
    implementation("org.glassfish.jersey.inject:jersey-hk2:3.1.9")
    implementation("org.glassfish.jersey.media:jersey-media-json-jackson:3.1.9")
    implementation("org.glassfish.jersey.media:jersey-media-multipart:3.1.9")
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.25")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("io.smallrye.reactive:mutiny:2.6.2")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
