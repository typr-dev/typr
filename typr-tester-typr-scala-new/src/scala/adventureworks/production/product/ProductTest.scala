package adventureworks.production.product

import adventureworks.production.productcategory.*
import adventureworks.production.productmodel.*
import adventureworks.production.productsubcategory.*
import adventureworks.production.unitmeasure.*
import adventureworks.public.{Flag, Name}
import adventureworks.{DbNow, SnapshotTest, WithConnection}
import org.junit.Assert.*
import org.junit.Test
import typr.data.Xml

import java.util.UUID

class ProductTest extends SnapshotTest {

  @Test
  def basicCrud(): Unit = {
    WithConnection {
      // setup dependencies
      val unitmeasureRepo = UnitmeasureRepoImpl()
      val unitmeasure = unitmeasureRepo.insert(
        UnitmeasureRowUnsaved(UnitmeasureId("kgg"), Name("name"))
      )

      val productcategoryRepo = ProductcategoryRepoImpl()
      val productCategory = productcategoryRepo.insert(ProductcategoryRowUnsaved(Name("name")))

      val productsubcategoryRepo = ProductsubcategoryRepoImpl()
      val productSubcategory = productsubcategoryRepo.insert(
        ProductsubcategoryRowUnsaved(productCategory.productcategoryid, Name("name"))
      )

      val productmodelRepo = ProductmodelRepoImpl()
      val productmodel = productmodelRepo.insert(
        ProductmodelRowUnsaved(Name("name")).copy(
          catalogdescription = Some(Xml("<xml/>")),
          instructions = Some(Xml("<instructions/>"))
        )
      )

      val productRepo = ProductRepoImpl()

      val unsaved = ProductRowUnsaved(
        name = Name("name"),
        productnumber = "productnumber",
        safetystocklevel = 16,
        reorderpoint = 18,
        standardcost = BigDecimal(20),
        listprice = BigDecimal(22),
        daystomanufacture = 26,
        sellstartdate = DbNow.localDateTime().plusDays(1)
      ).copy(
        color = Some("color"),
        sizeunitmeasurecode = Some(unitmeasure.unitmeasurecode),
        productsubcategoryid = Some(productSubcategory.productsubcategoryid),
        productmodelid = Some(productmodel.productmodelid)
      )

      // insert
      val saved = productRepo.insert(unsaved)
      assertNotNull(saved)
      assertNotNull(saved.productid)

      // select by id
      val found = productRepo.selectById(saved.productid)
      assertTrue(found.isDefined)
      assertEquals(saved, found.get)

      // update
      val newModifiedDate = saved.modifieddate.minusDays(1)
      val updated = productRepo.update(saved.copy(modifieddate = newModifiedDate))
      assertTrue(updated)
      val afterUpdate = productRepo.selectById(saved.productid)
      assertTrue(afterUpdate.isDefined)
      assertEquals(newModifiedDate, afterUpdate.get.modifieddate)

      // delete
      val deleted = productRepo.deleteById(saved.productid)
      assertTrue(deleted)
      val afterDelete = productRepo.selectById(saved.productid)
      assertFalse(afterDelete.isDefined)
    }
  }

  @Test
  def testDslQueries(): Unit = {
    WithConnection {
      val productRepo = ProductRepoImpl()
      val productmodelRepo = ProductmodelRepoImpl()
      val productsubcategoryRepo = ProductsubcategoryRepoImpl()
      val productcategoryRepo = ProductcategoryRepoImpl()

      // Test join FK query
      val query = productRepo.select
        .joinFk(p => p.fkProductmodel, productmodelRepo.select)
        .joinFk(p_pm => p_pm._1.fkProductsubcategory, productsubcategoryRepo.select)
        .joinFk(p_pm_ps => p_pm_ps._2.fkProductcategory, productcategoryRepo.select)

      compareFragment("joinFk", query.sql())
    }
  }

  @Test
  def testToRow(): Unit = {
    val unsaved = ProductRowUnsaved(
      name = Name("name"),
      productnumber = "productnumber",
      safetystocklevel = 16,
      reorderpoint = 18,
      standardcost = BigDecimal(20),
      listprice = BigDecimal(22),
      daystomanufacture = 26,
      sellstartdate = DbNow.localDateTime().plusDays(1)
    )

    val row = unsaved.toRow(
      ProductId(1),
      Flag(true),
      Flag(false),
      UUID.randomUUID(),
      DbNow.localDateTime()
    )
//
    assertNotNull(row)
    assertEquals(ProductId(1), row.productid)
    assertEquals(Name("name"), row.name)
  }
}
