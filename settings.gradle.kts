rootProject.name = "typr"

include("foundations-jdbc")
include("foundations-jdbc-dsl")
include("foundations-jdbc-dsl-kotlin")

// PostgreSQL Kotlin testers
include("testers:pg:kotlin")
project(":testers:pg:kotlin").projectDir = file("testers/pg/kotlin")

// DuckDB Kotlin testers
include("testers:duckdb:kotlin")
project(":testers:duckdb:kotlin").projectDir = file("testers/duckdb/kotlin")

// MariaDB Kotlin testers
include("testers:mariadb:kotlin")
project(":testers:mariadb:kotlin").projectDir = file("testers/mariadb/kotlin")

// Oracle Kotlin testers
include("testers:oracle:kotlin")
project(":testers:oracle:kotlin").projectDir = file("testers/oracle/kotlin")

// SQL Server Kotlin testers
include("testers:sqlserver:kotlin")
project(":testers:sqlserver:kotlin").projectDir = file("testers/sqlserver/kotlin")

// DB2 Kotlin testers
include("testers:db2:kotlin")
project(":testers:db2:kotlin").projectDir = file("testers/db2/kotlin")

// Avro Kotlin testers
include("testers:avro:kotlin")
project(":testers:avro:kotlin").projectDir = file("testers/avro/kotlin")
include("testers:avro:kotlin-json")
project(":testers:avro:kotlin-json").projectDir = file("testers/avro/kotlin-json")
include("testers:avro:kotlin-quarkus-mutiny")
project(":testers:avro:kotlin-quarkus-mutiny").projectDir = file("testers/avro/kotlin-quarkus-mutiny")

// gRPC Kotlin testers
include("testers:grpc:kotlin")
project(":testers:grpc:kotlin").projectDir = file("testers/grpc/kotlin")
include("testers:grpc:kotlin-quarkus")
project(":testers:grpc:kotlin-quarkus").projectDir = file("testers/grpc/kotlin-quarkus")

// OpenAPI Kotlin testers
include("testers:openapi:kotlin:jaxrs")
project(":testers:openapi:kotlin:jaxrs").projectDir = file("testers/openapi/kotlin/jaxrs")
include("testers:openapi:kotlin:spring")
project(":testers:openapi:kotlin:spring").projectDir = file("testers/openapi/kotlin/spring")
include("testers:openapi:kotlin:quarkus")
project(":testers:openapi:kotlin:quarkus").projectDir = file("testers/openapi/kotlin/quarkus")
