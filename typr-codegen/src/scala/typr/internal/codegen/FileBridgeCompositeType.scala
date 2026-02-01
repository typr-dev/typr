package typr
package internal
package codegen

/** Generates a file for a Bridge composite type.
  *
  * Generates:
  *   - A record class with typed fields
  *   - JSON codec instances
  *   - Projection mapper methods (fromXxx/toXxx)
  */
object FileBridgeCompositeType {

  def apply(
      computed: ComputedBridgeCompositeType,
      options: InternalOptions,
      relationLookup: db.RelationName => Option[ComputedTable]
  ): jvm.File = {
    if (!computed.generateCanonical) {
      return emptyFile(computed)
    }

    val params = computed.fields.map { field =>
      jvm.Param(field.name, field.tpe)
    }

    val comments = scaladoc(
      List(s"Bridge composite type: ${computed.name}") ++
        computed.description.toList
    )

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

    val instances = jsonInstances.flatMap(_.givens)
    val mapperMethods = FileBridgeProjectionMapper.generateMapperMethods(computed, options, relationLookup)

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
      staticMembers = instances ++ mapperMethods
    )

    jvm.File(computed.tpe, record, secondaryTypes = Nil, scope = Scope.Main)
  }

  private def emptyFile(computed: ComputedBridgeCompositeType): jvm.File = {
    val record = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = false,
      privateConstructor = false,
      comments = scaladoc(List(s"Bridge composite type: ${computed.name} (canonical generation disabled)")),
      name = computed.tpe,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = Nil,
      staticMembers = Nil
    )
    jvm.File(computed.tpe, record, secondaryTypes = Nil, scope = Scope.Main)
  }
}
