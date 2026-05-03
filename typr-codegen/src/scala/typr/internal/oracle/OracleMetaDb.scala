package typr
package internal
package oracle

import anorm.Column
import anorm.RowParser
import anorm.Success
import typr.internal.analysis.*

import java.sql.Connection
import scala.concurrent.{ExecutionContext, Future}

/** Oracle-specific metadata extraction using Anorm */
object OracleMetaDb {

  case class OracleColumn(
      owner: Option[String],
      tableName: String,
      columnName: String,
      dataType: String,
      dataLength: Option[Int],
      dataPrecision: Option[Int],
      dataScale: Option[Int],
      nullable: String,
      columnId: Int,
      dataDefault: Option[String],
      charLength: Option[Int],
      comments: Option[String]
  )

  object OracleColumn {
    def rowParser(idx: Int): RowParser[OracleColumn] = RowParser[OracleColumn] { row =>
      Success(
        OracleColumn(
          owner = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 1)(Column.columnToString),
          columnName = row(idx + 2)(Column.columnToString),
          dataType = row(idx + 3)(Column.columnToString),
          dataLength = row(idx + 4)(Column.columnToOption(Column.columnToInt)),
          dataPrecision = row(idx + 5)(Column.columnToOption(Column.columnToInt)),
          dataScale = row(idx + 6)(Column.columnToOption(Column.columnToInt)),
          nullable = row(idx + 7)(Column.columnToString),
          columnId = row(idx + 8)(Column.columnToInt),
          dataDefault = row(idx + 9)(Column.columnToOption(Column.columnToString)),
          charLength = row(idx + 10)(Column.columnToOption(Column.columnToInt)),
          comments = row(idx + 11)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  case class OracleTable(
      owner: Option[String],
      tableName: String,
      tableType: String,
      comments: Option[String]
  )

  object OracleTable {
    def rowParser(idx: Int): RowParser[OracleTable] = RowParser[OracleTable] { row =>
      Success(
        OracleTable(
          owner = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          tableName = row(idx + 1)(Column.columnToString),
          tableType = row(idx + 2)(Column.columnToString),
          comments = row(idx + 3)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  case class OracleConstraint(
      owner: Option[String],
      constraintName: String,
      constraintType: String,
      tableName: String,
      rOwner: Option[String],
      rConstraintName: Option[String]
  )

  object OracleConstraint {
    def rowParser(idx: Int): RowParser[OracleConstraint] = RowParser[OracleConstraint] { row =>
      Success(
        OracleConstraint(
          owner = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          constraintName = row(idx + 1)(Column.columnToString),
          constraintType = row(idx + 2)(Column.columnToString),
          tableName = row(idx + 3)(Column.columnToString),
          rOwner = row(idx + 4)(Column.columnToOption(Column.columnToString)),
          rConstraintName = row(idx + 5)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  case class OracleConstraintColumn(
      owner: Option[String],
      constraintName: String,
      tableName: String,
      columnName: String,
      position: Int
  )

  object OracleConstraintColumn {
    def rowParser(idx: Int): RowParser[OracleConstraintColumn] = RowParser[OracleConstraintColumn] { row =>
      Success(
        OracleConstraintColumn(
          owner = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          constraintName = row(idx + 1)(Column.columnToString),
          tableName = row(idx + 2)(Column.columnToString),
          columnName = row(idx + 3)(Column.columnToString),
          position = row(idx + 4)(Column.columnToInt)
        )
      )
    }
  }

  /** View definition from ALL_VIEWS */
  case class OracleView(
      owner: Option[String],
      viewName: String,
      textLength: Option[Long],
      text: Option[String]
  )

  object OracleView {
    def rowParser(idx: Int): RowParser[OracleView] = RowParser[OracleView] { row =>
      Success(
        OracleView(
          owner = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          viewName = row(idx + 1)(Column.columnToString),
          textLength = row(idx + 2)(Column.columnToOption(Column.columnToLong)),
          text = row(idx + 3)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  /** Analyzed view with JDBC metadata for dependency tracking */
  case class AnalyzedView(
      row: OracleView,
      decomposedSql: DecomposedSql,
      jdbcMetadata: OracleJdbcMetadata
  )

  /** Oracle user-defined object type from ALL_TYPES */
  case class OracleObjectType(
      owner: Option[String],
      typeName: String,
      typecode: String,
      isFinal: Boolean,
      isInstantiable: Boolean,
      supertypeOwner: Option[String],
      supertypeName: Option[String]
  )

  object OracleObjectType {
    def rowParser(idx: Int): RowParser[OracleObjectType] = RowParser[OracleObjectType] { row =>
      Success(
        OracleObjectType(
          owner = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          typeName = row(idx + 1)(Column.columnToString),
          typecode = row(idx + 2)(Column.columnToString),
          isFinal = row(idx + 3)(Column.columnToInt) == 1,
          isInstantiable = row(idx + 4)(Column.columnToInt) == 1,
          supertypeOwner = row(idx + 5)(Column.columnToOption(Column.columnToString)),
          supertypeName = row(idx + 6)(Column.columnToOption(Column.columnToString))
        )
      )
    }
  }

  /** Oracle type attribute from ALL_TYPE_ATTRS */
  case class OracleTypeAttribute(
      owner: Option[String],
      typeName: String,
      attrName: String,
      attrTypeName: String,
      attrTypeOwner: Option[String],
      attrNo: Int,
      length: Option[Int],
      precision: Option[Int],
      scale: Option[Int]
  )

  object OracleTypeAttribute {
    def rowParser(idx: Int): RowParser[OracleTypeAttribute] = RowParser[OracleTypeAttribute] { row =>
      Success(
        OracleTypeAttribute(
          owner = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          typeName = row(idx + 1)(Column.columnToString),
          attrName = row(idx + 2)(Column.columnToString),
          attrTypeName = row(idx + 3)(Column.columnToString),
          attrTypeOwner = row(idx + 4)(Column.columnToOption(Column.columnToString)),
          attrNo = row(idx + 5)(Column.columnToInt),
          length = row(idx + 6)(Column.columnToOption(Column.columnToInt)),
          precision = row(idx + 7)(Column.columnToOption(Column.columnToInt)),
          scale = row(idx + 8)(Column.columnToOption(Column.columnToInt))
        )
      )
    }
  }

  /** Oracle collection type from ALL_COLL_TYPES */
  case class OracleCollectionType(
      owner: Option[String],
      typeName: String,
      collType: String,
      upperBound: Option[Int],
      elemTypeName: String,
      elemTypeOwner: Option[String],
      length: Option[Int],
      precision: Option[Int],
      scale: Option[Int]
  )

  object OracleCollectionType {
    def rowParser(idx: Int): RowParser[OracleCollectionType] = RowParser[OracleCollectionType] { row =>
      Success(
        OracleCollectionType(
          owner = row(idx + 0)(Column.columnToOption(Column.columnToString)),
          typeName = row(idx + 1)(Column.columnToString),
          collType = row(idx + 2)(Column.columnToString),
          upperBound = row(idx + 3)(Column.columnToOption(Column.columnToInt)),
          elemTypeName = row(idx + 4)(Column.columnToString),
          elemTypeOwner = row(idx + 5)(Column.columnToOption(Column.columnToString)),
          length = row(idx + 6)(Column.columnToOption(Column.columnToInt)),
          precision = row(idx + 7)(Column.columnToOption(Column.columnToInt)),
          scale = row(idx + 8)(Column.columnToOption(Column.columnToInt))
        )
      )
    }
  }

  case class Input(
      tables: List[OracleTable],
      columns: List[OracleColumn],
      constraints: List[OracleConstraint],
      constraintColumns: List[OracleConstraintColumn],
      analyzedViews: Map[db.RelationName, AnalyzedView],
      objectTypes: List[OracleObjectType],
      typeAttributes: Map[String, List[OracleTypeAttribute]],
      collectionTypes: List[OracleCollectionType]
  ) {
    def filter(schemaMode: SchemaMode): Input = {
      schemaMode match {
        case SchemaMode.MultiSchema => this
        case SchemaMode.SingleSchema(wantedSchema) =>
          def keep(os: Option[String]): Boolean = os.exists(_.equalsIgnoreCase(wantedSchema))

          Input(
            tables = tables.collect { case t if keep(t.owner) => t.copy(owner = None) },
            columns = columns.collect { case c if keep(c.owner) => c.copy(owner = None) },
            constraints = constraints.collect {
              case c if keep(c.owner) =>
                c.copy(owner = None, rOwner = if (keep(c.rOwner)) None else c.rOwner)
            },
            constraintColumns = constraintColumns.collect { case cc if keep(cc.owner) => cc.copy(owner = None) },
            analyzedViews = analyzedViews.collect {
              case (k, v) if keep(k.schema) =>
                k.copy(schema = None) -> v.copy(row = v.row.copy(owner = None))
            },
            objectTypes = objectTypes.collect { case ot if keep(ot.owner) => ot.copy(owner = None, supertypeOwner = if (keep(ot.supertypeOwner)) None else ot.supertypeOwner) },
            typeAttributes = typeAttributes.map { case (k, v) =>
              k -> v.collect { case ta if keep(ta.owner) => ta.copy(owner = None, attrTypeOwner = if (keep(ta.attrTypeOwner)) None else ta.attrTypeOwner) }
            },
            collectionTypes = collectionTypes.collect { case ct if keep(ct.owner) => ct.copy(owner = None, elemTypeOwner = if (keep(ct.elemTypeOwner)) None else ct.elemTypeOwner) }
          )
      }
    }
  }

  object Input {
    def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode)(implicit ec: ExecutionContext): Future[Input] = {
      for {
        tables <- logger.timed("fetching tables")(ds.run(implicit c => fetchTables(c)))
        columns <- logger.timed("fetching columns")(ds.run(implicit c => fetchColumns(c)))
        constraints <- logger.timed("fetching constraints")(ds.run(implicit c => fetchConstraints(c)))
        constraintColumns <- logger.timed("fetching constraintColumns")(ds.run(implicit c => fetchConstraintColumns(c)))
        analyzedViews <- logger.timed("fetching and analyzing views")(analyzeViews(ds, viewSelector))
        objectTypes <- logger.timed("fetching object types")(ds.run(implicit c => fetchObjectTypes(c)))
        typeAttributes <- logger.timed("fetching type attributes")(ds.run(implicit c => fetchTypeAttributes(c)))
        collectionTypes <- logger.timed("fetching collection types")(ds.run(implicit c => fetchCollectionTypes(c)))
      } yield {
        val input = Input(tables, columns, constraints, constraintColumns, analyzedViews, objectTypes, typeAttributes, collectionTypes)
        input.filter(schemaMode)
      }
    }

    private def analyzeViews(ds: TypoDataSource, viewSelector: Selector)(implicit ec: ExecutionContext): Future[Map[db.RelationName, AnalyzedView]] = {
      ds.run { implicit c =>
        val viewRows = fetchViews(c)
        viewRows.flatMap { viewRow =>
          val name = db.RelationName(viewRow.owner, viewRow.viewName)
          if (viewRow.text.isDefined && viewSelector.include(name)) {
            val sqlContent = viewRow.text.get
            val decomposedSql = DecomposedSql.parse(sqlContent)
            // Use JDBC metadata to get base table/column info for dependency tracking
            OracleJdbcMetadata.from(sqlContent) match {
              case Right(jdbcMetadata) =>
                Some(name -> AnalyzedView(viewRow, decomposedSql, jdbcMetadata))
              case Left(_) =>
                // If we can't analyze the view SQL, create an empty analysis
                Some(name -> AnalyzedView(viewRow, decomposedSql, OracleJdbcMetadata(MaybeReturnsRows.Update)))
            }
          } else None
        }.toMap
      }
    }

    private def fetchViews(implicit conn: Connection): List[OracleView] = {
      // Use raw JDBC to bypass Anorm's duplicate column name issue with Oracle dictionary views
      val stmt = conn.prepareStatement("""
        SELECT OWNER, VIEW_NAME, TEXT_LENGTH, TEXT
        FROM ALL_VIEWS
        WHERE OWNER NOT IN ('SYS', 'SYSTEM', 'OUTLN', 'DIP', 'ORACLE_OCM', 'DBSNMP', 'APPQOSSYS', 'WMSYS', 'EXFSYS', 'CTXSYS', 'XDB', 'MDSYS', 'ORDDATA', 'ORDSYS', 'OLAPSYS', 'APEX_040200', 'FLOWS_FILES', 'GSMADMIN_INTERNAL', 'LBACSYS', 'OJVMSYS', 'AUDSYS', 'REMOTE_SCHEDULER_AGENT')
        ORDER BY OWNER, VIEW_NAME
      """)
      try {
        val rs = stmt.executeQuery()
        try {
          val buffer = scala.collection.mutable.ListBuffer.empty[OracleView]
          while (rs.next()) {
            buffer += OracleView(
              owner = Option(rs.getString(1)),
              viewName = rs.getString(2),
              textLength = Option(rs.getLong(3)).filter(_ => !rs.wasNull()),
              text = Option(rs.getString(4))
            )
          }
          buffer.toList
        } finally rs.close()
      } finally stmt.close()
    }

    private def fetchTables(implicit conn: Connection): List[OracleTable] = {
      // Use raw JDBC to bypass Anorm's duplicate column name issue with Oracle dictionary views
      val stmt = conn.prepareStatement("""
        SELECT t.OWNER, t.TABLE_NAME, 'TABLE' as TABLE_TYPE, c.COMMENTS
        FROM ALL_TABLES t
        LEFT JOIN ALL_TAB_COMMENTS c ON t.OWNER = c.OWNER AND t.TABLE_NAME = c.TABLE_NAME
        WHERE t.OWNER NOT IN ('SYS', 'SYSTEM', 'OUTLN', 'DIP', 'ORACLE_OCM', 'DBSNMP', 'APPQOSSYS', 'WMSYS', 'EXFSYS', 'CTXSYS', 'XDB', 'MDSYS', 'ORDDATA', 'ORDSYS', 'OLAPSYS', 'APEX_040200', 'FLOWS_FILES', 'GSMADMIN_INTERNAL', 'LBACSYS', 'OJVMSYS', 'AUDSYS', 'REMOTE_SCHEDULER_AGENT')
        UNION ALL
        SELECT v.OWNER, v.VIEW_NAME, 'VIEW' as TABLE_TYPE, c.COMMENTS
        FROM ALL_VIEWS v
        LEFT JOIN ALL_TAB_COMMENTS c ON v.OWNER = c.OWNER AND v.VIEW_NAME = c.TABLE_NAME
        WHERE v.OWNER NOT IN ('SYS', 'SYSTEM', 'OUTLN', 'DIP', 'ORACLE_OCM', 'DBSNMP', 'APPQOSSYS', 'WMSYS', 'EXFSYS', 'CTXSYS', 'XDB', 'MDSYS', 'ORDDATA', 'ORDSYS', 'OLAPSYS', 'APEX_040200', 'FLOWS_FILES', 'GSMADMIN_INTERNAL', 'LBACSYS', 'OJVMSYS', 'AUDSYS', 'REMOTE_SCHEDULER_AGENT')
        ORDER BY 1, 2
      """)
      try {
        val rs = stmt.executeQuery()
        try {
          val buffer = scala.collection.mutable.ListBuffer.empty[OracleTable]
          while (rs.next()) {
            buffer += OracleTable(
              owner = Option(rs.getString(1)),
              tableName = rs.getString(2),
              tableType = rs.getString(3),
              comments = Option(rs.getString(4))
            )
          }
          buffer.toList
        } finally rs.close()
      } finally stmt.close()
    }

    private def fetchColumns(implicit conn: Connection): List[OracleColumn] = {
      // Use raw JDBC to bypass Anorm's duplicate column name issue with Oracle dictionary views
      val stmt = conn.prepareStatement("""
        SELECT c.OWNER, c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE,
               c.DATA_LENGTH, c.DATA_PRECISION, c.DATA_SCALE,
               c.NULLABLE, c.COLUMN_ID, c.DATA_DEFAULT, c.CHAR_LENGTH,
               cc.COMMENTS
        FROM ALL_TAB_COLUMNS c
        LEFT JOIN ALL_COL_COMMENTS cc ON c.OWNER = cc.OWNER AND c.TABLE_NAME = cc.TABLE_NAME AND c.COLUMN_NAME = cc.COLUMN_NAME
        WHERE c.OWNER NOT IN ('SYS', 'SYSTEM', 'OUTLN', 'DIP', 'ORACLE_OCM', 'DBSNMP', 'APPQOSSYS', 'WMSYS', 'EXFSYS', 'CTXSYS', 'XDB', 'MDSYS', 'ORDDATA', 'ORDSYS', 'OLAPSYS', 'APEX_040200', 'FLOWS_FILES', 'GSMADMIN_INTERNAL', 'LBACSYS', 'OJVMSYS', 'AUDSYS', 'REMOTE_SCHEDULER_AGENT')
        ORDER BY c.OWNER, c.TABLE_NAME, c.COLUMN_ID
      """)
      try {
        val rs = stmt.executeQuery()
        try {
          val buffer = scala.collection.mutable.ListBuffer.empty[OracleColumn]
          while (rs.next()) {
            buffer += OracleColumn(
              owner = Option(rs.getString(1)),
              tableName = rs.getString(2),
              columnName = rs.getString(3),
              dataType = rs.getString(4),
              dataLength = Option(rs.getInt(5)).filter(_ => !rs.wasNull()),
              dataPrecision = Option(rs.getInt(6)).filter(_ => !rs.wasNull()),
              dataScale = Option(rs.getInt(7)).filter(_ => !rs.wasNull()),
              nullable = rs.getString(8),
              columnId = rs.getInt(9),
              dataDefault = Option(rs.getString(10)),
              charLength = Option(rs.getInt(11)).filter(_ => !rs.wasNull()),
              comments = Option(rs.getString(12))
            )
          }
          buffer.toList
        } finally rs.close()
      } finally stmt.close()
    }

    private def fetchConstraints(implicit conn: Connection): List[OracleConstraint] = {
      // Use raw JDBC to bypass Anorm's duplicate column name issue with Oracle dictionary views
      val stmt = conn.prepareStatement("""
        SELECT OWNER, CONSTRAINT_NAME, CONSTRAINT_TYPE, TABLE_NAME, R_OWNER, R_CONSTRAINT_NAME
        FROM ALL_CONSTRAINTS
        WHERE OWNER NOT IN ('SYS', 'SYSTEM', 'OUTLN', 'DIP', 'ORACLE_OCM', 'DBSNMP', 'APPQOSSYS', 'WMSYS', 'EXFSYS', 'CTXSYS', 'XDB', 'MDSYS', 'ORDDATA', 'ORDSYS', 'OLAPSYS', 'APEX_040200', 'FLOWS_FILES', 'GSMADMIN_INTERNAL', 'LBACSYS', 'OJVMSYS', 'AUDSYS', 'REMOTE_SCHEDULER_AGENT')
          AND CONSTRAINT_TYPE IN ('P', 'R', 'U')
        ORDER BY OWNER, TABLE_NAME, CONSTRAINT_NAME
      """)
      try {
        val rs = stmt.executeQuery()
        try {
          val buffer = scala.collection.mutable.ListBuffer.empty[OracleConstraint]
          while (rs.next()) {
            buffer += OracleConstraint(
              owner = Option(rs.getString(1)),
              constraintName = rs.getString(2),
              constraintType = rs.getString(3),
              tableName = rs.getString(4),
              rOwner = Option(rs.getString(5)),
              rConstraintName = Option(rs.getString(6))
            )
          }
          buffer.toList
        } finally rs.close()
      } finally stmt.close()
    }

    private def fetchConstraintColumns(implicit conn: Connection): List[OracleConstraintColumn] = {
      val stmt = conn.prepareStatement("""
        SELECT c.OWNER, c.CONSTRAINT_NAME, c.TABLE_NAME, c.COLUMN_NAME, c.POSITION
        FROM ALL_CONS_COLUMNS c
        WHERE c.OWNER NOT IN ('SYS', 'SYSTEM', 'OUTLN', 'DIP', 'ORACLE_OCM', 'DBSNMP', 'APPQOSSYS', 'WMSYS', 'EXFSYS', 'CTXSYS', 'XDB', 'MDSYS', 'ORDDATA', 'ORDSYS', 'OLAPSYS', 'APEX_040200', 'FLOWS_FILES', 'GSMADMIN_INTERNAL', 'LBACSYS', 'OJVMSYS', 'AUDSYS', 'REMOTE_SCHEDULER_AGENT')
        ORDER BY c.OWNER, c.TABLE_NAME, c.CONSTRAINT_NAME, c.POSITION
      """)
      try {
        val rs = stmt.executeQuery()
        try {
          val buffer = scala.collection.mutable.ListBuffer.empty[OracleConstraintColumn]
          while (rs.next()) {
            buffer += OracleConstraintColumn(
              owner = Option(rs.getString(1)),
              constraintName = rs.getString(2),
              tableName = rs.getString(3),
              columnName = rs.getString(4),
              position = rs.getInt(5)
            )
          }
          buffer.toList
        } finally rs.close()
      } finally stmt.close()
    }

    private def fetchObjectTypes(implicit conn: Connection): List[OracleObjectType] = {
      // Use raw JDBC to bypass Anorm's duplicate column name issue with Oracle dictionary views
      val stmt = conn.prepareStatement("""
        SELECT OWNER, TYPE_NAME, TYPECODE,
               CASE WHEN FINAL = 'YES' THEN 1 ELSE 0 END as IS_FINAL,
               CASE WHEN INSTANTIABLE = 'YES' THEN 1 ELSE 0 END as IS_INSTANTIABLE,
               SUPERTYPE_OWNER, SUPERTYPE_NAME
        FROM ALL_TYPES
        WHERE OWNER NOT IN ('SYS', 'SYSTEM', 'OUTLN', 'DIP', 'ORACLE_OCM', 'DBSNMP', 'APPQOSSYS', 'WMSYS', 'EXFSYS', 'CTXSYS', 'XDB', 'MDSYS', 'ORDDATA', 'ORDSYS', 'OLAPSYS', 'APEX_040200', 'FLOWS_FILES', 'PUBLIC', 'GSMADMIN_INTERNAL', 'LBACSYS', 'OJVMSYS', 'AUDSYS', 'REMOTE_SCHEDULER_AGENT')
          AND TYPECODE IN ('OBJECT', 'COLLECTION')
        ORDER BY OWNER, TYPE_NAME
      """)
      try {
        val rs = stmt.executeQuery()
        try {
          val buffer = scala.collection.mutable.ListBuffer.empty[OracleObjectType]
          while (rs.next()) {
            buffer += OracleObjectType(
              owner = Option(rs.getString(1)),
              typeName = rs.getString(2),
              typecode = rs.getString(3),
              isFinal = rs.getInt(4) == 1,
              isInstantiable = rs.getInt(5) == 1,
              supertypeOwner = Option(rs.getString(6)),
              supertypeName = Option(rs.getString(7))
            )
          }
          buffer.toList
        } finally rs.close()
      } finally stmt.close()
    }

    private def fetchTypeAttributes(implicit conn: Connection): Map[String, List[OracleTypeAttribute]] = {
      // Use raw JDBC to bypass Anorm's duplicate column name issue with Oracle dictionary views
      val stmt = conn.prepareStatement("""
        SELECT OWNER, TYPE_NAME, ATTR_NAME, ATTR_TYPE_NAME, ATTR_TYPE_OWNER,
               ATTR_NO, LENGTH, PRECISION, SCALE
        FROM ALL_TYPE_ATTRS
        WHERE OWNER NOT IN ('SYS', 'SYSTEM', 'OUTLN', 'DIP', 'ORACLE_OCM', 'DBSNMP', 'APPQOSSYS', 'WMSYS', 'EXFSYS', 'CTXSYS', 'XDB', 'MDSYS', 'ORDDATA', 'ORDSYS', 'OLAPSYS', 'APEX_040200', 'FLOWS_FILES', 'PUBLIC', 'GSMADMIN_INTERNAL', 'LBACSYS', 'OJVMSYS', 'AUDSYS', 'REMOTE_SCHEDULER_AGENT')
        ORDER BY TYPE_NAME, ATTR_NO
      """)
      try {
        val rs = stmt.executeQuery()
        try {
          val buffer = scala.collection.mutable.ListBuffer.empty[OracleTypeAttribute]
          while (rs.next()) {
            buffer += OracleTypeAttribute(
              owner = Option(rs.getString(1)),
              typeName = rs.getString(2),
              attrName = rs.getString(3),
              attrTypeName = rs.getString(4),
              attrTypeOwner = Option(rs.getString(5)),
              attrNo = rs.getInt(6),
              length = Option(rs.getInt(7)).filter(_ => !rs.wasNull()),
              precision = Option(rs.getInt(8)).filter(_ => !rs.wasNull()),
              scale = Option(rs.getInt(9)).filter(_ => !rs.wasNull())
            )
          }
          buffer.toList.groupBy(_.typeName)
        } finally rs.close()
      } finally stmt.close()
    }

    private def fetchCollectionTypes(implicit conn: Connection): List[OracleCollectionType] = {
      // Use raw JDBC to bypass Anorm's duplicate column name issue with Oracle dictionary views
      val stmt = conn.prepareStatement("""
        SELECT OWNER, TYPE_NAME, COLL_TYPE, UPPER_BOUND,
               ELEM_TYPE_NAME, ELEM_TYPE_OWNER, LENGTH, PRECISION, SCALE
        FROM ALL_COLL_TYPES
        WHERE OWNER NOT IN ('SYS', 'SYSTEM', 'OUTLN', 'DIP', 'ORACLE_OCM', 'DBSNMP', 'APPQOSSYS', 'WMSYS', 'EXFSYS', 'CTXSYS', 'XDB', 'MDSYS', 'ORDDATA', 'ORDSYS', 'OLAPSYS', 'APEX_040200', 'FLOWS_FILES', 'PUBLIC', 'GSMADMIN_INTERNAL', 'LBACSYS', 'OJVMSYS', 'AUDSYS', 'REMOTE_SCHEDULER_AGENT')
        ORDER BY OWNER, TYPE_NAME
      """)
      try {
        val rs = stmt.executeQuery()
        try {
          val buffer = scala.collection.mutable.ListBuffer.empty[OracleCollectionType]
          while (rs.next()) {
            buffer += OracleCollectionType(
              owner = Option(rs.getString(1)),
              typeName = rs.getString(2),
              collType = rs.getString(3),
              upperBound = Option(rs.getInt(4)).filter(_ => !rs.wasNull()),
              elemTypeName = rs.getString(5),
              elemTypeOwner = Option(rs.getString(6)),
              length = Option(rs.getInt(7)).filter(_ => !rs.wasNull()),
              precision = Option(rs.getInt(8)).filter(_ => !rs.wasNull()),
              scale = Option(rs.getInt(9)).filter(_ => !rs.wasNull())
            )
          }
          buffer.toList
        } finally rs.close()
      } finally stmt.close()
    }
  }

  /** Build object types and collection types with recursive dependency resolution */
  private def buildUserDefinedTypes(input: Input): (Map[String, db.OracleType.ObjectType], Map[String, db.OracleType]) = {
    // Recursive approach to handle forward type references
    val basicMapper = OracleTypeMapperDb(Map.empty, Map.empty)
    val typesByName = scala.collection.mutable.Map.empty[String, db.OracleType]

    // Build a lookup map for faster type searches
    val collectionTypesByName: Map[String, OracleCollectionType] =
      input.collectionTypes.map(ct => ct.typeName.toUpperCase -> ct).toMap
    val objectTypesByName: Map[String, OracleObjectType] =
      input.objectTypes.map(ot => ot.typeName.toUpperCase -> ot).toMap

    def resolveType(typeName: String, typeOwner: Option[String]): db.Type = {
      val _ = typeOwner // TODO: use schema-qualified names for types in multi-schema mode
      val normalized = typeName.toUpperCase

      // First check if it's a user-defined type we've already built
      typesByName.get(normalized) match {
        case Some(userType) => userType
        case None           =>
          // Check if it's a collection type we need to build
          collectionTypesByName.get(normalized) match {
            case Some(ct) =>
              // Build this collection type recursively
              val elementType = resolveType(ct.elemTypeName, ct.elemTypeOwner)
              val collType = ct.collType match {
                case "VARRAY" | "VARYING ARRAY" =>
                  db.OracleType.VArray(
                    name = db.RelationName(ct.owner, ct.typeName),
                    maxSize = ct.upperBound.getOrElse(0),
                    elementType = elementType
                  )
                case "TABLE" =>
                  db.OracleType.NestedTable(
                    name = db.RelationName(ct.owner, ct.typeName),
                    elementType = elementType,
                    storageTable = None
                  )
                case other =>
                  throw new RuntimeException(s"Unknown collection type: $other")
              }
              typesByName.put(normalized, collType): Unit
              collType

            case None =>
              // Check if it's an object type we need to build
              objectTypesByName.get(normalized) match {
                case Some(ot) =>
                  // Build this object type recursively
                  val attrs = input.typeAttributes
                    .getOrElse(ot.typeName, Nil)
                    .sortBy(_.attrNo)
                    .map { attr =>
                      val attrType = resolveType(attr.attrTypeName, attr.attrTypeOwner)
                      db.OracleType.ObjectAttribute(
                        name = attr.attrName,
                        tpe = attrType,
                        position = attr.attrNo
                      )
                    }

                  val supertype = ot.supertypeName.map(stName => db.RelationName(ot.supertypeOwner, stName))

                  val objType = db.OracleType.ObjectType(
                    name = db.RelationName(ot.owner, ot.typeName),
                    attributes = attrs,
                    isFinal = ot.isFinal,
                    isInstantiable = ot.isInstantiable,
                    supertype = supertype
                  )
                  typesByName.put(normalized, objType): Unit
                  objType

                case None =>
                  // It's a built-in type
                  basicMapper.dbTypeFrom(
                    dataType = typeName,
                    dataLength = None,
                    dataScale = None,
                    dataPrecision = None,
                    charLength = None
                  )
              }
          }
      }
    }

    // Trigger building of all types by calling resolveType on each
    input.collectionTypes.foreach(ct => resolveType(ct.typeName, ct.owner))
    input.objectTypes.foreach(ot => resolveType(ot.typeName, ot.owner))

    // Extract just the object types for the separate map
    val objectTypes: Map[String, db.OracleType.ObjectType] = typesByName.collect { case (name, ot: db.OracleType.ObjectType) =>
      name -> ot
    }.toMap

    (objectTypes, typesByName.toMap)
  }

  def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode)(implicit ec: ExecutionContext): Future[MetaDb] = {
    Input.fromDb(logger, ds, viewSelector, schemaMode).map(input => fromInput(input))
  }

  def fromInput(input: Input): MetaDb = {
    // Build object types and collection types first so we can reference them in columns
    val (objectTypes, collectionTypes) = buildUserDefinedTypes(input)
    val typeMapper = OracleTypeMapperDb(objectTypes, collectionTypes)

    // Build primary keys (constraint_type = 'P')
    val primaryKeys: Map[db.RelationName, db.PrimaryKey] = {
      val pkConstraints = input.constraints.filter(_.constraintType == "P")

      pkConstraints.flatMap { c =>
        val cols = input.constraintColumns
          .filter(cc => cc.constraintName == c.constraintName && cc.owner == c.owner && cc.tableName == c.tableName)
          .sortBy(_.position)
          .map(cc => db.ColName(cc.columnName))

        NonEmptyList.fromList(cols).map { nelCols =>
          val name = db.RelationName(c.owner, c.tableName)
          val constraintName = db.RelationName(c.owner, c.constraintName)
          name -> db.PrimaryKey(nelCols, constraintName)
        }
      }.toMap
    }

    // Build foreign keys (constraint_type = 'R')
    val foreignKeys: Map[db.RelationName, List[db.ForeignKey]] = {
      val fkConstraints = input.constraints.filter(_.constraintType == "R")

      // Get referenced constraint info - use (owner, constraintName) as key
      val refConstraints: Map[(Option[String], String), OracleConstraint] =
        input.constraints.map(c => (c.owner, c.constraintName) -> c).toMap

      fkConstraints
        .flatMap { c =>
          val cols = input.constraintColumns
            .filter(cc => cc.constraintName == c.constraintName && cc.owner == c.owner && cc.tableName == c.tableName)
            .sortBy(_.position)

          // Get referenced constraint - lookup by (rOwner or owner, rConstraintName)
          val refOwner: Option[String] = c.rOwner.orElse(c.owner)
          val refConstraint: Option[OracleConstraint] = c.rConstraintName.flatMap(rcn => refConstraints.get((refOwner, rcn)))

          refConstraint.flatMap { rc =>
            val refCols = input.constraintColumns
              .filter(cc => cc.constraintName == rc.constraintName && cc.owner == rc.owner && cc.tableName == rc.tableName)
              .sortBy(_.position)

            if (cols.nonEmpty && refCols.nonEmpty && cols.length == refCols.length) {
              val fkCols = cols.map(cc => db.ColName(cc.columnName))
              val refColNames = refCols.map(cc => db.ColName(cc.columnName))
              val refTable = db.RelationName(rc.owner, rc.tableName)

              NonEmptyList.fromList(fkCols).flatMap { nelCols =>
                NonEmptyList.fromList(refColNames).map { nelRefCols =>
                  val name = db.RelationName(c.owner, c.tableName)
                  val constraintName = db.RelationName(c.owner, c.constraintName)
                  name -> db.ForeignKey(nelCols, refTable, nelRefCols, constraintName)
                }
              }
            } else None
          }
        }
        .groupBy(_._1)
        .map { case (k, v) => k -> v.map(_._2).toList }
    }

    // Build unique keys (constraint_type = 'U')
    val uniqueKeys: Map[db.RelationName, List[db.UniqueKey]] = {
      val ukConstraints = input.constraints.filter(_.constraintType == "U")

      ukConstraints
        .flatMap { c =>
          val cols = input.constraintColumns
            .filter(cc => cc.constraintName == c.constraintName && cc.owner == c.owner && cc.tableName == c.tableName)
            .sortBy(_.position)
            .map(cc => db.ColName(cc.columnName))

          NonEmptyList.fromList(cols).map { nelCols =>
            val name = db.RelationName(c.owner, c.tableName)
            val constraintName = db.RelationName(c.owner, c.constraintName)
            name -> db.UniqueKey(nelCols, constraintName)
          }
        }
        .groupBy(_._1)
        .map { case (k, v) => k -> v.map(_._2).toList }
    }

    // Group columns by table
    val columnsByTable: Map[db.RelationName, List[OracleColumn]] =
      input.columns.groupBy(c => db.RelationName(c.owner, c.tableName))

    // Build tables (excluding views)
    val baseTables = input.tables.filter(_.tableType == "TABLE")

    val tables: Map[db.RelationName, Lazy[db.Table]] =
      baseTables.flatMap { table =>
        val relationName = db.RelationName(table.owner, table.tableName)

        NonEmptyList.fromList(columnsByTable.getOrElse(relationName, Nil).sortBy(_.columnId)).map { columns =>
          val lazyAnalysis = Lazy {
            val mappedCols: NonEmptyList[db.Col] = columns.map { c =>
              val parsedName = ParsedName.of(c.columnName)

              val nullability = c.nullable match {
                case "Y" => Nullability.Nullable
                case "N" => Nullability.NoNulls
                case _   => Nullability.NullableUnknown
              }

              val tpe = typeMapper.dbTypeFrom(
                dataType = c.dataType,
                dataLength = c.dataLength,
                dataScale = c.dataScale,
                dataPrecision = c.dataPrecision,
                charLength = c.charLength
              )

              // Detect identity columns (Oracle 12c+)
              // In Oracle, identity columns have DATA_DEFAULT containing IDENTITY
              val generated: Option[db.Generated] =
                c.dataDefault.filter(_.contains("IDENTITY")).map { default =>
                  db.Generated.IsGenerated("ALWAYS", Some(default))
                }

              db.Col(
                parsedName = parsedName,
                tpe = tpe,
                udtName = Some(c.dataType),
                nullability = nullability,
                columnDefault = c.dataDefault.filterNot(_.contains("IDENTITY")),
                maybeGenerated = generated,
                comment = c.comments,
                constraints = Nil, // Oracle doesn't have PostgreSQL-style check constraints easily accessible
                jsonDescription = DebugJson(Map("dataType" -> c.dataType, "dataLength" -> c.dataLength.map(_.toString).getOrElse(""), "dataPrecision" -> c.dataPrecision.map(_.toString).getOrElse("")))
              )
            }

            db.Table(
              name = relationName,
              comment = table.comments,
              cols = mappedCols,
              primaryKey = primaryKeys.get(relationName),
              uniqueKeys = uniqueKeys.getOrElse(relationName, Nil),
              foreignKeys = foreignKeys.getOrElse(relationName, Nil)
            )
          }

          relationName -> lazyAnalysis
        }
      }.toMap

    // Build views from analyzed view data
    val viewTables = input.tables.filter(_.tableType == "VIEW")

    val views: Map[db.RelationName, Lazy[db.View]] =
      viewTables.flatMap { view =>
        val relationName = db.RelationName(view.owner, view.tableName)

        // Try to find analyzed view data (with JDBC metadata for dependencies)
        val maybeAnalyzed = input.analyzedViews.get(relationName)

        NonEmptyList.fromList(columnsByTable.getOrElse(relationName, Nil).sortBy(_.columnId)).map { columns =>
          val lazyAnalysis = Lazy {
            // Build deps from JDBC metadata if available
            val deps: Map[db.ColName, List[(db.RelationName, db.ColName)]] =
              maybeAnalyzed.map(_.jdbcMetadata.columns) match {
                case Some(MaybeReturnsRows.Query(metadataCols)) =>
                  metadataCols.toList.flatMap { col =>
                    col.baseRelationName.zip(col.baseColumnName).map(t => col.name -> List(t))
                  }.toMap
                case _ =>
                  Map.empty
              }

            val decomposedSql = maybeAnalyzed.map(_.decomposedSql).getOrElse(DecomposedSql(Nil))

            val cols: NonEmptyList[(db.Col, ParsedName)] = columns.map { c =>
              val parsedName = ParsedName.of(c.columnName)

              val nullability = c.nullable match {
                case "Y" => Nullability.Nullable
                case "N" => Nullability.NoNulls
                case _   => Nullability.NullableUnknown
              }

              val tpe = typeMapper.dbTypeFrom(
                dataType = c.dataType,
                dataLength = c.dataLength,
                dataScale = c.dataScale,
                dataPrecision = c.dataPrecision,
                charLength = c.charLength
              )

              val col = db.Col(
                parsedName = parsedName,
                tpe = tpe,
                udtName = Some(c.dataType),
                nullability = nullability,
                columnDefault = c.dataDefault,
                maybeGenerated = None,
                comment = c.comments,
                constraints = Nil,
                jsonDescription = DebugJson(Map("dataType" -> c.dataType))
              )

              (col, parsedName)
            }

            db.View(
              name = relationName,
              comment = view.comments,
              decomposedSql = decomposedSql,
              cols = cols,
              deps = deps,
              isMaterialized = false // Oracle has materialized views but detection would need additional query
            )
          }

          relationName -> lazyAnalysis
        }
      }.toMap

    // Oracle doesn't have PostgreSQL-style enums or domains, but has object types and collection types
    MetaDb(
      dbType = DbType.Oracle,
      relations = tables ++ views,
      enums = Nil,
      domains = Nil,
      oracleObjectTypes = objectTypes,
      oracleCollectionTypes = collectionTypes
    )
  }
}
