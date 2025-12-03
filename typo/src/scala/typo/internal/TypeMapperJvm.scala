package typo
package internal

abstract class TypeMapperJvm(lang: Lang, typeOverride: TypeOverride, nullabilityOverride: NullabilityOverride) {
  def baseType(tpe: db.Type): jvm.Type

  def col(relation: db.RelationName, col: db.Col, typeFromFK: Option[jvm.Type]): jvm.Type = {
    def go(tpe: db.Type): jvm.Type = {
      val maybeOverridden = typeOverride(relation, col.name).map(overriddenString => jvm.Type.UserDefined(jvm.Type.Qualified(overriddenString)))
      val maybeFromFK = typeFromFK.map(stripOptionAndArray)
      val base = baseType(tpe)
      maybeOverridden.orElse(maybeFromFK).getOrElse(base)
    }

    val baseTpe = col.tpe match {
      case db.Type.Array(tpe) =>
        jvm.Type.ArrayOf(go(tpe))
      case other =>
        go(other)
    }

    withNullability(baseTpe, nullabilityOverride.apply(relation, col.name).getOrElse(col.nullability))
  }

  def sqlFile(maybeOverridden: Option[jvm.Type], dbType: db.Type, nullability: Nullability): jvm.Type = {
    def go(tpe: db.Type): jvm.Type =
      maybeOverridden.map(stripOptionAndArray).getOrElse(baseType(tpe))

    val base = dbType match {
      case db.Type.Array(tpe) =>
        jvm.Type.ArrayOf(go(tpe))
      case other =>
        go(other)
    }

    withNullability(base, nullability)
  }

  // domains have nullability information, but afaiu it's used for checking,
  // and the optionality should live in the field definitions
  def domain(dbType: db.Type): jvm.Type =
    dbType match {
      case db.Type.Array(tpe) =>
        jvm.Type.ArrayOf(baseType(tpe))
      case other =>
        baseType(other)
    }

  def withNullability(tpe: jvm.Type, nullability: Nullability): jvm.Type =
    nullability match {
      case Nullability.NoNulls         => tpe
      case Nullability.Nullable        => lang.Optional.tpe.of(tpe)
      case Nullability.NullableUnknown => lang.Optional.tpe.of(tpe).withComment("nullability unknown")
    }

  def stripOptionAndArray(tpe: jvm.Type): jvm.Type =
    tpe match {
      case jvm.Type.ArrayOf(tpe)                         => stripOptionAndArray(tpe)
      case jvm.Type.Commented(underlying, comment)       => jvm.Type.Commented(stripOptionAndArray(underlying), comment)
      case jvm.Type.Annotated(underlying, ann)           => jvm.Type.Annotated(stripOptionAndArray(underlying), ann)
      case jvm.Type.TApply(lang.Optional.tpe, List(tpe)) => stripOptionAndArray(tpe)
      case jvm.Type.TApply(other, targs)                 => jvm.Type.TApply(stripOptionAndArray(other), targs.map(stripOptionAndArray))
      case jvm.Type.UserDefined(underlying)              => jvm.Type.UserDefined(stripOptionAndArray(underlying))
      case tpe @ (
            jvm.Type.Abstract(_, _) | jvm.Type.Wildcard | jvm.Type.Qualified(_) | jvm.Type.Function0(_) | jvm.Type.Function1(_, _) | jvm.Type.Function2(_, _, _) | jvm.Type.Void | jvm.Type.Primitive(_)
          ) =>
        tpe
    }
}
