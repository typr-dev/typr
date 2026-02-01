package typr.grpc

import typr.jvm
import typr.effects.EffectType

import java.nio.file.Path

/** Configuration options for gRPC/Protobuf code generation */
case class GrpcOptions(
    /** Base package for generated code */
    pkg: jvm.QIdent,
    /** Proto source location */
    protoSource: ProtoSource,
    /** Whether to generate message classes (clean JVM types) */
    generateMessages: Boolean,
    /** Whether to generate service interfaces */
    generateServices: Boolean,
    /** Whether to generate server adapters (extends grpc-java *ImplBase) */
    generateServers: Boolean,
    /** Whether to generate client wrappers (wraps grpc-java stubs) */
    generateClients: Boolean,
    /** Effect type for async/reactive operations */
    effectType: EffectType,
    /** Framework integration for gRPC (Spring, Quarkus, or None for framework-agnostic code) */
    frameworkIntegration: GrpcFrameworkIntegration
)

object GrpcOptions {
  def default(pkg: jvm.QIdent, protoSource: ProtoSource): GrpcOptions =
    GrpcOptions(
      pkg = pkg,
      protoSource = protoSource,
      generateMessages = true,
      generateServices = true,
      generateServers = true,
      generateClients = true,
      effectType = EffectType.Blocking,
      frameworkIntegration = GrpcFrameworkIntegration.None
    )
}

/** Proto source for gRPC code generation */
sealed trait ProtoSource

object ProtoSource {

  /** Load protos from a directory containing .proto files.
    *
    * @param path
    *   Directory containing .proto files
    * @param includePaths
    *   Additional include paths for proto imports (e.g., for well-known types). The source directory is always included.
    */
  case class Directory(path: Path, includePaths: List[Path]) extends ProtoSource

  object Directory {
    def apply(path: Path): Directory = Directory(path, Nil)
  }

  /** Use a pre-built FileDescriptorSet file.
    *
    * For users who already run protoc in their build and have a descriptor set file.
    */
  case class DescriptorSet(path: Path) extends ProtoSource
}

/** Framework integration for gRPC server/client generation.
  *
  * This determines which annotations and types are used in generated code:
  *   - None: Generate framework-agnostic code (default)
  *   - Spring: Use spring-grpc annotations (@GrpcService, @ImportGrpcClients)
  *   - Quarkus: Use quarkus-grpc annotations (@GrpcService, @GrpcClient)
  */
sealed trait GrpcFrameworkIntegration {
  import typr.grpc.codegen.GrpcFramework

  /** Get the GrpcFramework implementation for this integration, if any */
  def grpcFramework: Option[GrpcFramework]
}

object GrpcFrameworkIntegration {
  import typr.grpc.codegen.{GrpcFrameworkSpring, GrpcFrameworkQuarkus}

  /** No framework annotations - generate framework-agnostic code */
  case object None extends GrpcFrameworkIntegration {
    override def grpcFramework: Option[typr.grpc.codegen.GrpcFramework] = scala.None
  }

  /** Spring gRPC integration.
    *
    * Server: @GrpcService annotation, auto-registered as BindableService Client: @ImportGrpcClients + @Autowired stub injection
    */
  case object Spring extends GrpcFrameworkIntegration {
    override def grpcFramework: Option[typr.grpc.codegen.GrpcFramework] = Some(GrpcFrameworkSpring)
  }

  /** Quarkus gRPC integration.
    *
    * Server: @GrpcService + @Singleton annotation Client: @GrpcClient("service-name") field injection
    */
  case object Quarkus extends GrpcFrameworkIntegration {
    override def grpcFramework: Option[typr.grpc.codegen.GrpcFramework] = Some(GrpcFrameworkQuarkus)
  }
}
