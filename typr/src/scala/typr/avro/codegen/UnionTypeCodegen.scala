package typr.avro.codegen

import typr.avro._
import typr.jvm.Code.{CodeOps, TreeOps, TypeOps}
import typr.{jvm, Lang, Naming, Scope, TypesJava}
import typr.internal.codegen._

/** Generates sealed interfaces and wrapper types for complex Avro unions.
  *
  * For a union like `["string", "int", "boolean"]`, generates:
  *   - A sealed interface `StringOrIntOrBoolean`
  *   - Wrapper case classes `StringValue(value: String)`, `IntValue(value: Int)`, `BooleanValue(value: Boolean)`
  *   - Factory methods `of(String)`, `of(int)`, `of(boolean)`
  *   - Methods for checking and extracting values: `isString()`, `asString()`, etc.
  */
class UnionTypeCodegen(naming: Naming, lang: Lang) {

  /** Generate a sealed interface for a complex union type */
  def generate(union: AvroType.Union, unionTypeName: jvm.Type.Qualified): jvm.File = {
    val nonNullMembers = union.members.filterNot(_ == AvroType.Null)
    val hasNull = union.members.contains(AvroType.Null)

    // Generate wrapper types as inner sealed members
    val wrapperTypes = nonNullMembers.map { member =>
      generateWrapperType(member, nonNullMembers, unionTypeName)
    }

    // Generate factory methods
    val factoryMethods = nonNullMembers.map { member =>
      generateFactoryMethod(member, unionTypeName)
    }

    // Generate value extraction methods (abstract)
    val valueMethods = nonNullMembers.flatMap { member =>
      List(
        generateIsMethod(member),
        generateAsMethod(member)
      )
    }

    val sealedTrait = jvm.Adt.Sum(
      annotations = Nil,
      comments = jvm.Comments(List(s"Union type for: ${formatUnionMembers(nonNullMembers)}")),
      name = unionTypeName,
      tparams = Nil,
      members = valueMethods,
      implements = Nil,
      subtypes = wrapperTypes.map(_._1),
      staticMembers = factoryMethods,
      permittedSubtypes = Nil // Let Java derive permits clause from nested subtypes
    )

    jvm.File(unionTypeName, jvm.Code.Tree(sealedTrait), secondaryTypes = wrapperTypes.map(_._2), scope = Scope.Main)
  }

  /** Generate a wrapper type for a union member.
    *
    * Returns a tuple of (inner ADT definition, qualified type name)
    */
  private def generateWrapperType(
      member: AvroType,
      allMembers: List[AvroType],
      parentType: jvm.Type.Qualified
  ): (jvm.Adt.Record, jvm.Type.Qualified) = {
    val wrapperName = getWrapperName(member)
    val wrapperType = jvm.Type.Qualified(parentType.value / jvm.Ident(wrapperName))
    val valueType = mapMemberType(member)

    val valueParam = jvm.Param(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      name = jvm.Ident("value"),
      tpe = valueType,
      default = None
    )

    // Generate isXxx and asXxx implementations for ALL union members
    val methods = allMembers.flatMap { otherMember =>
      val isMatch = otherMember == member
      val otherValueType = mapMemberType(otherMember)
      generateIsMethodImpl(otherMember, isMatch = isMatch) ++
        generateAsMethodImpl(otherMember, otherValueType, isMatch = isMatch, valueExpr = jvm.Ident("value").code)
    }

    val record = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = true,
      privateConstructor = false,
      comments = jvm.Comments(List(s"Wrapper for ${formatTypeName(member)} value in union")),
      name = wrapperType,
      tparams = Nil,
      params = List(valueParam),
      implicitParams = Nil,
      `extends` = None,
      implements = List(parentType),
      members = methods,
      staticMembers = Nil
    )

    (record, wrapperType)
  }

  /** Generate a factory method for a union member */
  private def generateFactoryMethod(member: AvroType, parentType: jvm.Type.Qualified): jvm.Method = {
    val wrapperName = getWrapperName(member)
    val wrapperType = jvm.Type.Qualified(parentType.value / jvm.Ident(wrapperName))
    val valueType = mapMemberType(member)

    val valueParam = jvm.Param(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      name = jvm.Ident("value"),
      tpe = valueType,
      default = None
    )

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List(s"Create a union value from a ${formatTypeName(member)}")),
      tparams = Nil,
      name = jvm.Ident("of"),
      params = List(valueParam),
      implicitParams = Nil,
      tpe = parentType,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(jvm.New(wrapperType.code, List(jvm.Arg.Pos(valueParam.name.code))).code).code)),
      isOverride = false,
      isDefault = false
    )
  }

  /** Generate an abstract isXxx method */
  private def generateIsMethod(member: AvroType): jvm.Method = {
    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List(s"Check if this union contains a ${formatTypeName(member)} value")),
      tparams = Nil,
      name = jvm.Ident(s"is${getTypeNamePart(member)}"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.Boolean,
      throws = Nil,
      body = jvm.Body.Abstract,
      isOverride = false,
      isDefault = false
    )
  }

  /** Generate an abstract asXxx method */
  private def generateAsMethod(member: AvroType): jvm.Method = {
    val valueType = mapMemberType(member)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List(s"Get the ${formatTypeName(member)} value. Throws if this is not a ${formatTypeName(member)}.")),
      tparams = Nil,
      name = jvm.Ident(s"as${getTypeNamePart(member)}"),
      params = Nil,
      implicitParams = Nil,
      tpe = valueType,
      throws = Nil,
      body = jvm.Body.Abstract,
      isOverride = false,
      isDefault = false
    )
  }

  /** Generate isXxx method implementations for a wrapper type */
  private def generateIsMethodImpl(targetMember: AvroType, isMatch: Boolean): List[jvm.Method] = {
    val returnExpr = if (isMatch) code"true" else code"false"
    List(
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident(s"is${getTypeNamePart(targetMember)}"),
        params = Nil,
        implicitParams = Nil,
        tpe = lang.Boolean,
        throws = Nil,
        body = jvm.Body.Stmts(List(jvm.Return(returnExpr).code)),
        isOverride = true,
        isDefault = false
      )
    )
  }

  /** Generate asXxx method implementations for a wrapper type */
  private def generateAsMethodImpl(
      targetMember: AvroType,
      valueType: jvm.Type,
      isMatch: Boolean,
      valueExpr: jvm.Code
  ): List[jvm.Method] = {
    val typeName = getTypeNamePart(targetMember)
    val body = if (isMatch) {
      jvm.Body.Stmts(List(jvm.Return(valueExpr).code))
    } else {
      val errorMsg = jvm.StrLit(s"Not a $typeName value").code
      val throwExpr = jvm.Throw(jvm.Type.Qualified("java.lang.UnsupportedOperationException").construct(errorMsg))
      jvm.Body.Stmts(List(throwExpr.code))
    }

    List(
      jvm.Method(
        annotations = Nil,
        comments = jvm.Comments.Empty,
        tparams = Nil,
        name = jvm.Ident(s"as$typeName"),
        params = Nil,
        implicitParams = Nil,
        tpe = valueType,
        throws = Nil,
        body = body,
        isOverride = true,
        isDefault = false
      )
    )
  }

  /** Get the wrapper class name for a member type */
  private def getWrapperName(member: AvroType): String = {
    s"${getTypeNamePart(member)}Value"
  }

  /** Get the name part for type-related methods (isXxx, asXxx) */
  private def getTypeNamePart(member: AvroType): String = member match {
    case AvroType.Boolean                                                                             => "Boolean"
    case AvroType.Int                                                                                 => "Int"
    case AvroType.Long                                                                                => "Long"
    case AvroType.Float                                                                               => "Float"
    case AvroType.Double                                                                              => "Double"
    case AvroType.Bytes                                                                               => "Bytes"
    case AvroType.String                                                                              => "String"
    case AvroType.UUID                                                                                => "UUID"
    case AvroType.Date                                                                                => "Date"
    case AvroType.TimeMillis | AvroType.TimeMicros | AvroType.TimeNanos                               => "Time"
    case AvroType.TimestampMillis | AvroType.TimestampMicros | AvroType.TimestampNanos                => "Timestamp"
    case AvroType.LocalTimestampMillis | AvroType.LocalTimestampMicros | AvroType.LocalTimestampNanos => "LocalTimestamp"
    case _: AvroType.DecimalBytes                                                                     => "Decimal"
    case _: AvroType.DecimalFixed                                                                     => "Decimal"
    case AvroType.Duration                                                                            => "Duration"
    case AvroType.Array(_)                                                                            => "Array"
    case AvroType.Map(_)                                                                              => "Map"
    case AvroType.Named(fullName)                                                                     => fullName.split('.').last
    case AvroType.Record(r)                                                                           => r.name
    case AvroType.EnumType(e)                                                                         => e.name
    case AvroType.Fixed(f)                                                                            => f.name
    case AvroType.Null                                                                                => "Null"
    case AvroType.Union(_)                                                                            => "Union"
  }

  /** Map a union member to its JVM type */
  private def mapMemberType(member: AvroType): jvm.Type = member match {
    case AvroType.Null                                                                                => lang.voidType
    case AvroType.Boolean                                                                             => lang.Boolean
    case AvroType.Int                                                                                 => lang.Int
    case AvroType.Long                                                                                => lang.Long
    case AvroType.Float                                                                               => lang.Float
    case AvroType.Double                                                                              => lang.Double
    case AvroType.Bytes                                                                               => lang.ByteArray
    case AvroType.String                                                                              => lang.String
    case AvroType.UUID                                                                                => TypesJava.UUID
    case AvroType.Date                                                                                => TypesJava.LocalDate
    case AvroType.TimeMillis | AvroType.TimeMicros | AvroType.TimeNanos                               => TypesJava.LocalTime
    case AvroType.TimestampMillis | AvroType.TimestampMicros | AvroType.TimestampNanos                => TypesJava.Instant
    case AvroType.LocalTimestampMillis | AvroType.LocalTimestampMicros | AvroType.LocalTimestampNanos => TypesJava.LocalDateTime
    case _: AvroType.DecimalBytes                                                                     => TypesJava.BigDecimal
    case _: AvroType.DecimalFixed                                                                     => TypesJava.BigDecimal
    case AvroType.Duration                                                                            => lang.ByteArray
    case AvroType.Array(items)                                                                        => lang.ListType.tpe.of(mapMemberType(items))
    case AvroType.Map(values)                                                                         => lang.MapOps.tpe.of(lang.String, mapMemberType(values))
    case AvroType.Named(fullName)                                                                     => jvm.Type.Qualified(jvm.QIdent(fullName))
    case AvroType.Record(r)                                                                           => jvm.Type.Qualified(jvm.QIdent(r.fullName))
    case AvroType.EnumType(e)                                                                         => jvm.Type.Qualified(jvm.QIdent(e.fullName))
    case AvroType.Fixed(_)                                                                            => lang.ByteArray
    case AvroType.Union(_)                                                                            => lang.topType // Nested unions - rare, fall back to Object
  }

  /** Format a type name for documentation */
  private def formatTypeName(member: AvroType): String = member match {
    case AvroType.Named(fullName) => fullName
    case AvroType.Record(r)       => r.fullName
    case AvroType.EnumType(e)     => e.fullName
    case AvroType.Fixed(f)        => f.fullName
    case other                    => getTypeNamePart(other).toLowerCase
  }

  /** Format union members for documentation */
  private def formatUnionMembers(members: List[AvroType]): String =
    members.map(formatTypeName).mkString(" | ")

  /** Generate a name for a union type based on its members */
  def generateUnionTypeName(union: AvroType.Union, namespace: Option[String]): jvm.Type.Qualified = {
    val nonNullMembers = union.members.filterNot(_ == AvroType.Null)
    val name = nonNullMembers.map(getTypeNamePart).mkString("Or")
    val pkg = namespace.map(ns => jvm.QIdent(ns)).getOrElse(naming.avroRecordPackage)
    jvm.Type.Qualified(pkg / jvm.Ident(name))
  }
}
