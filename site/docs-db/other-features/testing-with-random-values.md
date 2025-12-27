---
title: Testing with random values
---

This covers a lot of interesting ground, test-wise.

If you enable `enableTestInserts` in `typr.Options` you now get an `TestInsert` class, with a method to insert a row for each table Typo knows about. 
All values except ids, foreign keys and so on are *randomly generated*, but you can override them with named parameters.

The idea is that you:
- can easily insert rows for testing
- can explicitly set the values you *do* care about
- will get random values for the rest
- are still forced to follow FKs to setup the data graph correctly
- it's easy to follow those FKs, because after inserting a row you get the persisted version back, including generated IDs
- can get the same values each time by hard coding the seed `new TestInsert(new scala.util.Random(0L))`, or you can run it multiple times with different seeds to see that the random values really do not matter
- do not need to write *any* code to get all this available to you, like the rest of Typo.

In summary, this is a fantastic way of setting up complex test scenarios in the database!

### Domains
If you use [postgres domains](../type-safety/domains.md) you typically want affect the generation of data yourself.
For that reason there is a trait you need to implement and pass in. This only affect you if you use domains.

```scala
import adventureworks.public.*

import scala.util.Random

// apply domain-specific rules here
object DomainInsert extends adventureworks.TestDomainInsert {
  override def publicAccountNumber(random: Random): AccountNumber = AccountNumber(random.nextString(10))
  override def publicFlag(random: Random): Flag = Flag(random.nextBoolean())
  override def publicMydomain(random: Random): Mydomain = Mydomain(random.nextString(10))
  override def publicName(random: Random): Name = Name(random.nextString(10))
  override def publicNameStyle(random: Random): NameStyle = NameStyle(random.nextBoolean())
  override def publicPhone(random: Random): Phone = Phone(random.nextString(10))
  override def publicShortText(random: Random): ShortText = ShortText(random.nextString(10))
  override def publicOrderNumber(random: Random): OrderNumber = OrderNumber(random.nextString(10))
}
```

### Usage example

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

<Tabs>
<TabItem value="scala" label="Scala/Kotlin" default>

```scala
import adventureworks.customtypes.{Defaulted, TypoShort, TypoLocalDateTime, TypoXml}
import adventureworks.production.unitmeasure.UnitmeasureId
import adventureworks.TestInsert

import scala.util.Random

val testInsert = new TestInsert(new Random(0), DomainInsert)
// testInsert: TestInsert = TestInsert(
//   random = scala.util.Random@12ce2cc7,
//   domainInsert = repl.MdocSession$App$DomainInsert$@62f1b14b
// )

val unitmeasure = testInsert.productionUnitmeasure(UnitmeasureId("kgg"))
// unitmeasure: UnitmeasureRow = UnitmeasureRow(
//   unitmeasurecode = UnitmeasureId(value = "kgg"),
//   name = Name(value = "椕皿鈻瑜㶯眀㏝⑂Ᶎ䩇"),
//   modifieddate = TypoLocalDateTime(value = 2025-12-06T09:48:20.342898)
// )
val productCategory = testInsert.productionProductcategory()
// productCategory: ProductcategoryRow = ProductcategoryRow(
//   productcategoryid = ProductcategoryId(value = 691),
//   name = Name(value = "℣ٿ玁冧ἀ蓆鋥射ⅅ匫"),
//   rowguid = TypoUUID(value = 51086b82-d280-11f0-89a7-0242c0a8d002),
//   modifieddate = TypoLocalDateTime(value = 2025-12-06T09:48:20.342898)
// )
val productSubcategory = testInsert.productionProductsubcategory(productCategory.productcategoryid)
// productSubcategory: ProductsubcategoryRow = ProductsubcategoryRow(
//   productsubcategoryid = ProductsubcategoryId(value = 691),
//   productcategoryid = ProductcategoryId(value = 691),
//   name = Name(value = "䪾悲켺я瘊ꖗ欖뢐豶㤖"),
//   rowguid = TypoUUID(value = 510d7712-d280-11f0-89a7-0242c0a8d002),
//   modifieddate = TypoLocalDateTime(value = 2025-12-06T09:48:20.342898)
// )
val productModel = testInsert.productionProductmodel(catalogdescription = Some(new TypoXml("<xml/>")), instructions = Some(new TypoXml("<instructions/>")))
// productModel: ProductmodelRow = ProductmodelRow(
//   productmodelid = ProductmodelId(value = 691),
//   name = Name(value = "衳⦥ፀ⟑骩誖ڠ鮩焸뭷"),
//   catalogdescription = Some(value = TypoXml(value = "<xml/>")),
//   instructions = Some(value = TypoXml(value = "<instructions/>")),
//   rowguid = TypoUUID(value = 5113a9c0-d280-11f0-89a7-0242c0a8d002),
//   modifieddate = TypoLocalDateTime(value = 2025-12-06T09:48:20.342898)
// )
testInsert.productionProduct(
  safetystocklevel = TypoShort(1),
  reorderpoint = TypoShort(1),
  standardcost = BigDecimal(1),
  listprice = BigDecimal(1),
  daystomanufacture = 10,
  sellstartdate = TypoLocalDateTime.now,
  sizeunitmeasurecode = Some(unitmeasure.unitmeasurecode),
  weightunitmeasurecode = Some(unitmeasure.unitmeasurecode),
  `class` = Some("H "),
  style = Some("W "),
  productsubcategoryid = Some(productSubcategory.productsubcategoryid),
  productmodelid = Some(productModel.productmodelid)
)
// res1: ProductRow = ProductRow(
//   productid = ProductId(value = 691),
//   name = Name(value = "睍먀缶쏄迄넼䒄䣃㓎俠"),
//   productnumber = "JXQqPyuxbr589wyJzS2S",
//   makeflag = Flag(value = true),
//   finishedgoodsflag = Flag(value = true),
//   color = Some(value = "iHrAOB2RuvBbFbQ"),
//   safetystocklevel = TypoShort(value = 1),
//   reorderpoint = TypoShort(value = 1),
//   standardcost = 1,
//   listprice = 1,
//   size = Some(value = "NB7Zu"),
//   sizeunitmeasurecode = Some(value = UnitmeasureId(value = "kgg")),
//   weightunitmeasurecode = Some(value = UnitmeasureId(value = "kgg")),
//   weight = None,
//   daystomanufacture = 10,
//   productline = None,
//   class = Some(value = "H "),
//   style = Some(value = "W "),
//   productsubcategoryid = Some(value = ProductsubcategoryId(value = 691)),
//   productmodelid = Some(value = ProductmodelId(value = 691)),
//   sellstartdate = TypoLocalDateTime(value = 2025-12-06T09:48:20.656753),
//   sellenddate = None,
//   discontinueddate = Some(
//     value = TypoLocalDateTime(value = 2034-05-19T00:51:56)
//   ),
//   rowguid = TypoUUID(value = 511ab1d4-d280-11f0-89a7-0242c0a8d002),
//   modifieddate = TypoLocalDateTime(value = 2025-12-06T09:48:20.342898)
// )
```

</TabItem>
<TabItem value="java" label="Java">

Java doesn't have default parameters, so TestInsert uses the **Inserter pattern** - a fluent builder that lets you customize rows before inserting:

```java
import adventureworks.TestInsert;
import adventureworks.DomainInsertImpl;
import adventureworks.production.unitmeasure.UnitmeasureId;
import adventureworks.public_.Name;
import java.util.Random;

var testInsert = new TestInsert(new Random(0), new DomainInsertImpl());

// Simple insert - just call insert(connection)
var productCategory = testInsert.productionProductcategory().insert(c);

// Insert with required parameters
var unitmeasure = testInsert.productionUnitmeasure(new UnitmeasureId("kgg")).insert(c);

// Customize with .with() before inserting
var productModel = testInsert.productionProductmodel()
    .with(row -> row
        .withCatalogdescription(Optional.of(new Xml("<xml/>")))
        .withInstructions(Optional.of(new Xml("<instructions/>"))))
    .insert(c);

// Complex example with foreign keys
var productSubcategory = testInsert
    .productionProductsubcategory(productCategory.productcategoryid())
    .insert(c);

var product = testInsert.productionProduct(
        (short) 1,           // safetystocklevel
        (short) 1,           // reorderpoint
        BigDecimal.ONE,      // standardcost
        BigDecimal.ONE,      // listprice
        10,                  // daystomanufacture
        LocalDateTime.now()) // sellstartdate
    .with(row -> row
        .withSizeunitmeasurecode(Optional.of(unitmeasure.unitmeasurecode()))
        .withWeightunitmeasurecode(Optional.of(unitmeasure.unitmeasurecode()))
        .withClass(Optional.of("H "))
        .withStyle(Optional.of("W "))
        .withProductsubcategoryid(Optional.of(productSubcategory.productsubcategoryid()))
        .withProductmodelid(Optional.of(productModel.productmodelid())))
    .insert(c);
```

The `Inserter<U, R>` interface provides:
- `insert(Connection c)` - Execute the insert and return the saved row
- `with(UnaryOperator<U> transformer)` - Customize the unsaved row before inserting

</TabItem>
</Tabs>

### Comparison with scalacheck

This does look a lot like scalacheck indeed.

But look closer, there are:
- no implicits
- no integration glue code with test libraries
- almost no imports, you need to mention very few types
- no keeping track of all the possible row types and repositories
- and so on

This feature is meant to be easy to use, and I really think/hope it is!
