package typr
package internal
package codegen

import typr.jvm.Code

/** Generates a file for a PostgreSQL composite type.
  *
  * Generates:
  *   - A record class with typed fields
  *   - A static `pgType` field built via `PgTypes.compositeOf(name, RowCodec.namedBuilder()...build(...))`
  *   - A static `pgTypeArray` field built via `pgType.array`
  *   - JSON codec instances
  */
object FilePgCompositeType {
  def apply(
      computed: ComputedPgCompositeType,
      options: InternalOptions,
      adapter: DbAdapter,
      compositeLookup: Map[db.RelationName, ComputedPgCompositeType],
      naming: Naming
  ): jvm.File = {
    val lang = options.lang
    val params = computed.fields.map { field =>
      val tpe = if (field.nullable) lang.Optional.tpe(field.tpe) else field.tpe
      jvm.Param(field.name, tpe)
    }

    val comments = scaladoc(List(s"PostgreSQL composite type: ${computed.underlying.compositeType.name.value}"))

    // Generate JSON codec instances using productInstances
    val jsonInstances = NonEmptyList
      .fromList(computed.fields)
      .map { nonEmptyFields =>
        options.jsonLibs.map { jsonLib =>
          jsonLib.productInstances(
            tpe = computed.tpe,
            fields = nonEmptyFields.map { field =>
              val tpe = if (field.nullable) lang.Optional.tpe(field.tpe) else field.tpe
              JsonLib.Field(
                scalaName = field.name,
                jsonName = jvm.StrLit(field.name.value),
                tpe = tpe
              )
            }
          )
        }
      }
      .getOrElse(Nil)

    // Generate PgType instances (only for DbLibFoundations which has the foundations runtime)
    val structInstances = options.dbLib.toList.flatMap {
      case _: DbLibFoundations => pgTypeInstances(computed, adapter, options.lang, compositeLookup, naming)
      case _                   => Nil
    }

    val instances = List(
      jsonInstances.flatMap(_.givens),
      structInstances
    ).flatten

    val fieldAnnotations = JsonLib.mergeFieldAnnotations(jsonInstances.flatMap(_.fieldAnnotations.toList))
    val typeAnnotations = jsonInstances.flatMap(_.typeAnnotations)

    val paramsWithAnnotations = params.map { p =>
      fieldAnnotations.get(p.name) match {
        case Some(anns) => p.copy(annotations = p.annotations ++ anns)
        case None       => p
      }
    }

    val record = jvm.Adt.Record(
      annotations = typeAnnotations,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = comments,
      name = computed.tpe,
      tparams = Nil,
      params = paramsWithAnnotations,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = Nil,
      staticMembers = instances
    )

    jvm.File(computed.tpe, record, secondaryTypes = Nil, scope = Scope.Main)
  }

  /** Generate PgType static fields using `PgTypes.compositeOf(name, RowCodec.namedBuilder()...build(decoder))`. */
  private def pgTypeInstances(
      computed: ComputedPgCompositeType,
      adapter: DbAdapter,
      lang: Lang,
      compositeLookup: Map[db.RelationName, ComputedPgCompositeType],
      naming: Naming
  ): List[jvm.ClassMember] = {
    if (computed.fields.isEmpty) {
      Nil
    } else {
      val PgTypes = adapter.typesFor(lang.typeSupport)
      val PgType = adapter.typeClassFor(lang.typeSupport)
      val RowCodec = lang.dsl.RowCodec
      val typeFieldName = adapter.typeFieldName
      val typeArrayFieldName = adapter.typeArrayFieldName

      // RowCodec.namedBuilder[Row]()
      val builderStart = jvm.GenericMethodCall(
        target = RowCodec,
        methodName = jvm.Ident("namedBuilder"),
        typeArgs = List(computed.tpe),
        args = Nil
      )

      // Each field becomes `.field(name, fieldType[.opt()], getter)`.
      // For nullable fields the type is `fieldType.opt` (Scala wrapper) / `fieldType.opt()` (Java/Kotlin),
      // and the getter must produce the optional shape used by that language.
      val usesScalaOption = lang.typeSupport eq TypeSupportScala
      val fieldCalls = computed.fields
        .map { field =>
          val fieldTypeBase = lookupFieldType(field.dbType, adapter, lang, compositeLookup, naming)
          val fieldTypeCode = (lang, field.nullable) match {
            case (_: LangScala, true) if usesScalaOption => code"$fieldTypeBase.opt"
            case (_, true)                               => code"$fieldTypeBase.opt()"
            case _                                       => fieldTypeBase
          }
          val getterCode = (lang, field.nullable) match {
            case (_: LangScala, true) if usesScalaOption =>
              // Scala wrapper PgType.opt yields PgType[Option[F]] — pass Option directly
              code"(v: ${computed.tpe}) => v.${field.name}"
            case (_: LangScala, true) =>
              // Scala-with-Java-types: convert Option to Java Optional
              val fieldAccess = code"v.${field.name}"
              code"(v: ${computed.tpe}) => ${lang.Optional.toJavaOptional(fieldAccess)}"
            case (_: LangScala, false) =>
              code"(v: ${computed.tpe}) => v.${field.name}"
            case (_: LangKotlin, true) =>
              code"{ v: ${computed.tpe} -> v.${field.name} }"
            case (_: LangKotlin, false) =>
              code"{ v: ${computed.tpe} -> v.${field.name} }"
            case (_, true) =>
              code"v -> v.${field.name}()"
            case (_, false) =>
              code"${computed.tpe}::${field.name}"
          }
          (jvm.StrLit(field.name.value), fieldTypeCode, getterCode)
        }
        .foldLeft[Code](builderStart.code) { case (acc, (fieldName, fieldType, getter)) =>
          // Scala-wrapper RowCodec has curried `.field(name, tpe)(getter)`; the Java `RowCodecNamedBuilders` is positional. We dispatch on the DSL because Scala-source-with-Java-DSL still hits the Java builder.
          if (lang.dsl == DslQualifiedNames.Scala) code"$acc.field($fieldName, $fieldType)($getter)"
          else code"$acc.field($fieldName, $fieldType, $getter)"
        }

      // .build(decoder). The builder lambda's parameter types now match the row class's field types
      // exactly (Option/Optional for nullable fields), because we pass `type.opt`/`type.opt()` to `.field`.
      // No Optional-to-Option dance needed at the constructor.
      val readerLambda: Code = lang match {
        case _: LangScala =>
          val params = computed.fields.zipWithIndex.map { case (_, i) => jvm.Ident(s"t$i") }
          val args = computed.fields.zip(params).map { case (f, p) => code"${f.name} = $p" }
          code"(${params.map(_.code).mkCode(", ")}) => ${computed.tpe}(${args.mkCode(", ")})"
        case _: LangKotlin =>
          // Kotlin opt() yields PgType[F?], so the builder gives F? directly — no Optional unwrap.
          val params = computed.fields.zipWithIndex.map { case (_, i) => jvm.Ident(s"t$i") }
          code"{ ${params.map(_.code).mkCode(", ")} -> ${computed.tpe}(${params.map(_.code).mkCode(", ")}) }"
        case _ =>
          // Java: record constructor takes Optional<F> for nullable fields, matching builder types exactly
          jvm.ConstructorMethodRef(computed.tpe).code
      }

      val codecBuild = code"$fieldCalls.build($readerLambda)"
      val sqlTypeName = jvm.StrLit(computed.underlying.compositeType.name.value)
      val pgTypeBody = code"$PgTypes.compositeOf($sqlTypeName, $codecBuild)"

      val typeField = jvm.Given(
        tparams = Nil,
        name = typeFieldName,
        implicitParams = Nil,
        tpe = PgType.of(computed.tpe),
        body = pgTypeBody
      )

      // pgTypeArray: `pgType.array` (Scala-wrapper PgType) / `pgType.array()` (Java/Kotlin PgType).
      // Returns PgType[List[Row]] — Scala-wrapper version yields immutable.List, Java/Kotlin versions yield java.util.List. Whether we use the wrapper or Java directly depends on the DSL, not on the source language.
      val arrayElementContainer: jvm.Type = (lang, lang.dsl) match {
        case (_: LangScala, DslQualifiedNames.Scala) => jvm.Type.Qualified("scala.collection.immutable.List").of(computed.tpe)
        case (_: LangKotlin, _)                      => jvm.Type.Qualified("kotlin.collections.List").of(computed.tpe)
        case _                                       => jvm.Type.Qualified("java.util.List").of(computed.tpe)
      }
      val arrayBody = lang.dsl match {
        case DslQualifiedNames.Scala => code"$typeFieldName.array"
        case _                       => code"$typeFieldName.array()"
      }
      val typeArrayField = jvm.Given(
        tparams = Nil,
        name = typeArrayFieldName,
        implicitParams = Nil,
        tpe = PgType.of(arrayElementContainer),
        body = arrayBody
      )

      List(typeField, typeArrayField)
    }
  }

  /** Lookup the PgType for a field's db.Type.
    *
    * Handles nested composite types by looking them up in the composite lookup map.
    */
  private def lookupFieldType(
      dbType: db.Type,
      adapter: DbAdapter,
      lang: Lang,
      compositeLookup: Map[db.RelationName, ComputedPgCompositeType],
      naming: Naming
  ): Code = {
    dbType match {
      case c: db.PgType.CompositeType =>
        compositeLookup.get(c.name) match {
          case Some(computed) =>
            code"${computed.tpe}.${adapter.typeFieldName}"
          case None =>
            sys.error(s"Nested composite type not found in lookup: ${c.name.value}")
        }
      case db.PgType.Array(c: db.PgType.CompositeType) =>
        compositeLookup.get(c.name) match {
          case Some(computed) =>
            code"${computed.tpe}.${adapter.typeArrayFieldName}"
          case None =>
            sys.error(s"Nested composite type not found in lookup: ${c.name.value}")
        }
      case _ =>
        adapter.lookupType(TypoType.Standard(lang.String, dbType), naming, lang.typeSupport)
    }
  }
}
