package typr
package internal
package codegen

import typr.jvm.Code

/** Generates a file for a PostgreSQL composite type.
  *
  * Generates:
  *   - A record class with typed fields
  *   - A static `pgStruct` field using PgStruct.builder()
  *   - A static `pgType` field that calls pgStruct.asType()
  *   - JSON codec instances
  */
object FilePgCompositeType {
  val PgStruct: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.PgStruct")
  val PgType: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.PgType")
  val PgTypes: jvm.Type.Qualified = jvm.Type.Qualified("dev.typr.foundations.PgTypes")

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

    // Generate PgStruct and PgType instances (only for DbLibTypo which has PgStruct support)
    val structInstances = options.dbLib.toList.flatMap {
      case _: DbLibTypo => pgStructInstances(computed, adapter, options.lang, compositeLookup, naming)
      case _            => Nil
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

  /** Generate PgStruct and PgType static fields. */
  private def pgStructInstances(
      computed: ComputedPgCompositeType,
      adapter: DbAdapter,
      lang: Lang,
      compositeLookup: Map[db.RelationName, ComputedPgCompositeType],
      naming: Naming
  ): List[jvm.ClassMember] = {
    if (computed.fields.isEmpty) {
      Nil
    } else {
      // Build the chain: PgStruct.<CompositeType>builder("typename")
      val builderStart = jvm.GenericMethodCall(
        target = PgStruct,
        methodName = jvm.Ident("builder"),
        typeArgs = List(computed.tpe),
        args = List(jvm.Arg.Pos(jvm.StrLit(computed.underlying.compositeType.name.value)))
      )

      // Add all .field(...) calls
      val fieldCalls = computed.fields
        .map { field =>
          val fieldTypeCode = lookupFieldType(field.dbType, adapter, lang, compositeLookup, naming)
          val getterCode = lang match {
            case _: LangScala  => code"(v: ${computed.tpe}) => v.${field.name}"
            case _: LangKotlin => code"{ v: ${computed.tpe} -> v.${field.name} }"
            case _             => code"${computed.tpe}::${field.name}"
          }
          (jvm.StrLit(field.name.value), fieldTypeCode, getterCode, field.nullable)
        }
        .foldLeft[Code](builderStart.code) { case (acc, (fieldName, fieldType, getter, nullable)) =>
          val methodName = if (nullable) "nullableField" else "field"
          code"$acc.$methodName($fieldName, $fieldType, $getter)"
        }

      // Add .build() call with reader lambda
      val readerLambda = lang match {
        case _: LangScala =>
          val assignments = computed.fields.zipWithIndex.map { case (f, i) =>
            val cast = if (f.nullable) {
              code"Option(arr($i)).map(_.asInstanceOf[${f.tpe}])"
            } else {
              code"arr($i).asInstanceOf[${f.tpe}]"
            }
            code"${f.name} = $cast"
          }
          code"arr => ${computed.tpe}(${assignments.mkCode(", ")})"
        case _: LangKotlin =>
          val assignments = computed.fields.zipWithIndex.map { case (f, i) =>
            if (f.nullable) {
              code"arr[$i] as? ${f.tpe}"
            } else {
              code"arr[$i] as ${f.tpe}"
            }
          }
          code"{ arr -> ${computed.tpe}(${assignments.mkCode(", ")}) }"
        case _ =>
          val assignments = computed.fields.zipWithIndex.map { case (f, i) =>
            code"(${f.tpe}) arr[$i]"
          }
          code"arr -> new ${computed.tpe}(${assignments.mkCode(", ")})"
      }

      val buildCall = code"$fieldCalls.build($readerLambda)"

      // Create the pgStruct field
      val pgStructField = jvm.Given(
        tparams = Nil,
        name = jvm.Ident("pgStruct"),
        implicitParams = Nil,
        tpe = PgStruct.of(computed.tpe),
        body = buildCall
      )

      // Create the pgType field that calls pgStruct.asType()
      val pgTypeField = jvm.Given(
        tparams = Nil,
        name = jvm.Ident("pgType"),
        implicitParams = Nil,
        tpe = PgType.of(computed.tpe),
        body = code"pgStruct.asType()"
      )

      List(pgStructField, pgTypeField)
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
        // Nested composite - look up its type and reference its pgType field
        compositeLookup.get(c.name) match {
          case Some(computed) =>
            code"${computed.tpe}.pgType"
          case None =>
            sys.error(s"Nested composite type not found in lookup: ${c.name.value}")
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
