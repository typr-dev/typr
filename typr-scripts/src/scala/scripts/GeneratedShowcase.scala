package scripts

import ryddig.{Formatter, LogLevel, LogPatterns, Loggers}
import scripts.showcase.ShowcaseSchema
import typr.*
import typr.internal.codegen.*
import typr.internal.{FileSync, Lazy, generate}

import java.nio.file.Path

/** Self-contained showcase code generation for all databases.
  *
  * This script generates showcase code directly from ShowcaseSchema definitions without requiring any database connections. It builds MetaDb instances programmatically from the schema definitions.
  */
object GeneratedShowcase {
  val buildDir: Path = Path.of(sys.props("user.dir"))

  // clickable links in intellij
  implicit val PathFormatter: Formatter[Path] = _.toUri.toString

  /** Language variants for showcase */
  val variants: Seq[(Lang, DbLibName, String)] = List(
    (LangJava, DbLibName.Typo, "java"),
    (LangScala.scalaDsl(Dialect.Scala3, TypeSupportScala), DbLibName.Typo, "scala"),
    (LangKotlin(TypeSupportKotlin), DbLibName.Typo, "kotlin")
  )

  def main(args: Array[String]): Unit =
    Loggers
      .stdout(LogPatterns.interface(None, noColor = false), disableProgress = true)
      .map(_.withMinLogLevel(LogLevel.info))
      .use { logger =>
        // Generate showcase for each database type
        val dbTypes = List(
          DbType.PostgreSQL,
          DbType.MariaDB,
          DbType.DuckDB,
          DbType.Oracle,
          DbType.SqlServer
        )

        dbTypes.foreach { dbType =>
          val dbName = dbType match {
            case DbType.PostgreSQL => "postgres"
            case DbType.MariaDB    => "mariadb"
            case DbType.DuckDB     => "duckdb"
            case DbType.Oracle     => "oracle"
            case DbType.SqlServer  => "sqlserver"
            case DbType.DB2        => "db2"
          }

          try {
            logger.warn(s"Generating showcase for $dbName...")

            // Build MetaDb directly from ShowcaseSchema - no database connection needed!
            val metadb = buildMetaDb(dbType)
            logger.warn(s"  Built ${metadb.relations.size} relations from schema")

            generateForDb(dbName, metadb, logger)
          } catch {
            case e: Exception =>
              logger.error(s"Failed to generate showcase for $dbName: ${e.getMessage}")
          }
        }

        logger.warn("Showcase code generation complete!")
      }

  /** Build a MetaDb directly from ShowcaseSchema definitions.
    *
    * No database connection required - we already have all the metadata we need.
    */
  def buildMetaDb(dbType: DbType): MetaDb = {
    // Get all relations (tables + views)
    val allRelations = ShowcaseSchema.relations(dbType)
    val enums = ShowcaseSchema.enums(dbType)

    // Convert to relations map
    val relations: Map[db.RelationName, Lazy[db.Relation]] =
      allRelations.map { relation =>
        relation.name -> Lazy(relation)
      }.toMap

    // For DuckDB: extract STRUCT types (column types remain unchanged)
    val duckDbStructs = if (dbType == DbType.DuckDB) {
      MetaDb.extractDuckDbStructTypes(relations)
    } else {
      Nil
    }

    MetaDb(
      dbType = dbType,
      relations = relations,
      enums = enums,
      domains = Nil,
      oracleObjectTypes = Map.empty,
      oracleCollectionTypes = Map.empty,
      duckDbStructTypes = duckDbStructs
    )
  }

  /** Generate showcase code for a database */
  def generateForDb(dbName: String, metadb: MetaDb, logger: ryddig.Logger): Unit = {
    variants.foreach { case (lang, dbLib, langName) =>
      val options = Options(
        pkg = "showcase",
        lang = lang,
        dbLib = Some(dbLib),
        jsonLibs = Nil,
        generateMockRepos = Selector.All,
        enablePrimaryKeyType = Selector.All,
        enableTestInserts = Selector.All,
        enableDsl = true
      )
      // Output to showcase-generated (outside site folder, gitignored)
      val targetSources = buildDir.resolve(s"showcase-generated/$dbName/$langName")

      val newFiles: Generated =
        generate.orThrow(options, metadb, ProjectGraph(name = "", targetSources, None, Selector.All, Nil, Nil), Map.empty).head

      val changedFiles = newFiles
        .overwriteFolder(softWrite = FileSync.SoftWrite.Yes(Set.empty))
        .filter { case (_, synced) => synced != FileSync.Synced.Unchanged }

      if (changedFiles.nonEmpty) {
        changedFiles.foreach { case (path, synced) =>
          logger.withContext("path", path).info(synced.toString)
        }
        logger.warn(s"  Generated ${changedFiles.size} files for $dbName/$langName")
      }
    }
  }
}
