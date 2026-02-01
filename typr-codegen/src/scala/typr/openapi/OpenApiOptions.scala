package typr.openapi

import typr.{jvm, TypeDefinitions}
import typr.effects.{EffectType, EffectTypeOps}
import typr.openapi.codegen.{JacksonSupport, JsonLibSupport}

/** Configuration options for OpenAPI code generation */
case class OpenApiOptions(
    /** Base package for generated code */
    pkg: jvm.QIdent,
    /** Sub-package for model classes (default: "model") */
    modelPackage: String,
    /** Sub-package for API interfaces (default: "api") */
    apiPackage: String,
    /** JSON library to use for serialization annotations */
    jsonLib: JsonLibSupport,
    /** Server library for API generation (None = base interface only) */
    serverLib: Option[OpenApiServerLib],
    /** Client library for API generation (None = no client) */
    clientLib: Option[OpenApiClientLib],
    /** Whether to generate wrapper types for ID-like fields */
    generateWrapperTypes: Boolean,
    /** Custom type mappings: schema name -> qualified type name */
    typeOverrides: Map[String, jvm.Type.Qualified],
    /** Explicit field name -> type mappings. These override field types by property name (lowercase). Used for shared types across sources where the type is defined elsewhere.
      */
    fieldTypeOverrides: Map[String, jvm.Type.Qualified],
    /** TypeDefinitions for matching fields to shared types. When a field matches an ApiMatch predicate, it uses the corresponding wrapper type.
      */
    typeDefinitions: TypeDefinitions,
    /** Whether to generate nullable fields as Optional instead of using @Nullable */
    useOptionalForNullable: Boolean,
    /** Whether to generate deprecated annotations */
    includeDeprecated: Boolean,
    /** Whether to add validation annotations (JSR-380) */
    generateValidation: Boolean,
    /** Whether to generate API interface files */
    generateApiInterfaces: Boolean,
    /** Whether to generate webhook handler interfaces (OpenAPI 3.1+) */
    generateWebhooks: Boolean,
    /** Whether to generate callback handler interfaces */
    generateCallbacks: Boolean,
    /** Whether to use generic response types to deduplicate response classes with the same shape. When true, generates types like Response200Default[T] instead of per-method response types. This
      * reduces generated code when many methods have the same response status codes.
      */
    useGenericResponseTypes: Boolean
)

object OpenApiOptions {
  def default(pkg: jvm.QIdent): OpenApiOptions =
    OpenApiOptions(
      pkg = pkg,
      modelPackage = "model",
      apiPackage = "api",
      jsonLib = JacksonSupport,
      serverLib = Some(OpenApiServerLib.QuarkusReactive),
      clientLib = None,
      generateWrapperTypes = true,
      typeOverrides = Map.empty,
      fieldTypeOverrides = Map.empty,
      typeDefinitions = TypeDefinitions.Empty,
      useOptionalForNullable = false,
      includeDeprecated = true,
      generateValidation = false,
      generateApiInterfaces = true,
      generateWebhooks = true,
      generateCallbacks = true,
      useGenericResponseTypes = false
    )
}

/** Effect type for async/reactive APIs.
  *
  * Type alias for the shared EffectType. OpenAPI code generation uses these to wrap async operations in the appropriate effect wrapper for the target framework.
  */
type OpenApiEffectType = EffectType

/** Effect type companion with values for backwards compatibility */
object OpenApiEffectType {

  /** SmallRye Mutiny Uni - used by Quarkus */
  val MutinyUni: EffectType = EffectType.MutinyUni

  /** Project Reactor Mono - used by Spring WebFlux */
  val ReactorMono: EffectType = EffectType.ReactorMono

  /** Java CompletableFuture */
  val CompletableFuture: EffectType = EffectType.CompletableFuture

  /** Cats Effect IO - used by http4s */
  val CatsIO: EffectType = EffectType.CatsIO

  /** ZIO */
  val ZIO: EffectType = EffectType.ZIO

  /** Blocking/synchronous (no effect wrapper) */
  val Blocking: EffectType = EffectType.Blocking
}

/** Server library for API generation */
sealed abstract class OpenApiServerLib(val effectType: OpenApiEffectType)
object OpenApiServerLib {

  /** Quarkus with RESTEasy Reactive (Mutiny Uni) */
  case object QuarkusReactive extends OpenApiServerLib(OpenApiEffectType.MutinyUni)

  /** Quarkus with RESTEasy Classic (blocking) */
  case object QuarkusBlocking extends OpenApiServerLib(OpenApiEffectType.Blocking)

  /** Spring WebFlux (Reactor Mono) */
  case object SpringWebFlux extends OpenApiServerLib(OpenApiEffectType.ReactorMono)

  /** Spring MVC (blocking) */
  case object SpringMvc extends OpenApiServerLib(OpenApiEffectType.Blocking)

  /** JAX-RS with async (CompletableFuture) */
  case object JaxRsAsync extends OpenApiServerLib(OpenApiEffectType.CompletableFuture)

  /** JAX-RS sync (blocking) */
  case object JaxRsSync extends OpenApiServerLib(OpenApiEffectType.Blocking)

  /** http4s with Cats Effect */
  case object Http4s extends OpenApiServerLib(OpenApiEffectType.CatsIO)

  /** ZIO HTTP */
  case object ZioHttp extends OpenApiServerLib(OpenApiEffectType.ZIO)
}

/** Client library for API generation */
sealed abstract class OpenApiClientLib(val effectType: OpenApiEffectType)
object OpenApiClientLib {

  /** JDK HTTP Client (java.net.http.HttpClient) - zero dependency, configurable effect type.
    *
    * When effectType is Blocking: uses httpClient.send() (synchronous) When effectType is async (MutinyUni, ReactorMono, etc.): uses httpClient.sendAsync() wrapped in the effect type
    *
    * Examples:
    *   - JdkHttpClient(Blocking) for synchronous blocking calls
    *   - JdkHttpClient(MutinyUni) for Quarkus reactive
    *   - JdkHttpClient(ReactorMono) for Spring WebFlux
    *   - JdkHttpClient(CompletableFuture) for plain Java async
    */
  case class JdkHttpClient(override val effectType: OpenApiEffectType) extends OpenApiClientLib(effectType)

  /** Spring WebClient (Reactor Mono) */
  case object SpringWebClient extends OpenApiClientLib(OpenApiEffectType.ReactorMono)

  /** Spring RestTemplate (blocking) */
  case object SpringRestTemplate extends OpenApiClientLib(OpenApiEffectType.Blocking)

  /** http4s client with Cats Effect */
  case object Http4s extends OpenApiClientLib(OpenApiEffectType.CatsIO)

  /** sttp client (supports multiple backends) */
  case object Sttp extends OpenApiClientLib(OpenApiEffectType.CatsIO)

  /** ZIO HTTP client */
  case object ZioHttp extends OpenApiClientLib(OpenApiEffectType.ZIO)
}
