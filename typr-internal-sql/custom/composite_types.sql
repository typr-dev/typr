-- Query PostgreSQL composite types (CREATE TYPE name AS (...))
-- Returns one row per attribute (field) of each composite type
-- Note: We filter to relkind = 'c' to exclude implicit table row types
select
    n.nspname as "typeSchema?",
    t.typname as "typeName",
    a.attname as "attrName",
    a.attnum as "attrNum",
    at.typname as "attrType",
    a.attnotnull as "attrNotNull"
from pg_type t
join pg_namespace n on t.typnamespace = n.oid
join pg_class c on t.typrelid = c.oid
join pg_attribute a on t.typrelid = a.attrelid and a.attnum > 0 and not a.attisdropped
join pg_type at on a.atttypid = at.oid
where t.typtype = 'c'  -- 'c' = composite type
  and c.relkind = 'c'  -- 'c' = composite type class (excludes 'r' = ordinary tables)
  and t.typrelid != 0  -- has attributes
  and n.nspname not in ('pg_catalog', 'information_schema')  -- exclude system schemas
order by n.nspname, t.typname, a.attnum
