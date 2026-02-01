package typr

import typr.internal.db2.{Db2MetaDb, Db2TypeMapperDb}
import typr.internal.duckdb.{DuckDbMetaDb, DuckDbTypeMapperDb}
import typr.internal.external.ExternalTools
import typr.internal.mariadb.{MariaMetaDb, MariaTypeMapperDb}
import typr.internal.oracle.{OracleMetaDb, OracleTypeMapperDb}
import typr.internal.pg.{PgMetaDb, PgTypeMapperDb}
import typr.internal.sqlserver.{SqlServerMetaDb, SqlServerTypeMapperDb}
import typr.internal.{Lazy, TypeMapperDb}

import scala.concurrent.{ExecutionContext, Future}

/** Named DuckDB STRUCT type extracted from columns.
  *
  * @param name
  *   Generated name for the struct (e.g., "employee_contact_info" from table.column)
  * @param structType
  *   The underlying StructType with field definitions
  */
case class DuckDbNamedStruct(name: String, structType: db.DuckDbType.StructType)

/** PostgreSQL composite type (CREATE TYPE name AS (...))
  *
  * Unlike DuckDB structs which are anonymous, PostgreSQL composite types are named at definition time. We store the type info directly since it already has a qualified name.
  */
case class PgCompositeType(compositeType: db.PgType.CompositeType)

case class MetaDb(
    dbType: DbType,
    relations: Map[db.RelationName, Lazy[db.Relation]],
    enums: List[db.StringEnum],
    domains: List[db.Domain],
    oracleObjectTypes: Map[String, db.OracleType.ObjectType] = Map.empty,
    oracleCollectionTypes: Map[String, db.OracleType] = Map.empty,
    duckDbStructTypes: List[DuckDbNamedStruct] = Nil,
    pgCompositeTypes: List[PgCompositeType] = Nil,
    mariaSetTypes: List[db.MariaType.Set] = Nil
) {
  val typeMapperDb: TypeMapperDb = dbType match {
    case DbType.PostgreSQL => PgTypeMapperDb(enums, domains, pgCompositeTypes)
    case DbType.MariaDB    => MariaTypeMapperDb()
    case DbType.DuckDB     => DuckDbTypeMapperDb()
    case DbType.Oracle     => OracleTypeMapperDb(oracleObjectTypes, oracleCollectionTypes)
    case DbType.SqlServer  => SqlServerTypeMapperDb(domains)
    case DbType.DB2        => Db2TypeMapperDb()
  }
}

object MetaDb {
  // Re-export PostgreSQL-specific types for backwards compatibility
  type Input = PgMetaDb.Input
  val Input = PgMetaDb.Input

  type AnalyzedView = PgMetaDb.AnalyzedView
  val AnalyzedView = PgMetaDb.AnalyzedView

  /** Extract named STRUCT types from all columns in relations.
    *
    * STRUCT types are anonymous in column definitions, so we generate names based on table+column. Names use Naming.titleCase for consistency with other generated types.
    *
    * The extracted structs are stored separately and used during type mapping - column types remain unchanged (no rewriting).
    */
  def extractDuckDbStructTypes(relations: Map[db.RelationName, Lazy[db.Relation]]): List[DuckDbNamedStruct] = {
    val structs = for {
      (relName, lazyRel) <- relations.toList
      rel <- lazyRel.get.toList
      table <- (rel match {
        case t: db.Table => Some(t)
        case _           => None
      }).toList
      col <- table.cols.toList
      structType <- extractStructType(col.tpe).toList
    } yield {
      // Generate name from table + column using Naming.titleCase for consistency
      val name = Naming.titleCase(Array(relName.name, col.parsedName.originalName.value))
      DuckDbNamedStruct(name, structType)
    }
    // Deduplicate by struct signature (fields) - compatible with Scala 2.12
    structs
      .groupBy(s => s.structType.fields.map { case (n, t) => (n, t.toString) })
      .values
      .flatMap(_.headOption)
      .toList
  }

  /** Recursively extract StructType from a db.Type (handles nested structs in lists/maps) */
  private def extractStructType(tpe: db.Type): Option[db.DuckDbType.StructType] = tpe match {
    case s: db.DuckDbType.StructType   => Some(s)
    case db.DuckDbType.ListType(inner) => extractStructType(inner)
    case db.DuckDbType.MapType(_, v)   => extractStructType(v)
    case _                             => None
  }

  /** Build a lookup map from StructType to its generated name.
    *
    * Used during type mapping to resolve anonymous StructTypes to their generated type names.
    */
  def buildStructLookup(structs: List[DuckDbNamedStruct]): Map[db.DuckDbType.StructType, String] =
    structs.map(ns => ns.structType -> ns.name).toMap

  /** Extract unique MariaDB SET types from all columns in relations.
    *
    * SET types are deduplicated by their sorted values (which determines the generated type name).
    */
  def extractMariaSetTypes(relations: Map[db.RelationName, Lazy[db.Relation]]): List[db.MariaType.Set] = {
    val sets = for {
      (_, lazyRel) <- relations.toList
      rel <- lazyRel.get.toList
      table <- (rel match {
        case t: db.Table => Some(t)
        case _           => None
      }).toList
      col <- table.cols.toList
      setType <- extractSetType(col.tpe).toList
    } yield setType
    // Deduplicate by sorted values
    sets.groupBy(s => s.values.sorted).values.flatMap(_.headOption).toList
  }

  /** Extract SET type from a db.Type */
  private def extractSetType(tpe: db.Type): Option[db.MariaType.Set] = tpe match {
    case s: db.MariaType.Set => Some(s)
    case _                   => None
  }

  /** Build a lookup map from sorted SET values to the SET type.
    *
    * Used during type mapping to resolve SET types to their generated type names.
    */
  def buildMariaSetLookup(sets: List[db.MariaType.Set]): Map[List[String], db.MariaType.Set] =
    sets.map(s => s.values.sorted -> s).toMap

  /** Load metadata from database, dispatching to the appropriate implementation based on database type */
  def fromDb(logger: TypoLogger, ds: TypoDataSource, viewSelector: Selector, schemaMode: SchemaMode, externalTools: ExternalTools)(implicit ec: ExecutionContext): Future[MetaDb] =
    ds.dbType match {
      case DbType.PostgreSQL => PgMetaDb.fromDb(logger, ds, viewSelector, schemaMode, externalTools)
      case DbType.MariaDB    => MariaMetaDb.fromDb(logger, ds, viewSelector, schemaMode)
      case DbType.DuckDB     => DuckDbMetaDb.fromDb(logger, ds, viewSelector, schemaMode)
      case DbType.Oracle     => OracleMetaDb.fromDb(logger, ds, viewSelector, schemaMode)
      case DbType.SqlServer  => SqlServerMetaDb.fromDb(logger, ds, viewSelector, schemaMode)
      case DbType.DB2        => Db2MetaDb.fromDb(logger, ds, viewSelector, schemaMode)
    }

  /** Load metadata from PostgreSQL-specific input (for backwards compatibility) */
  def fromInput(logger: TypoLogger, input: Input): MetaDb =
    PgMetaDb.fromInput(logger, input)
}
