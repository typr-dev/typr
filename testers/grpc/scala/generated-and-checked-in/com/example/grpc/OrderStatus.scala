package com.example.grpc

import java.lang.IllegalArgumentException


enum OrderStatus {
  case ORDER_STATUS_UNSPECIFIED, ORDER_STATUS_PENDING, ORDER_STATUS_PROCESSING, ORDER_STATUS_SHIPPED, ORDER_STATUS_DELIVERED, ORDER_STATUS_CANCELLED
  def toValue: Int = {
    if (this.toString.equals("ORDER_STATUS_UNSPECIFIED")) { return 0 }
    else if (this.toString.equals("ORDER_STATUS_PENDING")) { return 1 }
    else if (this.toString.equals("ORDER_STATUS_PROCESSING")) { return 2 }
    else if (this.toString.equals("ORDER_STATUS_SHIPPED")) { return 3 }
    else if (this.toString.equals("ORDER_STATUS_DELIVERED")) { return 4 }
    else if (this.toString.equals("ORDER_STATUS_CANCELLED")) { return 5 }
    else { return 0 }
  }
}

object OrderStatus {
  def fromValue(value: Int): OrderStatus = {
    if (value == 0) { return OrderStatus.ORDER_STATUS_UNSPECIFIED }
    else if (value == 1) { return OrderStatus.ORDER_STATUS_PENDING }
    else if (value == 2) { return OrderStatus.ORDER_STATUS_PROCESSING }
    else if (value == 3) { return OrderStatus.ORDER_STATUS_SHIPPED }
    else if (value == 4) { return OrderStatus.ORDER_STATUS_DELIVERED }
    else if (value == 5) { return OrderStatus.ORDER_STATUS_CANCELLED }
    else { throw new IllegalArgumentException("Unknown enum value: " + value) }
  }
  extension (e: OrderStatus) def value: java.lang.String = e.toString
  def apply(str: java.lang.String): scala.Either[java.lang.String, OrderStatus] =
    scala.util.Try(OrderStatus.valueOf(str)).toEither.left.map(_ => s"'$str' does not match any of the following legal values: $Names")
  def force(str: java.lang.String): OrderStatus = OrderStatus.valueOf(str)
  val All: scala.List[OrderStatus] = values.toList
  val Names: java.lang.String = All.map(_.toString).mkString(", ")
  val ByName: scala.collection.immutable.Map[java.lang.String, OrderStatus] = All.map(x => (x.toString, x)).toMap
}