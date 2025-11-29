package typo

import scala.reflect.ClassTag

/** Simplified model of JVM languages.
  *
  * The generated code is stored in the `Code` data structure. For full flexibility, some parts are stored as text and other parts in trees. Most notably *all type and term references* which need an
  * import to work should be in a tree.
  *
  * You'll mainly use this module with the `code"..."` interpolator.
  */
object jvm {
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
  case class IfExpr(cond: Code, thenp: Code, elsep: Code) extends Tree

  case class MethodRef(tpe: Type, name: Ident) extends Tree
  case class ConstructorMethodRef(tpe: Type) extends Tree
  case class ClassOf(tpe: Type) extends Tree

  case class StrLit(str: String) extends Tree

  case class Annotation(
      tpe: Type.Qualified,
      args: List[Annotation.Arg] = Nil
  ) extends Tree

  object Annotation {
    sealed trait Arg
    object Arg {
      case class Named(name: Ident, value: Code) extends Arg
      case class Positional(value: Code) extends Arg
    }
  }

  // we use this to abstract over calling static methods for java, and real string interplators for scala.
  // the nice thing is that it makes generating code a bit easier
  case class StringInterpolate(`import`: Type, prefix: Ident, content: Code) extends Tree
  case class RuntimeInterpolation(value: Code) extends Tree

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

  /** Anonymous class instantiation with body members: Scala: `new Trait { members }`, Java: `new Trait() { members }` */
  case class NewWithBody(tpe: Type, members: List[ClassMember]) extends Tree

  /** Diamond operator in Java: `new Foo<>()`. Renders as empty in Scala. */
  case class InferredTargs(target: Code) extends Tree

  /** Generic method call with type arguments: Java: `.<T>method(args)`, Scala: `.method[T](args)` */
  case class GenericMethodCall(target: Code, methodName: Ident, typeArgs: List[Type], args: List[Arg]) extends Tree

  /** Lambda expressions: () -> body, x -> body, (x, y) -> body */
  case class Lambda0(body: Code) extends Tree
  case class Lambda1(param: Ident, body: Code) extends Tree
  case class Lambda2(param1: Ident, param2: Ident, body: Code) extends Tree

  /** By-name argument for method calls. Scala: `body` (by-name parameter), Java: `() -> body` (Supplier/Runnable) */
  case class ByName(body: Code) extends Tree

  /** Typed lambda: Java: (Type param) -> body, Scala: (param: Type) => body */
  case class TypedLambda1(paramType: Type, param: Ident, body: Code) extends Tree

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

  case class TypeSwitch(value: Code, cases: List[TypeSwitch.Case], nullCase: Option[Code] = None, defaultCase: Option[Code] = None) extends Tree
  object TypeSwitch {
    case class Case(tpe: Type, ident: Ident, body: Code)
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
        staticMembers: List[ClassMember]
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

  case class Value(
      annotations: List[Annotation],
      name: Ident,
      tpe: Type,
      body: Option[Code],
      isLazy: Boolean = false,
      isOverride: Boolean = false
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
      body: List[Code]
  ) extends ClassMember

  sealed trait Type extends Tree {
    def of(targs: Type*): Type = Type.TApply(this, targs.toList)
    def withComment(str: String): Type = Type.Commented(this, s"/* $str */")
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
    case class Abstract(value: Ident) extends Type
    case class Commented(underlying: Type, comment: String) extends Type
    case class UserDefined(underlying: Type) extends Type
    case class ArrayOf(underlying: Type) extends Type

    object Qualified {
      implicit val ordering: Ordering[Qualified] = scala.Ordering.by(x => x.dotName)

      def apply(str: String): Qualified =
        Qualified(QIdent(str))
      def apply(value: Ident): Qualified =
        Qualified(QIdent(scala.List(value)))
    }

    object dsl {
      val Bijection = Qualified("typo.dsl.Bijection")
      val CompositeIn = Qualified("typo.dsl.SqlExpr.CompositeIn")
      val CompositeInPart = Qualified("typo.dsl.SqlExpr.CompositeIn.Part") // Java
      val CompositeTuplePart = Qualified("typo.dsl.SqlExpr.CompositeIn.TuplePart") // Scala
      val ConstAs = Qualified("typo.dsl.SqlExpr.Const.As")
      val ConstAsAs = ConstAs / Ident("as")
      val ConstAsAsOpt = ConstAs / Ident("asOpt")
      val DeleteBuilder = Qualified("typo.dsl.DeleteBuilder")
      val DeleteBuilderMock = Qualified("typo.dsl.DeleteBuilder.DeleteBuilderMock")
      val DeleteParams = Qualified("typo.dsl.DeleteParams")
      val Field = Qualified("typo.dsl.SqlExpr.Field")
      val FieldLikeNoHkt = Qualified("typo.dsl.SqlExpr.FieldLike")
      val ForeignKey = Qualified("typo.dsl.ForeignKey")
      val IdField = Qualified("typo.dsl.SqlExpr.IdField")
      val OptField = Qualified("typo.dsl.SqlExpr.OptField")
      val Path = Qualified("typo.dsl.Path")
      val SelectBuilder = Qualified("typo.dsl.SelectBuilder")
      val SelectBuilderMock = Qualified("typo.dsl.SelectBuilderMock")
      val SelectParams = Qualified("typo.dsl.SelectParams")
      val SqlExpr = Qualified("typo.dsl.SqlExpr")
      val StructureRelation = Qualified("typo.dsl.Structure.Relation")
      val UpdateBuilder = Qualified("typo.dsl.UpdateBuilder")
      val UpdateBuilderMock = Qualified("typo.dsl.UpdateBuilder.UpdateBuilderMock")
      val UpdateParams = Qualified("typo.dsl.UpdateParams")
    }

    // todo: represent this fact better.
    def containsUserDefined(tpe: Type): Boolean =
      tpe match {
        case Abstract(_)               => false
        case ArrayOf(targ)             => containsUserDefined(targ)
        case Commented(underlying, _)  => containsUserDefined(underlying)
        case Qualified(_)              => false
        case TApply(underlying, targs) => containsUserDefined(underlying) || targs.exists(containsUserDefined)
        case UserDefined(_)            => true
        case Void                      => false
        case Wildcard                  => false
        case Function0(_)              => false
        case Function1(_, _)           => false
        case Function2(_, _, _)        => false
      }

    def base(tpe: Type): Type = tpe match {
      case Abstract(_)               => tpe
      case ArrayOf(targ)             => Type.ArrayOf(base(targ))
      case Commented(underlying, _)  => base(underlying)
      case Qualified(_)              => tpe
      case TApply(underlying, targs) => TApply(base(underlying), targs.map(base))
      case UserDefined(tpe)          => base(tpe)
      case Void                      => Void
      case Wildcard                  => tpe
      case tpe @ Function0(_)        => tpe
      case tpe @ Function1(_, _)     => tpe
      case tpe @ Function2(_, _, _)  => tpe
    }
  }

  case class File(tpe: Type.Qualified, contents: Code, secondaryTypes: List[Type.Qualified], scope: Scope) {
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
