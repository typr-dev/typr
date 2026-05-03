---
title: Best Practices
sidebar_position: 5
---

# Best Practices for Unified Types

This guide covers patterns and recommendations for getting the most out of Unified Types.

## Naming Conventions

### Use Semantic Names

Name types after what they represent, not how they're stored:

```yaml
# Good - semantic names
types:
  Email:
    db: { column: ["*email*"] }
  PhoneNumber:
    db: { column: [phone, mobile, telephone] }
  IsActive:
    db: { column: [is_active, active, enabled] }

# Avoid - technical names
types:
  Varchar50:     # Too generic
  BooleanColumn: # Doesn't convey meaning
```

### Follow Language Conventions

Type names should match your target language's conventions:

| Language | Convention | Example |
|----------|------------|---------|
| Java | PascalCase | `CustomerId`, `IsActive` |
| Kotlin | PascalCase | `CustomerId`, `IsActive` |
| Scala | PascalCase | `CustomerId`, `IsActive` |

## Pattern Design

### Start Specific, Generalize Carefully

Start with specific column names, then add patterns as needed:

```yaml
types:
  # Start specific
  Email:
    db:
      column: [email]

  # Later, if you find more columns, add patterns
  Email:
    db:
      column: [email, user_email, contact_email, "*_email"]
```

### Use Table Scoping for Ambiguous Names

When column names are ambiguous, scope them to tables:

```yaml
types:
  # "name" is too ambiguous globally
  ProductName:
    db:
      table: [products, product_*]
      column: [name, product_name]

  CustomerName:
    db:
      table: [customers, customer_*]
      column: [name, full_name]
```

### Primary Keys Deserve Their Own Types

Create distinct types for primary keys to prevent mixing IDs:

```yaml
types:
  CustomerId:
    db:
      column: [customer_id]
      primary_key: true
    api:
      name: [customerId]

  ProductId:
    db:
      column: [product_id]
      primary_key: true
    api:
      name: [productId]

  # Don't do this - mixing different IDs
  # GenericId:
  #   db:
  #     column: ["*_id"]
  #     primary_key: true
```

## Organization

### Group Related Types

Organize your configuration logically:

```yaml
# config/types-identity.yaml
types:
  CustomerId:
    # ...
  ProductId:
    # ...
  OrderId:
    # ...
```

```yaml
# config/types-personal.yaml
types:
  FirstName:
    # ...
  LastName:
    # ...
  Email:
    # ...
  PhoneNumber:
    # ...
```

```yaml
# config/types-audit.yaml
types:
  CreatedAt:
    # ...
  UpdatedAt:
    # ...
  CreatedBy:
    # ...
```

### Use Include Files

Split large configurations:

```yaml
# typr-types.yaml
version: 1
include:
  - config/sources.yaml
  - config/types-identity.yaml
  - config/types-personal.yaml
  - config/types-audit.yaml
  - config/types-flags.yaml
  - config/output.yaml
```

## Cross-Source Alignment

### Match Naming Variations

Account for different naming conventions across sources:

```yaml
types:
  FirstName:
    db:
      # SQL conventions: snake_case, abbreviated
      column: [first_name, firstname, fname]
    api:
      # API conventions: camelCase
      name: [firstName, fname]
```

### Handle Legacy Systems

When integrating legacy databases with modern APIs:

```yaml
types:
  IsActive:
    db:
      # Legacy uses various patterns
      column: [is_active, active, status_active, act_flg]
    api:
      # Modern API uses consistent naming
      name: [isActive]
```

## Type Safety Levels

### Progression of Type Safety

You can adopt Unified Types incrementally:

**Level 1: Identity Types Only**
```yaml
types:
  CustomerId:
    db: { column: [customer_id], primary_key: true }
  ProductId:
    db: { column: [product_id], primary_key: true }
```

**Level 2: Add Common Strings**
```yaml
types:
  Email:
    db: { column: ["*email*"] }
  FirstName:
    db: { column: [first_name, firstname] }
```

**Level 3: Add Flags**
```yaml
types:
  IsActive:
    db: { column: [is_active, active] }
  IsVerified:
    db: { column: [is_verified, verified] }
```

**Level 4: Full Coverage**
```yaml
types:
  # Identity, strings, flags, AND...
  Currency:
    db: { annotation: ["@currency"] }
  Money:
    db: { column: ["*price*", "*amount*", "*cost*"] }
```

## Testing

### Validate Configuration Regularly

Add validation to your CI/CD:

```bash
typr validate --strict
```

### Test Type Changes

When modifying type definitions, regenerate and compile:

```bash
typr generate && ./gradlew compileJava
```

### Document Breaking Changes

When a type mapping changes, document it:

```yaml
types:
  # BREAKING: Renamed from PhoneNumber to Phone in v2.0
  # Migration: Update all usages of PhoneNumber to Phone
  Phone:
    db: { column: [phone, mobile, telephone] }
```

## Common Patterns

### Boolean Flag Pattern

```yaml
types:
  IsActive:
    db: { column: [is_active, active, enabled] }
    api: { name: [isActive, active, enabled] }

  IsDeleted:
    db: { column: [is_deleted, deleted, removed] }
    api: { name: [isDeleted, deleted] }

  IsVerified:
    db: { column: [is_verified, verified, confirmed] }
    api: { name: [isVerified, verified] }
```

### Audit Field Pattern

```yaml
types:
  CreatedAt:
    db: { column: [created_at, created_date, create_date, date_created] }
    api: { name: [createdAt, dateCreated] }

  UpdatedAt:
    db: { column: [updated_at, modified_at, last_modified, date_modified] }
    api: { name: [updatedAt, modifiedAt, lastModified] }

  CreatedBy:
    db: { column: [created_by, creator, author] }
    api: { name: [createdBy, creator] }

  UpdatedBy:
    db: { column: [updated_by, modified_by, last_modified_by] }
    api: { name: [updatedBy, modifiedBy] }
```

### Foreign Key Pattern

```yaml
types:
  # Match foreign keys by what they reference
  CustomerId:
    db:
      column: [customer_id]
      references: [customers]

  ProductId:
    db:
      column: [product_id]
      references: [products]
```

### Sensitive Data Pattern

Use annotations in column comments:

```sql
-- In your database
COMMENT ON COLUMN users.ssn IS '@sensitive Social Security Number';
COMMENT ON COLUMN users.password_hash IS '@sensitive @no-log Password hash';
```

```yaml
types:
  SensitiveString:
    db:
      annotation: ["@sensitive"]
    api:
      extension:
        x-sensitive: "true"
```

## Performance Considerations

### Keep Type Count Reasonable

While more types provide more safety, too many can:
- Increase compilation time
- Make IDE autocomplete slower
- Create cognitive overhead

**Recommendation**: Start with 10-20 core types, add more as needed.

### Use Specific Patterns

More specific patterns match faster:

```yaml
# Faster - specific column names
Email:
  db: { column: [email, user_email] }

# Slower - glob pattern scans all columns
Email:
  db: { column: ["*email*"] }
```

## Troubleshooting

### Type Not Matching

Check these common issues:

1. **Case sensitivity**: Column names are case-sensitive in patterns
2. **Schema qualification**: Add schema to narrow matches
3. **Pattern syntax**: Globs use `*` for any characters, `?` for single character

```bash
# Debug matching
typr types show Email --verbose
```

### Conflicting Types

When multiple types could match:

```yaml
# More specific type should come first
types:
  PrimaryEmail:
    db:
      table: [users]
      column: [email]

  Email:
    db:
      column: ["*email*"]
```

### Generated Code Issues

If generated code doesn't compile:

1. Check for name collisions
2. Verify all matched columns have compatible types
3. Run `typr validate --strict`
