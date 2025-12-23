package typr.openapi.parser

import io.swagger.v3.oas.models.media.{ObjectSchema, StringSchema}
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite
import typr.openapi.{PrimitiveType, TypeInfo}

import scala.jdk.CollectionConverters._

class TypeResolverTest extends AnyFunSuite with TypeCheckedTripleEquals {

  test("resolve OpenAPI 3.0 string type") {
    val schema = new StringSchema()
    val result = TypeResolver.resolveBase(schema)
    assert(result === TypeInfo.Primitive(PrimitiveType.String))
  }

  test("resolve OpenAPI 3.0 nullable string") {
    val schema = new StringSchema()
    schema.setNullable(true)
    val result = TypeResolver.resolve(schema, required = true)
    assert(result === TypeInfo.Optional(TypeInfo.Primitive(PrimitiveType.String)))
  }

  test("resolve OpenAPI 3.1 type array with null") {
    val schema = new StringSchema()
    schema.setTypes(Set("string", "null").asJava)
    val result = TypeResolver.resolve(schema, required = true)
    // Should be Optional because of "null" in the types array
    assert(result === TypeInfo.Optional(TypeInfo.Primitive(PrimitiveType.String)))
  }

  test("resolve OpenAPI 3.1 type array without null") {
    val schema = new StringSchema()
    schema.setTypes(Set("string").asJava)
    val result = TypeResolver.resolve(schema, required = true)
    // Should not be Optional because no "null" in types
    assert(result === TypeInfo.Primitive(PrimitiveType.String))
  }

  test("resolve date format") {
    val schema = new StringSchema()
    schema.setFormat("date")
    val result = TypeResolver.resolveBase(schema)
    assert(result === TypeInfo.Primitive(PrimitiveType.Date))
  }

  test("resolve date-time format") {
    val schema = new StringSchema()
    schema.setFormat("date-time")
    val result = TypeResolver.resolveBase(schema)
    assert(result === TypeInfo.Primitive(PrimitiveType.DateTime))
  }

  test("resolve uuid format") {
    val schema = new StringSchema()
    schema.setFormat("uuid")
    val result = TypeResolver.resolveBase(schema)
    assert(result === TypeInfo.Primitive(PrimitiveType.UUID))
  }

  test("resolve optional when not required") {
    val schema = new StringSchema()
    val result = TypeResolver.resolve(schema, required = false)
    assert(result === TypeInfo.Optional(TypeInfo.Primitive(PrimitiveType.String)))
  }

  test("resolve $ref") {
    val schema = new ObjectSchema()
    schema.set$ref("#/components/schemas/Pet")
    val result = TypeResolver.resolveBase(schema)
    assert(result === TypeInfo.Ref("Pet"))
  }

  test("extractRefName with full path") {
    assert(TypeResolver.extractRefName("#/components/schemas/Pet") === "Pet")
  }

  test("extractRefName with simple name") {
    assert(TypeResolver.extractRefName("Pet") === "Pet")
  }

  test("extractRefName with relative path") {
    assert(TypeResolver.extractRefName("./models/Pet.yaml") === "Pet.yaml")
  }
}
