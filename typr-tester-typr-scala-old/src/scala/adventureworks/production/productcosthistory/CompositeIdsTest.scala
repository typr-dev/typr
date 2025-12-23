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

import java.math.BigDecimal
import java.sql.Connection
import java.time.LocalDateTime
import java.util.{Optional, UUID, List as JList}
import scala.jdk.CollectionConverters.*

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
          safetystocklevel = java.lang.Short.valueOf(1.toShort),
          reorderpoint = java.lang.Short.valueOf(1.toShort),
          standardcost = BigDecimal.ONE,
          listprice = BigDecimal.ONE,
          daystomanufacture = Integer.valueOf(10),
          sellstartdate = now
        ).copy(
          sizeunitmeasurecode = Optional.of(unitmeasure.unitmeasurecode),
          weightunitmeasurecode = Optional.of(unitmeasure.unitmeasurecode),
          productsubcategoryid = Optional.of(productSubcategory.productsubcategoryid),
          productmodelid = Optional.of(productModel.productmodelid)
        )
      )

      // Create product cost history records using correct constructor order
      val ph1 = repo.insert(
        ProductcosthistoryRowUnsaved(
          productid = product.productid,
          startdate = now,
          enddate = Optional.of(now.plusDays(1)),
          standardcost = BigDecimal.ONE
        )
      )
      val ph2 = repo.insert(
        ProductcosthistoryRowUnsaved(
          productid = product.productid,
          startdate = now.plusDays(1),
          enddate = Optional.of(now.plusDays(2)),
          standardcost = BigDecimal.ONE
        )
      )
      val ph3 = repo.insert(
        ProductcosthistoryRowUnsaved(
          productid = product.productid,
          startdate = now.plusDays(2),
          enddate = Optional.of(now.plusDays(3)),
          standardcost = BigDecimal.ONE
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

      val selected = repo.selectByIds(wanted).asScala.map(_.compositeId).toSet
      assertEquals(Set(ph1.compositeId, ph2.compositeId), selected)

      // Test deleteByIds
      assertEquals(Integer.valueOf(2), repo.deleteByIds(wanted))

      // Verify remaining
      val remaining = repo.selectAll.asScala.map(_.compositeId).toList
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
        EmailaddressRow(businessentity1.businessentityid, 1, Optional.of("a@b.c"), UUID.randomUUID(), now)
      )
      val emailaddress1_2 = emailaddressRepo.insert(
        EmailaddressRow(businessentity1.businessentityid, 2, Optional.of("aa@bb.cc"), UUID.randomUUID(), now)
      )
      val emailaddress1_3 = emailaddressRepo.insert(
        EmailaddressRow(businessentity1.businessentityid, 3, Optional.of("aaa@bbb.ccc"), UUID.randomUUID(), now)
      )

      // Test compositeIdIs DSL method
      val res1 = emailaddressRepo.select
        .where(e => e.compositeIdIs(emailaddress1_1.compositeId))
        .toList(summon[Connection])
        .asScala
        .toList
      assertEquals(List(emailaddress1_1), res1)

      // Test compositeIdIn DSL method
      val query2 = emailaddressRepo.select
        .where(e => e.compositeIdIn(JList.of(emailaddress1_2.compositeId, emailaddress1_3.compositeId)))
      val res2 = query2.toList(summon[Connection]).asScala.toList
      compareFragment("query2", query2.sql())
      assertEquals(List(emailaddress1_2, emailaddress1_3), res2)
    }
  }

  private def createPersonRow(businessentityid: BusinessentityId, i: Int, now: LocalDateTime): PersonRow = {
    PersonRow(
      businessentityid,
      "SC",
      NameStyle(true),
      Optional.empty(),
      FirstName(s"first name $i"),
      Optional.empty(),
      Name(s"last name $i"),
      Optional.empty(),
      Integer.valueOf(1),
      Optional.empty(),
      Optional.empty(),
      UUID.randomUUID(),
      now
    )
  }
}
