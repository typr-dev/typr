package com.example.grpc

import java.lang.IllegalArgumentException


enum Priority {
  case PRIORITY_UNSPECIFIED, PRIORITY_LOW, PRIORITY_MEDIUM, PRIORITY_HIGH, PRIORITY_CRITICAL
  def toValue: Int = {
    if (this.toString.equals("PRIORITY_UNSPECIFIED")) { return 0 }
    else if (this.toString.equals("PRIORITY_LOW")) { return 1 }
    else if (this.toString.equals("PRIORITY_MEDIUM")) { return 2 }
    else if (this.toString.equals("PRIORITY_HIGH")) { return 3 }
    else if (this.toString.equals("PRIORITY_CRITICAL")) { return 4 }
    else { return 0 }
  }
}

object Priority {
  def fromValue(value: Int): Priority = {
    if (value == 0) { return Priority.PRIORITY_UNSPECIFIED }
    else if (value == 1) { return Priority.PRIORITY_LOW }
    else if (value == 2) { return Priority.PRIORITY_MEDIUM }
    else if (value == 3) { return Priority.PRIORITY_HIGH }
    else if (value == 4) { return Priority.PRIORITY_CRITICAL }
    else { throw new IllegalArgumentException("Unknown enum value: " + value) }
  }
  extension (e: Priority) def value: java.lang.String = e.toString
  def apply(str: java.lang.String): scala.Either[java.lang.String, Priority] =
    scala.util.Try(Priority.valueOf(str)).toEither.left.map(_ => s"'$str' does not match any of the following legal values: $Names")
  def force(str: java.lang.String): Priority = Priority.valueOf(str)
  val All: scala.List[Priority] = values.toList
  val Names: java.lang.String = All.map(_.toString).mkString(", ")
  val ByName: scala.collection.immutable.Map[java.lang.String, Priority] = All.map(x => (x.toString, x)).toMap
}