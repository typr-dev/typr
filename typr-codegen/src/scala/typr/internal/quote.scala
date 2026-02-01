package typr.internal

object quote {
  val Quote = '\''
  val DoubleQuote = '"'
  def apply(str: String) = s"$Quote${str}$Quote"
  def double(str: String) = s"$DoubleQuote${str}$DoubleQuote"
}
