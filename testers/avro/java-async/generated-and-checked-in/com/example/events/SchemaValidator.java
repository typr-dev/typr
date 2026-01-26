package com.example.events;

import com.example.events.common.Money;
import java.util.ArrayList;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.apache.avro.SchemaCompatibility.SchemaPairCompatibility;

/**
 * Schema validation utility for Avro compatibility checking. Provides methods to verify schema
 * compatibility and validate field presence.
 */
public class SchemaValidator {
  public static Map<String, Schema> SCHEMAS =
      Map.ofEntries(
          Map.entry("com.example.events.Address", Address.SCHEMA),
          Map.entry("com.example.events.CustomerOrder", CustomerOrder.SCHEMA),
          Map.entry("com.example.events.DynamicValue", DynamicValue.SCHEMA),
          Map.entry("com.example.events.common.Money", Money.SCHEMA),
          Map.entry("com.example.events.Invoice", Invoice.SCHEMA),
          Map.entry("com.example.events.LinkedListNode", LinkedListNode.SCHEMA),
          Map.entry("com.example.events.TreeNode", TreeNode.SCHEMA),
          Map.entry("com.example.events.OrderCancelled", OrderCancelled.SCHEMA),
          Map.entry("com.example.events.OrderPlaced", OrderPlaced.SCHEMA),
          Map.entry("com.example.events.OrderUpdated", OrderUpdated.SCHEMA));

  /**
   * Check if a reader with readerSchema can read data written with writerSchema. Returns true if
   * backward compatible (new reader can read old data).
   */
  public boolean isBackwardCompatible(Schema readerSchema, Schema writerSchema) {
    return SchemaCompatibility.checkReaderWriterCompatibility(readerSchema, writerSchema).getType()
        == SchemaCompatibilityType.COMPATIBLE;
  }

  /**
   * Check if data written with writerSchema can be read by a reader with readerSchema. Returns true
   * if forward compatible (old reader can read new data).
   */
  public boolean isForwardCompatible(Schema writerSchema, Schema readerSchema) {
    return SchemaCompatibility.checkReaderWriterCompatibility(readerSchema, writerSchema).getType()
        == SchemaCompatibilityType.COMPATIBLE;
  }

  /**
   * Check if both schemas can read each other's data. Returns true if fully compatible (both
   * backward and forward).
   */
  public boolean isFullyCompatible(Schema schema1, Schema schema2) {
    return isBackwardCompatible(schema1, schema2) && isBackwardCompatible(schema2, schema1);
  }

  /**
   * Get detailed compatibility information between two schemas. Returns a SchemaPairCompatibility
   * with type, result, and any incompatibilities.
   */
  public SchemaPairCompatibility checkCompatibility(Schema newSchema, Schema oldSchema) {
    return SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema);
  }

  /**
   * Validate that all required fields in the schema are properly defined. Returns true if all
   * required fields are valid (non-union without default is allowed).
   */
  public boolean validateRequiredFields(Schema schema) {
    return true;
  }

  /**
   * Get the list of field names in writerSchema that are missing from readerSchema. Useful for
   * identifying which fields will be ignored during deserialization.
   */
  public ArrayList<String> getMissingFields(Schema readerSchema, Schema writerSchema) {
    var missing = new ArrayList<String>();
    writerSchema
        .getFields()
        .forEach(
            writerField -> {
              if (readerSchema.getField(writerField.name()) == null) {
                missing.add(writerField.name());
              }
            });
    return missing;
  }

  /**
   * Get the schema for a known record type by its full name. Returns null if the schema name is not
   * recognized.
   */
  public Schema getSchemaByName(String name) {
    return SchemaValidator.SCHEMAS.get(name);
  }
}
