package adventureworks

import adventureworks.customtypes.Defaulted
import adventureworks.customtypes.TypoLocalDate
import adventureworks.humanresources.employee.EmployeeRepoImpl
import adventureworks.humanresources.employee.EmployeeRowUnsaved
import adventureworks.person.businessentity.BusinessentityRepoImpl
import adventureworks.person.businessentity.BusinessentityRowUnsaved
import adventureworks.person.emailaddress.EmailaddressRepoImpl
import adventureworks.person.emailaddress.EmailaddressRowUnsaved
import adventureworks.person.person.PersonRepoImpl
import adventureworks.person.person.PersonRowUnsaved
import adventureworks.person_row_join.PersonRowJoinSqlRepoImpl
import adventureworks.public.Name
import adventureworks.sales.salesperson.SalespersonRepoImpl
import adventureworks.sales.salesperson.SalespersonRowUnsaved
import adventureworks.userdefined.FirstName
import org.junit.jupiter.api.Test
import java.util.Optional

class RecordTest {
    private val personRowJoinSqlRepo = PersonRowJoinSqlRepoImpl()
    private val businessentityRepo = BusinessentityRepoImpl()
    private val personRepo = PersonRepoImpl()
    private val emailaddressRepo = EmailaddressRepoImpl()
    private val employeeRepo = EmployeeRepoImpl()
    private val salespersonRepo = SalespersonRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            // Use short ctors + copy throughout

            // BusinessEntity - all fields Defaulted, no short ctor
            val businessentityRow = businessentityRepo.insert(BusinessentityRowUnsaved(), c)

            // Person - use full constructor
            val personRow = personRepo.insert(
                PersonRowUnsaved(
                    businessentityid = businessentityRow.businessentityid,
                    persontype = "EM",
                    firstname = FirstName("a"),
                    lastname = Name("lastname")
                ), c
            )

            // EmailAddress - use copy for optional field
            emailaddressRepo.insert(
                EmailaddressRowUnsaved(businessentityRow.businessentityid)
                    .copy(emailaddress = Optional.of("a@b.c")), c
            )

            // Employee - use full constructor
            val employeeRow = employeeRepo.insert(
                EmployeeRowUnsaved(
                    businessentityid = personRow.businessentityid,
                    nationalidnumber = "9912312312",
                    loginid = "loginid",
                    jobtitle = "jobtitle",
                    birthdate = TypoLocalDate.apply("1998-01-01"),
                    maritalstatus = "M",
                    gender = "M",
                    hiredate = TypoLocalDate.apply("1997-01-01")
                ), c
            )

            // Salesperson - use short ctor
            salespersonRepo.insert(SalespersonRowUnsaved(employeeRow.businessentityid), c)

            personRowJoinSqlRepo.apply(c).forEach { println(it) }
        }
    }
}
