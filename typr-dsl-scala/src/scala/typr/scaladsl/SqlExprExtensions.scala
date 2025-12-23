package typr.scaladsl

import typr.dsl.SqlExpr

object SqlExprExtensions {

  implicit class BooleanSqlExprOps(private val underlying: SqlExpr[Boolean]) extends AnyVal {
    def and(other: SqlExpr[Boolean]): SqlExpr[Boolean] = {
      underlying.and(other, Bijections.scalaBooleanToJavaBoolean)
    }

    def or(other: SqlExpr[Boolean]): SqlExpr[Boolean] = {
      underlying.or(other, Bijections.scalaBooleanToJavaBoolean)
    }

    def not(): SqlExpr[Boolean] = {
      underlying.not(Bijections.scalaBooleanToJavaBoolean)
    }
  }

  implicit class StringSqlExprOps(private val underlying: SqlExpr[String]) extends AnyVal {
    def like(pattern: String): SqlExpr[Boolean] = {
      underlying.like(pattern, Bijections.identity[String]).asInstanceOf[SqlExpr[Boolean]]
    }

    def stringAppend(other: SqlExpr[String]): SqlExpr[String] = {
      underlying.stringAppend(other, Bijections.identity[String])
    }

    def lower(): SqlExpr[String] = {
      underlying.lower(Bijections.identity[String])
    }

    def upper(): SqlExpr[String] = {
      underlying.upper(Bijections.identity[String])
    }

    def reverse(): SqlExpr[String] = {
      underlying.reverse(Bijections.identity[String])
    }

    def strpos(substring: SqlExpr[String]): SqlExpr[java.lang.Integer] = {
      underlying.strpos(substring, Bijections.identity[String])
    }

    def strLength(): SqlExpr[java.lang.Integer] = {
      underlying.strLength(Bijections.identity[String])
    }

    def substring(from: SqlExpr[java.lang.Integer], count: SqlExpr[java.lang.Integer]): SqlExpr[String] = {
      underlying.substring(from, count, Bijections.identity[String])
    }
  }
}
