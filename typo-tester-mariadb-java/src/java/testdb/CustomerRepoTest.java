package testdb;

import org.junit.Test;
import testdb.customer_status.CustomerStatusId;
import testdb.customer_status.CustomerStatusRepoImpl;
import testdb.customer_status.CustomerStatusRowUnsaved;
import testdb.customers.CustomersId;
import testdb.customers.CustomersRepoImpl;
import testdb.customers.CustomersRow;
import testdb.customers.CustomersRowUnsaved;
import testdb.customtypes.Defaulted.Provided;
import testdb.customtypes.Defaulted.UseDefault;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Full repository test for customers - tests all CRUD operations and more.
 * Note: The database has seeded customer_status data including 'active', 'pending', 'suspended', 'closed'.
 * Tests use these existing statuses rather than inserting new ones.
 */
public class CustomerRepoTest {
    private final CustomersRepoImpl customersRepo = new CustomersRepoImpl();
    private final CustomerStatusRepoImpl customerStatusRepo = new CustomerStatusRepoImpl();

    // Reference to existing seeded status in the database
    private static final CustomerStatusId ACTIVE_STATUS = new CustomerStatusId("active");
    private static final CustomerStatusId PENDING_STATUS = new CustomerStatusId("pending");

    @Test
    public void testSelectAll() {
        WithConnection.run(c -> {
            // Use existing seeded status 'active'
            // Insert multiple customers using TestInsert
            var testInsert = new TestInsert(new Random(0));
            var customer1 = testInsert.Customers(
                "password123".getBytes(),
                "john@example.com",
                "John",
                "Doe",
                new UseDefault<>(),
                new Provided<>(ACTIVE_STATUS),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                c
            );
            var customer2 = testInsert.Customers(
                "password456".getBytes(),
                "jane@example.com",
                "Jane",
                "Smith",
                new UseDefault<>(),
                new Provided<>(ACTIVE_STATUS),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>(),
                c
            );

            var all = customersRepo.selectAll(c);
            assertEquals(2, all.size());
        });
    }

    @Test
    public void testSelectById() {
        WithConnection.run(c -> {
            // Uses default 'pending' status from database
            var unsaved = new CustomersRowUnsaved("test@example.com", "hash".getBytes(), "Test", "User");
            var inserted = customersRepo.insert(unsaved, c);

            var selected = customersRepo.selectById(inserted.customerId(), c);
            assertTrue(selected.isPresent());
            assertEquals("test@example.com", selected.get().email());
            assertEquals("Test", selected.get().firstName());
            assertEquals("User", selected.get().lastName());
        });
    }

    @Test
    public void testSelectByIds() {
        WithConnection.run(c -> {
            // Use existing seeded status
            var customer1 = customersRepo.insert(
                new CustomersRowUnsaved("c1@example.com", "hash1".getBytes(), "Customer", "One"),
                c
            );
            var customer2 = customersRepo.insert(
                new CustomersRowUnsaved("c2@example.com", "hash2".getBytes(), "Customer", "Two"),
                c
            );
            var customer3 = customersRepo.insert(
                new CustomersRowUnsaved("c3@example.com", "hash3".getBytes(), "Customer", "Three"),
                c
            );

            var ids = new CustomersId[]{customer1.customerId(), customer3.customerId()};
            var selected = customersRepo.selectByIds(ids, c);

            assertEquals(2, selected.size());
        });
    }

    @Test
    public void testSelectByIdsTracked() {
        WithConnection.run(c -> {
            // Use existing seeded status
            var customer1 = customersRepo.insert(
                new CustomersRowUnsaved("c1@example.com", "hash1".getBytes(), "Customer", "One"),
                c
            );
            var customer2 = customersRepo.insert(
                new CustomersRowUnsaved("c2@example.com", "hash2".getBytes(), "Customer", "Two"),
                c
            );

            var ids = new CustomersId[]{customer1.customerId(), customer2.customerId()};
            Map<CustomersId, CustomersRow> tracked = customersRepo.selectByIdsTracked(ids, c);

            assertEquals(2, tracked.size());
            assertEquals("Customer", tracked.get(customer1.customerId()).firstName());
            assertEquals("Two", tracked.get(customer2.customerId()).lastName());
        });
    }

    @Test
    public void testSelectByUniqueEmail() {
        WithConnection.run(c -> {
            // Use existing seeded 'pending' status (default)
            var inserted = customersRepo.insert(
                new CustomersRowUnsaved("unique@example.com", "hash".getBytes(), "Unique", "User"),
                c
            );

            var selected = customersRepo.selectByUniqueEmail("unique@example.com", c);
            assertTrue(selected.isPresent());
            assertEquals(inserted.customerId(), selected.get().customerId());

            var notFound = customersRepo.selectByUniqueEmail("nonexistent@example.com", c);
            assertFalse(notFound.isPresent());
        });
    }

    @Test
    public void testInsertRow() {
        WithConnection.run(c -> {
            // Use existing seeded 'pending' status (default)
            var unsaved = new CustomersRowUnsaved("insert@example.com", "password".getBytes(), "Insert", "Test");
            var inserted = customersRepo.insert(unsaved, c);

            assertNotNull(inserted.customerId());
            assertEquals("insert@example.com", inserted.email());
            assertEquals("Insert", inserted.firstName());
            assertEquals("Test", inserted.lastName());
            assertEquals("pending", inserted.status().value()); // default status
            assertEquals("bronze", inserted.tier()); // default tier
        });
    }

    @Test
    public void testInsertRowWithAllFields() {
        WithConnection.run(c -> {
            // Use existing seeded 'suspended' status
            var unsaved = new CustomersRowUnsaved(
                "premium@example.com",
                "securepass".getBytes(),
                "Premium",
                "Customer",
                new Provided<>(Optional.of("+1234567890")),
                new Provided<>(new CustomerStatusId("suspended")),
                new Provided<>("gold"),
                new Provided<>(Optional.of("{\"lang\": \"en\"}")),
                new UseDefault<>(),
                new Provided<>(Optional.of("Important customer")),
                new UseDefault<>(),
                new UseDefault<>(),
                new UseDefault<>()
            );

            var inserted = customersRepo.insert(unsaved, c);

            assertEquals("premium@example.com", inserted.email());
            assertEquals(Optional.of("+1234567890"), inserted.phone());
            assertEquals("suspended", inserted.status().value());
            assertEquals("gold", inserted.tier());
            assertEquals(Optional.of("{\"lang\": \"en\"}"), inserted.preferences());
            assertEquals(Optional.of("Important customer"), inserted.notes());
        });
    }

    @Test
    public void testUpdate() {
        WithConnection.run(c -> {
            // Use existing seeded 'pending' status (default)
            var inserted = customersRepo.insert(
                new CustomersRowUnsaved("update@example.com", "hash".getBytes(), "Before", "Update"),
                c
            );

            var updated = inserted
                .withFirstName("After")
                .withLastName("Changed")
                .withTier("silver");

            var success = customersRepo.update(updated, c);
            assertTrue(success);

            var selected = customersRepo.selectById(inserted.customerId(), c);
            assertTrue(selected.isPresent());
            assertEquals("After", selected.get().firstName());
            assertEquals("Changed", selected.get().lastName());
            assertEquals("silver", selected.get().tier());
        });
    }

    @Test
    public void testUpsertInsert() {
        WithConnection.run(c -> {
            // Use existing seeded 'pending' status (default)
            // First insert a row to get a valid customerId
            var initial = customersRepo.insert(
                new CustomersRowUnsaved("upsert@example.com", "hash".getBytes(), "Upsert", "Test"),
                c
            );

            // Delete it
            customersRepo.deleteById(initial.customerId(), c);

            // Now upsert with a new row (using same id which should work for new insert)
            var row = new CustomersRow(
                initial.customerId(),
                "upsert2@example.com",
                "newhash".getBytes(),
                "New",
                "Upsert",
                Optional.empty(),
                new CustomerStatusId("pending"),
                "bronze",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                initial.createdAt(),
                initial.updatedAt(),
                Optional.empty()
            );

            var upserted = customersRepo.upsert(row, c);
            assertEquals("upsert2@example.com", upserted.email());
        });
    }

    @Test
    public void testUpsertUpdate() {
        WithConnection.run(c -> {
            // Use existing seeded 'pending' status (default)
            var inserted = customersRepo.insert(
                new CustomersRowUnsaved("upsert.update@example.com", "hash".getBytes(), "Before", "Upsert"),
                c
            );

            var modified = inserted
                .withFirstName("After")
                .withTier("gold");

            var upserted = customersRepo.upsert(modified, c);
            assertEquals("After", upserted.firstName());
            assertEquals("gold", upserted.tier());

            // Verify it's the same row
            assertEquals(inserted.customerId(), upserted.customerId());
        });
    }

    @Test
    public void testUpsertBatch() {
        WithConnection.run(c -> {
            // Use existing seeded 'pending' status (default)
            var c1 = customersRepo.insert(
                new CustomersRowUnsaved("batch1@example.com", "hash1".getBytes(), "Batch", "One"),
                c
            );
            var c2 = customersRepo.insert(
                new CustomersRowUnsaved("batch2@example.com", "hash2".getBytes(), "Batch", "Two"),
                c
            );

            // Modify both
            var m1 = c1.withFirstName("Modified1");
            var m2 = c2.withFirstName("Modified2");

            var upserted = customersRepo.upsertBatch(List.of(m1, m2).iterator(), c);
            assertEquals(2, upserted.size());

            // Verify changes
            var s1 = customersRepo.selectById(c1.customerId(), c);
            var s2 = customersRepo.selectById(c2.customerId(), c);
            assertEquals("Modified1", s1.get().firstName());
            assertEquals("Modified2", s2.get().firstName());
        });
    }

    @Test
    public void testDeleteById() {
        WithConnection.run(c -> {
            // Use existing seeded 'pending' status (default)
            var inserted = customersRepo.insert(
                new CustomersRowUnsaved("delete@example.com", "hash".getBytes(), "Delete", "Me"),
                c
            );

            var deleted = customersRepo.deleteById(inserted.customerId(), c);
            assertTrue(deleted);

            var selected = customersRepo.selectById(inserted.customerId(), c);
            assertFalse(selected.isPresent());
        });
    }

    @Test
    public void testDeleteByIds() {
        WithConnection.run(c -> {
            // Use existing seeded 'pending' status (default)
            var c1 = customersRepo.insert(
                new CustomersRowUnsaved("del1@example.com", "hash1".getBytes(), "Del", "One"),
                c
            );
            var c2 = customersRepo.insert(
                new CustomersRowUnsaved("del2@example.com", "hash2".getBytes(), "Del", "Two"),
                c
            );
            var c3 = customersRepo.insert(
                new CustomersRowUnsaved("del3@example.com", "hash3".getBytes(), "Del", "Three"),
                c
            );

            var ids = new CustomersId[]{c1.customerId(), c3.customerId()};
            var count = customersRepo.deleteByIds(ids, c);
            assertEquals(Integer.valueOf(2), count);

            var remaining = customersRepo.selectAll(c);
            assertEquals(1, remaining.size());
            assertEquals(c2.customerId(), remaining.get(0).customerId());
        });
    }

    @Test
    public void testDSLSelect() {
        WithConnection.run(c -> {
            // Use existing seeded 'pending' status (default)
            customersRepo.insert(
                new CustomersRowUnsaved("dsl1@example.com", "hash1".getBytes(), "Alice", "Smith"),
                c
            );
            customersRepo.insert(
                new CustomersRowUnsaved("dsl2@example.com", "hash2".getBytes(), "Bob", "Jones"),
                c
            );
            customersRepo.insert(
                new CustomersRowUnsaved("dsl3@example.com", "hash3".getBytes(), "Alice", "Brown"),
                c
            );

            // Test select with where clause
            var alices = customersRepo.select()
                .where(f -> f.firstName().isEqual("Alice"))
                .toList(c);
            assertEquals(2, alices.size());

            // Test select with multiple conditions
            var specificAlice = customersRepo.select()
                .where(f -> f.firstName().isEqual("Alice"))
                .where(f -> f.lastName().isEqual("Smith"))
                .toList(c);
            assertEquals(1, specificAlice.size());
            assertEquals("Alice", specificAlice.get(0).firstName());
            assertEquals("Smith", specificAlice.get(0).lastName());
        });
    }

    @Test
    public void testDSLUpdate() {
        WithConnection.run(c -> {
            // Use existing seeded 'pending' status (default)
            var customer = customersRepo.insert(
                new CustomersRowUnsaved("dsl.update@example.com", "hash".getBytes(), "Before", "Update"),
                c
            );

            customersRepo.update()
                .setValue(f -> f.firstName(), "After")
                .setValue(f -> f.tier(), "platinum")
                .where(f -> f.customerId().isEqual(customer.customerId()))
                .execute(c);

            var updated = customersRepo.selectById(customer.customerId(), c);
            assertTrue(updated.isPresent());
            assertEquals("After", updated.get().firstName());
            assertEquals("platinum", updated.get().tier());
        });
    }

    @Test
    public void testDSLDelete() {
        WithConnection.run(c -> {
            // Use existing seeded 'pending' status (default)
            customersRepo.insert(
                new CustomersRowUnsaved("dsl.del1@example.com", "hash1".getBytes(), "ToDelete", "One"),
                c
            );
            customersRepo.insert(
                new CustomersRowUnsaved("dsl.del2@example.com", "hash2".getBytes(), "ToDelete", "Two"),
                c
            );
            customersRepo.insert(
                new CustomersRowUnsaved("dsl.keep@example.com", "hash3".getBytes(), "ToKeep", "One"),
                c
            );

            customersRepo.delete()
                .where(f -> f.firstName().isEqual("ToDelete"))
                .execute(c);

            var remaining = customersRepo.selectAll(c);
            assertEquals(1, remaining.size());
            assertEquals("ToKeep", remaining.get(0).firstName());
        });
    }
}
