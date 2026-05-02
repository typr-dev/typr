package scripts

import ryddig.{Formatter, LogLevel, LogPatterns, Loggers}
import scripts.showcase.ShowcaseSchema
import typr.*
import typr.internal.codegen.*
import typr.internal.pg.OpenEnum
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
        val dbTypes = List(
          DbType.PostgreSQL,
          DbType.MariaDB,
          DbType.DuckDB,
          DbType.Oracle,
          DbType.SqlServer,
          DbType.DB2
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

            val metadb = buildMetaDb(dbType)
            logger.warn(s"  Built ${metadb.relations.size} relations from schema")

            generateForDb(dbName, metadb, logger)
          } catch {
            case e: Exception =>
              logger.error(s"Failed to generate showcase for $dbName: ${e.getMessage}")
          }
        }

        formatJavaFiles(logger)

        logger.warn("Showcase code generation complete!")
      }

  /** Format Java files with google-java-format via coursier */
  def formatJavaFiles(logger: ryddig.Logger): Unit = {
    import coursier.cache.FileCache
    import coursier.util.{Artifact, Task}
    import scala.concurrent.{Await, ExecutionContext}
    import scala.concurrent.duration.Duration

    try {
      logger.warn("Formatting Java files with google-java-format...")
      val javaFiles = java.nio.file.Files
        .walk(buildDir.resolve("site/showcase-generated"))
        .filter(p => p.toString.endsWith(".java"))
        .toArray
        .map(_.toString)
        .toSeq

      if (javaFiles.nonEmpty) {
        val version = "1.25.2"
        val ec: ExecutionContext = ExecutionContext.global
        val fileCache = FileCache[Task]()
        val artifact = Artifact(s"https://github.com/google/google-java-format/releases/download/v$version/google-java-format-$version-all-deps.jar")
        val jar = Await.result(fileCache.file(artifact).run.value(ec), Duration.Inf) match {
          case Left(err)   => throw new Exception(s"Failed to fetch google-java-format: $err")
          case Right(file) => file.toPath
        }

        val jvmFlags = List(
          "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
          "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
        )
        val cmd = Seq("java") ++ jvmFlags ++ Seq("-jar", jar.toString, "--replace") ++ javaFiles
        import scala.sys.process._
        val result = cmd.!
        if (result == 0) {
          logger.warn(s"  Formatted ${javaFiles.size} Java files")
        }
      }
    } catch {
      case e: Exception =>
        logger.error(s"Failed to format Java files: ${e.getMessage}")
    }
  }

  /** Build a MetaDb directly from ShowcaseSchema definitions. No database connection required. */
  def buildMetaDb(dbType: DbType): MetaDb = {
    val allRelations = ShowcaseSchema.relations(dbType)
    val enums = ShowcaseSchema.enums(dbType)
    val domains = ShowcaseSchema.domains(dbType)

    val relations: Map[db.RelationName, Lazy[db.Relation]] =
      allRelations.map { relation =>
        relation.name -> Lazy(relation)
      }.toMap

    val duckDbStructs = if (dbType == DbType.DuckDB) {
      MetaDb.extractDuckDbStructTypes(relations)
    } else {
      Nil
    }

    val (oracleCollections, oracleObjects) = if (dbType == DbType.Oracle) {
      val collections = scala.collection.mutable.Map.empty[String, db.OracleType]
      val objects = scala.collection.mutable.Map.empty[String, db.OracleType.ObjectType]

      def extractObjectType(tpe: db.Type): Unit = tpe match {
        case o: db.OracleType.ObjectType => objects += (o.name.value -> o)
        case _                           => ()
      }

      for {
        (_, lazyRel) <- relations.toList
        rel <- lazyRel.get.toList
        table <- (rel match {
          case t: db.Table => Some(t)
          case _           => None
        }).toList
        col <- table.cols.toList
      } col.tpe match {
        case v: db.OracleType.VArray =>
          collections += (v.name.value -> v)
          extractObjectType(v.elementType)
        case n: db.OracleType.NestedTable =>
          collections += (n.name.value -> n)
          extractObjectType(n.elementType)
        case o: db.OracleType.ObjectType =>
          objects += (o.name.value -> o)
        case _ => ()
      }
      (collections.toMap, objects.toMap)
    } else {
      (Map.empty[String, db.OracleType], Map.empty[String, db.OracleType.ObjectType])
    }

    val pgComposites = if (dbType == DbType.PostgreSQL) {
      val composites = for {
        (_, lazyRel) <- relations.toList
        rel <- lazyRel.get.toList
        table <- (rel match {
          case t: db.Table => Some(t)
          case _           => None
        }).toList
        col <- table.cols.toList
        composite <- (col.tpe match {
          case c: db.PgType.CompositeType => Some(PgCompositeType(c))
          case _                          => None
        }).toList
      } yield composite
      composites.distinct
    } else {
      Nil
    }

    MetaDb(
      dbType = dbType,
      relations = relations,
      enums = enums,
      domains = domains,
      oracleObjectTypes = oracleObjects,
      oracleCollectionTypes = oracleCollections,
      duckDbStructTypes = duckDbStructs,
      pgCompositeTypes = pgComposites
    )
  }

  /** Generate showcase code for a database */
  def generateForDb(dbName: String, metadb: MetaDb, logger: ryddig.Logger): Unit = {
    val titleRelationName = db.RelationName(Some("showcase"), "title")
    val openEnumsByTable: Map[db.RelationName, OpenEnum] = Map(
      titleRelationName -> OpenEnum.Text(ShowcaseSchema.titleOpenEnumValues)
    )

    variants.foreach { case (lang, dbLib, langName) =>
      val options = Options(
        pkg = "showcase",
        lang = lang,
        dbLib = Some(dbLib),
        jsonLibs = Nil,
        generateMockRepos = Selector.All,
        enablePrimaryKeyType = Selector.All,
        enableTestInserts = Selector.All,
        enableDsl = true,
        openEnums = Selector.relationNames("title")
      )
      val targetSources = buildDir.resolve(s"site/showcase-generated/$dbName/$langName")

      val allGenerated: List[Generated] =
        generate.orThrow(options, metadb, ProjectGraph(name = "", targetSources, None, Selector.All, Nil, Nil), openEnumsByTable)

      val changedFiles = allGenerated.flatMap { generated =>
        generated
          .overwriteFolder(softWrite = FileSync.SoftWrite.Yes(Set.empty))
          .filter { case (_, synced) => synced != FileSync.Synced.Unchanged }
      }

      if (changedFiles.nonEmpty) {
        changedFiles.foreach { case (path, synced) =>
          logger.withContext("path", path).info(synced.toString)
        }
        logger.warn(s"  Generated ${changedFiles.size} files for $dbName/$langName")
      }
    }
  }
}
