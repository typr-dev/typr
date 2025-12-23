package adventureworks.public_;

import static org.junit.Assert.*;

import adventureworks.DomainInsertImpl;
import adventureworks.TestInsert;
import adventureworks.WithConnection;
import adventureworks.public_.title.*;
import adventureworks.public_.title_domain.*;
import adventureworks.public_.titledperson.*;
import java.util.Random;
import org.junit.Test;

/**
 * Tests for open enum types (sealed interface with Known enum + Unknown record). Equivalent to
 * Scala OpenEnumTest.
 */
public class OpenEnumTest {
  private final TitleRepoImpl titleRepo = new TitleRepoImpl();
  private final TitleDomainRepoImpl titleDomainRepo = new TitleDomainRepoImpl();
  private final TitledpersonRepoImpl titledPersonRepo = new TitledpersonRepoImpl();

  @Test
  public void works() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new Random(0), new DomainInsertImpl());
          var john =
              testInsert.publicTitledperson(TitleDomainId.Known.dr, TitleId.Known.dr, "John", c);

          // DSL query with joinFk chain
          var found =
              titledPersonRepo
                  .select()
                  .joinFk(
                      tp -> tp.fkTitle(),
                      titleRepo.select().where(t -> t.code().in(TitleId.Known.dr)))
                  .joinFk(
                      tp_t -> tp_t._1().fkTitleDomain(),
                      titleDomainRepo.select().where(td -> td.code().in(TitleDomainId.Known.dr)))
                  .where(tp_t_td -> tp_t_td._1()._1().name().isEqual("John"))
                  .toList(c)
                  .stream()
                  .findFirst();

          assertTrue(found.isPresent());
          var result = found.get();
          // result is ((TitledpersonRow, TitleRow), TitleDomainRow)
          assertEquals(john, result._1()._1());
          assertEquals(new TitleRow(TitleId.Known.dr), result._1()._2());
          assertEquals(new TitleDomainRow(TitleDomainId.Known.dr), result._2());
        });
  }

  private void testDsl(
      TitledpersonRepo titledPersonRepo, TitleRepo titleRepo, TitleDomainRepo titleDomainRepo) {
    WithConnection.run(
        c -> {
          // Insert the titled person with dr/dr/John
          var john =
              titledPersonRepo.insert(
                  new TitledpersonRow(TitleDomainId.Known.dr, TitleId.Known.dr, "John"), c);

          // DSL query with joinFk chain
          var found =
              titledPersonRepo
                  .select()
                  .joinFk(
                      tp -> tp.fkTitle(),
                      titleRepo.select().where(t -> t.code().in(TitleId.Known.dr)))
                  .joinFk(
                      tp_t -> tp_t._1().fkTitleDomain(),
                      titleDomainRepo.select().where(td -> td.code().in(TitleDomainId.Known.dr)))
                  .where(tp_t_td -> tp_t_td._1()._1().name().isEqual("John"))
                  .toList(c)
                  .stream()
                  .findFirst();

          assertTrue(found.isPresent());
          var result = found.get();
          assertEquals(john, result._1()._1());
          assertEquals(new TitleRow(TitleId.Known.dr), result._1()._2());
          assertEquals(new TitleDomainRow(TitleDomainId.Known.dr), result._2());

          // Verify pattern matching works with open enums
          var titleValue = john.title();
          String titleString =
              switch (titleValue) {
                case TitleId.Known known -> "Known: " + known.value();
                case TitleId.Unknown unknown -> "Unknown: " + unknown.value();
              };
          assertEquals("Known: dr", titleString);
        });
  }

  @Test
  public void pg() {
    testDsl(new TitledpersonRepoImpl(), new TitleRepoImpl(), new TitleDomainRepoImpl());
  }

  // Note: inMemory test not implemented because TitledpersonRepoMock is not generated
  // and the existing mocks don't support DSL operations
}
