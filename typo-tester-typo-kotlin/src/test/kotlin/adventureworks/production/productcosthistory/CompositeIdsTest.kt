package adventureworks.production.productcosthistory

import adventureworks.DbNow
import adventureworks.WithConnection
import adventureworks.production.product.*
import adventureworks.production.productcategory.*
import adventureworks.production.productmodel.*
import adventureworks.production.productsubcategory.*
import adventureworks.production.unitmeasure.*
import adventureworks.public.Name
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class CompositeIdsTest {

    @Test
    fun testCompositeIdOperations() {
        WithConnection.run { c ->
            val repo = ProductcosthistoryRepoImpl()
            val unitmeasureRepo = UnitmeasureRepoImpl()
            val productcategoryRepo = ProductcategoryRepoImpl()
            val productsubcategoryRepo = ProductsubcategoryRepoImpl()
            val productmodelRepo = ProductmodelRepoImpl()
            val productRepo = ProductRepoImpl()

            // Setup unitmeasure
            val unitmeasure = unitmeasureRepo.insert(
                UnitmeasureRowUnsaved(UnitmeasureId("kgg"), Name("Kilograms")),
                c
            )

            // Setup product category
            val productCategory = productcategoryRepo.insert(
                ProductcategoryRowUnsaved(Name("Test Category")),
                c
            )

            // Setup product subcategory
            val productSubcategory = productsubcategoryRepo.insert(
                ProductsubcategoryRowUnsaved(productCategory.productcategoryid, Name("Test Subcategory")),
                c
            )

            // Setup product model
            val productModel = productmodelRepo.insert(
                ProductmodelRowUnsaved(Name("Test Model")),
                c
            )

            val now = DbNow.localDateTime()

            // Setup product
            val product = productRepo.insert(
                ProductRowUnsaved(
                    name = Name("Test Product"),
                    productnumber = "TEST-001",
                    safetystocklevel = 1,
                    reorderpoint = 1,
                    standardcost = BigDecimal.ONE,
                    listprice = BigDecimal.ONE,
                    daystomanufacture = 10,
                    sellstartdate = now,
                    sizeunitmeasurecode = unitmeasure.unitmeasurecode,
                    weightunitmeasurecode = unitmeasure.unitmeasurecode,
                    productsubcategoryid = productSubcategory.productsubcategoryid,
                    productmodelid = productModel.productmodelid
                ),
                c
            )

            // Create product cost history records
            val ph1 = repo.insert(
                ProductcosthistoryRowUnsaved(
                    productid = product.productid,
                    startdate = now,
                    enddate = now.plusDays(1),
                    standardcost = BigDecimal.ONE
                ),
                c
            )
            val ph2 = repo.insert(
                ProductcosthistoryRowUnsaved(
                    productid = product.productid,
                    startdate = now.plusDays(1),
                    enddate = now.plusDays(2),
                    standardcost = BigDecimal.ONE
                ),
                c
            )
            val ph3 = repo.insert(
                ProductcosthistoryRowUnsaved(
                    productid = product.productid,
                    startdate = now.plusDays(2),
                    enddate = now.plusDays(3),
                    standardcost = BigDecimal.ONE
                ),
                c
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
