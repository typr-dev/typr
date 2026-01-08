-- Simple Oracle test schema for Typo
-- Demonstrates object types, VARRAYs, nested tables, and views

-- Switch to TYPR schema
ALTER SESSION SET CONTAINER = FREEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = TYPR;

-- Create object types
CREATE OR REPLACE TYPE money_t AS OBJECT (
    amount NUMBER(19,4),
    currency CHAR(3)
);
/

CREATE OR REPLACE TYPE coordinates_t AS OBJECT (
    latitude NUMBER(9,6),
    longitude NUMBER(9,6)
);
/

CREATE OR REPLACE TYPE address_t AS OBJECT (
    street VARCHAR2(100),
    city VARCHAR2(50),
    location coordinates_t
);
/

-- Create collection types
CREATE OR REPLACE TYPE email_varray_t AS VARRAY(5) OF VARCHAR2(100);
/

CREATE OR REPLACE TYPE tag_varray_t AS VARRAY(10) OF VARCHAR2(50);
/

CREATE OR REPLACE TYPE email_table_t AS TABLE OF VARCHAR2(100);
/

-- Phone list VARRAY for testing nested collections in object types
CREATE OR REPLACE TYPE phone_list AS VARRAY(5) OF VARCHAR2(20);
/

-- Comprehensive object type that includes all supported Oracle types (except LOBs)
-- Used for testing object type code generation with various data types
CREATE OR REPLACE TYPE all_types_struct AS OBJECT (
    varchar_field VARCHAR2(100),
    nvarchar_field NVARCHAR2(100),
    char_field CHAR(10),
    nchar_field NCHAR(10),
    number_field NUMBER,
    number_int_field NUMBER(10),
    number_long_field NUMBER(19),
    binary_float_field BINARY_FLOAT,
    binary_double_field BINARY_DOUBLE,
    date_field DATE,
    timestamp_field TIMESTAMP,
    timestamp_tz_field TIMESTAMP WITH TIME ZONE,
    timestamp_ltz_field TIMESTAMP WITH LOCAL TIME ZONE,
    interval_ym_field INTERVAL YEAR TO MONTH,
    interval_ds_field INTERVAL DAY TO SECOND,
    nested_object_field address_t,
    varray_field phone_list
);
/

-- Same as all_types_struct but explicitly named for no-LOB scenarios
CREATE OR REPLACE TYPE all_types_struct_no_lobs AS OBJECT (
    varchar_field VARCHAR2(100),
    nvarchar_field NVARCHAR2(100),
    char_field CHAR(10),
    nchar_field NCHAR(10),
    number_field NUMBER,
    number_int_field NUMBER(10),
    number_long_field NUMBER(19),
    binary_float_field BINARY_FLOAT,
    binary_double_field BINARY_DOUBLE,
    date_field DATE,
    timestamp_field TIMESTAMP,
    timestamp_tz_field TIMESTAMP WITH TIME ZONE,
    timestamp_ltz_field TIMESTAMP WITH LOCAL TIME ZONE,
    interval_ym_field INTERVAL YEAR TO MONTH,
    interval_ds_field INTERVAL DAY TO SECOND,
    nested_object_field address_t,
    varray_field phone_list
);
/

-- Optional variant (all fields nullable - same structure)
CREATE OR REPLACE TYPE all_types_struct_optional AS OBJECT (
    varchar_field VARCHAR2(100),
    nvarchar_field NVARCHAR2(100),
    char_field CHAR(10),
    nchar_field NCHAR(10),
    number_field NUMBER,
    number_int_field NUMBER(10),
    number_long_field NUMBER(19),
    binary_float_field BINARY_FLOAT,
    binary_double_field BINARY_DOUBLE,
    date_field DATE,
    timestamp_field TIMESTAMP,
    timestamp_tz_field TIMESTAMP WITH TIME ZONE,
    timestamp_ltz_field TIMESTAMP WITH LOCAL TIME ZONE,
    interval_ym_field INTERVAL YEAR TO MONTH,
    interval_ds_field INTERVAL DAY TO SECOND,
    nested_object_field address_t,
    varray_field phone_list
);
/

CREATE OR REPLACE TYPE all_types_struct_no_lobs_optional AS OBJECT (
    varchar_field VARCHAR2(100),
    nvarchar_field NVARCHAR2(100),
    char_field CHAR(10),
    nchar_field NCHAR(10),
    number_field NUMBER,
    number_int_field NUMBER(10),
    number_long_field NUMBER(19),
    binary_float_field BINARY_FLOAT,
    binary_double_field BINARY_DOUBLE,
    date_field DATE,
    timestamp_field TIMESTAMP,
    timestamp_tz_field TIMESTAMP WITH TIME ZONE,
    timestamp_ltz_field TIMESTAMP WITH LOCAL TIME ZONE,
    interval_ym_field INTERVAL YEAR TO MONTH,
    interval_ds_field INTERVAL DAY TO SECOND,
    nested_object_field address_t,
    varray_field phone_list
);
/

-- Array types for collection testing
CREATE OR REPLACE TYPE all_types_struct_no_lobs_array AS VARRAY(10) OF all_types_struct_no_lobs;
/

CREATE OR REPLACE TYPE all_types_struct_no_lobs_optional_array AS VARRAY(10) OF all_types_struct_no_lobs_optional;
/

-- Create tables
CREATE TABLE all_scalar_types (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    col_varchar2 VARCHAR2(100),
    col_number NUMBER(10,2),
    col_date DATE,
    col_timestamp TIMESTAMP,
    col_clob CLOB,
    col_not_null VARCHAR2(50) NOT NULL
);

CREATE TABLE customers (
    customer_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR2(100) NOT NULL,
    billing_address address_t NOT NULL,
    credit_limit money_t,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
);

CREATE TABLE products (
    product_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku VARCHAR2(50) UNIQUE NOT NULL,
    name VARCHAR2(100) NOT NULL,
    price money_t NOT NULL,
    tags tag_varray_t
);

CREATE TABLE contacts (
    contact_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR2(100) NOT NULL,
    emails email_table_t,
    tags tag_varray_t
) NESTED TABLE emails STORE AS contact_emails_tab;

-- Create views
CREATE OR REPLACE VIEW customer_products AS
SELECT
    c.customer_id,
    c.name as customer_name,
    c.billing_address,
    p.product_id,
    p.name as product_name,
    p.price
FROM customers c
CROSS JOIN products p;

-- Insert sample data
INSERT INTO all_scalar_types (col_varchar2, col_number, col_date, col_not_null)
VALUES ('test', 123.45, DATE '2025-01-01', 'required');

INSERT INTO customers (name, billing_address, credit_limit) VALUES (
    'John Doe',
    address_t('123 Main St', 'New York', coordinates_t(40.7128, -74.0060)),
    money_t(10000, 'USD')
);

INSERT INTO products (sku, name, price, tags) VALUES (
    'PROD-001',
    'Oracle Database',
    money_t(47500, 'USD'),
    tag_varray_t('database', 'enterprise')
);

-- Tables with composite primary keys
CREATE TABLE departments (
    dept_code VARCHAR2(10) NOT NULL,
    dept_region VARCHAR2(10) NOT NULL,
    dept_name VARCHAR2(100) NOT NULL,
    budget money_t,
    CONSTRAINT pk_departments PRIMARY KEY (dept_code, dept_region)
);

CREATE TABLE employees (
    emp_number NUMBER NOT NULL,
    emp_suffix VARCHAR2(5) NOT NULL,
    dept_code VARCHAR2(10) NOT NULL,
    dept_region VARCHAR2(10) NOT NULL,
    emp_name VARCHAR2(100) NOT NULL,
    salary money_t,
    hire_date DATE DEFAULT SYSDATE NOT NULL,
    CONSTRAINT pk_employees PRIMARY KEY (emp_number, emp_suffix),
    CONSTRAINT fk_employees_departments FOREIGN KEY (dept_code, dept_region)
        REFERENCES departments(dept_code, dept_region)
);

-- Insert sample data
INSERT INTO departments (dept_code, dept_region, dept_name, budget) VALUES (
    'IT',
    'US-WEST',
    'Information Technology',
    money_t(1000000, 'USD')
);

INSERT INTO departments (dept_code, dept_region, dept_name, budget) VALUES (
    'HR',
    'US-EAST',
    'Human Resources',
    money_t(500000, 'USD')
);

INSERT INTO employees (emp_number, emp_suffix, dept_code, dept_region, emp_name, salary) VALUES (
    1001,
    'A',
    'IT',
    'US-WEST',
    'Alice Johnson',
    money_t(95000, 'USD')
);

INSERT INTO employees (emp_number, emp_suffix, dept_code, dept_region, emp_name, salary) VALUES (
    1002,
    'B',
    'HR',
    'US-EAST',
    'Bob Smith',
    money_t(75000, 'USD')
);

COMMIT;

-- ============================================================================
-- ALL_TYPES_STRUCT TEST TABLE
-- Tests comprehensive Oracle object type with all supported data types
-- ============================================================================

BEGIN EXECUTE IMMEDIATE 'DROP TABLE all_types_test CASCADE CONSTRAINTS'; EXCEPTION WHEN OTHERS THEN NULL; END;
/

CREATE TABLE all_types_test (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR2(100) NOT NULL,
    data all_types_struct_no_lobs,
    data_array all_types_struct_no_lobs_array
);

COMMIT;

-- ============================================================================
-- PRECISION TYPES TEST TABLES
-- These tables are used to test precision wrapper type generation.
-- The enablePreciseTypes selector should only include these tables.
-- ============================================================================

-- Precision types test table with all NOT NULL columns
CREATE TABLE precision_types (
    id                NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- String precision types
    string10          VARCHAR2(10)      NOT NULL,
    string20          VARCHAR2(20)      NOT NULL,
    string50          VARCHAR2(50)      NOT NULL,
    string100         VARCHAR2(100)     NOT NULL,
    string255         VARCHAR2(255)     NOT NULL,
    -- Fixed char
    char10            CHAR(10)          NOT NULL,
    -- Number precision types
    number5_2         NUMBER(5,2)       NOT NULL,
    number10_2        NUMBER(10,2)      NOT NULL,
    number18_4        NUMBER(18,4)      NOT NULL,
    -- Number with scale 0 (integers)
    number5_0         NUMBER(5)         NOT NULL,
    number10_0        NUMBER(10)        NOT NULL,
    number18_0        NUMBER(18)        NOT NULL,
    -- Timestamp precision types
    ts0               TIMESTAMP(0)      NOT NULL,
    ts3               TIMESTAMP(3)      NOT NULL,
    ts6               TIMESTAMP(6)      NOT NULL,
    ts9               TIMESTAMP(9)      NOT NULL
);

-- Precision types test table with all NULLABLE columns
CREATE TABLE precision_types_null (
    id                NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- String precision types
    string10          VARCHAR2(10),
    string20          VARCHAR2(20),
    string50          VARCHAR2(50),
    string100         VARCHAR2(100),
    string255         VARCHAR2(255),
    -- Fixed char
    char10            CHAR(10),
    -- Number precision types
    number5_2         NUMBER(5,2),
    number10_2        NUMBER(10,2),
    number18_4        NUMBER(18,4),
    -- Number with scale 0 (integers)
    number5_0         NUMBER(5),
    number10_0        NUMBER(10),
    number18_0        NUMBER(18),
    -- Timestamp precision types
    ts0               TIMESTAMP(0),
    ts3               TIMESTAMP(3),
    ts6               TIMESTAMP(6),
    ts9               TIMESTAMP(9)
);

COMMIT;
