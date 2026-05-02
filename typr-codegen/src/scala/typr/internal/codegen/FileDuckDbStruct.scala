package typr
package internal
package codegen

import typr.jvm.Code

/** Generates a file for a DuckDB STRUCT type.
  *
  * Generates: - A record class with typed fields - A static `duckDbType` built via `DuckDbTypes.compositeOf(name, RowCodec.namedBuilder()...build(...))` - JSON codec instances
  */
object FileDuckDbStruct {

  def apply(
      computed: ComputedDuckDbStruct,
      options: InternalOptions,
      adapter: DbAdapter,
      structLookup: Map[db.DuckDbType.StructType, String],
      naming: Naming
  ): jvm.File = {
    val params = computed.fields.map { field =>
      jvm.Param(field.name, field.tpe)
    }

    val comments = scaladoc(List(s"DuckDB STRUCT type: ${computed.name}"))

    // Generate JSON codec instances using productInstances
    val jsonInstances = NonEmptyList
      .fromList(computed.fields)
      .map { nonEmptyFields =>
        options.jsonLibs.map { jsonLib =>
          jsonLib.productInstances(
            tpe = computed.tpe,
            fields = nonEmptyFields.map { field =>
              JsonLib.Field(
                scalaName = field.name,
                jsonName = jvm.StrLit(field.name.value),
                tpe = field.tpe
              )
            }
          )
        }
      }
      .getOrElse(Nil)

    // Generate DuckDbStruct and DuckDbType instances
    val structInstances = options.dbLib.toList.flatMap { _ =>
      duckDbStructInstances(computed, adapter, options.lang, structLookup, naming)
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

  /** Generate DuckDbType static field via `DuckDbTypes.compositeOf(name, RowCodec.namedBuilder()...build(decoder))`. */
  private def duckDbStructInstances(
      computed: ComputedDuckDbStruct,
      adapter: DbAdapter,
      lang: Lang,
      structLookup: Map[db.DuckDbType.StructType, String],
      naming: Naming
  ): List[jvm.ClassMember] = {
    if (computed.fields.isEmpty) {
      Nil
    } else {
      val DuckDbTypes = adapter.typesFor(lang.typeSupport)
      val DuckDbType = adapter.typeClassFor(lang.typeSupport)
      val RowCodec = lang.dsl.RowCodec

      // RowCodec.namedBuilder[Row]()
      val builderStart = jvm.GenericMethodCall(
        target = RowCodec,
        methodName = jvm.Ident("namedBuilder"),
        typeArgs = List(computed.tpe),
        args = Nil
      )

      // Each field: .field(name, fieldType, getter)
      val fieldCalls = computed.fields
        .map { field =>
          val fieldTypeCode = lookupFieldType(field.dbType, adapter, lang, structLookup, naming)
          val getterCode = lang match {
            case _: LangScala  => code"(v: ${computed.tpe}) => v.${field.name}"
            case _: LangKotlin => code"{ v: ${computed.tpe} -> v.${field.name} }"
            case _             => code"${computed.tpe}::${field.name}"
          }
          (jvm.StrLit(field.name.value), fieldTypeCode, getterCode)
        }
        .foldLeft[Code](builderStart.code) { case (acc, (fieldName, fieldType, getter)) =>
          // Dispatch on DSL: Scala-wrapper builder is curried; Java's `RowCodecNamedBuilders` is positional.
          if (lang.dsl == DslQualifiedNames.Scala) code"$acc.field($fieldName, $fieldType)($getter)"
          else code"$acc.field($fieldName, $fieldType, $getter)"
        }

      // .build(decoder) — typed (T0, T1, ...) -> Row
      val readerLambda: Code = lang match {
        case _: LangScala =>
          val params = computed.fields.zipWithIndex.map { case (_, i) => jvm.Ident(s"t$i") }
          val args = computed.fields.zip(params).map { case (f, p) => code"${f.name} = $p" }
          code"(${params.map(_.code).mkCode(", ")}) => ${computed.tpe}(${args.mkCode(", ")})"
        case _: LangKotlin =>
          val params = computed.fields.zipWithIndex.map { case (_, i) => jvm.Ident(s"t$i") }
          val args = params.map(_.code)
          code"{ ${params.map(_.code).mkCode(", ")} -> ${computed.tpe}(${args.mkCode(", ")}) }"
        case _ =>
          jvm.ConstructorMethodRef(computed.tpe).code
      }

      val codecBuild = code"$fieldCalls.build($readerLambda)"
      val structName = jvm.StrLit(computed.name)
      val duckDbTypeBody = code"$DuckDbTypes.compositeOf($structName, $codecBuild)"

      val duckDbTypeField = jvm.Given(
        tparams = Nil,
        name = jvm.Ident("duckDbType"),
        implicitParams = Nil,
        tpe = DuckDbType.of(computed.tpe),
        body = duckDbTypeBody
      )

      List(duckDbTypeField)
    }
  }

  /** Lookup the DuckDbType for a field's db.Type.
    *
    * Handles nested structs by looking them up in the struct lookup map.
    */
  private def lookupFieldType(
      dbType: db.Type,
      adapter: DbAdapter,
      lang: Lang,
      structLookup: Map[db.DuckDbType.StructType, String],
      naming: Naming
  ): Code = {
    dbType match {
      case s: db.DuckDbType.StructType =>
        // Nested struct - look up its name and reference its duckDbType field
        structLookup.get(s) match {
          case Some(name) =>
            val structType = jvm.Type.Qualified(naming.structTypeName(name))
            code"$structType.${DuckDbAdapter.typeFieldName}"
          case None =>
            sys.error(s"Nested STRUCT type not found in lookup: $s")
        }
      case _ =>
        val typeSupport = lang match {
          case _: LangScala  => TypeSupportScala
          case _: LangKotlin => TypeSupportKotlin
          case _             => TypeSupportJava
        }
        // Use the adapter's lookupType for primitive types
        adapter.lookupType(TypoType.Standard(lang.String, dbType), naming, typeSupport)
    }
  }
}
