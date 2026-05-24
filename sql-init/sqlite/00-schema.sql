-- SQLite test schema for Typr
-- Exercises every type affinity, plus PK/FK/composite/unique/precision tables.
--
-- SQLite notes:
--   - All values use one of five storage classes (NULL, INTEGER, REAL, TEXT, BLOB).
--   - Declared column types map to one of five "affinities" via substring match.
--   - DATE/TIME/TIMESTAMP have no native storage class; we use ISO-8601 TEXT to
--     match the foundations SqliteTypes default codecs.
--   - FOREIGN KEYS are off by default on each connection (PRAGMA foreign_keys=ON).
--   - RETURNING is supported (SQLite >= 3.35).

-- ==================== Table covering all canonical affinities ====================
CREATE TABLE all_scalar_types (
    id INTEGER PRIMARY KEY,
    -- INTEGER affinity, with concrete Java mappings via SqliteTypes
    col_tinyint  TINYINT,
    col_smallint SMALLINT,
    col_int      INT,
    col_integer  INTEGER,
    col_bigint   BIGINT,
    col_boolean  BOOLEAN,
    -- REAL affinity
    col_real     REAL,
    col_double   DOUBLE,
    col_float    FLOAT,
    -- NUMERIC affinity
    col_decimal  DECIMAL(10, 2),
    col_numeric  NUMERIC,
    -- TEXT affinity
    col_text     TEXT,
    col_varchar  VARCHAR(100),
    col_char     CHAR(5),
    col_clob     CLOB,
    -- BLOB affinity
    col_blob     BLOB,
    col_binary   BINARY,
    -- Date/time (stored as ISO-8601 TEXT)
    col_date     DATE,
    col_time     TIME,
    col_datetime DATETIME,
    col_timestamp TIMESTAMP,
    -- Convenience types backed by TEXT
    col_uuid     UUID,
    col_json     JSON,
    -- NOT NULL marker
    col_not_null TEXT NOT NULL
);

-- ==================== Customers / Products / Orders / Order items ====================
CREATE TABLE customers (
    customer_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    email       TEXT,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE products (
    product_id INTEGER PRIMARY KEY AUTOINCREMENT,
    sku        VARCHAR(50) NOT NULL UNIQUE,
    name       TEXT NOT NULL,
    price      DECIMAL(10, 2) NOT NULL,
    metadata   JSON
);

CREATE TABLE orders (
    order_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id  INTEGER NOT NULL REFERENCES customers(customer_id),
    order_date   DATE NOT NULL,
    total_amount DECIMAL(12, 2),
    status       TEXT NOT NULL DEFAULT 'pending'
);

CREATE TABLE order_items (
    order_id   INTEGER NOT NULL REFERENCES orders(order_id),
    product_id INTEGER NOT NULL REFERENCES products(product_id),
    quantity   INTEGER NOT NULL DEFAULT 1,
    unit_price DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (order_id, product_id)
);

-- ==================== Composite primary key + composite foreign key ====================
CREATE TABLE departments (
    dept_code   VARCHAR(10) NOT NULL,
    dept_region VARCHAR(10) NOT NULL,
    dept_name   TEXT NOT NULL,
    budget      DECIMAL(15, 2),
    PRIMARY KEY (dept_code, dept_region)
);

CREATE TABLE employees (
    emp_number  INTEGER     NOT NULL,
    emp_suffix  VARCHAR(5)  NOT NULL,
    dept_code   VARCHAR(10) NOT NULL,
    dept_region VARCHAR(10) NOT NULL,
    emp_name    TEXT        NOT NULL,
    salary      DECIMAL(10, 2),
    hire_date   DATE        NOT NULL DEFAULT CURRENT_DATE,
    PRIMARY KEY (emp_number, emp_suffix),
    FOREIGN KEY (dept_code, dept_region) REFERENCES departments(dept_code, dept_region)
);

-- ==================== UNIQUE constraint (not a PK) ====================
CREATE TABLE users (
    user_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    email    VARCHAR(120) NOT NULL,
    UNIQUE (email)
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
JOIN customers c   ON o.customer_id = c.customer_id
JOIN order_items oi ON o.order_id    = oi.order_id
JOIN products p    ON oi.product_id = p.product_id;

-- ==================== Sample data ====================
INSERT INTO all_scalar_types (id, col_tinyint, col_smallint, col_int, col_integer, col_bigint,
                              col_boolean, col_text, col_date, col_not_null)
VALUES (1, 42, 1000, 100000, 100000, 10000000000, 1, 'hello', '2025-01-01', 'required');

INSERT INTO customers (customer_id, name, email) VALUES
(1, 'John Doe',   'john@example.com'),
(2, 'Jane Smith', 'jane@example.com');

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
('HR', 'US-EAST', 'Human Resources',         500000.00);

INSERT INTO employees (emp_number, emp_suffix, dept_code, dept_region, emp_name, salary) VALUES
(1001, 'A', 'IT', 'US-WEST', 'Alice Johnson', 95000.00),
(1002, 'B', 'HR', 'US-EAST', 'Bob Smith',     75000.00);

INSERT INTO users (user_id, username, email) VALUES
(1, 'jdoe',  'jdoe@example.com'),
(2, 'jsmith','jsmith@example.com');

-- ============================================================================
-- PRECISION TYPES TEST TABLES
-- These test precise wrapper-type generation. The precision_types selector
-- in typr.yaml restricts wrapper generation to these tables.
-- ============================================================================

CREATE TABLE precision_types (
    id           INTEGER       PRIMARY KEY,
    string10     VARCHAR(10)   NOT NULL,
    string20     VARCHAR(20)   NOT NULL,
    string50     VARCHAR(50)   NOT NULL,
    string100    VARCHAR(100)  NOT NULL,
    string255    VARCHAR(255)  NOT NULL,
    decimal5_2   DECIMAL(5,2)  NOT NULL,
    decimal10_2  DECIMAL(10,2) NOT NULL,
    decimal18_4  DECIMAL(18,4) NOT NULL,
    decimal5_0   DECIMAL(5,0)  NOT NULL,
    decimal10_0  DECIMAL(10,0) NOT NULL,
    decimal18_0  DECIMAL(18,0) NOT NULL
);

CREATE TABLE precision_types_null (
    id           INTEGER       PRIMARY KEY,
    string10     VARCHAR(10),
    string20     VARCHAR(20),
    string50     VARCHAR(50),
    string100    VARCHAR(100),
    string255    VARCHAR(255),
    decimal5_2   DECIMAL(5,2),
    decimal10_2  DECIMAL(10,2),
    decimal18_4  DECIMAL(18,4),
    decimal5_0   DECIMAL(5,0),
    decimal10_0  DECIMAL(10,0),
    decimal18_0  DECIMAL(18,0)
);
