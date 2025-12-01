package adventureworks.production.product

import adventureworks.SnapshotTest
import adventureworks.WithConnection
import adventureworks.customtypes.*
import adventureworks.production.productcategory.*
import adventureworks.production.productmodel.*
import adventureworks.production.productsubcategory.*
import adventureworks.production.unitmeasure.*
import adventureworks.public.Flag
import adventureworks.public.Name
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import typo.dsl.Bijection
import typo.dsl.SqlExpr
import typo.runtime.PgTypes
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

/**
 * Tests for Product repository.
 */
class ProductTest : SnapshotTest() {

    private fun runTest(
        productRepo: ProductRepo,
        projectModelRepo: ProductmodelRepo,
        unitmeasureRepo: UnitmeasureRepo,
        productcategoryRepo: ProductcategoryRepo,
        productsubcategoryRepo: ProductsubcategoryRepo,
        isMock: Boolean
    ) {
        WithConnection.run { c ->
            // setup
            val unitmeasure = unitmeasureRepo.insert(
                UnitmeasureRowUnsaved(
                    UnitmeasureId("kgg"),
                    Name("name")
                ),
                c
            )
            val productCategory = productcategoryRepo.insert(
                ProductcategoryRowUnsaved(Name("name")),
                c
            )

            val productSubcategory = productsubcategoryRepo.insert(
                ProductsubcategoryRowUnsaved(
                    productCategory.productcategoryid,
                    Name("name")
                ),
                c
            )
            val productmodel = projectModelRepo.insert(
                ProductmodelRowUnsaved(Name("name"))
                    .copy(
                        catalogdescription = Optional.of(TypoXml("<xml/>")),
                        instructions = Optional.of(TypoXml("<instructions/>"))
                    ),
                c
            )

            val unsaved1 = ProductRowUnsaved(
                name = Name("name"),
                productnumber = "productnumber",
                safetystocklevel = TypoShort(16.toShort()),
                reorderpoint = TypoShort(18.toShort()),
                standardcost = BigDecimal.valueOf(20),
                listprice = BigDecimal.valueOf(22),
                daystomanufacture = 26,
                sellstartdate = TypoLocalDateTime(LocalDateTime.now().plusDays(1)),
                color = Optional.of("color"),
                size = Optional.of("size"),
                sizeunitmeasurecode = Optional.of(unitmeasure.unitmeasurecode),
                weightunitmeasurecode = Optional.of(unitmeasure.unitmeasurecode),
                weight = Optional.of(BigDecimal.valueOf(1.00)),
                productline = Optional.of("T "),
                `class` = Optional.of("H "),
                style = Optional.of("W "),
                productsubcategoryid = Optional.of(productSubcategory.productsubcategoryid),
                productmodelid = Optional.of(productmodel.productmodelid),
                sellenddate = Optional.of(TypoLocalDateTime(LocalDateTime.now().plusDays(10))),
                discontinueddate = Optional.of(TypoLocalDateTime(LocalDateTime.now().plusDays(100))),
                makeflag = Defaulted.Provided(Flag(true)),
                finishedgoodsflag = Defaulted.Provided(Flag(true)),
                rowguid = Defaulted.Provided(TypoUUID.randomUUID()),
                modifieddate = Defaulted.Provided(TypoLocalDateTime.now())
            )

            // insert and round trip check
            val saved1 = productRepo.insert(unsaved1, c)
            val saved2 = unsaved1.toRow(
                { saved1.productid },
                { Flag(true) },
                { Flag(false) },
                { TypoUUID.randomUUID() },
                { TypoLocalDateTime.now() }
            )
            // note: saved1 and saved2 won't be equal due to different generated defaults
            assertNotNull(saved2)

            // check field values
            val newModifiedDate = TypoLocalDateTime(saved1.modifieddate.value.minusDays(1))
            val updatedOpt1 = productRepo.update(saved1.copy(modifieddate = newModifiedDate), c)
            assertTrue(updatedOpt1)
            val afterUpdate = productRepo.selectById(saved1.productid, c)
            assertTrue(afterUpdate.isPresent)
            assertEquals(newModifiedDate, afterUpdate.get().modifieddate)

            val saved3 = productRepo.selectAll(c)[0]
            assertEquals(newModifiedDate, saved3.modifieddate)

            val updatedOpt2 = productRepo.update(saved3.copy(size = Optional.empty()), c)
            assertTrue(updatedOpt2)
            val afterUpdate2 = productRepo.selectById(saved3.productid, c)
            assertTrue(afterUpdate2.isPresent)
            assertTrue(afterUpdate2.get().size.isEmpty)

            // Test DSL queries
            val query0 = productRepo.select()
                .joinFk({ p -> p.fkProductmodel() }, projectModelRepo.select())
                .joinFk({ p_pm -> p_pm._1().fkProductsubcategory() }, productsubcategoryRepo.select())
                .joinFk({ p_pm_ps -> p_pm_ps._2().fkProductcategory() }, productcategoryRepo.select())
            compareFragment("query0", query0.sql())
            query0.toList(c).forEach { println(it) }

            val query = productRepo.select()
                .where { p -> p.`class`().isEqual("H ") }
                .where { p -> p.daystomanufacture().greaterThan(25).or(p.daystomanufacture().lessThanOrEqual(0), Bijection.asBool()) }
                .where { p -> p.productline().isEqual("foo") }
                .join(unitmeasureRepo.select().where { um -> um.name().like("name%", Name.bijection) })
                .on { p_um -> p_um._1().sizeunitmeasurecode().isEqual(p_um._2().unitmeasurecode()) }
                .join(projectModelRepo.select())
                .leftOn { p_um_pm -> p_um_pm._1()._1().productmodelid().isEqual(p_um_pm._2().productmodelid()) }
                .where { p_um_pm -> p_um_pm._1()._1().productmodelid().isEqual(p_um_pm._2().productmodelid()) }
                .orderBy { p_um_pm -> p_um_pm._1()._1().productmodelid().asc() }
                .orderBy { p_um_pm -> p_um_pm._2().name().desc().withNullsFirst() }

            compareFragment("query", query.sql())
            println(query.toList(c))

            val leftJoined = productRepo.select()
                .join(projectModelRepo.select())
                .leftOn { p_pm -> p_pm._1().productmodelid().isEqual(p_pm._2().productmodelid()) }

            compareFragment("leftJoined", leftJoined.sql())
            leftJoined.toList(c).forEach { println(it) }

            val sellStartDate = TypoLocalDateTime.now()
            // Note: The mock does not support string function evaluation (reverse, upper, substring)
            // or numeric operations (plus). These are only evaluated in SQL.
            if (!isMock) {
                val update = productRepo.update()
                    .setComputedValue({ p -> p.name() }) { p -> p.reverse(Name.bijection).upper(Name.bijection).stringAppend(SqlExpr.ConstReq(Name("flaff"), Name.pgType), Name.bijection).substring(SqlExpr.ConstReq(2, PgTypes.int4), SqlExpr.ConstReq(4, PgTypes.int4), Name.bijection) }
                    .setValue({ p -> p.listprice() }, BigDecimal.valueOf(2))
                    .setComputedValue({ p -> p.reorderpoint() }) { p -> p.plus(TypoShort(22.toShort())) }
                    .setComputedValue({ p -> p.sizeunitmeasurecode() }) { _ -> SqlExpr.ConstOpt(Optional.of(unitmeasure.unitmeasurecode), UnitmeasureId.pgType) }
                    .setComputedValue({ p -> p.sellstartdate() }) { _ -> SqlExpr.ConstReq(sellStartDate, TypoLocalDateTime.pgType) }
                    .where { p -> p.productid().isEqual(saved1.productid) }

                compareFragment("updateReturning", update.sql())
                val updatedRows = update.executeReturning(c)
                assertEquals(1, updatedRows.size)
                val updated = updatedRows[0]
                assertEquals(Name("MANf"), updated.name)
                assertEquals(BigDecimal.valueOf(2), updated.listprice)
                assertEquals(TypoShort(40.toShort()), updated.reorderpoint)
                assertEquals(sellStartDate, updated.sellstartdate)
            }

            val q = productRepo.select()
                .where { p -> p.name().like("foo%", Name.bijection).not(Bijection.asBool()) }
                .where { p -> p.name().underlying(Name.bijection).stringAppend(p.color(), Bijection.identity()).like("foo%", Bijection.identity()).not(Bijection.asBool()) }
                .where { p -> p.daystomanufacture().greaterThan(0) }
                .where { p -> p.modifieddate().lessThan(TypoLocalDateTime.now()) }
                .join(projectModelRepo.select().where { pm -> pm.modifieddate().lessThan(TypoLocalDateTime.now()) })
                .on { p_pm -> p_pm._1().productmodelid().isEqual(p_pm._2().productmodelid()) }
                .where { p_pm -> p_pm._2().instructions().isNull().not(Bijection.asBool()) }

            compareFragment("q", q.sql())
            q.toList(c).forEach { println(it) }

            val q2 = productRepo.select()
                // select from id, arrays work
                .where { p -> p.productid().`in`(saved1.productid, ProductId(22)) }
                // call `length` function and compare result
                .where { p -> p.name().strLength(Name.bijection).greaterThan(SqlExpr.ConstReq(3, PgTypes.int4)) }
                // concatenate two strings (one of which is a wrapped type in scala) and compare result
                .where { p -> p.name().underlying(Name.bijection).stringAppend(p.color(), Bijection.identity()).like("foo%", Bijection.identity()).not(Bijection.asBool()) }
                // tracks nullability
                .where { p -> p.color().coalesce("yellow").isNotEqual(SqlExpr.ConstReq("blue", PgTypes.text)) }
                // compare dates
                .where { p -> p.modifieddate().lessThan(TypoLocalDateTime.now()) }
                // join, filter table we join with as well
                .join(projectModelRepo.select().where { pm -> pm.name().strLength(Name.bijection).greaterThan(SqlExpr.ConstReq(0, PgTypes.int4)) })
                .on { p_pm -> p_pm._1().productmodelid().isEqual(p_pm._2().productmodelid()) }
                // additional predicates for joined rows.
                .where { p_pm -> p_pm._2().name().underlying(Name.bijection).isNotEqual(SqlExpr.ConstReq("foo", PgTypes.text)) }
                // works arbitrarily deep
                .join(projectModelRepo.select().where { pm -> pm.name().strLength(Name.bijection).greaterThan(SqlExpr.ConstReq(0, PgTypes.int4)) })
                .leftOn { p_pm_pm2 -> p_pm_pm2._1()._1().productmodelid().isEqual(p_pm_pm2._2().productmodelid()).and(SqlExpr.ConstReq(false, PgTypes.bool), Bijection.asBool()) }
                // order by
                .orderBy { p_pm_pm2 -> p_pm_pm2._2().name().asc() }
                .orderBy { p_pm_pm2 -> p_pm_pm2._1()._1().color().desc().withNullsFirst() }

            compareFragment("q2", q2.sql())
            q2.toList(c).forEach { row ->
                println(row._1()._1())
                println(row._1()._2())
                println(row._2())
            }

            // delete
            val delete = productRepo.delete().where { p -> p.productid().isEqual(saved1.productid) }
            compareFragment("delete", delete.sql())

            delete.execute(c)

            assertEquals(0, productRepo.selectAll(c).size)
        }
    }

    @Test
    fun inMemory() {
        runTest(
            ProductRepoMock({ unsaved ->
                unsaved.toRow(
                    { ProductId(0) },
                    { Flag(true) },
                    { Flag(false) },
                    { TypoUUID.randomUUID() },
                    { TypoLocalDateTime.now() }
                )
            }),
            ProductmodelRepoMock({ unsaved ->
                unsaved.toRow(
                    { ProductmodelId(0) },
                    { TypoUUID.randomUUID() },
                    { TypoLocalDateTime.now() }
                )
            }),
            UnitmeasureRepoMock({ unsaved -> unsaved.toRow { TypoLocalDateTime.now() } }),
            ProductcategoryRepoMock({ unsaved ->
                unsaved.toRow(
                    { ProductcategoryId(0) },
                    { TypoUUID.randomUUID() },
                    { TypoLocalDateTime.now() }
                )
            }),
            ProductsubcategoryRepoMock({ unsaved ->
                unsaved.toRow(
                    { ProductsubcategoryId(0) },
                    { TypoUUID.randomUUID() },
                    { TypoLocalDateTime.now() }
                )
            }),
            true // isMock
        )
    }

    @Test
    fun pg() {
        runTest(
            ProductRepoImpl(),
            ProductmodelRepoImpl(),
            UnitmeasureRepoImpl(),
            ProductcategoryRepoImpl(),
            ProductsubcategoryRepoImpl(),
            false // isMock
        )
    }
}
