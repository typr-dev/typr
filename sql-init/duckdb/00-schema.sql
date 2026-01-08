-- DuckDB test schema for Typo
-- Demonstrates DuckDB-specific types and features

-- ==================== ENUM Types ====================
CREATE TYPE mood AS ENUM ('happy', 'sad', 'neutral');
CREATE TYPE priority AS ENUM ('low', 'medium', 'high', 'critical');

-- ==================== Table with all scalar types ====================
CREATE TABLE all_scalar_types (
    id INTEGER PRIMARY KEY,
    -- Integer types (signed)
    col_tinyint TINYINT,
    col_smallint SMALLINT,
    col_integer INTEGER,
    col_bigint BIGINT,
    col_hugeint HUGEINT,
    -- Integer types (unsigned)
    col_utinyint UTINYINT,
    col_usmallint USMALLINT,
    col_uinteger UINTEGER,
    col_ubigint UBIGINT,
    -- Floating-point
    col_float FLOAT,
    col_double DOUBLE,
    -- Fixed-point
    col_decimal DECIMAL(10, 2),
    -- Boolean
    col_boolean BOOLEAN,
    -- String types
    col_varchar VARCHAR(100),
    col_text VARCHAR,
    -- Binary
    col_blob BLOB,
    -- Date/Time
    col_date DATE,
    col_time TIME,
    col_timestamp TIMESTAMP,
    col_timestamptz TIMESTAMPTZ,
    col_interval INTERVAL,
    -- UUID
    col_uuid UUID,
    -- JSON
    col_json JSON,
    -- Enum
    col_mood mood,
    -- Not null constraint
    col_not_null VARCHAR NOT NULL
);

-- LIST and MAP types disabled for now - need more work on code generation
-- ==================== Table with LIST types ====================
-- CREATE TABLE list_types (
--     id INTEGER PRIMARY KEY,
--     int_list INTEGER[],
--     string_list VARCHAR[],
--     double_list DOUBLE[],
--     date_list DATE[],
--     uuid_list UUID[]
-- );

-- ==================== Table with MAP types ====================
-- CREATE TABLE map_types (
--     id INTEGER PRIMARY KEY,
--     string_to_int MAP(VARCHAR, INTEGER),
--     int_to_string MAP(INTEGER, VARCHAR),
--     string_to_double MAP(VARCHAR, DOUBLE)
-- );

-- ==================== Customers table ====================
CREATE TABLE customers (
    customer_id INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    created_at TIMESTAMP DEFAULT current_timestamp NOT NULL,
    priority priority DEFAULT 'medium'
    -- tags VARCHAR[]  -- disabled for now
);

-- ==================== Products table ====================
CREATE TABLE products (
    product_id INTEGER PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    metadata JSON
    -- categories VARCHAR[]  -- disabled for now
);

-- ==================== Orders with foreign keys ====================
CREATE TABLE orders (
    order_id INTEGER PRIMARY KEY,
    customer_id INTEGER NOT NULL REFERENCES customers(customer_id),
    order_date DATE NOT NULL DEFAULT current_date,
    total_amount DECIMAL(12, 2),
    status VARCHAR(20) DEFAULT 'pending'
);

-- ==================== Order items with composite foreign key ====================
CREATE TABLE order_items (
    order_id INTEGER NOT NULL REFERENCES orders(order_id),
    product_id INTEGER NOT NULL REFERENCES products(product_id),
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (order_id, product_id)
);

-- ==================== Table with composite primary key ====================
CREATE TABLE departments (
    dept_code VARCHAR(10) NOT NULL,
    dept_region VARCHAR(10) NOT NULL,
    dept_name VARCHAR(100) NOT NULL,
    budget DECIMAL(15, 2),
    PRIMARY KEY (dept_code, dept_region)
);

CREATE TABLE employees (
    emp_number INTEGER NOT NULL,
    emp_suffix VARCHAR(5) NOT NULL,
    dept_code VARCHAR(10) NOT NULL,
    dept_region VARCHAR(10) NOT NULL,
    emp_name VARCHAR(100) NOT NULL,
    salary DECIMAL(10, 2),
    hire_date DATE DEFAULT current_date NOT NULL,
    PRIMARY KEY (emp_number, emp_suffix),
    FOREIGN KEY (dept_code, dept_region) REFERENCES departments(dept_code, dept_region)
);

-- ==================== Views ====================
CREATE VIEW customer_orders AS
SELECT
    c.customer_id,
    c.name AS customer_name,
    c.email,
    o.order_id,
    o.order_date,
    o.total_amount,
    o.status
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id;

CREATE VIEW order_details AS
SELECT
    o.order_id,
    o.order_date,
    c.name AS customer_name,
    p.name AS product_name,
    oi.quantity,
    oi.unit_price,
    (oi.quantity * oi.unit_price) AS line_total
FROM orders o
JOIN customers c ON o.customer_id = c.customer_id
JOIN order_items oi ON o.order_id = oi.order_id
JOIN products p ON oi.product_id = p.product_id;

-- ==================== Sample data ====================
INSERT INTO all_scalar_types (id, col_tinyint, col_smallint, col_integer, col_bigint, col_boolean, col_varchar, col_date, col_not_null)
VALUES (1, 42, 1000, 100000, 10000000000, true, 'test', '2025-01-01', 'required');

INSERT INTO customers (customer_id, name, email, priority) VALUES
(1, 'John Doe', 'john@example.com', 'high'),
(2, 'Jane Smith', 'jane@example.com', 'medium');

INSERT INTO products (product_id, sku, name, price) VALUES
(1, 'PROD-001', 'Widget A', 29.99),
(2, 'PROD-002', 'Widget B', 49.99);

INSERT INTO orders (order_id, customer_id, order_date, total_amount, status) VALUES
(1, 1, '2025-01-15', 79.98, 'completed'),
(2, 2, '2025-01-16', 29.99, 'pending');

INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES
(1, 1, 1, 29.99),
(1, 2, 1, 49.99),
(2, 1, 1, 29.99);

INSERT INTO departments (dept_code, dept_region, dept_name, budget) VALUES
('IT', 'US-WEST', 'Information Technology', 1000000.00),
('HR', 'US-EAST', 'Human Resources', 500000.00);

INSERT INTO employees (emp_number, emp_suffix, dept_code, dept_region, emp_name, salary) VALUES
(1001, 'A', 'IT', 'US-WEST', 'Alice Johnson', 95000.00),
(1002, 'B', 'HR', 'US-EAST', 'Bob Smith', 75000.00);

-- INSERT INTO list_types (id, int_list, string_list, double_list) VALUES
-- (1, [1, 2, 3, 4, 5], ['a', 'b', 'c'], [1.1, 2.2, 3.3]);

-- INSERT INTO map_types (id, string_to_int, int_to_string) VALUES
-- (1, MAP {'one': 1, 'two': 2, 'three': 3}, MAP {1: 'one', 2: 'two'});

-- ============================================================================
-- PRECISION TYPES TEST TABLES
-- These tables are used to test precision wrapper type generation.
-- The enablePreciseTypes selector should only include these tables.
-- ============================================================================

-- Precision types test table with all NOT NULL columns
CREATE TABLE precision_types (
    id                INTEGER           PRIMARY KEY,
    -- String precision types
    string10          VARCHAR(10)       NOT NULL,
    string20          VARCHAR(20)       NOT NULL,
    string50          VARCHAR(50)       NOT NULL,
    string100         VARCHAR(100)      NOT NULL,
    string255         VARCHAR(255)      NOT NULL,
    -- Decimal precision types
    decimal5_2        DECIMAL(5,2)      NOT NULL,
    decimal10_2       DECIMAL(10,2)     NOT NULL,
    decimal18_4       DECIMAL(18,4)     NOT NULL,
    -- Decimal with scale 0 (integers)
    decimal5_0        DECIMAL(5)        NOT NULL,
    decimal10_0       DECIMAL(10)       NOT NULL,
    decimal18_0       DECIMAL(18)       NOT NULL
);

-- Precision types test table with all NULLABLE columns
CREATE TABLE precision_types_null (
    id                INTEGER           PRIMARY KEY,
    -- String precision types
    string10          VARCHAR(10),
    string20          VARCHAR(20),
    string50          VARCHAR(50),
    string100         VARCHAR(100),
    string255         VARCHAR(255),
    -- Decimal precision types
    decimal5_2        DECIMAL(5,2),
    decimal10_2       DECIMAL(10,2),
    decimal18_4       DECIMAL(18,4),
    -- Decimal with scale 0 (integers)
    decimal5_0        DECIMAL(5),
    decimal10_0       DECIMAL(10),
    decimal18_0       DECIMAL(18)
);
