package typr

import scala.reflect.ClassTag

/** Simplified model of JVM languages.
  *
  * The generated code is stored in the `Code` data structure. For full flexibility, some parts are stored as text and other parts in trees. Most notably *all type and term references* which need an
  * import to work should be in a tree.
  *
  * You'll mainly use this module with the `code"..."` interpolator.
  */
object jvm {

  /** Variance for type parameters */
  sealed trait Variance
  object Variance {
    case object Invariant extends Variance
    case object Covariant extends Variance
    case object Contravariant extends Variance
  }

  sealed trait Tree

  case class Ident(value: String) extends Tree {
    def map(f: String => String): Ident = Ident(f(value))
    def appended(suffix: String) = new Ident(value + suffix)
    def prepended(prefix: String) = new Ident(prefix + value)
  }

  object Ident {
    implicit val ordering: Ordering[Ident] = Ordering.by(_.value)
  }

  case class QIdent(idents: List[Ident]) extends Tree {
    lazy val dotName = idents.map(_.value).mkString(".")
    def parentOpt: Option[QIdent] = if (idents.size <= 1) None else Some(QIdent(idents.dropRight(1)))
    require(idents.nonEmpty)
    def /(ident: Ident): QIdent = QIdent(idents :+ ident)
    def /(newIdents: List[Ident]): QIdent = QIdent(idents ++ newIdents)
    def name = idents.last
  }
  object QIdent {
    def apply(str: String): QIdent =
      QIdent(str.split('.').toList.map(Ident.apply))
    def of(idents: Ident*): QIdent =
      QIdent(idents.toList)
  }

  sealed trait Ref[+T <: Type] extends Tree {
    def name: Ident
    def tpe: T
  }
  object Ref {
    def unapply[T <: Type](ref: Ref[T]): Some[(Ident, T)] = Some((ref.name, ref.tpe))
  }

  case class Param[+T <: Type](annotations: List[Annotation], comments: Comments, name: Ident, tpe: T, default: Option[Code]) extends Ref[T] {}
  object Param {
    implicit class ParamOpt[T <: Type](private val p: Param[T]) {
      def narrow[TT <: T](implicit CT: ClassTag[TT]): Param[TT] =
        p.tpe match {
          case tt: TT => Param(p.annotations, p.comments, p.name, tt, p.default)
          case other  => sys.error(s"Unexpected $other, should have gotten a ${CT.runtimeClass.getSimpleName}")
        }
    }

    def apply[T <: Type](name: Ident, tpe: T): Param[T] = Param(Nil, Comments.Empty, name, tpe, None)
  }

  /** Ternary/inline if expression: Scala: `if (cond) then else`, Java: `cond ? then : else` */
  case class IfExpr(cond: Code, thenp: Code, elsep: Code) extends Tree

  /** If statement with branches and optional else. Renders as: if (cond1) { body1 } else if (cond2) { body2 } else { elseBody }
    */
  case class If(branches: List[If.Branch], elseBody: Option[Code]) extends Tree
  object If {
    case class Branch(cond: Code, body: Code)
    def apply(cond: Code, body: Code): If = If(List(Branch(cond, body)), None)
    def apply(cond: Code, thenBody: Code, elseBody: Code): If = If(List(Branch(cond, thenBody)), Some(elseBody))
  }

  /** While loop statement: all languages: `while (cond) { body }` */
  case class While(cond: Code, body: List[Code]) extends Tree

  case class MethodRef(tpe: Type, name: Ident) extends Tree
  case class ConstructorMethodRef(tpe: Type) extends Tree
  case class ClassOf(tpe: Type) extends Tree

  /** Java Class literal - for Kotlin renders as `tpe::class.java`, for Java/Scala renders same as ClassOf */
  case class JavaClassOf(tpe: Type) extends Tree

  /** Not-null assertion: Kotlin renders as `expr!!`, other languages ignore */
  case class NotNull(expr: Code) extends Tree

  case class StrLit(str: String) extends Tree

  case class Annotation(
      tpe: Type.Qualified,
      args: List[Annotation.Arg] = Nil,
      /** Use-site target for Kotlin: @get:, @field:, @param: etc. None for other languages. */
      useTarget: Option[Annotation.UseTarget] = None
  ) extends Tree

  object Annotation {
    sealed trait Arg
    object Arg {
      case class Named(name: Ident, value: Code) extends Arg
      case class Positional(value: Code) extends Arg
    }

    /** Use-site targets for Kotlin annotations on data class parameters */
    sealed abstract class UseTarget(val name: String)
    object UseTarget {
      case object Get extends UseTarget("get")
      case object Set extends UseTarget("set")
      case object Field extends UseTarget("field")
      case object Param extends UseTarget("param")
      case object SetParam extends UseTarget("setparam")
      case object Delegate extends UseTarget("delegate")
      case object File extends UseTarget("file")
      case object Property extends UseTarget("property")
      case object Receiver extends UseTarget("receiver")
    }
  }

  /** Annotation array literal: Java `{ a, b }`, Kotlin `[ a, b ]` */
  case class AnnotationArray(elements: List[Code]) extends Tree

  // we use this to abstract over calling static methods for java, and real string interplators for scala.
  // the nice thing is that it makes generating code a bit easier
  case class StringInterpolate(`import`: Type, prefix: Ident, content: Code) extends Tree
  case class RuntimeInterpolation(value: Code) extends Tree

  /** Used to trigger an import without generating any code */
  case class Import(`import`: Type.Qualified, isStatic: Boolean = false) extends Tree

  sealed trait StaticMember extends Tree

  sealed trait ClassMember extends StaticMember {
    def name: Ident
  }

  case class Summon(tpe: Type) extends Tree

  case class Given(
      annotations: List[Annotation],
      tparams: List[Type.Abstract],
      name: Ident,
      implicitParams: List[Param[Type]],
      tpe: Type,
      body: Code
  ) extends ClassMember

  object Given {
    def apply(tparams: List[Type.Abstract], name: Ident, implicitParams: List[Param[Type]], tpe: Type, body: Code): Given =
      Given(Nil, tparams, name, implicitParams, tpe, body)
  }

  case class Comments(lines: List[String])
  object Comments {
    val Empty = Comments(Nil)
  }

  sealed trait ClassType
  object ClassType {
    case object Class extends ClassType
    case object AbstractClass extends ClassType
    case object Interface extends ClassType
  }

  case class Enum(
      annotations: List[Annotation],
      comments: Comments,
      tpe: Type.Qualified,
      values: NonEmptyList[(Ident, jvm.Code)],
      staticMembers: List[ClassMember]
  ) extends Tree

  case class OpenEnum(
      annotations: List[Annotation],
      comments: Comments,
      tpe: Type.Qualified,
      underlyingType: jvm.Type.Qualified,
      values: NonEmptyList[(Ident, jvm.Code)],
      staticMembers: List[StaticMember]
  ) extends Tree

  case class Call(target: Code, argGroups: List[Call.ArgGroup]) extends Tree
  object Call {
    case class ArgGroup(args: List[Arg], isImplicit: Boolean)
    // Backward compatible constructor for args + implicitArgs pattern
    def withImplicits(target: Code, args: List[Arg], implicitArgs: List[Arg]): Call = {
      val groups = List(
        Some(ArgGroup(args, isImplicit = false)),
        if (implicitArgs.nonEmpty) Some(ArgGroup(implicitArgs, isImplicit = true)) else None
      ).flatten
      Call(target, groups)
    }
  }
  case class Select(target: Code, name: Ident) extends Tree
  case class ApplyNullary(target: Code, name: Ident) extends Tree
  case class ArrayIndex(target: Code, num: Int) extends Tree
  case class Apply0(function: Param[Type.Function0]) extends Tree
  case class Apply1(function: Param[Type.Function1], arg1: Code) extends Tree
  case class Apply2(function: Param[Type.Function2], arg1: Code, arg2: Code) extends Tree

  case class New(target: Code, args: List[Arg]) extends Tree

  /** Anonymous class instantiation with body members: Scala: `new Trait { members }`, Java: `new Trait() { members }`
    * @param extendsClass
    *   Optional class to extend (needs constructor call in Kotlin)
    * @param implementsInterface
    *   Optional interface to implement (no constructor in Kotlin)
    */
  case class NewWithBody(extendsClass: Option[Type], implementsInterface: Option[Type], members: List[ClassMember]) extends Tree

  /** Diamond operator in Java: `new Foo<>()`. Renders as empty in Scala. */
  case class InferredTargs(target: Code) extends Tree

  /** Generic method call with type arguments: Java: `.<T>method(args)`, Scala: `.method[T](args)` */
  case class GenericMethodCall(target: Code, methodName: Ident, typeArgs: List[Type], args: List[Arg]) extends Tree

  /** Return statement for explicit control flow in statement bodies */
  case class Return(expr: Code) extends Tree

  /** Throw statement for explicit exception throwing */
  case class Throw(expr: Code) extends Tree

  /** Body of a method or lambda - either a single expression, list of statements, or abstract (no body) */
  sealed trait Body
  object Body {

    /** No body - abstract method */
    case object Abstract extends Body

    /** Expression body - the expression's value is the return value */
    case class Expr(value: Code) extends Body

    /** Statement body - explicit statements with control flow (Return/Throw) */
    case class Stmts(stmts: List[Code]) extends Body

    /** Convert a list of code to Body - single element becomes Expr (unless it's a statement construct), multiple becomes Stmts */
    def apply(stmts: List[Code]): Body = stmts match {
      case List(single) =>
        // TryCatch and IfElseChain are statement constructs in Java/Kotlin - they must remain as Stmts
        // so they get rendered as block bodies, not expression bodies
        single match {
          case Code.Tree(_: TryCatch)    => Stmts(stmts)
          case Code.Tree(_: IfElseChain) => Stmts(stmts)
          case _                         => Expr(single)
        }
      case multiple => Stmts(multiple)
    }
  }

  /** Lambda parameter - may or may not have explicit type */
  case class LambdaParam(name: Ident, tpe: Option[Type])
  object LambdaParam {
    def apply(name: Ident): LambdaParam = LambdaParam(name, None)
    def apply(name: String): LambdaParam = LambdaParam(Ident(name), None)
    def typed(name: Ident, tpe: Type): LambdaParam = LambdaParam(name, Some(tpe))
    def typed(name: String, tpe: Type): LambdaParam = LambdaParam(Ident(name), Some(tpe))
  }

  /** Lambda expressions: () -> body, x -> body, (x, y) -> body, (Type x) -> body */
  case class Lambda(params: List[LambdaParam], body: Body) extends Tree
  object Lambda {
    def apply(body: Body): Lambda = Lambda(Nil, body)
    def apply(p1: String, body: Body): Lambda = Lambda(List(LambdaParam(p1)), body)
    def apply(p1: String, p2: String, body: Body): Lambda = Lambda(List(LambdaParam(p1), LambdaParam(p2)), body)
    def apply(p1: Ident, body: Body): Lambda = Lambda(List(LambdaParam(p1)), body)
    def apply(p1: Ident, p2: Ident, body: Body): Lambda = Lambda(List(LambdaParam(p1), LambdaParam(p2)), body)
    def apply(p1: Ident, expr: Code): Lambda = Lambda(List(LambdaParam(p1)), Body.Expr(expr))
    def apply(p1: Ident, p2: Ident, expr: Code): Lambda = Lambda(List(LambdaParam(p1), LambdaParam(p2)), Body.Expr(expr))
    def apply(p1: String, expr: Code): Lambda = Lambda(List(LambdaParam(p1)), Body.Expr(expr))
    def apply(p1: String, p2: String, expr: Code): Lambda = Lambda(List(LambdaParam(p1), LambdaParam(p2)), Body.Expr(expr))
  }

  /** SAM-typed lambda for explicit SAM conversion. Needed in Kotlin when SAM type can't be inferred. Java: just renders as lambda (automatic SAM conversion) Kotlin: `TypeName { params -> body }`
    * (explicit SAM conversion) Scala: just renders as lambda (automatic SAM conversion)
    */
  case class SamLambda(samType: Type, lambda: Lambda) extends Tree

  /** Type cast expression. Java: `(Type) expr` Kotlin: `expr as Type` Scala: `expr.asInstanceOf[Type]`
    */
  case class Cast(targetType: Type, expr: Code) extends Tree

  /** By-name argument for method calls. Scala: `body` (by-name parameter), Java: `() -> body` (Supplier/Runnable) */
  case class ByName(body: Body) extends Tree

  /** Field getter as method reference. Java: RowType::field, Scala: _.field */
  case class FieldGetterRef(rowType: Type, field: Ident) extends Tree

  /** Call a nullary method on implicit this/self (no explicit receiver). Scala: `name`, Java: `name()` */
  case class SelfNullary(name: Ident) extends Tree

  /** Factory construction with type parameters. Scala: `Type[T1, T2](args)` (apply/new pattern) Java: `Type.<T1, T2>of(args)` (static factory method)
    */
  case class TypedFactoryCall(tpe: Type, typeArgs: List[Type], args: List[Arg]) extends Tree

  // : Unit in scala to tell compiler we diregard result
  case class IgnoreResult(expr: Code) extends Tree

  sealed trait Arg extends Tree {
    def value: Code
  }
  object Arg {
    case class Pos(value: Code) extends Arg
    case class Named(name: Ident, value: Code) extends Arg
  }

  case class TypeSwitch(value: Code, cases: List[TypeSwitch.Case], nullCase: Option[Code] = None, defaultCase: Option[Code] = None, unchecked: Boolean = false) extends Tree
  object TypeSwitch {
    case class Case(tpe: Type, ident: Ident, body: Code)
  }

  /** Try-catch block */
  case class TryCatch(tryBlock: List[Code], catches: List[TryCatch.Catch], finallyBlock: List[Code]) extends Tree
  object TryCatch {
    case class Catch(exceptionType: Type.Qualified, ident: Ident, body: List[Code])
  }

  /** If-else chain */
  case class IfElseChain(cases: List[(Code, Code)], elseCase: Code) extends Tree

  /** Statement wrapper - indicates whether semicolon is needed in Java/Kotlin. Compound statements (if/try/while) don't need semicolons, simple statements do.
    */
  case class Stmt(code: Code, needsSemicolon: Boolean) extends Tree
  object Stmt {
    def simple(code: Code): Stmt = Stmt(code, needsSemicolon = true)
    def compound(code: Code): Stmt = Stmt(code, needsSemicolon = false)
  }

  sealed trait Adt extends Tree {
    def name: Type.Qualified
  }
  object Adt {
    case class Sum(
        annotations: List[Annotation],
        comments: Comments,
        name: Type.Qualified,
        tparams: List[Type.Abstract],
        members: List[Method],
        implements: List[Type],
        subtypes: List[Adt],
        staticMembers: List[ClassMember],
        /** For Java sealed interfaces: explicitly permitted subtypes (empty means nested subtypes are used) */
        permittedSubtypes: List[Type.Qualified] = Nil
    ) extends Adt {
      def flattenedSubtypes: List[Adt] = {
        val b = List.newBuilder[Adt]
        def go(subtype: Adt): Unit = subtype match {
          case x: Adt.Sum =>
            b += x.copy(subtypes = Nil)
            x.subtypes.foreach(go)
          case x: Adt.Record =>
            b += x
        }
        subtypes.foreach(go)
        b.result()
      }
    }

    case class Record(
        annotations: List[Annotation],
        constructorAnnotations: List[Annotation],
        isWrapper: Boolean,
        comments: Comments,
        name: Type.Qualified,
        tparams: List[Type.Abstract],
        params: List[Param[Type]],
        implicitParams: List[Param[Type]],
        `extends`: Option[Type],
        implements: List[Type],
        members: List[ClassMember],
        staticMembers: List[ClassMember]
    ) extends Adt

  }
  case class Class(
      annotations: List[Annotation],
      comments: Comments,
      classType: ClassType,
      name: Type.Qualified,
      tparams: List[Type.Abstract],
      params: List[Param[Type]],
      implicitParams: List[Param[Type]],
      `extends`: Option[Type],
      implements: List[Type],
      members: List[ClassMember],
      staticMembers: List[ClassMember]
  ) extends StaticMember

  /** Nested class for inner class definitions. Scala: `private final class Name(params) extends Parent(args) { ... }` Java: `final class Name extends Parent { Name(params) { super(args); } ... }` */
  case class NestedClass(
      isPrivate: Boolean,
      isFinal: Boolean,
      name: Ident,
      params: List[Param[Type]],
      `extends`: Option[Type],
      superArgs: List[Arg],
      members: List[ClassMember]
  ) extends ClassMember

  /** Nested record for inner record definitions. Java: `record Name(params) implements Interface { ... }` */
  case class NestedRecord(
      isPrivate: Boolean,
      name: Ident,
      params: List[Param[Type]],
      implements: List[Type],
      members: List[ClassMember]
  ) extends ClassMember

  case class Value(
      annotations: List[Annotation],
      name: Ident,
      tpe: Type,
      body: Option[Code],
      isLazy: Boolean,
      isOverride: Boolean,
      isImplicit: Boolean = false
  ) extends ClassMember

  /** Local variable declaration - renders as `val` in Scala, `var` in Java */
  case class LocalVar(
      name: Ident,
      tpe: Option[Type],
      value: Code
  ) extends Tree

  case class Method(
      annotations: List[Annotation],
      comments: Comments,
      tparams: List[Type.Abstract],
      name: Ident,
      params: List[Param[Type]],
      implicitParams: List[Param[Type]],
      tpe: Type,
      throws: List[Type],
      body: Body,
      isOverride: Boolean,
      isDefault: Boolean
  ) extends ClassMember

  sealed trait Type extends Tree {
    def of(targs: Type*): Type = Type.TApply(this, targs.toList)
    def withComment(str: String): Type = Type.Commented(this, s"/* $str */")

    /** Strip optional type wrappers (KotlinNullable, Option, Optional, etc.) recursively. Keeps array structure but removes nullable wrappers from element types. We don't track nullability in the
      * type-system at the DSL level.
      */
    def stripNullable(lang: Lang): Type = {
      this match {
        case lang.Optional(underlying)              => underlying.stripNullable(lang)
        case Type.ArrayOf(underlying)               => Type.ArrayOf(underlying.stripNullable(lang))
        case Type.TApply(underlying, targs)         => Type.TApply(underlying, targs.map(_.stripNullable(lang)))
        case Type.Commented(underlying, comment)    => Type.Commented(underlying.stripNullable(lang), comment)
        case Type.Annotated(underlying, annotation) => Type.Annotated(underlying.stripNullable(lang), annotation)
        case Type.UserDefined(underlying)           => Type.UserDefined(underlying.stripNullable(lang))
        case Type.Function0(ret)                    => Type.Function0(ret.stripNullable(lang))
        case Type.Function1(tpe1, ret)              => Type.Function1(tpe1.stripNullable(lang), ret.stripNullable(lang))
        case Type.Function2(tpe1, tpe2, ret)        => Type.Function2(tpe1.stripNullable(lang), tpe2.stripNullable(lang), ret.stripNullable(lang))
        case other                                  => other
      }
    }
  }

  object Type {
    case class Function0(ret: Type) extends Type
    case class Function1(tpe1: Type, ret: Type) extends Type
    case class Function2(tpe1: Type, tpe2: Type, ret: Type) extends Type

    case object Wildcard extends Type
    case object Void extends Type
    case class TApply(underlying: Type, targs: List[Type]) extends Type
    case class Qualified(value: QIdent) extends Type {
      def /(ident: Ident): Qualified = Qualified(value / ident)
      lazy val dotName = value.dotName
      def name = value.name
    }
    case class Abstract(value: Ident, variance: Variance = Variance.Invariant) extends Type
    case class Commented(underlying: Type, comment: String) extends Type

    /** Type with annotation in type position: `T @annotation` (Scala) */
    case class Annotated(underlying: Type, annotation: Type.Qualified) extends Type
    case class UserDefined(underlying: Type) extends Type
    case class ArrayOf(underlying: Type) extends Type

    /** Kotlin nullable type: `T?` - for representing Kotlin's native nullable types */
    case class KotlinNullable(underlying: Type) extends Type

    /** Primitive type - rendered as-is without identifier escaping (for Java primitive types like byte, int, etc.) */
    case class Primitive(name: String) extends Type

    object Qualified {
      implicit val ordering: Ordering[Qualified] = scala.Ordering.by(x => x.dotName)

      def apply(str: String): Qualified =
        Qualified(QIdent(str))
      def apply(value: Ident): Qualified =
        Qualified(QIdent(scala.List(value)))
    }

    // todo: represent this fact better.
    def containsUserDefined(tpe: Type): Boolean =
      tpe match {
        case Abstract(_, _)             => false
        case ArrayOf(targ)              => containsUserDefined(targ)
        case KotlinNullable(underlying) => containsUserDefined(underlying)
        case Commented(underlying, _)   => containsUserDefined(underlying)
        case Annotated(underlying, _)   => containsUserDefined(underlying)
        case Qualified(_)               => false
        case TApply(underlying, targs)  => containsUserDefined(underlying) || targs.exists(containsUserDefined)
        case UserDefined(_)             => true
        case Void                       => false
        case Wildcard                   => false
        case Function0(_)               => false
        case Function1(_, _)            => false
        case Function2(_, _, _)         => false
        case Primitive(_)               => false
      }

    def base(tpe: Type): Type = tpe match {
      case Abstract(_, _)             => tpe
      case ArrayOf(targ)              => Type.ArrayOf(base(targ))
      case KotlinNullable(underlying) => KotlinNullable(base(underlying))
      case Commented(underlying, _)   => base(underlying)
      case Annotated(underlying, _)   => base(underlying)
      case Qualified(_)               => tpe
      case TApply(underlying, targs)  => TApply(base(underlying), targs.map(base))
      case UserDefined(tpe)           => base(tpe)
      case Void                       => Void
      case Wildcard                   => tpe
      case tpe @ Function0(_)         => tpe
      case tpe @ Function1(_, _)      => tpe
      case tpe @ Function2(_, _, _)   => tpe
      case tpe @ Primitive(_)         => tpe
    }
  }

  case class File(tpe: Type.Qualified, contents: Code, secondaryTypes: List[Type.Qualified], scope: Scope, additionalImports: List[String] = Nil) {
    val name: Ident = tpe.value.name
    val pkg = QIdent(tpe.value.idents.dropRight(1))
  }

  /** Semi-structured generated code. We keep all `Tree`s as long as possible so we can write imports based on what is used
    */
  sealed trait Code {
//    override def toString: String = sys.error("stringifying code too early loses structure")

    /** Strip margin from string parts and wrap args in Code.Indented for smart indentation */
    def stripMargin: Code =
      this match {
        case Code.Combined(codes)           => Code.Combined(codes.map(_.stripMarginInternal))
        case Code.Str(value)                => Code.Str(value.stripMargin)
        case tree @ Code.Tree(_)            => tree
        case Code.Interpolated(parts, args) =>
          // Strip margin from string parts and wrap args in Indented so they get smart-indented during render
          // Use stripMarginInternal for args to strip | without adding more Indented wrappers
          Code.Interpolated(parts.map(_.stripMargin), args.map(arg => Code.Indented(arg.stripMarginInternal)))
        case Code.Indented(content) => Code.Indented(content.stripMarginInternal)
      }

    /** Internal: strip | characters from Code.Str values without wrapping in Indented */
    private def stripMarginInternal: Code =
      this match {
        case Code.Combined(codes)           => Code.Combined(codes.map(_.stripMarginInternal))
        case Code.Str(value)                => Code.Str(value.stripMargin)
        case tree @ Code.Tree(_)            => tree
        case Code.Interpolated(parts, args) => Code.Interpolated(parts.map(_.stripMargin), args.map(_.stripMarginInternal))
        case Code.Indented(content)         => Code.Indented(content.stripMarginInternal)
      }

    /** Render code as a string */
    def render(language: Lang): Lines = renderWithColumn(language, 0)

    /** Render code tracking column position for smart indentation */
    private def renderWithColumn(language: Lang, startCol: Int): Lines =
      this match {
        case Code.Interpolated(parts, args) =>
          val sb = new StringBuilder()
          var col = startCol
          parts.iterator.zipWithIndex.foreach { case (str, n) =>
            if (n > 0) {
              val arg = args(n - 1)
              val argStr = arg match {
                case Code.Indented(content) =>
                  // Apply smart indentation based on current column position
                  val rendered = content.renderWithColumn(language, col).asString
                  Code.smartIndent(rendered, col)
                case other =>
                  other.renderWithColumn(language, col).asString
              }
              sb.append(argStr)
              // Update column position after arg
              val idx = argStr.lastIndexOf('\n')
              if (idx >= 0) col = argStr.length - idx - 1
              else col += argStr.length
            }
            val processed = StringContext.processEscapes(str)
            sb.append(processed)
            // Update column position after string part
            val idx = processed.lastIndexOf('\n')
            if (idx >= 0) col = processed.length - idx - 1
            else col += processed.length
          }
          Lines(sb.result())
        case Code.Combined(codes) =>
          var col = startCol
          val parts = codes.map { c =>
            val rendered = c.renderWithColumn(language, col).asString
            val idx = rendered.lastIndexOf('\n')
            if (idx >= 0) col = rendered.length - idx - 1
            else col += rendered.length
            rendered
          }
          Lines(parts.mkString)
        case Code.Str(str)          => Lines(str)
        case Code.Tree(tree)        => language.renderTree(tree, language.Ctx.Empty).renderWithColumn(language, startCol)
        case Code.Indented(content) =>
          // When Indented is encountered outside of Interpolated context, just render content
          content.renderWithColumn(language, startCol)
      }

    def mapTrees(f: Tree => Tree): Code =
      this match {
        case Code.Interpolated(parts, args) => Code.Interpolated(parts, args.map(_.mapTrees(f)))
        case Code.Combined(codes)           => Code.Combined(codes.map(_.mapTrees(f)))
        case str @ Code.Str(_)              => str
        case Code.Tree(tree)                => Code.Tree(f(tree))
        case Code.Indented(content)         => Code.Indented(content.mapTrees(f))
      }

    def ++(other: Code): Code = Code.Combined(List(this, other))
  }

  case class Lines(lines: Array[String]) {
    def ++(other: Lines): Lines =
      if (this == Lines.Empty) other
      else if (other == Lines.Empty) this
      else if (lines.last.endsWith("\n")) new Lines(lines ++ other.lines)
      else {
        val newArray = lines.init.iterator ++ Iterator(lines.last + other.lines.head) ++ other.lines.iterator.drop(1)
        new Lines(newArray.toArray)
      }

    def asString: String = lines.mkString
    override def toString: String = asString
  }

  object Lines {
    val Empty = new Lines(Array())
    def apply(str: String): Lines = if (str.isEmpty) Empty else new Lines(str.linesWithSeparators.toArray)
  }

  object Code {
    val Empty: Code = Str("")

    implicit class CodeOps(private val self: Code) extends AnyVal {

      /** Nullary method call: renders as `self.field` in Scala, `self.field()` in Java (for record accessors) */
      def callNullary(field: String): Code = Tree(ApplyNullary(self, Ident(field)))
      def callNullary(field: Ident): Code = Tree(ApplyNullary(self, field))
      def select(field: String): Code = Tree(Select(self, Ident(field)))

      /** Method call with arguments */
      def invoke(method: String, args: Code*): Code = Tree(Call(Tree(Select(self, Ident(method))), List(Call.ArgGroup(args.map(Arg.Pos.apply).toList, isImplicit = false))))
      def arrayIndex(num: Int): Code = Tree(ArrayIndex(self, num))
    }

    implicit class TypeOps(private val self: Type) extends AnyVal {

      /** Construct a new instance of this type with the given arguments */
      def construct(args: Code*): Code = Tree(New(Tree(self), args.map(Arg.Pos.apply).toList))
    }

    implicit class TreeOps(private val self: jvm.Tree) extends AnyVal {

      /** Convert a Tree to Code by wrapping in Code.Tree */
      def code: Code = Tree(self)
    }
    case class Interpolated(parts: Seq[String], args: Seq[Code]) extends Code
    case class Combined(codes: List[Code]) extends Code
    case class Str(value: String) extends Code
    case class Tree(value: jvm.Tree) extends Code

    /** Marks content for smart indentation - subsequent lines will be indented to match the column where this appears */
    case class Indented(content: Code) extends Code

    /** Apply smart indentation: add `indent` spaces to all lines after the first */
    def smartIndent(str: String, indent: Int): String = {
      if (indent <= 0 || !str.contains('\n')) str
      else {
        val indentStr = " " * indent
        val lines = str.linesWithSeparators.toArray
        if (lines.length <= 1) str
        else {
          val sb = new StringBuilder(str.length + indent * lines.length)
          sb.append(lines(0))
          var i = 1
          while (i < lines.length) {
            val line = lines(i)
            // Don't indent empty lines (lines that are just newline)
            if (line.trim.isEmpty) { val _ = sb.append(line) }
            else { val _ = sb.append(indentStr).append(line) }
            i += 1
          }
          sb.result()
        }
      }
    }

    /** Linearize a Code structure into alternating Combined and Tree(RuntimeInterpolation) nodes. First flattens all Combined and Interpolated nodes, then groups regular fragments between
      * RuntimeInterpolation nodes into Combined nodes.
      */
    def linearize(code: Code): List[Code] = {
      // Step 1: Flatten to get all fragments
      def flatten(c: Code): List[Code] = c match {
        case Combined(codes)           => codes.flatMap(flatten)
        case Indented(content)         => flatten(content) // Unwrap Indented to find RuntimeInterpolations
        case Interpolated(parts, args) =>
          // Pattern: part[0], arg[0], part[1], arg[1], ..., part[n]
          val fragments = List.newBuilder[Code]
          parts.zipWithIndex.foreach { case (part, i) =>
            if (part.nonEmpty) fragments += Str(part)
            if (i < args.length) {
              fragments ++= flatten(args(i))
            }
          }
          fragments.result()
        case other => List(other)
      }

      // Step 2: Group fragments, alternating between Combined and RuntimeInterpolation
      val allFragments = flatten(code)
      val result = List.newBuilder[Code]
      val currentGroup = List.newBuilder[Code]

      allFragments.foreach {
        case tree @ Tree(_: RuntimeInterpolation) =>
          // When we hit a RuntimeInterpolation, combine accumulated fragments
          val accumulated = currentGroup.result()
          if (accumulated.nonEmpty) {
            result += Combined(accumulated)
            currentGroup.clear()
          }
          result += tree
        case fragment =>
          currentGroup += fragment
      }

      // Don't forget the last group
      val finalGroup = currentGroup.result()
      if (finalGroup.nonEmpty) {
        result += Combined(finalGroup)
      }

      result.result()
    }
  }
}
