plugins {
    kotlin("jvm")
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
    api(project(":foundations-jdbc-dsl-kotlin"))
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.8.1.jre11")
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


tasks.test {
    // Set working directory to project root
    workingDir = rootProject.projectDir
}
