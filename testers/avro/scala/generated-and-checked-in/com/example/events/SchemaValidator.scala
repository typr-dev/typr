package com.example.events

import com.example.events.common.Money
import java.util.ArrayList
import org.apache.avro.Schema
import org.apache.avro.SchemaCompatibility
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType
import org.apache.avro.SchemaCompatibility.SchemaPairCompatibility

/** Schema validation utility for Avro compatibility checking.
 * Provides methods to verify schema compatibility and validate field presence.
 */
class SchemaValidator {
  /** Check if a reader with readerSchema can read data written with writerSchema.
   * Returns true if backward compatible (new reader can read old data).
   */
  def isBackwardCompatible(
    readerSchema: Schema,
    writerSchema: Schema
  ): Boolean = {
    return SchemaCompatibility.checkReaderWriterCompatibility(readerSchema, writerSchema).getType == SchemaCompatibilityType.COMPATIBLE
  }

  /** Check if data written with writerSchema can be read by a reader with readerSchema.
   * Returns true if forward compatible (old reader can read new data).
   */
  def isForwardCompatible(
    writerSchema: Schema,
    readerSchema: Schema
  ): Boolean = {
    return SchemaCompatibility.checkReaderWriterCompatibility(readerSchema, writerSchema).getType == SchemaCompatibilityType.COMPATIBLE
  }

  /** Check if both schemas can read each other's data.
   * Returns true if fully compatible (both backward and forward).
   */
  def isFullyCompatible(
    schema1: Schema,
    schema2: Schema
  ): Boolean = {
    return isBackwardCompatible(schema1, schema2) && isBackwardCompatible(schema2, schema1)
  }

  /** Get detailed compatibility information between two schemas.
   * Returns a SchemaPairCompatibility with type, result, and any incompatibilities.
   */
  def checkCompatibility(
    newSchema: Schema,
    oldSchema: Schema
  ): SchemaPairCompatibility = {
    return SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema)
  }

  /** Validate that all required fields in the schema are properly defined.
   * Returns true if all required fields are valid (non-union without default is allowed).
   */
  def validateRequiredFields(schema: Schema): Boolean = {
    return true
  }

  /** Get the list of field names in writerSchema that are missing from readerSchema.
   * Useful for identifying which fields will be ignored during deserialization.
   */
  def getMissingFields(
    readerSchema: Schema,
    writerSchema: Schema
  ): ArrayList[String] = {
    val missing = new ArrayList[String]()
    writerSchema.getFields.forEach(writerField => { if (readerSchema.getField(writerField.name()) == null) {
      missing.add(writerField.name())
    } })
    return missing
  }

  /** Get the schema for a known record type by its full name.
   * Returns null if the schema name is not recognized.
   */
  def getSchemaByName(name: String): Schema = {
    return SchemaValidator.SCHEMAS.get(name).orNull
  }
}

object SchemaValidator {
  val SCHEMAS: Map[String, Schema] = Map("com.example.events.Address" -> Address.SCHEMA, "com.example.events.CustomerOrder" -> CustomerOrder.SCHEMA, "com.example.events.DynamicValue" -> DynamicValue.SCHEMA, "com.example.events.common.Money" -> Money.SCHEMA, "com.example.events.Invoice" -> Invoice.SCHEMA, "com.example.events.LinkedListNode" -> LinkedListNode.SCHEMA, "com.example.events.TreeNode" -> TreeNode.SCHEMA, "com.example.events.OrderCancelled" -> OrderCancelled.SCHEMA, "com.example.events.OrderPlaced" -> OrderPlaced.SCHEMA, "com.example.events.OrderUpdated" -> OrderUpdated.SCHEMA)
}