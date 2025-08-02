package adventureworks.production.product

import adventureworks.customtypes.*
import adventureworks.person_dynamic.PersonDynamicSqlRepoImpl
import adventureworks.production.productcategory.*
import adventureworks.production.productmodel.*
import adventureworks.production.productsubcategory.*
import adventureworks.production.unitmeasure.*
import adventureworks.public.{Flag, Name}
import adventureworks.{DomainInsert, SnapshotTest, TestInsert, withConnection}
import org.scalatest.Assertion
import typo.dsl.SqlExpr._

import java.time.LocalDateTime
import scala.annotation.nowarn
import scala.util.Random

class ProductTest extends SnapshotTest {
  val personDynamicSqlRepo = new PersonDynamicSqlRepoImpl

  test("flaf") {
    withConnection { implicit c =>
      personDynamicSqlRepo(Some("A"))
    }
  }

  test("foo") {
    withConnection { implicit c =>
      val testInsert = new TestInsert(new Random(0), DomainInsert)
      val unitmeasure = testInsert.productionUnitmeasure(UnitmeasureId("kgg"))
      val productCategory = testInsert.productionProductcategory()
      val productSubcategory = testInsert.productionProductsubcategory(productCategory.productcategoryid)
      val productModel = testInsert.productionProductmodel(catalogdescription = Some(new TypoXml("<xml/>")), instructions = Some(new TypoXml("<instructions/>")))
      val product = testInsert.productionProduct(
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
      println(product)
      succeed
    }
  }

  def runTest(
      productRepo: ProductRepo,
      projectModelRepo: ProductmodelRepo,
      unitmeasureRepo: UnitmeasureRepo,
      productcategoryRepo: ProductcategoryRepo,
      productsubcategoryRepo: ProductsubcategoryRepo
  ): Assertion = {
    withConnection { implicit c =>
      // setup
      val unitmeasure = unitmeasureRepo.insert(
        UnitmeasureRowUnsaved(
          unitmeasurecode = UnitmeasureId("kgg"),
          name = Name("name")
        )
      )
      val productCategory = productcategoryRepo.insert(
        new ProductcategoryRowUnsaved(
          name = Name("name")
        )
      )

      val productSubcategory = productsubcategoryRepo.insert(
        ProductsubcategoryRowUnsaved(
          productcategoryid = productCategory.productcategoryid,
          name = Name("name")
        )
      )
      val productmodel = projectModelRepo.insert(
        ProductmodelRowUnsaved(
          name = Name("name"),
          catalogdescription = Some(new TypoXml("<xml/>")),
          instructions = Some(new TypoXml("<instructions/>"))
        )
      )
      val unsaved1 = ProductRowUnsaved(
        name = Name("name"),
        productnumber = "productnumber",
        color = Some("color"),
        safetystocklevel = TypoShort(16),
        reorderpoint = TypoShort(18),
        standardcost = 20,
        listprice = 22,
        size = Some("size"),
        sizeunitmeasurecode = Some(unitmeasure.unitmeasurecode),
        weightunitmeasurecode = Some(unitmeasure.unitmeasurecode),
        weight = Some(1.00),
        daystomanufacture = 26,
        productline = Some("T "),
        `class` = Some("H "),
        style = Some("W "),
        productsubcategoryid = Some(productSubcategory.productsubcategoryid),
        productmodelid = Some(productmodel.productmodelid),
        sellstartdate = TypoLocalDateTime(LocalDateTime.now().plusDays(1)),
        sellenddate = Some(TypoLocalDateTime(LocalDateTime.now().plusDays(10))),
        discontinueddate = Some(TypoLocalDateTime(LocalDateTime.now().plusDays(100))),
        productid = Defaulted.UseDefault,
        makeflag = Defaulted.Provided(Flag(true)),
        finishedgoodsflag = Defaulted.Provided(Flag(true)),
        rowguid = Defaulted.Provided(TypoUUID.randomUUID),
        modifieddate = Defaulted.Provided(TypoLocalDateTime.now)
      )

      // insert and round trip check
      val saved1 = productRepo.insert(unsaved1)
      val saved2 = unsaved1.toRow(saved1.productid, ???, ???, ???, ???)
      val _ = assert(saved1 === saved2)

      // check field values
      val newModifiedDate = TypoLocalDateTime(saved1.modifieddate.value.minusDays(1))
      val updatedOpt1 = productRepo.update(saved1.copy(modifieddate = newModifiedDate))
      assert(updatedOpt1.isDefined): @nowarn
      assert(updatedOpt1.get.modifieddate == newModifiedDate): @nowarn

      val List(saved3) = productRepo.selectAll: @unchecked
      assert(saved3.modifieddate == newModifiedDate): @nowarn

      val updatedOpt2 = productRepo.update(saved3.copy(size = None))
      assert(updatedOpt2.isDefined): @nowarn
      assert(updatedOpt2.get.size.isEmpty): @nowarn

      val query0 = productRepo.select
        .joinFk(_.fkProductmodel)(projectModelRepo.select)
        .joinFk(_._1.fkProductsubcategory)(productsubcategoryRepo.select)
        .joinFk(_._2.fkProductcategory)(productcategoryRepo.select)
      compareFragment("query0")(query0.sql)
      query0.toList.foreach(println)

      val query =
        productRepo.select
          .where(_.`class` === "H ")
          .where(x => (x.daystomanufacture > 25).or(x.daystomanufacture <= 0))
          .where(x => x.productline === "foo")
          .join(unitmeasureRepo.select.where(_.name.like("name%")))
          .on { case (p, um) => p.sizeunitmeasurecode === um.unitmeasurecode }
          .join(projectModelRepo.select)
          .leftOn { case ((product, _), productModel) => product.productmodelid === productModel.productmodelid }
          .where { case ((product, _), productModel) => product.productmodelid === productModel(_.productmodelid) }
          .orderBy { case ((product, _), _) => product.productmodelid.asc }
          .orderBy { case ((_, _), productModel) => productModel(_.name).desc.withNullsFirst }

      compareFragment("query")(query.sql)
      println(query.toList)

      val leftJoined = productRepo.select
        .join(projectModelRepo.select)
        .leftOn { case (p, pm) => p.productmodelid === pm.productmodelid }

      compareFragment("leftJoined")(leftJoined.sql)
      leftJoined.toList.foreach(println)

      val sellStartDate = TypoLocalDateTime.now
      val update = productRepo.update
        .setComputedValue(_.name)(p => (p.reverse.upper || Name("flaff")).substring(2, 4))
        .setValue(_.listprice)(BigDecimal(2))
        .setComputedValue(_.reorderpoint)(_ + TypoShort(22))
        .setComputedValue(_.sizeunitmeasurecode)(_ => Some(unitmeasure.unitmeasurecode))
        .setComputedValue(_.sellstartdate)(_ => sellStartDate)
        .where(_.productid === saved1.productid)

      compareFragment("updateReturning")(update.sql(returning = true))
      val List(updated) = update.executeReturnChanged()
      assert(updated.name === Name("MANf")): @nowarn
      assert(updated.listprice === BigDecimal(2)): @nowarn
      assert(updated.reorderpoint === TypoShort(40)): @nowarn
      assert(updated.sellstartdate === sellStartDate): @nowarn

      val q = productRepo.select
        .where(p => !p.name.like("foo%"))
        .where(p => !(p.name.underlying || p.color).like("foo%"))
        .where(p => p.daystomanufacture > 0)
        .where(p => p.modifieddate < TypoLocalDateTime.now)
        .join(projectModelRepo.select.where(p => p.modifieddate < TypoLocalDateTime.now))
        .on { case (p, pm) => p.productmodelid === pm.productmodelid }
        .where { case (_, pm) => !pm.instructions.isNull }

      compareFragment("q")(q.sql)
      q.toList.foreach(println)

      val q2 = productRepo.select
        // select from id, arrays work
        .where(p => p.productid.in(Array(saved1.productid, new ProductId(22))))
        // call `length` function and compare result
        .where(p => p.name.strLength > 3)
        // concatenate two strings (one of which is a wrapped type in scala) and compare result
        .where(p => !(p.name.underlying || p.color).like("foo%"))
        // tracks nullability
        .whereStrict(p => p.color.coalesce("yellow") !== "blue")
        // compare dates
        .where(p => p.modifieddate < TypoLocalDateTime.now)
        // join, filter table we join with as well
        .join(projectModelRepo.select.where(pm => pm.name.strLength > 0))
        .on { case (p, pm) => p.productmodelid === pm.productmodelid }
        // additional predicates for joined rows.
        .where { case (_, pm) => pm.name.underlying !== "foo" }
        // works arbitrarily deep
        .join(projectModelRepo.select.where(pm => pm.name.strLength > 0))
        .leftOn { case ((p, _), pm2) =>
          (p.productmodelid === pm2.productmodelid).and(false)
        }
        // order by
        .orderBy { case (_, pm2) => pm2(_.name).asc }
        .orderBy { case ((p, _), _) => p.color.desc.withNullsFirst }

      compareFragment("q2")(q2.sql)
      q2.toList.foreach { case ((p, pm1), pm2) =>
        println(p)
        println(pm1)
        println(pm2)
      }

      // delete
      val delete = productRepo.delete.where(_.productid === saved1.productid)
      compareFragment("delete")(delete.sql)

      delete.execute(): @nowarn

      val List() = productRepo.selectAll: @unchecked

      // done
      succeed
    }

  }

  test("in-memory") {
    runTest(
      productRepo = new ProductRepoMock(_.toRow(ProductId(0), Flag.apply(true), Flag.apply(false), TypoUUID.randomUUID, TypoLocalDateTime.now)),
      projectModelRepo = new ProductmodelRepoMock(_.toRow(ProductmodelId(0), TypoUUID.randomUUID, TypoLocalDateTime.now)),
      unitmeasureRepo = new UnitmeasureRepoMock(_.toRow(TypoLocalDateTime.now)),
      productcategoryRepo = new ProductcategoryRepoMock(_.toRow(ProductcategoryId(0), TypoUUID.randomUUID, TypoLocalDateTime.now)),
      productsubcategoryRepo = new ProductsubcategoryRepoMock(_.toRow(ProductsubcategoryId(0), TypoUUID.randomUUID, TypoLocalDateTime.now))
    )
  }

  test("pg") {
    runTest(
      productRepo = new ProductRepoImpl,
      projectModelRepo = new ProductmodelRepoImpl,
      unitmeasureRepo = new UnitmeasureRepoImpl,
      productcategoryRepo = new ProductcategoryRepoImpl,
      productsubcategoryRepo = new ProductsubcategoryRepoImpl
    )
  }
}
