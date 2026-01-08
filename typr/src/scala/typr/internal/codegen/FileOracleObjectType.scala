package typr
package internal
package codegen

object FileOracleObjectType {
  def apply(computed: ComputedOracleObjectType, options: InternalOptions): jvm.File = {
    val params = computed.attributes.map { attr =>
      jvm.Param(attr.name, attr.tpe)
    }

    val comments = scaladoc(List(s"Oracle Object Type: ${computed.underlying.name.value}"))

    // Generate JSON codec instances using productInstances
    val jsonInstances = NonEmptyList
      .fromList(computed.attributes)
      .map { nonEmptyAttrs =>
        options.jsonLibs.map { jsonLib =>
          jsonLib.productInstances(
            tpe = computed.tpe,
            fields = nonEmptyAttrs.map { attr =>
              JsonLib.Field(
                scalaName = attr.name,
                jsonName = jvm.StrLit(attr.dbAttribute.name),
                tpe = attr.tpe
              )
            }
          )
        }
      }
      .getOrElse(Nil)

    // Generate Oracle struct instances (OracleObject.builder() pattern)
    val structInstances = options.dbLib.toList.flatMap { dbLib =>
      dbLib.structInstances(computed)
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
}
