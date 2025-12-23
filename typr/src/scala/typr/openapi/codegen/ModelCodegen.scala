package typr.openapi.codegen

import typr.{jvm, Lang, Scope}
import typr.internal.codegen._
import typr.openapi.{ModelClass, SumType}

/** Generates jvm.File for model classes */
class ModelCodegen(
    modelPkg: jvm.QIdent,
    typeMapper: TypeMapper,
    lang: Lang,
    jsonLib: JsonLibSupport,
    validationSupport: ValidationSupport,
    serverFramework: FrameworkSupport
) {

  def generate(model: ModelClass): jvm.File = model match {
    case obj: ModelClass.ObjectType      => generateObjectType(obj)
    case enumType: ModelClass.EnumType   => generateEnumType(enumType)
    case wrapper: ModelClass.WrapperType => generateWrapperType(wrapper)
    case alias: ModelClass.AliasType     => generateAliasType(alias)
  }

  private def generateObjectType(obj: ModelClass.ObjectType): jvm.File = {
    val tpe = jvm.Type.Qualified(modelPkg / jvm.Ident(obj.name))
    val comments = obj.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    val params = obj.properties.map { prop =>
      val propType = typeMapper.map(prop.typeInfo)
      val jsonAnnotations = jsonLib.propertyAnnotations(prop.originalName)
      val validationAnnotations = validationSupport.propertyAnnotations(prop)
      jvm.Param(
        annotations = jsonAnnotations ++ validationAnnotations,
        comments = prop.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
        name = jvm.Ident(prop.name),
        tpe = propType,
        default = None
      )
    }

    // Add interface implementations for sum type parents
    val implements = obj.sumTypeParents.map { parentName =>
      jvm.Type.Qualified(modelPkg / jvm.Ident(parentName))
    }

    // Add discriminator property implementations if needed
    // isLazy=true makes LangJava render this as a method override instead of a field
    val discriminatorMembers = obj.discriminatorValues.toList.map { case (propName, value) =>
      jvm.Value(
        annotations = jsonLib.propertyAnnotations(propName),
        name = jvm.Ident(sanitizePropertyName(propName)),
        tpe = Types.String,
        body = Some(jvm.StrLit(value).code),
        isLazy = true,
        isOverride = true
      )
    }

    // Get static members for companion object (e.g., Circe codecs)
    val staticMembers = jsonLib.objectTypeStaticMembers(tpe)

    val record = jvm.Adt.Record(
      annotations = jsonLib.modelAnnotations,
      constructorAnnotations = Nil,
      isWrapper = false,
      comments = comments,
      name = tpe,
      tparams = Nil,
      params = params,
      implicitParams = Nil,
      `extends` = None,
      implements = implements,
      members = discriminatorMembers,
      staticMembers = staticMembers
    )

    val generatedCode = lang.renderTree(record, lang.Ctx.Empty)
    jvm.File(tpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)
  }

  private def generateEnumType(enumType: ModelClass.EnumType): jvm.File = {
    val tpe = jvm.Type.Qualified(modelPkg / jvm.Ident(enumType.name))
    val comments = enumType.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    val values = typr.NonEmptyList
      .fromList(enumType.values.map { value =>
        (jvm.Ident(sanitizeEnumValue(value)), jvm.StrLit(value).code)
      })
      .getOrElse(sys.error(s"Enum ${enumType.name} has no values"))

    // Get static members for companion object (e.g., Circe codecs)
    val staticMembers = jsonLib.enumTypeStaticMembers(tpe)

    val enumTree = jvm.Enum(
      annotations = Nil,
      comments = comments,
      tpe = tpe,
      values = values,
      staticMembers = staticMembers
    )

    val generatedCode = lang.renderTree(enumTree, lang.Ctx.Empty)
    jvm.File(tpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)
  }

  private def generateWrapperType(wrapper: ModelClass.WrapperType): jvm.File = {
    val tpe = jvm.Type.Qualified(modelPkg / jvm.Ident(wrapper.name))
    val comments = wrapper.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)
    val underlyingType = typeMapper.map(wrapper.underlying)

    val valueParam = jvm.Param(
      annotations = jsonLib.valueAnnotations,
      comments = jvm.Comments.Empty,
      name = jvm.Ident("value"),
      tpe = underlyingType,
      default = None
    )

    // Get static members for companion object (e.g., Circe codecs, Http4s path extractors)
    val staticMembers = jsonLib.wrapperTypeStaticMembers(tpe, underlyingType) ++
      serverFramework.wrapperTypeStaticMembers(tpe, underlyingType)

    val record = jvm.Adt.Record(
      annotations = jsonLib.wrapperAnnotations(tpe),
      constructorAnnotations = jsonLib.constructorAnnotations,
      isWrapper = true,
      comments = comments,
      name = tpe,
      tparams = Nil,
      params = List(valueParam),
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = Nil,
      staticMembers = staticMembers
    )

    val generatedCode = lang.renderTree(record, lang.Ctx.Empty)
    jvm.File(tpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)
  }

  private def generateAliasType(alias: ModelClass.AliasType): jvm.File = {
    val tpe = jvm.Type.Qualified(modelPkg / jvm.Ident(alias.name))
    val targetType = typeMapper.map(alias.underlying)
    val comments = alias.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    // Type aliases are handled differently - for Java we generate a wrapper, for Scala a type alias
    // For now, generate as a wrapper type with a single value field
    val valueParam = jvm.Param(
      annotations = Nil,
      comments = jvm.Comments.Empty,
      name = jvm.Ident("value"),
      tpe = targetType,
      default = None
    )

    val record = jvm.Adt.Record(
      annotations = Nil,
      constructorAnnotations = Nil,
      isWrapper = true,
      comments = comments,
      name = tpe,
      tparams = Nil,
      params = List(valueParam),
      implicitParams = Nil,
      `extends` = None,
      implements = Nil,
      members = Nil,
      staticMembers = Nil
    )

    val generatedCode = lang.renderTree(record, lang.Ctx.Empty)
    jvm.File(tpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)
  }

  def generateSumType(sumType: SumType): jvm.File = {
    val tpe = jvm.Type.Qualified(modelPkg / jvm.Ident(sumType.name))
    val comments = sumType.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty)

    // Generate common property abstract methods
    val commonMethods = sumType.commonProperties.map { prop =>
      val propType = typeMapper.map(prop.typeInfo)
      jvm.Method(
        annotations = jsonLib.methodPropertyAnnotations(prop.originalName),
        comments = prop.description.map(d => jvm.Comments(List(d))).getOrElse(jvm.Comments.Empty),
        tparams = Nil,
        name = jvm.Ident(prop.name),
        params = Nil,
        implicitParams = Nil,
        tpe = propType,
        throws = Nil,
        body = jvm.Body.Abstract,
        isOverride = false,
        isDefault = false
      )
    }

    // Add discriminator property method
    val discriminatorMethod = jvm.Method(
      annotations = jsonLib.methodPropertyAnnotations(sumType.discriminator.propertyName),
      comments = jvm.Comments.Empty,
      tparams = Nil,
      name = jvm.Ident(sanitizePropertyName(sumType.discriminator.propertyName)),
      params = Nil,
      implicitParams = Nil,
      tpe = Types.String,
      throws = Nil,
      body = jvm.Body.Abstract,
      isOverride = false,
      isDefault = false
    )

    // Get static members for companion object (e.g., Circe codecs)
    val staticMembers = jsonLib.sumTypeStaticMembers(tpe, sumType)

    // Generate permitted subtypes for Java sealed interfaces
    val permittedSubtypes = sumType.subtypeNames.map { subtypeName =>
      jvm.Type.Qualified(modelPkg / jvm.Ident(subtypeName))
    }

    val sumAdt = jvm.Adt.Sum(
      annotations = jsonLib.sumTypeAnnotations(sumType),
      comments = comments,
      name = tpe,
      tparams = Nil,
      members = discriminatorMethod :: commonMethods,
      implements = Nil,
      subtypes = Nil, // Subtypes are separate model classes
      staticMembers = staticMembers,
      permittedSubtypes = permittedSubtypes
    )

    val generatedCode = lang.renderTree(sumAdt, lang.Ctx.Empty)
    jvm.File(tpe, generatedCode, secondaryTypes = Nil, scope = Scope.Main)
  }

  private def sanitizePropertyName(name: String): String = {
    val sanitized = name.replace("-", "_").replace(".", "_")
    if (sanitized.headOption.exists(_.isDigit)) s"_$sanitized" else sanitized
  }

  private def sanitizeEnumValue(value: String): String = {
    val sanitized = value
      .replace("-", "_")
      .replace(".", "_")
      .replace(" ", "_")
      .filter(c => c.isLetterOrDigit || c == '_')
    if (sanitized.isEmpty) "_"
    else if (sanitized.headOption.exists(_.isDigit)) s"_$sanitized"
    else sanitized
  }
}
