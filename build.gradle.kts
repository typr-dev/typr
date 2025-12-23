plugins {
    java
    kotlin("jvm") version "2.0.20" apply false
}

allprojects {
    group = "com.olvind.typo"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    tasks.withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}
