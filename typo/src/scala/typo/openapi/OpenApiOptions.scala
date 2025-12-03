package typo.openapi

import typo.jvm

/** Configuration options for OpenAPI code generation */
case class OpenApiOptions(
    /** Base package for generated code */
    pkg: jvm.QIdent,
    /** Sub-package for model classes (default: "model") */
    modelPackage: String,
    /** Sub-package for API interfaces (default: "api") */
    apiPackage: String,
    /** JSON library to use for serialization annotations */
    jsonLib: OpenApiJsonLib,
    /** Server library for API generation (None = base interface only) */
    serverLib: Option[OpenApiServerLib],
    /** Client library for API generation (None = no client) */
    clientLib: Option[OpenApiClientLib],
    /** Whether to generate wrapper types for ID-like fields */
    generateWrapperTypes: Boolean,
    /** Custom type mappings: schema name -> qualified type name */
    typeOverrides: Map[String, jvm.Type.Qualified],
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
      jsonLib = OpenApiJsonLib.Jackson,
      serverLib = Some(OpenApiServerLib.QuarkusReactive),
      clientLib = Some(OpenApiClientLib.MicroProfileReactive),
      generateWrapperTypes = true,
      typeOverrides = Map.empty,
      useOptionalForNullable = false,
      includeDeprecated = true,
      generateValidation = false,
      generateApiInterfaces = true,
      generateWebhooks = true,
      generateCallbacks = true,
      useGenericResponseTypes = false
    )
}

/** JSON library options for OpenAPI generation */
sealed trait OpenApiJsonLib
object OpenApiJsonLib {
  case object Jackson extends OpenApiJsonLib
  case object Circe extends OpenApiJsonLib
  case object PlayJson extends OpenApiJsonLib
  case object ZioJson extends OpenApiJsonLib
}

/** Effect type operations - monadic interface for effect types */
trait EffectTypeOps {

  /** The effect type itself (e.g., Uni, Mono) */
  def tpe: jvm.Type.Qualified

  /** Map over the effect value: effect.map(f) */
  def map(effect: jvm.Code, f: jvm.Code): jvm.Code

  /** FlatMap over the effect value: effect.flatMap(f) where f returns Effect[B] */
  def flatMap(effect: jvm.Code, f: jvm.Code): jvm.Code

  /** Wrap a value in the effect: Effect.pure(value) */
  def pure(value: jvm.Code): jvm.Code
}

/** Effect type for async/reactive APIs */
sealed abstract class OpenApiEffectType(val effectType: Option[jvm.Type.Qualified], val ops: Option[EffectTypeOps])
object OpenApiEffectType {
  import typo.internal.codegen._

  private val UniType = jvm.Type.Qualified(jvm.QIdent(List("io", "smallrye", "mutiny", "Uni").map(jvm.Ident.apply)))
  private val MonoType = jvm.Type.Qualified(jvm.QIdent(List("reactor", "core", "publisher", "Mono").map(jvm.Ident.apply)))
  private val CompletableFutureType = jvm.Type.Qualified(jvm.QIdent(List("java", "util", "concurrent", "CompletableFuture").map(jvm.Ident.apply)))
  private val IOType = jvm.Type.Qualified(jvm.QIdent(List("cats", "effect", "IO").map(jvm.Ident.apply)))
  private val TaskType = jvm.Type.Qualified(jvm.QIdent(List("zio", "Task").map(jvm.Ident.apply)))

  private object MutinyUniOps extends EffectTypeOps {
    def tpe: jvm.Type.Qualified = UniType
    def map(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.map($f)"
    def flatMap(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.flatMap($f)"
    def pure(value: jvm.Code): jvm.Code = code"$tpe.createFrom().item($value)"
  }

  private object ReactorMonoOps extends EffectTypeOps {
    def tpe: jvm.Type.Qualified = MonoType
    def map(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.map($f)"
    def flatMap(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.flatMap($f)"
    def pure(value: jvm.Code): jvm.Code = code"$tpe.just($value)"
  }

  private object CompletableFutureOps extends EffectTypeOps {
    def tpe: jvm.Type.Qualified = CompletableFutureType
    def map(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.thenApply($f)"
    def flatMap(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.thenCompose($f)"
    def pure(value: jvm.Code): jvm.Code = code"$tpe.completedFuture($value)"
  }

  private object CatsIOOps extends EffectTypeOps {
    def tpe: jvm.Type.Qualified = IOType
    def map(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.map($f)"
    def flatMap(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.flatMap($f)"
    def pure(value: jvm.Code): jvm.Code = code"$tpe.pure($value)"
  }

  private object ZIOOps extends EffectTypeOps {
    def tpe: jvm.Type.Qualified = TaskType
    def map(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.map($f)"
    def flatMap(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.flatMap($f)"
    def pure(value: jvm.Code): jvm.Code = code"zio.ZIO.succeed($value)"
  }

  /** SmallRye Mutiny Uni - used by Quarkus */
  case object MutinyUni extends OpenApiEffectType(Some(UniType), Some(MutinyUniOps))

  /** Project Reactor Mono - used by Spring WebFlux */
  case object ReactorMono extends OpenApiEffectType(Some(MonoType), Some(ReactorMonoOps))

  /** Java CompletableFuture */
  case object CompletableFuture extends OpenApiEffectType(Some(CompletableFutureType), Some(CompletableFutureOps))

  /** Cats Effect IO - used by http4s */
  case object CatsIO extends OpenApiEffectType(Some(IOType), Some(CatsIOOps))

  /** ZIO */
  case object ZIO extends OpenApiEffectType(Some(TaskType), Some(ZIOOps))

  /** Blocking/synchronous (no effect wrapper) */
  case object Blocking extends OpenApiEffectType(None, None)
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

  /** MicroProfile Rest Client Reactive (Mutiny Uni) */
  case object MicroProfileReactive extends OpenApiClientLib(OpenApiEffectType.MutinyUni)

  /** MicroProfile Rest Client (blocking) */
  case object MicroProfileBlocking extends OpenApiClientLib(OpenApiEffectType.Blocking)

  /** Spring WebClient (Reactor Mono) */
  case object SpringWebClient extends OpenApiClientLib(OpenApiEffectType.ReactorMono)

  /** Spring RestTemplate (blocking) */
  case object SpringRestTemplate extends OpenApiClientLib(OpenApiEffectType.Blocking)

  /** Vert.x Web Client (Mutiny Uni) */
  case object VertxMutiny extends OpenApiClientLib(OpenApiEffectType.MutinyUni)

  /** http4s client with Cats Effect */
  case object Http4s extends OpenApiClientLib(OpenApiEffectType.CatsIO)

  /** sttp client (supports multiple backends) */
  case object Sttp extends OpenApiClientLib(OpenApiEffectType.CatsIO)

  /** ZIO HTTP client */
  case object ZioHttp extends OpenApiClientLib(OpenApiEffectType.ZIO)
}
