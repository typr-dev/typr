package adventureworks.production.productcosthistory

import adventureworks.{DbNow, SnapshotTest, WithConnection}
import adventureworks.person.businessentity.*
import adventureworks.person.emailaddress.*
import adventureworks.person.person.*
import adventureworks.production.product.*
import adventureworks.production.productcategory.*
import adventureworks.production.productmodel.*
import adventureworks.production.productsubcategory.*
import adventureworks.production.unitmeasure.*
import adventureworks.public.{Name, NameStyle}
import adventureworks.userdefined.FirstName
import org.junit.Assert.*
import org.junit.Test

import java.time.LocalDateTime
import java.util.UUID

class CompositeIdsTest extends SnapshotTest {

  @Test
  def testCompositeIdOperations(): Unit = {
    WithConnection {
      val repo = ProductcosthistoryRepoImpl()
      val unitmeasureRepo = UnitmeasureRepoImpl()
      val productcategoryRepo = ProductcategoryRepoImpl()
      val productsubcategoryRepo = ProductsubcategoryRepoImpl()
      val productmodelRepo = ProductmodelRepoImpl()
      val productRepo = ProductRepoImpl()

      // Setup unitmeasure
      val unitmeasure = unitmeasureRepo.insert(
        UnitmeasureRowUnsaved(UnitmeasureId("kgg"), Name("Kilograms"))
      )

      // Setup product category
      val productCategory = productcategoryRepo.insert(
        ProductcategoryRowUnsaved(Name("Test Category"))
      )

      // Setup product subcategory
      val productSubcategory = productsubcategoryRepo.insert(
        ProductsubcategoryRowUnsaved(productCategory.productcategoryid, Name("Test Subcategory"))
      )

      // Setup product model
      val productModel = productmodelRepo.insert(ProductmodelRowUnsaved(Name("Test Model")))

      val now = DbNow.localDateTime()

      // Setup product
      val product = productRepo.insert(
        ProductRowUnsaved(
          name = Name("Test Product"),
          productnumber = "TEST-001",
          safetystocklevel = 1,
          reorderpoint = 1,
          standardcost = BigDecimal(1),
          listprice = BigDecimal(1),
          daystomanufacture = 10,
          sellstartdate = now
        ).copy(
          sizeunitmeasurecode = Some(unitmeasure.unitmeasurecode),
          weightunitmeasurecode = Some(unitmeasure.unitmeasurecode),
          productsubcategoryid = Some(productSubcategory.productsubcategoryid),
          productmodelid = Some(productModel.productmodelid)
        )
      )

      // Create product cost history records using correct constructor order
      val ph1 = repo.insert(
        ProductcosthistoryRowUnsaved(
          productid = product.productid,
          startdate = now,
          enddate = Some(now.plusDays(1)),
          standardcost = BigDecimal(1)
        )
      )
      val ph2 = repo.insert(
        ProductcosthistoryRowUnsaved(
          productid = product.productid,
          startdate = now.plusDays(1),
          enddate = Some(now.plusDays(2)),
          standardcost = BigDecimal(1)
        )
      )
      val ph3 = repo.insert(
        ProductcosthistoryRowUnsaved(
          productid = product.productid,
          startdate = now.plusDays(2),
          enddate = Some(now.plusDays(3)),
          standardcost = BigDecimal(1)
        )
      )

      // Test composite ID
      assertNotNull(ph1.compositeId)
      assertEquals(product.productid, ph1.compositeId.productid)

      // Test selectByIds with composite IDs
      val wanted = Array(
        ph1.compositeId,
        ph2.compositeId,
        ProductcosthistoryId(ProductId(9999), ph3.compositeId.startdate)
      )

      val selected = repo.selectByIds(wanted).map(_.compositeId).toSet
      assertEquals(Set(ph1.compositeId, ph2.compositeId), selected)

      // Test deleteByIds
      assertEquals(2, repo.deleteByIds(wanted))

      // Verify remaining
      val remaining = repo.selectAll.map(_.compositeId).toList
      assertEquals(List(ph3.compositeId), remaining)
    }
  }

  @Test
  def testEmailaddressCompositeIdDsl(): Unit = {
    WithConnection {
      val emailaddressRepo = EmailaddressRepoImpl()
      val businessentityRepo = BusinessentityRepoImpl()
      val personRepo = PersonRepoImpl()

      val now = LocalDateTime.of(2021, 1, 1, 0, 0)

      // Create business entities
      val businessentity1 = businessentityRepo.insert(
        BusinessentityRow(BusinessentityId(1), UUID.randomUUID(), now)
      )
      val _ = personRepo.insert(createPersonRow(businessentity1.businessentityid, 1, now))

      val businessentity2 = businessentityRepo.insert(
        BusinessentityRow(BusinessentityId(2), UUID.randomUUID(), now)
      )
      val _ = personRepo.insert(createPersonRow(businessentity2.businessentityid, 2, now))

      // Create email addresses for businessentity1
      val emailaddress1_1 = emailaddressRepo.insert(
        EmailaddressRow(businessentity1.businessentityid, 1, Some("a@b.c"), UUID.randomUUID(), now)
      )
      val emailaddress1_2 = emailaddressRepo.insert(
        EmailaddressRow(businessentity1.businessentityid, 2, Some("aa@bb.cc"), UUID.randomUUID(), now)
      )
      val emailaddress1_3 = emailaddressRepo.insert(
        EmailaddressRow(businessentity1.businessentityid, 3, Some("aaa@bbb.ccc"), UUID.randomUUID(), now)
      )

      // Test compositeIdIs DSL method
      val res1 = emailaddressRepo.select
        .where(e => e.compositeIdIs(emailaddress1_1.compositeId))
        .toList
      assertEquals(List(emailaddress1_1), res1)

      // Test compositeIdIn DSL method
      val query2 = emailaddressRepo.select
        .where(e => e.compositeIdIn(List(emailaddress1_2.compositeId, emailaddress1_3.compositeId)))
      val res2 = query2.toList
      compareFragment("query2", query2.sql())
      assertEquals(List(emailaddress1_2, emailaddress1_3), res2)
    }
  }

  private def createPersonRow(businessentityid: BusinessentityId, i: Int, now: LocalDateTime): PersonRow = {
    PersonRow(
      businessentityid,
      "SC",
      NameStyle(true),
      None,
      FirstName(s"first name $i"),
      None,
      Name(s"last name $i"),
      None,
      1,
      None,
      None,
      UUID.randomUUID(),
      now
    )
  }
}
