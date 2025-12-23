package typr.openapi

/** Complete parsed OpenAPI specification ready for code generation */
case class ParsedSpec(
    info: SpecInfo,
    models: List[ModelClass],
    sumTypes: List[SumType],
    apis: List[ApiInterface],
    webhooks: List[Webhook],
    securitySchemes: Map[String, SecurityScheme],
    warnings: List[OpenApiError]
)

/** OpenAPI spec metadata */
case class SpecInfo(
    title: String,
    version: String,
    description: Option[String]
)

/** Security scheme types */
sealed trait SecurityScheme
object SecurityScheme {
  case class ApiKey(name: String, in: ParameterIn) extends SecurityScheme
  case class Http(scheme: String, bearerFormat: Option[String]) extends SecurityScheme
  case class OAuth2(flows: OAuth2Flows) extends SecurityScheme
  case class OpenIdConnect(openIdConnectUrl: String) extends SecurityScheme
}

/** OAuth2 flows configuration */
case class OAuth2Flows(
    authorizationCode: Option[OAuth2Flow],
    clientCredentials: Option[OAuth2Flow],
    password: Option[OAuth2Flow],
    `implicit`: Option[OAuth2Flow]
)

case class OAuth2Flow(
    authorizationUrl: Option[String],
    tokenUrl: Option[String],
    refreshUrl: Option[String],
    scopes: Map[String, String]
)
