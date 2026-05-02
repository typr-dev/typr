import React from "react";
import Link from "@docusaurus/Link";
import Layout from "@theme/Layout";
import CodeBlock from "@theme/CodeBlock";
import Tabs from "@theme/Tabs";
import TabItem from "@theme/TabItem";
import TypoLogo from "@site/src/components/TypoLogo";
import BoundaryDiagram from "@site/src/components/BoundaryDiagram";

import styles from "./index.module.css";

// =============================================================================
// CODE EXAMPLES - Platform-focused
// =============================================================================

const CODE = {
  // Without Typr - show the real pain
  without: {
    java: `// API types (you write these)
record CreateOrderRequest(Long userId, BigDecimal amount) {}
record OrderResponse(Long id, Long userId, String status) {}

// DB types (you write these too)
record OrderEntity(Long id, Long userId, BigDecimal amount, String status) {}

// The mapper (you write this)
class OrderMapper {
    OrderEntity toEntity(CreateOrderRequest req) {
        return new OrderEntity(null, req.userId(), req.amount(), "pending");
    }
    OrderResponse toResponse(OrderEntity e) {
        return new OrderResponse(e.id(), e.userId(), e.status());
    }
}

// The service
class OrderService {
    OrderResponse createOrder(CreateOrderRequest req) {
        OrderEntity entity = mapper.toEntity(req);
        OrderEntity saved = repository.save(entity);
        return mapper.toResponse(saved);
    }

    // BUG: userId and orderId are both Long - this compiles fine!
    User getUser(Long orderId) {
        return userRepository.findById(orderId); // Wrong ID type. Runtime bug.
    }
}`,
    kotlin: `// API types (you write these)
data class CreateOrderRequest(val userId: Long, val amount: BigDecimal)
data class OrderResponse(val id: Long, val userId: Long, val status: String)

// DB types (you write these too)
data class OrderEntity(val id: Long?, val userId: Long, val amount: BigDecimal, val status: String)

// The mapper (you write this)
class OrderMapper {
    fun toEntity(req: CreateOrderRequest) = OrderEntity(null, req.userId, req.amount, "pending")
    fun toResponse(e: OrderEntity) = OrderResponse(e.id!!, e.userId, e.status)
}

// The service
class OrderService {
    fun createOrder(req: CreateOrderRequest): OrderResponse {
        val entity = mapper.toEntity(req)
        val saved = repository.save(entity)
        return mapper.toResponse(saved)
    }

    // BUG: userId and orderId are both Long - this compiles fine!
    fun getUser(orderId: Long): User {
        return userRepository.findById(orderId) // Wrong ID type. Runtime bug.
    }
}`,
    scala: `// API types (you write these)
case class CreateOrderRequest(userId: Long, amount: BigDecimal)
case class OrderResponse(id: Long, userId: Long, status: String)

// DB types (you write these too)
case class OrderEntity(id: Option[Long], userId: Long, amount: BigDecimal, status: String)

// The mapper (you write this)
class OrderMapper {
  def toEntity(req: CreateOrderRequest) = OrderEntity(None, req.userId, req.amount, "pending")
  def toResponse(e: OrderEntity) = OrderResponse(e.id.get, e.userId, e.status)
}

// The service
class OrderService {
  def createOrder(req: CreateOrderRequest): OrderResponse = {
    val entity = mapper.toEntity(req)
    val saved = repository.save(entity)
    mapper.toResponse(saved)
  }

  // BUG: userId and orderId are both Long - this compiles fine!
  def getUser(orderId: Long): User =
    userRepository.findById(orderId) // Wrong ID type. Runtime bug.
}`
  },

  // With Typr - clean and safe
  withTypr: {
    java: `// Generated types - you write NOTHING
// UserId, OrderId, OrderRow, OrderRowUnsaved,
// CreateOrderRequest, OrderResponse - all generated

class OrderService {
    OrderResponse createOrder(CreateOrderRequest req) {
        // Types flow through - no mapping
        OrderRow saved = orderRepo.insert(
            new OrderRowUnsaved(req.userId(), req.amount()),
            conn
        );
        return new OrderResponse(saved.id(), saved.userId(), saved.status());
    }

    // This won't compile - OrderId and UserId are different types
    User getUser(OrderId orderId) {
        return userRepo.selectById(orderId);
        // COMPILE ERROR: selectById expects UserId, got OrderId
    }
}`,
    kotlin: `// Generated types - you write NOTHING
// UserId, OrderId, OrderRow, OrderRowUnsaved,
// CreateOrderRequest, OrderResponse - all generated

class OrderService {
    fun createOrder(req: CreateOrderRequest): OrderResponse {
        // Types flow through - no mapping
        val saved = orderRepo.insert(
            OrderRowUnsaved(req.userId, req.amount),
            conn
        )
        return OrderResponse(saved.id, saved.userId, saved.status)
    }

    // This won't compile - OrderId and UserId are different types
    fun getUser(orderId: OrderId): User {
        return userRepo.selectById(orderId)
        // COMPILE ERROR: selectById expects UserId, got OrderId
    }
}`,
    scala: `// Generated types - you write NOTHING
// UserId, OrderId, OrderRow, OrderRowUnsaved,
// CreateOrderRequest, OrderResponse - all generated

class OrderService {
  def createOrder(req: CreateOrderRequest): OrderResponse = {
    // Types flow through - no mapping
    val saved = orderRepo.insert(
      OrderRowUnsaved(req.userId, req.amount)
    )
    OrderResponse(saved.id, saved.userId, saved.status)
  }

  // This won't compile - OrderId and UserId are different types
  def getUser(orderId: OrderId): User =
    userRepo.selectById(orderId)
    // COMPILE ERROR: selectById expects UserId, got OrderId
}`
  },

  // Unified types - domain type YAML config
  bridgeConfig: `# Define your domain model
domainTypes:
  Customer:
    primary: postgres:sales.customer   # The anchor boundary
    fields:
      id: CustomerId
      firstName: FirstName
      lastName: LastName
      email: Email?
    alignedSources:
      mariadb:customers: superset      # Aligned to another DB
      api:Customer: exact              # Aligned to API contract

# Optional: field-level type safety
fieldTypes:
  CustomerId:
    db: { column: [customer_id], primary_key: true }
  FirstName:
    db: { column: [first_name] }`,

  // Unified types preview - generated domain type
  bridgePreview: {
    java: `// Generated: ONE domain type used everywhere
public record Customer(
    CustomerId id,
    FirstName firstName,
    LastName lastName,
    Optional<Email> email
) {
    // Auto-generated mappers for each boundary
    public static Customer fromPostgres(CustomerRow row) { ... }
    public static Customer fromMariadb(CustomersRow row) { ... }
    public static CustomerDto toApi(Customer c) { ... }
}`,
    kotlin: `// Generated: ONE domain type used everywhere
data class Customer(
    val id: CustomerId,
    val firstName: FirstName,
    val lastName: LastName,
    val email: Email?
) {
    companion object {
        // Auto-generated mappers for each boundary
        fun fromPostgres(row: CustomerRow): Customer = ...
        fun fromMariadb(row: CustomersRow): Customer = ...
        fun toApi(c: Customer): CustomerDto = ...
    }
}`,
    scala: `// Generated: ONE domain type used everywhere
case class Customer(
    id: CustomerId,
    firstName: FirstName,
    lastName: LastName,
    email: Option[Email]
)

object Customer:
  // Auto-generated mappers for each source
  def fromPostgres(row: CustomerRow): Customer = ...
  def fromMariadb(row: CustomersRow): Customer = ...
  def toApi(c: Customer): CustomerDto = ...`
  },

  // Architect preview - MCP workflow (no language tabs)
  architectWorkflow: `# Planning Mode
→ AI proposes schema change
→ Human reviews and approves
→ Typr generates types

# Implementation Mode
→ AI writes code using generated types
→ Compiler catches type errors immediately
→ AI fixes errors, iterates until it compiles
→ Human reviews working code`,

  // DB preview - from showcase/customer_order
  dbPreview: {
    java: `// Generated from showcase.customer_order table
public record CustomerOrderId(String value) {}

public record CustomerOrderRow(
    CustomerOrderId id,
    CustomerId customerId,        // FK → typed
    Optional<AddressId> shippingAddressId,
    OrderStatus status,
    BigDecimal total,
    Optional<Jsonb> metadata,     // PostgreSQL jsonb
    Optional<String[]> tags       // PostgreSQL array
) {}

public interface CustomerOrderRepo {
    CustomerOrderRow insert(CustomerOrderRowUnsaved row, Connection c);
    Optional<CustomerOrderRow> selectById(CustomerOrderId id, Connection c);
    SelectBuilder<CustomerOrderFields, CustomerOrderRow> select();
}`,
    kotlin: `// Generated from showcase.customer_order table
data class CustomerOrderId(val value: String)

data class CustomerOrderRow(
    val id: CustomerOrderId,
    val customerId: CustomerId,        // FK → typed
    val shippingAddressId: AddressId?,
    val status: OrderStatus?,
    val total: BigDecimal,
    val metadata: Jsonb?,              // PostgreSQL jsonb
    val tags: Array<String>?           // PostgreSQL array
)

interface CustomerOrderRepo {
    fun insert(row: CustomerOrderRowUnsaved, c: Connection): CustomerOrderRow
    fun selectById(id: CustomerOrderId, c: Connection): CustomerOrderRow?
    fun select(): SelectBuilder<CustomerOrderFields, CustomerOrderRow>
}`,
    scala: `// Generated from showcase.customer_order table
case class CustomerOrderId(value: String)

case class CustomerOrderRow(
    id: CustomerOrderId,
    customerId: CustomerId,        // FK → typed
    shippingAddressId: Option[AddressId],
    status: Option[OrderStatus],
    total: BigDecimal,
    metadata: Option[Jsonb],       // PostgreSQL jsonb
    tags: Option[Array[String]]    // PostgreSQL array
)

trait CustomerOrderRepo {
    def insert(row: CustomerOrderRowUnsaved)(using c: Connection): CustomerOrderRow
    def selectById(id: CustomerOrderId)(using c: Connection): Option[CustomerOrderRow]
    def select: SelectBuilder[CustomerOrderFields, CustomerOrderRow]
}`
  },

  // API preview
  apiPreview: {
    java: `// OpenAPI boundary: Your spec, typed
// Sealed response types - exhaustive pattern matching
public sealed interface Response200404<T200, T404>
    permits Ok, NotFound {}

// Same UserId as database layer
public record CreateOrderRequest(
    UserId userId,    // ← Same type!
    Money amount      // ← Same type!
) {}

// Server and client implement SAME interface
public interface OrdersApi {
    Response200404<Order, Error> getOrder(OrderId orderId);
    Order createOrder(CreateOrderRequest request);
}`,
    kotlin: `// OpenAPI boundary: Your spec, typed
// Sealed response types - exhaustive pattern matching
sealed interface Response200404<out T200, out T404>
data class Ok<T200>(val value: T200) : Response200404<T200, Nothing>
data class NotFound<T404>(val value: T404) : Response200404<Nothing, T404>

// Same UserId as database layer
data class CreateOrderRequest(
    val userId: UserId,   // ← Same type!
    val amount: Money     // ← Same type!
)

// Server and client implement SAME interface
interface OrdersApi {
    fun getOrder(orderId: OrderId): Response200404<Order, Error>
    fun createOrder(request: CreateOrderRequest): Order
}`,
    scala: `// OpenAPI boundary: Your spec, typed
// Sealed response types - exhaustive pattern matching
sealed trait Response200404[+T200, +T404]
case class Ok[T200](value: T200) extends Response200404[T200, Nothing]
case class NotFound[T404](value: T404) extends Response200404[Nothing, T404]

// Same UserId as database layer
case class CreateOrderRequest(
    userId: UserId,   // ← Same type!
    amount: Money     // ← Same type!
)

// Server and client implement SAME interface
trait OrdersApi {
    def getOrder(orderId: OrderId): IO[Response200404[Order, Error]]
    def createOrder(request: CreateOrderRequest): IO[Order]
}`
  },

};

// =============================================================================
// PAGE SECTIONS
// =============================================================================

function HeroSection() {
  return (
    <header className={styles.hero}>
      <div className={styles.heroBackground}>
        <div className={styles.gridOverlay}></div>
      </div>
      <div className="container">
        <div className={styles.heroContent}>
          <div className={styles.logoRow}>
            <TypoLogo size={64} animated={true} />
            <h1 className={styles.brandName}>Typr</h1>
          </div>
          <h2 className={styles.headline}>
            Seal Your System's <span className={styles.highlight}>Boundaries</span>
          </h2>
          <p className={styles.heroSubhead}>
            Generate type-safe JVM code for every boundary — databases, APIs, events, services. The compiler catches mistakes in seconds.
          </p>
          <ul className={styles.heroPunchlines}>
            <li className={styles.punchline}>Wrong column? You'll know immediately.</li>
            <li className={styles.punchline}>New hire? Productive and safe.</li>
            <li className={styles.punchline}>AI agent? Much more effective with guardrails.</li>
          </ul>
          <div className={styles.heroCta}>
            <Link className="button button--primary button--lg" to="/typr/getting-started">
              Get Started
            </Link>
            <Link className="button button--outline button--lg" to="/typr/">
              Learn More
            </Link>
          </div>
        </div>
      </div>
    </header>
  );
}

function ProblemSection() {
  return (
    <section className={styles.problem}>
      <div className="container">
        <h2 className={styles.sectionTitle}>The Boundary Problem</h2>
        <BoundaryDiagram />
      </div>
    </section>
  );
}

function CodeComparisonSection() {
  return (
    <section className={styles.codeComparisonSection}>
      <div className="container">
        <h2 className={styles.sectionTitle}>The Difference</h2>
        <p className={styles.sectionSubtitle}>
          Same feature. One hides mistakes. One surfaces them instantly.
        </p>

        <div className={styles.codeComparison}>
          <div className={styles.codeColumn}>
            <div className={styles.codeHeader}>
              <span className={styles.badBadge}>Without Typr</span>
            </div>
            <p className={styles.codeDescription}>
              You write all the types. You maintain all the mappers.
              Mix up <code>userId</code> and <code>orderId</code>? Compiles fine. Ships. Breaks in production.
            </p>
            <CodeTabs code={CODE.without} />
          </div>
          <div className={styles.codeColumn}>
            <div className={styles.codeHeader}>
              <span className={styles.goodBadge}>With Typr</span>
            </div>
            <p className={styles.codeDescription}>
              Types generated from your schema. No mappers to maintain.
              Mix up <code>userId</code> and <code>orderId</code>? Compiler catches it instantly.
            </p>
            <CodeTabs code={CODE.withTypr} />
          </div>
        </div>
      </div>
    </section>
  );
}

function FeedbackLoopSection() {
  return (
    <section className={styles.feedbackLoop}>
      <div className="container">
        <h2 className={styles.sectionTitle}>The Tight Feedback Loop</h2>
        <p className={styles.sectionSubtitle}>
          When mistakes are caught in seconds, not sprints, everyone moves faster.
        </p>

        <div className={styles.loopComparison}>
          <div className={styles.loopColumn}>
            <div className={styles.loopHeader}>
              <span className={styles.badBadge}>Without Type Safety</span>
            </div>
            <div className={styles.loopSteps}>
              <div className={styles.loopStep}>Write code</div>
              <div className={styles.loopArrow}>↓</div>
              <div className={styles.loopStep}>Compiles <span className={styles.loopNote}>(everything is Long/String)</span></div>
              <div className={styles.loopArrow}>↓</div>
              <div className={styles.loopStep}>Tests pass <span className={styles.loopNote}>(maybe)</span></div>
              <div className={styles.loopArrow}>↓</div>
              <div className={styles.loopStep}>Code review</div>
              <div className={styles.loopArrow}>↓</div>
              <div className={styles.loopStep}>Ships to prod</div>
              <div className={styles.loopArrow}>↓</div>
              <div className={styles.loopStepBad}>User finds bug</div>
              <div className={styles.loopArrow}>↓</div>
              <div className={styles.loopStepBad}>Debug: was it the ID type?</div>
            </div>
            <div className={styles.loopTime}>Hours to days to find the mistake</div>
          </div>

          <div className={styles.loopColumn}>
            <div className={styles.loopHeader}>
              <span className={styles.goodBadge}>With Typr</span>
            </div>
            <div className={styles.loopSteps}>
              <div className={styles.loopStep}>Write code</div>
              <div className={styles.loopArrow}>↓</div>
              <div className={styles.loopStepGood}>Compiler: "OrderId ≠ UserId"</div>
              <div className={styles.loopArrow}>↓</div>
              <div className={styles.loopStep}>Fix</div>
              <div className={styles.loopArrow}>↓</div>
              <div className={styles.loopStepGood}>Compiler: "✓"</div>
              <div className={styles.loopArrow}>↓</div>
              <div className={styles.loopStep}>Code review <span className={styles.loopNote}>(working code)</span></div>
              <div className={styles.loopArrow}>↓</div>
              <div className={styles.loopStepGood}>Ship with confidence</div>
            </div>
            <div className={styles.loopTime}>Seconds to find the mistake</div>
          </div>
        </div>

        <div className={styles.loopBenefit}>
          <p>
            Every compile error is a bug that didn't ship.<br/>
            Every type mismatch caught instantly is a debugging session that didn't happen.
          </p>
        </div>
      </div>
    </section>
  );
}

function WhoBenefitsSection() {
  return (
    <section className={styles.whoBenefits}>
      <div className="container">
        <h2 className={styles.sectionTitle}>Guardrails for Everyone</h2>
        <p className={styles.sectionSubtitle}>
          The less you trust someone with the full context, the more you need the compiler to enforce boundaries.
        </p>

        <div className={styles.benefitsGrid}>
          <div className={styles.benefitCard}>
            <div className={styles.benefitIcon}>👤</div>
            <h3>Junior Developers</h3>
            <p>
              Don't need to understand the whole system.
              The types tell them what's possible.
              Wrong ID type? Won't compile. Wrong response shape? Won't compile.
              <strong> Productive from day one.</strong>
            </p>
          </div>

          <div className={styles.benefitCard}>
            <div className={styles.benefitIcon}>🤝</div>
            <h3>Contractors</h3>
            <p>
              Clear boundaries, limited blast radius.
              They implement against a typed interface—can't accidentally
              break what they can't touch.
              <strong> Safe delegation.</strong>
            </p>
          </div>

          <div className={styles.benefitCard}>
            <div className={styles.benefitIcon}>🤖</div>
            <h3>AI Agents</h3>
            <p>
              Constrained by the compiler, not just prompts.
              Every type error is instant feedback. Fix, compile, fix, compile.
              <strong> Tight loops save tokens and time.</strong>
            </p>
          </div>

          <div className={styles.benefitCard}>
            <div className={styles.benefitIcon}>🎯</div>
            <h3>Tech Leads</h3>
            <p>
              Review the contracts, not the plumbing.
              Approve schema and API changes—implementation follows the types.
              <strong> High-leverage decisions.</strong>
            </p>
          </div>
        </div>

        <div className={styles.benefitsMessage}>
          <p>
            <strong>The pattern is the same:</strong> when someone lacks full context—whether that's experience,
            time on the project, or being an AI—constraints aren't limitations. They're the shortest path to working code.
          </p>
        </div>
      </div>
    </section>
  );
}

function PrecisionSection() {
  return (
    <section className={styles.precision}>
      <div className="container">
        <h2 className={styles.sectionTitle}>Precision, Not Approximation</h2>
        <p className={styles.sectionSubtitle}>
          Most tools flatten your contracts into basic types. Typr preserves everything at every boundary.
        </p>

        <div className={styles.precisionTables}>
          <div className={styles.precisionTableWrapper}>
            <h3>Database Schema</h3>
            <div className={styles.precisionTable}>
              <table>
                <thead>
                  <tr>
                    <th>Your Schema</th>
                    <th>Others</th>
                    <th>Typr</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td><code>CHAR(50)</code></td>
                    <td className={styles.weak}><code>String</code></td>
                    <td className={styles.strong}><code>PaddedString50</code></td>
                  </tr>
                  <tr>
                    <td><code>VARCHAR(100)</code></td>
                    <td className={styles.weak}><code>String</code></td>
                    <td className={styles.strong}><code>VarcharMax100</code></td>
                  </tr>
                  <tr>
                    <td><code>users.id</code> PK</td>
                    <td className={styles.weak}><code>Long</code></td>
                    <td className={styles.strong}><code>UserId</code></td>
                  </tr>
                  <tr>
                    <td><code>orders.user_id</code> FK</td>
                    <td className={styles.weak}><code>Long</code></td>
                    <td className={styles.strong}><code>UserId</code></td>
                  </tr>
                  <tr>
                    <td><code>DEFAULT NOW()</code></td>
                    <td className={styles.weak}><code>Timestamp</code></td>
                    <td className={styles.strong}><code>Defaulted&lt;OffsetDateTime&gt;</code></td>
                  </tr>
                  <tr>
                    <td>PostgreSQL <code>UUID[]</code></td>
                    <td className={styles.weak}>???</td>
                    <td className={styles.strong}><code>Array&lt;UUID&gt;</code></td>
                  </tr>
                  <tr>
                    <td>PostgreSQL composite type</td>
                    <td className={styles.weak}>???</td>
                    <td className={styles.strong}>record / data class</td>
                  </tr>
                  <tr>
                    <td>Oracle nested table</td>
                    <td className={styles.weak}>???</td>
                    <td className={styles.strong}>record / data class</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div className={styles.precisionTableWrapper}>
            <h3>OpenAPI Spec</h3>
            <div className={styles.precisionTable}>
              <table>
                <thead>
                  <tr>
                    <th>Your Spec</th>
                    <th>Others</th>
                    <th>Typr</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>200 / 404 responses</td>
                    <td className={styles.weak}><code>Object</code></td>
                    <td className={styles.strong}>Sealed ADT</td>
                  </tr>
                  <tr>
                    <td><code>userId</code> path param</td>
                    <td className={styles.weak}><code>String</code></td>
                    <td className={styles.strong}><code>UserId</code></td>
                  </tr>
                  <tr>
                    <td><code>nullable: true</code></td>
                    <td className={styles.weak}><code>T</code></td>
                    <td className={styles.strong}><code>Optional&lt;T&gt;</code> / <code>T?</code></td>
                  </tr>
                  <tr>
                    <td><code>enum: [a, b, c]</code></td>
                    <td className={styles.weak}><code>String</code></td>
                    <td className={styles.strong}><code>MyEnum</code></td>
                  </tr>
                  <tr>
                    <td>Server interface</td>
                    <td className={styles.weak}>loose types</td>
                    <td className={styles.strong}>exact contract</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div className={styles.precisionBenefits}>
          <h3>What This Means</h3>
          <p>When someone writes code using Typr's generated types, <strong>they can't</strong>:</p>
          <ul>
            <li>Pass <code>null</code> where non-null is required</li>
            <li>Mix up <code>userId</code> and <code>orderId</code></li>
            <li>Miss a response case the API declares</li>
            <li>Return a 200 when the spec says 201</li>
            <li>Use <code>String</code> where <code>UUID</code> is expected</li>
          </ul>
          <p className={styles.precisionPunchline}>
            The compiler catches it. Before code review. Before runtime. Before production.
          </p>
        </div>
      </div>
    </section>
  );
}

function CodeTabs({ code, title }) {
  return (
    <Tabs groupId="language">
      <TabItem value="java" label="Java">
        <CodeBlock language="java" title={title}>{code.java}</CodeBlock>
      </TabItem>
      <TabItem value="kotlin" label="Kotlin">
        <CodeBlock language="kotlin" title={title}>{code.kotlin}</CodeBlock>
      </TabItem>
      <TabItem value="scala" label="Scala">
        <CodeBlock language="scala" title={title}>{code.scala}</CodeBlock>
      </TabItem>
    </Tabs>
  );
}


function BoundariesSection() {
  return (
    <section className={styles.products}>
      <div className="container">
        <h2 className={styles.sectionTitle}>Every Boundary, One Type System</h2>
        <p className={styles.sectionSubtitle}>
          Typr reads schemas from every boundary in your system and generates type-safe code.
          Add a second boundary and Typr validates that shared types stay consistent across both.
        </p>

        <div className={styles.sourceGrid}>
          <div className={styles.sourceCard}>
            <h3>Databases</h3>
            <p>PostgreSQL, MariaDB, Oracle, SQL Server, DuckDB, DB2.
              Row types, ID types, repositories, DSL queries.</p>
            <div className={styles.productFeatures}>
              <span>6 databases</span>
              <span>Full DDL fidelity</span>
              <span>Type-safe DSL</span>
            </div>
            <Link className={styles.sourceLink} to="/typr/boundaries/databases/">Explore →</Link>
          </div>
          <div className={styles.sourceCard}>
            <h3>REST APIs</h3>
            <p>OpenAPI specs. Sealed response types, server stubs, client stubs.
              Same ID types as your database.</p>
            <div className={styles.productFeatures}>
              <span>Sealed responses</span>
              <span>Server + Client</span>
              <span>Shared types</span>
            </div>
            <Link className={styles.sourceLink} to="/typr/boundaries/apis/">Explore →</Link>
          </div>
          <div className={styles.sourceCard}>
            <h3>Kafka / Avro</h3>
            <p>Avro schemas. Typed producers and consumers.
              Multi-event topics with sealed interfaces. RPC support.</p>
            <div className={styles.productFeatures}>
              <span>Avro codegen</span>
              <span>Typed producers</span>
              <span>Typed consumers</span>
            </div>
            <Link className={styles.sourceLink} to="/typr/boundaries/events/">Explore →</Link>
          </div>
          <div className={styles.sourceCard}>
            <h3>gRPC / Protobuf</h3>
            <p>Proto definitions. Typr maps protobuf messages
              to your domain types with type-safe converters.</p>
            <div className={styles.productFeatures}>
              <span>Proto codegen</span>
              <span>Bidirectional</span>
              <span>Type policies</span>
            </div>
            <span className={styles.comingSoonLabel}>Coming soon</span>
          </div>
        </div>

        <div className={styles.bridgeRow}>
          <div className={styles.bridgeCard}>
            <div className={styles.bridgeCardContent}>
              <div>
                <div className={styles.productHeader}>
                  <h3>Unified Types</h3>
                  <span className={styles.productBadge}>Cross-Boundary</span>
                </div>
                <p>
                  <strong>Define <code>Customer</code> once. Use it everywhere.</strong> Declare
                  how data flows between all your boundaries — forward, drop, or custom transform per field.
                  Typr validates compatibility and generates all mapping code.
                </p>
              </div>
              <div className={styles.bridgeExample}>
                <CodeBlock language="yaml" title="typr.yaml">{CODE.bridgeConfig}</CodeBlock>
              </div>
            </div>
            <div className={styles.productFeatures}>
              <span>Flow declarations</span>
              <span>Type policies</span>
              <span>Auto mappers</span>
              <span>Custom transforms</span>
              <span>CI validation</span>
            </div>
            <Link className="button button--primary" to="/typr/unified-types/">
              Learn about Unified Types →
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}

function StackSection() {
  return (
    <section className={styles.stack}>
      <div className="container">
        <h2 className={styles.sectionTitle}>Works With Your Stack</h2>
        <div className={styles.stackGrid}>
          <div className={styles.stackColumn}>
            <h3>Boundaries</h3>
            <div className={styles.stackTags}>
              <span>PostgreSQL</span>
              <span>MariaDB</span>
              <span>Oracle</span>
              <span>SQL Server</span>
              <span>DuckDB</span>
              <span>DB2</span>
              <span>OpenAPI</span>
              <span>Kafka / Avro</span>
              <span className={styles.stackTagSoon}>gRPC / Protobuf</span>
              <span className={styles.stackTagSoon}>GraphQL</span>
            </div>
          </div>
          <div className={styles.stackColumn}>
            <h3>Languages</h3>
            <div className={styles.stackTags}>
              <span>Java 17+</span>
              <span>Kotlin</span>
              <span>Scala 2/3</span>
            </div>
          </div>
          <div className={styles.stackColumn}>
            <h3>Frameworks</h3>
            <div className={styles.stackTags}>
              <span>Spring Boot</span>
              <span>Quarkus</span>
              <span>Http4s</span>
              <span>JAX-RS</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function FinalCTA() {
  return (
    <section className={styles.finalCta}>
      <div className="container">
        <h2>Define your domain. See the flow. Ship with confidence.</h2>
        <p>
          Define <code>Customer</code> as your domain type. Typr validates compatibility across
          every boundary and generates the mapping code. The compiler enforces every boundary.
        </p>
        <div className={styles.ctaButtons}>
          <Link className="button button--primary button--lg" to="/typr/getting-started">
            Get Started
          </Link>
        </div>
      </div>
    </section>
  );
}

// =============================================================================
// PAGE ASSEMBLY
// =============================================================================

export default function Home() {
  return (
    <Layout
      title="Typr — Seal Your System's Boundaries"
      description="Generate type-safe code for every boundary in your system — databases, APIs, events, services. Unified types across all of them. Compiler-enforced contracts."
    >
      <HeroSection />
      <main>
        <ProblemSection />
        <WhoBenefitsSection />
        <CodeComparisonSection />
        <PrecisionSection />
        <BoundariesSection />
        <StackSection />
        <FinalCTA />
      </main>
    </Layout>
  );
}
