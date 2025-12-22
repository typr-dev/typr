-- Simple Oracle test schema for Typo
-- Demonstrates object types, VARRAYs, nested tables, and views

-- Switch to TYPO schema
ALTER SESSION SET CONTAINER = FREEPDB1;
ALTER SESSION SET CURRENT_SCHEMA = TYPO;

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
