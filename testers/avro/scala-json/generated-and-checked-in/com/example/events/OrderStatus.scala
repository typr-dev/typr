package com.example.events



/** Status of an order */

enum OrderStatus {
  case PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
}

object OrderStatus {
  
  extension (e: OrderStatus) def value: java.lang.String = e.toString
  def apply(str: java.lang.String): scala.Either[java.lang.String, OrderStatus] =
    scala.util.Try(OrderStatus.valueOf(str)).toEither.left.map(_ => s"'$str' does not match any of the following legal values: $Names")
  def force(str: java.lang.String): OrderStatus = OrderStatus.valueOf(str)
  val All: scala.List[OrderStatus] = values.toList
  val Names: java.lang.String = All.map(_.toString).mkString(", ")
  val ByName: scala.collection.immutable.Map[java.lang.String, OrderStatus] = All.map(x => (x.toString, x)).toMap
}