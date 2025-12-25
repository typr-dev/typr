# Typr - Type-Safe Database Code Generator for JVM Languages

## Project Overview

Typr is a database code generator that creates type-safe JVM code from database schemas. It follows a "SQL is King" philosophy, generating strongly-typed database access code for Scala, Java, and Kotlin that works with popular database libraries.

### Supported Languages
- **Scala** (2.13, 3.3, 3.7) - with cross-compilation support
- **Java** (21+) - using records and modern Java features
- **Kotlin** (2.0+) - with data classes and nullable types

### Supported Databases
- **PostgreSQL** - full support including domains, enums, arrays, JSON, UUID
- **MariaDB/MySQL** - including unsigned types and MySQL-specific features
- **DuckDB** - embedded analytical database
- **SQL Server** - T-SQL specific features
- **Oracle** - including OBJECT and MULTISET types

## Foundations JDBC

`foundations-jdbc` is a standalone JDBC wrapper library with perfect type modeling for all supported databases. See `site/docs-jdbc/` for full documentation.

## Build System

This project uses **Bleep** (https://bleep.build/) as the primary build tool. Kotlin modules are also buildable with Gradle.

### Common Bleep Commands
```bash
# Compile all projects
bleep compile

# Run tests
bleep test

# Format code (always run before testing/committing)
bleep fmt

# Code generation scripts
bleep generate-adventureworks    # PostgreSQL AdventureWorks schema
bleep generate-mariadb           # MariaDB test schema
bleep generate-duckdb            # DuckDB test schema
bleep generate-oracle            # Oracle test schema
bleep generate-sqlserver         # SQL Server test schema
bleep generate-openapi-test      # OpenAPI code generation
bleep generate-frontpage         # Website example code
bleep generate-sources           # Typr's internal generated code
bleep generate-all               # Run multiple generators in parallel

# Documentation
bleep generate-docs              # Generate documentation with mdoc
```

### Gradle for Kotlin
Kotlin modules have Gradle build files for IDE support and alternative building:
```bash
./gradlew :testers:pg:kotlin:build
./gradlew :foundations-jdbc-dsl-kotlin:build
```

The Gradle project includes:
- `foundations-jdbc` - Java runtime module
- `foundations-jdbc-dsl` - Java DSL module
- `foundations-jdbc-dsl-kotlin` - Kotlin DSL module
- All Kotlin testers (`testers:pg:kotlin`, `testers:mariadb:kotlin`, etc.)
- OpenAPI Kotlin testers (`testers:openapi:kotlin:jaxrs`, `spring`, `quarkus`)

## Tester Project Layout

Each tester project follows a consistent structure with generated code and manually written tests:

### Directory Structure
```
testers/{database}/{language}/
├── generated-and-checked-in/     # Generated code (committed to git)
│   └── {package}/                # Package structure matching generation options
│       ├── {schema}/             # Schema-specific code
│       │   ├── {table}/          # Table repositories, row types, ID types
│       │   └── ...
│       └── ...
├── src/                          # Manually written test code
│   └── {lang}/                   # java/, kotlin/, or scala/
│       └── {package}/            # Test files (e.g., *Test.java, *Test.scala)
├── build.gradle.kts              # Kotlin testers only - Gradle build file
└── gradle.properties             # Kotlin testers only - Gradle properties
```

### Scala Cross-Compilation
Scala testers with cross-compilation have separate generated folders per version:
```
testers/pg/scala/anorm/
├── generated-and-checked-in-2.13/    # Scala 2.13 generated code
├── generated-and-checked-in-3/       # Scala 3 generated code
└── src/scala/                        # Shared test code
```

### Examples

**Java tester** (`testers/pg/java/`):
- `generated-and-checked-in/adventureworks/` - Generated repos, rows, IDs
- `src/java/adventureworks/` - Test files like `DomainInsertImpl.java`, `SeekDbTest.java`

**Kotlin tester** (`testers/pg/kotlin/`):
- `generated-and-checked-in/` - Generated Kotlin code
- `src/kotlin/` - Test files
- `build.gradle.kts` - Gradle build configuration

**Scala Anorm tester** (`testers/pg/scala/anorm/`):
- `generated-and-checked-in-2.13/` - Scala 2.13 variant
- `generated-and-checked-in-3/` - Scala 3 variant
- `src/scala/adventureworks/` - Tests like `ArrayTest.scala`, `RecordTest.scala`

### Generation Variants

The `GeneratedAdventureWorks.scala` script generates multiple variants:
- Scala 2.13 + Anorm + PlayJson
- Scala 3 + Anorm + PlayJson
- Scala 2.13 + Doobie + Circe
- Scala 3 + Doobie + Circe
- Scala 2.13 + ZIO-JDBC + ZioJson
- Scala 3 + ZIO-JDBC + ZioJson
- Java + Typo DSL + Jackson
- Scala 3 with Java types + Typo DSL + Jackson
- Scala 3 with Scala types + Typo DSL + Jackson

## Docker-Compose Database Setup

### Starting Databases
```bash
# Start all databases
docker-compose up -d

# Check container status
docker-compose ps
```

### Database Connections
| Database   | Port | Database     | User     | Password            |
|------------|------|--------------|----------|---------------------|
| PostgreSQL | 6432 | Adventureworks | postgres | password           |
| MariaDB    | 3307 | typr         | typr     | password            |
| Oracle     | 1521 | -            | typr     | typr_password       |
| SQL Server | 1433 | -            | sa       | YourStrong@Passw0rd |

### Database Initialization

**PostgreSQL** uses mounted volumes and init scripts:
- `init/install.sh` - Main initialization script
- `db/pg/` - Schema files mounted to `/docker-entrypoint-initdb.d/pg/`
- Files executed: `install.sql`, `test-tables.sql`, `issue148.sql`, `frontpage/schema.sql`

**MariaDB** auto-executes SQL files from mounted directory:
- `db/mariadb/` - Schema files mounted to `/docker-entrypoint-initdb.d/`
- Files executed in order: `01-test-tables.sql`, `02-ordering-system.sql`

**Oracle** uses container init scripts:
- `db/oracle/` - Mounted to `/container-entrypoint-initdb.d/`
- Files: `00-init.sql`, `01-comprehensive-schema.sql`

**SQL Server** requires manual initialization or application-level setup.

### Ensuring Databases Are Up to Date

**PostgreSQL schema changes:**
```bash
# 1. Add/modify SQL files in db/pg/
# 2. Update init/install.sh if adding new files
# 3. Restart to reinitialize
docker-compose down
docker-compose up -d

# 4. Regenerate code
bleep generate-adventureworks
```

**MariaDB schema changes:**
```bash
# 1. Modify files in db/mariadb/ (numbered for execution order)
# 2. Restart to reinitialize
docker-compose down
docker-compose up -d

# 3. Regenerate code
bleep generate-mariadb
```

**Oracle schema changes:**
```bash
# 1. Modify files in db/oracle/
# 2. Remove volume to force reinitialization
docker-compose down
docker volume rm typr_oracle-data
docker-compose up -d

# 3. Wait for Oracle to be ready (can take 1-2 minutes)
docker-compose logs -f oracle

# 4. Regenerate code
bleep generate-oracle
```

**Complete reset (all databases):**
```bash
docker-compose down -v    # -v removes volumes
docker-compose up -d
# Wait for all databases to initialize
bleep generate-all
```

### Persistent Volumes
- `oracle-data` - Oracle database files (persists across restarts)
- `sqlserver-data` - SQL Server database files

To fully reset a database with persistent volumes, you must remove the volume:
```bash
docker volume rm typr_oracle-data
docker volume rm typr_sqlserver-data
```

## SQL File Locations

There are two types of SQL file locations:

### Schema Files (`db/{database}/`)
Schema definition files mounted into Docker containers for database initialization:
- `db/pg/` - PostgreSQL (install.sql, test-tables.sql, frontpage/, issue*.sql)
- `db/mariadb/` - MariaDB (01-test-tables.sql, 02-ordering-system.sql)
- `db/oracle/` - Oracle (00-init.sql, 01-comprehensive-schema.sql)
- `db/duckdb/` - DuckDB schemas

### Query Files (`{database}_sql/`)
SQL query files that Typr uses to generate typed query classes:
- `adventureworks_sql/` - PostgreSQL SQL queries for AdventureWorks
- `mariadb_sql/` - MariaDB SQL queries
- `sqlserver_sql/` - SQL Server SQL queries

These files contain parameterized SQL that generates typed repository methods:
```sql
-- mariadb_sql/customer_orders.sql
SELECT c.customer_id, c.name, COUNT(o.order_id) as order_count
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
WHERE c.customer_id = :customer_id:int!
GROUP BY c.customer_id, c.name
```

## DSL Architecture

### Core DSL (Modern)
The DSL is implemented in **Java** (`foundations-jdbc-dsl`) and wrapped for other languages:

```
foundations-jdbc-dsl        <-- Core implementation (Java)
       |
       +-- foundations-jdbc-dsl-kotlin   (Kotlin wrapper)
       +-- foundations-jdbc-dsl-scala    (Scala wrapper)
```

**Important**: When making changes to the DSL, you must update all three implementations to keep them in sync. The Java implementation is the source of truth.

### Legacy DSL Modules
The following modules are **legacy** and will not be improved:
- `typr-dsl-anorm` - Anorm integration (Scala only, PostgreSQL only)
- `typr-dsl-doobie` - Doobie integration (Scala only, PostgreSQL only)
- `typr-dsl-zio-jdbc` - ZIO-JDBC integration (Scala only, PostgreSQL only)
- `typr-runtime-anorm` - Anorm runtime (Scala only, PostgreSQL only)
- `typr-runtime-doobie` - Doobie runtime (Scala only, PostgreSQL only)
- `typr-runtime-zio-jdbc` - ZIO-JDBC runtime (Scala only, PostgreSQL only)

These legacy modules only support PostgreSQL and Scala. They exist for backward compatibility but new features target the modern Typo DSL which works across all databases and languages.

**Future plan**: Replace legacy integrations with a higher-kinded type abstraction in codegen.

### JSON Libraries
- **Jackson** - Multi-language (Java, Kotlin, Scala)
- **Circe** - Scala functional JSON
- **Play JSON** - Play Framework JSON
- **ZIO JSON** - ZIO ecosystem JSON

## Project Structure

```
typr/                              # Main code generator
├── src/scala/typr/                # Public API
│   ├── Lang.scala                 # Language abstraction
│   ├── Options.scala              # Generation options
│   ├── DbType.scala               # Database type detection
│   ├── generateFromDb.scala       # Main entry point
│   └── internal/                  # Implementation
│       ├── codegen/               # Language-specific code generation
│       │   ├── LangScala.scala    # Scala code generation
│       │   ├── LangJava.scala     # Java code generation
│       │   └── LangKotlin.scala   # Kotlin code generation
│       ├── pg/                    # PostgreSQL adapter
│       ├── mariadb/               # MariaDB adapter
│       ├── oracle/                # Oracle adapter
│       ├── duckdb/                # DuckDB adapter
│       └── sqlserver/             # SQL Server adapter
│   └── openapi/                   # OpenAPI code generation

testers/                           # Integration test projects
├── pg/                            # PostgreSQL testers
│   ├── java/                      # Java tester
│   ├── kotlin/                    # Kotlin tester (Gradle buildable)
│   └── scala/                     # Scala testers (anorm, doobie, zio-jdbc, scalatypes, javatypes)
├── mariadb/                       # MariaDB testers (java, kotlin, scala)
├── duckdb/                        # DuckDB testers (java, kotlin, scala)
├── oracle/                        # Oracle testers (java, kotlin, scala)
├── sqlserver/                     # SQL Server testers (java, kotlin, scala)
└── openapi/                       # OpenAPI framework testers
    ├── java/                      # JAX-RS, Spring, Quarkus
    ├── kotlin/                    # JAX-RS, Spring, Quarkus
    └── scala/                     # HTTP4s, Spring

foundations-jdbc/                  # Java runtime (base for all languages)
foundations-jdbc-dsl/              # Java SQL DSL (core implementation)
foundations-jdbc-dsl-kotlin/       # Kotlin SQL DSL (wraps Java DSL)
foundations-jdbc-dsl-scala/        # Scala SQL DSL (wraps Java DSL)
typr-dsl-anorm/                    # [LEGACY] Anorm-specific DSL (Scala, PostgreSQL only)
typr-dsl-doobie/                   # [LEGACY] Doobie-specific DSL (Scala, PostgreSQL only)
typr-dsl-zio-jdbc/                 # [LEGACY] ZIO-JDBC-specific DSL (Scala, PostgreSQL only)

typr-scripts/                      # Generation scripts
├── GeneratedAdventureWorks.scala  # PostgreSQL generation
├── GeneratedMariaDb.scala         # MariaDB generation
├── GeneratedDuckDb.scala          # DuckDB generation
├── GeneratedOracle.scala          # Oracle generation
├── GeneratedSqlServer.scala       # SQL Server generation
├── GenerateOpenApiTest.scala      # OpenAPI generation
├── GenerateAll.scala              # Run all generators
└── ...

db/                                # Schema files (mounted to Docker)
├── pg/                            # PostgreSQL schemas
├── mariadb/                       # MariaDB schemas
├── duckdb/                        # DuckDB schemas
└── oracle/                        # Oracle schemas

adventureworks_sql/                # PostgreSQL SQL query files
mariadb_sql/                       # MariaDB SQL query files
sqlserver_sql/                     # SQL Server SQL query files
```

## Code Generation

### Main Entry Point
```scala
typr.generateFromDb(
  dataSource = TypoDataSource.fromDataSource(ds),
  options = Options(
    pkg = "myapp",
    lang = Lang.Kotlin,           // Lang.Scala, Lang.Java, Lang.Kotlin
    dbLib = DbLib.Typo,           // DbLib.Typo (modern) or DbLib.Anorm/Doobie/ZioJdbc (legacy, Scala+PostgreSQL only)
    jsonLibs = List(JsonLib.Jackson)
  ),
  targetFolder = Path.of("generated"),
  selector = Selector.All
)
```

### Key Configuration Options
- `pkg` - Base package name
- `lang` - Target language (Lang.Scala, Lang.Java, Lang.Kotlin)
- `dbLib` - Database library: Typo (modern, all DBs) or Anorm/Doobie/ZioJdbc (legacy, PostgreSQL+Scala only)
- `jsonLibs` - JSON libraries (Jackson, Circe, PlayJson, ZioJson)
- `enablePrimaryKeyType` - Generate type-safe ID types
- `enableTestInserts` - Generate test data helpers
- `enableDsl` - Generate SQL DSL
- `generateMockRepos` - Generate mock implementations

### Type Overrides
```scala
// Custom type mappings
TypeOverride.relation {
  case (_, "firstname") => "myapp.userdefined.FirstName"
  case ("sales.creditcard", "creditcardid") => "myapp.userdefined.CustomCreditcardId"
}

// Nullability overrides
NullabilityOverride.relation {
  case (_, "column_name") => Nullability.NoNulls
}
```

## Generated Code Structure

- **Row Classes** - Data classes mirroring table structure (case class/record/data class)
- **ID Types** - Strongly-typed primary keys (e.g., `UserId(value: Long)`)
- **Repository Interfaces** - Complete CRUD operations
- **Unsaved Row Types** - For insertions with default handling
- **SQL DSL** - Type-safe query building (optional)

### SQL DSL Example
```scala
// Type-safe query building (works in all languages)
val query = select
  .from(person)
  .join(address)
  .on(person.addressid, address.addressid)
  .where(person.firstname.like("John%"))
  .orderBy(person.lastname.asc)
  .limit(10)
```

## SQL Files Integration

### SQL File Syntax
```sql
-- Parameters: :param_name:type! (required), :param_name:type? (optional)
SELECT p.productid, p.name as product_name!
FROM production.product p
WHERE p.productcategory = :category_id:myapp.production.productcategory.ProductcategoryId!
```

### Type Annotations
- `!` suffix - Column is non-null
- `?` suffix - Parameter is optional
- Custom types reference generated types

## Development Workflow

### Working on Issues
1. **Create Test Case**: Add SQL file in `db/pg/issueNNN.sql` (or appropriate database folder)
2. **Update Install Script**: Add to `init/install.sh` for PostgreSQL
3. **Restart Database**: `docker-compose down && docker-compose up -d`
4. **Generate Code**: Run appropriate generator (e.g., `bleep generate-adventureworks`)
5. **Trace Issue**: Examine generated code
6. **Commit Test Setup**: Commit before making changes
7. **Implement Fix**: Make code changes
8. **Format and Test**: `bleep fmt && bleep test`
9. **Commit Fix**: Reference issue number

### Testing
```bash
# Run all tests
bleep test

# Test specific database/language combination
bleep test testers/pg/scala/anorm
bleep test testers/pg/java
bleep test testers/mariadb/scala

# Kotlin tests via Gradle
./gradlew :testers:pg:kotlin:test
```

## Documentation

### Building Documentation
```bash
bleep generate-docs
cd site && npm install && npm run build
```

### Frontpage Examples
Website code examples come from the `frontpage` schema:
1. Edit `db/pg/frontpage/schema.sql`
2. Restart database: `docker-compose down && docker-compose up -d`
3. Generate: `bleep generate-frontpage`
4. Copy from `frontpage-generated/` to website components

## Key Files

- `bleep.yaml` - Main build configuration (all projects, scripts, templates)
- `build.gradle.kts` - Root Gradle config for Kotlin modules
- `settings.gradle.kts` - Gradle project structure
- `docker-compose.yml` - Database infrastructure (PostgreSQL, MariaDB, Oracle, SQL Server)
- `init/install.sh` - PostgreSQL initialization script

## Troubleshooting

### Common Issues
- **Database Connection**: Ensure Docker containers are running
- **Kotlin Compilation**: Use Gradle for Kotlin modules if Bleep has issues
- **Generated Code Errors**: Re-run appropriate generator after schema changes
- **Oracle slow to start**: Wait 1-2 minutes, check `docker-compose logs -f oracle`
- **Stale data**: Remove volumes with `docker-compose down -v`

### Debug Commands
```bash
# Check PostgreSQL connection
psql -h localhost -p 6432 -U postgres -d Adventureworks

# Check MariaDB connection
mysql -h 127.0.0.1 -P 3307 -u typr -ppassword typr

# Check Oracle connection
sqlplus typr/typr_password@localhost:1521/FREEPDB1

# Check SQL Server connection
sqlcmd -S localhost,1433 -U sa -P 'YourStrong@Passw0rd'

# List Bleep projects
bleep projects --json

# Check Docker container logs
docker-compose logs -f postgres
docker-compose logs -f oracle
```

## Project Memories and Notes

### Code Generation Philosophy
- Never generate code that relies on derivation - we are the deriver
- Run appropriate generator (e.g., `bleep generate-adventureworks`) before testing to see codegen effects

### Development Rules
- Always run `bleep fmt` before testing
- Always run `bleep test` before committing
- Always run bleep with `--no-color`

### Strict Orders
- **NEVER REPORT SUCCESS IF ITS NOT A SUCCESS.**
- Never ever use default parameters for anything
 CODE IN BOTH ENDS HERE
- YOU ARE NOT UNDER ANY CIRCUMSTANCE ALLOWED TO CAST TO CHEAT THE TYPE SYSTEM. IF YOU COME ACROSS A SITUATION WHERE YOU HAVE NO OTHER CHOICE, STOP AND ASK USER
- NEVER EVER PERFORM DESTRUCTIVE GIT ACTIONS IN GIT WHERE CHANGES ARE IRREVOCABLY LOST. GIT CHECKOUT FILE? STASH CHANGES INSTEAD. GIT RESET HARD? A STASH INSTEAD
