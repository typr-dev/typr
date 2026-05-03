---
title: Typr Bridge
sidebar_position: 1
---

# Typr Bridge

**Define your domain. Bridge your sources.**

Typr Bridge generates a canonical domain model that works across all your data sources. Define `Customer` once, map it to PostgreSQL, MariaDB, Oracle, and your API. Typr validates compatibility and generates all the conversion code.

## The Problem

You have multiple data sources. Each has its own representation:

```
PostgreSQL          MariaDB             REST API
────────────        ────────────        ────────────
CustomerRow         CustomersRow        CustomerDto
OrderRow            OrdersRow           OrderDto
ProductRow          ProductsRow         ProductDto
```

Three types for the same concept. You write mapping code between them:

```java
// You write this. For every entity. For every source.
Customer fromPostgres(CustomerRow row) {
    return new Customer(row.id(), row.firstName(), row.lastName(), row.email());
}

Customer fromMariadb(CustomersRow row) {
    return new Customer(row.customerId(), row.firstName(), row.lastName(), row.email());
}

CustomerDto toApi(Customer c) {
    return new CustomerDto(c.id(), c.firstName(), c.lastName(), c.email());
}
```

Multiply by every entity. Update when schemas change. Debug when fields drift.

## The Solution

Define your domain type once. Typr generates everything else.

```yaml
domainTypes:
  Customer:
    primary: postgres:sales.customer    # The anchor source
    fields:
      id: CustomerId
      firstName: FirstName
      lastName: LastName
      email: Email?
    alignedSources:
      mariadb:customers: superset       # Additional sources aligned to primary
      api:Customer: exact
```

Typr generates a `Customer` type with converters for every source:

```java
public record Customer(
    CustomerId id,
    FirstName firstName,
    LastName lastName,
    Optional<Email> email
) {
    public static Customer fromPostgres(CustomerRow row) { ... }
    public static Customer fromMariadb(CustomersRow row) { ... }
    public CustomerDto toApi() { ... }
}
```

Your service code becomes trivial:

```java
public CustomerDto getCustomer(CustomerId id) {
    CustomerRow row = customerRepo.selectById(id, conn);
    return Customer.fromPostgres(row).toApi();
}
```

## Compatibility Validation

Typr validates that your domain type is compatible with all aligned sources:

```
Domain type 'Customer' has incompatible aligned sources:

  Field 'id':
    mariadb:legacy_customers.cust_id → String (expected Long from primary)

  Fix: Ensure all sources use compatible underlying types
```

Schema mismatches become compile-time errors, not production bugs.

## Field-Level Type Safety

For extra type safety, define field types that wrap primitive values:

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
```

Now `CustomerId` and `OrderId` are different types. The compiler catches if you mix them up.

## Next Steps

- [Domain Types](/typr/unified-types/domain-types) - Define your canonical model with projections
- [Field Types](/typr/unified-types/field-types) - Add field-level type safety
- [Configuration](/typr/unified-types/configuration) - Complete YAML reference
