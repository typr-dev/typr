---
title: Field Types
sidebar_position: 3
---

# Field Types

**Add type safety at the individual field level.**

Field types let you wrap primitive values with semantic meaning. Instead of using `String` for both first names and emails, you use `FirstName` and `Email`. The compiler catches mistakes before they ship.

## Why Field Types?

Without field types, different concepts use the same primitive:

```java
// Everything is String or Long
String firstName = "John";
String email = "john@example.com";
Long customerId = 123L;
Long orderId = 456L;

// This compiles but is wrong:
void processCustomer(Long customerId) { ... }
processCustomer(orderId);  // Bug! Passed orderId instead of customerId
```

With field types:

```java
FirstName firstName = new FirstName("John");
Email email = new Email("john@example.com");
CustomerId customerId = new CustomerId(123L);
OrderId orderId = new OrderId(456L);

void processCustomer(CustomerId customerId) { ... }
processCustomer(orderId);  // COMPILE ERROR: expected CustomerId, got OrderId
```

---

## Defining Field Types

Field types use **pattern matching** to identify columns and API fields that should use your custom type.

### Basic Structure

```yaml
fieldTypes:
  TypeName:
    db:
      column: [pattern1, pattern2]    # Match database columns
      # Additional DB filters...
    model:
      name: [pattern1, pattern2]      # Match API/model fields
      # Additional model filters...
```

### Simple Examples

```yaml
fieldTypes:
  # Match by exact column name
  FirstName:
    db:
      column: [first_name, firstname]
    model:
      name: [firstName]

  # Match using wildcards
  Email:
    db:
      column: ["*email*"]       # Matches email, user_email, email_address
    model:
      name: ["*email*", "*Email*"]

  # Match primary key columns
  CustomerId:
    db:
      column: [customer_id]
      primary_key: true         # Only match if it's a PK
    model:
      name: [customerId]
```

---

## Pattern Syntax

Field types use glob-style patterns for flexible matching.

### Wildcards

| Pattern | Matches |
|---------|---------|
| `email` | Exactly "email" |
| `*email*` | Contains "email" (email, user_email, email_address) |
| `*_email` | Ends with "_email" (user_email, contact_email) |
| `email*` | Starts with "email" (email, email_hash, email_verified) |

### Negation

Use `!` to exclude certain patterns:

```yaml
Email:
  db:
    column: ["*email*", "!*_hash", "!*_verified"]
    # Matches: email, user_email
    # Excludes: email_hash, email_verified
```

### Multiple Patterns (OR Logic)

Multiple values in a list use OR logic - any match counts:

```yaml
FirstName:
  db:
    column: [first_name, firstname, fname, given_name]
    # Matches any of these
```

---

## Database Filters

Filter which columns match using these predicates:

| Filter | Description | Example |
|--------|-------------|---------|
| `column` | Column name patterns | `[customer_id, cust_id]` |
| `table` | Specific tables only | `[customers, users]` |
| `schema` | Specific schemas only | `[sales, public]` |
| `db_type` | Database column type | `[uuid, bigint]` |
| `primary_key` | Only primary keys | `true` |
| `annotation` | Column comment annotation | `["@currency"]` |

### Examples

```yaml
fieldTypes:
  # Only match PKs named customer_id
  CustomerId:
    db:
      column: [customer_id]
      primary_key: true

  # Match UUID columns
  RequestId:
    db:
      column: [request_id]
      db_type: [uuid]

  # Match in specific schema
  SalesOrderId:
    db:
      column: [order_id]
      schema: [sales]
      primary_key: true

  # Match by column comment
  Currency:
    db:
      annotation: ["@currency"]
```

---

## Model Filters (OpenAPI)

Filter which API fields match:

| Filter | Description | Example |
|--------|-------------|---------|
| `name` | Field name patterns | `[customerId, customer_id]` |
| `schema` | Specific model names | `[Customer, Order]` |
| `format` | OpenAPI format | `[email, uuid, date-time]` |

### Examples

```yaml
fieldTypes:
  # Match by OpenAPI format
  EmailAddress:
    db:
      column: ["*email*"]
    model:
      format: [email]

  UUID:
    db:
      db_type: [uuid]
    model:
      format: [uuid]

  # Match in specific models
  CustomerName:
    model:
      name: [name, customerName]
      schema: [Customer, CustomerCreate]
```

---

## Generated Code

Typr generates a wrapper type for each field type with database adapters and JSON support:

```java
/**
 * Field type 'FirstName' aligned across sources:
 * - postgres: person.firstname, customer.first_name
 * - mariadb: customers.first_name
 * - api: Customer.firstName, CustomerCreate.firstName
 */
public record FirstName(@JsonValue String value) {
    // Database type adapters
    public static PgType<FirstName> pgType =
        PgTypes.text.bimap(FirstName::new, FirstName::value);

    public static MariaType<FirstName> mariaType =
        MariaTypes.varchar.bimap(FirstName::new, FirstName::value);
}
```

The generated code:
- Documents all matched locations across sources
- Provides database type adapters for each database
- Includes JSON serialization support
- Works seamlessly with generated repositories

---

## Using Field Types with Domain Types

Field types are the building blocks for [Domain Types](/typr/unified-types/domain-types). When you define a domain type, you reference your field types:

```yaml
fieldTypes:
  CustomerId:
    db: { column: [customer_id], primary_key: true }
    model: { name: [customerId] }
  FirstName:
    db: { column: [first_name, firstname] }
    model: { name: [firstName] }
  Email:
    db: { column: ["*email*"] }
    model: { name: ["*email*"] }

domainTypes:
  Customer:
    fields:
      id: CustomerId        # Uses your field type
      firstName: FirstName  # Uses your field type
      email: Email?         # Uses your field type, nullable
    projections:
      postgres:sales.customer: superset
      api:Customer: exact
```

This gives you type safety at both levels:
- **Field level:** Can't confuse `CustomerId` and `OrderId`
- **Record level:** Can't confuse `Customer` and `CustomerRow`

---

## Best Practices

### Start with ID Types

Primary key types are the most valuable - they prevent the most common bugs:

```yaml
fieldTypes:
  CustomerId:
    db: { column: [customer_id, cust_id], primary_key: true }
    model: { name: [customerId] }
  OrderId:
    db: { column: [order_id], primary_key: true }
    model: { name: [orderId] }
  ProductId:
    db: { column: [product_id, sku], primary_key: true }
    model: { name: [productId] }
```

### Add Semantic String Types

After IDs, add types for strings with semantic meaning:

```yaml
fieldTypes:
  Email:
    db: { column: ["*email*", "!*_hash"] }
    model: { name: ["*email*"], format: [email] }
  PhoneNumber:
    db: { column: ["*phone*", "*mobile*"] }
    model: { name: ["*phone*", "*mobile*"] }
  URL:
    db: { column: ["*url*", "*link*"] }
    model: { format: [uri] }
```

### Use Specific Patterns

Be specific enough to avoid false matches:

```yaml
# Too broad - matches created_at, updated_at, etc.
# Timestamp:
#   db: { column: ["*at"] }

# Better - explicit list
CreatedAt:
  db: { column: [created_at, created_date, date_created] }
  model: { name: [createdAt] }
```

### Use Negation for Exceptions

When a pattern is mostly right but has exceptions:

```yaml
Email:
  db:
    column: ["*email*", "!*_hash", "!*_verified", "!*_count"]
    # Matches: email, user_email, contact_email
    # Excludes: email_hash, email_verified, email_count
```

---

## Next Steps

- [Domain Types](/typr/unified-types/domain-types) - Define record-level types with projections
- [Configuration](/typr/unified-types/configuration) - Complete YAML reference
