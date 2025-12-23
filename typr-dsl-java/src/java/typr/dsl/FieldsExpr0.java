package typr.dsl;

/**
 * Abstract class wrapper for FieldsExpr to enable Scala code to extend it as a class.
 *
 * <p>This exists to work around a scala 3 compiler bug concerning inheritance of sealed java
 * interfaces
 */
public abstract class FieldsExpr0<Row> implements FieldsExpr<Row> {}
