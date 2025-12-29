-- PostgreSQL Composite Types Test Schema
-- Tests for nested composite types, arrays of composites, and edge cases

-- Simple composite type
CREATE TYPE address AS (
    street VARCHAR(100),
    city VARCHAR(50),
    zip VARCHAR(20),
    country VARCHAR(50)
);

-- Composite type with optional fields (to test NULL handling)
CREATE TYPE person_name AS (
    first_name VARCHAR(50),
    middle_name VARCHAR(50),
    last_name VARCHAR(50),
    suffix VARCHAR(10)
);

-- Nested composite type (contains another composite)
CREATE TYPE contact_info AS (
    email VARCHAR(100),
    phone VARCHAR(20),
    address address  -- nested composite
);

-- Deeply nested composite type
CREATE TYPE employee_record AS (
    name person_name,          -- nested composite
    contact contact_info,      -- nested composite (which contains nested)
    employee_id INTEGER,
    salary NUMERIC(12,2),
    hire_date DATE
);

-- Composite with array types
CREATE TYPE inventory_item AS (
    name VARCHAR(100),
    tags TEXT[],              -- array of text
    prices NUMERIC[],         -- array of numeric
    available BOOLEAN
);

-- Composite with JSON
CREATE TYPE metadata_record AS (
    key VARCHAR(50),
    value JSONB,
    created_at TIMESTAMPTZ
);

-- Composite with problematic characters for parsing
CREATE TYPE text_with_special_chars AS (
    with_comma VARCHAR(100),      -- will contain: hello, world
    with_quotes VARCHAR(100),     -- will contain: say "hello"
    with_parens VARCHAR(100),     -- will contain: (nested) stuff
    with_backslash VARCHAR(100),  -- will contain: path\to\file
    with_newline TEXT,            -- will contain newlines
    with_all TEXT                 -- will contain all special chars
);

-- Composite with nullable vs non-nullable (test empty string vs NULL)
CREATE TYPE nullable_test AS (
    always_present VARCHAR(50),
    often_null VARCHAR(50),
    empty_vs_null VARCHAR(50)
);

-- Array of composite types
CREATE TYPE point_2d AS (
    x DOUBLE PRECISION,
    y DOUBLE PRECISION
);

CREATE TYPE polygon_custom AS (
    name VARCHAR(50),
    vertices point_2d[]  -- array of composite types
);

-- Recursive-like structure (via arrays)
CREATE TYPE tree_node AS (
    id INTEGER,
    label VARCHAR(100),
    parent_id INTEGER
);

-- Composite with all PostgreSQL scalar types
CREATE TYPE all_types_composite AS (
    col_boolean BOOLEAN,
    col_smallint SMALLINT,
    col_integer INTEGER,
    col_bigint BIGINT,
    col_real REAL,
    col_double DOUBLE PRECISION,
    col_numeric NUMERIC(10,3),
    col_text TEXT,
    col_varchar VARCHAR(255),
    col_char CHAR(10),
    col_bytea BYTEA,
    col_date DATE,
    col_time TIME,
    col_timestamp TIMESTAMP,
    col_timestamptz TIMESTAMPTZ,
    col_interval INTERVAL,
    col_uuid UUID,
    col_json JSON,
    col_jsonb JSONB,
    col_xml XML
);

-- Test table using the composite types
CREATE TABLE composite_test (
    id SERIAL PRIMARY KEY,

    -- Simple composite
    simple_address address,

    -- Nested composite
    full_contact contact_info,

    -- Deeply nested
    employee employee_record,

    -- Composite with arrays
    item inventory_item,

    -- Composite with JSON
    meta metadata_record,

    -- Composite with special chars
    special text_with_special_chars,

    -- Nullable test
    nullable nullable_test,

    -- Array of composites
    poly polygon_custom,

    -- Direct array of composite
    points point_2d[],

    -- All types
    all_types all_types_composite
);

-- Insert test data with various edge cases
INSERT INTO composite_test (
    simple_address,
    full_contact,
    employee,
    item,
    meta,
    special,
    nullable,
    poly,
    points,
    all_types
) VALUES (
    -- simple_address
    ROW('123 Main St', 'New York', '10001', 'USA')::address,

    -- full_contact (nested)
    ROW(
        'test@example.com',
        '+1-555-0123',
        ROW('456 Oak Ave', 'Los Angeles', '90001', 'USA')::address
    )::contact_info,

    -- employee (deeply nested)
    ROW(
        ROW('John', 'Michael', 'Doe', 'Jr.')::person_name,
        ROW(
            'john.doe@company.com',
            '+1-555-9999',
            ROW('789 Pine Rd', 'Chicago', '60601', 'USA')::address
        )::contact_info,
        12345,
        75000.50,
        '2020-01-15'
    )::employee_record,

    -- item (with arrays)
    ROW('Widget Pro', ARRAY['electronics', 'gadgets'], ARRAY[19.99, 24.99, 29.99], true)::inventory_item,

    -- meta (with JSON)
    ROW('settings', '{"theme": "dark", "notifications": true}'::jsonb, NOW())::metadata_record,

    -- special (with problematic characters)
    ROW(
        'hello, world',                        -- comma
        'say "hello"',                         -- quotes
        '(nested) stuff',                      -- parentheses
        E'path\\to\\file',                     -- backslashes
        E'line1\nline2\nline3',               -- newlines
        E'all: "quotes", (parens), \\slash\n' -- all together
    )::text_with_special_chars,

    -- nullable
    ROW('present', NULL, '')::nullable_test,

    -- poly (array of composites)
    ROW('triangle', ARRAY[ROW(0.0, 0.0)::point_2d, ROW(1.0, 0.0)::point_2d, ROW(0.5, 1.0)::point_2d])::polygon_custom,

    -- points (direct array of composites)
    ARRAY[ROW(1.0, 2.0)::point_2d, ROW(3.0, 4.0)::point_2d],

    -- all_types
    ROW(
        true,
        123::smallint,
        456789,
        9876543210::bigint,
        1.5::real,
        2.718281828::double precision,
        12345.678::numeric(10,3),
        'Some text value',
        'varchar value',
        'char val  ',
        '\xDEADBEEF'::bytea,
        '2024-06-15'::date,
        '14:30:00'::time,
        '2024-06-15 14:30:00'::timestamp,
        '2024-06-15 14:30:00+02'::timestamptz,
        '1 year 2 months 3 days'::interval,
        'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'::uuid,
        '{"key": "value"}'::json,
        '{"key": "value"}'::jsonb,
        '<root><child>text</child></root>'::xml
    )::all_types_composite
);

-- Insert with NULLs at various levels
INSERT INTO composite_test (
    simple_address,
    full_contact,
    employee,
    item,
    meta,
    special,
    nullable,
    poly,
    points,
    all_types
) VALUES (
    NULL,  -- entire composite is null

    -- nested with inner null
    ROW(
        'no-phone@example.com',
        NULL,  -- phone is null
        NULL   -- address is null
    )::contact_info,

    -- deeply nested with some nulls
    ROW(
        ROW('Jane', NULL, 'Smith', NULL)::person_name,  -- middle_name and suffix null
        NULL,  -- entire contact is null
        99999,
        NULL,  -- salary null
        '2021-06-01'
    )::employee_record,

    ROW('Empty Item', NULL, NULL, NULL)::inventory_item,  -- all arrays null

    NULL,  -- entire meta null

    ROW(NULL, NULL, NULL, NULL, NULL, NULL)::text_with_special_chars,  -- all fields null

    ROW('only-this', NULL, NULL)::nullable_test,

    ROW('empty-poly', NULL)::polygon_custom,  -- vertices array null

    NULL,  -- points array null

    NULL   -- all_types null
);

-- Edge case: empty strings vs NULLs
INSERT INTO composite_test (
    simple_address,
    nullable
) VALUES (
    ROW('', '', '', '')::address,  -- all empty strings (not null!)
    ROW('', '', '')::nullable_test
);
