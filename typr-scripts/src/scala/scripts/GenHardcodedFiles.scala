package scripts

import bleep.*
import ryddig.TypedLogger
import typr.*
import typr.internal.FileSync.SoftWrite
import typr.internal.analysis.ParsedName
import typr.internal.codegen.{LangJava, LangScala}
import typr.internal.{DebugJson, Lazy, generate}

// this runs automatically at build time to instantly see results.
// it does not need a running database
object GenHardcodedFiles extends BleepCodegenScript("GenHardcodedFiles") {
  val sector = db.StringEnum(db.RelationName(Some("myschema"), "sector"), NonEmptyList("PUBLIC", "PRIVATE", "OTHER"))
  val number = db.StringEnum(db.RelationName(Some("myschema"), "number"), NonEmptyList("one", "two", "three"))

  val person = db.Table(
    name = db.RelationName(Some("myschema"), "person"),
    comment = Some("person table"),
    cols = NonEmptyList(
      db.Col(ParsedName.of("id"), db.PgType.Int8, Some("int8"), Nullability.NoNulls, columnDefault = Some("auto-increment"), None, None, Nil, DebugJson.Empty),
      db.Col(
        parsedName = ParsedName.of("favourite_football_club_id"),
        tpe = db.PgType.VarChar(Some(50)),
        udtName = Some("varchar"),
        nullability = Nullability.NoNulls,
        columnDefault = None,
        maybeGenerated = None,
        comment = None,
        constraints = Nil,
        jsonDescription = DebugJson.Empty
      ),
      db.Col(ParsedName.of("name"), db.PgType.VarChar(Some(100)), Some("varchar"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("nick_name"), db.PgType.VarChar(Some(30)), Some("varchar"), Nullability.Nullable, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("blog_url"), db.PgType.VarChar(Some(100)), Some("varchar"), Nullability.Nullable, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("email"), db.PgType.VarChar(Some(254)), Some("varchar"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("phone"), db.PgType.VarChar(Some(8)), Some("varchar"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("likes_pizza"), db.PgType.Boolean, Some("bool"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(
        parsedName = ParsedName.of("marital_status_id"),
        tpe = db.PgType.VarChar(Some(50)),
        udtName = Some("varchar"),
        nullability = Nullability.NoNulls,
        columnDefault = Some("some-value"),
        maybeGenerated = None,
        comment = None,
        constraints = Nil,
        jsonDescription = DebugJson.Empty
      ),
      db.Col(ParsedName.of("work_email"), db.PgType.VarChar(Some(254)), Some("varchar"), Nullability.Nullable, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(
        parsedName = ParsedName.of("sector"),
        tpe = db.PgType.EnumRef(sector),
        udtName = Some("myschema.sector"),
        nullability = Nullability.NoNulls,
        columnDefault = Some("PUBLIC"),
        maybeGenerated = Some(db.Generated.Identity("ALWAYS", None, None, None, None)),
        comment = None,
        constraints = Nil,
        jsonDescription = DebugJson.Empty
      ),
      db.Col(
        parsedName = ParsedName.of("favorite_number"),
        tpe = db.PgType.EnumRef(number),
        udtName = Some("myschema.number"),
        nullability = Nullability.NoNulls,
        columnDefault = Some("one"),
        maybeGenerated = None,
        comment = None,
        constraints = Nil,
        jsonDescription = DebugJson.Empty
      )
    ),
    Some(db.PrimaryKey(NonEmptyList(db.ColName("id")), db.RelationName(Some("myschema"), "person_pkey"))),
    Nil,
    List(
      db.ForeignKey(
        NonEmptyList(db.ColName("favourite_football_club_id")),
        db.RelationName(Some("myschema"), "football_club"),
        NonEmptyList(db.ColName("id")),
        db.RelationName(Some("myschema"), "person_favourite_football_club_id_fkey")
      ),
      db.ForeignKey(
        NonEmptyList(db.ColName("marital_status_id")),
        db.RelationName(Some("myschema"), "marital_status"),
        NonEmptyList(db.ColName("id")),
        db.RelationName(Some("myschema"), "person_marital_status_id_fkey")
      )
    )
  )
  val football_club = db.Table(
    name = db.RelationName(Some("myschema"), "football_club"),
    comment = Some("football club"),
    cols = NonEmptyList(
      db.Col(ParsedName.of("id"), db.PgType.Int8, Some("int8"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("name"), db.PgType.VarChar(Some(100)), Some("varchar"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty)
    ),
    Some(db.PrimaryKey(NonEmptyList(db.ColName("id")), db.RelationName(Some("myschema"), "football_club_pkey"))),
    Nil,
    Nil
  )
  val marital_status = db.Table(
    name = db.RelationName(Some("myschema"), "marital_status"),
    comment = None,
    cols = NonEmptyList(
      db.Col(ParsedName.of("id"), db.PgType.Int8, Some("int8"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty)
    ),
    Some(db.PrimaryKey(NonEmptyList(db.ColName("id")), db.RelationName(Some("myschema"), "marital_status_pkey"))),
    Nil,
    Nil
  )

  val cpk_person = db.Table(
    name = db.RelationName(Some("compositepk"), "person"), // name clash to ensure we handle it
    comment = None,
    cols = NonEmptyList(
      db.Col(ParsedName.of("one"), db.PgType.Int8, Some("int8"), Nullability.NoNulls, columnDefault = Some("auto-increment"), None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("two"), db.PgType.Text, Some("text"), Nullability.Nullable, columnDefault = Some("auto-increment"), None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("name"), db.PgType.Text, Some("text"), Nullability.Nullable, columnDefault = None, None, None, Nil, DebugJson.Empty)
    ),
    Some(db.PrimaryKey(NonEmptyList(db.ColName("one"), db.ColName("two")), db.RelationName(Some("compositepk"), "person_pkey"))),
    Nil,
    Nil
  )

  val all = List(person, football_club, marital_status, cpk_person)

  override def run(started: Started, commands: Commands, targets: List[Target], args: List[String]): Unit = {
    val header =
      """|/**
         | * File automatically generated by `typo` for its own test suite.
         | *
         | * IF YOU CHANGE THIS FILE YOUR CHANGES WILL BE OVERWRITTEN
         | */
         |""".stripMargin

    targets.foreach { target =>
      val dialect: Dialect =
        if (target.project.value.contains("jvm3")) Dialect.Scala3 else Dialect.Scala2XSource3

      val isTypoScala = target.project.value.contains("typr-scala")
      val (language, dbLib, jsonLib, dslEnabled) =
        if (target.project.value.contains("doobie"))
          (LangScala.javaDsl(dialect, TypeSupportScala), Some(DbLibName.Doobie), JsonLibName.Circe, true)
        else if (target.project.value.contains("zio-jdbc"))
          (LangScala.javaDsl(dialect, TypeSupportScala), Some(DbLibName.ZioJdbc), JsonLibName.ZioJson, true)
        else if (target.project.value.contains("typr-java"))
          (LangJava, Some(DbLibName.Typo), JsonLibName.Jackson, true)
        else if (isTypoScala)
          (LangScala.scalaDsl(Dialect.Scala3, TypeSupportJava), Some(DbLibName.Typo), JsonLibName.Jackson, true)
        else (LangScala.javaDsl(dialect, TypeSupportScala), Some(DbLibName.Anorm), JsonLibName.PlayJson, true)
      // Mock repos use Scala idioms not compatible with Java types
      val mockSelector = if (isTypoScala) Selector.None else Selector.All
      val domains = Nil

      val metaDb = MetaDb(dbType = DbType.PostgreSQL, relations = all.map(t => t.name -> Lazy(t)).toMap, enums = List(sector, number), domains = domains)

      val generated: List[Generated] =
        generate(
          Options(
            pkg = "testdb.hardcoded",
            lang = language,
            dbLib = dbLib,
            jsonLibs = List(jsonLib),
            generateMockRepos = mockSelector,
            silentBanner = true,
            fileHeader = header,
            naming = (pkg, lang) =>
              new Naming(pkg, lang) {
                override def enumValue(name: String): jvm.Ident = jvm.Ident("_" + name.toLowerCase)
              },
            enableFieldValue = if (language == LangJava) Selector.None else Selector.All,
            enableTestInserts = Selector.All,
            enableDsl = dslEnabled
          ),
          metaDb,
          ProjectGraph(name = "", target.sources, None, Selector.All, scripts = Nil, Nil),
          Map.empty
        )

      generated.foreach(
        _.overwriteFolder(
          // todo: bleep should use something better than timestamps
          softWrite = SoftWrite.No
        )
      )
      cli("add to git", target.sources, List("git", "add", "-f", target.sources.toString), TypedLogger.DevNull, cli.Out.Raw)
    }
  }
}
