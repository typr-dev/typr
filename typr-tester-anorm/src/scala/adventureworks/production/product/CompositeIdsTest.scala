package adventureworks.production.product

import adventureworks.customtypes.{TypoLocalDateTime, TypoShort, TypoUUID, TypoXml}
import adventureworks.person.businessentity.*
import adventureworks.person.emailaddress.{EmailaddressRepo, EmailaddressRepoImpl, EmailaddressRepoMock, EmailaddressRow}
import adventureworks.person.person.{PersonRepo, PersonRepoImpl, PersonRepoMock, PersonRow}
import adventureworks.production.productcosthistory.*
import adventureworks.production.unitmeasure.UnitmeasureId
import adventureworks.public.{Name, NameStyle}
import adventureworks.userdefined.FirstName
import adventureworks.{DomainInsert, SnapshotTest, TestInsert, withConnection}
import org.scalatest.Assertion

import java.time.LocalDateTime
import scala.annotation.nowarn
import scala.util.Random

class CompositeIdsTest extends SnapshotTest {
  implicit class Foo(x: TypoLocalDateTime) {
    def map(f: LocalDateTime => LocalDateTime): TypoLocalDateTime = TypoLocalDateTime(f(x.value))
  }

  test("works") {
    val testInsert = new TestInsert(new Random(0), DomainInsert)

    withConnection { implicit c =>
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

      val now = TypoLocalDateTime.now
      val ph1 = testInsert.productionProductcosthistory(product.productid, standardcost = BigDecimal(1), startdate = now, enddate = Some(now.map(_.plusDays(1))))
      val ph2 = testInsert.productionProductcosthistory(product.productid, standardcost = BigDecimal(1), startdate = now.map(_.plusDays(1)), enddate = Some(now.map(_.plusDays(2))))
      val ph3 = testInsert.productionProductcosthistory(product.productid, standardcost = BigDecimal(1), startdate = now.map(_.plusDays(2)), enddate = Some(now.map(_.plusDays(3))))
      List(ph1.compositeId, ph2.compositeId, ph3.compositeId) foreach println
      val wanted = Array(ph1.compositeId, ph2.compositeId, ph3.compositeId.copy(productid = ProductId(9999)))

      val repo = new ProductcosthistoryRepoImpl()
      assert(repo.selectByIds(wanted).map(_.compositeId).toSet === Set(ph1.compositeId, ph2.compositeId)): @nowarn
      assert(repo.deleteByIds(wanted) === 2): @nowarn
      assert(repo.selectAll.map(_.compositeId) === List(ph3.compositeId))
    }
  }

  def testDsl(
      emailaddressRepo: EmailaddressRepo,
      businessentityRepo: BusinessentityRepo,
      personRepo: PersonRepo
  ): Assertion = {
    val now = LocalDateTime.of(2021, 1, 1, 0, 0)

    def personRow(businessentityid: BusinessentityId, i: Int) =
      new PersonRow(
        businessentityid = businessentityid,
        persontype = "SC",
        namestyle = NameStyle(true),
        title = None,
        firstname = FirstName(s"first name $i"),
        middlename = None,
        lastname = Name(s"last name $i"),
        suffix = None,
        emailpromotion = 1,
        additionalcontactinfo = None,
        demographics = None,
        rowguid = TypoUUID.randomUUID,
        modifieddate = TypoLocalDateTime(now)
      )

    withConnection { implicit c =>
      val businessentity1 = businessentityRepo.insert(BusinessentityRow(BusinessentityId(1), TypoUUID.randomUUID, TypoLocalDateTime(now)))
      personRepo.insert(personRow(businessentity1.businessentityid, 1)): @nowarn
      val businessentity2 = businessentityRepo.insert(BusinessentityRow(BusinessentityId(2), TypoUUID.randomUUID, TypoLocalDateTime(now)))
      personRepo.insert(personRow(businessentity2.businessentityid, 2)): @nowarn
      val emailaddress1_1 = emailaddressRepo.insert(EmailaddressRow(businessentity1.businessentityid, 1, Some("a@b.c"), TypoUUID.randomUUID, TypoLocalDateTime(now)))
      val emailaddress1_2 = emailaddressRepo.insert(EmailaddressRow(businessentity1.businessentityid, 2, Some("aa@bb.cc"), TypoUUID.randomUUID, TypoLocalDateTime(now)))
      val emailaddress1_3 = emailaddressRepo.insert(EmailaddressRow(businessentity1.businessentityid, 3, Some("aaa@bbb.ccc"), TypoUUID.randomUUID, TypoLocalDateTime(now)))
      emailaddressRepo.insert(EmailaddressRow(businessentity2.businessentityid, 1, Some("A@B.C"), TypoUUID.randomUUID, TypoLocalDateTime(now))): @nowarn
      emailaddressRepo.insert(EmailaddressRow(businessentity2.businessentityid, 2, Some("AA@BB.CC"), TypoUUID.randomUUID, TypoLocalDateTime(now))): @nowarn
      emailaddressRepo.insert(EmailaddressRow(businessentity2.businessentityid, 3, Some("AAA@BBB.CCC"), TypoUUID.randomUUID, TypoLocalDateTime(now))): @nowarn
      val res1 = emailaddressRepo.select.where(_.compositeIdIs(emailaddress1_1.compositeId)).toList
      assert(res1 === List(emailaddress1_1)): @nowarn
      val query2 = emailaddressRepo.select
        .where(
          _.compositeIdIn(
            Array(emailaddress1_2.compositeId, emailaddress1_3.compositeId)
          )
        )
      val res2 = query2.toList
      compareFragment("query2")(query2.sql)
      assert(res2 === List(emailaddress1_2, emailaddress1_3))
    }
  }

  test("dsl pg") {
    testDsl(new EmailaddressRepoImpl(), new BusinessentityRepoImpl(), new PersonRepoImpl())
  }

  test("dsl in-memory") {
    testDsl(new EmailaddressRepoMock(_.toRow(???, ???, ???)), new BusinessentityRepoMock(_.toRow(???, ???, ???)), new PersonRepoMock(_.toRow(???, ???, ???, ???)))
  }
}
