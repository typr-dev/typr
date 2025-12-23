package typr.openapi.parser

import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import typr.openapi._

import java.nio.file.Path
import scala.jdk.CollectionConverters._

/** Main parser for OpenAPI specifications */
object OpenApiParser {

  /** Configuration for spec validation */
  case class ParseConfig(
      validateSpec: Boolean
  )

  object ParseConfig {
    val default: ParseConfig = ParseConfig(validateSpec = true)
  }

  /** Parse an OpenAPI spec from a file path */
  def parseFile(path: Path, config: ParseConfig): Either[List[String], ParsedSpec] = {
    parse(path.toString, config)
  }

  /** Parse an OpenAPI spec from a file path with default config */
  def parseFile(path: Path): Either[List[String], ParsedSpec] = {
    parseFile(path, ParseConfig.default)
  }

  /** Parse an OpenAPI spec from a URL or file path string */
  def parse(location: String, config: ParseConfig): Either[List[String], ParsedSpec] = {
    val parseOptions = new ParseOptions()
    parseOptions.setResolve(true)
    // Don't fully resolve - we need to preserve $ref info for type names
    parseOptions.setResolveFully(false)

    val result = new OpenAPIV3Parser().readLocation(location, java.util.Collections.emptyList(), parseOptions)

    val messages = Option(result.getMessages).map(_.asScala.toList).getOrElse(Nil)
    val openApi = result.getOpenAPI

    if (openApi == null) {
      Left(if (messages.isEmpty) List("Failed to parse OpenAPI spec") else messages)
    } else {
      Right(extractSpec(openApi, config))
    }
  }

  /** Parse an OpenAPI spec from a URL or file path string with default config */
  def parse(location: String): Either[List[String], ParsedSpec] = {
    parse(location, ParseConfig.default)
  }

  /** Parse an OpenAPI spec from YAML/JSON content string */
  def parseContent(content: String, config: ParseConfig): Either[List[String], ParsedSpec] = {
    val parseOptions = new ParseOptions()
    parseOptions.setResolve(true)
    parseOptions.setResolveFully(true)

    val result = new OpenAPIV3Parser().readContents(content, java.util.Collections.emptyList(), parseOptions)

    val messages = Option(result.getMessages).map(_.asScala.toList).getOrElse(Nil)
    val openApi = result.getOpenAPI

    if (openApi == null) {
      Left(if (messages.isEmpty) List("Failed to parse OpenAPI spec") else messages)
    } else {
      Right(extractSpec(openApi, config))
    }
  }

  /** Parse an OpenAPI spec from YAML/JSON content string with default config */
  def parseContent(content: String): Either[List[String], ParsedSpec] = {
    parseContent(content, ParseConfig.default)
  }

  private def extractSpec(openApi: io.swagger.v3.oas.models.OpenAPI, config: ParseConfig): ParsedSpec = {
    val info = extractInfo(openApi)
    val extracted = ModelExtractor.extract(openApi)
    val apis = ApiExtractor.extract(openApi)
    val webhooks = ApiExtractor.extractWebhooks(openApi)
    val securitySchemes = extractSecuritySchemes(openApi)

    // Run validation if enabled
    val warnings = if (config.validateSpec) {
      SpecValidator.validate(openApi)
    } else {
      Nil
    }

    // Link sum types to their subtypes
    val linkedModels = linkSumTypeParents(extracted.models, extracted.sumTypes)

    ParsedSpec(
      info = info,
      models = linkedModels,
      sumTypes = extracted.sumTypes,
      apis = apis,
      webhooks = webhooks,
      securitySchemes = securitySchemes,
      warnings = warnings
    )
  }

  private def extractInfo(openApi: io.swagger.v3.oas.models.OpenAPI): SpecInfo = {
    val info = openApi.getInfo
    SpecInfo(
      title = Option(info).flatMap(i => Option(i.getTitle)).getOrElse("API"),
      version = Option(info).flatMap(i => Option(i.getVersion)).getOrElse("1.0.0"),
      description = Option(info).flatMap(i => Option(i.getDescription))
    )
  }

  private def extractSecuritySchemes(openApi: io.swagger.v3.oas.models.OpenAPI): Map[String, SecurityScheme] = {
    Option(openApi.getComponents)
      .flatMap(c => Option(c.getSecuritySchemes))
      .map(_.asScala.toMap)
      .getOrElse(Map.empty[String, io.swagger.v3.oas.models.security.SecurityScheme])
      .flatMap { case (name, scheme) =>
        extractSecurityScheme(scheme).map(name -> _)
      }
  }

  private def extractSecurityScheme(scheme: io.swagger.v3.oas.models.security.SecurityScheme): Option[SecurityScheme] = {
    import io.swagger.v3.oas.models.security.{SecurityScheme => SwaggerSecurityScheme}

    scheme.getType match {
      case SwaggerSecurityScheme.Type.APIKEY =>
        val paramIn = scheme.getIn match {
          case SwaggerSecurityScheme.In.HEADER => ParameterIn.Header
          case SwaggerSecurityScheme.In.QUERY  => ParameterIn.Query
          case SwaggerSecurityScheme.In.COOKIE => ParameterIn.Cookie
          case null                            => ParameterIn.Header
        }
        Some(SecurityScheme.ApiKey(scheme.getName, paramIn))

      case SwaggerSecurityScheme.Type.HTTP =>
        Some(SecurityScheme.Http(scheme.getScheme, Option(scheme.getBearerFormat)))

      case SwaggerSecurityScheme.Type.OAUTH2 =>
        val flows = scheme.getFlows
        Some(
          SecurityScheme.OAuth2(
            OAuth2Flows(
              authorizationCode = Option(flows.getAuthorizationCode).map(extractOAuth2Flow),
              clientCredentials = Option(flows.getClientCredentials).map(extractOAuth2Flow),
              password = Option(flows.getPassword).map(extractOAuth2Flow),
              `implicit` = Option(flows.getImplicit).map(extractOAuth2Flow)
            )
          )
        )

      case SwaggerSecurityScheme.Type.OPENIDCONNECT =>
        Some(SecurityScheme.OpenIdConnect(scheme.getOpenIdConnectUrl))

      case SwaggerSecurityScheme.Type.MUTUALTLS =>
        // Mutual TLS not yet supported
        None

      case null => None
    }
  }

  private def extractOAuth2Flow(flow: io.swagger.v3.oas.models.security.OAuthFlow): OAuth2Flow = {
    OAuth2Flow(
      authorizationUrl = Option(flow.getAuthorizationUrl),
      tokenUrl = Option(flow.getTokenUrl),
      refreshUrl = Option(flow.getRefreshUrl),
      scopes = Option(flow.getScopes).map(_.asScala.toMap).getOrElse(Map.empty[String, String])
    )
  }

  /** Link ObjectTypes to their parent sum types and populate discriminator values */
  private def linkSumTypeParents(models: List[ModelClass], sumTypes: List[SumType]): List[ModelClass] = {
    // Build a map from subtype name to list of parent sum type names
    // This is the 2.12-compatible version of groupMap
    val pairs = sumTypes.flatMap(st => st.subtypeNames.map(sub => sub -> st.name))
    val sumTypesBySubtype: Map[String, List[String]] = pairs.groupBy(_._1).map { case (k, vs) => k -> vs.map(_._2) }

    // Build a map from subtype name to discriminator property and value
    // Key: subtype name, Value: Map[discriminator property name -> discriminator value]
    val discriminatorPairs = sumTypes.flatMap { st =>
      st.subtypeNames.map { subtypeName =>
        val discValue = st.discriminator.mapping.getOrElse(subtypeName, subtypeName)
        subtypeName -> (st.discriminator.propertyName -> discValue)
      }
    }
    val discriminatorsBySubtype: Map[String, Map[String, String]] = discriminatorPairs
      .groupBy(_._1)
      .map { case (k, vs) => k -> vs.map(_._2).toMap }

    models.map {
      case obj: ModelClass.ObjectType =>
        val parents = sumTypesBySubtype.getOrElse(obj.name, Nil)
        val discriminators = discriminatorsBySubtype.getOrElse(obj.name, Map.empty)
        if (parents.nonEmpty || discriminators.nonEmpty) {
          obj.copy(sumTypeParents = parents, discriminatorValues = discriminators)
        } else {
          obj
        }
      case other => other
    }
  }
}
