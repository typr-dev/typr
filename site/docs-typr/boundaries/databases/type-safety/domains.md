---
title: Domains / Wrapper Types
---

# Domains / Wrapper Types

A **domain** (or wrapper type / newtype) wraps a single underlying value to create a distinct type. This prevents accidentally mixing values that have the same representation but different meanings.

## The Problem

```java
void transferFunds(long fromAccount, long toAccount, BigDecimal amount)

// Compiles, but wrong - arguments swapped!
transferFunds(toAccountId, fromAccountId, amount);
```

## The Solution

```java
record FromAccountId(long value) {}
record ToAccountId(long value) {}

void transferFunds(FromAccountId from, ToAccountId to, BigDecimal amount)

// Compile error - types don't match!
transferFunds(toAccountId, fromAccountId, amount);
```

## Database Support

| Database | Mechanism | Notes |
|----------|-----------|-------|
| PostgreSQL | `CREATE DOMAIN` | Named types with CHECK constraints |
| SQL Server | User-Defined Types | Simple type aliases |
| Oracle | - | Use OBJECT types or bimap |
| DuckDB | - | No domain support, use bimap |
| MariaDB | - | No domain support, use bimap |
| DB2 | - | No domain support, use bimap |

## PostgreSQL Domains

PostgreSQL domains create named types from base types, optionally with constraints:

```sql
CREATE DOMAIN order_number AS VARCHAR(25);
CREATE DOMAIN positive_amount AS NUMERIC CHECK (VALUE > 0);
CREATE DOMAIN email AS TEXT CHECK (VALUE ~ '^[^@]+@[^@]+$');
```

Typr generates wrapper types for domains:

```java
// Java
public record OrderNumber(String value) {
  // Type class instances for DB access
}
```

```kotlin
// Kotlin
@JvmInline
value class OrderNumber(val value: String)
```

```scala
// Scala
case class OrderNumber(value: String) extends AnyVal
```

## SQL Server User-Defined Types

SQL Server UDTs work similarly:

```sql
CREATE TYPE OrderNumber FROM VARCHAR(25);
```

Typr generates the same wrapper types as for PostgreSQL domains.

## Databases Without Domain Support

For Oracle, DuckDB, MariaDB, and DB2, use `bimap` to create wrapper types manually:

```java
public record AccountId(Long value) {}

// PostgreSQL
PgType<AccountId> accountIdType =
    PgTypes.int8.bimap(AccountId::new, AccountId::value);

// MariaDB
MariaType<AccountId> accountIdType =
    MariaTypes.bigint.bimap(AccountId::new, AccountId::value);

// Oracle
OracleType<AccountId> accountIdType =
    OracleTypes.numberLong.bimap(AccountId::new, AccountId::value);

// DuckDB
DuckDbType<AccountId> accountIdType =
    DuckDbTypes.bigint.bimap(AccountId::new, AccountId::value);
```

## Benefits

1. **Compile-time safety**: The compiler catches type mismatches before runtime
2. **Self-documenting code**: Types convey semantic meaning
3. **Refactoring confidence**: Changing a type reveals all affected code
4. **Database constraints**: Domains can enforce validation at the database level
