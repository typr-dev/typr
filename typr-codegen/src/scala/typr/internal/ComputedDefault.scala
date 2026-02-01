package typr
package internal

case class ComputedDefault(naming: Naming) {
  val Defaulted = jvm.Type.Qualified(naming.className(List(jvm.Ident("Defaulted"))))
  val Provided = jvm.Ident("Provided")
  val UseDefault = jvm.Ident("UseDefault")
}
