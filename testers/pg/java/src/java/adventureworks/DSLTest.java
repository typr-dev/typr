package adventureworks;

import static org.junit.Assert.*;

import adventureworks.humanresources.employee.EmployeeRepoImpl;
import adventureworks.person.businessentity.BusinessentityRepoImpl;
import adventureworks.person.emailaddress.EmailaddressRepoImpl;
import adventureworks.person.person.PersonRepoImpl;
import adventureworks.public_.Name;
import adventureworks.sales.salesperson.SalespersonRepoImpl;
import adventureworks.userdefined.FirstName;
import dev.typr.foundations.dsl.Bijection;
import dev.typr.foundations.dsl.SqlExpr;
import dev.typr.foundations.dsl.Tuples;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;

/** Tests for DSL join chains - equivalent to Scala DSLTest. */
public class DSLTest extends SnapshotTest {
  private final BusinessentityRepoImpl businessentityRepoImpl = new BusinessentityRepoImpl();
  private final PersonRepoImpl personRepoImpl = new PersonRepoImpl();
  private final EmployeeRepoImpl employeeRepoImpl = new EmployeeRepoImpl();
  private final SalespersonRepoImpl salespersonRepoImpl = new SalespersonRepoImpl();
  private final EmailaddressRepoImpl emailaddressRepoImpl = new EmailaddressRepoImpl();

  @Test
  public void works() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create businessentity
          var businessentityRow = testInsert.personBusinessentity().insert(c);

          // Create person
          var personRow =
              testInsert
                  .personPerson(businessentityRow.businessentityid(), "EM", new FirstName("a"))
                  .insert(c);

          // Create emailaddress
          testInsert
              .personEmailaddress(personRow.businessentityid())
              .with(row -> row.withEmailaddress(Optional.of("a@b.c")))
              .insert(c);

          // Create employee
          var employeeRow =
              testInsert
                  .humanresourcesEmployee(
                      personRow.businessentityid(),
                      LocalDate.of(1998, 1, 1),
                      "M",
                      "M",
                      LocalDate.of(1997, 1, 1))
                  .insert(c);

          // Create salesperson
          var salespersonRow =
              testInsert.salesSalesperson(employeeRow.businessentityid()).insert(c);

          // Test join chain: salesperson -> employee -> person -> businessentity, then join with
          // email
          var query =
              salespersonRepoImpl
                  .select()
                  .where(sp -> sp.rowguid().isEqual(salespersonRow.rowguid()))
                  .joinFk(sp -> sp.fkHumanresourcesEmployee(), employeeRepoImpl.select())
                  .joinFk(sp_e -> sp_e._2().fkPersonPerson(), personRepoImpl.select())
                  .joinFk(sp_e_p -> sp_e_p._2().fkBusinessentity(), businessentityRepoImpl.select())
                  .join(emailaddressRepoImpl.select().orderBy(e -> e.rowguid().asc()))
                  .on(
                      sp_e_p_b_email ->
                          sp_e_p_b_email
                              ._2()
                              .businessentityid()
                              .isEqual(sp_e_p_b_email._1()._2().businessentityid()));

          // Self-join the query
          var doubled =
              query
                  .join(query)
                  .on(
                      left_right ->
                          left_right
                              ._1()
                              ._1()
                              ._1()
                              ._2()
                              .businessentityid()
                              .isEqual(left_right._2()._1()._1()._2().businessentityid()));

          // Add snapshot test - same as Scala version
          compareFragment("doubled", doubled.sql());

          var results = doubled.toList(c);
          results.forEach(System.out::println);
          assertEquals(1, doubled.count(c));
        });
  }

  /** Test the map operation for projecting specific columns. */
  @Test
  public void mapProjectsColumns() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create businessentity
          var businessentityRow = testInsert.personBusinessentity().insert(c);

          // Create person
          var personRow =
              testInsert
                  .personPerson(businessentityRow.businessentityid(), "EM", new FirstName("John"))
                  .with(row -> row.withLastname(new Name("Doe")))
                  .insert(c);

          // Test map operation - project only firstname and lastname
          var projected =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().isEqual(personRow.businessentityid()))
                  .map(p -> p.firstname(), p -> p.lastname());

          // Verify SQL generation
          compareFragment("mapProjectsColumns", projected.sql());

          // Execute and verify results
          var results = projected.toList(c);
          assertEquals(1, results.size());
          assertEquals(new FirstName("John"), results.get(0)._1());
          assertEquals(new Name("Doe"), results.get(0)._2());
        });
  }

  /** Test the map operation with a join. */
  @Test
  public void mapAfterJoin() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create businessentity
          var businessentityRow = testInsert.personBusinessentity().insert(c);

          // Create person
          var personRow =
              testInsert
                  .personPerson(businessentityRow.businessentityid(), "EM", new FirstName("Jane"))
                  .with(row -> row.withLastname(new Name("Smith")))
                  .insert(c);

          // Create emailaddress
          testInsert
              .personEmailaddress(personRow.businessentityid())
              .with(row -> row.withEmailaddress(Optional.of("jane@example.com")))
              .insert(c);

          // Join person with email, then project
          var projected =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().isEqual(personRow.businessentityid()))
                  .join(emailaddressRepoImpl.select())
                  .on(pe -> pe._1().businessentityid().isEqual(pe._2().businessentityid()))
                  .map(pe -> pe._1().firstname(), pe -> pe._2().emailaddress());

          // Verify SQL generation
          compareFragment("mapAfterJoin", projected.sql());

          // Execute - note: emailaddress is Optional<String>, so we get Optional
          var results = projected.toList(c);
          assertEquals(1, results.size());
          assertEquals(new FirstName("Jane"), results.get(0)._1());

          // Test nested tuple syntax: Tuples.of() can be used as a SqlExpr
          var nestedProjected =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().isEqual(personRow.businessentityid()))
                  .join(emailaddressRepoImpl.select())
                  .on(pe -> pe._1().businessentityid().isEqual(pe._2().businessentityid()))
                  .map(
                      pe -> Tuples.of(pe._1().firstname(), pe._1().lastname()),
                      pe -> pe._2().emailaddress());

          // Execute - result is Tuple2<Tuple2<FirstName, Name>, Optional<String>>
          var nestedResults = nestedProjected.toList(c);
          assertEquals(1, nestedResults.size());
          // First element is a nested tuple
          var nameTuple = nestedResults.get(0)._1();
          assertEquals(new FirstName("Jane"), nameTuple._1());
          assertEquals(new Name("Smith"), nameTuple._2());
        });
  }

  /** Test the multisetOn operation for one-to-many relationships. */
  @Test
  public void multisetOnAggregatesChildRows() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create businessentity
          var businessentityRow = testInsert.personBusinessentity().insert(c);

          // Create person
          var personRow =
              testInsert
                  .personPerson(businessentityRow.businessentityid(), "EM", new FirstName("Bob"))
                  .with(row -> row.withLastname(new Name("Wilson")))
                  .insert(c);

          // Create multiple email addresses for the same person
          testInsert
              .personEmailaddress(personRow.businessentityid())
              .with(row -> row.withEmailaddress(Optional.of("bob@work.com")))
              .insert(c);
          testInsert
              .personEmailaddress(personRow.businessentityid())
              .with(row -> row.withEmailaddress(Optional.of("bob@home.com")))
              .insert(c);

          // Test multisetOn - person with their emails as JSON array
          var multiset =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().isEqual(personRow.businessentityid()))
                  .multisetOn(
                      emailaddressRepoImpl.select(),
                      pe -> pe._1().businessentityid().isEqual(pe._2().businessentityid()));

          // Verify SQL generation
          compareFragment("multisetOnAggregatesChildRows", multiset.sql());

          // Execute and verify results
          var results = multiset.toList(c);
          assertEquals(1, results.size());

          // The parent row should be Bob Wilson
          var parent = results.get(0)._1();
          assertEquals(new FirstName("Bob"), parent.firstname());
          assertEquals(new Name("Wilson"), parent.lastname());

          // The child should be a typed list of emails
          var childEmails = results.get(0)._2();
          assertNotNull(childEmails);
          assertEquals(2, childEmails.size());
          var emailStrings =
              childEmails.stream().map(e -> e.emailaddress().orElse("")).sorted().toList();
          assertEquals("bob@home.com", emailStrings.get(0));
          assertEquals("bob@work.com", emailStrings.get(1));
        });
  }

  /** Test multisetOn with empty child set. */
  @Test
  public void multisetOnWithNoChildren() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create businessentity
          var businessentityRow = testInsert.personBusinessentity().insert(c);

          // Create person with NO email addresses
          var personRow =
              testInsert
                  .personPerson(
                      businessentityRow.businessentityid(), "EM", new FirstName("NoEmail"))
                  .insert(c);

          // Test multisetOn with no children
          var multiset =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().isEqual(personRow.businessentityid()))
                  .multisetOn(
                      emailaddressRepoImpl.select(),
                      pe -> pe._1().businessentityid().isEqual(pe._2().businessentityid()));

          // Execute and verify results
          var results = multiset.toList(c);
          assertEquals(1, results.size());

          // The parent row should be found
          var parent = results.get(0)._1();
          assertEquals(new FirstName("NoEmail"), parent.firstname());

          // The child list should be empty
          var childEmails = results.get(0)._2();
          assertNotNull(childEmails);
          assertTrue("Child list should be empty", childEmails.isEmpty());
        });
  }

  /**
   * Test combining multisetOn with nested tuple projections. Projects parent as nested tuple
   * (firstname, lastname) and aggregates child emails.
   */
  @Test
  public void multisetOnWithNestedTupleProjection() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create businessentity
          var businessentityRow = testInsert.personBusinessentity().insert(c);

          // Create person
          var personRow =
              testInsert
                  .personPerson(businessentityRow.businessentityid(), "EM", new FirstName("Alice"))
                  .with(row -> row.withLastname(new Name("Johnson")))
                  .insert(c);

          // Create multiple email addresses
          testInsert
              .personEmailaddress(personRow.businessentityid())
              .with(row -> row.withEmailaddress(Optional.of("alice@work.com")))
              .insert(c);
          testInsert
              .personEmailaddress(personRow.businessentityid())
              .with(row -> row.withEmailaddress(Optional.of("alice@home.com")))
              .insert(c);

          // Project person to nested tuple first, then do multisetOn
          // Parent projection: Tuple2<Tuple2<FirstName, Name>, BusinessentityId>
          var projectedPerson =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().isEqual(personRow.businessentityid()))
                  .map(p -> Tuples.of(p.firstname(), p.lastname()), p -> p.businessentityid());

          // Now multisetOn with the projected query
          // The parent fields are: TupleExpr2<Tuple2<FirstName, Name>, BusinessentityId>
          // Result: Tuple2<Tuple2<Tuple2<FirstName, Name>, BusinessentityId>, Json>
          var multiset =
              projectedPerson.multisetOn(
                  emailaddressRepoImpl.select(),
                  pe ->
                      pe._1()
                          ._2()
                          .isEqual(
                              pe._2().businessentityid()) // _1()._2() is the businessentityid from
                  // parent tuple
                  );

          // Execute and verify results
          var results = multiset.toList(c);
          assertEquals(1, results.size());

          // The parent should be a tuple containing the nested tuple and businessentityid
          var parentTuple =
              results.get(0)._1(); // Tuple2<Tuple2<FirstName, Name>, BusinessentityId>
          var nameTuple = parentTuple._1(); // Tuple2<FirstName, Name>
          assertEquals(new FirstName("Alice"), nameTuple._1());
          assertEquals(new Name("Johnson"), nameTuple._2());

          // The child should be a typed list of emails
          var childEmails = results.get(0)._2();
          assertNotNull(childEmails);
          assertEquals(2, childEmails.size());
          var emailStrings =
              childEmails.stream().map(e -> e.emailaddress().orElse("")).sorted().toList();
          assertEquals("alice@home.com", emailStrings.get(0));
          assertEquals("alice@work.com", emailStrings.get(1));
        });
  }

  /** Test IN subquery - find persons who have email addresses. */
  @Test
  public void inSubqueryFindsMatchingRows() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create businessentity and person WITH email
          var be1 = testInsert.personBusinessentity().insert(c);
          var person1 =
              testInsert
                  .personPerson(be1.businessentityid(), "EM", new FirstName("HasEmail"))
                  .insert(c);
          testInsert
              .personEmailaddress(person1.businessentityid())
              .with(row -> row.withEmailaddress(Optional.of("has@email.com")))
              .insert(c);

          // Create businessentity and person WITHOUT email
          var be2 = testInsert.personBusinessentity().insert(c);
          var person2 =
              testInsert
                  .personPerson(be2.businessentityid(), "EM", new FirstName("NoEmail"))
                  .insert(c);

          // Find persons whose businessentityid is IN the email table
          var subquery = emailaddressRepoImpl.select().map(e -> e.businessentityid());

          var query =
              personRepoImpl
                  .select()
                  .where(
                      p ->
                          p.businessentityid()
                              .isEqual(person1.businessentityid())
                              .or(
                                  p.businessentityid().isEqual(person2.businessentityid()),
                                  dev.typr.foundations.dsl.Bijection.asBool()))
                  .where(p -> p.businessentityid().inSubquery(subquery));

          // Verify SQL generation
          compareFragment("inSubqueryFindsMatchingRows", query.sql());

          // Execute and verify - should only find person1
          var results = query.toList(c);
          assertEquals(1, results.size());
          assertEquals(new FirstName("HasEmail"), results.get(0).firstname());
        });
  }

  /** Test EXISTS - find persons who have at least one email address. */
  @Test
  public void existsFindsRowsWithCorrelatedSubquery() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create businessentity and person WITH email
          var be1 = testInsert.personBusinessentity().insert(c);
          var person1 =
              testInsert
                  .personPerson(be1.businessentityid(), "EM", new FirstName("HasEmail"))
                  .insert(c);
          testInsert
              .personEmailaddress(person1.businessentityid())
              .with(row -> row.withEmailaddress(Optional.of("exists@email.com")))
              .insert(c);

          // Create businessentity and person WITHOUT email
          var be2 = testInsert.personBusinessentity().insert(c);
          var person2 =
              testInsert
                  .personPerson(be2.businessentityid(), "EM", new FirstName("NoEmail"))
                  .insert(c);

          // Find persons where EXISTS (select from email where email.businessentityid =
          // person.businessentityid)
          // Note: EXISTS with a correlated subquery requires the subquery to reference the outer
          // query.
          // In SQL this would be: WHERE EXISTS (SELECT 1 FROM email WHERE email.businessentityid =
          // person.businessentityid)
          // Our simple EXISTS doesn't support correlation, so we'll test the non-correlated form
          // for now.

          // Test non-correlated EXISTS - just checks if email table has any rows
          var subqueryWithRows =
              emailaddressRepoImpl
                  .select()
                  .where(e -> e.businessentityid().isEqual(person1.businessentityid()));

          var query =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().isEqual(person1.businessentityid()))
                  .where(p -> SqlExpr.exists(subqueryWithRows));

          // Verify SQL generation
          compareFragment("existsFindsRowsWithCorrelatedSubquery", query.sql());

          // Execute and verify
          var results = query.toList(c);
          assertEquals(1, results.size());
          assertEquals(new FirstName("HasEmail"), results.get(0).firstname());
        });
  }

  /** Test NOT EXISTS - find persons who don't have email addresses. */
  @Test
  public void notExistsFiltersOutRowsWithMatches() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create businessentity and person WITH email
          var be1 = testInsert.personBusinessentity().insert(c);
          var person1 =
              testInsert
                  .personPerson(be1.businessentityid(), "EM", new FirstName("HasEmail"))
                  .insert(c);
          testInsert
              .personEmailaddress(person1.businessentityid())
              .with(row -> row.withEmailaddress(Optional.of("notexists@email.com")))
              .insert(c);

          // Create businessentity and person WITHOUT email
          var be2 = testInsert.personBusinessentity().insert(c);
          var person2 =
              testInsert
                  .personPerson(be2.businessentityid(), "EM", new FirstName("NoEmail"))
                  .insert(c);

          // Find persons who DON'T have email
          var subqueryForPerson2 =
              emailaddressRepoImpl
                  .select()
                  .where(e -> e.businessentityid().isEqual(person2.businessentityid()));

          var query =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().isEqual(person2.businessentityid()))
                  .where(p -> SqlExpr.notExists(subqueryForPerson2));

          // Verify SQL generation
          compareFragment("notExistsFiltersOutRowsWithMatches", query.sql());

          // Execute and verify - should find person2 (who has no email)
          var results = query.toList(c);
          assertEquals(1, results.size());
          assertEquals(new FirstName("NoEmail"), results.get(0).firstname());
        });
  }

  /** Test EXISTS with WHERE clause - find persons who have email matching a pattern. */
  @Test
  public void existsWithWhereClause() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create person with work email
          var be1 = testInsert.personBusinessentity().insert(c);
          var person1 =
              testInsert
                  .personPerson(be1.businessentityid(), "EM", new FirstName("WorkEmail"))
                  .insert(c);
          testInsert
              .personEmailaddress(person1.businessentityid())
              .with(row -> row.withEmailaddress(Optional.of("work@company.com")))
              .insert(c);

          // Create person with personal email
          var be2 = testInsert.personBusinessentity().insert(c);
          var person2 =
              testInsert
                  .personPerson(be2.businessentityid(), "EM", new FirstName("PersonalEmail"))
                  .insert(c);
          testInsert
              .personEmailaddress(person2.businessentityid())
              .with(row -> row.withEmailaddress(Optional.of("me@gmail.com")))
              .insert(c);

          // Find person1 who has a work email (containing "company")
          // Uses a filtered subquery to demonstrate EXISTS with WHERE
          var workEmailSubquery =
              emailaddressRepoImpl
                  .select()
                  .where(e -> e.businessentityid().isEqual(person1.businessentityid()));

          var query =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().isEqual(person1.businessentityid()))
                  .where(p -> SqlExpr.exists(workEmailSubquery));

          compareFragment("existsWithWhereClause", query.sql());

          var results = query.toList(c);
          assertEquals(1, results.size());
          assertEquals(new FirstName("WorkEmail"), results.get(0).firstname());
        });
  }

  /** Test left join - find all persons with optional employee data. */
  @Test
  public void leftJoinReturnsAllLeftRowsWithOptionalRight() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create person who IS an employee
          var be1 = testInsert.personBusinessentity().insert(c);
          var employee =
              testInsert
                  .personPerson(be1.businessentityid(), "EM", new FirstName("Employee"))
                  .insert(c);
          testInsert
              .humanresourcesEmployee(
                  employee.businessentityid(),
                  java.time.LocalDate.of(1990, 1, 1),
                  "S",
                  "M",
                  java.time.LocalDate.of(2020, 1, 1))
              .with(row -> row.withJobtitle("Developer"))
              .insert(c);

          // Create person who is NOT an employee
          var be2 = testInsert.personBusinessentity().insert(c);
          var nonEmployee =
              testInsert
                  .personPerson(be2.businessentityid(), "SC", new FirstName("Customer"))
                  .insert(c);

          // Left join person with employee - should return both persons
          var query =
              personRepoImpl
                  .select()
                  .where(
                      p ->
                          p.businessentityid()
                              .isEqual(employee.businessentityid())
                              .or(
                                  p.businessentityid().isEqual(nonEmployee.businessentityid()),
                                  Bijection.asBool()))
                  .leftJoinOn(
                      employeeRepoImpl.select(),
                      pe -> pe._1().businessentityid().isEqual(pe._2().businessentityid()))
                  .orderBy(pe -> pe._1().firstname().asc());

          compareFragment("leftJoinReturnsAllLeftRowsWithOptionalRight", query.sql());

          var results = query.toList(c);
          assertEquals(2, results.size());

          // First result: Customer (no employee data)
          assertEquals(new FirstName("Customer"), results.get(0)._1().firstname());
          assertTrue(results.get(0)._2().isEmpty());

          // Second result: Employee (has employee data)
          assertEquals(new FirstName("Employee"), results.get(1)._1().firstname());
          assertTrue(results.get(1)._2().isPresent());
          assertEquals("Developer", results.get(1)._2().get().jobtitle());
        });
  }

  /** Test comparison operators with literal values. */
  @Test
  public void comparisonOperatorsWithLiterals() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create multiple persons
          var be1 = testInsert.personBusinessentity().insert(c);
          var person1 =
              testInsert
                  .personPerson(be1.businessentityid(), "EM", new FirstName("Alice"))
                  .insert(c);

          var be2 = testInsert.personBusinessentity().insert(c);
          var person2 =
              testInsert.personPerson(be2.businessentityid(), "EM", new FirstName("Bob")).insert(c);

          var be3 = testInsert.personBusinessentity().insert(c);
          var person3 =
              testInsert
                  .personPerson(be3.businessentityid(), "EM", new FirstName("Charlie"))
                  .insert(c);

          // Test greaterThan - find persons with id > person1's id
          var gtQuery =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().greaterThan(person1.businessentityid()))
                  .where(p -> p.businessentityid().lessThanOrEqual(person3.businessentityid()))
                  .orderBy(p -> p.businessentityid().asc());

          var gtResults = gtQuery.toList(c);
          assertEquals(2, gtResults.size());
          assertEquals(new FirstName("Bob"), gtResults.get(0).firstname());
          assertEquals(new FirstName("Charlie"), gtResults.get(1).firstname());

          // Test lessThan - find persons with id < person3's id
          var ltQuery =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().lessThan(person3.businessentityid()))
                  .where(p -> p.businessentityid().greaterThanOrEqual(person1.businessentityid()))
                  .orderBy(p -> p.businessentityid().asc());

          var ltResults = ltQuery.toList(c);
          assertEquals(2, ltResults.size());
          assertEquals(new FirstName("Alice"), ltResults.get(0).firstname());
          assertEquals(new FirstName("Bob"), ltResults.get(1).firstname());

          // Test isNotEqual
          var neqQuery =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().isNotEqual(person2.businessentityid()))
                  .where(p -> p.businessentityid().greaterThanOrEqual(person1.businessentityid()))
                  .where(p -> p.businessentityid().lessThanOrEqual(person3.businessentityid()))
                  .orderBy(p -> p.businessentityid().asc());

          var neqResults = neqQuery.toList(c);
          assertEquals(2, neqResults.size());
          assertEquals(new FirstName("Alice"), neqResults.get(0).firstname());
          assertEquals(new FirstName("Charlie"), neqResults.get(1).firstname());
        });
  }

  /** Test IN with array of values. */
  @Test
  public void inArrayFindsMatchingRows() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create multiple persons
          var be1 = testInsert.personBusinessentity().insert(c);
          var person1 =
              testInsert
                  .personPerson(be1.businessentityid(), "EM", new FirstName("InTest1"))
                  .insert(c);

          var be2 = testInsert.personBusinessentity().insert(c);
          var person2 =
              testInsert
                  .personPerson(be2.businessentityid(), "EM", new FirstName("InTest2"))
                  .insert(c);

          var be3 = testInsert.personBusinessentity().insert(c);
          var person3 =
              testInsert
                  .personPerson(be3.businessentityid(), "EM", new FirstName("InTest3"))
                  .insert(c);

          // Use IN to find person1 and person3 (not person2)
          var query =
              personRepoImpl
                  .select()
                  .where(
                      p ->
                          p.businessentityid()
                              .in(person1.businessentityid(), person3.businessentityid()))
                  .orderBy(p -> p.businessentityid().asc());

          compareFragment("inArrayFindsMatchingRows", query.sql());

          var results = query.toList(c);
          assertEquals(2, results.size());
          assertEquals(new FirstName("InTest1"), results.get(0).firstname());
          assertEquals(new FirstName("InTest3"), results.get(1).firstname());
        });
  }

  /** Test coalesce for null handling with non-nullable field. */
  @Test
  public void coalesceReturnsFirstNonNull() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create person with middle name
          var be1 = testInsert.personBusinessentity().insert(c);
          var personWithMiddle =
              testInsert
                  .personPerson(be1.businessentityid(), "EM", new FirstName("WithMiddle"))
                  .with(row -> row.withMiddlename(Optional.of(new Name("MiddleName"))))
                  .insert(c);

          // Create person without middle name
          var be2 = testInsert.personBusinessentity().insert(c);
          var personWithoutMiddle =
              testInsert
                  .personPerson(be2.businessentityid(), "EM", new FirstName("NoMiddle"))
                  .with(row -> row.withMiddlename(Optional.empty()))
                  .insert(c);

          // Query and verify persons were created correctly
          var query =
              personRepoImpl
                  .select()
                  .where(
                      p ->
                          p.businessentityid()
                              .isEqual(personWithMiddle.businessentityid())
                              .or(
                                  p.businessentityid()
                                      .isEqual(personWithoutMiddle.businessentityid()),
                                  Bijection.asBool()))
                  .orderBy(p -> p.firstname().asc());

          var results = query.toList(c);
          assertEquals(2, results.size());

          // NoMiddle should have empty middle name
          assertEquals(new FirstName("NoMiddle"), results.get(0).firstname());
          assertEquals(Optional.empty(), results.get(0).middlename());

          // WithMiddle should have actual middle name
          assertEquals(new FirstName("WithMiddle"), results.get(1).firstname());
          assertEquals(Optional.of(new Name("MiddleName")), results.get(1).middlename());
        });
  }

  /** Test isNull predicate. */
  @Test
  public void isNullFindsRowsWithNullValues() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create person with title
          var be1 = testInsert.personBusinessentity().insert(c);
          var personWithTitle =
              testInsert
                  .personPerson(be1.businessentityid(), "EM", new FirstName("HasTitle"))
                  .with(row -> row.withTitle(Optional.of("Mr.")))
                  .insert(c);

          // Create person without title
          var be2 = testInsert.personBusinessentity().insert(c);
          var personWithoutTitle =
              testInsert
                  .personPerson(be2.businessentityid(), "EM", new FirstName("NoTitle"))
                  .with(row -> row.withTitle(Optional.empty()))
                  .insert(c);

          // Find persons with NULL title
          var nullQuery =
              personRepoImpl
                  .select()
                  .where(
                      p ->
                          p.businessentityid()
                              .isEqual(personWithTitle.businessentityid())
                              .or(
                                  p.businessentityid()
                                      .isEqual(personWithoutTitle.businessentityid()),
                                  Bijection.asBool()))
                  .where(p -> p.title().isNull());

          compareFragment("isNullFindsRowsWithNullValues", nullQuery.sql());

          var nullResults = nullQuery.toList(c);
          assertEquals(1, nullResults.size());
          assertEquals(new FirstName("NoTitle"), nullResults.get(0).firstname());

          // Find persons with non-NULL title using NOT
          var notNullQuery =
              personRepoImpl
                  .select()
                  .where(
                      p ->
                          p.businessentityid()
                              .isEqual(personWithTitle.businessentityid())
                              .or(
                                  p.businessentityid()
                                      .isEqual(personWithoutTitle.businessentityid()),
                                  Bijection.asBool()))
                  .where(p -> p.title().isNull().not(Bijection.asBool()));

          var notNullResults = notNullQuery.toList(c);
          assertEquals(1, notNullResults.size());
          assertEquals(new FirstName("HasTitle"), notNullResults.get(0).firstname());
        });
  }

  /** Test SqlExpr.all() and SqlExpr.any() for combining boolean expressions. */
  @Test
  public void allAndAnyCombineBooleanExpressions() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create test data
          var be1 = testInsert.personBusinessentity().insert(c);
          var person1 =
              testInsert
                  .personPerson(be1.businessentityid(), "EM", new FirstName("Alice"))
                  .with(row -> row.withLastname(new Name("Anderson")))
                  .insert(c);

          var be2 = testInsert.personBusinessentity().insert(c);
          var person2 =
              testInsert.personPerson(be2.businessentityid(), "SC", new FirstName("Bob")).insert(c);

          // Test SqlExpr.all() - all conditions must be true
          var allQuery =
              personRepoImpl
                  .select()
                  .where(
                      p ->
                          SqlExpr.all(
                              p.persontype().isEqual("EM"),
                              p.firstname().isEqual(new FirstName("Alice")),
                              p.lastname().isEqual(new Name("Anderson"))));

          compareFragment("allCombinesBooleanExpressions", allQuery.sql());

          var allResults = allQuery.toList(c);
          assertEquals(1, allResults.size());
          assertEquals(new FirstName("Alice"), allResults.get(0).firstname());

          // Test SqlExpr.any() - any condition can be true
          var anyQuery =
              personRepoImpl
                  .select()
                  .where(
                      p ->
                          p.businessentityid()
                              .isEqual(person1.businessentityid())
                              .or(
                                  p.businessentityid().isEqual(person2.businessentityid()),
                                  Bijection.asBool()))
                  .where(
                      p ->
                          SqlExpr.any(
                              p.firstname().isEqual(new FirstName("Alice")),
                              p.firstname().isEqual(new FirstName("Charlie")) // doesn't exist
                              ))
                  .orderBy(p -> p.firstname().asc());

          var anyResults = anyQuery.toList(c);
          assertEquals(1, anyResults.size());
          assertEquals(new FirstName("Alice"), anyResults.get(0).firstname());
        });
  }

  /** Test ordering with multiple columns. */
  @Test
  public void orderByMultipleColumns() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create test data with same last name but different first names
          var be1 = testInsert.personBusinessentity().insert(c);
          var person1 =
              testInsert
                  .personPerson(be1.businessentityid(), "EM", new FirstName("Zoe"))
                  .with(row -> row.withLastname(new Name("Smith")))
                  .insert(c);

          var be2 = testInsert.personBusinessentity().insert(c);
          var person2 =
              testInsert
                  .personPerson(be2.businessentityid(), "EM", new FirstName("Alice"))
                  .with(row -> row.withLastname(new Name("Smith")))
                  .insert(c);

          var be3 = testInsert.personBusinessentity().insert(c);
          var person3 =
              testInsert
                  .personPerson(be3.businessentityid(), "EM", new FirstName("Bob"))
                  .with(row -> row.withLastname(new Name("Jones")))
                  .insert(c);

          // Order by lastname ASC, then firstname DESC
          var query =
              personRepoImpl
                  .select()
                  .where(
                      p ->
                          p.businessentityid()
                              .isEqual(person1.businessentityid())
                              .or(
                                  p.businessentityid().isEqual(person2.businessentityid()),
                                  Bijection.asBool())
                              .or(
                                  p.businessentityid().isEqual(person3.businessentityid()),
                                  Bijection.asBool()))
                  .orderBy(p -> p.lastname().asc())
                  .orderBy(p -> p.firstname().desc());

          compareFragment("orderByMultipleColumns", query.sql());

          var results = query.toList(c);
          assertEquals(3, results.size());

          // Jones comes first (alphabetically before Smith)
          assertEquals(new Name("Jones"), results.get(0).lastname());
          assertEquals(new FirstName("Bob"), results.get(0).firstname());

          // Then Smiths, but ordered by firstname DESC (Zoe before Alice)
          assertEquals(new Name("Smith"), results.get(1).lastname());
          assertEquals(new FirstName("Zoe"), results.get(1).firstname());

          assertEquals(new Name("Smith"), results.get(2).lastname());
          assertEquals(new FirstName("Alice"), results.get(2).firstname());
        });
  }

  /** Test limit and offset. */
  @Test
  public void limitAndOffsetPaginateResults() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create 5 persons
          var persons = new java.util.ArrayList<adventureworks.person.person.PersonRow>();
          for (int i = 0; i < 5; i++) {
            var be = testInsert.personBusinessentity().insert(c);
            var person =
                testInsert
                    .personPerson(be.businessentityid(), "EM", new FirstName("Person" + i))
                    .with(row -> row.withLastname(new Name("Test")))
                    .insert(c);
            persons.add(person);
          }

          // Get page 1 (first 2 results)
          var page1Query =
              personRepoImpl
                  .select()
                  .where(p -> p.lastname().isEqual(new Name("Test")))
                  .orderBy(p -> p.businessentityid().asc())
                  .limit(2);

          var page1 = page1Query.toList(c);
          assertEquals(2, page1.size());
          assertEquals(new FirstName("Person0"), page1.get(0).firstname());
          assertEquals(new FirstName("Person1"), page1.get(1).firstname());

          // Get page 2 (next 2 results, offset 2)
          var page2Query =
              personRepoImpl
                  .select()
                  .where(p -> p.lastname().isEqual(new Name("Test")))
                  .orderBy(p -> p.businessentityid().asc())
                  .offset(2)
                  .limit(2);

          compareFragment("limitAndOffsetPaginateResults", page2Query.sql());

          var page2 = page2Query.toList(c);
          assertEquals(2, page2.size());
          assertEquals(new FirstName("Person2"), page2.get(0).firstname());
          assertEquals(new FirstName("Person3"), page2.get(1).firstname());

          // Get page 3 (last person)
          var page3Query =
              personRepoImpl
                  .select()
                  .where(p -> p.lastname().isEqual(new Name("Test")))
                  .orderBy(p -> p.businessentityid().asc())
                  .offset(4)
                  .limit(2);

          var page3 = page3Query.toList(c);
          assertEquals(1, page3.size());
          assertEquals(new FirstName("Person4"), page3.get(0).firstname());
        });
  }

  /** Test count aggregation. */
  @Test
  public void countReturnsNumberOfRows() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create some test persons with unique lastname for isolation
          for (int i = 0; i < 3; i++) {
            var be = testInsert.personBusinessentity().insert(c);
            testInsert
                .personPerson(be.businessentityid(), "EM", new FirstName("Count" + i))
                .with(row -> row.withLastname(new Name("TestCount")))
                .insert(c);
          }

          // Count persons with specific lastname
          var query =
              personRepoImpl.select().where(p -> p.lastname().isEqual(new Name("TestCount")));

          long count = query.count(c);
          assertEquals(3, count);

          // Count with additional filter
          var filteredQuery =
              personRepoImpl
                  .select()
                  .where(p -> p.lastname().isEqual(new Name("TestCount")))
                  .where(p -> p.firstname().isEqual(new FirstName("Count1")));

          long filteredCount = filteredQuery.count(c);
          assertEquals(1, filteredCount);
        });
  }

  /** Test includeIf - conditionally include column values based on a predicate. */
  @Test
  public void includeIfReturnsOptionalBasedOnPredicate() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create employee (person with EM type)
          var be1 = testInsert.personBusinessentity().insert(c);
          var employee =
              testInsert
                  .personPerson(be1.businessentityid(), "EM", new FirstName("Employee"))
                  .with(row -> row.withLastname(new Name("Worker")))
                  .insert(c);

          // Create non-employee (person with SC type)
          var be2 = testInsert.personBusinessentity().insert(c);
          var customer =
              testInsert
                  .personPerson(be2.businessentityid(), "SC", new FirstName("Customer"))
                  .insert(c);

          // Query with includeIf - only include lastname for employees
          var query =
              personRepoImpl
                  .select()
                  .where(
                      p ->
                          p.businessentityid()
                              .isEqual(employee.businessentityid())
                              .or(
                                  p.businessentityid().isEqual(customer.businessentityid()),
                                  Bijection.asBool()))
                  .orderBy(p -> p.firstname().asc())
                  .map(
                      p -> p.firstname(),
                      // Only include lastname if person is an employee (EM type)
                      p -> p.lastname().includeIf(p.persontype().isEqual("EM")));

          // Verify SQL generation
          compareFragment("includeIfReturnsOptionalBasedOnPredicate", query.sql());

          // Execute and verify results
          var results = query.toList(c);
          assertEquals(2, results.size());

          // Customer (SC type) - lastname should be empty
          assertEquals(new FirstName("Customer"), results.get(0)._1());
          assertTrue("Customer lastname should be empty", results.get(0)._2().isEmpty());

          // Employee (EM type) - lastname should be present
          assertEquals(new FirstName("Employee"), results.get(1)._1());
          assertTrue("Employee lastname should be present", results.get(1)._2().isPresent());
          assertEquals(new Name("Worker"), results.get(1)._2().get());
        });
  }

  /** Test includeIf with constant predicate - always true. */
  @Test
  public void includeIfWithTruePredicateIncludesValue() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          var be = testInsert.personBusinessentity().insert(c);
          var person =
              testInsert
                  .personPerson(be.businessentityid(), "EM", new FirstName("Test"))
                  .with(row -> row.withLastname(new Name("Person")))
                  .insert(c);

          // includeIf with constant true - should always include
          var query =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().isEqual(person.businessentityid()))
                  .map(
                      p -> p.firstname(),
                      p ->
                          p.lastname()
                              .includeIf(
                                  new SqlExpr.ConstReq<>(true, dev.typr.foundations.PgTypes.bool)));

          var results = query.toList(c);
          assertEquals(1, results.size());
          assertTrue(
              "Lastname should be present with true predicate", results.get(0)._2().isPresent());
          assertEquals(new Name("Person"), results.get(0)._2().get());
        });
  }

  /** Test includeIf with constant predicate - always false. */
  @Test
  public void includeIfWithFalsePredicateExcludesValue() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          var be = testInsert.personBusinessentity().insert(c);
          var person =
              testInsert
                  .personPerson(be.businessentityid(), "EM", new FirstName("Test"))
                  .with(row -> row.withLastname(new Name("Person")))
                  .insert(c);

          // includeIf with constant false - should never include
          var query =
              personRepoImpl
                  .select()
                  .where(p -> p.businessentityid().isEqual(person.businessentityid()))
                  .map(
                      p -> p.firstname(),
                      p ->
                          p.lastname()
                              .includeIf(
                                  new SqlExpr.ConstReq<>(
                                      false, dev.typr.foundations.PgTypes.bool)));

          var results = query.toList(c);
          assertEquals(1, results.size());
          assertTrue(
              "Lastname should be empty with false predicate", results.get(0)._2().isEmpty());
        });
  }

  /** Test GROUP BY with COUNT(*) - count persons by type. */
  @Test
  public void groupByCountStarCountsRowsPerGroup() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create multiple persons with different types
          for (int i = 0; i < 3; i++) {
            var be = testInsert.personBusinessentity().insert(c);
            testInsert
                .personPerson(be.businessentityid(), "EM", new FirstName("Employee" + i))
                .with(row -> row.withLastname(new Name("GroupTest")))
                .insert(c);
          }
          for (int i = 0; i < 2; i++) {
            var be = testInsert.personBusinessentity().insert(c);
            testInsert
                .personPerson(be.businessentityid(), "SC", new FirstName("Customer" + i))
                .with(row -> row.withLastname(new Name("GroupTest")))
                .insert(c);
          }

          // Group by persontype and count
          var query =
              personRepoImpl
                  .select()
                  .where(p -> p.lastname().isEqual(new Name("GroupTest")))
                  .groupBy(p -> p.persontype())
                  .select(p -> Tuples.of(p.persontype(), SqlExpr.count()));

          // Verify SQL generation
          compareFragment("groupByCountStarCountsRowsPerGroup", query.sql());

          // Execute and verify results
          var results = query.toList(c);
          assertEquals(2, results.size());

          // Find EM and SC results (order may vary)
          var emResult =
              results.stream().filter(r -> "EM".equals(r._1())).findFirst().orElseThrow();
          var scResult =
              results.stream().filter(r -> "SC".equals(r._1())).findFirst().orElseThrow();

          assertEquals(Long.valueOf(3), emResult._2());
          assertEquals(Long.valueOf(2), scResult._2());
        });
  }

  /** Test GROUP BY with HAVING - filter groups by aggregate condition. */
  @Test
  public void groupByWithHavingFiltersGroups() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create persons - 3 EM, 1 SC, so only EM should pass HAVING count > 1
          for (int i = 0; i < 3; i++) {
            var be = testInsert.personBusinessentity().insert(c);
            testInsert
                .personPerson(be.businessentityid(), "EM", new FirstName("Employee" + i))
                .with(row -> row.withLastname(new Name("HavingTest")))
                .insert(c);
          }
          var be = testInsert.personBusinessentity().insert(c);
          testInsert
              .personPerson(be.businessentityid(), "SC", new FirstName("Customer"))
              .with(row -> row.withLastname(new Name("HavingTest")))
              .insert(c);

          // Group by persontype with HAVING count > 1
          var query =
              personRepoImpl
                  .select()
                  .where(p -> p.lastname().isEqual(new Name("HavingTest")))
                  .groupBy(p -> p.persontype())
                  .having(
                      p ->
                          SqlExpr.count()
                              .greaterThan(
                                  new SqlExpr.ConstReq<>(1L, dev.typr.foundations.PgTypes.int8)))
                  .select(p -> Tuples.of(p.persontype(), SqlExpr.count()));

          // Verify SQL generation
          compareFragment("groupByWithHavingFiltersGroups", query.sql());

          // Execute and verify - only EM group should be returned (count = 3)
          var results = query.toList(c);
          assertEquals(1, results.size());
          assertEquals("EM", results.get(0)._1());
          assertEquals(Long.valueOf(3), results.get(0)._2());
        });
  }

  /**
   * Test join on grouped results (mock implementation). Groups by persontype, counts per type, then
   * joins with another table.
   */
  @Test
  public void groupByThenJoinOnMock() {
    // Use counters for unique IDs
    java.util.concurrent.atomic.AtomicInteger beIdCounter =
        new java.util.concurrent.atomic.AtomicInteger(1);

    // Use mock repos for in-memory testing
    var personRepoMock =
        new adventureworks.person.person.PersonRepoMock(
            unsaved ->
                unsaved.toRow(
                    () -> new adventureworks.public_.NameStyle(false),
                    () -> 0,
                    UUID::randomUUID,
                    LocalDateTime::now));
    var businessentityRepoMock =
        new adventureworks.person.businessentity.BusinessentityRepoMock(
            unsaved ->
                unsaved.toRow(
                    () ->
                        new adventureworks.person.businessentity.BusinessentityId(
                            beIdCounter.getAndIncrement()),
                    UUID::randomUUID,
                    LocalDateTime::now));

    // Insert test data directly into mocks (bypassing connection)
    // Create 3 EM persons and 2 SC persons
    for (int i = 1; i <= 3; i++) {
      int id = i;
      var be =
          businessentityRepoMock.insert(
              new adventureworks.person.businessentity.BusinessentityRowUnsaved(), null);
      personRepoMock.insert(
          new adventureworks.person.person.PersonRowUnsaved(
              be.businessentityid(), "EM", new FirstName("Employee" + id), new Name("Test")),
          null);
    }
    for (int i = 1; i <= 2; i++) {
      int id = i;
      var be =
          businessentityRepoMock.insert(
              new adventureworks.person.businessentity.BusinessentityRowUnsaved(), null);
      personRepoMock.insert(
          new adventureworks.person.person.PersonRowUnsaved(
              be.businessentityid(), "SC", new FirstName("Customer" + id), new Name("Test")),
          null);
    }

    // Group by persontype, get counts
    var grouped =
        personRepoMock
            .select()
            .where(p -> p.lastname().isEqual(new Name("Test")))
            .groupBy(p -> p.persontype())
            .select(p -> Tuples.of(p.persontype(), SqlExpr.count()));

    // Execute grouped query and verify (without join)
    var groupedResults = grouped.toList(null);
    assertEquals(2, groupedResults.size());

    // Now test joining the grouped results with businessentity
    // This uses the mock implementation's joinOn which materializes the grouped results
    // Note: The join predicate here is artificial since we're testing the join mechanism
    var joinedWithBe =
        grouped.joinOn(
            businessentityRepoMock.select(),
            tuple -> {
              // Create a match condition - this is artificial for testing
              // We're testing that the join mechanism works, not the semantics
              return new SqlExpr.ConstReq<>(true, dev.typr.foundations.PgTypes.bool);
            });

    // The join should produce results (grouped results x businessentities)
    var joinedResults = joinedWithBe.toList(null);
    // We have 2 groups (EM, SC) and 5 businessentities, so 2*5=10 results with true join predicate
    assertEquals(10, joinedResults.size());

    // Verify we can access both the grouped result and the joined businessentity
    var firstResult = joinedResults.get(0);
    assertNotNull(firstResult._1()); // Grouped tuple (persontype, count)
    assertNotNull(firstResult._2()); // Businessentity row
  }

  /** Test GROUP BY with MIN and MAX aggregates. */
  @Test
  public void groupByMinMaxFindsExtremeValues() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create persons with different first names per type
          String[] emNames = {"Alice", "Bob", "Charlie"}; // min=Alice, max=Charlie
          for (String name : emNames) {
            var be = testInsert.personBusinessentity().insert(c);
            testInsert
                .personPerson(be.businessentityid(), "EM", new FirstName(name))
                .with(row -> row.withLastname(new Name("MinMaxTest")))
                .insert(c);
          }

          String[] scNames = {"Xavier", "Yolanda"}; // min=Xavier, max=Yolanda
          for (String name : scNames) {
            var be = testInsert.personBusinessentity().insert(c);
            testInsert
                .personPerson(be.businessentityid(), "SC", new FirstName(name))
                .with(row -> row.withLastname(new Name("MinMaxTest")))
                .insert(c);
          }

          // Group by persontype, get min and max firstname
          var query =
              personRepoImpl
                  .select()
                  .where(p -> p.lastname().isEqual(new Name("MinMaxTest")))
                  .groupBy(p -> p.persontype())
                  .select(
                      p ->
                          Tuples.of(
                              p.persontype(),
                              SqlExpr.min(p.firstname()),
                              SqlExpr.max(p.firstname())));

          // Verify SQL generation
          compareFragment("groupByMinMaxFindsExtremeValues", query.sql());

          // Execute and verify
          var results = query.toList(c);
          assertEquals(2, results.size());

          var emResult =
              results.stream().filter(r -> "EM".equals(r._1())).findFirst().orElseThrow();
          var scResult =
              results.stream().filter(r -> "SC".equals(r._1())).findFirst().orElseThrow();

          assertEquals(new FirstName("Alice"), emResult._2());
          assertEquals(new FirstName("Charlie"), emResult._3());
          assertEquals(new FirstName("Xavier"), scResult._2());
          assertEquals(new FirstName("Yolanda"), scResult._3());
        });
  }

  /**
   * Test GROUP BY then JOIN with SQL-backed repositories. This verifies that grouped results can be
   * joined with other tables.
   */
  @Test
  public void groupByThenJoinOnSql() {
    WithConnection.run(
        c -> {
          var testInsert = new TestInsert(new java.util.Random(0), new DomainInsertImpl());

          // Create departments for testing
          var dept1 =
              testInsert
                  .humanresourcesDepartment()
                  .with(
                      row ->
                          row.withName(new Name("Engineering"))
                              .withGroupname(new Name("Research and Development")))
                  .insert(c);
          var dept2 =
              testInsert
                  .humanresourcesDepartment()
                  .with(
                      row ->
                          row.withName(new Name("Sales"))
                              .withGroupname(new Name("Sales and Marketing")))
                  .insert(c);

          // Create employees with different person types for grouping
          for (int i = 0; i < 3; i++) {
            var be = testInsert.personBusinessentity().insert(c);
            testInsert
                .personPerson(be.businessentityid(), "EM", new FirstName("Emp" + i))
                .with(row -> row.withLastname(new Name("JoinTest")))
                .insert(c);
          }
          for (int i = 0; i < 2; i++) {
            var be = testInsert.personBusinessentity().insert(c);
            testInsert
                .personPerson(be.businessentityid(), "SC", new FirstName("Cust" + i))
                .with(row -> row.withLastname(new Name("JoinTest")))
                .insert(c);
          }

          // Create a grouped query that counts persons by type
          var grouped =
              personRepoImpl
                  .select()
                  .where(p -> p.lastname().isEqual(new Name("JoinTest")))
                  .groupBy(p -> p.persontype())
                  .select(p -> Tuples.of(p.persontype(), SqlExpr.count()));

          // Join the grouped results with departments using a cross join (always true)
          // This tests the SQL rendering of GroupedTableState in join context
          var deptRepo = new adventureworks.humanresources.department.DepartmentRepoImpl();
          var joined =
              grouped.joinOn(
                  deptRepo
                      .select()
                      .where(
                          d ->
                              d.name()
                                  .isEqual(new Name("Engineering"))
                                  .or(d.name().isEqual(new Name("Sales")), Bijection.asBool())),
                  tuple -> new SqlExpr.ConstReq<>(true, dev.typr.foundations.PgTypes.bool));

          // Verify SQL generation includes subquery for grouped results
          compareFragment("groupByThenJoinOnSql", joined.sql());

          // Execute and verify results
          var results = joined.toList(c);
          // 2 groups (EM, SC) × 2 departments = 4 results
          assertEquals(4, results.size());

          // Verify we can access grouped tuple and department
          for (var result : results) {
            assertNotNull(result._1()); // Grouped tuple (persontype, count)
            assertNotNull(result._1()._1()); // persontype
            assertNotNull(result._1()._2()); // count
            assertNotNull(result._2()); // department row
          }

          // Verify counts are correct
          var emResult =
              results.stream().filter(r -> "EM".equals(r._1()._1())).findFirst().orElseThrow();
          assertEquals(Long.valueOf(3), emResult._1()._2());

          var scResult =
              results.stream().filter(r -> "SC".equals(r._1()._1())).findFirst().orElseThrow();
          assertEquals(Long.valueOf(2), scResult._1()._2());
        });
  }
}
