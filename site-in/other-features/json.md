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

```scala mdoc:silent
import typr.*
import typr.internal.codegen.LangScala

val options = Options(
  pkg = "org.foo",
  lang = LangScala.scalaDsl(Dialect.Scala3, TypeSupportScala),
  dbLib = Some(DbLibName.Anorm),
  jsonLibs = List(JsonLibName.PlayJson)
)
```

And you will get instances like this:

```scala mdoc

import adventureworks.customtypes.TypoLocalDateTime
import adventureworks.customtypes.TypoUUID
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

/** Table: production.productmodel
 * Product model classification.
 * Primary key: productmodelid
 */
case class ProductmodelRow(
    /** Primary key for ProductModel records.
     * Default: nextval('production.productmodel_productmodelid_seq'::regclass)
     */
    productmodelid: ProductmodelId,
    /** Product model description. */
    name: Name,
    /** Detailed product catalog information in xml format. */
    catalogdescription: Option[TypoXml],
    /** Manufacturing instructions in xml format. */
    instructions: Option[TypoXml],
    /** Default: uuid_generate_v1() */
    rowguid: TypoUUID,
    /** Default: now() */
    modifieddate: TypoLocalDateTime
)

object ProductmodelRow {
  given reads: Reads[ProductmodelRow] = {
    Reads[ProductmodelRow](json => JsResult.fromTry(
      Try(
        ProductmodelRow(
          productmodelid = json.\("productmodelid").as(using ProductmodelId.reads),
          name = json.\("name").as(using Name.reads),
          catalogdescription = json.\("catalogdescription").toOption.map(_.as(using TypoXml.reads)),
          instructions = json.\("instructions").toOption.map(_.as(using TypoXml.reads)),
          rowguid = json.\("rowguid").as(using TypoUUID.reads),
          modifieddate = json.\("modifieddate").as(using TypoLocalDateTime.reads)
        )
      )
    ),
    )
  }
  given writes: OWrites[ProductmodelRow] = {
    OWrites[ProductmodelRow](o =>
      new JsObject(ListMap[String, JsValue](
        "productmodelid" -> ProductmodelId.writes.writes(o.productmodelid),
        "name" -> Name.writes.writes(o.name),
        "catalogdescription" -> Writes.OptionWrites(using TypoXml.writes).writes(o.catalogdescription),
        "instructions" -> Writes.OptionWrites(using TypoXml.writes).writes(o.instructions),
        "rowguid" -> TypoUUID.writes.writes(o.rowguid),
        "modifieddate" -> TypoLocalDateTime.writes.writes(o.modifieddate)
      ))
    )
  }
}
```

Then you can go to and from JSON without doing any extra work:
```scala mdoc
import play.api.libs.json._

Json.prettyPrint(
  Json.toJson(
    ProductmodelRow(
      productmodelid = ProductmodelId(1),
      name = Name("name"),
      catalogdescription = None,
      instructions = Some(TypoXml("<xml/>")),
      rowguid = TypoUUID("0cf84c1c-0a05-449c-8e09-562663d101ed"),
      modifieddate = TypoLocalDateTime(java.time.LocalDateTime.parse("2023-08-08T22:50:48.377623"))
    )
  )
)
```