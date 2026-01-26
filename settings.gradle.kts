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

// OpenAPI Kotlin testers
include("testers:openapi:kotlin:jaxrs")
project(":testers:openapi:kotlin:jaxrs").projectDir = file("testers/openapi/kotlin/jaxrs")
include("testers:openapi:kotlin:spring")
project(":testers:openapi:kotlin:spring").projectDir = file("testers/openapi/kotlin/spring")
include("testers:openapi:kotlin:quarkus")
project(":testers:openapi:kotlin:quarkus").projectDir = file("testers/openapi/kotlin/quarkus")
