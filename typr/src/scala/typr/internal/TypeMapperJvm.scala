package typr
package internal

abstract class TypeMapperJvm(lang: Lang, typeOverride: TypeOverride, nullabilityOverride: NullabilityOverride) {
  def baseType(tpe: db.Type): jvm.Type

  /** Whether this type mapper needs SQL casts for timestamp/date types. TypeMapperJvmOld needs casts because it uses custom wrappers with toString/parse. TypeMapperJvmNew doesn't need casts because
    * it uses native JDBC types.
    */
  def needsTimestampCasts: Boolean

  def col(relation: db.RelationName, col: db.Col, typeFromFK: Option[jvm.Type]): jvm.Type = {
    def go(tpe: db.Type): jvm.Type = {
      val maybeOverridden = typeOverride(relation, col.name).map(overriddenString => jvm.Type.UserDefined(jvm.Type.Qualified(overriddenString)))
      val maybeFromFK = typeFromFK.map(stripOptionAndArray)
      val base = baseType(tpe)
      maybeOverridden.orElse(maybeFromFK).getOrElse(base)
    }

    val baseTpe = col.tpe match {
      case db.PgType.Array(tpe) =>
        jvm.Type.ArrayOf(go(tpe))
      case db.DuckDbType.ListType(tpe) =>
        jvm.Type.ArrayOf(go(tpe))
      case db.DuckDbType.ArrayType(tpe, _) =>
        jvm.Type.ArrayOf(go(tpe))
      case other =>
        go(other)
    }

    withNullability(baseTpe, nullabilityOverride.apply(relation, col.name).getOrElse(col.nullability))
  }

  def sqlFile(maybeOverridden: Option[jvm.Type], dbType: db.Type, nullability: Nullability): jvm.Type = {
    def go(tpe: db.Type, stripArrays: Boolean): jvm.Type =
      maybeOverridden.map(if (stripArrays) stripOptionAndArray else stripOption).getOrElse(baseType(tpe))

    val base = dbType match {
      case db.PgType.Array(tpe) =>
        jvm.Type.ArrayOf(go(tpe, stripArrays = true))
      case db.DuckDbType.ListType(tpe) =>
        jvm.Type.ArrayOf(go(tpe, stripArrays = true))
      case db.DuckDbType.ArrayType(tpe, _) =>
        jvm.Type.ArrayOf(go(tpe, stripArrays = true))
      case other =>
        go(other, stripArrays = false)
    }

    withNullability(base, nullability)
  }

  def stripOption(tpe: jvm.Type): jvm.Type =
    tpe match {
      case jvm.Type.Commented(underlying, comment) => jvm.Type.Commented(stripOption(underlying), comment)
      case jvm.Type.Annotated(underlying, ann)     => jvm.Type.Annotated(stripOption(underlying), ann)
      case lang.Optional(underlying)               => stripOption(underlying) // Use unapply
      case jvm.Type.TApply(other, targs)           => jvm.Type.TApply(stripOption(other), targs.map(stripOption))
      case jvm.Type.UserDefined(underlying)        => jvm.Type.UserDefined(stripOption(underlying))
      case tpe                                     => tpe
    }

  // domains have nullability information, but afaiu it's used for checking,
  // and the optionality should live in the field definitions
  def domain(dbType: db.Type): jvm.Type =
    dbType match {
      case db.PgType.Array(tpe) =>
        jvm.Type.ArrayOf(baseType(tpe))
      case db.DuckDbType.ListType(tpe) =>
        jvm.Type.ArrayOf(baseType(tpe))
      case db.DuckDbType.ArrayType(tpe, _) =>
        jvm.Type.ArrayOf(baseType(tpe))
      case other =>
        baseType(other)
    }

  def withNullability(tpe: jvm.Type, nullability: Nullability): jvm.Type =
    nullability match {
      case Nullability.NoNulls         => tpe
      case Nullability.Nullable        => lang.Optional.tpe(tpe)
      case Nullability.NullableUnknown => lang.Optional.tpe(tpe).withComment("nullability unknown")
    }

  def stripOptionAndArray(tpe: jvm.Type): jvm.Type =
    tpe match {
      case jvm.Type.ArrayOf(tpe)                   => stripOptionAndArray(tpe)
      case jvm.Type.KotlinNullable(underlying)     => stripOptionAndArray(underlying)
      case jvm.Type.Commented(underlying, comment) => jvm.Type.Commented(stripOptionAndArray(underlying), comment)
      case jvm.Type.Annotated(underlying, ann)     => jvm.Type.Annotated(stripOptionAndArray(underlying), ann)
      case lang.Optional(underlying)               => stripOptionAndArray(underlying) // Use unapply
      case jvm.Type.TApply(other, targs)           => jvm.Type.TApply(stripOptionAndArray(other), targs.map(stripOptionAndArray))
      case jvm.Type.UserDefined(underlying)        => jvm.Type.UserDefined(stripOptionAndArray(underlying))
      case tpe @ (
            jvm.Type.Abstract(_, _) | jvm.Type.Wildcard | jvm.Type.Qualified(_) | jvm.Type.Function0(_) | jvm.Type.Function1(_, _) | jvm.Type.Function2(_, _, _) | jvm.Type.Void | jvm.Type.Primitive(_)
          ) =>
        tpe
    }
}
