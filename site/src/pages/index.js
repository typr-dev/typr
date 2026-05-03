import React from "react";
import Link from "@docusaurus/Link";
import Layout from "@theme/Layout";
import CodeBlock from "@theme/CodeBlock";
import Tabs from "@theme/Tabs";
import TabItem from "@theme/TabItem";
import TypoLogo from "@site/src/components/TypoLogo";

import styles from "./index.module.css";

// =============================================================================
// CODE EXAMPLES
// =============================================================================

const CODE = {
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

class OrderService {
    // BUG: userId and orderId are both Long — this compiles fine.
    User getUser(Long orderId) {
        return userRepository.findById(orderId);
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

class OrderService {
    // BUG: userId and orderId are both Long — this compiles fine.
    fun getUser(orderId: Long): User {
        return userRepository.findById(orderId)
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

class OrderService {
  // BUG: userId and orderId are both Long — this compiles fine.
  def getUser(orderId: Long): User =
    userRepository.findById(orderId)
}`,
  },

  withTypr: {
    java: `// Generated types — you write NOTHING.
// UserId, OrderId, OrderRow, OrderRowUnsaved,
// CreateOrderRequest, OrderResponse — all generated.

class OrderService {
    OrderResponse createOrder(CreateOrderRequest req) {
        OrderRow saved = orderRepo.insert(
            new OrderRowUnsaved(req.userId(), req.amount()), conn);
        return new OrderResponse(saved.id(), saved.userId(), saved.status());
    }

    // Won't compile: OrderId and UserId are different types.
    User getUser(OrderId orderId) {
        return userRepo.selectById(orderId);
        //                         ^^^^^^^
        //   error: required UserId, found OrderId
    }
}`,
    kotlin: `// Generated types — you write NOTHING.
// UserId, OrderId, OrderRow, OrderRowUnsaved,
// CreateOrderRequest, OrderResponse — all generated.

class OrderService {
    fun createOrder(req: CreateOrderRequest): OrderResponse {
        val saved = orderRepo.insert(
            OrderRowUnsaved(req.userId, req.amount), conn)
        return OrderResponse(saved.id, saved.userId, saved.status)
    }

    // Won't compile: OrderId and UserId are different types.
    fun getUser(orderId: OrderId): User {
        return userRepo.selectById(orderId)
        //                         ^^^^^^^
        //   error: required UserId, found OrderId
    }
}`,
    scala: `// Generated types — you write NOTHING.
// UserId, OrderId, OrderRow, OrderRowUnsaved,
// CreateOrderRequest, OrderResponse — all generated.

class OrderService:
  def createOrder(req: CreateOrderRequest): OrderResponse =
    val saved = orderRepo.insert(
      OrderRowUnsaved(req.userId, req.amount))
    OrderResponse(saved.id, saved.userId, saved.status)

  // Won't compile: OrderId and UserId are different types.
  def getUser(orderId: OrderId): User =
    userRepo.selectById(orderId)
    //                  ^^^^^^^
    //   error: required UserId, found OrderId
`,
  },

  bridgeConfig: `# typr.yaml — define your domain once
domainTypes:
  Customer:
    primary: postgres:sales.customer       # anchor boundary
    fields:
      id: CustomerId
      firstName: FirstName
      lastName: LastName
      email: Email?
    alignedSources:
      mariadb:customers: superset          # legacy DB
      api:Customer:    exact               # OpenAPI contract
      kafka:CustomerCreated: subset        # event topic

# Optional: per-field type rules
fieldTypes:
  CustomerId:
    db:  { column: [customer_id], primary_key: true }
  FirstName:
    db:  { column: [first_name] }`,
};

// =============================================================================
// LAYOUT PRIMITIVES
// =============================================================================

function SectionLabel({ label }) {
  return (
    <div className={styles.sectionLabel}>
      <span className={styles.sectionLabelDivider} aria-hidden="true" />
      <span className={styles.sectionLabelText}>{label}</span>
    </div>
  );
}

function CodeTabs({ code }) {
  return (
    <Tabs groupId="language">
      <TabItem value="java" label="Java">
        <CodeBlock language="java">{code.java}</CodeBlock>
      </TabItem>
      <TabItem value="kotlin" label="Kotlin">
        <CodeBlock language="kotlin">{code.kotlin}</CodeBlock>
      </TabItem>
      <TabItem value="scala" label="Scala">
        <CodeBlock language="scala">{code.scala}</CodeBlock>
      </TabItem>
    </Tabs>
  );
}

// =============================================================================
// SECTIONS
// =============================================================================

function HeroSection() {
  return (
    <header className={styles.hero}>
      <div className={styles.heroGrid} aria-hidden="true" />
      <div className={`container ${styles.heroContainer}`}>
        <aside className={styles.heroMargin} aria-hidden="true">
          <div className={styles.heroMarginRow}>
            <span className={styles.heroMarginLabel}>doc.</span>
            <span className={styles.heroMarginValue}>typr/index</span>
          </div>
          <div className={styles.heroMarginRow}>
            <span className={styles.heroMarginLabel}>rev.</span>
            <span className={styles.heroMarginValue}>2026.05</span>
          </div>
          <div className={styles.heroMarginRow}>
            <span className={styles.heroMarginLabel}>scope</span>
            <span className={styles.heroMarginValue}>jvm / multi-boundary</span>
          </div>
        </aside>

        <div className={styles.heroMain}>
          <div className={styles.heroMasthead}>
            <TypoLogo size={56} animated={true} />
            <span className={styles.heroWordmark}>typr</span>
            <span className={styles.heroSerial}>— v1.0 RC6</span>
          </div>

          <h1 className={styles.heroHeadline}>
            <span className={styles.heroLine}>Seal your system's</span>
            <span className={styles.heroLine}>
              <em className={styles.heroEm}>boundaries</em>
              <span className={styles.heroDot} aria-hidden="true">·</span>
            </span>
          </h1>

          <p className={styles.heroLede}>
            Type‑safe code, generated for every boundary in your stack —
            databases, REST APIs, Avro topics, gRPC services. One domain
            type definition. The compiler enforces every contract.
          </p>

          <div className={styles.heroSpec}>
            <dl className={styles.heroSpecList}>
              <div className={styles.heroSpecRow}>
                <dt>Languages</dt>
                <dd>Java · Kotlin · Scala 2/3</dd>
              </div>
              <div className={styles.heroSpecRow}>
                <dt>Boundaries</dt>
                <dd>PostgreSQL · MariaDB · Oracle · SQL Server · DuckDB · DB2 · OpenAPI · Avro/Kafka · gRPC</dd>
              </div>
              <div className={styles.heroSpecRow}>
                <dt>Method</dt>
                <dd>Schema → AST → typed code. No reflection. No runtime mapping.</dd>
              </div>
            </dl>
          </div>

          <div className={styles.heroActions}>
            <Link className={styles.btnPrimary} to="/typr/getting-started">
              Begin <span className={styles.btnArrow} aria-hidden="true">→</span>
            </Link>
            <Link className={styles.btnGhost} to="/typr/">
              Read the docs
            </Link>
          </div>
        </div>

        <div className={styles.heroRule} aria-hidden="true" />
      </div>
    </header>
  );
}

function ManifestoSection() {
  return (
    <section className={styles.section}>
      <div className={`container ${styles.sectionContainer}`}>
        <SectionLabel label="The boundary problem" />

        <div className={styles.manifestoLayout}>
          <h2 className={styles.bigHeadline}>
            Every system bug starts at a&nbsp;boundary.
          </h2>

          <div className={styles.manifestoBody}>
            <p className={styles.lede}>
              Database to API. API to event bus. Event bus to RPC service.
              Each crossing is a translation, and every translation is an
              opportunity to drop, mistype, or quietly mangle a field.
            </p>
            <p>
              Most teams paper over this with hand‑written DTOs, mapper
              classes, and a slow‑burning fear of refactoring. Typr takes
              the opposite approach: <strong>read the schema, generate the
              types, let the compiler enforce the boundary.</strong>
            </p>
          </div>

          <BlueprintDiagram />
          <BlueprintDiagramMobile />
        </div>
      </div>
    </section>
  );
}

function BlueprintDiagramMobile() {
  const stations = [
    { tag: "A", name: "postgres",       policy: "anchor"   },
    { tag: "B", name: "mariadb",        policy: "superset" },
    { tag: "C", name: "openapi",        policy: "exact"    },
    { tag: "D", name: "kafka / avro",   policy: "subset"   },
  ];
  return (
    <div className={styles.diagramMobile}>
      <p className={styles.diagramMobileHeading}>One domain type, four boundaries</p>
      <ul className={styles.diagramMobileList}>
        <li className={styles.diagramMobileItem}>
          <span className={styles.diagramMobileTag}>·</span>
          <span className={styles.diagramMobileName}>Customer (domain)</span>
          <span className={styles.diagramMobilePolicy}>canonical</span>
        </li>
        {stations.map((s) => (
          <li key={s.tag} className={styles.diagramMobileItem}>
            <span className={styles.diagramMobileTag}>{s.tag}</span>
            <span className={styles.diagramMobileName}>{s.name}</span>
            <span className={styles.diagramMobilePolicy}>{s.policy}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function BlueprintDiagram() {
  // Domain fields (canonical Customer)
  const fields = [
    { i: "01", name: "id",         type: "CustomerId" },
    { i: "02", name: "firstName",  type: "FirstName" },
    { i: "03", name: "lastName",   type: "LastName" },
    { i: "04", name: "email",      type: "Email?" },
  ];

  // Boundary stations — mapped against the same fields with policies
  const stations = [
    {
      id: "pg",   tag: "A", code: "postgres",      title: "postgres",
      role: "primary",   policy: "anchor",
      rows: ["customer_id  int4 PK", "first_name   varchar", "last_name    varchar", "email        varchar?"],
    },
    {
      id: "maria",tag: "B", code: "mariadb",       title: "mariadb",
      role: "aligned",   policy: "superset",
      rows: ["id           bigint PK", "first_name   varchar", "last_name    varchar", "email        text?", "+ legacy_field"],
    },
    {
      id: "api",  tag: "C", code: "openapi",       title: "openapi",
      role: "aligned",   policy: "exact",
      rows: ["customerId   string", "firstName    string",  "lastName     string",  "email        string?"],
    },
    {
      id: "kafka",tag: "D", code: "kafka / avro",  title: "kafka",
      role: "aligned",   policy: "subset",
      rows: ["customerId   string", "firstName    string",  "lastName     string"],
    },
  ];

  const W = 1200, H = 720;
  // Domain card position
  const dx = 70, dy = 175, dw = 320, dh = 360;
  // Anchor points on the right edge of domain card, one per field
  const anchorX = dx + dw;
  const anchorYs = fields.map((_, i) => dy + 92 + i * 56);

  // Station card layout: 2x2 grid on the right
  const sx0 = 730, sy0 = 100, sw = 380, sh = 240, sgapX = 0, sgapY = 40;
  const stationPositions = [
    { x: sx0, y: sy0 },
    { x: sx0, y: sy0 + sh + sgapY },
  ];
  const stationsLeft  = [stations[0], stations[2]]; // postgres top, openapi bottom — actually let's lay 2 rows × 2 cols
  // But we have 4 stations and limited width → use 2 rows × 2 cols
  const stationGrid = [
    { ...stations[0], x: 770,  y: 60 },
    { ...stations[1], x: 770,  y: 320 },
    { ...stations[2], x: 770 + 200, y: 60 + 30 }, // not used (we'll do single column)
  ];

  // Simpler: single column of 4 stations on the right
  const colX = 760;
  const cellW = 380;
  const cellH = 145;
  const cellGap = 12;
  const colY0 = 60;
  const positionedStations = stations.map((s, i) => ({
    ...s,
    x: colX,
    y: colY0 + i * (cellH + cellGap),
    cy: colY0 + i * (cellH + cellGap) + cellH / 2,
  }));

  return (
    <figure className={styles.diagramFigure}>
      <div className={styles.diagramScroll}>
        <svg
          className={styles.diagram}
          viewBox={`0 0 ${W} ${H}`}
          xmlns="http://www.w3.org/2000/svg"
          role="img"
          aria-label="Customer domain type aligned across postgres, mariadb, openapi and kafka boundaries."
        >
          <defs>
            <pattern id="bp-grid" width="24" height="24" patternUnits="userSpaceOnUse">
              <path d="M 24 0 L 0 0 0 24" fill="none" stroke="currentColor" strokeWidth="0.4" opacity="0.16" />
            </pattern>
            <pattern id="bp-grid-major" width="120" height="120" patternUnits="userSpaceOnUse">
              <path d="M 120 0 L 0 0 0 120" fill="none" stroke="currentColor" strokeWidth="0.6" opacity="0.32" />
            </pattern>
            <pattern id="bp-hatch" width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
              <line x1="0" y1="0" x2="0" y2="6" stroke="currentColor" strokeWidth="0.8" opacity="0.35" />
            </pattern>
            <marker id="arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto" markerUnits="strokeWidth">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="currentColor" />
            </marker>
            <marker id="dot" viewBox="0 0 6 6" refX="3" refY="3" markerWidth="6" markerHeight="6">
              <circle cx="3" cy="3" r="2" fill="currentColor" />
            </marker>
          </defs>

          {/* === BACKGROUND === */}
          <rect width={W} height={H} fill="url(#bp-grid)" />
          <rect width={W} height={H} fill="url(#bp-grid-major)" opacity="0.6" />

          {/* === DRAFTING FRAME === */}
          <rect x="14" y="14" width={W - 28} height={H - 28} fill="none" stroke="currentColor" strokeWidth="0.8" opacity="0.5" />
          <rect x="22" y="22" width={W - 44} height={H - 44} fill="none" stroke="currentColor" strokeWidth="0.5" opacity="0.32" strokeDasharray="2 4" />

          {/* === EDGE COORDINATE MARKS === */}
          {[1,2,3,4,5,6,7,8].map((i) => (
            <g key={`top-${i}`} opacity="0.6">
              <line x1={i * 130 + 60} y1="14" x2={i * 130 + 60} y2="30" stroke="currentColor" strokeWidth="0.6" />
              <text x={i * 130 + 60} y="46" textAnchor="middle" fontSize="9" className={styles.diagramTick}>{String(i).padStart(2,'0')}</text>
            </g>
          ))}
          {["A","B","C","D","E"].map((c, i) => (
            <g key={`left-${c}`} opacity="0.6">
              <line x1="14" y1={70 + i * 130} x2="30" y2={70 + i * 130} stroke="currentColor" strokeWidth="0.6" />
              <text x="40" y={74 + i * 130} fontSize="10" className={styles.diagramTick}>{c}</text>
            </g>
          ))}

          {/* === REGISTER MARKS (corner crosshairs) === */}
          {[
            [40, 40], [W - 40, 40], [40, H - 40], [W - 40, H - 40],
          ].map(([cx, cy], i) => (
            <g key={`reg-${i}`} opacity="0.65">
              <circle cx={cx} cy={cy} r="6" fill="none" stroke="currentColor" strokeWidth="0.7" />
              <line x1={cx - 12} y1={cy} x2={cx + 12} y2={cy} stroke="currentColor" strokeWidth="0.7" />
              <line x1={cx} y1={cy - 12} x2={cx} y2={cy + 12} stroke="currentColor" strokeWidth="0.7" />
            </g>
          ))}

          {/* === SECTION TITLES === */}
          <text x="70" y="100" fontSize="11" className={styles.diagramSectionLabel}>SECTION A — DOMAIN</text>
          <line x1="70" y1="108" x2="390" y2="108" stroke="currentColor" strokeWidth="0.8" opacity="0.7" />
          <text x="380" y="100" textAnchor="end" fontSize="10" className={styles.diagramTick}>fig. 01.A</text>

          <text x="760" y="40" fontSize="11" className={styles.diagramSectionLabel}>SECTION B — BOUNDARIES</text>
          <line x1="760" y1="48" x2="1140" y2="48" stroke="currentColor" strokeWidth="0.8" opacity="0.7" />
          <text x="1130" y="40" textAnchor="end" fontSize="10" className={styles.diagramTick}>fig. 01.B</text>

          {/* === DOMAIN CARD === */}
          <g>
            {/* Drop shadow / offset */}
            <rect x={dx + 4} y={dy + 4} width={dw} height={dh} fill="currentColor" opacity="0.06" />
            {/* Card body */}
            <rect x={dx} y={dy} width={dw} height={dh} fill="var(--paper, #f4f0e6)" stroke="currentColor" strokeWidth="1.6" />
            {/* Header band */}
            <rect x={dx} y={dy} width={dw} height={70} fill="currentColor" />
            <text x={dx + 16} y={dy + 28} fontSize="10" className={styles.diagramHeaderTag} letterSpacing="2">DOMAIN TYPE</text>
            <text x={dx + 16} y={dy + 58} fontSize="32" className={styles.diagramHeaderTitle} fontWeight="500" letterSpacing="-1">Customer</text>
            <text x={dx + dw - 16} y={dy + 28} textAnchor="end" fontSize="9" className={styles.diagramHeaderTag} letterSpacing="2">canonical</text>

            {/* Field rows */}
            {fields.map((f, i) => {
              const yMid = dy + 92 + i * 56;
              return (
                <g key={f.name}>
                  {i > 0 && (
                    <line x1={dx + 16} y1={yMid - 28} x2={dx + dw - 16} y2={yMid - 28} stroke="currentColor" strokeWidth="0.4" opacity="0.35" strokeDasharray="2 3" />
                  )}
                  <text x={dx + 16} y={yMid - 6} fontSize="9" className={styles.diagramTick} letterSpacing="2">{f.i}</text>
                  <text x={dx + 16} y={yMid + 12} fontSize="14" className={styles.diagramFieldName} fontWeight="500">{f.name}</text>
                  <text x={dx + dw - 16} y={yMid + 12} textAnchor="end" fontSize="13" className={styles.diagramFieldType}>{f.type}</text>
                  {/* Pin on right edge */}
                  <line x1={dx + dw - 4} y1={yMid + 6} x2={dx + dw + 8} y2={yMid + 6} stroke="currentColor" strokeWidth="0.8" />
                  <circle cx={dx + dw + 8} cy={yMid + 6} r="2.5" fill="currentColor" />
                </g>
              );
            })}
          </g>

          {/* === DIMENSIONAL CALLOUT === */}
          <g opacity="0.7">
            <line x1={dx - 24} y1={dy} x2={dx - 24} y2={dy + dh} stroke="currentColor" strokeWidth="0.6" />
            <line x1={dx - 28} y1={dy} x2={dx - 20} y2={dy} stroke="currentColor" strokeWidth="0.6" />
            <line x1={dx - 28} y1={dy + dh} x2={dx - 20} y2={dy + dh} stroke="currentColor" strokeWidth="0.6" />
            <text x={dx - 30} y={dy + dh / 2 + 4} textAnchor="end" fontSize="9" className={styles.diagramTick} fontFamily="JetBrains Mono">{fields.length} fields</text>
          </g>

          {/* === STATIONS (single column right) === */}
          {positionedStations.map((s, idx) => {
            const cy = s.cy;
            return (
              <g key={s.id}>
                {/* Card */}
                <rect x={s.x + 3} y={s.y + 3} width={cellW} height={cellH} fill="currentColor" opacity="0.06" />
                <rect x={s.x} y={s.y} width={cellW} height={cellH} fill="var(--paper, #f4f0e6)" stroke="currentColor" strokeWidth="1.2" />

                {/* Tag corner */}
                <rect x={s.x} y={s.y} width="36" height="36" fill="currentColor" />
                <text x={s.x + 18} y={s.y + 25} textAnchor="middle" fontSize="16" className={styles.diagramHeaderTitle} fontWeight="500">{s.tag}</text>

                {/* Title row */}
                <text x={s.x + 50} y={s.y + 22} fontSize="9" className={styles.diagramTick} letterSpacing="2">BOUNDARY · {s.role.toUpperCase()}</text>
                <text x={s.x + 50} y={s.y + 42} fontSize="18" className={styles.diagramFieldName} fontWeight="500" letterSpacing="-0.5">{s.title}</text>

                {/* Policy chip — top right */}
                <g>
                  <rect x={s.x + cellW - 110} y={s.y + 12} width="98" height="22" fill="none" stroke="currentColor" strokeWidth="0.8" />
                  <text x={s.x + cellW - 61} y={s.y + 27} textAnchor="middle" fontSize="9" className={styles.diagramTick} letterSpacing="2">{s.policy.toUpperCase()}</text>
                </g>

                {/* Rule */}
                <line x1={s.x + 12} y1={s.y + 56} x2={s.x + cellW - 12} y2={s.y + 56} stroke="currentColor" strokeWidth="0.6" opacity="0.45" />

                {/* Code rows */}
                {s.rows.slice(0, 4).map((row, ri) => (
                  <text key={ri} x={s.x + 18} y={s.y + 76 + ri * 16} fontSize="11" className={styles.diagramCodeRow} fontFamily="JetBrains Mono">{row}</text>
                ))}

                {/* Pin on left edge */}
                <line x1={s.x - 8} y1={cy} x2={s.x + 4} y2={cy} stroke="currentColor" strokeWidth="0.8" />
                <circle cx={s.x - 8} cy={cy} r="2.5" fill="currentColor" />

                {/* Connection from domain to station */}
                {(() => {
                  // Connect from each domain anchor (4 lines fanning out) to station center
                  const fromX = anchorX + 8;
                  const toX   = s.x - 8;
                  const midX  = fromX + (toX - fromX) * 0.5;
                  return anchorYs.map((ay, i) => {
                    const dropAt = ay - cy;
                    // Use orthogonal step routing
                    const path = `M ${fromX} ${ay} L ${midX - 20 + i * 6} ${ay} L ${midX - 20 + i * 6} ${cy + (i - 1.5) * 8} L ${toX} ${cy + (i - 1.5) * 8}`;
                    return (
                      <path
                        key={`c-${s.id}-${i}`}
                        d={path}
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="0.6"
                        opacity={s.policy === "anchor" ? 0.8 : 0.35}
                        strokeDasharray={s.policy === "subset" ? "4 3" : (s.policy === "superset" ? "1 0" : "1 0")}
                        markerEnd="url(#arr)"
                      />
                    );
                  });
                })()}
              </g>
            );
          })}

          {/* === ANCHOR HIGHLIGHT — ring around primary connection === */}
          <circle cx={positionedStations[0].x - 8} cy={positionedStations[0].cy} r="10" fill="none" stroke="currentColor" strokeWidth="0.8" opacity="0.5" />
          <text x={positionedStations[0].x - 26} y={positionedStations[0].cy - 16} fontSize="9" className={styles.diagramAnnotation} letterSpacing="1.5">PRIMARY</text>

          {/* === LEGEND === */}
          <g transform={`translate(70, ${dy + dh + 40})`}>
            <text x="0" y="0" fontSize="10" className={styles.diagramSectionLabel} letterSpacing="2">LEGEND</text>
            <line x1="0" y1="10" x2="320" y2="10" stroke="currentColor" strokeWidth="0.8" opacity="0.5" />
            <g transform="translate(0, 30)">
              <line x1="0" y1="0" x2="40" y2="0" stroke="currentColor" strokeWidth="0.8" opacity="0.85" markerEnd="url(#arr)" />
              <text x="50" y="4" fontSize="11" className={styles.diagramLegend}>anchor — primary source</text>
            </g>
            <g transform="translate(0, 50)">
              <line x1="0" y1="0" x2="40" y2="0" stroke="currentColor" strokeWidth="0.8" opacity="0.45" markerEnd="url(#arr)" />
              <text x="50" y="4" fontSize="11" className={styles.diagramLegend}>exact / superset alignment</text>
            </g>
            <g transform="translate(0, 70)">
              <line x1="0" y1="0" x2="40" y2="0" stroke="currentColor" strokeWidth="0.8" opacity="0.45" strokeDasharray="4 3" markerEnd="url(#arr)" />
              <text x="50" y="4" fontSize="11" className={styles.diagramLegend}>subset alignment (partial)</text>
            </g>
          </g>

          {/* === TITLE BLOCK (bottom right) === */}
          <g transform={`translate(${W - 290}, ${H - 130})`}>
            <rect x="0" y="0" width="270" height="110" fill="var(--paper, #f4f0e6)" stroke="currentColor" strokeWidth="1" />
            <line x1="0" y1="32" x2="270" y2="32" stroke="currentColor" strokeWidth="0.6" />
            <line x1="180" y1="32" x2="180" y2="110" stroke="currentColor" strokeWidth="0.6" />
            <line x1="0" y1="71" x2="180" y2="71" stroke="currentColor" strokeWidth="0.6" />

            <text x="12" y="22" fontSize="14" className={styles.diagramHeaderTitle} fontWeight="500">UNIFIED TYPE / CUSTOMER</text>

            <text x="12" y="50" fontSize="8" className={styles.diagramTick} letterSpacing="2">DRAWING NO.</text>
            <text x="12" y="64" fontSize="12" className={styles.diagramFieldName}>typr.fig.01</text>

            <text x="12" y="89" fontSize="8" className={styles.diagramTick} letterSpacing="2">REVISION</text>
            <text x="12" y="103" fontSize="12" className={styles.diagramFieldName}>RC6 / 2026.05</text>

            <text x="192" y="50" fontSize="8" className={styles.diagramTick} letterSpacing="2">SCALE</text>
            <text x="192" y="64" fontSize="12" className={styles.diagramFieldName}>1 : 1</text>

            <text x="192" y="89" fontSize="8" className={styles.diagramTick} letterSpacing="2">SHEET</text>
            <text x="192" y="103" fontSize="12" className={styles.diagramFieldName}>01 / 01</text>
          </g>

          {/* === ANIMATED FLOW DOT — primary anchor === */}
          <circle r="3" fill="var(--vermillion, #d44324)">
            <animateMotion
              dur="3.2s"
              repeatCount="indefinite"
              path={`M ${anchorX + 8} ${anchorYs[0]} L ${positionedStations[0].x - 8} ${positionedStations[0].cy}`}
            />
          </circle>
        </svg>
      </div>

      <figcaption className={styles.diagramCaption}>
        <span className={styles.captionMark}>fig. 01</span>
        <span>
          One declaration of <code>Customer</code>, four boundaries, four
          alignment policies. The anchor (<strong>postgres</strong>) defines the
          shape; the others align — exact, superset, or subset — and Typr
          generates the mappers between them.
        </span>
      </figcaption>
    </figure>
  );
}

function ComparisonSection() {
  return (
    <section className={`${styles.section} ${styles.sectionDark}`}>
      <div className={`container ${styles.sectionContainer}`}>
        <SectionLabel label="The same bug, twice" inverted />

        <div className={styles.comparisonHeader}>
          <h2 className={styles.bigHeadline}>
            One ships. <em>One never compiles.</em>
          </h2>
          <p className={`${styles.lede} ${styles.ledeOnDark}`}>
            Same feature. Same domain. Different consequences.
          </p>
        </div>

        <div className={styles.comparisonGrid}>
          <div className={styles.comparisonColumn}>
            <header className={styles.comparisonHead}>
              <span className={styles.comparisonTag} data-variant="bad">
                without typr
              </span>
              <h3 className={styles.comparisonTitle}>Long, Long, Long.</h3>
              <p className={styles.comparisonNote}>
                Every ID is a Long. Every name is a String. Mix them up
                and the compiler waves you through.
              </p>
            </header>
            <CodeTabs code={CODE.without} />
          </div>

          <div className={styles.comparisonDivider} aria-hidden="true">
            <span>vs</span>
          </div>

          <div className={styles.comparisonColumn}>
            <header className={styles.comparisonHead}>
              <span className={styles.comparisonTag} data-variant="good">
                with typr
              </span>
              <h3 className={styles.comparisonTitle}>UserId is not OrderId.</h3>
              <p className={styles.comparisonNote}>
                Generated distinct types. The compiler refuses
                the bug before it leaves your editor.
              </p>
            </header>
            <CodeTabs code={CODE.withTypr} />
          </div>
        </div>
      </div>
    </section>
  );
}

function GuardrailsSection() {
  const cards = [
    {
      kind: "01",
      who: "Junior developers",
      claim: "Productive on day one.",
      body: "Don't need the whole‑system map in their head. The types tell them what's possible — wrong ID type? wrong response shape? Won't compile.",
    },
    {
      kind: "02",
      who: "Contractors",
      claim: "Limited blast radius.",
      body: "Implement against a typed interface. They cannot accidentally break what they cannot touch. Safe delegation by construction.",
    },
    {
      kind: "03",
      who: "AI agents",
      claim: "Constrained by the compiler.",
      body: "Every type error is instant feedback. Fix, compile, fix, compile — tight loops save tokens and time. Guardrails outperform prompts.",
    },
    {
      kind: "04",
      who: "Tech leads",
      claim: "Review contracts, not plumbing.",
      body: "Approve schema and API changes — the implementation has to follow the types. High‑leverage decisions, low‑drag review.",
    },
  ];

  return (
    <section className={styles.section}>
      <div className={`container ${styles.sectionContainer}`}>
        <SectionLabel label="Guardrails" />

        <div className={styles.guardrailsHeader}>
          <h2 className={styles.bigHeadline}>
            The less context they have, the more the compiler matters.
          </h2>
          <p className={styles.lede}>
            New hires, contractors, AI agents — they don't carry your
            mental model. Typr gives them a working one, enforced.
          </p>
        </div>

        <div className={styles.guardrailsGrid}>
          {cards.map((c) => (
            <article className={styles.guardrailCard} key={c.kind}>
              <div className={styles.guardrailIndex}>{c.kind}</div>
              <h3 className={styles.guardrailWho}>{c.who}</h3>
              <p className={styles.guardrailClaim}>{c.claim}</p>
              <p className={styles.guardrailBody}>{c.body}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

function PrecisionSection() {
  const dbRows = [
    ["CHAR(50)", "String", "PaddedString50"],
    ["VARCHAR(100)", "String", "VarcharMax100"],
    ["users.id (PK)", "Long", "UserId"],
    ["orders.user_id (FK)", "Long", "UserId"],
    ["DEFAULT NOW()", "Timestamp", "Defaulted<OffsetDateTime>"],
    ["UUID[]", "—", "Array<UUID>"],
    ["composite type", "—", "record / data class"],
  ];

  const apiRows = [
    ["200 / 404 responses", "Object", "sealed ADT"],
    ["userId path param", "String", "UserId"],
    ["nullable: true", "T", "Optional<T> / T?"],
    ["enum: [a, b, c]", "String", "MyEnum"],
    ["Server interface", "loose types", "exact contract"],
  ];

  return (
    <section className={`${styles.section} ${styles.sectionPaper}`}>
      <div className={`container ${styles.sectionContainer}`}>
        <SectionLabel label="Precision specifications" />

        <div className={styles.precisionHeader}>
          <h2 className={styles.bigHeadline}>
            <em>Precision</em>, not approximation.
          </h2>
          <p className={styles.lede}>
            Most generators flatten your contracts into the lowest common
            primitive. Typr preserves every distinction your schema cared
            enough to make.
          </p>
        </div>

        <div className={styles.specSheet}>
          <div className={styles.specBlock}>
            <div className={styles.specHead}>
              <span className={styles.specTag}>schema source</span>
              <h3 className={styles.specTitle}>Database column</h3>
            </div>
            <table className={styles.specTable}>
              <thead>
                <tr>
                  <th>declared</th>
                  <th>others</th>
                  <th>typr</th>
                </tr>
              </thead>
              <tbody>
                {dbRows.map(([a, b, c]) => (
                  <tr key={a}>
                    <td><code>{a}</code></td>
                    <td className={styles.specWeak}>{b === "—" ? <span>—</span> : <code>{b}</code>}</td>
                    <td className={styles.specStrong}><code>{c}</code></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className={styles.specBlock}>
            <div className={styles.specHead}>
              <span className={styles.specTag}>schema source</span>
              <h3 className={styles.specTitle}>OpenAPI spec</h3>
            </div>
            <table className={styles.specTable}>
              <thead>
                <tr>
                  <th>declared</th>
                  <th>others</th>
                  <th>typr</th>
                </tr>
              </thead>
              <tbody>
                {apiRows.map(([a, b, c]) => (
                  <tr key={a}>
                    <td>{a.includes("(") || a.includes(":") || a.includes("nullable") ? <code>{a}</code> : a}</td>
                    <td className={styles.specWeak}><code>{b}</code></td>
                    <td className={styles.specStrong}><code>{c}</code></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <aside className={styles.specCallout}>
          <div className={styles.specCalloutMark}>note.</div>
          <div className={styles.specCalloutBody}>
            <p>
              Code written against Typr's generated types <em>cannot</em>: pass
              <code> null</code> where the schema rejects it, mix
              <code> userId</code> with <code>orderId</code>, miss a
              declared response case, return a 200 where the spec says
              201, or pass a <code>String</code> where a <code>UUID</code> is
              required.
            </p>
            <p className={styles.specCalloutPunch}>
              The compiler catches it. Before review. Before runtime. Before
              production.
            </p>
          </div>
        </aside>
      </div>
    </section>
  );
}

function BoundariesSection() {
  const boundaries = [
    {
      n: "01",
      title: "Databases",
      tagline: "Schemas in. Repos out.",
      desc: "Six engines: PostgreSQL, MariaDB, Oracle, SQL Server, DuckDB, DB2. Row types, ID types, repositories, type‑safe DSL queries. Full DDL fidelity — composite types, arrays, enums, domains, defaults.",
      tags: ["6 engines", "DDL fidelity", "Type‑safe DSL"],
      to: "/typr/boundaries/databases/",
    },
    {
      n: "02",
      title: "REST APIs",
      tagline: "OpenAPI as a contract.",
      desc: "Sealed response types. Server stubs and client stubs that share the same interface. Same UserId in your handler as in your database row.",
      tags: ["Sealed responses", "Server + client", "Shared types"],
      to: "/typr/boundaries/apis/",
    },
    {
      n: "03",
      title: "Events · Avro/Kafka",
      tagline: "Topics as types.",
      desc: "Avro schemas in, typed producers and consumers out. Multi‑event topics with sealed interfaces. Built‑in Kafka RPC support — Spring, Quarkus, Cats Effect.",
      tags: ["Avro codegen", "Typed producers", "Multi‑event topics"],
      to: "/typr/boundaries/events/",
    },
    {
      n: "04",
      title: "gRPC · Protobuf",
      tagline: "Proto as a domain.",
      desc: "Proto definitions map to your domain types with bidirectional, type‑safe converters. Enforced compatibility policies between proto and Java/Kotlin/Scala.",
      tags: ["Proto codegen", "Bidirectional", "Type policies"],
      coming: true,
    },
  ];

  return (
    <section className={styles.section}>
      <div className={`container ${styles.sectionContainer}`}>
        <SectionLabel label="Boundaries" />

        <div className={styles.boundariesHeader}>
          <h2 className={styles.bigHeadline}>
            Every boundary, one type system.
          </h2>
          <p className={styles.lede}>
            Add a second boundary and Typr validates that shared types
            stay consistent. Add a third and the validation compounds.
          </p>
        </div>

        <ol className={styles.boundariesList}>
          {boundaries.map((b) => (
            <li key={b.n} className={styles.boundaryRow}>
              <div className={styles.boundaryIndex}>
                <span className={styles.boundaryNumber}>{b.n}</span>
                <span className={styles.boundaryDot} aria-hidden="true">●</span>
              </div>
              <div className={styles.boundaryBody}>
                <div className={styles.boundaryHead}>
                  <h3 className={styles.boundaryTitle}>{b.title}</h3>
                  <span className={styles.boundaryTagline}>{b.tagline}</span>
                </div>
                <p className={styles.boundaryDesc}>{b.desc}</p>
                <div className={styles.boundaryFooter}>
                  <ul className={styles.boundaryTags}>
                    {b.tags.map((t) => (
                      <li key={t}>{t}</li>
                    ))}
                  </ul>
                  {b.coming ? (
                    <span className={styles.boundaryComing}>coming soon</span>
                  ) : (
                    <Link className={styles.boundaryLink} to={b.to}>
                      explore <span aria-hidden="true">→</span>
                    </Link>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}

function UnifiedTypesSection() {
  return (
    <section className={`${styles.section} ${styles.sectionFeature}`}>
      <div className={`container ${styles.sectionContainer}`}>
        <SectionLabel label="Unified types" inverted />

        <div className={styles.unifiedLayout}>
          <div className={styles.unifiedText}>
            <h2 className={`${styles.bigHeadline} ${styles.headlineOnDark}`}>
              Define <em>Customer</em> once. Use it everywhere.
            </h2>
            <p className={`${styles.lede} ${styles.ledeOnDark}`}>
              Declare a domain type, anchor it to a primary boundary, and
              align other sources to it. Typr validates compatibility and
              generates every mapper.
            </p>

            <ul className={styles.unifiedFeatures}>
              <li>
                <strong>Flow declarations</strong>
                <span>forward, drop, merge, split, computed</span>
              </li>
              <li>
                <strong>Type policies</strong>
                <span>exact · widen · narrow · nullable rules</span>
              </li>
              <li>
                <strong>Auto mappers</strong>
                <span>fromX(...) and toX(...) per boundary</span>
              </li>
              <li>
                <strong>CI validation</strong>
                <span><code>typr check</code> — fails on schema drift</span>
              </li>
            </ul>

            <Link className={styles.btnPrimaryInverted} to="/typr/unified-types/">
              Read the Unified Types spec <span className={styles.btnArrow} aria-hidden="true">→</span>
            </Link>
          </div>

          <CodeBlock language="yaml" title="typr.yaml">{CODE.bridgeConfig}</CodeBlock>
        </div>
      </div>
    </section>
  );
}

function StackSection() {
  const groups = [
    {
      title: "Boundaries",
      items: [
        "PostgreSQL", "MariaDB", "Oracle", "SQL Server", "DuckDB", "DB2",
        "OpenAPI", "Kafka / Avro",
      ],
      soon: ["gRPC / Protobuf", "GraphQL"],
    },
    {
      title: "Languages",
      items: ["Java 17+", "Kotlin", "Scala 2", "Scala 3"],
      soon: [],
    },
    {
      title: "Frameworks",
      items: ["Spring Boot", "Quarkus", "Http4s", "JAX‑RS", "Cats Effect"],
      soon: [],
    },
  ];

  return (
    <section className={styles.section}>
      <div className={`container ${styles.sectionContainer}`}>
        <SectionLabel label="Compatibility" />

        <h2 className={styles.bigHeadline}>Works with your stack.</h2>

        <div className={styles.stackTable}>
          {groups.map((g) => (
            <div className={styles.stackColumn} key={g.title}>
              <div className={styles.stackHead}>
                <h3 className={styles.stackTitle}>{g.title}</h3>
              </div>
              <ul className={styles.stackList}>
                {g.items.map((it) => (
                  <li key={it}>
                    <span className={styles.stackBullet} aria-hidden="true">·</span>
                    {it}
                  </li>
                ))}
                {g.soon.map((it) => (
                  <li key={it} className={styles.stackSoon}>
                    <span className={styles.stackBullet} aria-hidden="true">·</span>
                    {it}
                    <span className={styles.stackSoonLabel}>soon</span>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function ColophonSection() {
  return (
    <section className={`${styles.section} ${styles.sectionColophon}`}>
      <div className={`container ${styles.sectionContainer}`}>
        <SectionLabel label="Begin" />

        <div className={styles.colophonGrid}>
          <div className={styles.colophonText}>
            <h2 className={styles.colophonHeadline}>
              Define your domain.<br />
              See the flow.<br />
              <em>Ship with confidence.</em>
            </h2>
            <p className={styles.lede}>
              Point typr at your schemas. Get back a typed surface for every
              boundary. Let the compiler do the work that used to live in
              your head.
            </p>
            <div className={styles.colophonActions}>
              <Link className={styles.btnPrimary} to="/typr/getting-started">
                Get started <span className={styles.btnArrow} aria-hidden="true">→</span>
              </Link>
              <Link className={styles.btnGhost} to="/typr/">
                Browse the docs
              </Link>
            </div>
          </div>

          <aside className={styles.colophonMeta}>
            <dl>
              <div>
                <dt>set in</dt>
                <dd>Fraunces · Bricolage Grotesque · JetBrains Mono</dd>
              </div>
              <div>
                <dt>license</dt>
                <dd>Business Source 1.1</dd>
              </div>
              <div>
                <dt>repo</dt>
                <dd><a href="https://github.com/oyvindberg/typr">github.com/oyvindberg/typr</a></dd>
              </div>
            </dl>
          </aside>
        </div>
      </div>
    </section>
  );
}

// =============================================================================
// PAGE
// =============================================================================

export default function Home() {
  return (
    <Layout
      title="Typr — type‑safe code generation for every JVM boundary"
      description="Generate type‑safe Java, Kotlin and Scala code from databases, OpenAPI, Avro and gRPC. One unified domain type, validated across every boundary."
    >
      <div className={styles.page}>
        <HeroSection />
        <main>
          <ManifestoSection />
          <ComparisonSection />
          <GuardrailsSection />
          <PrecisionSection />
          <BoundariesSection />
          <UnifiedTypesSection />
          <StackSection />
          <ColophonSection />
        </main>
      </div>
    </Layout>
  );
}
