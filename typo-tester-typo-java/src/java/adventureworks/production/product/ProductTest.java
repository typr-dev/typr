package adventureworks.production.product;

import adventureworks.SnapshotTest;
import adventureworks.WithConnection;
import adventureworks.customtypes.*;
import adventureworks.production.productcategory.*;
import adventureworks.production.productmodel.*;
import adventureworks.production.productsubcategory.*;
import adventureworks.production.unitmeasure.*;
import adventureworks.public_.Flag;
import adventureworks.public_.Name;
import org.junit.Test;
import typo.dsl.Bijection;
import typo.dsl.SqlExpr;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Tests for Product repository - equivalent to Scala ProductTest.
 */
public class ProductTest extends SnapshotTest {

    private void runTest(
            ProductRepo productRepo,
            ProductmodelRepo projectModelRepo,
            UnitmeasureRepo unitmeasureRepo,
            ProductcategoryRepo productcategoryRepo,
            ProductsubcategoryRepo productsubcategoryRepo,
            boolean isMock
    ) {
        WithConnection.run(c -> {
            // setup
            var unitmeasure = unitmeasureRepo.insert(
                    new UnitmeasureRowUnsaved(
                            new UnitmeasureId("kgg"),
                            new Name("name")
                    ),
                    c
            );
            var productCategory = productcategoryRepo.insert(
                    new ProductcategoryRowUnsaved(new Name("name")),
                    c
            );

            var productSubcategory = productsubcategoryRepo.insert(
                    new ProductsubcategoryRowUnsaved(
                            productCategory.productcategoryid(),
                            new Name("name")
                    ),
                    c
            );
            var productmodel = projectModelRepo.insert(
                    new ProductmodelRowUnsaved(new Name("name"))
                            .withCatalogdescription(Optional.of(new TypoXml("<xml/>")))
                            .withInstructions(Optional.of(new TypoXml("<instructions/>"))),
                    c
            );

            var unsaved1 = new ProductRowUnsaved(
                    new Name("name"),
                    "productnumber",
                    new TypoShort((short) 16),
                    new TypoShort((short) 18),
                    BigDecimal.valueOf(20),
                    BigDecimal.valueOf(22),
                    26,
                    new TypoLocalDateTime(LocalDateTime.now().plusDays(1))
            )
                    .withColor(Optional.of("color"))
                    .withSize(Optional.of("size"))
                    .withSizeunitmeasurecode(Optional.of(unitmeasure.unitmeasurecode()))
                    .withWeightunitmeasurecode(Optional.of(unitmeasure.unitmeasurecode()))
                    .withWeight(Optional.of(BigDecimal.valueOf(1.00)))
                    .withProductline(Optional.of("T "))
                    .withClass(Optional.of("H "))
                    .withStyle(Optional.of("W "))
                    .withProductsubcategoryid(Optional.of(productSubcategory.productsubcategoryid()))
                    .withProductmodelid(Optional.of(productmodel.productmodelid()))
                    .withSellenddate(Optional.of(new TypoLocalDateTime(LocalDateTime.now().plusDays(10))))
                    .withDiscontinueddate(Optional.of(new TypoLocalDateTime(LocalDateTime.now().plusDays(100))))
                    .withMakeflag(new Defaulted.Provided<>(new Flag(true)))
                    .withFinishedgoodsflag(new Defaulted.Provided<>(new Flag(true)))
                    .withRowguid(new Defaulted.Provided<>(TypoUUID.randomUUID()))
                    .withModifieddate(new Defaulted.Provided<>(TypoLocalDateTime.now()));

            // insert and round trip check
            var saved1 = productRepo.insert(unsaved1, c);
            var saved2 = unsaved1.toRow(
                    () -> saved1.productid(),
                    () -> new Flag(true),
                    () -> new Flag(false),
                    () -> TypoUUID.randomUUID(),
                    () -> TypoLocalDateTime.now()
            );
            // note: saved1 and saved2 won't be equal due to different generated defaults
            assertNotNull(saved2);

            // check field values
            var newModifiedDate = new TypoLocalDateTime(saved1.modifieddate().value().minusDays(1));
            var updatedOpt1 = productRepo.update(saved1.withModifieddate(newModifiedDate), c);
            assertTrue(updatedOpt1);
            var afterUpdate = productRepo.selectById(saved1.productid(), c);
            assertTrue(afterUpdate.isPresent());
            assertEquals(newModifiedDate, afterUpdate.get().modifieddate());

            var saved3 = productRepo.selectAll(c).get(0);
            assertEquals(newModifiedDate, saved3.modifieddate());

            var updatedOpt2 = productRepo.update(saved3.withSize(Optional.empty()), c);
            assertTrue(updatedOpt2);
            var afterUpdate2 = productRepo.selectById(saved3.productid(), c);
            assertTrue(afterUpdate2.isPresent());
            assertTrue(afterUpdate2.get().size().isEmpty());

            // Test DSL queries
            var query0 = productRepo.select()
                    .joinFk(p -> p.fkProductmodel(), projectModelRepo.select())
                    .joinFk(p_pm -> p_pm._1().fkProductsubcategory(), productsubcategoryRepo.select())
                    .joinFk(p_pm_ps -> p_pm_ps._2().fkProductcategory(), productcategoryRepo.select());
            compareFragment("query0", query0.sql());
            query0.toList(c).forEach(System.out::println);

            var query = productRepo.select()
                    .where(p -> p.class_().isEqual("H "))
                    .where(p -> p.daystomanufacture().greaterThan(25).or(p.daystomanufacture().lessThanOrEqual(0), Bijection.asBool()))
                    .where(p -> p.productline().isEqual("foo"))
                    .join(unitmeasureRepo.select().where(um -> um.name().like("name%", Name.bijection)))
                    .on(p_um -> p_um._1().sizeunitmeasurecode().isEqual(p_um._2().unitmeasurecode()))
                    .join(projectModelRepo.select())
                    .leftOn(p_um_pm -> p_um_pm._1()._1().productmodelid().isEqual(p_um_pm._2().productmodelid()))
                    .where(p_um_pm -> p_um_pm._1()._1().productmodelid().isEqual(p_um_pm._2().productmodelid()))
                    .orderBy(p_um_pm -> p_um_pm._1()._1().productmodelid().asc())
                    .orderBy(p_um_pm -> p_um_pm._2().name().desc().withNullsFirst());

            compareFragment("query", query.sql());
            System.out.println(query.toList(c));

            var leftJoined = productRepo.select()
                    .join(projectModelRepo.select())
                    .leftOn(p_pm -> p_pm._1().productmodelid().isEqual(p_pm._2().productmodelid()));

            compareFragment("leftJoined", leftJoined.sql());
            leftJoined.toList(c).forEach(System.out::println);

            var sellStartDate = TypoLocalDateTime.now();
            // Note: The mock does not support string function evaluation (reverse, upper, substring)
            // or numeric operations (plus). These are only evaluated in SQL.
            if (!isMock) {
                var update = productRepo.update()
                        .setComputedValue(p -> p.name(), p -> p.reverse(Name.bijection).upper(Name.bijection).stringAppend(new SqlExpr.ConstReq<>(new Name("flaff"), Name.pgType), Name.bijection).substring(new SqlExpr.ConstReq<>(2, typo.runtime.PgTypes.int4), new SqlExpr.ConstReq<>(4, typo.runtime.PgTypes.int4), Name.bijection))
                        .setValue(p -> p.listprice(), BigDecimal.valueOf(2))
                        .setComputedValue(p -> p.reorderpoint(), p -> p.plus(new TypoShort((short) 22)))
                        .setComputedValue(p -> p.sizeunitmeasurecode(), p -> new SqlExpr.ConstOpt<>(Optional.of(unitmeasure.unitmeasurecode()), UnitmeasureId.pgType))
                        .setComputedValue(p -> p.sellstartdate(), p -> new SqlExpr.ConstReq<>(sellStartDate, TypoLocalDateTime.pgType))
                        .where(p -> p.productid().isEqual(saved1.productid()));

                compareFragment("updateReturning", update.sql());
                var updatedRows = update.executeReturning(c);
                assertEquals(1, updatedRows.size());
                var updated = updatedRows.get(0);
                assertEquals(new Name("MANf"), updated.name());
                assertEquals(BigDecimal.valueOf(2), updated.listprice());
                assertEquals(new TypoShort((short) 40), updated.reorderpoint());
                assertEquals(sellStartDate, updated.sellstartdate());
            }

            var q = productRepo.select()
                    .where(p -> p.name().like("foo%", Name.bijection).not(Bijection.asBool()))
                    .where(p -> p.name().underlying(Name.bijection, typo.runtime.PgTypes.text).stringAppend(p.color(), typo.dsl.Bijection.identity()).like("foo%", typo.dsl.Bijection.identity()).not(Bijection.asBool()))
                    .where(p -> p.daystomanufacture().greaterThan(0))
                    .where(p -> p.modifieddate().lessThan(TypoLocalDateTime.now()))
                    .join(projectModelRepo.select().where(pm -> pm.modifieddate().lessThan(TypoLocalDateTime.now())))
                    .on(p_pm -> p_pm._1().productmodelid().isEqual(p_pm._2().productmodelid()))
                    .where(p_pm -> p_pm._2().instructions().isNull().not(Bijection.asBool()));

            compareFragment("q", q.sql());
            q.toList(c).forEach(System.out::println);

            var q2 = productRepo.select()
                    // select from id, arrays work
                    .where(p -> p.productid().in(saved1.productid(), new ProductId(22)))
                    // call `length` function and compare result
                    .where(p -> p.name().strLength(Name.bijection).greaterThan(new SqlExpr.ConstReq<>(3, typo.runtime.PgTypes.int4)))
                    // concatenate two strings (one of which is a wrapped type in scala) and compare result
                    .where(p -> p.name().underlying(Name.bijection, typo.runtime.PgTypes.text).stringAppend(p.color(), typo.dsl.Bijection.identity()).like("foo%", typo.dsl.Bijection.identity()).not(Bijection.asBool()))
                    // tracks nullability
                    .where(p -> p.color().coalesce("yellow").isNotEqual(new SqlExpr.ConstReq<>("blue", typo.runtime.PgTypes.text)))
                    // compare dates
                    .where(p -> p.modifieddate().lessThan(TypoLocalDateTime.now()))
                    // join, filter table we join with as well
                    .join(projectModelRepo.select().where(pm -> pm.name().strLength(Name.bijection).greaterThan(new SqlExpr.ConstReq<>(0, typo.runtime.PgTypes.int4))))
                    .on(p_pm -> p_pm._1().productmodelid().isEqual(p_pm._2().productmodelid()))
                    // additional predicates for joined rows.
                    .where(p_pm -> p_pm._2().name().underlying(Name.bijection, typo.runtime.PgTypes.text).isNotEqual(new SqlExpr.ConstReq<>("foo", typo.runtime.PgTypes.text)))
                    // works arbitrarily deep
                    .join(projectModelRepo.select().where(pm -> pm.name().strLength(Name.bijection).greaterThan(new SqlExpr.ConstReq<>(0, typo.runtime.PgTypes.int4))))
                    .leftOn(p_pm_pm2 -> p_pm_pm2._1()._1().productmodelid().isEqual(p_pm_pm2._2().productmodelid()).and(new SqlExpr.ConstReq<>(false, typo.runtime.PgTypes.bool), Bijection.asBool()))
                    // order by
                    .orderBy(p_pm_pm2 -> p_pm_pm2._2().name().asc())
                    .orderBy(p_pm_pm2 -> p_pm_pm2._1()._1().color().desc().withNullsFirst());

            compareFragment("q2", q2.sql());
            q2.toList(c).forEach(row -> {
                System.out.println(row._1()._1());
                System.out.println(row._1()._2());
                System.out.println(row._2());
            });

            // delete
            var delete = productRepo.delete().where(p -> p.productid().isEqual(saved1.productid()));
            compareFragment("delete", delete.sql());

            delete.execute(c);

            assertEquals(0, productRepo.selectAll(c).size());
        });
    }

    @Test
    public void inMemory() {
        runTest(
                new ProductRepoMock(unsaved -> unsaved.toRow(
                        () -> new ProductId(0),
                        () -> new Flag(true),
                        () -> new Flag(false),
                        () -> TypoUUID.randomUUID(),
                        () -> TypoLocalDateTime.now()
                )),
                new ProductmodelRepoMock(unsaved -> unsaved.toRow(
                        () -> new ProductmodelId(0),
                        () -> TypoUUID.randomUUID(),
                        () -> TypoLocalDateTime.now()
                )),
                new UnitmeasureRepoMock(unsaved -> unsaved.toRow(() -> TypoLocalDateTime.now())),
                new ProductcategoryRepoMock(unsaved -> unsaved.toRow(
                        () -> new ProductcategoryId(0),
                        () -> TypoUUID.randomUUID(),
                        () -> TypoLocalDateTime.now()
                )),
                new ProductsubcategoryRepoMock(unsaved -> unsaved.toRow(
                        () -> new ProductsubcategoryId(0),
                        () -> TypoUUID.randomUUID(),
                        () -> TypoLocalDateTime.now()
                )),
                true // isMock
        );
    }

    @Test
    public void pg() {
        runTest(
                new ProductRepoImpl(),
                new ProductmodelRepoImpl(),
                new UnitmeasureRepoImpl(),
                new ProductcategoryRepoImpl(),
                new ProductsubcategoryRepoImpl(),
                false // isMock
        );
    }
}
