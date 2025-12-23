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
import doobie.free.connection.delay
import org.scalatest.Assertion

import java.time.LocalDateTime
import scala.util.Random

class CompositeIdsTest extends SnapshotTest {
  implicit class Foo(x: TypoLocalDateTime) {
    def map(f: LocalDateTime => LocalDateTime): TypoLocalDateTime = TypoLocalDateTime(f(x.value))
  }

  test("works") {
    val testInsert = new TestInsert(new Random(0), DomainInsert)
    val repo = new ProductcosthistoryRepoImpl()

    withConnection {
      for {
        unitmeasure <- testInsert.productionUnitmeasure(UnitmeasureId("kgg"))
        productCategory <- testInsert.productionProductcategory()
        productSubcategory <- testInsert.productionProductsubcategory(productCategory.productcategoryid)
        productModel <- testInsert.productionProductmodel(catalogdescription = Some(new TypoXml("<xml/>")), instructions = Some(new TypoXml("<instructions/>")))
        product <- testInsert.productionProduct(
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

        now = TypoLocalDateTime.now
        ph1 <- testInsert.productionProductcosthistory(product.productid, standardcost = BigDecimal(1), startdate = now, enddate = Some(now.map(_.plusDays(1))))
        ph2 <- testInsert.productionProductcosthistory(product.productid, standardcost = BigDecimal(1), startdate = now.map(_.plusDays(1)), enddate = Some(now.map(_.plusDays(2))))
        ph3 <- testInsert.productionProductcosthistory(product.productid, standardcost = BigDecimal(1), startdate = now.map(_.plusDays(2)), enddate = Some(now.map(_.plusDays(3))))
        wanted = Array(ph1.compositeId, ph2.compositeId, ph3.compositeId.copy(productid = ProductId(9999)))
        found <- repo.selectByIds(wanted).compile.toList
        _ <- delay(assert(found.map(_.compositeId).toSet === Set(ph1.compositeId, ph2.compositeId)))
        deleted <- repo.deleteByIds(wanted)
        _ <- delay(assert(deleted === 2))
        all <- repo.selectAll.compile.toList
        _ <- delay(assert(all.map(_.compositeId) === List(ph3.compositeId)))
      } yield true
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

    withConnection {
      for {
        businessentity1 <- businessentityRepo.insert(BusinessentityRow(BusinessentityId(1), TypoUUID.randomUUID, TypoLocalDateTime(now)))
        _ <- personRepo.insert(personRow(businessentity1.businessentityid, 1))
        businessentity2 <- businessentityRepo.insert(BusinessentityRow(BusinessentityId(2), TypoUUID.randomUUID, TypoLocalDateTime(now)))
        _ <- personRepo.insert(personRow(businessentity2.businessentityid, 2))
        emailaddress1_1 <- emailaddressRepo.insert(EmailaddressRow(businessentity1.businessentityid, 1, Some("a@b.c"), TypoUUID.randomUUID, TypoLocalDateTime(now)))
        emailaddress1_2 <- emailaddressRepo.insert(EmailaddressRow(businessentity1.businessentityid, 2, Some("aa@bb.cc"), TypoUUID.randomUUID, TypoLocalDateTime(now)))
        emailaddress1_3 <- emailaddressRepo.insert(EmailaddressRow(businessentity1.businessentityid, 3, Some("aaa@bbb.ccc"), TypoUUID.randomUUID, TypoLocalDateTime(now)))
        _ <- emailaddressRepo.insert(EmailaddressRow(businessentity2.businessentityid, 1, Some("A@B.C"), TypoUUID.randomUUID, TypoLocalDateTime(now)))
        _ <- emailaddressRepo.insert(EmailaddressRow(businessentity2.businessentityid, 2, Some("AA@BB.CC"), TypoUUID.randomUUID, TypoLocalDateTime(now)))
        _ <- emailaddressRepo.insert(EmailaddressRow(businessentity2.businessentityid, 3, Some("AAA@BBB.CCC"), TypoUUID.randomUUID, TypoLocalDateTime(now)))
        res1 <- emailaddressRepo.select.where(_.compositeIdIs(emailaddress1_1.compositeId)).toList
        _ <- delay(assert(res1 === List(emailaddress1_1)))
        query2 = emailaddressRepo.select
          .where(
            _.compositeIdIn(
              Array(emailaddress1_2.compositeId, emailaddress1_3.compositeId)
            )
          )
        res2 <- query2.toList
        _ <- delay(compareFragment("query2")(query2.sql))
      } yield assert(res2 === List(emailaddress1_2, emailaddress1_3))
    }
  }

  test("dsl pg") {
    testDsl(new EmailaddressRepoImpl(), new BusinessentityRepoImpl(), new PersonRepoImpl())
  }

  test("dsl in-memory") {
    testDsl(new EmailaddressRepoMock(_.toRow(???, ???, ???)), new BusinessentityRepoMock(_.toRow(???, ???, ???)), new PersonRepoMock(_.toRow(???, ???, ???, ???)))
  }
}
