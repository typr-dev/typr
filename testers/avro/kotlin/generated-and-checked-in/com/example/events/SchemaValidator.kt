package com.example.events

import com.example.events.common.Money
import java.util.ArrayList
import kotlin.collections.Map
import org.apache.avro.Schema
import org.apache.avro.SchemaCompatibility
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType
import org.apache.avro.SchemaCompatibility.SchemaPairCompatibility

/** Schema validation utility for Avro compatibility checking.
  * Provides methods to verify schema compatibility and validate field presence.
  */
class SchemaValidator() {
  /** Get detailed compatibility information between two schemas.
    * Returns a SchemaPairCompatibility with type, result, and any incompatibilities.
    */
  fun checkCompatibility(
    newSchema: Schema,
    oldSchema: Schema
  ): SchemaPairCompatibility {
    return SchemaCompatibility.checkReaderWriterCompatibility(newSchema, oldSchema)
  }

  /** Get the list of field names in writerSchema that are missing from readerSchema.
    * Useful for identifying which fields will be ignored during deserialization.
    */
  fun getMissingFields(
    readerSchema: Schema,
    writerSchema: Schema
  ): ArrayList<kotlin.String> {
    val missing = ArrayList<kotlin.String>()
    writerSchema.getFields().forEach({ writerField -> if (readerSchema.getField(writerField.name()) == null) {
      missing.add(writerField.name())
    } })
    return missing
  }

  /** Get the schema for a known record type by its full name.
    * Returns null if the schema name is not recognized.
    */
  fun getSchemaByName(name: kotlin.String): Schema? {
    return SchemaValidator.SCHEMAS[name]
  }

  /** Check if a reader with readerSchema can read data written with writerSchema.
    * Returns true if backward compatible (new reader can read old data).
    */
  fun isBackwardCompatible(
    readerSchema: Schema,
    writerSchema: Schema
  ): kotlin.Boolean {
    return SchemaCompatibility.checkReaderWriterCompatibility(readerSchema, writerSchema).getType() == SchemaCompatibilityType.COMPATIBLE
  }

  /** Check if data written with writerSchema can be read by a reader with readerSchema.
    * Returns true if forward compatible (old reader can read new data).
    */
  fun isForwardCompatible(
    writerSchema: Schema,
    readerSchema: Schema
  ): kotlin.Boolean {
    return SchemaCompatibility.checkReaderWriterCompatibility(readerSchema, writerSchema).getType() == SchemaCompatibilityType.COMPATIBLE
  }

  /** Check if both schemas can read each other's data.
    * Returns true if fully compatible (both backward and forward).
    */
  fun isFullyCompatible(
    schema1: Schema,
    schema2: Schema
  ): kotlin.Boolean {
    return isBackwardCompatible(schema1, schema2) && isBackwardCompatible(schema2, schema1)
  }

  /** Validate that all required fields in the schema are properly defined.
    * Returns true if all required fields are valid (non-union without default is allowed).
    */
  fun validateRequiredFields(schema: Schema): kotlin.Boolean {
    return true
  }

  companion object {
    val SCHEMAS: Map<kotlin.String, Schema> = mapOf("com.example.events.Address" to Address.SCHEMA, "com.example.events.CustomerOrder" to CustomerOrder.SCHEMA, "com.example.events.DynamicValue" to DynamicValue.SCHEMA, "com.example.events.common.Money" to Money.SCHEMA, "com.example.events.Invoice" to Invoice.SCHEMA, "com.example.events.LinkedListNode" to LinkedListNode.SCHEMA, "com.example.events.TreeNode" to TreeNode.SCHEMA, "com.example.events.OrderCancelled" to OrderCancelled.SCHEMA, "com.example.events.OrderPlaced" to OrderPlaced.SCHEMA, "com.example.events.OrderUpdated" to OrderUpdated.SCHEMA)
  }
}