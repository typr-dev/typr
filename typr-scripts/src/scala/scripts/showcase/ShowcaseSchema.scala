package scripts.showcase

import typr.{db, DbType, NonEmptyList, Nullability}
import typr.db.{Col, ColName, ForeignKey, PrimaryKey, RelationName, Table, UniqueKey, View}
import typr.internal.analysis.{DecomposedSql, ParsedName}
import typr.internal.DebugJson

/** Defines the showcase schema programmatically using db.Table and db.View.
  *
  * Pattern matches on DbType to produce database-specific types and columns.
  */
object ShowcaseSchema {

  def schema(dbType: DbType): String = "showcase"

  def relations(dbType: DbType): List[db.Relation] = tables(dbType) ++ views(dbType)

  // ═══════════════════════════════════════════════════════════════════════════
  // Domain definitions (PostgreSQL only)
  // ═══════════════════════════════════════════════════════════════════════════

  /** PostgreSQL domains for showcase */
  def domains(dbType: DbType): List[db.Domain] = dbType match {
    case DbType.PostgreSQL =>
      List(
        db.Domain(
          name = RelationName(Some("showcase"), "email_address"),
          tpe = db.PgType.VarChar(Some(255)),
          originalType = "character varying(255)",
          isNotNull = Nullability.NoNulls,
          hasDefault = false,
          constraintDefinition = Some("CHECK ((VALUE)::text ~~ '%@%.%'::text)")
        ),
        db.Domain(
          name = RelationName(Some("showcase"), "positive_amount"),
          tpe = db.PgType.Numeric,
          originalType = "numeric",
          isNotNull = Nullability.NoNulls,
          hasDefault = false,
          constraintDefinition = Some("CHECK (VALUE > 0)")
        ),
        db.Domain(
          name = RelationName(Some("showcase"), "phone_number"),
          tpe = db.PgType.VarChar(Some(20)),
          originalType = "character varying(20)",
          isNotNull = Nullability.Nullable,
          hasDefault = false,
          constraintDefinition = Some("CHECK ((VALUE)::text ~ '^[+]?[0-9\\-\\s]+$'::text)")
        ),
        db.Domain(
          name = RelationName(Some("showcase"), "percentage"),
          tpe = db.PgType.Numeric,
          originalType = "numeric",
          isNotNull = Nullability.NoNulls,
          hasDefault = false,
          constraintDefinition = Some("CHECK (VALUE >= 0 AND VALUE <= 100)")
        )
      )
    case DbType.SqlServer =>
      // SQL Server uses db.Domain for alias types (CREATE TYPE ... FROM base_type)
      List(
        db.Domain(
          name = RelationName(Some("showcase"), "EmailAddress"),
          tpe = db.SqlServerType.NVarChar(Some(255)),
          originalType = "nvarchar(255)",
          isNotNull = Nullability.NoNulls,
          hasDefault = false,
          constraintDefinition = None
        ),
        db.Domain(
          name = RelationName(Some("showcase"), "PositiveAmount"),
          tpe = db.SqlServerType.Decimal(Some(10), Some(2)),
          originalType = "decimal(10,2)",
          isNotNull = Nullability.NoNulls,
          hasDefault = false,
          constraintDefinition = None
        ),
        db.Domain(
          name = RelationName(Some("showcase"), "PhoneNumber"),
          tpe = db.SqlServerType.NVarChar(Some(20)),
          originalType = "nvarchar(20)",
          isNotNull = Nullability.Nullable,
          hasDefault = false,
          constraintDefinition = None
        ),
        db.Domain(
          name = RelationName(Some("showcase"), "Percentage"),
          tpe = db.SqlServerType.Decimal(Some(5), Some(2)),
          originalType = "decimal(5,2)",
          isNotNull = Nullability.NoNulls,
          hasDefault = false,
          constraintDefinition = None
        )
      )
    case DbType.DB2 =>
      // DB2 uses DISTINCT TYPE (CREATE DISTINCT TYPE)
      List(
        db.Domain(
          name = RelationName(Some("SHOWCASE"), "EMAIL_ADDRESS"),
          tpe = db.DB2Type.VarChar(Some(255)),
          originalType = "VARCHAR(255)",
          isNotNull = Nullability.NoNulls,
          hasDefault = false,
          constraintDefinition = None
        ),
        db.Domain(
          name = RelationName(Some("SHOWCASE"), "POSITIVE_AMOUNT"),
          tpe = db.DB2Type.Decimal(Some(10), Some(2)),
          originalType = "DECIMAL(10,2)",
          isNotNull = Nullability.NoNulls,
          hasDefault = false,
          constraintDefinition = None
        ),
        db.Domain(
          name = RelationName(Some("SHOWCASE"), "PHONE_NUMBER"),
          tpe = db.DB2Type.VarChar(Some(20)),
          originalType = "VARCHAR(20)",
          isNotNull = Nullability.Nullable,
          hasDefault = false,
          constraintDefinition = None
        ),
        db.Domain(
          name = RelationName(Some("SHOWCASE"), "PERCENTAGE"),
          tpe = db.DB2Type.Decimal(Some(5), Some(2)),
          originalType = "DECIMAL(5,2)",
          isNotNull = Nullability.NoNulls,
          hasDefault = false,
          constraintDefinition = None
        )
      )
    case _ => Nil
  }

  def tables(dbType: DbType): List[Table] = {
    val base = List(
      title(dbType), // Open enum lookup table - must come first as it's referenced by employee
      company(dbType),
      department(dbType),
      employee(dbType),
      category(dbType),
      product(dbType),
      customer(dbType),
      address(dbType),
      customerOrder(dbType),
      orderItem(dbType),
      project(dbType),
      projectAssignment(dbType),
      auditLog(dbType)
    )

    // Add database-specific tables
    val dbSpecific = dbType match {
      case DbType.DuckDB => duckDbTables(dbType)
      case _             => Nil
    }

    base ++ dbSpecific
  }

  // Enum definitions - used both for registration and column type references
  val orderStatusEnum = db.StringEnum(RelationName(Some("showcase"), "order_status"), NonEmptyList("pending", List("confirmed", "processing", "shipped", "delivered", "cancelled", "refunded")))
  val projectStatusEnum = db.StringEnum(RelationName(Some("showcase"), "project_status"), NonEmptyList("planning", List("active", "on_hold", "completed", "cancelled")))
  val addressTypeEnum = db.StringEnum(RelationName(Some("showcase"), "address_type"), NonEmptyList("billing", List("shipping", "both")))
  val priorityLevelEnum = db.StringEnum(RelationName(Some("showcase"), "priority_level"), NonEmptyList("low", List("medium", "high", "critical")))
  val employeeStatusEnum = db.StringEnum(RelationName(Some("showcase"), "employee_status"), NonEmptyList("active", List("on_leave", "terminated")))

  // Open enum values - used for code generation
  val titleOpenEnumValues: NonEmptyList[String] = NonEmptyList("mr", List("ms", "dr", "phd"))

  def enums(dbType: DbType): List[db.StringEnum] = dbType match {
    case DbType.PostgreSQL | DbType.DuckDB =>
      List(orderStatusEnum, projectStatusEnum, addressTypeEnum, priorityLevelEnum, employeeStatusEnum)
    case _ => Nil
  }

  // Type helpers for enums by database
  private def orderStatusType(dbType: DbType): db.Type = dbType match {
    case DbType.PostgreSQL => db.PgType.EnumRef(orderStatusEnum)
    case DbType.DuckDB     => db.DuckDbType.Enum("order_status", orderStatusEnum.values.toList)
    case DbType.MariaDB    => db.MariaType.Enum(orderStatusEnum.values.toList)
    case _                 => varcharType(dbType, Some(20))
  }

  private def projectStatusType(dbType: DbType): db.Type = dbType match {
    case DbType.PostgreSQL => db.PgType.EnumRef(projectStatusEnum)
    case DbType.DuckDB     => db.DuckDbType.Enum("project_status", projectStatusEnum.values.toList)
    case DbType.MariaDB    => db.MariaType.Enum(projectStatusEnum.values.toList)
    case _                 => varcharType(dbType, Some(20))
  }

  private def addressTypeType(dbType: DbType): db.Type = dbType match {
    case DbType.PostgreSQL => db.PgType.EnumRef(addressTypeEnum)
    case DbType.DuckDB     => db.DuckDbType.Enum("address_type", addressTypeEnum.values.toList)
    case DbType.MariaDB    => db.MariaType.Enum(addressTypeEnum.values.toList)
    case _                 => varcharType(dbType, Some(20))
  }

  private def priorityLevelType(dbType: DbType): db.Type = dbType match {
    case DbType.PostgreSQL => db.PgType.EnumRef(priorityLevelEnum)
    case DbType.DuckDB     => db.DuckDbType.Enum("priority_level", priorityLevelEnum.values.toList)
    case DbType.MariaDB    => db.MariaType.Enum(priorityLevelEnum.values.toList)
    case _                 => varcharType(dbType, Some(20))
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // View definitions
  // ═══════════════════════════════════════════════════════════════════════════

  def views(dbType: DbType): List[View] = {
    val base = List(
      employeeSummaryView(dbType),
      orderSummaryView(dbType),
      productInventoryView(dbType),
      departmentStatsView(dbType)
    )

    // Add materialized views for databases that support them
    val materialized = dbType match {
      case DbType.PostgreSQL | DbType.Oracle => List(monthlySalesMV(dbType))
      case _                                 => Nil
    }

    base ++ materialized
  }

  /** Employee summary view - joins employee with department and company */
  private def employeeSummaryView(dbType: DbType): View = View(
    name = RelationName(Some(schema(dbType)), "employee_summary"),
    comment = Some("Summary view of employees with their department and company information"),
    decomposedSql = DecomposedSql(
      List(
        DecomposedSql.SqlText(
          """SELECT
            |  e.id AS employee_id,
            |  e.email,
            |  e.first_name,
            |  e.last_name,
            |  e.salary,
            |  e.hired_at,
            |  e.active,
            |  d.id AS department_id,
            |  d.name AS department_name,
            |  d.budget AS department_budget,
            |  c.id AS company_id,
            |  c.name AS company_name,
            |  m.id AS manager_id,
            |  m.first_name AS manager_first_name,
            |  m.last_name AS manager_last_name
            |FROM showcase.employee e
            |JOIN showcase.department d ON e.department_id = d.id
            |JOIN showcase.company c ON d.company_id = c.id
            |LEFT JOIN showcase.employee m ON e.manager_id = m.id""".stripMargin
        )
      )
    ),
    cols = NonEmptyList(
      viewCol("employee_id", varcharType(dbType, None), notNull = true),
      List(
        viewCol("email", varcharType(dbType, Some(255)), notNull = true),
        viewCol("first_name", varcharType(dbType, Some(50)), notNull = true),
        viewCol("last_name", varcharType(dbType, Some(50)), notNull = true),
        viewCol("salary", decimalType(dbType, 10, 2), notNull = false),
        viewCol("hired_at", dateType(dbType), notNull = false),
        viewCol("active", boolType(dbType), notNull = false),
        viewCol("department_id", varcharType(dbType, None), notNull = true),
        viewCol("department_name", varcharType(dbType, Some(100)), notNull = true),
        viewCol("department_budget", decimalType(dbType, 12, 2), notNull = false),
        viewCol("company_id", varcharType(dbType, None), notNull = true),
        viewCol("company_name", varcharType(dbType, Some(100)), notNull = true),
        viewCol("manager_id", varcharType(dbType, None), notNull = false),
        viewCol("manager_first_name", varcharType(dbType, Some(50)), notNull = false),
        viewCol("manager_last_name", varcharType(dbType, Some(50)), notNull = false)
      )
    ),
    deps = Map(
      ColName("employee_id") -> List((RelationName(Some(schema(dbType)), "employee"), ColName("id"))),
      ColName("department_id") -> List((RelationName(Some(schema(dbType)), "department"), ColName("id"))),
      ColName("company_id") -> List((RelationName(Some(schema(dbType)), "company"), ColName("id")))
    ),
    isMaterialized = false
  )

  /** Order summary view - aggregates order information */
  private def orderSummaryView(dbType: DbType): View = View(
    name = RelationName(Some(schema(dbType)), "order_summary"),
    comment = Some("Summary view of orders with customer info and item counts"),
    decomposedSql = DecomposedSql(
      List(
        DecomposedSql.SqlText(
          """SELECT
            |  o.id AS order_id,
            |  o.status,
            |  o.total,
            |  o.created_at,
            |  o.shipped_at,
            |  o.delivered_at,
            |  c.id AS customer_id,
            |  c.email AS customer_email,
            |  c.first_name AS customer_first_name,
            |  c.last_name AS customer_last_name,
            |  COUNT(oi.product_id) AS item_count,
            |  SUM(oi.quantity) AS total_quantity
            |FROM showcase.customer_order o
            |JOIN showcase.customer c ON o.customer_id = c.id
            |LEFT JOIN showcase.order_item oi ON o.id = oi.order_id
            |GROUP BY o.id, o.status, o.total, o.created_at, o.shipped_at, o.delivered_at,
            |         c.id, c.email, c.first_name, c.last_name""".stripMargin
        )
      )
    ),
    cols = NonEmptyList(
      viewCol("order_id", varcharType(dbType, None), notNull = true),
      List(
        viewCol("status", varcharType(dbType, Some(20)), notNull = false),
        viewCol("total", decimalType(dbType, 10, 2), notNull = true),
        viewCol("created_at", timestampType(dbType), notNull = false),
        viewCol("shipped_at", timestampType(dbType), notNull = false),
        viewCol("delivered_at", timestampType(dbType), notNull = false),
        viewCol("customer_id", varcharType(dbType, None), notNull = true),
        viewCol("customer_email", varcharType(dbType, Some(255)), notNull = true),
        viewCol("customer_first_name", varcharType(dbType, Some(50)), notNull = true),
        viewCol("customer_last_name", varcharType(dbType, Some(50)), notNull = true),
        viewCol("item_count", bigintType(dbType), notNull = false),
        viewCol("total_quantity", bigintType(dbType), notNull = false)
      )
    ),
    deps = Map(
      ColName("order_id") -> List((RelationName(Some(schema(dbType)), "customer_order"), ColName("id"))),
      ColName("customer_id") -> List((RelationName(Some(schema(dbType)), "customer"), ColName("id")))
    ),
    isMaterialized = false
  )

  /** Product inventory view - shows product stock status */
  private def productInventoryView(dbType: DbType): View = View(
    name = RelationName(Some(schema(dbType)), "product_inventory"),
    comment = Some("Product inventory status with category information"),
    decomposedSql = DecomposedSql(
      List(
        DecomposedSql.SqlText(
          """SELECT
            |  p.id AS product_id,
            |  p.name AS product_name,
            |  p.sku,
            |  p.price,
            |  p.cost,
            |  p.quantity,
            |  p.in_stock,
            |  p.weight,
            |  c.id AS category_id,
            |  c.name AS category_name,
            |  pc.name AS parent_category_name,
            |  CASE WHEN p.quantity > 0 THEN 'In Stock' ELSE 'Out of Stock' END AS stock_status,
            |  p.price - COALESCE(p.cost, 0) AS profit_margin
            |FROM showcase.product p
            |LEFT JOIN showcase.category c ON p.category_id = c.id
            |LEFT JOIN showcase.category pc ON c.parent_id = pc.id""".stripMargin
        )
      )
    ),
    cols = NonEmptyList(
      viewCol("product_id", varcharType(dbType, None), notNull = true),
      List(
        viewCol("product_name", varcharType(dbType, Some(200)), notNull = true),
        viewCol("sku", varcharType(dbType, Some(50)), notNull = true),
        viewCol("price", decimalType(dbType, 10, 2), notNull = true),
        viewCol("cost", decimalType(dbType, 10, 2), notNull = false),
        viewCol("quantity", intType(dbType), notNull = false),
        viewCol("in_stock", boolType(dbType), notNull = false),
        viewCol("weight", decimalType(dbType, 8, 3), notNull = false),
        viewCol("category_id", varcharType(dbType, None), notNull = false),
        viewCol("category_name", varcharType(dbType, Some(100)), notNull = false),
        viewCol("parent_category_name", varcharType(dbType, Some(100)), notNull = false),
        viewCol("stock_status", varcharType(dbType, Some(20)), notNull = false),
        viewCol("profit_margin", decimalType(dbType, 10, 2), notNull = false)
      )
    ),
    deps = Map(
      ColName("product_id") -> List((RelationName(Some(schema(dbType)), "product"), ColName("id"))),
      ColName("category_id") -> List((RelationName(Some(schema(dbType)), "category"), ColName("id")))
    ),
    isMaterialized = false
  )

  /** Department statistics view */
  private def departmentStatsView(dbType: DbType): View = View(
    name = RelationName(Some(schema(dbType)), "department_stats"),
    comment = Some("Department statistics including employee counts and salary info"),
    decomposedSql = DecomposedSql(
      List(
        DecomposedSql.SqlText(
          """SELECT
            |  d.id AS department_id,
            |  d.name AS department_name,
            |  d.budget,
            |  c.id AS company_id,
            |  c.name AS company_name,
            |  COUNT(e.id) AS employee_count,
            |  AVG(e.salary) AS avg_salary,
            |  MIN(e.salary) AS min_salary,
            |  MAX(e.salary) AS max_salary,
            |  SUM(e.salary) AS total_salary
            |FROM showcase.department d
            |JOIN showcase.company c ON d.company_id = c.id
            |LEFT JOIN showcase.employee e ON d.id = e.department_id
            |GROUP BY d.id, d.name, d.budget, c.id, c.name""".stripMargin
        )
      )
    ),
    cols = NonEmptyList(
      viewCol("department_id", varcharType(dbType, None), notNull = true),
      List(
        viewCol("department_name", varcharType(dbType, Some(100)), notNull = true),
        viewCol("budget", decimalType(dbType, 12, 2), notNull = false),
        viewCol("company_id", varcharType(dbType, None), notNull = true),
        viewCol("company_name", varcharType(dbType, Some(100)), notNull = true),
        viewCol("employee_count", bigintType(dbType), notNull = false),
        viewCol("avg_salary", decimalType(dbType, 10, 2), notNull = false),
        viewCol("min_salary", decimalType(dbType, 10, 2), notNull = false),
        viewCol("max_salary", decimalType(dbType, 10, 2), notNull = false),
        viewCol("total_salary", decimalType(dbType, 12, 2), notNull = false)
      )
    ),
    deps = Map(
      ColName("department_id") -> List((RelationName(Some(schema(dbType)), "department"), ColName("id"))),
      ColName("company_id") -> List((RelationName(Some(schema(dbType)), "company"), ColName("id")))
    ),
    isMaterialized = false
  )

  /** Monthly sales materialized view (PostgreSQL and Oracle) */
  private def monthlySalesMV(dbType: DbType): View = View(
    name = RelationName(Some(schema(dbType)), "monthly_sales"),
    comment = Some("Materialized view of monthly sales aggregations"),
    decomposedSql = DecomposedSql(
      List(
        DecomposedSql.SqlText(
          """SELECT
            |  DATE_TRUNC('month', o.created_at) AS month,
            |  COUNT(DISTINCT o.id) AS order_count,
            |  COUNT(DISTINCT o.customer_id) AS unique_customers,
            |  SUM(o.total) AS total_revenue,
            |  AVG(o.total) AS avg_order_value,
            |  SUM(oi.quantity) AS total_items_sold
            |FROM showcase.customer_order o
            |LEFT JOIN showcase.order_item oi ON o.id = oi.order_id
            |GROUP BY DATE_TRUNC('month', o.created_at)""".stripMargin
        )
      )
    ),
    cols = NonEmptyList(
      viewCol("month", timestampType(dbType), notNull = false),
      List(
        viewCol("order_count", bigintType(dbType), notNull = false),
        viewCol("unique_customers", bigintType(dbType), notNull = false),
        viewCol("total_revenue", decimalType(dbType, 12, 2), notNull = false),
        viewCol("avg_order_value", decimalType(dbType, 10, 2), notNull = false),
        viewCol("total_items_sold", bigintType(dbType), notNull = false)
      )
    ),
    deps = Map.empty,
    isMaterialized = true
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // Table definitions
  // ═══════════════════════════════════════════════════════════════════════════

  /** Title lookup table for open enum pattern - works across all databases */
  private def title(dbType: DbType): Table = Table(
    name = RelationName(Some(schema(dbType)), "title"),
    comment = Some("Lookup table for title values (open enum pattern)"),
    cols = NonEmptyList(
      col("code", varcharType(dbType, Some(10)), notNull = true),
      Nil
    ),
    primaryKey = Some(PrimaryKey(NonEmptyList(ColName("code"), Nil), RelationName(None, "title_pkey"))),
    uniqueKeys = Nil,
    foreignKeys = Nil
  )

  private def company(dbType: DbType): Table = Table(
    name = RelationName(Some(schema(dbType)), "company"),
    comment = None,
    cols = NonEmptyList(
      col("id", varcharType(dbType, None), notNull = true),
      List(
        col("name", varcharType(dbType, Some(100)), notNull = true),
        col("founded_year", intType(dbType), notNull = false),
        col("active", boolType(dbType), notNull = false, default = Some("true")),
        col("created_at", timestampType(dbType), notNull = false, default = Some("CURRENT_TIMESTAMP"))
      )
    ),
    primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "company_pkey"))),
    uniqueKeys = Nil,
    foreignKeys = Nil
  )

  private def department(dbType: DbType): Table = Table(
    name = RelationName(Some(schema(dbType)), "department"),
    comment = None,
    cols = NonEmptyList(
      col("id", varcharType(dbType, None), notNull = true),
      List(
        col("company_id", varcharType(dbType, None), notNull = true),
        col("name", varcharType(dbType, Some(100)), notNull = true),
        col("budget", decimalType(dbType, 12, 2), notNull = false)
      )
    ),
    primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "department_pkey"))),
    uniqueKeys = Nil,
    foreignKeys = List(
      ForeignKey(
        NonEmptyList(ColName("company_id"), Nil),
        RelationName(Some(schema(dbType)), "company"),
        NonEmptyList(ColName("id"), Nil),
        RelationName(None, "department_company_fk")
      )
    )
  )

  private def employee(dbType: DbType): Table = {
    // Use domain types for databases that support them
    val (emailType, salaryType, phoneType, bonusPercentageType): (db.Type, db.Type, db.Type, db.Type) = dbType match {
      case DbType.PostgreSQL =>
        (
          db.PgType.DomainRef(RelationName(Some("showcase"), "email_address"), "character varying(255)", db.PgType.VarChar(Some(255))),
          db.PgType.DomainRef(RelationName(Some("showcase"), "positive_amount"), "numeric", db.PgType.Numeric),
          db.PgType.DomainRef(RelationName(Some("showcase"), "phone_number"), "character varying(20)", db.PgType.VarChar(Some(20))),
          db.PgType.DomainRef(RelationName(Some("showcase"), "percentage"), "numeric", db.PgType.Numeric)
        )
      case DbType.SqlServer =>
        (
          db.SqlServerType.AliasTypeRef(RelationName(Some("showcase"), "EmailAddress"), "nvarchar(255)", db.SqlServerType.NVarChar(Some(255)), hasConstraint = false),
          db.SqlServerType.AliasTypeRef(RelationName(Some("showcase"), "PositiveAmount"), "decimal(10,2)", db.SqlServerType.Decimal(Some(10), Some(2)), hasConstraint = false),
          db.SqlServerType.AliasTypeRef(RelationName(Some("showcase"), "PhoneNumber"), "nvarchar(20)", db.SqlServerType.NVarChar(Some(20)), hasConstraint = false),
          db.SqlServerType.AliasTypeRef(RelationName(Some("showcase"), "Percentage"), "decimal(5,2)", db.SqlServerType.Decimal(Some(5), Some(2)), hasConstraint = false)
        )
      case DbType.DB2 =>
        (
          db.DB2Type.DistinctType(RelationName(Some("SHOWCASE"), "EMAIL_ADDRESS"), db.DB2Type.VarChar(Some(255))),
          db.DB2Type.DistinctType(RelationName(Some("SHOWCASE"), "POSITIVE_AMOUNT"), db.DB2Type.Decimal(Some(10), Some(2))),
          db.DB2Type.DistinctType(RelationName(Some("SHOWCASE"), "PHONE_NUMBER"), db.DB2Type.VarChar(Some(20))),
          db.DB2Type.DistinctType(RelationName(Some("SHOWCASE"), "PERCENTAGE"), db.DB2Type.Decimal(Some(5), Some(2)))
        )
      case _ =>
        (varcharType(dbType, Some(255)), decimalType(dbType, 10, 2), varcharType(dbType, Some(20)), decimalType(dbType, 5, 2))
    }

    val baseCols = List(
      col("department_id", varcharType(dbType, None), notNull = true),
      col("manager_id", varcharType(dbType, None), notNull = false),
      col("title", varcharType(dbType, Some(10)), notNull = false), // Open enum - references title lookup table
      col("email", emailType, notNull = true),
      col("first_name", varcharType(dbType, Some(50)), notNull = true),
      col("last_name", varcharType(dbType, Some(50)), notNull = true),
      col("salary", salaryType, notNull = false),
      col("phone", phoneType, notNull = false),
      col("bonus_percentage", bonusPercentageType, notNull = false),
      col("hired_at", dateType(dbType), notNull = false),
      col("active", boolType(dbType), notNull = false, default = Some("true")),
      col("created_at", timestampType(dbType), notNull = false, default = Some("CURRENT_TIMESTAMP"))
    )

    // Database-specific columns
    val dbSpecificCols = dbType match {
      case DbType.PostgreSQL =>
        List(
          col("skills", db.PgType.Array(db.PgType.Text), notNull = false),
          col("certifications", db.PgType.Array(db.PgType.Text), notNull = false),
          col("metadata", db.PgType.Jsonb, notNull = false),
          col("settings", db.PgType.Hstore, notNull = false),
          col("work_schedule", db.PgType.Array(db.PgType.Boolean), notNull = false),
          col("ip_address", db.PgType.Inet, notNull = false),
          col("network_cidr", db.PgType.Cidr, notNull = false),
          col("mac_address", db.PgType.MacAddr, notNull = false),
          col("probation_period", db.PgType.PGInterval, notNull = false),
          col("photo", db.PgType.Bytea, notNull = false),
          // Geometric types
          col("office_location", db.PgType.PGpoint, notNull = false),
          col("parking_area", db.PgType.PGbox, notNull = false),
          col("commute_path", db.PgType.PGpath, notNull = false),
          col("work_zone", db.PgType.PGpolygon, notNull = false),
          col("desk_radius", db.PgType.PGcircle, notNull = false),
          // Other special types
          col("monthly_salary", db.PgType.PGmoney, notNull = false),
          col("resume_xml", db.PgType.Xml, notNull = false),
          col("skill_embedding", db.PgType.Vector, notNull = false),
          col(
            "contact_info",
            db.PgType.CompositeType(
              RelationName(Some("showcase"), "contact_info"),
              List(
                db.PgType.CompositeField("phone", db.PgType.Text, nullable = true),
                db.PgType.CompositeField("mobile", db.PgType.Text, nullable = true),
                db.PgType.CompositeField("emergency_contact", db.PgType.Text, nullable = true),
                db.PgType.CompositeField("emergency_phone", db.PgType.Text, nullable = true)
              )
            ),
            notNull = false
          )
        )
      case DbType.MariaDB =>
        List(
          col("metadata", db.MariaType.Json, notNull = false),
          col("photo", db.MariaType.MediumBlob, notNull = false),
          col("ip_address", db.MariaType.Inet4, notNull = false),
          col("permissions", db.MariaType.Set(List("read", "write", "delete", "admin")), notNull = false)
        )
      case DbType.DuckDB =>
        List(
          col("skills", db.DuckDbType.ListType(db.DuckDbType.VarChar(None)), notNull = false),
          col("certifications", db.DuckDbType.ListType(db.DuckDbType.VarChar(None)), notNull = false),
          col("weekly_availability", db.DuckDbType.ListType(db.DuckDbType.Boolean), notNull = false),
          col(
            "contact_info",
            db.DuckDbType.StructType(
              List(
                ("phone", db.DuckDbType.VarChar(None)),
                ("mobile", db.DuckDbType.VarChar(None)),
                ("emergency_contact", db.DuckDbType.VarChar(None)),
                ("emergency_phone", db.DuckDbType.VarChar(None))
              )
            ),
            notNull = false
          ),
          col("settings", db.DuckDbType.MapType(db.DuckDbType.VarChar(None), db.DuckDbType.VarChar(None)), notNull = false),
          col("skills_rating", db.DuckDbType.MapType(db.DuckDbType.VarChar(None), db.DuckDbType.Integer), notNull = false),
          col("probation_period", db.DuckDbType.Interval, notNull = false)
        )
      case DbType.Oracle =>
        List(
          col("metadata", db.OracleType.Json, notNull = false),
          col("resume", db.OracleType.Clob, notNull = false),
          col("photo", db.OracleType.Blob, notNull = false),
          col("probation_period", db.OracleType.IntervalYearToMonth(None), notNull = false),
          col("shift_duration", db.OracleType.IntervalDayToSecond(None, None), notNull = false),
          col(
            "skills",
            db.OracleType.VArray(
              RelationName(Some("showcase"), "skills_array"),
              10,
              db.OracleType.Varchar2(Some(100))
            ),
            notNull = false
          ),
          col(
            "certifications",
            db.OracleType.NestedTable(
              RelationName(Some("showcase"), "certifications_table"),
              db.OracleType.ObjectType(
                RelationName(Some("showcase"), "certification_t"),
                List(
                  db.OracleType.ObjectAttribute("name", db.OracleType.Varchar2(Some(100)), 1),
                  db.OracleType.ObjectAttribute("issuer", db.OracleType.Varchar2(Some(100)), 2),
                  db.OracleType.ObjectAttribute("year_obtained", db.OracleType.Number(Some(4), Some(0)), 3)
                ),
                isFinal = true,
                isInstantiable = true,
                supertype = None
              ),
              Some("certifications_nt")
            ),
            notNull = false
          ),
          col(
            "contact_info",
            db.OracleType.ObjectType(
              RelationName(Some("showcase"), "contact_info_t"),
              List(
                db.OracleType.ObjectAttribute("phone", db.OracleType.Varchar2(Some(20)), 1),
                db.OracleType.ObjectAttribute("mobile", db.OracleType.Varchar2(Some(20)), 2),
                db.OracleType.ObjectAttribute("emergency_contact", db.OracleType.Varchar2(Some(100)), 3),
                db.OracleType.ObjectAttribute("emergency_phone", db.OracleType.Varchar2(Some(20)), 4)
              ),
              isFinal = true,
              isInstantiable = true,
              supertype = None
            ),
            notNull = false
          )
        )
      case DbType.SqlServer =>
        List(
          col("metadata", db.SqlServerType.Json, notNull = false),
          col("resume", db.SqlServerType.Xml, notNull = false),
          col("photo", db.SqlServerType.VarBinary(None), notNull = false),
          col("employee_uid", db.SqlServerType.UniqueIdentifier, notNull = false),
          col("last_modified", db.SqlServerType.DateTimeOffset(None), notNull = false),
          col("row_version", db.SqlServerType.RowVersion, notNull = false),
          col("org_path", db.SqlServerType.HierarchyId, notNull = false)
        )
      case DbType.DB2 =>
        List(
          col("resume", db.DB2Type.Clob, notNull = false),
          col("photo", db.DB2Type.Blob, notNull = false)
        )
    }

    Table(
      name = RelationName(Some(schema(dbType)), "employee"),
      comment = None,
      cols = NonEmptyList(col("id", varcharType(dbType, None), notNull = true), baseCols ++ dbSpecificCols),
      primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "employee_pkey"))),
      uniqueKeys = List(UniqueKey(NonEmptyList(ColName("email"), Nil), RelationName(None, "employee_email_key"))),
      foreignKeys = List(
        ForeignKey(
          NonEmptyList(ColName("department_id"), Nil),
          RelationName(Some(schema(dbType)), "department"),
          NonEmptyList(ColName("id"), Nil),
          RelationName(None, "employee_department_fk")
        ),
        ForeignKey(
          NonEmptyList(ColName("manager_id"), Nil),
          RelationName(Some(schema(dbType)), "employee"),
          NonEmptyList(ColName("id"), Nil),
          RelationName(None, "employee_manager_fk")
        ),
        ForeignKey(
          NonEmptyList(ColName("title"), Nil),
          RelationName(Some(schema(dbType)), "title"),
          NonEmptyList(ColName("code"), Nil),
          RelationName(None, "employee_title_fk")
        )
      )
    )
  }

  private def category(dbType: DbType): Table = Table(
    name = RelationName(Some(schema(dbType)), "category"),
    comment = None,
    cols = NonEmptyList(
      col("id", varcharType(dbType, None), notNull = true),
      List(
        col("parent_id", varcharType(dbType, None), notNull = false),
        col("name", varcharType(dbType, Some(100)), notNull = true),
        col("description", varcharType(dbType, Some(500)), notNull = false)
      )
    ),
    primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "category_pkey"))),
    uniqueKeys = Nil,
    foreignKeys = List(
      ForeignKey(
        NonEmptyList(ColName("parent_id"), Nil),
        RelationName(Some(schema(dbType)), "category"),
        NonEmptyList(ColName("id"), Nil),
        RelationName(None, "category_parent_fk")
      )
    )
  )

  private def product(dbType: DbType): Table = {
    val baseCols = List(
      col("category_id", varcharType(dbType, None), notNull = false),
      col("name", varcharType(dbType, Some(200)), notNull = true),
      col("sku", varcharType(dbType, Some(50)), notNull = true),
      col("price", decimalType(dbType, 10, 2), notNull = true),
      col("cost", decimalType(dbType, 10, 2), notNull = false),
      col("in_stock", boolType(dbType), notNull = false, default = Some("true")),
      col("quantity", intType(dbType), notNull = false, default = Some("0")),
      col("weight", decimalType(dbType, 8, 3), notNull = false),
      col("created_at", timestampType(dbType), notNull = false, default = Some("CURRENT_TIMESTAMP"))
    )

    // Database-specific columns
    val dbSpecificCols = dbType match {
      case DbType.PostgreSQL =>
        List(
          col("tags", db.PgType.Array(db.PgType.Text), notNull = false),
          col("images", db.PgType.Array(db.PgType.Text), notNull = false),
          col("related_skus", db.PgType.Array(db.PgType.Text), notNull = false),
          col("price_history", db.PgType.Array(db.PgType.Numeric), notNull = false),
          col("attributes", db.PgType.Jsonb, notNull = false),
          col("specifications", db.PgType.Jsonb, notNull = false),
          col("seo_metadata", db.PgType.Jsonb, notNull = false),
          col("search_vector", db.PgType.Text, notNull = false), // tsvector would need to be added
          col("product_uid", db.PgType.UUID, notNull = false)
        )
      case DbType.MariaDB =>
        List(
          col("attributes", db.MariaType.Json, notNull = false),
          col("specifications", db.MariaType.Json, notNull = false),
          col("seo_metadata", db.MariaType.Json, notNull = false),
          col("description_long", db.MariaType.LongText, notNull = false),
          col("thumbnail", db.MariaType.MediumBlob, notNull = false)
        )
      case DbType.DuckDB =>
        List(
          col("tags", db.DuckDbType.ListType(db.DuckDbType.VarChar(None)), notNull = false),
          col("images", db.DuckDbType.ListType(db.DuckDbType.VarChar(None)), notNull = false),
          col("related_skus", db.DuckDbType.ListType(db.DuckDbType.VarChar(None)), notNull = false),
          col("price_history", db.DuckDbType.ListType(db.DuckDbType.Decimal(Some(10), Some(2))), notNull = false),
          col("category_ids", db.DuckDbType.ListType(db.DuckDbType.Integer), notNull = false),
          col(
            "dimensions",
            db.DuckDbType.StructType(
              List(
                ("width", db.DuckDbType.Double),
                ("height", db.DuckDbType.Double),
                ("depth", db.DuckDbType.Double),
                ("weight", db.DuckDbType.Double),
                ("unit", db.DuckDbType.VarChar(None))
              )
            ),
            notNull = false
          ),
          col("attributes", db.DuckDbType.MapType(db.DuckDbType.VarChar(None), db.DuckDbType.VarChar(None)), notNull = false),
          col("specifications", db.DuckDbType.MapType(db.DuckDbType.VarChar(None), db.DuckDbType.VarChar(None)), notNull = false),
          col("prices_by_region", db.DuckDbType.MapType(db.DuckDbType.VarChar(None), db.DuckDbType.Decimal(Some(10), Some(2))), notNull = false),
          col("stock_by_warehouse", db.DuckDbType.MapType(db.DuckDbType.VarChar(None), db.DuckDbType.Integer), notNull = false),
          col("seo_metadata", db.DuckDbType.Json, notNull = false)
        )
      case DbType.Oracle =>
        List(
          col("attributes", db.OracleType.Json, notNull = false),
          col("specifications", db.OracleType.Clob, notNull = false),
          col("description_long", db.OracleType.Clob, notNull = false),
          col("thumbnail", db.OracleType.Blob, notNull = false),
          col("xml_data", db.OracleType.XmlType, notNull = false)
        )
      case DbType.SqlServer =>
        List(
          col("attributes", db.SqlServerType.Json, notNull = false),
          col("specifications", db.SqlServerType.Xml, notNull = false),
          col("description_long", db.SqlServerType.NVarChar(None), notNull = false),
          col("thumbnail", db.SqlServerType.VarBinary(None), notNull = false),
          col("product_uid", db.SqlServerType.UniqueIdentifier, notNull = false),
          col("list_price", db.SqlServerType.Money, notNull = false)
        )
      case DbType.DB2 =>
        List(
          col("description_long", db.DB2Type.Clob, notNull = false),
          col("thumbnail", db.DB2Type.Blob, notNull = false),
          col("xml_data", db.DB2Type.Xml, notNull = false)
        )
    }

    Table(
      name = RelationName(Some(schema(dbType)), "product"),
      comment = None,
      cols = NonEmptyList(col("id", varcharType(dbType, None), notNull = true), baseCols ++ dbSpecificCols),
      primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "product_pkey"))),
      uniqueKeys = List(UniqueKey(NonEmptyList(ColName("sku"), Nil), RelationName(None, "product_sku_key"))),
      foreignKeys = List(
        ForeignKey(
          NonEmptyList(ColName("category_id"), Nil),
          RelationName(Some(schema(dbType)), "category"),
          NonEmptyList(ColName("id"), Nil),
          RelationName(None, "product_category_fk")
        )
      )
    )
  }

  private def customer(dbType: DbType): Table = {
    val baseCols = List(
      col("email", varcharType(dbType, Some(255)), notNull = true),
      col("first_name", varcharType(dbType, Some(50)), notNull = true),
      col("last_name", varcharType(dbType, Some(50)), notNull = true),
      col("phone", varcharType(dbType, Some(20)), notNull = false),
      col("verified", boolType(dbType), notNull = false, default = Some("false")),
      col("created_at", timestampType(dbType), notNull = false, default = Some("CURRENT_TIMESTAMP"))
    )

    // Database-specific columns
    val dbSpecificCols = dbType match {
      case DbType.PostgreSQL =>
        List(
          col("interests", db.PgType.Array(db.PgType.Text), notNull = false),
          col("preferences", db.PgType.Jsonb, notNull = false),
          col("metadata", db.PgType.Hstore, notNull = false),
          col("customer_uid", db.PgType.UUID, notNull = false),
          col("avatar", db.PgType.Bytea, notNull = false)
        )
      case DbType.MariaDB =>
        List(
          col("preferences", db.MariaType.Json, notNull = false),
          col("avatar", db.MariaType.MediumBlob, notNull = false),
          col("customer_type", db.MariaType.Enum(List("regular", "premium", "vip")), notNull = false)
        )
      case DbType.DuckDB =>
        List(
          col("interests", db.DuckDbType.ListType(db.DuckDbType.VarChar(None)), notNull = false),
          col("order_counts", db.DuckDbType.ListType(db.DuckDbType.Integer), notNull = false),
          col(
            "preferences",
            db.DuckDbType.StructType(
              List(
                ("newsletter", db.DuckDbType.Boolean),
                ("language", db.DuckDbType.VarChar(None)),
                ("currency", db.DuckDbType.VarChar(None)),
                ("timezone", db.DuckDbType.VarChar(None)),
                ("theme", db.DuckDbType.VarChar(None))
              )
            ),
            notNull = false
          ),
          col("metadata", db.DuckDbType.MapType(db.DuckDbType.VarChar(None), db.DuckDbType.VarChar(None)), notNull = false)
        )
      case DbType.Oracle =>
        List(
          col("preferences", db.OracleType.Json, notNull = false),
          col("avatar", db.OracleType.Blob, notNull = false),
          col("notes", db.OracleType.Clob, notNull = false)
        )
      case DbType.SqlServer =>
        List(
          col("preferences", db.SqlServerType.Json, notNull = false),
          col("avatar", db.SqlServerType.VarBinary(None), notNull = false),
          col("customer_uid", db.SqlServerType.UniqueIdentifier, notNull = false),
          col("credit_limit", db.SqlServerType.Money, notNull = false)
        )
      case DbType.DB2 =>
        List(
          col("avatar", db.DB2Type.Blob, notNull = false),
          col("notes", db.DB2Type.Clob, notNull = false)
        )
    }

    Table(
      name = RelationName(Some(schema(dbType)), "customer"),
      comment = None,
      cols = NonEmptyList(col("id", varcharType(dbType, None), notNull = true), baseCols ++ dbSpecificCols),
      primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "customer_pkey"))),
      uniqueKeys = List(UniqueKey(NonEmptyList(ColName("email"), Nil), RelationName(None, "customer_email_key"))),
      foreignKeys = Nil
    )
  }

  private def address(dbType: DbType): Table = {
    val baseCols = List(
      col("customer_id", varcharType(dbType, None), notNull = true),
      col("address_type", addressTypeType(dbType), notNull = true),
      col("street", varcharType(dbType, Some(200)), notNull = true),
      col("city", varcharType(dbType, Some(100)), notNull = true),
      col("state", varcharType(dbType, Some(100)), notNull = false),
      col("postal_code", varcharType(dbType, Some(20)), notNull = false),
      col("country", varcharType(dbType, Some(100)), notNull = true),
      col("is_default", boolType(dbType), notNull = false, default = Some("false"))
    )

    // Database-specific columns
    val dbSpecificCols = dbType match {
      case DbType.PostgreSQL =>
        List(
          col("location", db.PgType.PGpoint, notNull = false),
          col("boundary", db.PgType.PGbox, notNull = false),
          col("delivery_zone", db.PgType.PGpolygon, notNull = false)
        )
      case DbType.MariaDB =>
        List(
          col("location", db.MariaType.Point, notNull = false),
          col("delivery_zone", db.MariaType.Polygon, notNull = false)
        )
      case DbType.DuckDB =>
        List(
          col(
            "coordinates",
            db.DuckDbType.StructType(
              List(
                ("latitude", db.DuckDbType.Double),
                ("longitude", db.DuckDbType.Double),
                ("accuracy", db.DuckDbType.Double)
              )
            ),
            notNull = false
          )
        )
      case DbType.Oracle =>
        List(
          col("location", db.OracleType.SdoPoint, notNull = false),
          col("notes", db.OracleType.Clob, notNull = false)
        )
      case DbType.SqlServer =>
        List(
          col("location", db.SqlServerType.Geography, notNull = false),
          col("delivery_zone", db.SqlServerType.Geometry, notNull = false)
        )
      case DbType.DB2 =>
        List(
          col("notes", db.DB2Type.Clob, notNull = false)
        )
    }

    Table(
      name = RelationName(Some(schema(dbType)), "address"),
      comment = None,
      cols = NonEmptyList(col("id", varcharType(dbType, None), notNull = true), baseCols ++ dbSpecificCols),
      primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "address_pkey"))),
      uniqueKeys = Nil,
      foreignKeys = List(
        ForeignKey(
          NonEmptyList(ColName("customer_id"), Nil),
          RelationName(Some(schema(dbType)), "customer"),
          NonEmptyList(ColName("id"), Nil),
          RelationName(None, "address_customer_fk")
        )
      )
    )
  }

  private def customerOrder(dbType: DbType): Table = {
    val baseCols = List(
      col("customer_id", varcharType(dbType, None), notNull = true),
      col("shipping_address_id", varcharType(dbType, None), notNull = false),
      col("billing_address_id", varcharType(dbType, None), notNull = false),
      col("status", orderStatusType(dbType), notNull = false, default = Some("'pending'")),
      col("subtotal", decimalType(dbType, 10, 2), notNull = true),
      col("tax", decimalType(dbType, 10, 2), notNull = false, default = Some("0")),
      col("shipping", decimalType(dbType, 10, 2), notNull = false, default = Some("0")),
      col("total", decimalType(dbType, 10, 2), notNull = true),
      col("notes", varcharType(dbType, Some(1000)), notNull = false),
      col("created_at", timestampType(dbType), notNull = false, default = Some("CURRENT_TIMESTAMP")),
      col("shipped_at", timestampType(dbType), notNull = false),
      col("delivered_at", timestampType(dbType), notNull = false)
    )

    // Database-specific columns
    val dbSpecificCols = dbType match {
      case DbType.PostgreSQL =>
        List(
          col("order_uid", db.PgType.UUID, notNull = false),
          col("metadata", db.PgType.Jsonb, notNull = false),
          col("tags", db.PgType.Array(db.PgType.Text), notNull = false),
          col("fulfillment_times", db.PgType.Array(db.PgType.Timestamp), notNull = false),
          col("ip_address", db.PgType.Inet, notNull = false),
          col("processing_time", db.PgType.PGInterval, notNull = false)
        )
      case DbType.MariaDB =>
        List(
          col("metadata", db.MariaType.Json, notNull = false),
          col("ip_address", db.MariaType.Inet4, notNull = false),
          col("payment_method", db.MariaType.Enum(List("credit_card", "debit_card", "paypal", "bank_transfer", "cash")), notNull = false)
        )
      case DbType.DuckDB =>
        List(
          col("ordered_at_tz", db.DuckDbType.TimestampTz, notNull = false, default = Some("now()")),
          col("delivered_at_tz", db.DuckDbType.TimestampTz, notNull = false),
          col("tags", db.DuckDbType.ListType(db.DuckDbType.VarChar(None)), notNull = false),
          col("fulfillment_times", db.DuckDbType.ListType(db.DuckDbType.Timestamp), notNull = false),
          col(
            "line_items_summary",
            db.DuckDbType.StructType(
              List(
                ("item_count", db.DuckDbType.Integer),
                ("unique_products", db.DuckDbType.Integer),
                ("heaviest_item_weight", db.DuckDbType.Double),
                ("total_weight", db.DuckDbType.Double)
              )
            ),
            notNull = false
          ),
          col("metadata", db.DuckDbType.MapType(db.DuckDbType.VarChar(None), db.DuckDbType.VarChar(None)), notNull = false),
          col("processing_time", db.DuckDbType.Interval, notNull = false)
        )
      case DbType.Oracle =>
        List(
          col("metadata", db.OracleType.Json, notNull = false),
          col("notes_extended", db.OracleType.Clob, notNull = false),
          col("receipt_data", db.OracleType.Blob, notNull = false),
          col("processing_time", db.OracleType.IntervalDayToSecond(None, None), notNull = false),
          col("xml_invoice", db.OracleType.XmlType, notNull = false)
        )
      case DbType.SqlServer =>
        List(
          col("order_uid", db.SqlServerType.UniqueIdentifier, notNull = false),
          col("metadata", db.SqlServerType.Json, notNull = false),
          col("xml_invoice", db.SqlServerType.Xml, notNull = false),
          col("receipt_data", db.SqlServerType.VarBinary(None), notNull = false),
          col("row_version", db.SqlServerType.RowVersion, notNull = false),
          col("order_amount", db.SqlServerType.Money, notNull = false)
        )
      case DbType.DB2 =>
        List(
          col("notes_extended", db.DB2Type.Clob, notNull = false),
          col("receipt_data", db.DB2Type.Blob, notNull = false),
          col("xml_invoice", db.DB2Type.Xml, notNull = false)
        )
    }

    Table(
      name = RelationName(Some(schema(dbType)), "customer_order"),
      comment = None,
      cols = NonEmptyList(col("id", varcharType(dbType, None), notNull = true), baseCols ++ dbSpecificCols),
      primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "customer_order_pkey"))),
      uniqueKeys = Nil,
      foreignKeys = List(
        ForeignKey(
          NonEmptyList(ColName("customer_id"), Nil),
          RelationName(Some(schema(dbType)), "customer"),
          NonEmptyList(ColName("id"), Nil),
          RelationName(None, "customer_order_customer_fk")
        ),
        ForeignKey(
          NonEmptyList(ColName("shipping_address_id"), Nil),
          RelationName(Some(schema(dbType)), "address"),
          NonEmptyList(ColName("id"), Nil),
          RelationName(None, "customer_order_shipping_fk")
        ),
        ForeignKey(
          NonEmptyList(ColName("billing_address_id"), Nil),
          RelationName(Some(schema(dbType)), "address"),
          NonEmptyList(ColName("id"), Nil),
          RelationName(None, "customer_order_billing_fk")
        )
      )
    )
  }

  private def orderItem(dbType: DbType): Table = Table(
    name = RelationName(Some(schema(dbType)), "order_item"),
    comment = None,
    cols = NonEmptyList(
      col("order_id", varcharType(dbType, None), notNull = true),
      List(
        col("product_id", varcharType(dbType, None), notNull = true),
        col("quantity", intType(dbType), notNull = true),
        col("unit_price", decimalType(dbType, 10, 2), notNull = true),
        col("discount", decimalType(dbType, 5, 2), notNull = false, default = Some("0"))
      )
    ),
    primaryKey = Some(PrimaryKey(NonEmptyList(ColName("order_id"), List(ColName("product_id"))), RelationName(None, "order_item_pkey"))),
    uniqueKeys = Nil,
    foreignKeys = List(
      ForeignKey(
        NonEmptyList(ColName("order_id"), Nil),
        RelationName(Some(schema(dbType)), "customer_order"),
        NonEmptyList(ColName("id"), Nil),
        RelationName(None, "order_item_order_fk")
      ),
      ForeignKey(
        NonEmptyList(ColName("product_id"), Nil),
        RelationName(Some(schema(dbType)), "product"),
        NonEmptyList(ColName("id"), Nil),
        RelationName(None, "order_item_product_fk")
      )
    )
  )

  private def project(dbType: DbType): Table = {
    val baseCols = List(
      col("name", varcharType(dbType, Some(200)), notNull = true),
      col("description", varcharType(dbType, Some(1000)), notNull = false),
      col("start_date", dateType(dbType), notNull = false),
      col("end_date", dateType(dbType), notNull = false),
      col("budget", decimalType(dbType, 12, 2), notNull = false),
      col("status", projectStatusType(dbType), notNull = false, default = Some("'planning'")),
      col("created_at", timestampType(dbType), notNull = false, default = Some("CURRENT_TIMESTAMP"))
    )

    // Database-specific columns
    val dbSpecificCols = dbType match {
      case DbType.PostgreSQL =>
        List(
          col("project_uid", db.PgType.UUID, notNull = false),
          col("milestones", db.PgType.Array(db.PgType.Text), notNull = false),
          col("budget_snapshots", db.PgType.Array(db.PgType.Numeric), notNull = false),
          col("team_members", db.PgType.Array(db.PgType.Text), notNull = false),
          col("metadata", db.PgType.Jsonb, notNull = false),
          col("config", db.PgType.Hstore, notNull = false),
          col("estimated_duration", db.PgType.PGInterval, notNull = false),
          col("actual_duration", db.PgType.PGInterval, notNull = false)
        )
      case DbType.MariaDB =>
        List(
          col("metadata", db.MariaType.Json, notNull = false),
          col("description_long", db.MariaType.LongText, notNull = false),
          col("attachments", db.MariaType.MediumBlob, notNull = false),
          col("priority", db.MariaType.Enum(List("low", "medium", "high", "critical")), notNull = false)
        )
      case DbType.DuckDB =>
        List(
          col("milestones", db.DuckDbType.ListType(db.DuckDbType.VarChar(None)), notNull = false),
          col("budget_snapshots", db.DuckDbType.ListType(db.DuckDbType.Decimal(Some(12), Some(2))), notNull = false),
          col("team_members", db.DuckDbType.ListType(db.DuckDbType.VarChar(None)), notNull = false),
          col(
            "project_phases",
            db.DuckDbType.StructType(
              List(
                ("planning_complete", db.DuckDbType.Boolean),
                ("development_complete", db.DuckDbType.Boolean),
                ("testing_complete", db.DuckDbType.Boolean),
                ("deployment_complete", db.DuckDbType.Boolean),
                ("current_phase", db.DuckDbType.VarChar(None))
              )
            ),
            notNull = false
          ),
          col("config", db.DuckDbType.MapType(db.DuckDbType.VarChar(None), db.DuckDbType.VarChar(None)), notNull = false),
          col("risk_scores", db.DuckDbType.MapType(db.DuckDbType.VarChar(None), db.DuckDbType.Integer), notNull = false),
          col("estimated_duration", db.DuckDbType.Interval, notNull = false),
          col("actual_duration", db.DuckDbType.Interval, notNull = false),
          col("metadata", db.DuckDbType.Json, notNull = false)
        )
      case DbType.Oracle =>
        List(
          col("metadata", db.OracleType.Json, notNull = false),
          col("description_long", db.OracleType.Clob, notNull = false),
          col("attachments", db.OracleType.Blob, notNull = false),
          col("estimated_duration", db.OracleType.IntervalYearToMonth(None), notNull = false),
          col("actual_duration", db.OracleType.IntervalDayToSecond(None, None), notNull = false),
          col("xml_plan", db.OracleType.XmlType, notNull = false)
        )
      case DbType.SqlServer =>
        List(
          col("project_uid", db.SqlServerType.UniqueIdentifier, notNull = false),
          col("metadata", db.SqlServerType.Json, notNull = false),
          col("xml_plan", db.SqlServerType.Xml, notNull = false),
          col("attachments", db.SqlServerType.VarBinary(None), notNull = false),
          col("row_version", db.SqlServerType.RowVersion, notNull = false),
          col("total_budget", db.SqlServerType.Money, notNull = false),
          col("org_path", db.SqlServerType.HierarchyId, notNull = false)
        )
      case DbType.DB2 =>
        List(
          col("description_long", db.DB2Type.Clob, notNull = false),
          col("attachments", db.DB2Type.Blob, notNull = false),
          col("xml_plan", db.DB2Type.Xml, notNull = false)
        )
    }

    Table(
      name = RelationName(Some(schema(dbType)), "project"),
      comment = None,
      cols = NonEmptyList(col("id", varcharType(dbType, None), notNull = true), baseCols ++ dbSpecificCols),
      primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "project_pkey"))),
      uniqueKeys = Nil,
      foreignKeys = Nil
    )
  }

  private def projectAssignment(dbType: DbType): Table = Table(
    name = RelationName(Some(schema(dbType)), "project_assignment"),
    comment = None,
    cols = NonEmptyList(
      col("employee_id", varcharType(dbType, None), notNull = true),
      List(
        col("project_id", varcharType(dbType, None), notNull = true),
        col("role", varcharType(dbType, Some(50)), notNull = true),
        col("hours_allocated", intType(dbType), notNull = false, default = Some("0")),
        col("start_date", dateType(dbType), notNull = false),
        col("end_date", dateType(dbType), notNull = false)
      )
    ),
    primaryKey = Some(PrimaryKey(NonEmptyList(ColName("employee_id"), List(ColName("project_id"))), RelationName(None, "project_assignment_pkey"))),
    uniqueKeys = Nil,
    foreignKeys = List(
      ForeignKey(
        NonEmptyList(ColName("employee_id"), Nil),
        RelationName(Some(schema(dbType)), "employee"),
        NonEmptyList(ColName("id"), Nil),
        RelationName(None, "project_assignment_employee_fk")
      ),
      ForeignKey(
        NonEmptyList(ColName("project_id"), Nil),
        RelationName(Some(schema(dbType)), "project"),
        NonEmptyList(ColName("id"), Nil),
        RelationName(None, "project_assignment_project_fk")
      )
    )
  )

  private def auditLog(dbType: DbType): Table = Table(
    name = RelationName(Some(schema(dbType)), "audit_log"),
    comment = None,
    cols = NonEmptyList(
      col("id", varcharType(dbType, None), notNull = true),
      List(
        col("table_name", varcharType(dbType, Some(100)), notNull = true),
        col("record_id", varcharType(dbType, None), notNull = true),
        col("action", varcharType(dbType, Some(20)), notNull = true),
        col("old_values", varcharType(dbType, Some(4000)), notNull = false),
        col("new_values", varcharType(dbType, Some(4000)), notNull = false),
        col("changed_by", varcharType(dbType, None), notNull = false),
        col("changed_at", timestampType(dbType), notNull = false, default = Some("CURRENT_TIMESTAMP"))
      )
    ),
    primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "audit_log_pkey"))),
    uniqueKeys = Nil,
    foreignKeys = List(
      ForeignKey(
        NonEmptyList(ColName("changed_by"), Nil),
        RelationName(Some(schema(dbType)), "employee"),
        NonEmptyList(ColName("id"), Nil),
        RelationName(None, "audit_log_employee_fk")
      )
    )
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // DuckDB-specific tables
  // ═══════════════════════════════════════════════════════════════════════════

  private def duckDbTables(dbType: DbType): List[Table] = List(
    // Session table with UUID
    Table(
      name = RelationName(Some(schema(dbType)), "session"),
      comment = None,
      cols = NonEmptyList(
        col("id", db.DuckDbType.UUID, notNull = true, default = Some("uuid()")),
        List(
          col("user_id", db.DuckDbType.VarChar(None), notNull = false),
          col("token", db.DuckDbType.UUID, notNull = true, default = Some("uuid()")),
          col("refresh_token", db.DuckDbType.UUID, notNull = false),
          col("device_id", db.DuckDbType.UUID, notNull = false),
          col("created_at", db.DuckDbType.Timestamp, notNull = false, default = Some("CURRENT_TIMESTAMP")),
          col("expires_at", db.DuckDbType.Timestamp, notNull = true)
        )
      ),
      primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "session_pkey"))),
      uniqueKeys = Nil,
      foreignKeys = Nil
    ),
    // Numeric types demo
    Table(
      name = RelationName(Some(schema(dbType)), "numeric_types_demo"),
      comment = None,
      cols = NonEmptyList(
        col("id", db.DuckDbType.VarChar(None), notNull = true),
        List(
          col("tiny_val", db.DuckDbType.TinyInt, notNull = false),
          col("utiny_val", db.DuckDbType.UTinyInt, notNull = false),
          col("small_val", db.DuckDbType.SmallInt, notNull = false),
          col("usmall_val", db.DuckDbType.USmallInt, notNull = false),
          col("int_val", db.DuckDbType.Integer, notNull = false),
          col("uint_val", db.DuckDbType.UInteger, notNull = false),
          col("big_val", db.DuckDbType.BigInt, notNull = false),
          col("ubig_val", db.DuckDbType.UBigInt, notNull = false),
          col("huge_val", db.DuckDbType.HugeInt, notNull = false),
          col("uhuge_val", db.DuckDbType.UHugeInt, notNull = false),
          col("created_at", db.DuckDbType.Timestamp, notNull = false, default = Some("CURRENT_TIMESTAMP"))
        )
      ),
      primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "numeric_types_demo_pkey"))),
      uniqueKeys = Nil,
      foreignKeys = Nil
    ),
    // Financial record with high precision decimals
    Table(
      name = RelationName(Some(schema(dbType)), "financial_record"),
      comment = None,
      cols = NonEmptyList(
        col("id", db.DuckDbType.VarChar(None), notNull = true),
        List(
          col("description", db.DuckDbType.VarChar(None), notNull = true),
          col("amount", db.DuckDbType.Decimal(Some(38), Some(10)), notNull = true),
          col("balance", db.DuckDbType.Decimal(Some(38), Some(10)), notNull = false),
          col("exchange_rate", db.DuckDbType.Decimal(Some(18), Some(8)), notNull = false),
          col("percentage", db.DuckDbType.Decimal(Some(5), Some(2)), notNull = false),
          col("created_at", db.DuckDbType.Timestamp, notNull = false, default = Some("CURRENT_TIMESTAMP"))
        )
      ),
      primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "financial_record_pkey"))),
      uniqueKeys = Nil,
      foreignKeys = Nil
    ),
    // Scientific data with floats
    Table(
      name = RelationName(Some(schema(dbType)), "scientific_data"),
      comment = None,
      cols = NonEmptyList(
        col("id", db.DuckDbType.VarChar(None), notNull = true),
        List(
          col("float_val", db.DuckDbType.Float, notNull = false),
          col("double_val", db.DuckDbType.Double, notNull = false),
          col("created_at", db.DuckDbType.Timestamp, notNull = false, default = Some("CURRENT_TIMESTAMP"))
        )
      ),
      primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "scientific_data_pkey"))),
      uniqueKeys = Nil,
      foreignKeys = Nil
    ),
    // Subscription with interval
    Table(
      name = RelationName(Some(schema(dbType)), "subscription"),
      comment = None,
      cols = NonEmptyList(
        col("id", db.DuckDbType.VarChar(None), notNull = true),
        List(
          col("customer_id", db.DuckDbType.VarChar(None), notNull = true),
          col("plan_name", db.DuckDbType.VarChar(None), notNull = true),
          col("billing_cycle", db.DuckDbType.Interval, notNull = true),
          col("trial_period", db.DuckDbType.Interval, notNull = false),
          col("started_at", db.DuckDbType.Timestamp, notNull = false, default = Some("CURRENT_TIMESTAMP"))
        )
      ),
      primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "subscription_pkey"))),
      uniqueKeys = Nil,
      foreignKeys = Nil
    ),
    // Datetime demo
    Table(
      name = RelationName(Some(schema(dbType)), "datetime_demo"),
      comment = None,
      cols = NonEmptyList(
        col("id", db.DuckDbType.VarChar(None), notNull = true),
        List(
          col("date_only", db.DuckDbType.Date, notNull = false),
          col("time_only", db.DuckDbType.Time, notNull = false),
          col("timestamp_val", db.DuckDbType.Timestamp, notNull = false),
          col("timestamp_tz", db.DuckDbType.TimestampTz, notNull = false),
          col("timestamp_s", db.DuckDbType.TimestampS, notNull = false),
          col("timestamp_ms", db.DuckDbType.TimestampMS, notNull = false),
          col("timestamp_ns", db.DuckDbType.TimestampNS, notNull = false),
          col("created_at", db.DuckDbType.Timestamp, notNull = false, default = Some("CURRENT_TIMESTAMP"))
        )
      ),
      primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "datetime_demo_pkey"))),
      uniqueKeys = Nil,
      foreignKeys = Nil
    ),
    // File storage with blob
    Table(
      name = RelationName(Some(schema(dbType)), "file_storage"),
      comment = None,
      cols = NonEmptyList(
        col("id", db.DuckDbType.VarChar(None), notNull = true),
        List(
          col("filename", db.DuckDbType.VarChar(None), notNull = true),
          col("mime_type", db.DuckDbType.VarChar(None), notNull = false),
          col("file_data", db.DuckDbType.Blob, notNull = false),
          col("thumbnail", db.DuckDbType.Blob, notNull = false),
          col("checksum", db.DuckDbType.Blob, notNull = false),
          col("file_size", db.DuckDbType.UBigInt, notNull = false),
          col("uploaded_at", db.DuckDbType.Timestamp, notNull = false, default = Some("CURRENT_TIMESTAMP"))
        )
      ),
      primaryKey = Some(PrimaryKey(NonEmptyList(ColName("id"), Nil), RelationName(None, "file_storage_pkey"))),
      uniqueKeys = Nil,
      foreignKeys = Nil
    )
  )

  // ═══════════════════════════════════════════════════════════════════════════
  // Type helpers - pattern match on dbType to produce correct types
  // ═══════════════════════════════════════════════════════════════════════════

  private def varcharType(dbType: DbType, maxLength: Option[Int]): db.Type = dbType match {
    case DbType.PostgreSQL => db.PgType.VarChar(maxLength)
    case DbType.MariaDB    => db.MariaType.VarChar(maxLength)
    case DbType.DuckDB     => db.DuckDbType.VarChar(maxLength)
    case DbType.Oracle     => db.OracleType.Varchar2(maxLength)
    case DbType.SqlServer  => db.SqlServerType.NVarChar(maxLength)
    case DbType.DB2        => db.DB2Type.VarChar(maxLength)
  }

  private def intType(dbType: DbType): db.Type = dbType match {
    case DbType.PostgreSQL => db.PgType.Int4
    case DbType.MariaDB    => db.MariaType.Int
    case DbType.DuckDB     => db.DuckDbType.Integer
    case DbType.Oracle     => db.OracleType.Number(Some(10), Some(0))
    case DbType.SqlServer  => db.SqlServerType.Int
    case DbType.DB2        => db.DB2Type.Integer
  }

  private def boolType(dbType: DbType): db.Type = dbType match {
    case DbType.PostgreSQL => db.PgType.Boolean
    case DbType.MariaDB    => db.MariaType.Boolean
    case DbType.DuckDB     => db.DuckDbType.Boolean
    case DbType.Oracle     => db.OracleType.Number(Some(1), Some(0))
    case DbType.SqlServer  => db.SqlServerType.Bit
    case DbType.DB2        => db.DB2Type.Boolean
  }

  private def decimalType(dbType: DbType, precision: Int, scale: Int): db.Type = dbType match {
    case DbType.PostgreSQL => db.PgType.Numeric
    case DbType.MariaDB    => db.MariaType.Decimal(Some(precision), Some(scale))
    case DbType.DuckDB     => db.DuckDbType.Decimal(Some(precision), Some(scale))
    case DbType.Oracle     => db.OracleType.Number(Some(precision), Some(scale))
    case DbType.SqlServer  => db.SqlServerType.Decimal(Some(precision), Some(scale))
    case DbType.DB2        => db.DB2Type.Decimal(Some(precision), Some(scale))
  }

  private def dateType(dbType: DbType): db.Type = dbType match {
    case DbType.PostgreSQL => db.PgType.Date
    case DbType.MariaDB    => db.MariaType.Date
    case DbType.DuckDB     => db.DuckDbType.Date
    case DbType.Oracle     => db.OracleType.Date
    case DbType.SqlServer  => db.SqlServerType.Date
    case DbType.DB2        => db.DB2Type.Date
  }

  private def timestampType(dbType: DbType): db.Type = dbType match {
    case DbType.PostgreSQL => db.PgType.Timestamp
    case DbType.MariaDB    => db.MariaType.Timestamp(None)
    case DbType.DuckDB     => db.DuckDbType.Timestamp
    case DbType.Oracle     => db.OracleType.Timestamp(None)
    case DbType.SqlServer  => db.SqlServerType.DateTime2(None)
    case DbType.DB2        => db.DB2Type.Timestamp(None)
  }

  private def bigintType(dbType: DbType): db.Type = dbType match {
    case DbType.PostgreSQL => db.PgType.Int8
    case DbType.MariaDB    => db.MariaType.BigInt
    case DbType.DuckDB     => db.DuckDbType.BigInt
    case DbType.Oracle     => db.OracleType.Number(Some(19), Some(0))
    case DbType.SqlServer  => db.SqlServerType.BigInt
    case DbType.DB2        => db.DB2Type.BigInt
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Column helpers
  // ═══════════════════════════════════════════════════════════════════════════

  private def col(
      name: String,
      tpe: db.Type,
      notNull: Boolean,
      default: Option[String] = None
  ): Col = Col(
    parsedName = ParsedName.of(name),
    tpe = tpe,
    udtName = None,
    nullability = if (notNull) Nullability.NoNulls else Nullability.Nullable,
    columnDefault = default,
    maybeGenerated = None,
    comment = None,
    constraints = Nil,
    jsonDescription = DebugJson.Empty
  )

  /** Create a column for a view - returns tuple of (Col, ParsedName) as views use this format */
  private def viewCol(
      name: String,
      tpe: db.Type,
      notNull: Boolean
  ): (Col, ParsedName) = {
    val parsedName = ParsedName.of(name)
    val c = Col(
      parsedName = parsedName,
      tpe = tpe,
      udtName = None,
      nullability = if (notNull) Nullability.NoNulls else Nullability.Nullable,
      columnDefault = None,
      maybeGenerated = None,
      comment = None,
      constraints = Nil,
      jsonDescription = DebugJson.Empty
    )
    (c, parsedName)
  }
}
