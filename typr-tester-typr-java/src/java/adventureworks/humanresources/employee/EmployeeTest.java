package adventureworks.humanresources.employee;

import static org.junit.Assert.*;

import adventureworks.DbNow;
import adventureworks.WithConnection;
import adventureworks.customtypes.Defaulted;
import adventureworks.person.businessentity.BusinessentityId;
import adventureworks.person.businessentity.BusinessentityRepoImpl;
import adventureworks.person.businessentity.BusinessentityRowUnsaved;
import adventureworks.person.person.PersonRepoImpl;
import adventureworks.person.person.PersonRowUnsaved;
import adventureworks.public_.Flag;
import adventureworks.public_.Name;
import adventureworks.userdefined.FirstName;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import typr.data.Xml;

public class EmployeeTest {
  private final EmployeeRepoImpl employeeRepo = new EmployeeRepoImpl();
  private final PersonRepoImpl personRepo = new PersonRepoImpl();
  private final BusinessentityRepoImpl businessentityRepo = new BusinessentityRepoImpl();

  @Test
  public void works() {
    WithConnection.run(
        c -> {
          // Create businessentity first (all fields Defaulted, no short ctor)
          var businessentityRow =
              businessentityRepo.insert(
                  new BusinessentityRowUnsaved(
                      new Defaulted.UseDefault<>(),
                      new Defaulted.UseDefault<>(),
                      new Defaulted.UseDefault<>()),
                  c);

          // Create person - use short ctor + withers
          var personRow =
              personRepo.insert(
                  new PersonRowUnsaved(
                          businessentityRow.businessentityid(),
                          "SC",
                          new FirstName("firstname"),
                          new Name("lastname"))
                      .withMiddlename(Optional.of(new Name("middlename")))
                      .withSuffix(Optional.of("suffix"))
                      .withAdditionalcontactinfo(Optional.of(new Xml("<additionalcontactinfo/>"))),
                  c);

          // Setup employee - use short ctor + withers for specific values
          var unsaved =
              new EmployeeRowUnsaved(
                      personRow.businessentityid(),
                      "9912312312",
                      "loginid",
                      "jobtitle",
                      LocalDate.of(1950, 1, 1),
                      "M",
                      "F",
                      LocalDate.now().minusYears(1))
                  .withSalariedflag(new Defaulted.Provided<>(new Flag(true)))
                  .withVacationhours(new Defaulted.Provided<>((short) 1))
                  .withSickleavehours(new Defaulted.Provided<>((short) 2))
                  .withCurrentflag(new Defaulted.Provided<>(new Flag(true)))
                  .withRowguid(new Defaulted.Provided<>(UUID.randomUUID()))
                  .withModifieddate(new Defaulted.Provided<>(DbNow.localDateTime()))
                  .withOrganizationnode(new Defaulted.Provided<>(Optional.of("/")));

          // Insert and round trip check
          var saved1 = employeeRepo.insert(unsaved, c);
          assertEquals(unsaved.nationalidnumber(), saved1.nationalidnumber());
          assertEquals(unsaved.loginid(), saved1.loginid());
          assertEquals(unsaved.jobtitle(), saved1.jobtitle());

          // Check field values
          employeeRepo.update(saved1.withGender("M"), c);
          var all = employeeRepo.selectAll(c);
          assertEquals(1, all.size());
          var saved3 = all.get(0);
          assertEquals("M", saved3.gender());

          // Select by ids
          var byIds =
              employeeRepo.selectByIds(
                  new BusinessentityId[] {saved1.businessentityid(), new BusinessentityId(22)}, c);
          assertEquals(1, byIds.size());
          assertEquals(saved3, byIds.get(0));

          // Delete
          employeeRepo.deleteById(saved1.businessentityid(), c);
          var afterDelete = employeeRepo.selectAll(c);
          assertTrue(afterDelete.isEmpty());

          // Scala: Test insert with minimal fields to verify defaults - use short ctor
          var minimalUnsaved =
              new EmployeeRowUnsaved(
                  personRow.businessentityid(),
                  "9912312313",
                  "loginid2",
                  "jobtitle2",
                  LocalDate.of(1960, 1, 1),
                  "M",
                  "F",
                  LocalDate.now().minusYears(1));

          var withDefaults = employeeRepo.insert(minimalUnsaved, c);

          // Verify the static default values from the database schema
          assertEquals(new Flag(true), withDefaults.salariedflag());
          assertEquals(Short.valueOf((short) 0), withDefaults.vacationhours());
          assertEquals(Short.valueOf((short) 0), withDefaults.sickleavehours());
          assertEquals(new Flag(true), withDefaults.currentflag());
          assertEquals(Optional.of("/"), withDefaults.organizationnode());
        });
  }
}
