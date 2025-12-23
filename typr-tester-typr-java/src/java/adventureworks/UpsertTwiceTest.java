package adventureworks;

import static org.junit.Assert.*;

import adventureworks.public_.only_pk_columns.OnlyPkColumnsRepoImpl;
import adventureworks.public_.only_pk_columns.OnlyPkColumnsRow;
import org.junit.Test;

public class UpsertTwiceTest {
  private final OnlyPkColumnsRepoImpl onlyPkColumnsRepo = new OnlyPkColumnsRepoImpl();

  @Test
  public void secondUpsertShouldNotError() {
    var row = new OnlyPkColumnsRow("the answer is", 42);
    WithConnection.run(
        c -> {
          assertEquals(onlyPkColumnsRepo.upsert(row, c), onlyPkColumnsRepo.upsert(row, c));
        });
  }
}
