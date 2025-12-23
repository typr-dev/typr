package adventureworks;

import adventureworks.humanresources.employee.EmployeeRepoImpl;
import adventureworks.humanresources.employee.EmployeeRowUnsaved;
import adventureworks.person.businessentity.BusinessentityRepoImpl;
import adventureworks.person.businessentity.BusinessentityRowUnsaved;
import adventureworks.person.emailaddress.EmailaddressRepoImpl;
import adventureworks.person.emailaddress.EmailaddressRowUnsaved;
import adventureworks.person.person.PersonRepoImpl;
import adventureworks.person.person.PersonRowUnsaved;
import adventureworks.person_row_join.PersonRowJoinSqlRepoImpl;
import adventureworks.public_.Name;
import adventureworks.sales.salesperson.SalespersonRepoImpl;
import adventureworks.sales.salesperson.SalespersonRowUnsaved;
import adventureworks.userdefined.FirstName;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.Test;

public class RecordTest {
  private final PersonRowJoinSqlRepoImpl personRowJoinSqlRepo = new PersonRowJoinSqlRepoImpl();
  private final BusinessentityRepoImpl businessentityRepo = new BusinessentityRepoImpl();
  private final PersonRepoImpl personRepo = new PersonRepoImpl();
  private final EmailaddressRepoImpl emailaddressRepo = new EmailaddressRepoImpl();
  private final EmployeeRepoImpl employeeRepo = new EmployeeRepoImpl();
  private final SalespersonRepoImpl salespersonRepo = new SalespersonRepoImpl();

  @Test
  public void works() {
    WithConnection.run(
        c -> {
          // Use short ctors + withers throughout

          // BusinessEntity - all fields Defaulted, no short ctor
          var businessentityRow = businessentityRepo.insert(new BusinessentityRowUnsaved(), c);

          // Person - use short ctor
          var personRow =
              personRepo.insert(
                  new PersonRowUnsaved(
                      businessentityRow.businessentityid(),
                      "EM",
                      new FirstName("a"),
                      new Name("lastname")),
                  c);

          // EmailAddress - use short ctor + wither
          emailaddressRepo.insert(
              new EmailaddressRowUnsaved(personRow.businessentityid())
                  .withEmailaddress(Optional.of("a@b.c")),
              c);

          // Employee - use short ctor
          var employeeRow =
              employeeRepo.insert(
                  new EmployeeRowUnsaved(
                      personRow.businessentityid(),
                      "9912312312",
                      "loginid",
                      "jobtitle",
                      LocalDate.parse("1998-01-01"),
                      "M",
                      "M",
                      LocalDate.parse("1997-01-01")),
                  c);

          // Salesperson - use short ctor
          salespersonRepo.insert(new SalespersonRowUnsaved(employeeRow.businessentityid()), c);

          personRowJoinSqlRepo.apply(c).forEach(System.out::println);
        });
  }
}
