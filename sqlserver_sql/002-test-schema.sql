-- Test schema for SQL Server type testing
-- Comprehensive schema that exercises all SQL Server types

USE typr;
GO

-- ==================== Test Table: All Scalar Types ====================

CREATE TABLE all_scalar_types (
    -- Primary key with IDENTITY
    id INT IDENTITY(1,1) PRIMARY KEY,

    -- Integer types
    col_tinyint TINYINT NULL,              -- 0-255 (UNSIGNED!)
    col_smallint SMALLINT NULL,            -- -32768 to 32767
    col_int INT NULL,                      -- -2^31 to 2^31-1
    col_bigint BIGINT NULL,                -- -2^63 to 2^63-1

    -- Fixed-point types
    col_decimal DECIMAL(18, 4) NULL,
    col_numeric NUMERIC(10, 2) NULL,
    col_money MONEY NULL,
    col_smallmoney SMALLMONEY NULL,

    -- Floating-point types
    col_real REAL NULL,
    col_float FLOAT NULL,

    -- Boolean
    col_bit BIT NULL,

    -- String types (non-unicode)
    col_char CHAR(10) NULL,
    col_varchar VARCHAR(255) NULL,
    col_varchar_max VARCHAR(MAX) NULL,
    col_text TEXT NULL,

    -- String types (unicode)
    col_nchar NCHAR(10) NULL,
    col_nvarchar NVARCHAR(255) NULL,
    col_nvarchar_max NVARCHAR(MAX) NULL,
    col_ntext NTEXT NULL,

    -- Binary types
    col_binary BINARY(10) NULL,
    col_varbinary VARBINARY(255) NULL,
    col_varbinary_max VARBINARY(MAX) NULL,
    col_image IMAGE NULL,

    -- Date/time types
    col_date DATE NULL,
    col_time TIME(7) NULL,
    col_datetime DATETIME NULL,
    col_smalldatetime SMALLDATETIME NULL,
    col_datetime2 DATETIME2(7) NULL,
    col_datetimeoffset DATETIMEOFFSET(7) NULL,

    -- Special types
    col_uniqueidentifier UNIQUEIDENTIFIER NULL,
    col_xml XML NULL,
    col_json NVARCHAR(MAX) NULL CHECK (ISJSON(col_json) = 1),
    col_rowversion ROWVERSION,
    col_hierarchyid HIERARCHYID NULL,

    -- Spatial types
    col_geography GEOGRAPHY NULL,
    col_geometry GEOMETRY NULL,

    -- Non-null column for testing
    col_not_null NVARCHAR(100) NOT NULL DEFAULT 'default_value'
);
GO

-- Insert test data
INSERT INTO all_scalar_types (
    col_tinyint, col_smallint, col_int, col_bigint,
    col_decimal, col_numeric, col_money, col_smallmoney,
    col_real, col_float, col_bit,
    col_char, col_varchar, col_nchar, col_nvarchar,
    col_date, col_time, col_datetime, col_datetime2, col_datetimeoffset,
    col_uniqueidentifier,
    col_not_null
) VALUES (
    255, 32767, 2147483647, 9223372036854775807,
    12345.6789, 999.99, 922337203685477.5807, 214748.3647,
    3.14, 2.718281828, 1,
    'test      ', 'varchar test', 'nchar test', 'nvarchar test 中文',
    '2024-12-22', '14:30:45.1234567', '2024-12-22 14:30:45', '2024-12-22 14:30:45.1234567',
    '2024-12-22 14:30:45.1234567 -05:00',
    NEWID(),
    'test row'
);
GO

-- ==================== Simple Test Tables for Relationships ====================

CREATE TABLE customers (
    customer_id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    email NVARCHAR(255) NOT NULL UNIQUE,
    created_at DATETIME2 DEFAULT GETDATE()
);
GO

CREATE TABLE products (
    product_id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(200) NOT NULL,
    price MONEY NOT NULL,
    description NVARCHAR(MAX) NULL
);
GO

CREATE TABLE orders (
    order_id INT IDENTITY(1,1) PRIMARY KEY,
    customer_id INT NOT NULL FOREIGN KEY REFERENCES customers(customer_id),
    order_date DATETIME2 DEFAULT GETDATE(),
    total_amount MONEY NOT NULL
);
GO

CREATE TABLE order_items (
    order_item_id INT IDENTITY(1,1) PRIMARY KEY,
    order_id INT NOT NULL FOREIGN KEY REFERENCES orders(order_id),
    product_id INT NOT NULL FOREIGN KEY REFERENCES products(product_id),
    quantity INT NOT NULL,
    price MONEY NOT NULL
);
GO

-- Insert test data
INSERT INTO customers (name, email) VALUES
    ('John Doe', 'john@example.com'),
    ('Jane Smith', 'jane@example.com');
GO

INSERT INTO products (name, price, description) VALUES
    ('Widget A', 19.99, 'A high-quality widget'),
    ('Gadget B', 49.99, 'An amazing gadget');
GO

INSERT INTO orders (customer_id, total_amount) VALUES
    (1, 69.98),
    (2, 19.99);
GO

INSERT INTO order_items (order_id, product_id, quantity, price) VALUES
    (1, 1, 2, 19.99),  -- Order 1: 2x Widget A
    (1, 2, 1, 49.99),  -- Order 1: 1x Gadget B
    (2, 1, 1, 19.99);  -- Order 2: 1x Widget A
GO

-- ==================== Test View ====================

CREATE VIEW customer_orders_view AS
SELECT
    c.customer_id,
    c.name AS customer_name,
    c.email AS customer_email,
    o.order_id,
    o.order_date,
    o.total_amount AS order_total
FROM customers c
INNER JOIN orders o ON c.customer_id = o.customer_id;
GO

PRINT 'SQL Server test schema created successfully!';
GO
