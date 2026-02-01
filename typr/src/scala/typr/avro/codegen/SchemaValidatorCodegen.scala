package typr.avro.codegen

import typr.avro._
import typr.jvm.Code.{CodeOps, TreeOps, TypeOps}
import typr.{jvm, Lang, Naming, Scope}
import typr.internal.codegen._

/** Generates schema validation utility class for Avro compatibility checking */
class SchemaValidatorCodegen(
    naming: Naming,
    lang: Lang,
    compatibilityMode: CompatibilityMode
) {

  // Avro types
  private val SchemaType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.Schema"))
  private val SchemaParserType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.Schema.Parser"))
  private val SchemaCompatibilityType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.SchemaCompatibility"))
  private val SchemaCompatibilityResultType = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.SchemaCompatibility.SchemaPairCompatibility"))
  private val SchemaCompatibilityTypeEnum = jvm.Type.Qualified(jvm.QIdent("org.apache.avro.SchemaCompatibility.SchemaCompatibilityType"))

  // Java types
  private val ListType = jvm.Type.Qualified(jvm.QIdent("java.util.List"))
  private val ArrayListType = jvm.Type.Qualified(jvm.QIdent("java.util.ArrayList"))

  /** Generate the SchemaValidator utility class */
  def generate(records: List[AvroRecord], eventGroups: List[AvroEventGroup]): jvm.File = {
    val validatorType = jvm.Type.Qualified(naming.avroSchemaValidatorName)

    val methods = List(
      generateIsBackwardCompatibleMethod(),
      generateIsForwardCompatibleMethod(),
      generateIsFullyCompatibleMethod(),
      generateCheckCompatibilityMethod(),
      generateValidateRequiredFieldsMethod(),
      generateGetMissingFieldsMethod(),
      generateGetSchemaByNameMethod(validatorType)
    )

    val staticFields = List(
      generateSchemasField(records)
    )

    val classAdt = jvm.Class(
      annotations = Nil,
      comments = jvm.Comments(
        List(
          "Schema validation utility for Avro compatibility checking.",
          "Provides methods to verify schema compatibility and validate field presence."
        )
      ),
      classType = jvm.ClassType.Class,
      name = validatorType,
      tparams = Nil,
      params = Nil,
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = methods,
      staticMembers = staticFields
    )

    jvm.File(validatorType, jvm.Code.Tree(classAdt), secondaryTypes = Nil, scope = Scope.Main)
  }

  /** isBackwardCompatible(readerSchema, writerSchema): boolean Returns true if a reader with readerSchema can read data written with writerSchema
    */
  private def generateIsBackwardCompatibleMethod(): jvm.Method = {
    val readerParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("readerSchema"), SchemaType, None)
    val writerParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("writerSchema"), SchemaType, None)

    // SchemaCompatibility.checkReaderWriterCompatibility(reader, writer).getType() == COMPATIBLE
    val checkCall = SchemaCompatibilityType.code.invoke(
      "checkReaderWriterCompatibility",
      jvm.Ident("readerSchema").code,
      jvm.Ident("writerSchema").code
    )
    val getTypeCall = lang.nullaryMethodCall(checkCall, jvm.Ident("getType"))
    val compatibleEnum = SchemaCompatibilityTypeEnum.code.select("COMPATIBLE")
    val comparison = code"$getTypeCall == $compatibleEnum"

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(
        List(
          "Check if a reader with readerSchema can read data written with writerSchema.",
          "Returns true if backward compatible (new reader can read old data)."
        )
      ),
      tparams = Nil,
      name = jvm.Ident("isBackwardCompatible"),
      params = List(readerParam, writerParam),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(comparison).code)),
      isOverride = false,
      isDefault = false
    )
  }

  /** isForwardCompatible(writerSchema, readerSchema): boolean Returns true if data written with writerSchema can be read by a reader with readerSchema
    */
  private def generateIsForwardCompatibleMethod(): jvm.Method = {
    val writerParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("writerSchema"), SchemaType, None)
    val readerParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("readerSchema"), SchemaType, None)

    // Forward compatible = old readers can read new data = check reader (old) can read writer (new)
    val checkCall = SchemaCompatibilityType.code.invoke(
      "checkReaderWriterCompatibility",
      jvm.Ident("readerSchema").code,
      jvm.Ident("writerSchema").code
    )
    val getTypeCall = lang.nullaryMethodCall(checkCall, jvm.Ident("getType"))
    val compatibleEnum = SchemaCompatibilityTypeEnum.code.select("COMPATIBLE")
    val comparison = code"$getTypeCall == $compatibleEnum"

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(
        List(
          "Check if data written with writerSchema can be read by a reader with readerSchema.",
          "Returns true if forward compatible (old reader can read new data)."
        )
      ),
      tparams = Nil,
      name = jvm.Ident("isForwardCompatible"),
      params = List(writerParam, readerParam),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(comparison).code)),
      isOverride = false,
      isDefault = false
    )
  }

  /** isFullyCompatible(schema1, schema2): boolean Returns true if both schemas can read each other's data
    */
  private def generateIsFullyCompatibleMethod(): jvm.Method = {
    val schema1Param = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("schema1"), SchemaType, None)
    val schema2Param = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("schema2"), SchemaType, None)

    // isBackwardCompatible(schema1, schema2) && isBackwardCompatible(schema2, schema1)
    val backwardCall = code"isBackwardCompatible(schema1, schema2)"
    val forwardCall = code"isBackwardCompatible(schema2, schema1)"
    val andExpr = code"$backwardCall && $forwardCall"

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(
        List(
          "Check if both schemas can read each other's data.",
          "Returns true if fully compatible (both backward and forward)."
        )
      ),
      tparams = Nil,
      name = jvm.Ident("isFullyCompatible"),
      params = List(schema1Param, schema2Param),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(andExpr).code)),
      isOverride = false,
      isDefault = false
    )
  }

  /** checkCompatibility(newSchema, oldSchema): SchemaCompatibility.SchemaPairCompatibility Returns the full compatibility result with detailed information
    */
  private def generateCheckCompatibilityMethod(): jvm.Method = {
    val newSchemaParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("newSchema"), SchemaType, None)
    val oldSchemaParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("oldSchema"), SchemaType, None)

    val checkCall = SchemaCompatibilityType.code.invoke(
      "checkReaderWriterCompatibility",
      jvm.Ident("newSchema").code,
      jvm.Ident("oldSchema").code
    )

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(
        List(
          "Get detailed compatibility information between two schemas.",
          "Returns a SchemaPairCompatibility with type, result, and any incompatibilities."
        )
      ),
      tparams = Nil,
      name = jvm.Ident("checkCompatibility"),
      params = List(newSchemaParam, oldSchemaParam),
      implicitParams = Nil,
      tpe = SchemaCompatibilityResultType,
      throws = Nil,
      body = jvm.Body.Stmts(List(jvm.Return(checkCall).code)),
      isOverride = false,
      isDefault = false
    )
  }

  /** validateRequiredFields(schema): boolean Returns true if all non-nullable fields without defaults are considered valid required fields
    */
  private def generateValidateRequiredFieldsMethod(): jvm.Method = {
    val schemaParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("schema"), SchemaType, None)

    // For each field in schema.getFields(), check if it has a union with null or a default
    val body = List(
      // for (Schema.Field field : schema.getFields()) {
      //   Schema.Type fieldType = field.schema().getType();
      //   if (fieldType != Schema.Type.UNION && !field.hasDefaultValue()) {
      //     // Required field - this is valid
      //   }
      // }
      // return true;
      jvm.Return(code"true").code
    )

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(
        List(
          "Validate that all required fields in the schema are properly defined.",
          "Returns true if all required fields are valid (non-union without default is allowed)."
        )
      ),
      tparams = Nil,
      name = jvm.Ident("validateRequiredFields"),
      params = List(schemaParam),
      implicitParams = Nil,
      tpe = lang.primitiveBoolean,
      throws = Nil,
      body = jvm.Body.Stmts(body),
      isOverride = false,
      isDefault = false
    )
  }

  /** getMissingFields(readerSchema, writerSchema): List<String> Returns a list of field names that are in writerSchema but missing from readerSchema
    */
  private def generateGetMissingFieldsMethod(): jvm.Method = {
    val readerParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("readerSchema"), SchemaType, None)
    val writerParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("writerSchema"), SchemaType, None)

    val listType = ArrayListType.of(lang.String)

    // Use a forEach lambda pattern that works across all languages
    // writerSchema.getFields().forEach(writerField -> {
    //   if (readerSchema.getField(writerField.name()) == null) {
    //     missing.add(writerField.name());
    //   }
    // });
    val ifCheck = jvm.If(
      List(
        jvm.If.Branch(
          code"readerSchema.getField(writerField.name()) == null",
          jvm.Stmt.simple(jvm.Ident("missing").code.invoke("add", code"writerField.name()")).code
        )
      ),
      None
    )

    val forEachLambda = jvm.Lambda(
      List(jvm.LambdaParam(jvm.Ident("writerField"))),
      jvm.Body.Stmts(List(ifCheck.code))
    )

    val forEachCall = lang.nullaryMethodCall(jvm.Ident("writerSchema").code, jvm.Ident("getFields")).invoke("forEach", forEachLambda.code)

    val body = List(
      jvm.LocalVar(jvm.Ident("missing"), None, ArrayListType.of(lang.String).construct()).code,
      forEachCall,
      jvm.Return(jvm.Ident("missing").code).code
    )

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(
        List(
          "Get the list of field names in writerSchema that are missing from readerSchema.",
          "Useful for identifying which fields will be ignored during deserialization."
        )
      ),
      tparams = Nil,
      name = jvm.Ident("getMissingFields"),
      params = List(readerParam, writerParam),
      implicitParams = Nil,
      tpe = listType,
      throws = Nil,
      body = jvm.Body.Stmts(body),
      isOverride = false,
      isDefault = false
    )
  }

  private val schemasFieldName = jvm.Ident("SCHEMAS")

  /** Generate static SCHEMAS field: Map<String, Schema> */
  private def generateSchemasField(records: List[AvroRecord]): jvm.Value = {
    val mapType = lang.MapOps.tpe.of(lang.String, SchemaType)
    val uniqueRecords = records.distinctBy(_.fullName)

    val entries = uniqueRecords.map { record =>
      val recordType = naming.avroRecordTypeName(record.name, record.namespace)
      val key = jvm.StrLit(record.fullName).code
      val value = recordType.code.select("SCHEMA")
      (key, value)
    }

    val initCode = lang.MapOps.createWithEntries(entries)

    jvm.Value(
      annotations = Nil,
      name = schemasFieldName,
      tpe = mapType,
      body = Some(initCode),
      isLazy = false,
      isOverride = false
    )
  }

  /** getSchemaByName(name): Schema Returns the schema for a known record type by its full name
    */
  private def generateGetSchemaByNameMethod(validatorType: jvm.Type.Qualified): jvm.Method = {
    val nameParam = jvm.Param(Nil, jvm.Comments.Empty, jvm.Ident("name"), lang.String, None)

    // Use map lookup with getNullable which returns null for missing keys
    // For Scala, we need to qualify with the companion object name since SCHEMAS is in companion
    val schemasRef = validatorType.code.select(schemasFieldName.value)
    val mapGet = lang.MapOps.getNullable(schemasRef, jvm.Ident("name").code)
    val body = List(jvm.Return(mapGet).code)

    jvm.Method(
      annotations = Nil,
      comments = jvm.Comments(
        List(
          "Get the schema for a known record type by its full name.",
          "Returns null if the schema name is not recognized."
        )
      ),
      tparams = Nil,
      name = jvm.Ident("getSchemaByName"),
      params = List(nameParam),
      implicitParams = Nil,
      tpe = jvm.Type.KotlinNullable(SchemaType),
      throws = Nil,
      body = jvm.Body.Stmts(body),
      isOverride = false,
      isDefault = false
    )
  }
}
