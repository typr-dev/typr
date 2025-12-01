package adventureworks.production.productcosthistory

import adventureworks.SnapshotTest
import adventureworks.WithConnection
import adventureworks.customtypes.TypoLocalDateTime
import adventureworks.customtypes.TypoShort
import adventureworks.customtypes.TypoUUID
import adventureworks.person.businessentity.*
import adventureworks.person.emailaddress.*
import adventureworks.person.person.*
import adventureworks.production.product.ProductId
import adventureworks.production.product.ProductRepoImpl
import adventureworks.production.product.ProductRowUnsaved
import adventureworks.production.productcategory.ProductcategoryRepoImpl
import adventureworks.production.productcategory.ProductcategoryRowUnsaved
import adventureworks.production.productmodel.ProductmodelRepoImpl
import adventureworks.production.productmodel.ProductmodelRowUnsaved
import adventureworks.production.productsubcategory.ProductsubcategoryRepoImpl
import adventureworks.production.productsubcategory.ProductsubcategoryRowUnsaved
import adventureworks.production.unitmeasure.UnitmeasureId
import adventureworks.production.unitmeasure.UnitmeasureRepoImpl
import adventureworks.production.unitmeasure.UnitmeasureRow
import adventureworks.public.Name
import adventureworks.public.NameStyle
import adventureworks.userdefined.FirstName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

/**
 * Tests for composite IDs functionality.
 */
class CompositeIdsTest : SnapshotTest() {
    private val repo = ProductcosthistoryRepoImpl()
    private val unitmeasureRepo = UnitmeasureRepoImpl()
    private val productcategoryRepo = ProductcategoryRepoImpl()
    private val productsubcategoryRepo = ProductsubcategoryRepoImpl()
    private val productmodelRepo = ProductmodelRepoImpl()
    private val productRepo = ProductRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            // Setup unitmeasure
            val unitmeasure = unitmeasureRepo.insert(
                UnitmeasureRow(UnitmeasureId("kgg"), Name("Kilograms"), TypoLocalDateTime.now()),
                c
            )

            // Setup product category - use short ctor
            val productCategory = productcategoryRepo.insert(
                ProductcategoryRowUnsaved(Name("Test Category")), c
            )

            // Setup product subcategory - use short ctor
            val productSubcategory = productsubcategoryRepo.insert(
                ProductsubcategoryRowUnsaved(productCategory.productcategoryid, Name("Test Subcategory")), c
            )

            // Setup product model - use short ctor
            val productModel = productmodelRepo.insert(
                ProductmodelRowUnsaved(Name("Test Model")), c
            )

            val now = TypoLocalDateTime.now()

            // Setup product - use named params + copy
            val product = productRepo.insert(
                ProductRowUnsaved(
                    name = Name("Test Product"),
                    productnumber = "TEST-001",
                    safetystocklevel = TypoShort(1.toShort()),
                    reorderpoint = TypoShort(1.toShort()),
                    standardcost = BigDecimal.ONE,
                    listprice = BigDecimal.ONE,
                    daystomanufacture = 10,
                    sellstartdate = now,
                    sizeunitmeasurecode = Optional.of(unitmeasure.unitmeasurecode),
                    weightunitmeasurecode = Optional.of(unitmeasure.unitmeasurecode),
                    productsubcategoryid = Optional.of(productSubcategory.productsubcategoryid),
                    productmodelid = Optional.of(productModel.productmodelid)
                ),
                c
            )

            // Create product cost history records - use named params
            val ph1 = repo.insert(
                ProductcosthistoryRowUnsaved(
                    productid = product.productid,
                    startdate = now,
                    standardcost = BigDecimal.ONE,
                    enddate = Optional.of(TypoLocalDateTime.apply(now.value.plusDays(1)))
                ), c
            )
            val ph2 = repo.insert(
                ProductcosthistoryRowUnsaved(
                    productid = product.productid,
                    startdate = TypoLocalDateTime.apply(now.value.plusDays(1)),
                    standardcost = BigDecimal.ONE,
                    enddate = Optional.of(TypoLocalDateTime.apply(now.value.plusDays(2)))
                ), c
            )
            val ph3 = repo.insert(
                ProductcosthistoryRowUnsaved(
                    productid = product.productid,
                    startdate = TypoLocalDateTime.apply(now.value.plusDays(2)),
                    standardcost = BigDecimal.ONE,
                    enddate = Optional.of(TypoLocalDateTime.apply(now.value.plusDays(3)))
                ), c
            )

            val wanted = arrayOf(
                ph1.compositeId(),
                ph2.compositeId(),
                ProductcosthistoryId(ProductId(9999), ph3.compositeId().startdate)
            )

            val selected = repo.selectByIds(wanted, c)
                .map { it.compositeId() }
                .toSet()
            assertEquals(setOf(ph1.compositeId(), ph2.compositeId()), selected)

            assertEquals(2, repo.deleteByIds(wanted, c))

            val remaining = repo.selectAll(c)
                .map { it.compositeId() }
            assertEquals(listOf(ph3.compositeId()), remaining)
        }
    }

    private fun testDsl(
        emailaddressRepo: EmailaddressRepo,
        businessentityRepo: BusinessentityRepo,
        personRepo: PersonRepo
    ) {
        val now = LocalDateTime.of(2021, 1, 1, 0, 0)

        WithConnection.run { c ->
            // Create business entities
            val businessentity1 = businessentityRepo.insert(
                BusinessentityRow(BusinessentityId(1), TypoUUID.randomUUID(), TypoLocalDateTime(now)), c
            )
            personRepo.insert(personRow(businessentity1.businessentityid, 1, now), c)

            val businessentity2 = businessentityRepo.insert(
                BusinessentityRow(BusinessentityId(2), TypoUUID.randomUUID(), TypoLocalDateTime(now)), c
            )
            personRepo.insert(personRow(businessentity2.businessentityid, 2, now), c)

            // Create email addresses for businessentity1
            val emailaddress1_1 = emailaddressRepo.insert(
                EmailaddressRow(businessentity1.businessentityid, 1, Optional.of("a@b.c"), TypoUUID.randomUUID(), TypoLocalDateTime(now)), c
            )
            val emailaddress1_2 = emailaddressRepo.insert(
                EmailaddressRow(businessentity1.businessentityid, 2, Optional.of("aa@bb.cc"), TypoUUID.randomUUID(), TypoLocalDateTime(now)), c
            )
            val emailaddress1_3 = emailaddressRepo.insert(
                EmailaddressRow(businessentity1.businessentityid, 3, Optional.of("aaa@bbb.ccc"), TypoUUID.randomUUID(), TypoLocalDateTime(now)), c
            )

            // Create email addresses for businessentity2
            emailaddressRepo.insert(
                EmailaddressRow(businessentity2.businessentityid, 1, Optional.of("A@B.C"), TypoUUID.randomUUID(), TypoLocalDateTime(now)), c
            )
            emailaddressRepo.insert(
                EmailaddressRow(businessentity2.businessentityid, 2, Optional.of("AA@BB.CC"), TypoUUID.randomUUID(), TypoLocalDateTime(now)), c
            )
            emailaddressRepo.insert(
                EmailaddressRow(businessentity2.businessentityid, 3, Optional.of("AAA@BBB.CCC"), TypoUUID.randomUUID(), TypoLocalDateTime(now)), c
            )

            // Test compositeIdIs
            val res1 = emailaddressRepo.select()
                .where { e -> e.compositeIdIs(emailaddress1_1.compositeId()) }
                .toList(c)
            assertEquals(listOf(emailaddress1_1), res1)

            // Test compositeIdIn
            val query2 = emailaddressRepo.select()
                .where { e -> e.compositeIdIn(listOf(emailaddress1_2.compositeId(), emailaddress1_3.compositeId())) }
            val res2 = query2.toList(c)
            compareFragment("query2", query2.sql())
            assertEquals(listOf(emailaddress1_2, emailaddress1_3), res2)
        }
    }

    @Test
    fun dslPg() {
        testDsl(EmailaddressRepoImpl(), BusinessentityRepoImpl(), PersonRepoImpl())
    }

    @Test
    fun dslInMemory() {
        testDsl(
            EmailaddressRepoMock({ unsaved ->
                unsaved.toRow(
                    { 0 },  // emailaddressid
                    { TypoUUID.randomUUID() },
                    { TypoLocalDateTime.now() }
                )
            }),
            BusinessentityRepoMock({ unsaved ->
                unsaved.toRow(
                    { BusinessentityId(0) },  // businessentityid
                    { TypoUUID.randomUUID() },
                    { TypoLocalDateTime.now() }
                )
            }),
            PersonRepoMock({ unsaved ->
                unsaved.toRow(
                    { NameStyle(false) },  // namestyle
                    { 0 },  // emailpromotion
                    { TypoUUID.randomUUID() },
                    { TypoLocalDateTime.now() }
                )
            })
        )
    }

    private fun personRow(businessentityid: BusinessentityId, i: Int, now: LocalDateTime): PersonRow {
        return PersonRow(
            businessentityid,
            "SC",
            NameStyle(true),
            Optional.empty(),
            FirstName("first name $i"),
            Optional.empty(),
            Name("last name $i"),
            Optional.empty(),
            1,
            Optional.empty(),
            Optional.empty(),
            TypoUUID.randomUUID(),
            TypoLocalDateTime(now)
        )
    }
}
