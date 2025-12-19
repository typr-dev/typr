package typo.internal.codegen

import typo.jvm.Type
import typo.*

object TypeSupportKotlin extends TypeSupport {
  override val BigDecimal: jvm.Type.Qualified = TypesJava.BigDecimal
  override val Boolean: jvm.Type.Qualified = TypesKotlin.Boolean
  override val Byte: jvm.Type.Qualified = TypesKotlin.Byte
  override val Double: jvm.Type.Qualified = TypesKotlin.Double
  override val Float: jvm.Type.Qualified = TypesKotlin.Float
  override val Int: jvm.Type.Qualified = TypesKotlin.Int
  override val IteratorType: jvm.Type.Qualified = TypesKotlin.Iterator
  override val Long: jvm.Type.Qualified = TypesKotlin.Long
  override val Short: jvm.Type.Qualified = TypesKotlin.Short
  override val String: jvm.Type.Qualified = TypesKotlin.String
  override val ByteArray: jvm.Type = TypesKotlin.ByteArray
  override val FloatArray: jvm.Type = TypesKotlin.FloatArray

  override def bigDecimalFromDouble(d: jvm.Code): jvm.Code =
    code"${TypesJava.BigDecimal}.valueOf($d)"

  // Use Kotlin native nullable types (T?) instead of Java Optional
  override object Optional extends OptionalSupport {
    // For Kotlin, generate T? instead of Optional<T>
    def tpe(underlying: jvm.Type): jvm.Type = jvm.Type.KotlinNullable(underlying)

    // For Kotlin, none is null
    val none: jvm.Code = code"null"

    // For Kotlin, some(value) is just the value (no wrapping)
    def some(value: jvm.Code): jvm.Code = value

    // Kotlin nullable operations using elvis operator and safe calls
    def map(opt: jvm.Code, f: jvm.Code): jvm.Code = code"$opt?.let($f)"
    def filter(opt: jvm.Code, predicate: jvm.Code): jvm.Code = code"$opt?.takeIf($predicate)"
    def get(opt: jvm.Code): jvm.Code = code"$opt!!" // Force unwrap (throws if null)
    def getOrElse(opt: jvm.Code, default: jvm.Code): jvm.Code = code"$opt ?: $default" // Elvis operator
    def isEmpty(opt: jvm.Code): jvm.Code = code"$opt == null"
    def isDefined(opt: jvm.Code): jvm.Code = code"$opt != null"
    def filterMapOrElse(opt: jvm.Code, predicate: jvm.Code, mapper: jvm.Code, default: jvm.Code): jvm.Code =
      code"$opt?.takeIf($predicate)?.let($mapper) ?: $default"
  }

  override object ListType extends ListSupport {
    val tpe: jvm.Type = TypesKotlin.List
    def create(values: List[jvm.Code]): jvm.Code = code"listOf(${values.mkCode(", ")})"

    def arrayFlatMapOptional(array: jvm.Code, getter: jvm.Code): jvm.Code =
      code"$array.mapNotNull($getter)"

    def findFirst(collection: jvm.Code, predicate: jvm.Code): jvm.Code =
      // Kotlin find() returns T? natively
      code"$collection.find($predicate)"

    def streamMapToList(collection: jvm.Code, mapper: jvm.Code): jvm.Code =
      code"$collection.map($mapper)"

    def collectToMap(collection: jvm.Code, keyExtractor: jvm.Code, keyType: jvm.Type, valueType: jvm.Type): jvm.Code =
      code"$collection.associateBy($keyExtractor)"

    def mapJoinString(collection: jvm.Code, mapper: jvm.Code, separator: String): jvm.Code =
      code"""$collection.map($mapper).joinToString("$separator")"""

    def forEach(collection: jvm.Code, lambda: jvm.Code): jvm.Code =
      code"$collection.forEach($lambda)"

    def arrayMapToList(array: jvm.Code, mapper: jvm.Code): jvm.Code =
      code"$array.map($mapper).toList()"

    def listMapToArray(list: jvm.Code, mapper: jvm.Code, arrayGenerator: jvm.Code): jvm.Code =
      code"$list.map($mapper).toTypedArray()"

    def map(collection: jvm.Code, mapper: jvm.Code): jvm.Code =
      code"$collection.map($mapper)"
  }

  override object Random extends RandomSupport {
    val tpe: Type = TypesJava.Random

    def nextInt(random: jvm.Code): jvm.Code = code"$random.nextInt()"
    def nextIntBounded(random: jvm.Code, bound: jvm.Code): jvm.Code = code"$random.nextInt($bound)"
    def nextLong(random: jvm.Code): jvm.Code = code"$random.nextLong()"
    def nextLongBounded(random: jvm.Code, bound: jvm.Code): jvm.Code = code"$random.nextLong($bound)"
    def nextFloat(random: jvm.Code): jvm.Code = code"$random.nextFloat()"
    def nextDouble(random: jvm.Code): jvm.Code = code"$random.nextDouble()"
    def nextBoolean(random: jvm.Code): jvm.Code = code"$random.nextBoolean()"
    def nextBytes(random: jvm.Code, bytes: jvm.Code): jvm.Code = code"$random.nextBytes($bytes)"
    def alphanumeric(random: jvm.Code, length: jvm.Code): jvm.Code = code"${TypesJava.RandomHelper}.alphanumeric($random, $length)"
    def nextPrintableChar(random: jvm.Code): jvm.Code = code"(33 + $random.nextInt(94)).toChar()"
  }

  override object MapOps extends MapSupport {
    val tpe: jvm.Type = TypesKotlin.Map
    val mutableImpl: jvm.Type = TypesKotlin.MutableMap

    def newMutableMap(keyType: jvm.Type, valueType: jvm.Type): jvm.Code =
      code"mutableMapOf<$keyType, $valueType>()"

    def newMap(keyType: jvm.Type, valueType: jvm.Type, value: jvm.Code): jvm.Code =
      value // In Kotlin, just use the value directly

    def put(map: jvm.Code, key: jvm.Code, value: jvm.Code): jvm.Code =
      code"$map[$key] = $value"

    def putVoid(map: jvm.Code, key: jvm.Code, value: jvm.Code): jvm.Code =
      code"$map[$key] = $value"

    def mkStringKV(map: jvm.Code, kvSep: String, entrySep: String): jvm.Code =
      code"""$map.entries.joinToString("$entrySep") { "$${it.key}$kvSep$${it.value}" }"""

    def forEach(map: jvm.Code, lambda: jvm.Lambda): jvm.Code =
      code"$map.forEach($lambda)"

    def castMap(expr: jvm.Code, keyType: jvm.Type, valueType: jvm.Type): jvm.Code =
      code"$expr as ${TypesKotlin.Map}<$keyType, $valueType>"

    def toImmutable(map: jvm.Code, keyType: jvm.Type, valueType: jvm.Type): jvm.Code =
      code"$map.toMap()"

    // Kotlin map operations return nullable types natively
    def get(map: jvm.Code, key: jvm.Code): jvm.Code =
      code"$map[$key]"

    // Kotlin map.remove() returns nullable type natively
    def remove(map: jvm.Code, key: jvm.Code): jvm.Code =
      code"$map.remove($key)"

    def removeVoid(map: jvm.Code, key: jvm.Code): jvm.Code =
      code"$map.remove($key)"

    def contains(map: jvm.Code, key: jvm.Code): jvm.Code =
      code"$map.containsKey($key)"

    def valuesToList(map: jvm.Code): jvm.Code =
      code"$map.values.toList()"
  }

  override object IteratorOps extends IteratorSupport {
    // Kotlin's Iterator/MutableIterator doesn't have map(), so we use imperative style
    def mapSum(iterator: jvm.Code, mapper: jvm.Code): jvm.Code =
      code"""|run {
             |  var count = 0L
             |  while ($iterator.hasNext()) {
             |    count += $mapper($iterator.next())
             |  }
             |  count
             |}""".stripMargin

    def forEach(iterator: jvm.Code, lambda: jvm.Code): jvm.Code =
      code"$iterator.forEach($lambda)"
  }

  override object MutableListOps extends MutableListSupport {
    val tpe: jvm.Type.Qualified = TypesJava.ArrayList

    def empty: jvm.Code =
      code"${TypesJava.ArrayList}()"

    def add(list: jvm.Code, element: jvm.Code): jvm.Code =
      code"$list.add($element)"
  }
}
