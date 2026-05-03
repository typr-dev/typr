package showcase;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Random;
import showcase.showcase.EmailAddress;

public class TestInsertExample {
    public void example(Connection c) {
        var testInsert = new TestInsert(new Random(0L), new DomainInsertImpl());

        // Insert with just a name - everything else is random
        var company1 = testInsert.showcaseCompany()
            .withName("Acme Corp")
            .insert(c);
        // => CompanyRow[id=CCzLNHBFHuRvbI1iI19W, name=Acme Corp,
        //      foundedYear=Optional.empty, active=Optional[true],
        //      createdAt=Optional[2026-01-04T12:00:00]]

        // Another company - all values random
        var company2 = testInsert.showcaseCompany().insert(c);
        // => CompanyRow[id=Hfyqts0coJXQqPyuxbr5, name=bFbQPNB7ZuKSWpBejTwv,
        //      foundedYear=Optional[1926371], active=Optional[true],
        //      createdAt=Optional[2026-01-04T12:00:00]]

        // Department with FK to company - budget is random
        var dept1 = testInsert.showcaseDepartment(company1.id())
            .withName("Engineering")
            .insert(c);
        // => DepartmentRow[id=1NdpH7sjZu9W3pBe, companyId=CCzLNHBFHuRvbI1iI19W,
        //      name=Engineering, budget=Optional[8472.19]]

        // Another department - all values random except FK
        var dept2 = testInsert.showcaseDepartment(company1.id()).insert(c);
        // => DepartmentRow[id=jTwvKSWpQqPyuxbr, companyId=CCzLNHBFHuRvbI1iI19W,
        //      name=xQqPyuxbr5Hfyqts, budget=Optional[3891.42]]

        // Employee with FK to department - domains auto-generated
        var employee = testInsert.showcaseEmployee(dept1.id())
            .withFirstName("John")
            .withLastName("Doe")
            .insert(c);
        // => EmployeeRow[id=9W3pBeKSWpQqPyux, departmentId=1NdpH7sjZu9W3pBe,
        //      email=EmailAddress[xK7mN2pQ@example.com], firstName=John, lastName=Doe,
        //      salary=Optional[PositiveAmount[4721.83]],
        //      phone=Optional[PhoneNumber[+1-555-847-2938]], ...]
    }
}
