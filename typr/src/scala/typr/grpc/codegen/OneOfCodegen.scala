package typr.grpc.codegen

import typr.grpc._
import typr.{jvm, Lang, Naming, Scope}
import typr.jvm.Code.{CodeOps, TreeOps}

/** Generates jvm.File for Protobuf oneof types.
  *
  * Each oneof becomes a sealed interface/trait. Each field in the oneof becomes a case (record implementing the sealed type).
  */
class OneOfCodegen(
    naming: Naming,
    typeMapper: ProtobufTypeMapper,
    lang: Lang
) {

  /** Generate a sealed type for a oneof group.
    *
    * @param messageFullName
    *   Full name of the containing message
    * @param oneof
    *   The oneof definition
    * @return
    *   Generated file for the sealed oneof type
    */
  def generate(messageFullName: String, oneof: ProtoOneof): jvm.File = {
    val oneofType = naming.grpcOneofTypeName(messageFullName, oneof.name)

    // Generate a case record for each oneof field
    val subtypes = oneof.fields.map { field =>
      val caseName = naming.grpcOneofCaseName(field)
      val caseType = oneofType / caseName
      val fieldType = typeMapper.mapFieldType(field)

      jvm.Adt.Record(
        annotations = Nil,
        constructorAnnotations = Nil,
        isWrapper = false,
        privateConstructor = false,
        comments = jvm.Comments.Empty,
        name = caseType,
        tparams = Nil,
        params = List(jvm.Param(Nil, jvm.Comments.Empty, naming.grpcFieldName(field.name), fieldType, None)),
        implicitParams = Nil,
        `extends` = None,
        implements = List(oneofType),
        members = Nil,
        staticMembers = Nil
      )
    }

    val sealedInterface = jvm.Adt.Sum(
      annotations = Nil,
      comments = jvm.Comments(List(s"OneOf type for ${oneof.name}")),
      name = oneofType,
      tparams = Nil,
      members = Nil,
      implements = Nil,
      subtypes = subtypes,
      staticMembers = Nil
    )

    jvm.File(oneofType, jvm.Code.Tree(sealedInterface), secondaryTypes = Nil, scope = Scope.Main)
  }
}
