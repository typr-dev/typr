package adventureworks.person_detail;

import adventureworks.DbNow;
import adventureworks.WithConnection;
import adventureworks.person.businessentity.BusinessentityId;
import org.junit.Test;

public class PersonDetailTest {
  private final PersonDetailSqlRepoImpl personDetailSqlRepo = new PersonDetailSqlRepoImpl();

  @Test
  public void timestampWorks() {
    WithConnection.run(
        c -> {
          personDetailSqlRepo.apply(new BusinessentityId(1), DbNow.localDateTime(), c);
        });
  }
}
