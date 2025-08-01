package scripts

import bleep.*
import ryddig.TypedLogger
import typo.*
import typo.internal.FileSync.SoftWrite
import typo.internal.analysis.ParsedName
import typo.internal.{DebugJson, Lazy, generate}

// this runs automatically at build time to instantly see results.
// it does not need a running database
object GenHardcodedFiles extends BleepCodegenScript("GenHardcodedFiles") {
  val sector = db.StringEnum(db.RelationName(Some("myschema"), "sector"), NonEmptyList("PUBLIC", "PRIVATE", "OTHER"))
  val number = db.StringEnum(db.RelationName(Some("myschema"), "number"), NonEmptyList("one", "two", "three"))

  val person = db.Table(
    name = db.RelationName(Some("myschema"), "person"),
    comment = Some("person table"),
    cols = NonEmptyList(
      db.Col(ParsedName.of("id"), db.Type.Int8, Some("int8"), Nullability.NoNulls, columnDefault = Some("auto-increment"), None, None, Nil, DebugJson.Empty),
      db.Col(
        parsedName = ParsedName.of("favourite_football_club_id"),
        tpe = db.Type.VarChar(Some(50)),
        udtName = Some("varchar"),
        nullability = Nullability.NoNulls,
        columnDefault = None,
        maybeGenerated = None,
        comment = None,
        constraints = Nil,
        jsonDescription = DebugJson.Empty
      ),
      db.Col(ParsedName.of("name"), db.Type.VarChar(Some(100)), Some("varchar"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("nick_name"), db.Type.VarChar(Some(30)), Some("varchar"), Nullability.Nullable, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("blog_url"), db.Type.VarChar(Some(100)), Some("varchar"), Nullability.Nullable, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("email"), db.Type.VarChar(Some(254)), Some("varchar"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("phone"), db.Type.VarChar(Some(8)), Some("varchar"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("likes_pizza"), db.Type.Boolean, Some("bool"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(
        parsedName = ParsedName.of("marital_status_id"),
        tpe = db.Type.VarChar(Some(50)),
        udtName = Some("varchar"),
        nullability = Nullability.NoNulls,
        columnDefault = Some("some-value"),
        maybeGenerated = None,
        comment = None,
        constraints = Nil,
        jsonDescription = DebugJson.Empty
      ),
      db.Col(ParsedName.of("work_email"), db.Type.VarChar(Some(254)), Some("varchar"), Nullability.Nullable, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(
        parsedName = ParsedName.of("sector"),
        tpe = db.Type.EnumRef(sector),
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
        tpe = db.Type.EnumRef(number),
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
      db.Col(ParsedName.of("id"), db.Type.Int8, Some("int8"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("name"), db.Type.VarChar(Some(100)), Some("varchar"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty)
    ),
    Some(db.PrimaryKey(NonEmptyList(db.ColName("id")), db.RelationName(Some("myschema"), "football_club_pkey"))),
    Nil,
    Nil
  )
  val marital_status = db.Table(
    name = db.RelationName(Some("myschema"), "marital_status"),
    comment = None,
    cols = NonEmptyList(
      db.Col(ParsedName.of("id"), db.Type.Int8, Some("int8"), Nullability.NoNulls, columnDefault = None, None, None, Nil, DebugJson.Empty)
    ),
    Some(db.PrimaryKey(NonEmptyList(db.ColName("id")), db.RelationName(Some("myschema"), "marital_status_pkey"))),
    Nil,
    Nil
  )

  val cpk_person = db.Table(
    name = db.RelationName(Some("compositepk"), "person"), // name clash to ensure we handle it
    comment = None,
    cols = NonEmptyList(
      db.Col(ParsedName.of("one"), db.Type.Int8, Some("int8"), Nullability.NoNulls, columnDefault = Some("auto-increment"), None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("two"), db.Type.Text, Some("text"), Nullability.Nullable, columnDefault = Some("auto-increment"), None, None, Nil, DebugJson.Empty),
      db.Col(ParsedName.of("name"), db.Type.Text, Some("text"), Nullability.Nullable, columnDefault = None, None, None, Nil, DebugJson.Empty)
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
      val (dbLib, jsonLib) =
        if (target.project.value.contains("doobie"))
          (DbLibName.Doobie, JsonLibName.Circe)
        else if (target.project.value.contains("zio-jdbc"))
          (DbLibName.ZioJdbc, JsonLibName.ZioJson)
        else (DbLibName.Anorm, JsonLibName.PlayJson)
      val domains = Nil

      val implicitOrUsing: ImplicitOrUsing =
        if (target.project.value.contains("jvm3")) ImplicitOrUsing.Using else ImplicitOrUsing.Implicit

      val metaDb = MetaDb(relations = all.map(t => t.name -> Lazy(t)).toMap, enums = List(sector, number), domains = domains)

      val generated: List[Generated] =
        generate(
          Options(
            pkg = "testdb.hardcoded",
            Some(dbLib),
            List(jsonLib),
            naming = pkg =>
              new Naming(pkg) {
                override def enumValue(name: String): sc.Ident = sc.Ident("_" + name.toLowerCase)
              },
            fileHeader = header,
            enableDsl = true,
            enableTestInserts = Selector.All,
            enableFieldValue = Selector.All,
            silentBanner = true,
            implicitOrUsing = implicitOrUsing
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
