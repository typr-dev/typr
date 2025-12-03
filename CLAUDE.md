# Typo - Type-Safe PostgreSQL Code Generator for Scala

## Project Overview

Typo is a PostgreSQL-specific code generator that creates type-safe Scala code from database schemas. It follows a "SQL is King" philosophy, generating strongly-typed database access code that works with popular Scala database libraries (Anorm, Doobie, ZIO-JDBC).

## Architecture

### Core Components
- **`typo/`** - Main code generator with database introspection and code generation
- **`typo-dsl-*`** - DSL implementations for different database libraries
- **`typo-runtime-*`** - Runtime support libraries for generated code
- **`typo-tester-*`** - Test projects demonstrating generated code usage

### Key Philosophy
- SQL files are first-class citizens (stored in dedicated `.sql` files)
- Type safety propagates through foreign key relationships
- Generates idiomatic Scala code for each supported database library
- PostgreSQL-specific optimizations and type support

## Build System - Bleep

This project uses **Bleep** (https://github.com/oyvindberg/bleep) as the build tool:

### Common Commands
```bash
# Compile all projects
bleep compile

# Run tests
bleep test

# Run specific test project
bleep test typo-tester-anorm

# Run code generation scripts
bleep run GeneratedAdventureWorks
bleep run GeneratedAdventureWorks -- --watch  # Watch mode for SQL files

# Cross-compile for different Scala versions
bleep compile @jvm212  # Scala 2.12
bleep compile @jvm213  # Scala 2.13
bleep compile @jvm3    # Scala 3.3
```

### Key Scripts (defined in bleep.yaml)
- **`GeneratedAdventureWorks`** - Main code generation from AdventureWorks DB
- **`GeneratedSources`** - Generate Typo's internal code from database
- **`GenHardcodedFiles`** - Generate test fixtures without database
- **`GenerateDocumentation`** - Generate documentation using mdoc

## Development Environment

### Database Setup
```bash
# Start PostgreSQL and MariaDB with test data
docker-compose up -d

# PostgreSQL runs on localhost:6432
# Database: Adventureworks
# User: postgres
# Password: password

# MariaDB runs on localhost:3307
# Database: typo
# User: typo
# Password: password
```

### Generated Code Structure
- **Row Classes** - Case classes mirroring table structure
- **ID Types** - Strongly-typed primary keys (e.g., `UserId(value: Long)`)
- **Repository Interfaces** - Complete CRUD operations
- **Unsaved Row Types** - For insertions with default handling
- **SQL DSL** - Type-safe query building (optional)

## Code Generation Process

### Main Generation Script (scripts/GeneratedAdventureWorks.scala)
```scala
// Basic setup
val options = typo.Options(
  pkg = "adventureworks",
  dbLib = typo.DbLib.Anorm,
  jsonLib = typo.JsonLib.PlayJson,
  enablePrimaryKeyType = true,
  enableTestInserts = true
)

// Generate code
typo.generate(options, db, sqlFiles, folder)
```

### Key Configuration Options
- `pkg` - Base package name
- `dbLib` - Database library (Anorm, Doobie, ZioJdbc)
- `jsonLib` - JSON library (PlayJson, Circe, ZioJson)
- `enablePrimaryKeyType` - Generate type-safe ID types
- `enableTestInserts` - Generate test data helpers
- `enableDsl` - Generate SQL DSL
- `generateMockRepos` - Generate mock implementations

### Type Overrides
```scala
// Custom type mappings
TypeOverride.relation {
  case (_, "firstname") => "adventureworks.userdefined.FirstName"
  case ("sales.creditcard", "creditcardid") => "adventureworks.userdefined.CustomCreditcardId"
}

// Nullability overrides
NullabilityOverride.relation {
  case (_, "column_name") => Nullability.NoNulls
}
```

## Testing

### Test Structure
- **Unit Tests** - Individual repository methods
- **Integration Tests** - Real database operations
- **DSL Tests** - Type-safe query DSL
- **Snapshot Tests** - Generated SQL verification

### TestInsert Pattern
```scala
// Generated test data factory
val testInsert = new TestInsert(new Random(0), DomainInsert)
val productCategory = testInsert.productionProductcategory()
val product = testInsert.productionProduct(productcategory = productCategory.productcategoryid)
```

### Running Tests
```bash
# All tests (transactional, rolled back)
bleep test

# Specific test class
bleep test --only DepartmentTest

# Watch mode
bleep test --watch
```

## SQL Files Integration

### SQL File Syntax
```sql
-- Parameters: :param_name:type! (required), :param_name:type? (optional)
SELECT p.productid, p.name as product_name!
FROM production.product p
WHERE p.productcategory = :category_id:adventureworks.production.productcategory.ProductcategoryId!
```

### Type Annotations
- `!` suffix - Column is non-null
- `?` suffix - Parameter is optional
- Custom types reference generated types

## Key Features

### Type Safety
- **Type Flow** - Foreign key relationships propagate specific types
- **ID Types** - Prevents mixing different entity IDs
- **Nullability Inference** - Comprehensive null-safety analysis
- **Custom Types** - Support for PostgreSQL domains, enums, arrays

### SQL DSL Example
```scala
// Type-safe query building
val query = select
  .from(person)
  .join(address)
  .on(person.addressid, address.addressid)
  .where(person.firstname.like("John%"))
  .orderBy(person.lastname.asc)
  .limit(10)
```

### Generated Repository Methods
```scala
// CRUD operations
def insert(unsaved: PersonRowUnsaved): PersonRow
def update(row: PersonRow): Boolean
def delete(id: PersonId): Boolean
def selectAll: List[PersonRow]
def selectById(id: PersonId): Option[PersonRow]
```

## Development Workflow

### Making Changes
1. **Code Generation Changes** - Modify scripts in `scripts/`
2. **Core Changes** - Edit `typo/` source code
3. **Test Changes** - Update test files in `typo-tester-*`
4. **Documentation** - Update files in `site-in/`

### Regenerating Code
```bash
# Watch for SQL file changes and regenerate
bleep run GeneratedAdventureWorks -- --watch

# Regenerate test fixtures
bleep run GenHardcodedFiles

# Regenerate documentation
bleep run GenerateDocumentation
```

### Testing Changes
```bash
# Test specific database library
bleep test typo-tester-anorm
bleep test typo-tester-doobie
bleep test typo-tester-zio-jdbc

# Test core functionality
bleep test typo
```

## Documentation

### Building Documentation Site
```bash
cd site
npm install
npm run build
npm run serve
```

### Documentation Structure
- **`site-in/`** - Source markdown files
- **`site/`** - Generated Docusaurus site
- **Type Safety** - Documentation on type system features
- **Customization** - Guides for customizing generation
- **Patterns** - Common usage patterns

## Common Tasks

### Adding New Database Library Support
1. Create `typo-dsl-newlib` module
2. Create `typo-runtime-newlib` module
3. Add to `DbLib` enum in core
4. Implement code generation templates
5. Add test project `typo-tester-newlib`

### Adding New PostgreSQL Type Support
1. Add to `PgType` enum
2. Update type mapping in `ScalaType`
3. Add runtime support if needed
4. Update tests and documentation

### Debugging Generated Code
- Generated code is in `generated-and-checked-in/` directories
- Use `--watch` mode to see changes in real-time
- Check `bleep.yaml` for script configurations
- Use database introspection tools to verify schema

### Working with Frontpage Code Examples

The website's code examples are generated from real Typo code using the `frontpage` schema in the test database. This ensures that all examples shown on the website are accurate and compile correctly.

**Schema Location**: `db/pg/frontpage/` contains:
- `schema.sql` - Database schema for frontpage examples
- `*.sql` - SQL files that generate Typo repositories and types

**Generation Process**:
1. **Modify Schema**: Edit files in `db/pg/frontpage/` to change database structure or add new examples
2. **Restart Database**: Run `docker-compose down && docker-compose up -d` to apply schema changes  
3. **Generate Code**: Run `bleep run GeneratedFrontpage` to generate fresh Scala code
4. **Update Website**: Manually copy the generated code from `frontpage-generated/` into website components in `site/src/components/FeatureShowcase/index.js`

**Important Notes**:
- The `frontpage-generated/` directory is gitignored - we don't check in generated code
- The frontpage schema uses real PostgreSQL features (domains, enums, foreign keys) to showcase Typo's capabilities
- Always verify examples compile by running `bleep generate-docs` after updating website code
- This is the only part of the documentation workflow that requires manual copying of generated code
- Website logo and favicon can be regenerated using `site/scripts/generate-logo.js` and `npm run generate-favicon`

**Example Workflow**:
```bash
# 1. Edit schema
vi db/pg/frontpage/schema.sql

# 2. Restart database
docker-compose down && docker-compose up -d

# 3. Generate fresh code
bleep run GeneratedFrontpage

# 4. Copy relevant parts to website (manual step)
# Look in frontpage-generated/ for the code you need

# 5. Verify documentation builds
bleep generate-docs
```

## Troubleshooting

### Common Issues
- **Database Connection** - Ensure PostgreSQL is running on port 6432
- **Generated Code Compilation** - Check type overrides and nullability settings
- **SQL File Syntax** - Verify parameter syntax and type annotations
- **Bleep Issues** - Check `bleep.yaml` syntax and script configurations

### Debug Commands
```bash
# Check database connection
psql -h localhost -p 6432 -U postgres -d Adventureworks

# Verify generated code
find . -name "generated-and-checked-in" -type d

# Check Bleep configuration
bleep projects --json
```

This project represents a sophisticated approach to type-safe database access in Scala, with comprehensive tooling and extensive test coverage.

## Development Workflow

When working on Typo issues, follow this workflow:

1. **Create Test Case**: Add a minimal reproduction SQL file in `db/pg/issueNNN.sql`
2. **Update Install Script**: Add the SQL file to `init/install.sh`  
3. **Restart Database**: Run `docker-compose down && docker-compose up -d`
4. **Generate Code**: Run `bleep generate-adventureworks` to generate test code
5. **Trace Issue**: Examine generated code to understand the problem
6. **Commit Test Setup**: Always commit the test setup before making changes
7. **Implement Fix**: Make necessary code changes
8. **Format Code**: Always run `bleep fmt` before testing
9. **Run Tests**: Always run `bleep test` before committing
10. **Commit Fix**: Commit with descriptive message referencing the issue

### Debugging and Development Approach

**General Problem-Solving Process**:
1. **Create Minimal Reproduction**: Add minimal test case to `db/pg/issueNNN.sql`
2. **Set up Test Environment**: Update `init/install.sh`, restart database, regenerate code
3. **Understand the Issue**: Trace through generated code to identify root cause
4. **Test-Driven Development**:
    - Add debug logging to capture relevant data
    - Create comprehensive test suite covering edge cases
    - Use static test data based on debug output
    - Implement fix and verify all tests pass
5. **Code Quality**: Always run `bleep fmt` and `bleep test` before committing
6. **Commit**: Clear commit message referencing the issue number

**Debug Techniques**:
- **Temporary Logging**: Add `println` statements to capture runtime data
- **Static Analysis**: Read generated code to understand behavior
- **Test Coverage**: Create tests for various scenarios (different names, ordering, edge cases)
- **Incremental Testing**: Verify each component works in isolation

**Code Generation Issues**:
- **Schema Analysis**: Use database introspection to understand relationships
- **AST Manipulation**: Modify code generation trees (`sc.Code`, `sc.Type`, etc.)
- **Template Generation**: Update generation logic in core modules
- **Verification**: Check generated code produces expected output

**Testing Strategy**:
- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test full generation pipeline
- **Regression Tests**: Ensure fixes don't break existing functionality
- **Edge Case Coverage**: Test boundary conditions and unusual schemas

## Project Memories and Notes

### Code Generation and Development Workflow
- You need to run `bleep generate-adventureworks` to see the effect on codegen based on what is in the test database. Do this before running tests

### Development Philosophy
- We should never generate code which relies on derivation. *we* are the deriver

### Memories
- **NEVER REPORT SUCCESS IF ITS NOT A SUCCESS.**
- never ever use default parameters for anything
- UNDER NO CIRCUMSTANCES ARE YOU USING UNTYPED HTTP CLIENTS WHEN IMPLEMENTING INTEGRATION TESTS. WE ARE TESTING GENERATED CODE IN BOTH ENDS HERE