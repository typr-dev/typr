package adventureworks.public_.users;

import adventureworks.WithConnection;
import adventureworks.customtypes.Defaulted;
import adventureworks.customtypes.TypoInstant;
import adventureworks.customtypes.TypoUUID;
import adventureworks.customtypes.TypoUnknownCitext;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Tests for UsersRepo functionality with UUID and custom types.
 * Equivalent to Scala UsersRepoTest.
 */
public class UsersRepoTest {

    private void testRoundtrip(UsersRepo usersRepo) {
        WithConnection.run(c -> {
            // Use short ctor + withers for optional/defaulted fields
            var before = new UsersRowUnsaved(
                    new UsersId(TypoUUID.randomUUID()),
                    "name",
                    new TypoUnknownCitext("email@asd.no"),
                    "password"
            )
                    .withLastName(Optional.of("last_name"))
                    .withVerifiedOn(Optional.of(TypoInstant.now()))
                    .withCreatedAt(new Defaulted.Provided<>(TypoInstant.now()));

            usersRepo.insert(before, c);

            // Use DSL to query
            var foundList = usersRepo.select()
                    .where(p -> p.userId().isEqual(before.userId()))
                    .toList(c);

            assertEquals(1, foundList.size());
            var after = foundList.get(0);

            // Compare using toRow with the actual createdAt from the database
            assertEquals(before.toRow(() -> after.createdAt()), after);
        });
    }

    private void testInsertUnsavedStreaming(UsersRepo usersRepo) {
        WithConnection.run(c -> {
            List<UsersRowUnsaved> before = new ArrayList<>();
            for (int idx = 0; idx < 10; idx++) {
                // Use short ctor + withers
                before.add(new UsersRowUnsaved(
                        new UsersId(TypoUUID.randomUUID()),
                        "name",
                        new TypoUnknownCitext("email-" + idx + "@asd.no"),
                        "password"
                )
                        .withLastName(Optional.of("last_name"))
                        .withVerifiedOn(Optional.of(TypoInstant.now())));
            }

            usersRepo.insertUnsavedStreaming(before.iterator(), 2, c);

            UsersId[] ids = before.stream().map(UsersRowUnsaved::userId).toArray(UsersId[]::new);
            var afterList = usersRepo.selectByIds(ids, c);

            Map<UsersId, UsersRowUnsaved> beforeById = before.stream()
                    .collect(Collectors.toMap(UsersRowUnsaved::userId, row -> row));

            assertEquals(before.size(), afterList.size());

            for (var after : afterList) {
                var beforeRow = beforeById.get(after.userId());
                assertNotNull("Should find matching before row", beforeRow);
                assertEquals(beforeRow.toRow(() -> after.createdAt()), after);
            }
        });
    }

    @Test
    public void testRoundtripInMemory() {
        // Use mock repo - the Scala uses ??? but we need a real supplier
        testRoundtrip(new UsersRepoMock(unsaved -> unsaved.toRow(() -> TypoInstant.now())));
    }

    @Test
    public void testRoundtripPg() {
        testRoundtrip(new UsersRepoImpl());
    }

    @Test
    public void testInsertUnsavedStreamingInMemory() {
        testInsertUnsavedStreaming(new UsersRepoMock(unsaved -> unsaved.toRow(() -> TypoInstant.now())));
    }

    @Test
    public void testInsertUnsavedStreamingPg() {
        // Check PostgreSQL version first
        boolean shouldRun = WithConnection.apply(c -> {
            var versionResult = typo.runtime.Fragment.lit("SELECT VERSION()")
                    .query(typo.runtime.RowParsers.of(typo.runtime.PgTypes.text, s -> s, s -> new Object[]{s}).first())
                    .runUnchecked(c);

            if (versionResult.isEmpty()) {
                System.err.println("Could not determine PostgreSQL version");
                return false;
            }

            String versionString = versionResult.get();
            String[] parts = versionString.split(" ");
            double version = Double.parseDouble(parts[1].split("\\.")[0]);

            if (version < 16) {
                System.err.println("Skipping testInsertUnsavedStreaming pg because version " + version + " < 16");
                return false;
            }
            return true;
        });

        if (shouldRun) {
            testInsertUnsavedStreaming(new UsersRepoImpl());
        }
    }
}
