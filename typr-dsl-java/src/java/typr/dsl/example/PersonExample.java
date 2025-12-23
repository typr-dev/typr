package typr.dsl.example;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import typr.dsl.*;
import typr.dsl.Bijection;
import typr.runtime.PgType;
import typr.runtime.PgTypes;

/**
 * Example showing how the Java DSL might be used with generated code. This demonstrates the
 * type-safe query building capabilities.
 */
public class PersonExample {

  // Example generated fields structure with PgType instances
  public static class PersonFields {
    private final String tableAlias;

    // PgType instances for each field
    public static final PgType<Long> personIdType = PgTypes.int8;
    public static final PgType<String> firstNameType = PgTypes.text;
    public static final PgType<String> lastNameType = PgTypes.text;
    public static final PgType<Integer> ageType = PgTypes.int4;
    public static final PgType<String> emailType = PgTypes.text;

    public PersonFields(String tableAlias) {
      this.tableAlias = tableAlias;
    }

    public SqlExpr.IdField<Long, PersonRow> personId() {
      return new SqlExpr.IdField<>(
          List.of(Path.of(tableAlias)),
          "person_id",
          PersonRow::personId,
          Optional.empty(), // sqlReadCast
          Optional.empty(), // sqlWriteCast
          PersonRow::withPersonId, // setter
          personIdType);
    }

    public SqlExpr.Field<String, PersonRow> firstName() {
      return new SqlExpr.Field<>(
          List.of(Path.of(tableAlias)),
          "first_name",
          PersonRow::firstName,
          Optional.empty(), // sqlReadCast
          Optional.empty(), // sqlWriteCast
          PersonRow::withFirstName, // setter
          firstNameType);
    }

    public SqlExpr.Field<String, PersonRow> lastName() {
      return new SqlExpr.Field<>(
          List.of(Path.of(tableAlias)),
          "last_name",
          PersonRow::lastName,
          Optional.empty(), // sqlReadCast
          Optional.empty(), // sqlWriteCast
          PersonRow::withLastName, // setter
          lastNameType);
    }

    public SqlExpr.Field<Integer, PersonRow> age() {
      return new SqlExpr.Field<>(
          List.of(Path.of(tableAlias)),
          "age",
          PersonRow::age,
          Optional.empty(), // sqlReadCast
          Optional.empty(), // sqlWriteCast
          PersonRow::withAge, // setter
          ageType);
    }

    public SqlExpr.OptField<String, PersonRow> email() {
      return new SqlExpr.OptField<>(
          List.of(Path.of(tableAlias)),
          "email",
          PersonRow::email,
          Optional.empty(), // sqlReadCast
          Optional.empty(), // sqlWriteCast
          PersonRow::withEmail, // setter
          emailType);
    }
  }

  // Example row class
  public record PersonRow(
      Long personId, String firstName, String lastName, Integer age, Optional<String> email) {
    public PersonRow withPersonId(Long personId) {
      return new PersonRow(personId, firstName, lastName, age, email);
    }

    public PersonRow withFirstName(String firstName) {
      return new PersonRow(personId, firstName, lastName, age, email);
    }

    public PersonRow withLastName(String lastName) {
      return new PersonRow(personId, firstName, lastName, age, email);
    }

    public PersonRow withAge(Integer age) {
      return new PersonRow(personId, firstName, lastName, age, email);
    }

    public PersonRow withEmail(Optional<String> email) {
      return new PersonRow(personId, firstName, lastName, age, email);
    }
  }

  // Example repository interface
  public interface PersonRepo {
    SelectBuilder<PersonFields, PersonRow> select();

    UpdateBuilder<PersonFields, PersonRow> update();

    DeleteBuilder<PersonFields, PersonRow> delete();

    PersonRow insert(PersonRow row);
  }

  // Example usage with typed fields
  public static void exampleQueries(PersonRepo repo, Connection conn) {
    // Simple select with where clause - PgType is automatically used
    List<PersonRow> adults = repo.select().where(p -> p.age().greaterThanOrEqual(18)).toList(conn);

    // Select with multiple conditions
    List<PersonRow> smiths =
        repo.select()
            .where(p -> p.lastName().isEqual("Smith"))
            .where(p -> p.age().greaterThan(25))
            .orderBy(p -> SortOrder.asc(p.firstName()))
            .limit(10)
            .toList(conn);

    // Using LIKE operator
    List<PersonRow> johnsAndJanes =
        repo.select().where(p -> p.firstName().like("Jo%", Bijection.asString())).toList(conn);

    // Complex boolean logic
    List<PersonRow> complexQuery =
        repo.select()
            .where(p -> p.age().lessThan(18).or(p.age().greaterThan(65), Bijection.asBool()))
            .where(p -> p.email().isNull().not(Bijection.asBool()))
            .toList(conn);

    // Update example with proper type
    int updated =
        repo.update()
            .set(PersonFields::age, 30, PersonFields.ageType)
            .where(p -> p.personId().isEqual(123L))
            .execute(conn);

    // Delete example
    int deleted = repo.delete().where(p -> p.age().lessThan(0)).execute(conn);

    // Using IN operator with array
    List<PersonRow> specificPeople =
        repo.select().where(p -> p.personId().in(1L, 2L, 3L, 4L)).toList(conn);

    // Working with optional fields
    List<PersonRow> withEmail =
        repo.select()
            .where(p -> p.email().isNull().not(Bijection.asBool()))
            .orderBy(p -> SortOrder.desc(p.email()))
            .toList(conn);
  }

  // Example with joins
  public static void joinExample(PersonRepo personRepo, AddressRepo addressRepo, Connection conn) {

    // Assuming Address has similar structure
    var query =
        personRepo
            .select()
            .join(addressRepo.select())
            .<PersonFields, PersonRow>on(
                tuple -> {
                  PersonFields person = tuple._1();
                  AddressFields address = tuple._2();
                  // TypedFields can compare directly with each other's values
                  return person.personId().isEqual(address.personId());
                });

    List<Tuple2<PersonRow, AddressRow>> results = query.toList(conn);

    // Left join example
    var leftJoinQuery =
        personRepo
            .select()
            .join(addressRepo.select())
            .<PersonFields, PersonRow>leftOn(
                tuple -> {
                  PersonFields person = tuple._1();
                  AddressFields address = tuple._2();
                  return person.personId().isEqual(address.personId());
                });

    List<Tuple2<PersonRow, Optional<AddressRow>>> leftResults = leftJoinQuery.toList(conn);
  }

  // Stub for address types
  public interface AddressRepo {
    SelectBuilder<AddressFields, AddressRow> select();
  }

  public static class AddressFields {
    public SqlExpr.Field<Long, AddressRow> personId() {
      return new SqlExpr.Field<>(
          List.of(Path.of("a")),
          "person_id",
          AddressRow::personId,
          Optional.empty(), // sqlReadCast
          Optional.empty(), // sqlWriteCast
          AddressRow::withPersonId, // setter
          PersonFields.personIdType // reuse the same PgType
          );
    }
  }

  public record AddressRow(Long addressId, Long personId, String street) {
    public AddressRow withPersonId(Long personId) {
      return new AddressRow(addressId, personId, street);
    }
  }
}
