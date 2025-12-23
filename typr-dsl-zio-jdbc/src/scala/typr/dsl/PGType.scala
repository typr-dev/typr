package typr.dsl

import java.sql.Types

/** ** Copied from anorm**
  *
  * Parameter meta data for type `T`
  */
trait PGType[T] {
  def sqlType: String
  def jdbcType: Int
  final def as[U]: PGType[U] = PGType.instance(sqlType, jdbcType)
}

object PGType {
  def instance[T](sqlType: String, jdbcType: Int): PGType[T] = {
    val _sqlType = sqlType
    val _jdbcType = jdbcType
    new PGType[T] {
      val sqlType = _sqlType
      val jdbcType = _jdbcType
    }
  }

  implicit def forArray[T](implicit T: PGType[T]): PGType[Array[T]] = new PGType[Array[T]] {
    override def sqlType: java.lang.String = T.sqlType + "[]"
    override def jdbcType: scala.Int = java.sql.Types.ARRAY
  }

  implicit val PGTypeBigDecimal: PGType[BigDecimal] = instance(sqlType = "numeric", jdbcType = Types.DECIMAL)
  implicit val PGTypeByteArray: PGType[Array[Byte]] = instance(sqlType = "bytea", jdbcType = Types.ARRAY)
  implicit val PGTypeBoolean: PGType[Boolean] = instance(sqlType = "boolean", jdbcType = Types.BOOLEAN)
  implicit val PGTypeDouble: PGType[Double] = instance(sqlType = "float8", jdbcType = Types.DOUBLE)
  implicit val PGTypeFloat: PGType[Float] = instance(sqlType = "float4", jdbcType = Types.FLOAT)
  implicit val PGTypeInt: PGType[Int] = instance(sqlType = "int4", jdbcType = Types.INTEGER)
  implicit val PGTypeLong: PGType[Long] = instance(sqlType = "int8", jdbcType = Types.BIGINT)
  implicit val PGTypeShort: PGType[Short] = instance(sqlType = "int2", jdbcType = Types.SMALLINT)
  implicit val PGTypeString: PGType[String] = instance(sqlType = "text", jdbcType = Types.VARCHAR)
  implicit val PGTypeUUID: PGType[java.util.UUID] = instance(sqlType = "uuid", jdbcType = Types.OTHER)

  implicit val PGTypeJBigDecimal: PGType[java.math.BigDecimal] = instance(sqlType = PGTypeBigDecimal.sqlType, jdbcType = PGTypeBigDecimal.jdbcType)
  implicit val PGTypeJBool: PGType[java.lang.Boolean] = instance(sqlType = PGTypeBoolean.sqlType, jdbcType = PGTypeBoolean.jdbcType)
  implicit val PGTypeJDouble: PGType[java.lang.Double] = instance(sqlType = PGTypeDouble.sqlType, jdbcType = PGTypeDouble.jdbcType)
  implicit val PGTypeJInteger: PGType[java.lang.Integer] = instance(sqlType = PGTypeInt.sqlType, jdbcType = PGTypeInt.jdbcType)
  implicit val PGTypeJFloat: PGType[java.lang.Float] = instance(sqlType = PGTypeFloat.sqlType, jdbcType = PGTypeFloat.jdbcType)
  implicit val PGTypeJLong: PGType[java.lang.Long] = instance(sqlType = PGTypeLong.sqlType, jdbcType = PGTypeLong.jdbcType)
  implicit val PGTypeJShort: PGType[java.lang.Short] = instance(sqlType = PGTypeShort.sqlType, jdbcType = PGTypeShort.jdbcType)
}
