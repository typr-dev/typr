package typr.grpc.codegen

import typr.grpc._
import typr.{jvm, Lang, Naming, Scope, NonEmptyList}
import typr.jvm.Code.{CodeOps, TreeOps, TypeOps}
import typr.internal.codegen._

/** Generates jvm.File for Protobuf enum types.
  *
  * Proto enums are mapped to JVM enums with toValue()/fromValue() for wire format encoding. The toValue() method returns the protobuf numeric value and fromValue(int) does the reverse lookup.
  */
class EnumCodegen(
    naming: Naming,
    lang: Lang
) {

  /** Generate an enum file from a ProtoEnum */
  def generate(protoEnum: ProtoEnum): jvm.File = {
    val tpe = naming.grpcEnumTypeName(protoEnum.fullName)

    // Filter out aliases (same numeric value) - keep first occurrence
    val uniqueValues = protoEnum.values.foldLeft(List.empty[ProtoEnumValue]) { (acc, v) =>
      if (acc.exists(_.number == v.number)) acc
      else acc :+ v
    }

    val values = NonEmptyList
      .fromList(uniqueValues.map { value =>
        (naming.grpcEnumValueName(value.name), jvm.StrLit(value.name).code)
      })
      .getOrElse(sys.error(s"Enum ${protoEnum.name} has no values"))

    // toValue() instance method - returns the protobuf numeric value
    // Use if-else with return in each branch (valid in both Java and Scala)
    val toValueCases: List[(jvm.Code, jvm.Code)] = uniqueValues.map { value =>
      val cond = code"this".callNullary("toString").invoke("equals", jvm.StrLit(value.name).code)
      val body = jvm.Return(jvm.Code.Str(value.number.toString)).code
      (cond, body)
    }
    val toValueDefault = jvm.Return(jvm.Code.Str("0")).code

    val toValueBody = if (toValueCases.nonEmpty) {
      jvm.IfElseChain(toValueCases, toValueDefault).code
    } else {
      toValueDefault
    }

    val toValueMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("toValue"),
      params = Nil,
      implicitParams = Nil,
      tpe = lang.Int,
      throws = Nil,
      body = jvm.Body.Stmts(List(toValueBody)),
      isOverride = false,
      isDefault = false
    )

    // fromValue(int) static method - reverse lookup from protobuf number to enum constant
    val fromValueParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("value"), lang.Int, None)

    val fromValueCases: List[(jvm.Code, jvm.Code)] = uniqueValues.map { value =>
      val cond = code"${fromValueParam.name.code} == ${jvm.Code.Str(value.number.toString)}"
      val body = jvm.Return(tpe.code.select(naming.grpcEnumValueName(value.name).value)).code
      (cond, body)
    }

    val throwExpr = jvm
      .Throw(
        jvm.Type
          .Qualified("java.lang.IllegalArgumentException")
          .construct(code""""Unknown enum value: " + ${fromValueParam.name.code}""")
      )
      .code

    val fromValueBody = if (fromValueCases.nonEmpty) {
      jvm.IfElseChain(fromValueCases, throwExpr).code
    } else {
      throwExpr
    }

    val fromValueMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident("fromValue"),
      params = List(fromValueParam),
      implicitParams = Nil,
      tpe = tpe,
      throws = Nil,
      body = jvm.Body.Stmts(List(fromValueBody)),
      isOverride = false,
      isDefault = false
    )

    val enumTree = jvm.Enum(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      tpe = tpe,
      values = values,
      members = List(toValueMethod),
      staticMembers = List(fromValueMethod)
    )

    val generatedCode = jvm.Code.Tree(enumTree)
    jvm.File(tpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)
  }
}
