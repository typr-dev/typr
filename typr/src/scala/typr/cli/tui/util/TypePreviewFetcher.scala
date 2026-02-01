package typr.cli.tui.util

import io.circe.Json
import typr.*
import typr.cli.config.ConfigParser
import typr.cli.config.ConfigToOptions
import typr.cli.config.ParsedSource
import typr.cli.tui.TypePreviewStatus
import typr.config.generated.DatabaseSource
import typr.config.generated.DuckdbSource
import typr.config.generated.FieldType
import typr.internal.TypeMatcher
import typr.internal.external.ExternalTools
import typr.internal.external.ExternalToolsConfig

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success

class TypePreviewFetcher {
  private val results = new ConcurrentHashMap[String, TypePreviewStatus]()
  private val activeFetches = new AtomicReference[Set[String]](Set.empty)

  def getResults: Map[String, TypePreviewStatus] = results.asScala.toMap

  def getResult(sourceName: String): TypePreviewStatus =
    results.getOrDefault(sourceName, TypePreviewStatus.Pending)

  def fetchPreview(
      typeName: String,
      typeDef: FieldType,
      sources: Map[String, Json]
  ): Unit = {
    sources.foreach { case (sourceName, sourceJson) =>
      if (!activeFetches.get().contains(sourceName) && !results.containsKey(sourceName)) {
        activeFetches.updateAndGet(_ + sourceName)
        results.put(sourceName, TypePreviewStatus.Fetching)

        Future {
          ConfigParser.parseSource(sourceJson) match {
            case Right(ParsedSource.Database(dbConfig)) =>
              fetchDatabasePreview(typeName, typeDef, sourceName, dbConfig)
            case Right(ParsedSource.DuckDb(duckConfig)) =>
              fetchDuckDbPreview(typeName, typeDef, sourceName, duckConfig)
            case Right(_) =>
              TypePreviewStatus.Loaded(0, Nil)
            case Left(error) =>
              throw new Exception(error)
          }
        }.onComplete {
          case Success(status) =>
            results.put(sourceName, status)
            activeFetches.updateAndGet(_ - sourceName)
          case Failure(e) =>
            val msg = Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
            results.put(sourceName, TypePreviewStatus.Failed(msg))
            activeFetches.updateAndGet(_ - sourceName)
        }
      }
    }
  }

  def reset(): Unit = {
    results.clear()
    activeFetches.set(Set.empty)
  }

  private def fetchDatabasePreview(
      typeName: String,
      typeDef: FieldType,
      sourceName: String,
      dbConfig: DatabaseSource
  ): TypePreviewStatus = {
    val sourceConfig = ConfigToOptions.convertDatabaseSource(sourceName, dbConfig) match {
      case Right(cfg) => cfg
      case Left(err)  => throw new Exception(err)
    }

    val dataSource = buildDataSource(dbConfig)

    try {
      val logger = TypoLogger.Noop
      val externalTools = ExternalTools.init(logger, ExternalToolsConfig.default)

      val metadb = Await.result(
        MetaDb.fromDb(logger, dataSource, sourceConfig.selector, sourceConfig.schemaMode, externalTools),
        Duration.Inf
      )

      matchColumnsInMetaDb(typeName, typeDef, sourceName, metadb)
    } finally {
      dataSource.ds.asInstanceOf[com.zaxxer.hikari.HikariDataSource].close()
    }
  }

  private def fetchDuckDbPreview(
      typeName: String,
      typeDef: FieldType,
      sourceName: String,
      duckConfig: DuckdbSource
  ): TypePreviewStatus = {
    val sourceConfig = ConfigToOptions.convertDuckDbSource(sourceName, duckConfig) match {
      case Right(cfg) => cfg
      case Left(err)  => throw new Exception(err)
    }

    val dataSource = TypoDataSource.hikariDuckDbInMemory(duckConfig.path)

    try {
      val logger = TypoLogger.Noop
      val externalTools = ExternalTools.init(logger, ExternalToolsConfig.default)

      duckConfig.schema_sql.foreach { schemaPath =>
        val conn = dataSource.ds.getConnection
        try {
          val stmt = conn.createStatement()
          val sql = java.nio.file.Files.readString(java.nio.file.Paths.get(schemaPath))
          stmt.execute(sql)
          stmt.close()
        } finally {
          conn.close()
        }
      }

      val metadb = Await.result(
        MetaDb.fromDb(logger, dataSource, sourceConfig.selector, sourceConfig.schemaMode, externalTools),
        Duration.Inf
      )

      matchColumnsInMetaDb(typeName, typeDef, sourceName, metadb)
    } finally {
      dataSource.ds.asInstanceOf[com.zaxxer.hikari.HikariDataSource].close()
    }
  }

  private def matchColumnsInMetaDb(
      typeName: String,
      typeDef: FieldType,
      sourceName: String,
      metadb: MetaDb
  ): TypePreviewStatus = {
    val entry = TypeEntry(
      name = typeName,
      db = toDbMatch(typeDef),
      model = ModelMatch.Empty,
      api = ApiMatch.Empty
    )

    val definitions = TypeDefinitions(List(entry))
    val matches = scala.collection.mutable.ListBuffer[String]()

    metadb.relations.foreach { case (relName, lazyRel) =>
      lazyRel.forceGet match {
        case table: db.Table =>
          table.cols.toList.foreach { col =>
            val ctx = TypeMatcher.DbColumnContext.from(sourceName, table, col)
            val columnMatches = TypeMatcher.findDbMatches(definitions, ctx)
            if (columnMatches.nonEmpty) {
              val fullName = relName.schema match {
                case Some(s) => s"$s.${relName.name}.${col.name.value}"
                case None    => s"${relName.name}.${col.name.value}"
              }
              matches += fullName
            }
          }
        case _: db.View =>
      }
    }

    val sampleMatches = matches.take(5).toList
    TypePreviewStatus.Loaded(matches.size, sampleMatches)
  }

  private def toDbMatch(typeDef: FieldType): DbMatch = {
    typeDef.db match {
      case None => DbMatch.Empty
      case Some(dbMatch) =>
        DbMatch(
          database = toPatterns(dbMatch.source),
          schema = toPatterns(dbMatch.schema),
          table = toPatterns(dbMatch.table),
          column = toPatterns(dbMatch.column),
          dbType = toPatterns(dbMatch.db_type),
          domain = toPatterns(dbMatch.domain),
          primaryKey = dbMatch.primary_key,
          nullable = dbMatch.nullable,
          references = toPatterns(dbMatch.references),
          comment = toPatterns(dbMatch.comment),
          annotation = toPatterns(dbMatch.annotation)
        )
    }
  }

  private def toPatterns(opt: Option[typr.config.generated.StringOrArray]): List[String] = {
    import typr.config.generated.*
    opt match {
      case None                          => Nil
      case Some(StringOrArrayString(s))  => List(s)
      case Some(StringOrArrayArray(arr)) => arr
    }
  }

  private def buildDataSource(dbConfig: DatabaseSource): TypoDataSource = {
    val dbType = dbConfig.`type`.getOrElse("unknown")
    val host = dbConfig.host.getOrElse("localhost")
    val database = dbConfig.database.getOrElse("")
    val username = dbConfig.username.getOrElse("")
    val password = dbConfig.password.getOrElse("")

    dbType match {
      case "postgresql" =>
        val port = dbConfig.port.map(_.toInt).getOrElse(5432)
        TypoDataSource.hikariPostgres(host, port, database, username, password)

      case "mariadb" | "mysql" =>
        val port = dbConfig.port.map(_.toInt).getOrElse(3306)
        TypoDataSource.hikariMariaDb(host, port, database, username, password)

      case "sqlserver" =>
        val port = dbConfig.port.map(_.toInt).getOrElse(1433)
        TypoDataSource.hikariSqlServer(host, port, database, username, password)

      case "oracle" =>
        val port = dbConfig.port.map(_.toInt).getOrElse(1521)
        val serviceName = dbConfig.service.orElse(dbConfig.sid).getOrElse("")
        TypoDataSource.hikariOracle(host, port, serviceName, username, password)

      case "db2" =>
        val port = dbConfig.port.map(_.toInt).getOrElse(50000)
        TypoDataSource.hikariDb2(host, port, database, username, password)

      case other =>
        throw new Exception(s"Unknown database type: $other")
    }
  }
}
