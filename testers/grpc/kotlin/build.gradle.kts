plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":foundations-jdbc"))
    implementation("com.google.protobuf:protobuf-java:4.29.3")
    implementation("io.grpc:grpc-netty-shaded:1.69.0")
    implementation("io.grpc:grpc-protobuf:1.69.0")
    implementation("io.grpc:grpc-stub:1.69.0")

    testImplementation("io.grpc:grpc-testing:1.69.0")
    testImplementation("io.grpc:grpc-inprocess:1.69.0")
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
