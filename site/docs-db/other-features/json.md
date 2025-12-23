---
title: JSON codecs
---

Since Typo has the entire schema in memory anyway, it can also generate JSON codecs for you.

If you want to transfer the row objects anywhere else than to and from
PostgreSQL or write some generic code across tables, it's very convenient to be able to use 

You add the wanted JSON libraries to `typr.Options` when running typo to get the codecs.

Currently, you can choose between `play-json`, `circe` and `zio-json`. 
It's likely quite easy to add another one if you want to contribute! 

For instance:

```scala
import typr.*
import typr.internal.codegen.LangScala

val options = Options(
  pkg = "org.foo",
  lang = LangScala(Dialect.Scala3, TypeSupportScala),
  jsonLibs = List(JsonLibName.PlayJson), // or `Nil` if you don't want json
  dbLib = Some(DbLibName.Anorm)
)
```

And you will get instances like this:

```scala
import adventureworks.customtypes.TypoLocalDateTime
import adventureworks.customtypes.TypoXml
import adventureworks.production.productmodel.ProductmodelId
import adventureworks.public.Name
import java.util.UUID
import play.api.libs.json.JsObject
import play.api.libs.json.JsResult
import play.api.libs.json.JsValue
import play.api.libs.json.OWrites
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import scala.collection.immutable.ListMap
import scala.util.Try

case class ProductmodelRow(
    /** Primary key for ProductModel records. */
    productmodelid: ProductmodelId,
    /** Product model description. */
    name: Name,
    /** Detailed product catalog information in xml format. */
    catalogdescription: Option[TypoXml],
    /** Manufacturing instructions in xml format. */
    instructions: Option[TypoXml],
    rowguid: UUID,
    modifieddate: TypoLocalDateTime
)

object ProductmodelRow {
  implicit lazy val reads: Reads[ProductmodelRow] = Reads[ProductmodelRow](json => JsResult.fromTry(
    Try(
      ProductmodelRow(
        productmodelid = json.\("productmodelid").as(ProductmodelId.reads),
        name = json.\("name").as(Name.reads),
        catalogdescription = json.\("catalogdescription").toOption.map(_.as(TypoXml.reads)),
        instructions = json.\("instructions").toOption.map(_.as(TypoXml.reads)),
        rowguid = json.\("rowguid").as(Reads.uuidReads),
        modifieddate = json.\("modifieddate").as(TypoLocalDateTime.reads)
      )
    )
  ),
  )
  implicit lazy val writes: OWrites[ProductmodelRow] = OWrites[ProductmodelRow](o =>
    new JsObject(ListMap[String, JsValue](
      "productmodelid" -> ProductmodelId.writes.writes(o.productmodelid),
      "name" -> Name.writes.writes(o.name),
      "catalogdescription" -> Writes.OptionWrites(TypoXml.writes).writes(o.catalogdescription),
      "instructions" -> Writes.OptionWrites(TypoXml.writes).writes(o.instructions),
      "rowguid" -> Writes.UuidWrites.writes(o.rowguid),
      "modifieddate" -> TypoLocalDateTime.writes.writes(o.modifieddate)
    ))
  )
}
```

Then you can go to and from JSON without doing any extra work:
```scala
import play.api.libs.json._

Json.prettyPrint(
  Json.toJson(
    ProductmodelRow(
      productmodelid = ProductmodelId(1),
      name = Name("name"),
      catalogdescription = None,
      instructions = Some(TypoXml("<xml/>")),
      rowguid = UUID.fromString("0cf84c1c-0a05-449c-8e09-562663d101ed"),
      modifieddate = TypoLocalDateTime(java.time.LocalDateTime.parse("2023-08-08T22:50:48.377623"))
    )
  )
)
// res0: String = """{
//   "productmodelid" : 1,
//   "name" : "name",
//   "catalogdescription" : null,
//   "instructions" : "<xml/>",
//   "rowguid" : "0cf84c1c-0a05-449c-8e09-562663d101ed",
//   "modifieddate" : "2023-08-08T22:50:48.377623"
// }"""
```