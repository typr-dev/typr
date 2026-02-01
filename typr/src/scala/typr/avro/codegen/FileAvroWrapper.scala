package typr.avro.codegen

import typr.avro.ComputedAvroWrapper
import typr.internal.codegen.{CodeInterpolator, toCode}
import typr.jvm
import typr.jvm.Code.{CodeOps, TreeOps}
import typr.openapi.codegen.JsonLibSupport
import typr.{Lang, Scope}

/** File generator for Avro wrapper types.
  *
  * Follows the FileDomain pattern: generates a complete jvm.File for a wrapper type with:
  *   - A value field with optional @JsonValue annotation
  *   - An unwrap() method to get the underlying value
  *   - A valueOf() static method to create from a raw value
  *   - JSON serialization support via JsonLibSupport
  *   - Avro serialization support via AvroLib (currently minimal)
  *
  * TODO: JsonLibSupport and typr.internal.codegen.JsonLib should be unified into a single abstraction
  */
object FileAvroWrapper {

  def apply(
      wrapper: ComputedAvroWrapper,
      avroLib: AvroLib,
      jsonLibSupport: JsonLibSupport,
      lang: Lang
  ): jvm.File = {
    val value = jvm.Ident("value")
    val v = jvm.Ident("v")

    val avroInstances = avroLib.wrapperTypeInstances(
      wrapper.tpe,
      wrapper.underlyingJvmType,
      wrapper.underlyingAvroType
    )

    val valueAnnotations = jsonLibSupport.valueAnnotations
    val wrapperStaticMembers = jsonLibSupport.wrapperTypeStaticMembers(wrapper.tpe, wrapper.underlyingJvmType)

    val thisRef = code"this"
    val valueOfMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List(s"Create a ${wrapper.tpe.value.name.value} from a raw value")),
      tparams = Nil,
      name = jvm.Ident("valueOf"),
      params = List(jvm.Param(Nil, jvm.Comments.Empty, v, wrapper.underlyingJvmType, None)),
      implicitParams = Nil,
      tpe = wrapper.tpe,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(jvm.New(wrapper.tpe.code, List(jvm.Arg.Pos(v.code))).code).code)),
      isOverride = false,
      isDefault = false
    )

    val unwrapMethod = jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(List("Get the underlying value")),
      tparams = Nil,
      name = jvm.Ident("unwrap"),
      params = Nil,
      implicitParams = Nil,
      tpe = wrapper.underlyingJvmType,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(lang.prop(thisRef, value)).code)),
      isOverride = false,
      isDefault = false
    )

    val doc = wrapper.doc.getOrElse(s"Wrapper type for ${wrapper.underlyingJvmType.render}")
    val staticMembers: List[jvm.ClassMember] =
      List(valueOfMethod) ++ wrapperStaticMembers ++ avroInstances.methods ++ avroInstances.fields ++ avroInstances.givens

    val record = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = true,
      privateConstructor = false,
      comments = jvm.Comments(List(doc)),
      name = wrapper.tpe,
      tparams = Nil,
      params = List(jvm.Param(valueAnnotations, jvm.Comments.Empty, value, wrapper.underlyingJvmType, None)),
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = List(unwrapMethod),
      staticMembers = staticMembers
    )

    jvm.File(wrapper.tpe, jvm.Code.Tree(record), secondaryTypes = Nil, scope = Scope.Main)
  }
}
