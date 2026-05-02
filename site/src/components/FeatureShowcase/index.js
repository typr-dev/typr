import React from "react";
import Link from "@docusaurus/Link";
import CodeBlock from "@theme/CodeBlock";
import styles from "./styles.module.css";

const features = [
  {
    category: "All The Boilerplate, None Of The Work",
    items: [
      {
        title: "From Database Schema to Complete Scala Code",
        description: "Point Typr at your PostgreSQL database and watch it generate everything: case classes, repositories, type-safe IDs, JSON codecs, and test helpers. No manual mapping code ever again.",
        sqlCode: `-- Your PostgreSQL schema
CREATE TABLE user (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  department_id UUID REFERENCES departments(id)
);`,
        scalaCode: `// Generated automatically:
case class UserId(value: TyprUUID)
case class UserRow(
  id: UserId,
  email: String, 
  name: String,
  createdAt: Option[TyprLocalDateTime],
  departmentId: Option[DepartmentsId]
)

trait UserRepo {
  def selectAll(implicit c: Connection): List[UserRow]
  def selectById(id: UserId)(implicit c: Connection): Option[UserRow]
  def insert(unsaved: UserRowUnsaved)(implicit c: Connection): UserRow
  def update(row: UserRow)(implicit c: Connection): Boolean
  def deleteById(id: UserId)(implicit c: Connection): Boolean
  // + 20 more methods
}`,
        docs: "/typr/boundaries/databases/setup"
      },
      {
        title: "Complete CRUD + Advanced Operations", 
        description: "Get full repositories with not just basic CRUD, but batch operations, upserts, streaming inserts, and optional tracking methods. All generated, all type-safe.",
        code: `// All generated automatically from your schema:

// Basic operations
userRepo.selectById(UserId(uuid))
userRepo.insert(unsavedUser)
userRepo.update(user.copy(name = "New Name"))
userRepo.deleteById(userId)

// Batch operations  
userRepo.upsertBatch(users)  // Returns the upserted rows

// Advanced operations
userRepo.selectByIds(userIds)
userRepo.selectByIdsTracked(userIds) // tracks found/missing
userRepo.insertStreaming(userStream)  // PostgreSQL COPY API`,
        docs: "/typr/boundaries/databases/what-is/relations"
      }
    ]
  },
  {
    category: "Relationships Become Navigation",
    items: [
      {
        title: "Foreign Keys Drive Everything",
        description: "Every foreign key in your database automatically generates navigation methods, type-safe joins, and reverse lookups. Your schema relationships become first-class code citizens.",
        sqlCode: `-- Database relationships
CREATE TABLE order (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES user(id),
  product_id UUID REFERENCES product(id)
);`,
        scalaCode: `// Generated from foreign keys:
case class OrderRow(
  id: OrderId,
  userId: Option[UserId],    // Type flows through relationships  
  productId: Option[ProductId]
)

// Type-safe DSL with automatic foreign key joins:
orderRepo.select
  .joinFk(_.fkUser)(userRepo.select)  // Auto-joins via foreign key
  .where { case (_, user) => user.email === Email("admin@company.com") }
  
// joinFk knows the relationship from your schema!
// Your IDE will autocomplete available foreign keys`,
        docs: "/typr/boundaries/databases/what-is/relations"
      },
      {
        title: "Type-Safe Foreign Key Navigation",
        description: "Typr's DSL provides joinFk for easy type-safe navigation through foreign key relationships. Your IDE knows exactly what's available at each level.",
        sqlCode: `-- Database with foreign key relationships
CREATE TABLE product (
  id UUID PRIMARY KEY,
  model_id UUID REFERENCES product_model(id),
  subcategory_id UUID REFERENCES product_subcategory(id)
);
CREATE TABLE product_subcategory (
  id UUID PRIMARY KEY,
  category_id UUID REFERENCES product_category(id)
);`,
        scalaCode: `// Navigate through multiple foreign keys with perfect type safety:
val query = productRepo.select
  .joinFk(_.fkProductModel)(productModelRepo.select)
  .joinFk { case (p, _) => p.fkProductSubcategory }(productSubcategoryRepo.select)
  .joinFk { case ((_, _), ps) => ps.fkProductCategory }(productCategoryRepo.select)
  .where { case (((product, model), subcategory), category) => 
    product.inStock === true &&
    category.name === "Electronics"
  }
  
// Each joinFk automatically uses the foreign key constraint
// No manual ON clauses needed - Typr knows the relationships!`,
        docs: "/typr/boundaries/databases/other-features/dsl-in-depth"
      }
    ]
  },
  {
    category: "Type Safety Revolution",
    items: [
      {
        title: "Strongly-Typed Primary Keys",
        description: "Every table gets its own ID type that flows through foreign key relationships. No more mixing up User IDs and Product IDs.",
        code: `case class UserId(value: TyprUUID)
case class ProductId(value: TyprUUID)

// Compile error if you mix them up!
def getUserOrders(userId: UserId): List[OrderRow] = {
  orderRepo.select
    .where(_.userId === userId.?)
    .toList
  // orderRepo.select.where(_.userId === productId.?) // ❌ Won't compile
}`,
        docs: "/typr/boundaries/databases/type-safety/id-types"
      },
      {
        title: "Type Flow Through Relationships",
        description: "Foreign key relationships automatically propagate specific types throughout your domain model.",
        sqlCode: `-- Database schema creates type flow
CREATE TABLE user (
  id UUID PRIMARY KEY, 
  name TEXT
);

CREATE TABLE order (
  id UUID PRIMARY KEY, 
  user_id UUID REFERENCES user(id)
);`,
        scalaCode: `// Generated code maintains relationships
case class UserRow(id: UserId, name: String)
case class OrderRow(id: OrderId, userId: Option[UserId]) // ✅ Specific type, not just UUID`,
        docs: "/typr/boundaries/databases/type-safety/type-flow"
      },
      {
        title: "PostgreSQL Domain Types",
        description: "Full support for PostgreSQL domains with constraint documentation in your generated code.",
        sqlCode: `-- Database domain
CREATE DOMAIN email AS TEXT CHECK (VALUE ~ '^[^@]+@[^@]+\\.[^@]+$');`,
        scalaCode: `// Generated Scala code with constraint docs
/** Domain: frontpage.email
  * Constraint: CHECK ((VALUE ~ '^[^@]+@[^@]+\\.[^@]+$'::text))
  */
case class Email(value: String)

// Usage in generated types:
case class UserRow(id: UserId, email: String) // Type preserved`,
        docs: "/typr/boundaries/databases/type-safety/domains"
      },
      {
        title: "Composite Primary Keys",
        description: "First-class support for composite primary keys with generated helper types and methods.",
        sqlCode: `-- Composite key table
CREATE TABLE user_permission (
  user_id UUID REFERENCES user(id),
  permission_id UUID REFERENCES permission(id),
  granted_at TIMESTAMP,
  PRIMARY KEY (user_id, permission_id)
);`,
        scalaCode: `// Generated composite key row:
case class UserPermissionRow(
  userId: UserId,
  permissionId: PermissionId,
  grantedAt: Option[TyprLocalDateTime]
)

// Repository uses composite key directly:
userPermissionRepo.insert(UserPermissionRowUnsaved(
  userId = userId,
  permissionId = permissionId
))`,
        docs: "/typr/boundaries/databases/type-safety/id-types#composite-keys"
      }
    ]
  },
  {
    category: "The Perfect DSL For Real-World Data Access",
    items: [
      {
        title: "Incredibly Easy To Work With",
        description: "A pragmatic DSL that makes everyday data operations a breeze. Perfect IDE support with autocomplete, inline documentation, and compile-time validation. Focused on what you do most: fetching, updating, and deleting data with complex joins and filters.",
        code: `// Fetch exactly the data you need with type-safe joins
val activeOrdersWithDetails = orderRepo.select
  .join(customerRepo.select)
  .on((o, c) => o.userId === c.userId)
  .join(productRepo.select)
  .on { case ((o, _), p) => o.productId === p.id.? }
  .where { case ((order, _), _) => order.status === "active".? }
  .where { case ((_, customer), _) => customer.verified === true.? }
  .where { case (_, product) => product.inStock === true.? }
  .orderBy { case ((order, _), _) => order.createdAt.desc }
  .limit(100)
  .toList  // Execute and get results

// Update with complex conditions
productRepo.update
  .set(_.inStock, Some(false))
  .set(_.lastModified, Some(TyprLocalDateTime.now))
  .where(_.quantity === 0.?)
  .where(_.lastRestocked < thirtyDaysAgo.?)
  .execute

// Delete with conditions
orderItemRepo.delete
  .where(_.orderId.in(cancelledOrderIds))
  .where(_.shippedAt.isNull)
  .execute`,
        docs: "/typr/boundaries/databases/other-features/dsl-in-depth"
      }
    ]
  },
  {
    category: "Pure SQL Files as First-Class Citizens",
    items: [
      {
        title: "Write Real SQL For Complex Queries",
        description: "When you need aggregations, window functions, or complex analytics, write real SQL in dedicated .sql files. Typr analyzes your queries and generates perfectly typed Scala methods - the best of both worlds.",
        sqlCode: `-- sql/user-analytics.sql
SELECT 
  u.name,
  u.email,
  COUNT(o.id) as order_count,
  SUM(o.total) as lifetime_value,
  MAX(o.created_at) as last_order_date
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE u.created_at >= :start_date:LocalDate!
  AND u.status = :status:UserStatus?
  AND (:min_orders? IS NULL OR COUNT(o.id) >= :min_orders)
GROUP BY u.id, u.name, u.email
HAVING SUM(o.total) > :min_value:BigDecimal!
ORDER BY lifetime_value DESC
LIMIT :limit:Int!`,
        scalaCode: `// Generated automatically:
trait UserAnalyticsSqlRepo {
  def apply(
    startDate: LocalDate,
    status: Option[String] = None,
    minValue: BigDecimal,
    limit: Int
  )(implicit c: Connection): List[UserAnalyticsSqlRow]
}`,
        docs: "/typr/boundaries/databases/what-is/sql-is-king"
      },
      {
        title: "Smart Parameter Inference",
        description: "Typr analyzes your SQL parameters against the database schema to infer exact types. Override nullability and types as needed with simple annotations.",
        code: `-- Advanced parameter syntax
SELECT p.*, a.city, e.salary
FROM persons p
JOIN addresses a ON p.address_id = a.id
LEFT JOIN employees e ON p.id = e.person_id
WHERE p.id = :person_id!               -- Required parameter
  AND p.created_at >= :since!          -- Required parameter
  AND a.country = :country:String?     -- Optional string parameter
  AND (:max_salary? IS NULL OR e.salary <= :max_salary)

-- Dynamic filtering patterns work perfectly
-- Type inference follows foreign keys
-- Custom domain types are preserved`,
        docs: "/typr/boundaries/databases/what-is/sql-is-king"
      },
      {
        title: "Updates with RETURNING Support",
        description: "Write UPDATE, INSERT, and DELETE operations in SQL files. Full support for RETURNING clauses with type-safe result parsing.",
        sqlCode: `-- sql/update-user-status.sql
UPDATE user 
SET 
  status = :new_status:frontpage.user_status!,
  created_at = NOW()
WHERE id = :user_id!
  AND status != :new_status
RETURNING 
  id,
  name,
  status,
  created_at as "modified_at:java.time.LocalDateTime!"`,
        scalaCode: `// Generated method returns updated rows:
trait UpdateUserStatusSqlRepo {
  def apply(
    newStatus: String,
    userId: TyprUUID
  )(implicit c: Connection): List[UpdateUserStatusSqlRow]
}

// Perfect for audit trails and optimistic locking`,
        docs: "/typr/boundaries/databases/what-is/sql-is-king"
      }
    ]
  },
  {
    category: "Testing Excellence",
    items: [
      {
        title: "TestInsert: Build Valid Data Graphs",
        description: "Generate complete object graphs with valid foreign key relationships. All fields are random by default, but you override exactly what your test cares about. Eliminates the 'lingering test state' problem forever.",
        code: `val testInsert = new TestInsert(new Random(42))

// Build a complete, valid data graph
val company = testInsert.frontpageCompanies(name = "Acme Corp")
val department = testInsert.frontpageDepartments(companyId = Some(company.id))
val manager = testInsert.frontpageUsers(
  departmentId = Some(department.id),
  role = Defaulted.Provided(Some(UserRole.manager))
)
val employees = List.fill(5)(
  testInsert.frontpageUsers(
    departmentId = Some(department.id),
    managerId = Some(manager.id),
    role = Defaulted.Provided(Some(UserRole.employee))
  )
)

// Every foreign key is valid!
// All other fields are realistic random data!
// Zero lingering state between tests!`,
        docs: "/typr/boundaries/databases/other-features/testing-with-random-values"
      },
      {
        title: "In-Memory Repository Stubs",
        description: "Drop-in repository replacements that work entirely in memory. Run huge parts of your application without a database - perfect for unit tests and development.",
        code: `// Replace real repos with in-memory stubs
val userRepo = UserRepoMock.empty
val orderRepo = OrdersRepoMock.empty
val productRepo = ProductsRepoMock.empty

// Seed with test data
userRepo.insertUnsaved(testUsers: _*)
orderRepo.insertUnsaved(testOrders: _*)
productRepo.insertUnsaved(testProducts: _*)

// Your entire business logic works!
val orderService = new OrderService(userRepo, orderRepo, productRepo)
val result = orderService.calculateMonthlyReport(userId)

// Runs instantly, no database needed
// Full DSL support including complex joins`,
        docs: "/typr/boundaries/databases/other-features/testing-with-stubs"
      },
      {
        title: "Full DSL Support in Stubs",
        description: "Unlike other testing libraries, Typr's mocks support the complete DSL including complex joins and filtering. Your business logic runs unchanged.",
        code: `// Complex queries work in memory!
val topCustomers = userRepo.select
  .join(orderRepo.select)
  .on((u, o) => u.id === o.userId.?)
  .join(productRepo.select)
  .on { case ((_, o), p) => o.productId === p.id.? }
  .where { case ((user, _), _) => user.status === "active".? }
  .where { case (_, product) => product.price > BigDecimal("100") }
  .limit(50)
  .toList

// This runs instantly in memory!
// Same code as production database queries!`,
        docs: "/typr/boundaries/databases/other-features/testing-with-stubs"
      }
    ]
  },
  {
    category: "Advanced PostgreSQL Integration",
    items: [
      {
        title: "Unprecedented PostgreSQL Array Support",
        description: "First-class support for PostgreSQL arrays with type-safe operations. Use arrays naturally in queries with .in(), arrayOverlaps, arrayConcat, and array indexing.",
        code: `// Full array support for all PostgreSQL types
case class ProductRow(
  id: ProductsId,
  name: String,
  tags: Option[Array[String]],        // TEXT[]
  categories: Option[Array[Int]],     // INTEGER[]
  prices: Option[Array[BigDecimal]],  // NUMERIC[]
  attributes: Option[Array[TyprJsonb]] // JSONB[]
)

// Array operations in queries
productRepo.select
  .where(_.id.in(Array(id1, id2, id3)))
  .where(_.tags.getOrElse(Array.empty).contains("sale"))
  .toList`,
        docs: "/typr/boundaries/databases/type-safety/arrays"
      },
      {
        title: "Other PostgreSQL Types & Features",
        description: "Support for geometric types, network types, JSON/JSONB, XML, and more. If PostgreSQL has it, Typr supports it.",
        code: `// Geometric and network types
case class LocationRow(
  id: LocationsId,
  position: Option[TyprPoint],   // POINT
  area: Option[TyprPolygon],     // POLYGON  
  ipRange: Option[TyprInet],     // INET
  metadata: Option[TyprJsonb]    // JSONB
)

// Types are preserved and can be used in queries
locationRepo.select
  .where(_.name === "Main Office")
  .toList`,
        docs: "/typr/boundaries/databases/type-safety/struct-types"
      }
    ]
  },
  {
    category: "Performance & Scalability",
    items: [
      {
        title: "Streaming Bulk Operations",
        description: "PostgreSQL COPY API integration for high-performance bulk inserts and updates.",
        code: `// Streaming insert using PostgreSQL COPY
val users = Iterator.range(1, 1000000).map(i => 
  UserRowUnsaved(
    name = s"User \$i", 
    email = s"user\$i@example.com"
  )
)

// Streams directly to PostgreSQL COPY API
val inserted = userRepo.insertUnsavedStreaming(users)
println(s"Inserted \$inserted records in seconds")

// Batch operations - returns the upserted rows
val upsertedRows = userRepo.upsertBatch(usersList)
println(s"Upserted \${upsertedRows.length} rows")`,
        docs: "/blog/the-cost-of-implicits"
      },
      {
        title: "Efficient Batch Operations",
        description: "Optimized batch insert, update, and delete operations with detailed result tracking.",
        code: `// True batch operations - single database roundtrip!
val newUsers = List(
  UserRowUnsaved(email = Email("user1@example.com"), name = "User 1"),
  UserRowUnsaved(email = Email("user2@example.com"), name = "User 2"),
  UserRowUnsaved(email = Email("user3@example.com"), name = "User 3")
)

// Batch upsert - returns all upserted rows
val upsertedUsers = userRepo.upsertBatch(newUsers)
println(s"Upserted \${upsertedUsers.length} users")

// Batch delete by IDs
val deleted = userRepo.deleteByIds(Array(userId1, userId2, userId3))
println(s"Deleted \$deleted rows")

// Streaming batch operations for huge datasets
val millionUsers = Iterator.range(1, 1000000).map(i => 
  UserRowUnsaved(email = Email(s"user\$i@example.com"), name = s"User \$i")
)
userRepo.insertUnsavedStreaming(millionUsers) // Uses PostgreSQL COPY`,
        docs: "/blog/the-cost-of-implicits"
      }
    ]
  },
  {
    category: "Multi-Library Support",
    items: [
      {
        title: "Choose Your Database Library",
        description: "Full support for Anorm, Doobie, and ZIO-JDBC with library-specific optimizations.",
        code: `// Anorm (Play Framework)
class UserController @Inject()(userRepo: UserRepo, db: Database) {
  def getUser(id: UserId) = Action { 
    db.withConnection { implicit c =>
      userRepo.selectById(id) match {
        case Some(user) => Ok(Json.toJson(user))
        case None => NotFound
      }
    }
  }
}

// Doobie (Cats Effect)
def getActiveUsers: ConnectionIO[List[UserRow]] = 
  userRepo.select
    .where(user => user.status === "active".?)
    .toList

// ZIO-JDBC
def getUsersZIO: ZIO[Connection, Throwable, List[UserRow]] =
  ZIO.serviceWithZIO[Connection](userRepo.selectAll(_))`,
        docs: "/typr/boundaries/databases/customization/overview#database-libraries"
      },
      {
        title: "JSON Library Integration",
        description: "Typr generates JSON codecs for Play JSON, Circe, and ZIO JSON - no manual derivation needed.",
        code: `// Typr generates all JSON codecs for you!

// Play JSON - generated in UserRow companion
implicit val usersReads: Reads[UserRow] = UserRow.reads
implicit val usersWrites: Writes[UserRow] = UserRow.writes

// Circe - generated in UserRow companion  
implicit val usersDecoder: Decoder[UserRow] = UserRow.decoder
implicit val usersEncoder: Encoder[UserRow] = UserRow.encoder

// ZIO JSON - generated in UserRow companion
implicit val usersCodec: JsonCodec[UserRow] = UserRow.codec

// Just use them - handles all complex types, arrays, nested objects
val json = Json.toJson(user)
val decoded = json.as[UserRow]`,
        docs: "/typr/boundaries/databases/other-features/json"
      }
    ]
  }
];

export default function FeatureShowcase() {
  return (
    <section className={styles.featureShowcase}>
      <div className="container">
        <div className={styles.header}>
          <h2 className={styles.title}>
            Every Feature You Need, Nothing You Don't
          </h2>
          <p className={styles.subtitle}>
            Typr delivers a comprehensive PostgreSQL development experience with unprecedented type safety, 
            testing capabilities, and developer productivity features.
          </p>
        </div>

        {features.map((category, categoryIndex) => (
          <div key={categoryIndex} className={styles.categorySection}>
            <h3 className={styles.categoryTitle}>{category.category}</h3>
            <div className={styles.featuresGrid}>
              {category.items.map((feature, featureIndex) => (
                <div key={featureIndex} className={styles.featureCard}>
                  <div className={styles.featureHeader}>
                    <h4 className={styles.featureTitle}>{feature.title}</h4>
                    <p className={styles.featureDescription}>{feature.description}</p>
                  </div>
                  {feature.sqlCode && (
                    <CodeBlock language="sql" className={styles.featureCode}>
                      {feature.sqlCode}
                    </CodeBlock>
                  )}
                  {feature.scalaCode && (
                    <CodeBlock language="scala" className={styles.featureCode}>
                      {feature.scalaCode}
                    </CodeBlock>
                  )}
                  {feature.code && (
                    <CodeBlock language="scala" className={styles.featureCode}>
                      {feature.code}
                    </CodeBlock>
                  )}
                  <div className={styles.featureFooter}>
                    <Link 
                      className={styles.featureLink} 
                      to={feature.docs}
                    >
                      Learn More →
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}

        <div className={styles.moreFeatures}>
          <h3 className={styles.moreFeaturesTitle}>And Much More...</h3>
          <div className={styles.moreFeaturesList}>
            <div className={styles.moreFeatureItem}>
              <strong>Advanced Customization:</strong> Type overrides, nullability control, custom naming conventions
            </div>
            <div className={styles.moreFeatureItem}>
              <strong>Enterprise Ready:</strong> Transaction support, CI/CD integration, version control friendly
            </div>
            <div className={styles.moreFeatureItem}>
              <strong>Developer Experience:</strong> Real-time code generation, IDE integration, comprehensive logging
            </div>
            <div className={styles.moreFeatureItem}>
              <strong>PostgreSQL Deep Integration:</strong> Comprehensive array support with operations, enums, domains, geometric types, network types
            </div>
          </div>
          <div className={styles.moreFeaturesCTA}>
            <Link className="button button--primary button--lg" to="/typr/boundaries/databases/">
              Explore All Features
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}