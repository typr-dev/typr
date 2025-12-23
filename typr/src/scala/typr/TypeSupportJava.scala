package typr

import typr.internal.codegen.*

/** Type support using Java's standard library types */
object TypeSupportJava extends TypeSupport {
  override val BigDecimal: jvm.Type.Qualified = TypesJava.BigDecimal
  override val Boolean: jvm.Type.Qualified = TypesJava.Boolean
  override val Byte: jvm.Type.Qualified = TypesJava.Byte
  override val Double: jvm.Type.Qualified = TypesJava.Double
  override val Float: jvm.Type.Qualified = TypesJava.Float
  override val Int: jvm.Type.Qualified = TypesJava.Integer
  override val IteratorType: jvm.Type.Qualified = TypesJava.Iterator
  override val Long: jvm.Type.Qualified = TypesJava.Long
  override val Short: jvm.Type.Qualified = TypesJava.Short
  override val String: jvm.Type.Qualified = TypesJava.String
  override val ByteArray: jvm.Type = jvm.Type.ArrayOf(TypesJava.BytePrimitive)
  override val FloatArray: jvm.Type = jvm.Type.ArrayOf(TypesJava.FloatPrimitive)

  override def bigDecimalFromDouble(d: jvm.Code): jvm.Code =
    code"${TypesJava.BigDecimal}.valueOf($d)"

  override object Optional extends OptionalSupport {
    def tpe(underlying: jvm.Type): jvm.Type = TypesJava.Optional.of(underlying)
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
    def toJavaOptional(opt: jvm.Code): jvm.Code =
      opt // Already java.util.Optional, no conversion needed
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

    def fromJavaList(javaList: jvm.Code, elementType: jvm.Type): jvm.Code =
      javaList // No conversion needed in Java

    def toJavaList(nativeList: jvm.Code, elementType: jvm.Type): jvm.Code =
      nativeList // No conversion needed in Java
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

  override object IteratorOps extends IteratorSupport {
    // For java.util.Iterator, we need imperative style - this is used by DbLibTypo mock impl
    // The mapper is expected to return a long, so we sum using while loop
    def mapSum(iterator: jvm.Code, mapper: jvm.Code): jvm.Code =
      code"""|{
             |  long count = 0L;
             |  while ($iterator.hasNext()) {
             |    count += $mapper.apply($iterator.next());
             |  }
             |  return count;
             |}""".stripMargin

    def forEach(iterator: jvm.Code, lambda: jvm.Code): jvm.Code =
      code"$iterator.forEachRemaining($lambda)"

    def toJavaIterator(iterator: jvm.Code): jvm.Code =
      iterator // Already a Java Iterator, no conversion needed
  }

  override object MutableListOps extends MutableListSupport {
    val tpe: jvm.Type.Qualified = TypesJava.ArrayList

    def empty: jvm.Code =
      jvm.New(jvm.InferredTargs(TypesJava.ArrayList), Nil)

    def add(list: jvm.Code, element: jvm.Code): jvm.Code =
      jvm.IgnoreResult(code"$list.add($element)")
  }
}
