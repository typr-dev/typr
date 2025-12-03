package testdb;

import org.junit.Test;
import testdb.customtypes.Defaulted.UseDefault;
import testdb.simple_customer_lookup.SimpleCustomerLookupSqlRepoImpl;

import java.util.Random;

import static org.junit.Assert.*;

public class SimpleCustomerLookupSqlRepoTest {
    private final SimpleCustomerLookupSqlRepoImpl repo = new SimpleCustomerLookupSqlRepoImpl();

    @Test
    public void lookupCustomerByEmail() {
        WithConnection.run(c -> {
            var testInsert = new TestInsert(new Random(0));

            var testEmail = "test@example.com";
            var customer = testInsert.Customers(
                new byte[]{1, 2, 3},
                testEmail,
                "First",
                "Last",
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                c
            );

            var results = repo.apply(testEmail, c);

            assertEquals(1, results.size());
            var result = results.get(0);
            assertEquals(customer.customerId(), result.customerId());
            assertEquals(testEmail, result.email());
            assertEquals(customer.firstName(), result.firstName());
            assertEquals(customer.lastName(), result.lastName());
        });
    }

    @Test
    public void lookupNonExistentCustomerReturnsEmptyList() {
        WithConnection.run(c -> {
            var results = repo.apply("nonexistent@example.com", c);
            assertTrue(results.isEmpty());
        });
    }
}
