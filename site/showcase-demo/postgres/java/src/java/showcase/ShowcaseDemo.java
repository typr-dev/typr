package showcase;

import dev.typr.foundations.internal.RandomHelper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Random;
import showcase.showcase.*;
import showcase.showcase.company.*;
import showcase.showcase.department.*;
import showcase.showcase.employee.*;

public class ShowcaseDemo {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:6432/Adventureworks";
        String user = "postgres";
        String password = "password";

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);

            var domainInsert = new DomainInsertImpl();
            var testInsert = new TestInsert(new Random(0L), domainInsert);

            // Company -> Department -> Employee (3-level FK hierarchy)
            var company = testInsert.showcaseCompany()
                .with(row -> row.withName("Acme Corporation"))
                .insert(c);

            var department = testInsert.showcaseDepartment(company.id())
                .with(row -> row.withName("Engineering")
                    .withBudget(java.util.Optional.of(BigDecimal.valueOf(1000000))))
                .insert(c);

            var employee = testInsert.showcaseEmployee(department.id())
                .with(row -> row.withFirstName("John").withLastName("Doe"))
                .insert(c);

            // Output:
            // company    => CompanyRow[id=CCzLNHB..., name=Acme Corporation, ...]
            // department => DepartmentRow[id=Hfyqts0..., companyId=CCzLNHB..., ...]
            // employee   => EmployeeRow[id=bFbQPNB..., email=EmailAddress[...]]

            // Rollback - we just wanted to see the output
            c.rollback();
        }
    }
}

class DomainInsertImpl implements TestDomainInsert {
    @Override
    public EmailAddress showcaseEmailAddress(Random random) {
        return new EmailAddress(RandomHelper.alphanumeric(random, 8) + "@example.com");
    }

    @Override
    public PositiveAmount showcasePositiveAmount(Random random) {
        return new PositiveAmount(BigDecimal.valueOf(Math.abs(random.nextDouble() * 10000) + 1));
    }

    @Override
    public PhoneNumber showcasePhoneNumber(Random random) {
        return new PhoneNumber("+1-555-" + random.nextInt(1000) + "-" + random.nextInt(10000));
    }

    @Override
    public Percentage showcasePercentage(Random random) {
        return new Percentage(BigDecimal.valueOf(random.nextDouble() * 100));
    }
}
