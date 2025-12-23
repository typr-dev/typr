package adventureworks.production.productcosthistory;

import static org.junit.Assert.*;

import adventureworks.DbNow;
import adventureworks.SnapshotTest;
import adventureworks.WithConnection;
import adventureworks.person.businessentity.*;
import adventureworks.person.emailaddress.*;
import adventureworks.person.person.*;
import adventureworks.production.product.ProductId;
import adventureworks.production.product.ProductRepoImpl;
import adventureworks.production.product.ProductRowUnsaved;
import adventureworks.production.productcategory.ProductcategoryRepoImpl;
import adventureworks.production.productcategory.ProductcategoryRowUnsaved;
import adventureworks.production.productmodel.ProductmodelRepoImpl;
import adventureworks.production.productmodel.ProductmodelRowUnsaved;
import adventureworks.production.productsubcategory.ProductsubcategoryRepoImpl;
import adventureworks.production.productsubcategory.ProductsubcategoryRowUnsaved;
import adventureworks.production.unitmeasure.UnitmeasureId;
import adventureworks.production.unitmeasure.UnitmeasureRepoImpl;
import adventureworks.production.unitmeasure.UnitmeasureRow;
import adventureworks.public_.Name;
import adventureworks.public_.NameStyle;
import adventureworks.userdefined.FirstName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.Test;

/** Tests for composite IDs functionality - equivalent to Scala CompositeIdsTest. */
public class CompositeIdsTest extends SnapshotTest {
  private final ProductcosthistoryRepoImpl repo = new ProductcosthistoryRepoImpl();
  private final UnitmeasureRepoImpl unitmeasureRepo = new UnitmeasureRepoImpl();
  private final ProductcategoryRepoImpl productcategoryRepo = new ProductcategoryRepoImpl();
  private final ProductsubcategoryRepoImpl productsubcategoryRepo =
      new ProductsubcategoryRepoImpl();
  private final ProductmodelRepoImpl productmodelRepo = new ProductmodelRepoImpl();
  private final ProductRepoImpl productRepo = new ProductRepoImpl();

  @Test
  public void works() {
    WithConnection.run(
        c -> {
          // Setup unitmeasure
          var unitmeasure =
              unitmeasureRepo.insert(
                  new UnitmeasureRow(
                      new UnitmeasureId("kgg"), new Name("Kilograms"), DbNow.localDateTime()),
                  c);

          // Setup product category - use short ctor
          var productCategory =
              productcategoryRepo.insert(
                  new ProductcategoryRowUnsaved(new Name("Test Category")), c);

          // Setup product subcategory - use short ctor
          var productSubcategory =
              productsubcategoryRepo.insert(
                  new ProductsubcategoryRowUnsaved(
                      productCategory.productcategoryid(), new Name("Test Subcategory")),
                  c);

          // Setup product model - use short ctor
          var productModel =
              productmodelRepo.insert(new ProductmodelRowUnsaved(new Name("Test Model")), c);

          var now = DbNow.localDateTime();

          // Setup product - use short ctor + withers
          var product =
              productRepo.insert(
                  new ProductRowUnsaved(
                          new Name("Test Product"),
                          "TEST-001",
                          (short) 1,
                          (short) 1,
                          BigDecimal.ONE,
                          BigDecimal.ONE,
                          10,
                          now)
                      .withSizeunitmeasurecode(Optional.of(unitmeasure.unitmeasurecode()))
                      .withWeightunitmeasurecode(Optional.of(unitmeasure.unitmeasurecode()))
                      .withProductsubcategoryid(
                          Optional.of(productSubcategory.productsubcategoryid()))
                      .withProductmodelid(Optional.of(productModel.productmodelid())),
                  c);

          // Create product cost history records - use short ctor + withers
          var ph1 =
              repo.insert(
                  new ProductcosthistoryRowUnsaved(product.productid(), now, BigDecimal.ONE)
                      .withEnddate(Optional.of(now.plusDays(1))),
                  c);
          var ph2 =
              repo.insert(
                  new ProductcosthistoryRowUnsaved(
                          product.productid(), now.plusDays(1), BigDecimal.ONE)
                      .withEnddate(Optional.of(now.plusDays(2))),
                  c);
          var ph3 =
              repo.insert(
                  new ProductcosthistoryRowUnsaved(
                          product.productid(), now.plusDays(2), BigDecimal.ONE)
                      .withEnddate(Optional.of(now.plusDays(3))),
                  c);

          var wanted =
              new ProductcosthistoryId[] {
                ph1.compositeId(),
                ph2.compositeId(),
                new ProductcosthistoryId(new ProductId(9999), ph3.compositeId().startdate())
              };

          var selected =
              repo.selectByIds(wanted, c).stream()
                  .map(ProductcosthistoryRow::compositeId)
                  .collect(Collectors.toSet());
          assertEquals(Set.of(ph1.compositeId(), ph2.compositeId()), selected);

          assertEquals(Integer.valueOf(2), repo.deleteByIds(wanted, c));

          var remaining =
              repo.selectAll(c).stream().map(ProductcosthistoryRow::compositeId).toList();
          assertEquals(java.util.List.of(ph3.compositeId()), remaining);
        });
  }

  private void testDsl(
      EmailaddressRepo emailaddressRepo,
      BusinessentityRepo businessentityRepo,
      PersonRepo personRepo) {
    var now = LocalDateTime.of(2021, 1, 1, 0, 0);

    WithConnection.run(
        c -> {
          // Create business entities
          var businessentity1 =
              businessentityRepo.insert(
                  new BusinessentityRow(new BusinessentityId(1), UUID.randomUUID(), now), c);
          personRepo.insert(personRow(businessentity1.businessentityid(), 1, now), c);

          var businessentity2 =
              businessentityRepo.insert(
                  new BusinessentityRow(new BusinessentityId(2), UUID.randomUUID(), now), c);
          personRepo.insert(personRow(businessentity2.businessentityid(), 2, now), c);

          // Create email addresses for businessentity1
          var emailaddress1_1 =
              emailaddressRepo.insert(
                  new EmailaddressRow(
                      businessentity1.businessentityid(),
                      1,
                      Optional.of("a@b.c"),
                      UUID.randomUUID(),
                      now),
                  c);
          var emailaddress1_2 =
              emailaddressRepo.insert(
                  new EmailaddressRow(
                      businessentity1.businessentityid(),
                      2,
                      Optional.of("aa@bb.cc"),
                      UUID.randomUUID(),
                      now),
                  c);
          var emailaddress1_3 =
              emailaddressRepo.insert(
                  new EmailaddressRow(
                      businessentity1.businessentityid(),
                      3,
                      Optional.of("aaa@bbb.ccc"),
                      UUID.randomUUID(),
                      now),
                  c);

          // Create email addresses for businessentity2
          emailaddressRepo.insert(
              new EmailaddressRow(
                  businessentity2.businessentityid(),
                  1,
                  Optional.of("A@B.C"),
                  UUID.randomUUID(),
                  now),
              c);
          emailaddressRepo.insert(
              new EmailaddressRow(
                  businessentity2.businessentityid(),
                  2,
                  Optional.of("AA@BB.CC"),
                  UUID.randomUUID(),
                  now),
              c);
          emailaddressRepo.insert(
              new EmailaddressRow(
                  businessentity2.businessentityid(),
                  3,
                  Optional.of("AAA@BBB.CCC"),
                  UUID.randomUUID(),
                  now),
              c);

          // Test compositeIdIs
          var res1 =
              emailaddressRepo
                  .select()
                  .where(e -> e.compositeIdIs(emailaddress1_1.compositeId()))
                  .toList(c);
          assertEquals(List.of(emailaddress1_1), res1);

          // Test compositeIdIn
          var query2 =
              emailaddressRepo
                  .select()
                  .where(
                      e ->
                          e.compositeIdIn(
                              List.of(
                                  emailaddress1_2.compositeId(), emailaddress1_3.compositeId())));
          var res2 = query2.toList(c);
          compareFragment("query2", query2.sql());
          assertEquals(List.of(emailaddress1_2, emailaddress1_3), res2);
        });
  }

  @Test
  public void dslPg() {
    testDsl(new EmailaddressRepoImpl(), new BusinessentityRepoImpl(), new PersonRepoImpl());
  }

  @Test
  public void dslInMemory() {
    testDsl(
        new EmailaddressRepoMock(
            unsaved ->
                unsaved.toRow(
                    () -> 0, // emailaddressid
                    () -> UUID.randomUUID(),
                    () -> DbNow.localDateTime())),
        new BusinessentityRepoMock(
            unsaved ->
                unsaved.toRow(
                    () -> new BusinessentityId(0), // businessentityid
                    () -> UUID.randomUUID(),
                    () -> DbNow.localDateTime())),
        new PersonRepoMock(
            unsaved ->
                unsaved.toRow(
                    () -> new NameStyle(false), // namestyle
                    () -> 0, // emailpromotion
                    () -> UUID.randomUUID(),
                    () -> DbNow.localDateTime())));
  }

  private PersonRow personRow(BusinessentityId businessentityid, int i, LocalDateTime now) {
    return new PersonRow(
        businessentityid,
        "SC",
        new NameStyle(true),
        Optional.empty(),
        new FirstName("first name " + i),
        Optional.empty(),
        new Name("last name " + i),
        Optional.empty(),
        1,
        Optional.empty(),
        Optional.empty(),
        UUID.randomUUID(),
        now);
  }
}
