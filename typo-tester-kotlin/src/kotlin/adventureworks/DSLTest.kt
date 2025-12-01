package adventureworks

import adventureworks.customtypes.Defaulted
import adventureworks.customtypes.TypoLocalDate
import adventureworks.humanresources.employee.EmployeeRepoImpl
import adventureworks.person.businessentity.BusinessentityRepoImpl
import adventureworks.person.emailaddress.EmailaddressRepoImpl
import adventureworks.person.person.PersonRepoImpl
import adventureworks.public.Name
import adventureworks.sales.salesperson.SalespersonRepoImpl
import adventureworks.userdefined.FirstName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.Optional
import java.time.LocalDate

/**
 * Tests for DSL join chains.
 */
class DSLTest : SnapshotTest() {
    private val businessentityRepoImpl = BusinessentityRepoImpl()
    private val personRepoImpl = PersonRepoImpl()
    private val employeeRepoImpl = EmployeeRepoImpl()
    private val salespersonRepoImpl = SalespersonRepoImpl()
    private val emailaddressRepoImpl = EmailaddressRepoImpl()

    @Test
    fun works() {
        WithConnection.run { c ->
            val testInsert = TestInsert(java.util.Random(0), DomainInsertImpl())

            // Create businessentity
            val businessentityRow = testInsert.personBusinessentity(
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                c
            )

            // Create person
            val personRow = testInsert.personPerson(
                businessentityRow.businessentityid,
                "EM",  // persontype
                FirstName("a"),
                Optional.empty(),  // title
                Optional.empty(),  // middlename
                Name("lastname"),
                Optional.empty(),  // suffix
                Optional.empty(),  // additionalcontactinfo
                Optional.empty(),  // demographics
                Defaulted.UseDefault(),  // namestyle
                Defaulted.UseDefault(),  // emailpromotion
                Defaulted.UseDefault(),  // rowguid
                Defaulted.UseDefault(),  // modifieddate
                c
            )

            // Create emailaddress
            testInsert.personEmailaddress(
                personRow.businessentityid,
                Optional.of("a@b.c"),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                c
            )

            // Create employee
            val employeeRow = testInsert.humanresourcesEmployee(
                personRow.businessentityid,
                "12345",  // nationalidnumber
                "adventure-works\\a",  // loginid
                "Test Job",  // jobtitle
                TypoLocalDate(LocalDate.of(1998, 1, 1)),  // birthdate
                "M",  // maritalstatus
                "M",  // gender
                TypoLocalDate(LocalDate.of(1997, 1, 1)),  // hiredate
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                c
            )

            // Create salesperson
            val salespersonRow = testInsert.salesSalesperson(
                employeeRow.businessentityid,
                Optional.empty(),  // territoryid
                Optional.empty(),  // salesquota
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                Defaulted.UseDefault(),
                c
            )

            // Test join chain: salesperson -> employee -> person -> businessentity, then join with email
            val query = salespersonRepoImpl.select()
                .where { sp -> sp.rowguid().isEqual(salespersonRow.rowguid) }
                .joinFk({ sp -> sp.fkHumanresourcesEmployee() }, employeeRepoImpl.select())
                .joinFk({ sp_e -> sp_e._2().fkPersonPerson() }, personRepoImpl.select())
                .joinFk({ sp_e_p -> sp_e_p._2().fkBusinessentity() }, businessentityRepoImpl.select())
                .join(emailaddressRepoImpl.select().orderBy { e -> e.rowguid().asc() })
                .on { sp_e_p_b_email -> sp_e_p_b_email._2().businessentityid().isEqual(sp_e_p_b_email._1()._2().businessentityid()) }

            // Self-join the query
            val doubled = query
                .join(query)
                .on { left_right -> left_right._1()._1()._1()._2().businessentityid().isEqual(left_right._2()._1()._1()._2().businessentityid()) }

            // Add snapshot test
            compareFragment("doubled", doubled.sql())

            val results = doubled.toList(c)
            results.forEach { println(it) }
            assertEquals(1, doubled.count(c))
        }
    }
}
