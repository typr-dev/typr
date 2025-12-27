package dev.typr.foundations.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Interface for relations - can be implemented by records. Expresses that (tuples of) Fields
 * structures can be joined. This also serves as the type-level connection between Fields and Row.
 *
 * <p>Uses _path to avoid conflicts with tables that have a 'path' column.
 */
public interface RelationStructure<Fields, Row> extends Structure<Fields, Row> {
  List<Path> _path();

  RelationStructure<Fields, Row> withPaths(List<Path> _path);

  @Override
  @SuppressWarnings("unchecked")
  default Fields fields() {
    return (Fields) this;
  }

  @Override
  @SuppressWarnings("unchecked")
  default List<SqlExpr.FieldLike<?, ?>> allFields() {
    // Generated Fields implement both RelationStructure and FieldsBase.
    // FieldsBase.columns() returns the list of fields, so we delegate to it.
    FieldsBase<Row> fieldsBase = (FieldsBase<Row>) this;
    return new ArrayList<>(fieldsBase.columns());
  }

  @Override
  default Structure<Fields, Row> withPath(Path newPath) {
    List<Path> newPaths = new ArrayList<>();
    newPaths.add(newPath);
    newPaths.addAll(_path());
    return withPaths(newPaths);
  }

  @Override
  @SuppressWarnings("unchecked")
  default <T> Optional<T> untypedGetFieldValue(SqlExpr.FieldLike<T, ?> field, Row row) {
    // This cast is necessary because the field's Row type parameter might not
    // match our Row type due to joins and other operations. This mirrors Scala's
    // castRow extension method. The cast is safe within our controlled environment
    // where we ensure fields and rows correspond correctly.
    SqlExpr.FieldLike<T, Row> typedField = (SqlExpr.FieldLike<T, Row>) field;
    return typedField.get(row);
  }
}
