package typr
package internal
package codegen

import typr.jvm.Code

/** Generates a file for a DuckDB STRUCT type.
  *
  * Generates: - A record class with typed fields - A static `duckDbStruct` field using DuckDbStruct.builder() - A static `duckDbType` field that calls duckDbStruct.asType() - JSON codec instances
  */
object FileDuckDbStruct {
  val DuckDbStruct: jvm.Type.Qualified = FoundationsTypes.duckdb.DuckDbStruct
  val DuckDbType: jvm.Type.Qualified = FoundationsTypes.duckdb.DuckDbType
  val DuckDbTypes: jvm.Type.Qualified = FoundationsTypes.duckdb.DuckDbTypes

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

  /** Generate DuckDbStruct and DuckDbType static fields. */
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
      // Build the chain: DuckDbStruct.<StructType>builder("StructName")
      val builderStart = jvm.GenericMethodCall(
        target = DuckDbStruct,
        methodName = jvm.Ident("builder"),
        typeArgs = List(computed.tpe),
        args = List(jvm.Arg.Pos(jvm.StrLit(computed.name)))
      )

      // Add all .field(...) calls
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
          code"$acc.field($fieldName, $fieldType, $getter)"
        }

      // Add .build() call with reader lambda
      val readerLambda = lang match {
        case _: LangScala =>
          val assignments = computed.fields.zipWithIndex.map { case (f, i) =>
            code"${f.name} = arr($i).asInstanceOf[${f.tpe}]"
          }
          code"arr => ${computed.tpe}(${assignments.mkCode(", ")})"
        case _: LangKotlin =>
          val assignments = computed.fields.zipWithIndex.map { case (f, i) =>
            code"arr[$i] as ${f.tpe}"
          }
          code"{ arr -> ${computed.tpe}(${assignments.mkCode(", ")}) }"
        case _ =>
          val assignments = computed.fields.zipWithIndex.map { case (f, i) =>
            code"(${f.tpe}) arr[$i]"
          }
          code"arr -> new ${computed.tpe}(${assignments.mkCode(", ")})"
      }

      val buildCall = code"$fieldCalls.build($readerLambda)"

      // Create the duckDbStruct field
      val duckDbStructField = jvm.Given(
        tparams = Nil,
        name = jvm.Ident("duckDbStruct"),
        implicitParams = Nil,
        tpe = DuckDbStruct.of(computed.tpe),
        body = buildCall
      )

      // Create the duckDbType field that calls duckDbStruct.asType()
      val duckDbTypeField = jvm.Given(
        tparams = Nil,
        name = jvm.Ident("duckDbType"),
        implicitParams = Nil,
        tpe = DuckDbType.of(computed.tpe),
        body = code"duckDbStruct.asType()"
      )

      List(duckDbStructField, duckDbTypeField)
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
