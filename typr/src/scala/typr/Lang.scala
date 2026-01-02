package typr

import typr.jvm.Code.TreeOps

trait Lang {
  val typeSupport: TypeSupport
  val dsl: DslQualifiedNames
  val `;`: jvm.Code

  // Delegate type support to the provided TypeSupport
  final lazy val BigDecimal: jvm.Type.Qualified = typeSupport.BigDecimal
  final lazy val Boolean: jvm.Type.Qualified = typeSupport.Boolean
  final lazy val Byte: jvm.Type.Qualified = typeSupport.Byte
  final lazy val Double: jvm.Type.Qualified = typeSupport.Double
  final lazy val Float: jvm.Type.Qualified = typeSupport.Float
  final lazy val Int: jvm.Type.Qualified = typeSupport.Int
  final lazy val IteratorType: jvm.Type.Qualified = typeSupport.IteratorType
  final lazy val Long: jvm.Type.Qualified = typeSupport.Long
  final lazy val Short: jvm.Type.Qualified = typeSupport.Short
  final lazy val String: jvm.Type.Qualified = typeSupport.String
  final lazy val ByteArray: jvm.Type = typeSupport.ByteArray
  final lazy val FloatArray: jvm.Type = typeSupport.FloatArray
  final lazy val Optional: OptionalSupport = typeSupport.Optional
  final lazy val ListType: ListSupport = typeSupport.ListType
  final lazy val Random: RandomSupport = typeSupport.Random
  final lazy val MapOps: MapSupport = typeSupport.MapOps
  final def bigDecimalFromDouble(d: jvm.Code): jvm.Code = typeSupport.bigDecimalFromDouble(d)

  // Type system types - these are language-level concepts, not library concepts
  /** The "Nothing" type for this language - represents an impossible value in type parameters */
  val nothingType: jvm.Type

  /** The "Void" type for this language - represents no return value */
  val voidType: jvm.Type

  /** The "Any" / "Object" type for this language - the top type */
  val topType: jvm.Type

  // `s"..." interpolator - returns Code instead of StringInterpolate for Java/Kotlin
  def s(content: jvm.Code): jvm.Code

  def escapedIdent(value: String): String
  def docLink(cls: jvm.QIdent, value: jvm.Ident): String
  val BuiltIn: Map[jvm.Ident, jvm.Type.Qualified]
  def extension: String

  // Rendering context - tracks what modifiers are implied by the enclosing scope
  type Ctx
  val Ctx: CtxCompanion
  trait CtxCompanion {
    def Empty: Ctx
  }

  def renderTree(tree: jvm.Tree, ctx: Ctx): jvm.Code

  val isKeyword: Set[String]

  // Bijection creation: Scala: Bijection[A, B](getter)(constructor), Java: Bijection.of(getter, constructor)
  def bijection(wrapperType: jvm.Type, underlying: jvm.Type, getter: jvm.FieldGetterRef, constructor: jvm.ConstructorMethodRef): jvm.Code

  /** Generate a row setter expression: Scala: row.copy(field = value), Java: row.withField(value) */
  def rowSetter(fieldName: jvm.Ident): jvm.Code

  /** Iterate over array with a consumer lambda (for side effects) - handles Scala vs Java array iteration syntax */
  def arrayForEach(array: jvm.Code, elemVar: jvm.Ident, body: jvm.Body): jvm.Code

  /** Map over array elements. Scala: array.map(mapper), Java: arrayMap.map(array, mapper, targetClass) */
  def arrayMap(array: jvm.Code, mapper: jvm.Code, targetClass: jvm.Code): jvm.Code

  /** Ternary conditional expression: Java uses `cond ? thenExpr : elseExpr`, Scala uses `if (cond) thenExpr else elseExpr` */
  def ternary(condition: jvm.Code, thenExpr: jvm.Code, elseExpr: jvm.Code): jvm.Code

  /** Array creation: Java: `ClassName[]::new` or `new ClassName[length]`, Scala: `n => new Array[ClassName](n)` or `new Array[ClassName](length)` */
  def newArray(elementType: jvm.Type, length: Option[jvm.Code]): jvm.Code

  /** Byte array creation: Kotlin: `ByteArray(size)`, Scala: `Array.ofDim[Byte](size)`, Java: `new byte[size]` */
  def newByteArray(size: jvm.Code): jvm.Code

  /** The byte array type. Different in Kotlin (ByteArray) vs Scala/Java (Array[Byte]/byte[]).
    *   - Scala: Array[Byte]
    *   - Java: byte[]
    *   - Kotlin: ByteArray (a distinct primitive array type, not Array<Byte>)
    */
  val ByteArrayType: jvm.Type

  /** Get the max value constant for a numeric type.
    *   - Scala: Type.MaxValue (e.g., Byte.MaxValue)
    *   - Java: Type.MAX_VALUE (e.g., Byte.MAX_VALUE)
    *   - Kotlin: Type.MAX_VALUE
    */
  def maxValue(tpe: jvm.Type): jvm.Code

  /** Array fill: Kotlin: `Array(size) { factory }`, Scala: `Array.fill(size)(factory)`, Java: requires stream API */
  def arrayFill(size: jvm.Code, factory: jvm.Code, elementType: jvm.Type): jvm.Code

  /** Primitive ShortArray fill - Java/Scala use boxed array, Kotlin uses ShortArray */
  def shortArrayFill(size: jvm.Code, factory: jvm.Code): jvm.Code = arrayFill(size, factory, Short)

  /** Primitive FloatArray fill - Java/Scala use boxed array, Kotlin uses FloatArray */
  def floatArrayFill(size: jvm.Code, factory: jvm.Code): jvm.Code = arrayFill(size, factory, Float)

  /** Class reference for annotations */
  def annotationClassRef(tpe: jvm.Type): jvm.Code

  /** Property access on record/data class/case class: Java `()`, Kotlin/Scala no `()` */
  def propertyGetterAccess(target: jvm.Code, name: jvm.Ident): jvm.Code

  /** Access to lazy val (Scala) / fun (Kotlin/Java) in structures */
  def overrideValueAccess(target: jvm.Code, name: jvm.Ident): jvm.Code

  /** Zero-arg method call: all languages use `()` */
  def nullaryMethodCall(target: jvm.Code, name: jvm.Ident): jvm.Code

  /** Array literal */
  def arrayOf(elements: List[jvm.Code]): jvm.Code

  /** Typed array literal with specific element type: Java: `new String[]{"a", "b"}`, Kotlin: `arrayOf<String>("a", "b")`, Scala: `Array[String]("a", "b")` */
  def typedArrayOf(elementType: jvm.Type, elements: List[jvm.Code]): jvm.Code

  /** Create a string literal with proper escaping: "hello" */
  def stringLiteral(value: String): jvm.Code = jvm.StrLit(value).code

  /** Structural equality check */
  def equals(left: jvm.Code, right: jvm.Code): jvm.Code

  /** Structural inequality check */
  def notEquals(left: jvm.Code, right: jvm.Code): jvm.Code

  /** Convert Int to Short. Scala: `.toShort`, Kotlin: `.toShort()`, Java: `(short)` */
  def toShort(expr: jvm.Code): jvm.Code

  /** Convert Int to Byte. Scala: `.toByte`, Kotlin: `.toByte()`, Java: `(byte)` */
  def toByte(expr: jvm.Code): jvm.Code

  /** Convert Int to Long. Scala: `.toLong`, Kotlin: `.toLong()`, Java: `(long)` */
  def toLong(expr: jvm.Code): jvm.Code

  /** Get all enum values as a list. Scala: `Type.All`, Kotlin: `Type.entries`, Java: `Arrays.asList(Type.values())` */
  def enumAll(enumType: jvm.Type): jvm.Code

  /** Cast from Object to a specific type. Java: (Type) expr, Scala: expr.asInstanceOf[Type] */
  def castFromObject(targetType: jvm.Type, expr: jvm.Code): jvm.Code

  /** Convenience method for property access on generated data classes */
  final def prop(target: jvm.Code, field: jvm.Ident): jvm.Code = propertyGetterAccess(target, field)
  final def prop(target: jvm.Code, field: String): jvm.Code = propertyGetterAccess(target, jvm.Ident(field))
}

trait ListSupport {
  val tpe: jvm.Type
  def create(values: List[jvm.Code]): jvm.Code

  /** Get element at index. Scala: `list(idx)`, Kotlin: `list[idx]`, Java: `list.get(idx)` */
  def index(list: jvm.Code, idx: jvm.Code): jvm.Code

  /** Get list size. Scala: `list.length`, Kotlin: `list.size`, Java: `list.size()` */
  def size(list: jvm.Code): jvm.Code

  /** Find first element in collection matching predicate */
  def findFirst(collection: jvm.Code, predicate: jvm.Code): jvm.Code

  /** Stream and map to list */
  def map(collection: jvm.Code, mapper: jvm.Code): jvm.Code

  /** Map elements to strings and join with separator */
  def mapJoinString(collection: jvm.Code, mapper: jvm.Code, separator: String): jvm.Code

  /** Stream list and collect to map using key extractor */
  def collectToMap(collection: jvm.Code, keyExtractor: jvm.Code, keyType: jvm.Type, valueType: jvm.Type): jvm.Code

  /** Iterate over collection with a consumer lambda (for side effects) */
  def forEach(collection: jvm.Code, lambda: jvm.Code): jvm.Code

  /** Map over array elements and collect to list. Scala: array.map(mapper).toList, Java: Arrays.stream(array).map(mapper).toList() */
  def arrayMapToList(array: jvm.Code, mapper: jvm.Code): jvm.Code

  /** Map over list elements and collect to array. Scala: list.map(mapper).toArray, Java: list.stream().map(mapper).toArray(generator) */
  def listMapToArray(list: jvm.Code, mapper: jvm.Code, arrayGenerator: jvm.Code): jvm.Code

  /** Convert from java.util.List to language-native list type. Scala: javaList.asScala.toList, Java: javaList */
  def fromJavaList(javaList: jvm.Code, elementType: jvm.Type): jvm.Code

  /** Convert from language-native list type to java.util.List. Scala: scalaList.asJava, Java: javaList */
  def toJavaList(nativeList: jvm.Code, elementType: jvm.Type): jvm.Code
}

trait OptionalSupport {
  def tpe(underlying: jvm.Type): jvm.Type
  val none: jvm.Code

  def some(value: jvm.Code): jvm.Code

  /** Wrap a potentially-null value in the language's optional type. Java: Optional.ofNullable(x), Scala: Option(x), Kotlin: x (nullable type)
    */
  def ofNullable(value: jvm.Code): jvm.Code
  def map(opt: jvm.Code, f: jvm.Code): jvm.Code
  def filter(opt: jvm.Code, predicate: jvm.Code): jvm.Code
  def get(opt: jvm.Code): jvm.Code
  def getOrElse(opt: jvm.Code, default: jvm.Code): jvm.Code
  def isEmpty(opt: jvm.Code): jvm.Code
  def isDefined(opt: jvm.Code): jvm.Code

  /** fold: if defined use ifSome(get(opt)), else return ifNone. Uses IfExpr for language-agnostic rendering. */
  def fold(opt: jvm.Code, ifNone: jvm.Code, ifSome: jvm.Code => jvm.Code): jvm.Code = {
    jvm.IfExpr(isDefined(opt), ifSome(get(opt)), ifNone).code
  }

  /** filter then map then getOrElse - combines common pattern */
  def filterMapOrElse(opt: jvm.Code, predicate: jvm.Code, mapper: jvm.Code, default: jvm.Code): jvm.Code

  /** Convert optional to java.util.Optional - for Scala this converts, for Java/Kotlin it's a noop or wraps nullable */
  def toJavaOptional(opt: jvm.Code): jvm.Code

  def unapply(t: jvm.Type): Option[jvm.Type] =
    t match {
      case jvm.Type.Void                          => None
      case jvm.Type.ArrayOf(_)                    => None
      case jvm.Type.Wildcard                      => None
      case jvm.Type.KotlinNullable(underlying)    => Some(underlying) // Kotlin T?
      case jvm.Type.TApply(underlying, List(one)) =>
        // Check if this is an Optional/Option by seeing if wrapped type matches tpe(one)
        if (tpe(one) == t) Some(one) else unapply(underlying)
      case jvm.Type.TApply(underlying, _)    => unapply(underlying)
      case jvm.Type.Qualified(_)             => None
      case jvm.Type.Abstract(_, _)           => None
      case jvm.Type.Commented(underlying, _) => unapply(underlying)
      case jvm.Type.Annotated(underlying, _) => unapply(underlying)
      case jvm.Type.UserDefined(underlying)  => unapply(underlying)
      case jvm.Type.Function0(_)             => None
      case jvm.Type.Function1(_, _)          => None
      case jvm.Type.Function2(_, _, _)       => None
      case jvm.Type.Primitive(_)             => None
    }
}

trait RandomSupport {
  val tpe: jvm.Type

  def nextInt(random: jvm.Code): jvm.Code
  def nextIntBounded(random: jvm.Code, bound: jvm.Code): jvm.Code
  def nextLong(random: jvm.Code): jvm.Code
  def nextLongBounded(random: jvm.Code, bound: jvm.Code): jvm.Code
  def nextFloat(random: jvm.Code): jvm.Code
  def nextDouble(random: jvm.Code): jvm.Code
  def nextBoolean(random: jvm.Code): jvm.Code
  def nextBytes(random: jvm.Code, bytes: jvm.Code): jvm.Code
  def alphanumeric(random: jvm.Code, length: jvm.Code): jvm.Code
  def nextPrintableChar(random: jvm.Code): jvm.Code
  def randomUUID(random: jvm.Code): jvm.Code
}

trait MapSupport {

  /** The immutable map type (scala.collection.immutable.Map or java.util.Map) */
  val tpe: jvm.Type

  /** The mutable map implementation type (java.util.HashMap for both) */
  val mutableImpl: jvm.Type

  /** Create a new mutable map */
  def newMutableMap(keyType: jvm.Type, valueType: jvm.Type): jvm.Code

  /** Create a new immutable map (wrapping a value) */
  def newMap(keyType: jvm.Type, valueType: jvm.Type, value: jvm.Code): jvm.Code

  /** Put a key-value pair into a mutable map */
  def put(map: jvm.Code, key: jvm.Code, value: jvm.Code): jvm.Code

  /** Put a key-value pair into a mutable map, discarding the return value (for statement context) */
  def putVoid(map: jvm.Code, key: jvm.Code, value: jvm.Code): jvm.Code

  /** Convert map entries to string with separator (e.g., "k => v, k2 => v2") */
  def mkStringKV(map: jvm.Code, kvSep: String, entrySep: String): jvm.Code

  /** Iterate over map entries with a Lambda */
  def forEach(map: jvm.Code, lambda: jvm.Lambda): jvm.Code

  /** Cast a Map<?, ?> to the target map type */
  def castMap(expr: jvm.Code, keyType: jvm.Type, valueType: jvm.Type): jvm.Code

  /** Convert mutable map to immutable (noop in Java, builds scala Map in Scala) */
  def toImmutable(map: jvm.Code, keyType: jvm.Type, valueType: jvm.Type): jvm.Code

  /** Get value by key, returns Optional */
  def get(map: jvm.Code, key: jvm.Code): jvm.Code

  /** Remove key from map, returns Optional of removed value */
  def remove(map: jvm.Code, key: jvm.Code): jvm.Code

  /** Remove key from map, returns void (for use in Consumer lambdas in Java) */
  def removeVoid(map: jvm.Code, key: jvm.Code): jvm.Code

  /** Check if map contains key */
  def contains(map: jvm.Code, key: jvm.Code): jvm.Code

  /** Get all values as a List */
  def valuesToList(map: jvm.Code): jvm.Code
}
