package adventureworks.production.productcosthistory

import adventureworks.DbNow
import adventureworks.DomainInsert
import adventureworks.TestInsert
import adventureworks.WithConnection
import adventureworks.production.product.ProductId
import adventureworks.production.unitmeasure.UnitmeasureId
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Random

class CompositeIdsTest {
    private val testInsert = TestInsert(Random(0), DomainInsert)

    @Test
    fun testCompositeIdOperations() {
        WithConnection.run { c ->
            val repo = ProductcosthistoryRepoImpl()

            // Setup product with all dependencies using TestInsert
            val unitmeasure = testInsert.productionUnitmeasure(
                unitmeasurecode = UnitmeasureId("CM"),
                c = c
            )
            val productCategory = testInsert.productionProductcategory(c = c)
            val productSubcategory = testInsert.productionProductsubcategory(
                productcategoryid = productCategory.productcategoryid,
                c = c
            )
            val productModel = testInsert.productionProductmodel(c = c)

            val product = testInsert.productionProduct(
                productnumber = "TEST-001",
                safetystocklevel = 1,
                reorderpoint = 1,
                standardcost = BigDecimal.ONE,
                listprice = BigDecimal.ONE,
                daystomanufacture = 10,
                sellstartdate = LocalDateTime.now(),
                sizeunitmeasurecode = unitmeasure.unitmeasurecode,
                weightunitmeasurecode = unitmeasure.unitmeasurecode,
                productsubcategoryid = productSubcategory.productsubcategoryid,
                productmodelid = productModel.productmodelid,
                c = c
            )

            val now = DbNow.localDateTime()

            // Create product cost history records
            val ph1 = testInsert.productionProductcosthistory(
                productid = product.productid,
                startdate = now,
                enddate = now.plusDays(1),
                standardcost = BigDecimal.ONE,
                c = c
            )
            val ph2 = testInsert.productionProductcosthistory(
                productid = product.productid,
                startdate = now.plusDays(1),
                enddate = now.plusDays(2),
                standardcost = BigDecimal.ONE,
                c = c
            )
            val ph3 = testInsert.productionProductcosthistory(
                productid = product.productid,
                startdate = now.plusDays(2),
                enddate = now.plusDays(3),
                standardcost = BigDecimal.ONE,
                c = c
            )

            // Test composite ID
            assertNotNull(ph1.compositeId())
            assertEquals(product.productid, ph1.compositeId().productid)

            // Test selectByIds with composite IDs
            val wanted = arrayOf(
                ph1.compositeId(),
                ph2.compositeId(),
                ProductcosthistoryId(ProductId(9999), ph3.compositeId().startdate)
            )

            val selected = repo.selectByIds(wanted, c).map { it.compositeId() }.toSet()
            assertEquals(setOf(ph1.compositeId(), ph2.compositeId()), selected)

            // Test deleteByIds
            assertEquals(2, repo.deleteByIds(wanted, c))

            // Verify remaining
            val remaining = repo.selectAll(c).map { it.compositeId() }
            assertEquals(listOf(ph3.compositeId()), remaining)
        }
    }
}
