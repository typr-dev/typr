package typo

import typo.internal.codegen.*
import typo.jvm.Code.TreeOps

/** Type support configuration - determines which types to use for common patterns. This is separate from Lang (which determines rendering syntax) so they can be composed.
  *
  * For example:
  *   - Scala types: Option, scala.List, scala.Map, scala.Random
  *   - Java types: Optional, java.util.List, java.util.Map, java.util.Random
  */
trait TypeSupport {
  // Primitive type mappings
  val BigDecimal: jvm.Type
  val Boolean: jvm.Type
  val Byte: jvm.Type
  val Double: jvm.Type
  val Float: jvm.Type
  val Int: jvm.Type
  val IteratorType: jvm.Type
  val Long: jvm.Type
  val Short: jvm.Type
  val String: jvm.Type

  // Support traits for common patterns
  val Optional: OptionalSupport
  val ListType: ListSupport
  val Random: RandomSupport
  val MapOps: MapSupport

  /** Create a BigDecimal from a double value - Scala uses BigDecimal.decimal, Java uses BigDecimal.valueOf */
  def bigDecimalFromDouble(d: jvm.Code): jvm.Code
}

/** Type support using Scala's standard library types */
object TypeSupportScala extends TypeSupport {
  override val BigDecimal: jvm.Type = TypesScala.BigDecimal
  override val Boolean: jvm.Type = TypesScala.Boolean
  override val Byte: jvm.Type = TypesScala.Byte
  override val Double: jvm.Type = TypesScala.Double
  override val Float: jvm.Type = TypesScala.Float
  override val Int: jvm.Type = TypesScala.Int
  override val IteratorType: jvm.Type = TypesScala.Iterator
  override val Long: jvm.Type = TypesScala.Long
  override val Short: jvm.Type = TypesScala.Short
  override val String: jvm.Type = TypesJava.String

  override def bigDecimalFromDouble(d: jvm.Code): jvm.Code =
    code"${TypesScala.BigDecimal}.decimal($d)"

  override object Optional extends OptionalSupport {
    val tpe: jvm.Type = TypesScala.Option
    val none: jvm.Code = TypesScala.None.code

    def some(value: jvm.Code): jvm.Code = code"${TypesScala.Some}($value)"
    def map(opt: jvm.Code, f: jvm.Code): jvm.Code = code"$opt.map($f)"
    def filter(opt: jvm.Code, predicate: jvm.Code): jvm.Code = code"$opt.filter($predicate)"
    def get(opt: jvm.Code): jvm.Code = code"$opt.get"
    def getOrElse(opt: jvm.Code, default: jvm.Code): jvm.Code = code"$opt.getOrElse($default)"
    def isEmpty(opt: jvm.Code): jvm.Code = code"$opt.isEmpty"
    def isDefined(opt: jvm.Code): jvm.Code = code"$opt.isDefined"
    def filterMapOrElse(opt: jvm.Code, predicate: jvm.Code, mapper: jvm.Code, default: jvm.Code): jvm.Code =
      code"$opt.filter($predicate).map($mapper).getOrElse($default)"
  }

  override object Random extends RandomSupport {
    val tpe: jvm.Type = TypesScala.Random

    def nextInt(random: jvm.Code): jvm.Code = code"$random.nextInt()"
    def nextIntBounded(random: jvm.Code, bound: jvm.Code): jvm.Code = code"$random.nextInt($bound)"
    def nextLong(random: jvm.Code): jvm.Code = code"$random.nextLong()"
    def nextLongBounded(random: jvm.Code, bound: jvm.Code): jvm.Code = code"$random.nextLong($bound)"
    def nextFloat(random: jvm.Code): jvm.Code = code"$random.nextFloat()"
    def nextDouble(random: jvm.Code): jvm.Code = code"$random.nextDouble()"
    def nextBoolean(random: jvm.Code): jvm.Code = code"$random.nextBoolean()"
    def nextBytes(random: jvm.Code, bytes: jvm.Code): jvm.Code = code"$random.nextBytes($bytes)"
    def alphanumeric(random: jvm.Code, length: jvm.Code): jvm.Code = code"$random.alphanumeric.take($length).mkString"
    def nextPrintableChar(random: jvm.Code): jvm.Code = code"$random.nextPrintableChar()"
  }

  override object ListType extends ListSupport {
    val tpe: jvm.Type = TypesScala.List

    def create(values: List[jvm.Code]): jvm.Code = code"${TypesScala.List}(${values.map(_.code).mkCode(", ")})"

    def findFirst(collection: jvm.Code, predicate: jvm.Code): jvm.Code =
      code"$collection.find($predicate)"

    def map(collection: jvm.Code, mapper: jvm.Code): jvm.Code =
      code"$collection.map($mapper).toList"

    def mapJoinString(collection: jvm.Code, mapper: jvm.Code, separator: String): jvm.Code =
      code"""$collection.map($mapper).mkString("$separator")"""

    def collectToMap(collection: jvm.Code, keyExtractor: jvm.Code, keyType: jvm.Type, valueType: jvm.Type): jvm.Code =
      code"$collection.map(x => ($keyExtractor(x), x)).toMap"

    def forEach(collection: jvm.Code, lambda: jvm.Code): jvm.Code =
      code"$collection.foreach($lambda)"

    def arrayMapToList(array: jvm.Code, mapper: jvm.Code): jvm.Code =
      code"$array.map($mapper).toList"

    def listMapToArray(list: jvm.Code, mapper: jvm.Code, arrayGenerator: jvm.Code): jvm.Code =
      code"$list.map($mapper).toArray"
  }

  override object MapOps extends MapSupport {
    val tpe: jvm.Type = TypesScala.Map
    val mutableImpl: jvm.Type = TypesScala.mutableMap

    def newMutableMap(keyType: jvm.Type, valueType: jvm.Type): jvm.Code =
      code"${TypesScala.mutableMap}.empty[$keyType, $valueType]"

    def newMap(keyType: jvm.Type, valueType: jvm.Type, value: jvm.Code): jvm.Code =
      code"${TypesScala.Map}[$keyType, $valueType]($value)"

    def put(map: jvm.Code, key: jvm.Code, value: jvm.Code): jvm.Code =
      code"$map.put($key, $value)"

    def putVoid(map: jvm.Code, key: jvm.Code, value: jvm.Code): jvm.Code =
      jvm.IgnoreResult(code"$map.put($key, $value)")

    def mkStringKV(map: jvm.Code, kvSep: String, entrySep: String): jvm.Code =
      code"""$map.map { case (k, v) => k + "$kvSep" + v }.mkString("$entrySep")"""

    def forEach(map: jvm.Code, lambda: jvm.Lambda): jvm.Code =
      code"$map.foreach($lambda)"

    def castMap(expr: jvm.Code, keyType: jvm.Type, valueType: jvm.Type): jvm.Code =
      code"""|{
             |  val b = ${TypesScala.Map}.newBuilder[$keyType, $valueType]
             |  $expr.foreach((k, v) => b += k.asInstanceOf[$keyType] -> v.asInstanceOf[$valueType])
             |  b.result()
             |}""".stripMargin

    def toImmutable(map: jvm.Code, keyType: jvm.Type, valueType: jvm.Type): jvm.Code =
      code"$map.toMap"

    def get(map: jvm.Code, key: jvm.Code): jvm.Code =
      code"$map.get($key)"

    def remove(map: jvm.Code, key: jvm.Code): jvm.Code =
      code"$map.remove($key)"

    def removeVoid(map: jvm.Code, key: jvm.Code): jvm.Code =
      jvm.IgnoreResult(code"$map.remove($key)")

    def contains(map: jvm.Code, key: jvm.Code): jvm.Code =
      code"$map.contains($key)"

    def valuesToList(map: jvm.Code): jvm.Code =
      code"$map.values.toList"
  }
}

/** Type support using Java's standard library types */
object TypeSupportJava extends TypeSupport {
  override val BigDecimal: jvm.Type = TypesJava.BigDecimal
  override val Boolean: jvm.Type = TypesJava.Boolean
  override val Byte: jvm.Type = TypesJava.Byte
  override val Double: jvm.Type = TypesJava.Double
  override val Float: jvm.Type = TypesJava.Float
  override val Int: jvm.Type = TypesJava.Integer
  override val IteratorType: jvm.Type = TypesJava.Iterator
  override val Long: jvm.Type = TypesJava.Long
  override val Short: jvm.Type = TypesJava.Short
  override val String: jvm.Type = TypesJava.String

  override def bigDecimalFromDouble(d: jvm.Code): jvm.Code =
    code"${TypesJava.BigDecimal}.valueOf($d)"

  override object Optional extends OptionalSupport {
    val tpe: jvm.Type = TypesJava.Optional
    val none: jvm.Code = code"${TypesJava.Optional}.empty()"

    def some(value: jvm.Code): jvm.Code = code"${TypesJava.Optional}.of($value)"
    def map(opt: jvm.Code, f: jvm.Code): jvm.Code = code"$opt.map($f)"
    def filter(opt: jvm.Code, predicate: jvm.Code): jvm.Code = code"$opt.filter($predicate)"
    def get(opt: jvm.Code): jvm.Code = code"$opt.get()"
    def getOrElse(opt: jvm.Code, default: jvm.Code): jvm.Code = code"$opt.orElseGet($default)"
    def isEmpty(opt: jvm.Code): jvm.Code = code"$opt.isEmpty()"
    def isDefined(opt: jvm.Code): jvm.Code = code"$opt.isPresent()"
    def filterMapOrElse(opt: jvm.Code, predicate: jvm.Code, mapper: jvm.Code, default: jvm.Code): jvm.Code =
      code"$opt.filter($predicate).map($mapper).orElse($default)"
  }

  override object Random extends RandomSupport {
    val tpe: jvm.Type = TypesJava.Random

    def nextInt(random: jvm.Code): jvm.Code = code"$random.nextInt()"
    def nextIntBounded(random: jvm.Code, bound: jvm.Code): jvm.Code = code"$random.nextInt($bound)"
    def nextLong(random: jvm.Code): jvm.Code = code"$random.nextLong()"
    def nextLongBounded(random: jvm.Code, bound: jvm.Code): jvm.Code = code"$random.nextLong($bound)"
    def nextFloat(random: jvm.Code): jvm.Code = code"$random.nextFloat()"
    def nextDouble(random: jvm.Code): jvm.Code = code"$random.nextDouble()"
    def nextBoolean(random: jvm.Code): jvm.Code = code"$random.nextBoolean()"
    def nextBytes(random: jvm.Code, bytes: jvm.Code): jvm.Code = code"$random.nextBytes($bytes)"
    def alphanumeric(random: jvm.Code, length: jvm.Code): jvm.Code = code"${TypesJava.RandomHelper}.alphanumeric($random, $length)"
    def nextPrintableChar(random: jvm.Code): jvm.Code = code"(char)(33 + $random.nextInt(94))"
  }

  override object ListType extends ListSupport {
    val tpe: jvm.Type = TypesJava.List

    def create(values: List[jvm.Code]): jvm.Code = code"${TypesJava.List}.of(${values.mkCode(", ")})"

    def findFirst(collection: jvm.Code, predicate: jvm.Code): jvm.Code =
      code"$collection.stream().filter($predicate).findFirst()"

    def map(collection: jvm.Code, mapper: jvm.Code): jvm.Code =
      code"$collection.stream().map($mapper).toList()"

    def mapJoinString(collection: jvm.Code, mapper: jvm.Code, separator: String): jvm.Code =
      code"""$collection.stream().map($mapper).collect(${TypesJava.Collectors}.joining("$separator"))"""

    def collectToMap(collection: jvm.Code, keyExtractor: jvm.Code, keyType: jvm.Type, valueType: jvm.Type): jvm.Code =
      code"$collection.stream().collect(${TypesJava.Collectors}.toMap($keyExtractor, ${TypesJava.Function}.identity()))"

    def forEach(collection: jvm.Code, lambda: jvm.Code): jvm.Code =
      code"$collection.forEach($lambda)"

    def arrayMapToList(array: jvm.Code, mapper: jvm.Code): jvm.Code =
      code"${TypesJava.Arrays}.stream($array).map($mapper).toList()"

    def listMapToArray(list: jvm.Code, mapper: jvm.Code, arrayGenerator: jvm.Code): jvm.Code =
      code"$list.stream().map($mapper).toArray($arrayGenerator)"
  }

  override object MapOps extends MapSupport {
    val tpe: jvm.Type = TypesJava.Map
    val mutableImpl: jvm.Type = TypesJava.HashMap

    def newMutableMap(keyType: jvm.Type, valueType: jvm.Type): jvm.Code =
      jvm.New(TypesJava.HashMap.of(keyType, valueType), Nil)

    def newMap(keyType: jvm.Type, valueType: jvm.Type, value: jvm.Code): jvm.Code =
      value // In Java, just use the value directly

    def put(map: jvm.Code, key: jvm.Code, value: jvm.Code): jvm.Code =
      code"$map.put($key, $value)"

    def putVoid(map: jvm.Code, key: jvm.Code, value: jvm.Code): jvm.Code =
      jvm.IgnoreResult(code"$map.put($key, $value)")

    def mkStringKV(map: jvm.Code, kvSep: String, entrySep: String): jvm.Code =
      code"""${TypesJava.String}.join("$entrySep", $map.entrySet().stream().map(e -> e.getKey() + "$kvSep" + e.getValue()).toList())"""

    def forEach(map: jvm.Code, lambda: jvm.Lambda): jvm.Code =
      code"$map.forEach($lambda)"

    def castMap(expr: jvm.Code, keyType: jvm.Type, valueType: jvm.Type): jvm.Code =
      code"(${TypesJava.Map.of(keyType, valueType)}) $expr"

    def toImmutable(map: jvm.Code, keyType: jvm.Type, valueType: jvm.Type): jvm.Code =
      map // In Java, HashMap is already a Map - noop

    def get(map: jvm.Code, key: jvm.Code): jvm.Code =
      code"${TypesJava.Optional}.ofNullable($map.get($key))"

    def remove(map: jvm.Code, key: jvm.Code): jvm.Code =
      code"${TypesJava.Optional}.ofNullable($map.remove($key))"

    def removeVoid(map: jvm.Code, key: jvm.Code): jvm.Code =
      jvm.IgnoreResult(code"$map.remove($key)")

    def contains(map: jvm.Code, key: jvm.Code): jvm.Code =
      code"$map.containsKey($key)"

    def valuesToList(map: jvm.Code): jvm.Code =
      jvm.New(jvm.InferredTargs(TypesJava.ArrayList), List(jvm.Arg.Pos(code"$map.values()")))
  }
}
