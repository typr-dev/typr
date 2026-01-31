package typr.effects

import typr.jvm
import typr.internal.codegen._

/** Shared effect type definitions for code generation.
  *
  * Effect types are used by both OpenAPI and Avro code generation to wrap async operations in the appropriate effect wrapper for the target framework.
  */
sealed abstract class EffectType(val effectType: Option[jvm.Type.Qualified], val ops: Option[EffectTypeOps])

object EffectType {

  private val UniType = jvm.Type.Qualified(jvm.QIdent(List("io", "smallrye", "mutiny", "Uni").map(jvm.Ident.apply)))
  private val MonoType = jvm.Type.Qualified(jvm.QIdent(List("reactor", "core", "publisher", "Mono").map(jvm.Ident.apply)))
  private val CompletableFutureType = jvm.Type.Qualified(jvm.QIdent(List("java", "util", "concurrent", "CompletableFuture").map(jvm.Ident.apply)))
  private val IOType = jvm.Type.Qualified(jvm.QIdent(List("cats", "effect", "IO").map(jvm.Ident.apply)))
  private val TaskType = jvm.Type.Qualified(jvm.QIdent(List("zio", "Task").map(jvm.Ident.apply)))

  // Helper types for foreachDiscard implementations
  private val FluxType = jvm.Type.Qualified(jvm.QIdent(List("reactor", "core", "publisher", "Flux").map(jvm.Ident.apply)))
  private val MultiType = jvm.Type.Qualified(jvm.QIdent(List("io", "smallrye", "mutiny", "Multi").map(jvm.Ident.apply)))
  private val StreamConvertersType = jvm.Type.Qualified(jvm.QIdent("scala.jdk.CollectionConverters"))

  /** SmallRye Mutiny Uni operations - used by Quarkus */
  private object MutinyUniOps extends EffectTypeOps {
    def tpe: jvm.Type.Qualified = UniType
    def map(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.map($f)"
    def flatMap(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.flatMap($f)"
    def pure(value: jvm.Code): jvm.Code = code"$tpe.createFrom().item($value)"
    def fromCompletionStage(supplier: jvm.Code): jvm.Code = code"$tpe.createFrom().completionStage($supplier)"
    // Multi.createFrom().iterable(it).onItem().transformToUniAndConcatenate(f).collect().last()
    def foreachDiscard(iterable: jvm.Code, elementVar: jvm.Ident, body: jvm.Code): jvm.Code =
      code"$MultiType.createFrom().iterable($iterable).onItem().transformToUniAndConcatenate($elementVar -> $body).collect().last()"
    // Uni.createFrom().emitter(em -> { ... em.complete(result) / em.fail(error) ... })
    def async(resultType: jvm.Type)(bodyBuilder: (jvm.Code => jvm.Code, jvm.Code => jvm.Code) => jvm.Code): jvm.Code = {
      val em = jvm.Ident("em")
      val onSuccess = (value: jvm.Code) => em.code.invoke("complete", value)
      val onFailure = (error: jvm.Code) => em.code.invoke("fail", error)
      val body = bodyBuilder(onSuccess, onFailure)
      val emitterLambda = jvm.Lambda(List(jvm.LambdaParam(em)), jvm.Body.Stmts(List(body)))
      code"$tpe.createFrom().emitter($emitterLambda)"
    }
    def subscribeWith(effect: jvm.Code, onItem: jvm.Code, onFailure: jvm.Code): jvm.Code =
      code"$effect.subscribe().with($onItem, $onFailure)"
    def defer(supplier: jvm.Code): jvm.Code =
      code"$tpe.createFrom().item($supplier)"
  }

  /** Project Reactor Mono operations - used by Spring WebFlux */
  private object ReactorMonoOps extends EffectTypeOps {
    def tpe: jvm.Type.Qualified = MonoType
    def map(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.map($f)"
    def flatMap(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.flatMap($f)"
    def pure(value: jvm.Code): jvm.Code = code"$tpe.just($value)"
    def fromCompletionStage(supplier: jvm.Code): jvm.Code = code"$tpe.fromCompletionStage($supplier)"
    // Flux.fromIterable(it).flatMap(f).then()
    def foreachDiscard(iterable: jvm.Code, elementVar: jvm.Ident, body: jvm.Code): jvm.Code =
      code"$FluxType.fromIterable($iterable).flatMap($elementVar -> $body).then()"
    // Mono.create(sink -> { ... sink.success(result) / sink.error(error) ... })
    def async(resultType: jvm.Type)(bodyBuilder: (jvm.Code => jvm.Code, jvm.Code => jvm.Code) => jvm.Code): jvm.Code = {
      val sink = jvm.Ident("sink")
      val onSuccess = (value: jvm.Code) => sink.code.invoke("success", value)
      val onFailure = (error: jvm.Code) => sink.code.invoke("error", error)
      val body = bodyBuilder(onSuccess, onFailure)
      val sinkLambda = jvm.Lambda(List(jvm.LambdaParam(sink)), jvm.Body.Stmts(List(body)))
      code"$tpe.create($sinkLambda)"
    }
    def subscribeWith(effect: jvm.Code, onItem: jvm.Code, onFailure: jvm.Code): jvm.Code =
      code"$effect.subscribe($onItem, $onFailure)"
    def defer(supplier: jvm.Code): jvm.Code =
      code"$tpe.fromSupplier($supplier)"
  }

  /** Java CompletableFuture operations */
  private object CompletableFutureOps extends EffectTypeOps {
    private val StreamSupportType = jvm.Type.Qualified(jvm.QIdent("java.util.stream.StreamSupport"))

    def tpe: jvm.Type.Qualified = CompletableFutureType
    def map(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.thenApply($f)"
    def flatMap(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.thenCompose($f)"
    def pure(value: jvm.Code): jvm.Code = code"$tpe.completedFuture($value)"
    def fromCompletionStage(supplier: jvm.Code): jvm.Code = code"($supplier).get()"
    // CompletableFuture.allOf(StreamSupport.stream(...).map(f).toArray(CompletableFuture[]::new))
    def foreachDiscard(iterable: jvm.Code, elementVar: jvm.Ident, body: jvm.Code): jvm.Code =
      code"$tpe.allOf($StreamSupportType.stream($iterable.spliterator(), false).map($elementVar -> $body).toArray($tpe[]::new))"
    // CompletableFuture.supplyAsync(() -> { CF<T> future = new CF<>(); body; return future; }).thenCompose(f -> f)
    def async(resultType: jvm.Type)(bodyBuilder: (jvm.Code => jvm.Code, jvm.Code => jvm.Code) => jvm.Code): jvm.Code = {
      val future = jvm.Ident("future")
      val onSuccess = (value: jvm.Code) => future.code.invoke("complete", value)
      val onFailure = (error: jvm.Code) => future.code.invoke("completeExceptionally", error)
      val body = bodyBuilder(onSuccess, onFailure)

      // Use explicit type declaration to avoid inference issues: CompletableFuture<ResultType> future = new CompletableFuture<>();
      val futureType = tpe.of(resultType)
      val newFuture = jvm.New(jvm.InferredTargs(tpe.code).code, Nil).code
      val futureDecl = jvm.LocalVar(future, Some(futureType), newFuture)
      val supplierBody = jvm.Body.Stmts(List(futureDecl.code, body, jvm.Return(future.code).code))
      val supplierLambda = jvm.Lambda(Nil, supplierBody)

      val f = jvm.Ident("f")
      val identityLambda = jvm.Lambda(List(jvm.LambdaParam(f)), jvm.Body.Expr(f.code))
      code"$tpe.supplyAsync($supplierLambda).thenCompose($identityLambda)"
    }
    def subscribeWith(effect: jvm.Code, onItem: jvm.Code, onFailure: jvm.Code): jvm.Code = {
      val t = jvm.Ident("t")
      val exceptionallyBody = jvm.Body.Stmts(
        List(
          onFailure.invoke("accept", t.code),
          jvm.Return(code"null").code
        )
      )
      val exceptionallyLambda = jvm.Lambda(List(jvm.LambdaParam(t)), exceptionallyBody)
      code"$effect.thenAccept($onItem).exceptionally($exceptionallyLambda)"
    }
    def defer(supplier: jvm.Code): jvm.Code =
      code"$tpe.supplyAsync($supplier)"
  }

  /** Cats Effect IO operations */
  private object CatsIOOps extends EffectTypeOps {
    def tpe: jvm.Type.Qualified = IOType
    def map(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.map($f)"
    def flatMap(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.flatMap($f)"
    def pure(value: jvm.Code): jvm.Code = code"$tpe.pure($value)"
    def fromCompletionStage(supplier: jvm.Code): jvm.Code = code"$tpe.fromCompletableFuture($tpe.delay(($supplier).apply()))"
    // iterable.asScala.toList.traverse_(f) - requires wildcard imports
    def foreachDiscard(iterable: jvm.Code, elementVar: jvm.Ident, body: jvm.Code): jvm.Code =
      code"$iterable.asScala.toList.traverse_($elementVar => $body)"
    override def foreachDiscardImports: List[String] = List("cats.syntax.all._", "scala.jdk.CollectionConverters._")
    // IO.async_ { cb => body }
    def async(resultType: jvm.Type)(bodyBuilder: (jvm.Code => jvm.Code, jvm.Code => jvm.Code) => jvm.Code): jvm.Code = {
      val cb = jvm.Ident("cb")
      // Use scala.Right/scala.Left which are auto-imported in scala package
      val onSuccess = (value: jvm.Code) => code"$cb(scala.Right($value))"
      val onFailure = (error: jvm.Code) => code"$cb(scala.Left($error))"
      val body = bodyBuilder(onSuccess, onFailure)
      val cbLambda = jvm.Lambda(List(jvm.LambdaParam(cb)), jvm.Body.Stmts(List(body)))
      code"$tpe.async_($cbLambda)"
    }
    def subscribeWith(effect: jvm.Code, onItem: jvm.Code, onFailure: jvm.Code): jvm.Code = {
      val result = jvm.Ident("result")
      code"{ val $result = $effect.unsafeRunSync()(cats.effect.unsafe.IORuntime.global); $onItem.apply($result) }"
    }
    def defer(supplier: jvm.Code): jvm.Code =
      code"$tpe.delay($supplier.apply())"
  }

  /** ZIO Task operations */
  private object ZIOOps extends EffectTypeOps {
    def tpe: jvm.Type.Qualified = TaskType
    def map(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.map($f)"
    def flatMap(effect: jvm.Code, f: jvm.Code): jvm.Code = code"$effect.flatMap($f)"
    def pure(value: jvm.Code): jvm.Code = code"zio.ZIO.succeed($value)"
    def fromCompletionStage(supplier: jvm.Code): jvm.Code = code"zio.ZIO.fromCompletableFuture(($supplier).apply())"
    // ZIO.foreachDiscard(iterable.asScala)(f) - requires wildcard import
    def foreachDiscard(iterable: jvm.Code, elementVar: jvm.Ident, body: jvm.Code): jvm.Code =
      code"zio.ZIO.foreachDiscard($iterable.asScala)($elementVar => $body)"
    override def foreachDiscardImports: List[String] = List("scala.jdk.CollectionConverters._")
    // ZIO.async { cb => body }
    def async(resultType: jvm.Type)(bodyBuilder: (jvm.Code => jvm.Code, jvm.Code => jvm.Code) => jvm.Code): jvm.Code = {
      val cb = jvm.Ident("cb")
      val onSuccess = (value: jvm.Code) => code"$cb(zio.ZIO.succeed($value))"
      val onFailure = (error: jvm.Code) => code"$cb(zio.ZIO.fail($error))"
      val body = bodyBuilder(onSuccess, onFailure)
      val cbLambda = jvm.Lambda(List(jvm.LambdaParam(cb)), jvm.Body.Stmts(List(body)))
      code"zio.ZIO.async($cbLambda)"
    }
    def subscribeWith(effect: jvm.Code, onItem: jvm.Code, onFailure: jvm.Code): jvm.Code = {
      val UnsafeType = jvm.Type.Qualified(jvm.QIdent("zio.Unsafe"))
      val RuntimeType = jvm.Type.Qualified(jvm.QIdent("zio.Runtime"))
      val u = jvm.Ident("u")
      val result = jvm.Ident("result")
      code"$UnsafeType.unsafe { implicit $u => val $result = $RuntimeType.default.unsafe.run($effect).getOrThrowFiberFailure(); $onItem.apply($result) }"
    }
    def defer(supplier: jvm.Code): jvm.Code =
      code"zio.ZIO.attempt($supplier.apply())"
  }

  /** SmallRye Mutiny Uni - used by Quarkus */
  case object MutinyUni extends EffectType(Some(UniType), Some(MutinyUniOps))

  /** Project Reactor Mono - used by Spring WebFlux */
  case object ReactorMono extends EffectType(Some(MonoType), Some(ReactorMonoOps))

  /** Java CompletableFuture */
  case object CompletableFuture extends EffectType(Some(CompletableFutureType), Some(CompletableFutureOps))

  /** Cats Effect IO - used by http4s */
  case object CatsIO extends EffectType(Some(IOType), Some(CatsIOOps))

  /** ZIO */
  case object ZIO extends EffectType(Some(TaskType), Some(ZIOOps))

  /** Blocking/synchronous (no effect wrapper) */
  case object Blocking extends EffectType(None, None)
}
