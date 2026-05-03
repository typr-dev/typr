package dev.typr.dsl;

/**
 * Scala-friendly abstract class wrapper around FieldsExpr. Scala has issues extending Java sealed
 * interfaces directly, but can properly extend abstract classes. This provides a cleaner extension
 * point for generated Scala code.
 *
 * <p>This is used by generated Scala code when extending Fields traits.
 *
 * @param <Row> The corresponding row type for this fields structure
 */
public abstract class FieldsExpr0<Row> implements FieldsExpr<Row> {}
