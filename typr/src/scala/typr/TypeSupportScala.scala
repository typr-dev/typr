package typr

import typr.jvm.Code.TreeOps
import typr.internal.codegen.*

/** Type support using Scala's standard library types */
object TypeSupportScala extends TypeSupport {
  override val BigDecimal: jvm.Type.Qualified = TypesScala.BigDecimal
  override val Boolean: jvm.Type.Qualified = TypesScala.Boolean
  override val Byte: jvm.Type.Qualified = TypesScala.Byte
  override val Double: jvm.Type.Qualified = TypesScala.Double
  override val Float: jvm.Type.Qualified = TypesScala.Float
  override val Int: jvm.Type.Qualified = TypesScala.Int
  override val IteratorType: jvm.Type.Qualified = TypesScala.Iterator
  override val Long: jvm.Type.Qualified = TypesScala.Long
  override val Short: jvm.Type.Qualified = TypesScala.Short
  override val String: jvm.Type.Qualified = TypesJava.String
  override val ByteArray: jvm.Type = jvm.Type.ArrayOf(TypesScala.Byte)
  override val FloatArray: jvm.Type = jvm.Type.ArrayOf(TypesScala.Float)

  override def bigDecimalFromDouble(d: jvm.Code): jvm.Code =
    code"${TypesScala.BigDecimal}.decimal($d)"

  override object Optional extends OptionalSupport {
    def tpe(underlying: jvm.Type): jvm.Type = TypesScala.Option.of(underlying)
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
    def toJavaOptional(opt: jvm.Code): jvm.Code =
      code"${jvm.Import(jvm.Type.Qualified("scala.jdk.OptionConverters.RichOption"))}$opt.asJava"
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
      // Wrap keyExtractor in parens to ensure proper application when keyExtractor is a lambda
      code"$collection.map(x => (($keyExtractor).apply(x), x)).toMap"

    def forEach(collection: jvm.Code, lambda: jvm.Code): jvm.Code =
      code"$collection.foreach($lambda)"

    def arrayMapToList(array: jvm.Code, mapper: jvm.Code): jvm.Code =
      code"$array.map($mapper).toList"

    def listMapToArray(list: jvm.Code, mapper: jvm.Code, arrayGenerator: jvm.Code): jvm.Code =
      code"$list.map($mapper).toArray"

    def fromJavaList(javaList: jvm.Code, elementType: jvm.Type): jvm.Code = {
      // Use specific import for Scala 3 compatibility (wildcard `_` doesn't work in Scala 3)
      val listHasAsScala = jvm.Type.Qualified("scala.jdk.CollectionConverters.ListHasAsScala")
      code"${jvm.Import(listHasAsScala, isStatic = false)}$javaList.asScala.toList"
    }

    def toJavaList(nativeList: jvm.Code, elementType: jvm.Type): jvm.Code = {
      // Use specific import for Scala 3 compatibility (wildcard `_` doesn't work in Scala 3)
      val seqHasAsJava = jvm.Type.Qualified("scala.jdk.CollectionConverters.SeqHasAsJava")
      code"${jvm.Import(seqHasAsJava, isStatic = false)}$nativeList.asJava"
    }
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

  override object IteratorOps extends IteratorSupport {
    def mapSum(iterator: jvm.Code, mapper: jvm.Code): jvm.Code =
      code"$iterator.map($mapper).sum"

    def forEach(iterator: jvm.Code, lambda: jvm.Code): jvm.Code =
      code"$iterator.foreach($lambda)"

    def toJavaIterator(iterator: jvm.Code): jvm.Code =
      code"${jvm.Import(jvm.Type.Qualified("typr.scaladsl.ScalaIteratorOps"))}$iterator.toJavaIterator"
  }

  override object MutableListOps extends MutableListSupport {
    val tpe: jvm.Type.Qualified = TypesScala.ListBuffer

    def empty: jvm.Code =
      code"${TypesScala.ListBuffer}()"

    def add(list: jvm.Code, element: jvm.Code): jvm.Code =
      jvm.IgnoreResult(code"$list.addOne($element)")
  }
}
