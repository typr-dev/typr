package typr

/** Type support configuration - determines which types to use for common patterns. This is separate from Lang (which determines rendering syntax) so they can be composed.
  *
  * For example:
  *   - Scala types: Option, scala.List, scala.Map, scala.Random
  *   - Java types: Optional, java.util.List, java.util.Map, java.util.Random
  */
trait TypeSupport {

  // Primitive type mappings
  val BigDecimal: jvm.Type.Qualified
  val BigInteger: jvm.Type.Qualified
  val Boolean: jvm.Type.Qualified
  val Byte: jvm.Type.Qualified
  val Double: jvm.Type.Qualified
  val Float: jvm.Type.Qualified
  val Int: jvm.Type.Qualified
  val IteratorType: jvm.Type.Qualified
  val Long: jvm.Type.Qualified
  val Short: jvm.Type.Qualified
  val String: jvm.Type.Qualified

  // Primitive array types
  val ByteArray: jvm.Type
  val FloatArray: jvm.Type

  /** Primitive int type for interface implementations. Java: int, Kotlin: Int, Scala: Int */
  val primitiveInt: jvm.Type

  /** Primitive boolean type for interface implementations. Java: boolean, Kotlin: Boolean, Scala: Boolean */
  val primitiveBoolean: jvm.Type

  // Support traits for common patterns
  val Optional: OptionalSupport
  val ListType: ListSupport
  val Random: RandomSupport
  val MapOps: MapSupport
  val IteratorOps: IteratorSupport
  val MutableListOps: MutableListSupport

  /** Create a BigDecimal from a double value - Scala uses BigDecimal.decimal, Java uses BigDecimal.valueOf */
  def bigDecimalFromDouble(d: jvm.Code): jvm.Code

  /** Get the JVM type for a well-known primitive */
  def forPrimitive(p: internal.analysis.WellKnownPrimitive): jvm.Type.Qualified = p match {
    case internal.analysis.WellKnownPrimitive.String        => String
    case internal.analysis.WellKnownPrimitive.Boolean       => Boolean
    case internal.analysis.WellKnownPrimitive.Byte          => Byte
    case internal.analysis.WellKnownPrimitive.Short         => Short
    case internal.analysis.WellKnownPrimitive.Int           => Int
    case internal.analysis.WellKnownPrimitive.Long          => Long
    case internal.analysis.WellKnownPrimitive.Float         => Float
    case internal.analysis.WellKnownPrimitive.Double        => Double
    case internal.analysis.WellKnownPrimitive.BigDecimal    => BigDecimal
    case internal.analysis.WellKnownPrimitive.LocalDate     => TypesJava.LocalDate
    case internal.analysis.WellKnownPrimitive.LocalTime     => TypesJava.LocalTime
    case internal.analysis.WellKnownPrimitive.LocalDateTime => TypesJava.LocalDateTime
    case internal.analysis.WellKnownPrimitive.Instant       => TypesJava.Instant
    case internal.analysis.WellKnownPrimitive.UUID          => TypesJava.UUID
  }
}

trait IteratorSupport {

  /** Map over iterator elements and sum the results */
  def mapSum(iterator: jvm.Code, mapper: jvm.Code): jvm.Code

  /** Iterate over iterator elements with a consumer lambda (for side effects) */
  def forEach(iterator: jvm.Code, lambda: jvm.Code): jvm.Code

  /** Convert iterator to Java Iterator - for Scala this converts, for Java/Kotlin it's a noop */
  def toJavaIterator(iterator: jvm.Code): jvm.Code
}

trait MutableListSupport {

  /** The type of the mutable list */
  def tpe: jvm.Type.Qualified

  /** Create a new empty mutable list */
  def empty: jvm.Code

  /** Add an element to the mutable list */
  def add(list: jvm.Code, element: jvm.Code): jvm.Code
}
