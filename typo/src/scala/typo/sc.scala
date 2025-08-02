package typo

/** Simplified model of the scala language.
  *
  * The generated code is stored in the `Code` data structure. For full flexibility, some parts are stored as text and other parts in trees. Most notably *all type and term references* which need an
  * import to work should be in a tree.
  *
  * You'll mainly use this module with the `code"..."` interpolator.
  *
  * (It should rather be called `scala`, but let's avoid that name clash)
  */
object sc {
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
      sc.QIdent(str.split('.').toList.map(Ident.apply))
    def of(idents: Ident*): QIdent =
      QIdent(idents.toList)
  }
  case class Param(name: Ident, tpe: Type, default: Option[sc.Code]) extends Tree
  case class Params(params: List[Param]) extends Tree

  case class StrLit(str: String) extends Tree

  case class StringInterpolate(`import`: sc.Type, prefix: sc.Ident, content: sc.Code) extends Tree

  sealed trait ClassMember extends Tree {
    def name: sc.Ident
  }

  case class Summon(tpe: Type) extends Tree

  case class Given(
      tparams: List[Type.Abstract],
      name: sc.Ident,
      implicitParams: List[Param],
      tpe: sc.Type,
      body: sc.Code
  ) extends ClassMember

  case class Value(
      tparams: List[Type.Abstract],
      name: sc.Ident,
      params: List[Param],
      implicitParams: List[Param],
      tpe: sc.Type,
      body: sc.Code
  ) extends ClassMember

  case class Obj(
      name: sc.QIdent,
      members: List[ClassMember],
      body: Option[sc.Code]
  ) extends Tree

  sealed trait Type extends Tree {
    def of(targs: Type*): Type = Type.TApply(this, targs.toList)
    def withComment(str: String): Type = Type.Commented(this, s"/* $str */")
  }

  object Type {
    case object Wildcard extends Type
    case class TApply(underlying: Type, targs: List[Type]) extends Type
    case class Qualified(value: QIdent) extends Type {
      lazy val dotName = value.dotName
      def name = value.name
    }
    case class Abstract(value: Ident) extends Type
    case class Commented(underlying: Type, comment: String) extends Type
    case class UserDefined(underlying: Type) extends Type
    case class ByName(underlying: Type) extends Type
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
      val CompositeTuplePart = Qualified("typo.dsl.SqlExpr.CompositeIn.TuplePart")
      val DeleteBuilder = Qualified("typo.dsl.DeleteBuilder")
      val DeleteBuilderMock = Qualified("typo.dsl.DeleteBuilder.DeleteBuilderMock")
      val DeleteParams = Qualified("typo.dsl.DeleteParams")
      val Field = Qualified("typo.dsl.SqlExpr.Field")
      val FieldLikeNoHkt = Qualified("typo.dsl.SqlExpr.FieldLikeNoHkt")
      val ForeignKey = Qualified("typo.dsl.ForeignKey")
      val IdField = Qualified("typo.dsl.SqlExpr.IdField")
      val OptField = Qualified("typo.dsl.SqlExpr.OptField")
      val Path = Qualified("typo.dsl.Path")
      val Required = Qualified("typo.dsl.Required")
      val SelectBuilder = Qualified("typo.dsl.SelectBuilder")
      val SelectBuilderMock = Qualified("typo.dsl.SelectBuilderMock")
      val SelectBuilderSql = Qualified("typo.dsl.SelectBuilderSql")
      val SelectParams = Qualified("typo.dsl.SelectParams")
      val SqlExpr = Qualified("typo.dsl.SqlExpr")
      val StructureRelation = Qualified("typo.dsl.Structure.Relation")
      val UpdatedValue = Qualified("typo.dsl.UpdatedValue")
      val UpdateBuilder = Qualified("typo.dsl.UpdateBuilder")
      val UpdateBuilderMock = Qualified("typo.dsl.UpdateBuilder.UpdateBuilderMock")
      val UpdateParams = Qualified("typo.dsl.UpdateParams")
    }

    // don't generate imports for these
    val BuiltIn: Map[Ident, Type.Qualified] =
      Set(
        TypesScala.Any,
        TypesScala.AnyRef,
        TypesScala.AnyVal,
        TypesScala.Array,
        TypesScala.BigDecimal,
        TypesScala.Boolean,
        TypesScala.Byte,
        TypesScala.Double,
        TypesScala.Either,
        TypesScala.Float,
        TypesScala.Function1,
        TypesScala.Int,
        TypesScala.Iterable,
        TypesScala.Iterator,
        TypesJava.Character,
        TypesJava.Integer,
        TypesScala.Left,
        TypesScala.List,
        TypesScala.Long,
        TypesScala.Map,
        TypesScala.None,
        TypesScala.Nil,
        TypesScala.Numeric,
        TypesScala.Option,
        TypesScala.Ordering,
        TypesScala.Right,
        TypesScala.Short,
        TypesScala.Some,
        TypesJava.String,
        TypesJava.StringBuilder,
        TypesScala.StringContext,
        TypesScala.Unit,
        TypesJava.Throwable
      )
        .map(x => (x.value.name, x))
        .toMap

    def containsUserDefined(tpe: sc.Type): Boolean = tpe match {
      case ArrayOf(targ)             => containsUserDefined(targ)
      case Wildcard                  => false
      case TApply(underlying, targs) => containsUserDefined(underlying) || targs.exists(containsUserDefined)
      case Qualified(_)              => false
      case Abstract(_)               => false
      case Commented(underlying, _)  => containsUserDefined(underlying)
      case ByName(underlying)        => containsUserDefined(underlying)
      case UserDefined(_)            => true
    }

    def base(tpe: sc.Type): sc.Type = tpe match {
      case Wildcard                  => tpe
      case TApply(underlying, targs) => TApply(base(underlying), targs.map(base))
      case ArrayOf(targ)             => sc.Type.ArrayOf(base(targ))
      case Qualified(_)              => tpe
      case Abstract(_)               => tpe
      case Commented(underlying, _)  => base(underlying)
      case ByName(underlying)        => base(underlying)
      case UserDefined(tpe)          => base(tpe)
    }
  }

  val isScalaKeyword: Set[String] =
    Set(
      "abstract",
      "case",
      "class",
      "catch",
      "def",
      "do",
      "else",
      "enum",
      "extends",
      "export",
      "extension",
      "false",
      "final",
      "finally",
      "for",
      "forSome",
      "given",
      "if",
      "implicit",
      "import",
      "inline",
      "lazy",
      "macro",
      "match",
      "new",
      "null",
      "object",
      "override",
      "package",
      "private",
      "protected",
      "return",
      "sealed",
      "super",
      "then",
      "this",
      "throw",
      "trait",
      "true",
      "try",
      "type",
      "using",
      "val",
      "var",
      "with",
      "while",
      "yield",
      ".",
      "_",
      ":",
      "=",
      "=>",
      "<-",
      "<:",
      "<%",
      ">:",
      "#",
      "@"
    )

  val Quote = '"'.toString
  val TripleQuote = Quote * 3

  def renderGiven(givenInstance: Given, dialect: Dialect): String = {
    val Given(tparams, name, implicitParams, tpe, body) = givenInstance
    val renderedName = renderTree(name, dialect)
    val renderedTpe = renderTree(tpe, dialect)
    val renderedBody = body.render(dialect)

    if (tparams.isEmpty && implicitParams.isEmpty) {
      if (dialect == Dialect.Scala2XSource3)
        s"${dialect.valDefinition} $renderedName: $renderedTpe = $renderedBody"
      else
        s"${dialect.defDefinition} $renderedName: $renderedTpe = $renderedBody"
    } else {
      val renderedTparams = if (tparams.isEmpty) "" else tparams.map(t => renderTree(t, dialect)).mkString("[", ", ", "]")
      val renderedImplicitParams =
        if (implicitParams.isEmpty) ""
        else {
          implicitParams.map(t => renderTree(t, dialect)).mkString(s"(${dialect.implicitParamsPrefix}", ", ", ")")
        }
      s"${dialect.defDefinition} $renderedName$renderedTparams$renderedImplicitParams: $renderedTpe = $renderedBody"
    }
  }

  def renderTree(tree: Tree, dialect: Dialect): String =
    tree match {
      case Ident(value) =>
        def isValidId(str: String) = str.head.isUnicodeIdentifierStart && str.drop(1).forall(_.isUnicodeIdentifierPart)

        def escape(str: String) = s"`$str`"

        if (isScalaKeyword(value) || !isValidId(value)) escape(value) else value
      case QIdent(value)                      => value.map(t => renderTree(t, dialect)).mkString(".")
      case Param(name, tpe, Some(default))    => renderTree(name, dialect) + ": " + renderTree(tpe, dialect) + " = " + default.render(dialect)
      case Param(name, tpe, None)             => renderTree(name, dialect) + ": " + renderTree(tpe, dialect)
      case Params(params)                     => params.map(t => renderTree(t, dialect)).mkString("(", ", ", ")")
      case StrLit(str) if str.contains(Quote) => TripleQuote + str + TripleQuote
      case StrLit(str)                        => Quote + str + Quote
      case Summon(tpe) =>
        dialect match {
          case Dialect.Scala2XSource3 => s"implicitly[${renderTree(tpe, dialect)}]"
          case Dialect.Scala3         => s"summon[${renderTree(tpe, dialect)}]"
        }
      case tpe: Type =>
        tpe match {
          case Type.ArrayOf(value)                 => s"Array[${renderTree(value, dialect)}]"
          case Type.Abstract(value)                => renderTree(value, dialect)
          case Type.Wildcard                       => "?"
          case Type.TApply(underlying, targs)      => renderTree(underlying, dialect) + targs.map(t => renderTree(t, dialect)).mkString("[", ", ", "]")
          case Type.Qualified(value)               => renderTree(value, dialect)
          case Type.Commented(underlying, comment) => s"$comment ${renderTree(underlying, dialect)}"
          case Type.ByName(underlying)             => s"=> ${renderTree(underlying, dialect)}"
          case Type.UserDefined(underlying)        => s"/* user-picked */ ${renderTree(underlying, dialect)}"
        }
      case StringInterpolate(_, prefix, content) =>
        content.render(dialect).lines match {
          case Array(one) if one.contains(Quote) =>
            s"${renderTree(prefix, dialect)}$TripleQuote$one$TripleQuote"
          case Array(one) =>
            s"${renderTree(prefix, dialect)}$Quote$one$Quote"
          case more =>
            val renderedPrefix = renderTree(prefix, dialect)
            val pre = s"$renderedPrefix$TripleQuote"
            val ret = more.iterator.zipWithIndex.map {
              case (line, n) if n == 0 => pre + line
              // if line is last
              case (line, n) if n == more.length - 1 =>
                // if its empty align triple quotes with previous
                if (line.isEmpty) s"${" " * renderedPrefix.length}$TripleQuote"
                // or just align like the rest and put it at the end
                else s"${" " * pre.length}$line$TripleQuote"
              case (line, _) => s"${" " * pre.length}$line"
            }.mkString
            ret
        }
      case g: Given => renderGiven(g, dialect)
      case Value(tparams, name, params, implicitParams, tpe, body) =>
        val renderedName = renderTree(name, dialect)
        val renderedTpe = renderTree(tpe, dialect)
        val renderedBody = body.render(dialect)

        if (tparams.isEmpty && params.isEmpty && implicitParams.isEmpty)
          s"val $renderedName: $renderedTpe = $renderedBody"
        else {
          val renderedTparams = if (tparams.isEmpty) "" else tparams.map(t => renderTree(t, dialect)).mkString("[", ", ", "]")
          val init = s"def $renderedName$renderedTparams"
          val renderedParams =
            params match {
              case Nil            => ""
              case List(one)      => s"(${renderTree(one, dialect)})"
              case List(one, two) => s"(${renderTree(one, dialect)}, ${renderTree(two, dialect)})"
              case more =>
                val indent = " " * (init.length + 1)
                more.zipWithIndex.map { case (p, idx) => (if (idx == 0) "" else indent) + renderTree(p, dialect) }.mkString("(", ",\n", s"\n${" " * init.length})")
            }
          val renderedImplicitParams = if (implicitParams.isEmpty) "" else implicitParams.map(t => renderTree(t, dialect)).mkString("(implicit ", ", ", ")")
          s"def $renderedName$renderedTparams$renderedParams$renderedImplicitParams: $renderedTpe = $renderedBody"
        }
      case Obj(name, members, body) =>
        if (members.isEmpty && body.isEmpty) ""
        else {
          val codeMembers: List[String] =
            body.map(_.render(dialect).asString).toList ++ members.sortBy(_.name).map(t => renderTree(t, dialect))

          s"""|object ${name.name.value} {
              |${codeMembers.flatMap(_.linesIterator).map("  " + _).mkString("\n")}
              |}""".stripMargin
        }
    }

  case class File(tpe: Type.Qualified, contents: Code, secondaryTypes: List[Type.Qualified], scope: Scope) {
    val name: Ident = tpe.value.name
    val pkg = QIdent(tpe.value.idents.dropRight(1))
  }

  /** Semi-structured generated code. We keep all `Tree`s as long as possible so we can write imports based on what is used
    */
  sealed trait Code {
    override def toString: String = sys.error("stringifying code too early loses structure")

    def stripMargin: Code =
      this match {
        case Code.Combined(codes) => Code.Combined(codes.map(_.stripMargin))
        case Code.Str(value)      => Code.Str(value.stripMargin)
        case tree @ Code.Tree(_)  => tree
        case Code.Interpolated(parts, args) =>
          Code.Interpolated(parts.map(_.stripMargin), args.map(_.stripMargin))
      }

    // render tree as a string in such a way that newlines inside interpolated strings preserves outer indentation
    def render(dialect: Dialect): Lines =
      this match {
        case Code.Interpolated(parts, args) =>
          val lines = Array.newBuilder[String]
          val currentLine = new StringBuilder()

          def consume(str: Lines, indent: Int): Unit =
            // @unchecked because scala 2 and 3 disagree on exhaustivity
            (str.lines: @unchecked) match {
              case Array() => ()
              case Array(one) if one.endsWith("\n") =>
                currentLine.append(one)
                lines += currentLine.result()
                currentLine.clear()
              case Array(one) =>
                currentLine.append(one)
                ()
              case Array(first, rest*) =>
                currentLine.append(first)
                lines += currentLine.result()
                currentLine.clear()
                val indentedRest = rest.map(str => (" " * indent) + str)
                if (indentedRest.lastOption.exists(_.endsWith("\n"))) {
                  lines ++= indentedRest
                } else {
                  lines ++= indentedRest.init
                  currentLine.append(indentedRest.last)
                  ()
                }
            }

          // do the string interpolation
          parts.iterator.zipWithIndex.foreach { case (str, n) =>
            if (n > 0) {
              val rendered = args(n - 1).render(dialect)
              // consider the current indentation level when interpolating in multiline strings
              consume(rendered, indent = currentLine.length)
            }
            val escaped = StringContext.processEscapes(str)
            consume(Lines(escaped), indent = 0)
          }
          // commit last line
          lines += currentLine.result()
          // recombine lines back into one string
          Lines(lines.result())
        case Code.Combined(codes) => codes.iterator.map(_.render(dialect)).reduceOption(_ ++ _).getOrElse(Lines.Empty)
        case Code.Str(str)        => Lines(str)
        case Code.Tree(tree)      => Lines(renderTree(tree, dialect))
      }

    def mapTrees(f: Tree => Tree): Code =
      this match {
        case Code.Interpolated(parts, args) => Code.Interpolated(parts, args.map(_.mapTrees(f)))
        case Code.Combined(codes)           => Code.Combined(codes.map(_.mapTrees(f)))
        case str @ Code.Str(_)              => str
        case Code.Tree(tree)                => Code.Tree(f(tree))
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
    case class Interpolated(parts: Seq[String], args: Seq[Code]) extends Code
    case class Combined(codes: List[Code]) extends Code
    case class Str(value: String) extends Code
    case class Tree(value: sc.Tree) extends Code
  }

  // `s"..." interpolator
  def s(content: sc.Code) =
    sc.StringInterpolate(TypesScala.StringContext, sc.Ident("s"), content)
}
