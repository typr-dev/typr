-- Complex Ordering System Schema for MariaDB
-- Uses all features from the MariaDB support plan:
-- - All integer types (signed/unsigned)
-- - DECIMAL for money
-- - Various string types
-- - Date/Time with fractional seconds
-- - ENUM and SET types (inline)
-- - JSON for flexible data
-- - AUTO_INCREMENT
-- - Foreign keys
-- - Unique constraints
-- - Composite primary keys
-- - Views
-- - Spatial types for location data

-- ============================================
-- Customer Management
-- ============================================

CREATE TABLE customer_status (
    status_code       VARCHAR(20)       NOT NULL,
    description       VARCHAR(255)      NOT NULL,
    is_active         BOOLEAN           NOT NULL DEFAULT TRUE,
    PRIMARY KEY (status_code)
) ENGINE=InnoDB;

INSERT INTO customer_status (status_code, description, is_active) VALUES
('active', 'Active customer', TRUE),
('suspended', 'Account suspended', TRUE),
('closed', 'Account closed', FALSE),
('pending', 'Pending verification', TRUE);

CREATE TABLE customers (
    customer_id       BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    email             VARCHAR(255)      NOT NULL,
    password_hash     BINARY(64)        NOT NULL,
    first_name        VARCHAR(100)      NOT NULL,
    last_name         VARCHAR(100)      NOT NULL,
    phone             VARCHAR(20),
    status            VARCHAR(20)       NOT NULL DEFAULT 'pending',
    tier              ENUM('bronze', 'silver', 'gold', 'platinum') NOT NULL DEFAULT 'bronze',
    preferences       JSON,
    marketing_flags   SET('email', 'sms', 'push', 'mail'),
    notes             TEXT,
    created_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    last_login_at     DATETIME(6),
    PRIMARY KEY (customer_id),
    UNIQUE KEY uk_customer_email (email),
    CONSTRAINT fk_customer_status FOREIGN KEY (status) REFERENCES customer_status (status_code)
) ENGINE=InnoDB;

CREATE TABLE customer_addresses (
    address_id        INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    customer_id       BIGINT UNSIGNED   NOT NULL,
    address_type      ENUM('billing', 'shipping', 'both') NOT NULL,
    is_default        BOOLEAN           NOT NULL DEFAULT FALSE,
    recipient_name    VARCHAR(200)      NOT NULL,
    street_line1      VARCHAR(255)      NOT NULL,
    street_line2      VARCHAR(255),
    city              VARCHAR(100)      NOT NULL,
    state_province    VARCHAR(100),
    postal_code       VARCHAR(20)       NOT NULL,
    country_code      CHAR(2)           NOT NULL,
    location          POINT,
    delivery_notes    TINYTEXT,
    created_at        DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (address_id),
    KEY idx_customer_addresses (customer_id),
    CONSTRAINT fk_address_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================
-- Product Catalog
-- ============================================

CREATE TABLE categories (
    category_id       MEDIUMINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id         MEDIUMINT UNSIGNED,
    name              VARCHAR(100)      NOT NULL,
    slug              VARCHAR(100)      NOT NULL,
    description       MEDIUMTEXT,
    image_url         VARCHAR(500),
    sort_order        SMALLINT          NOT NULL DEFAULT 0,
    is_visible        BOOLEAN           NOT NULL DEFAULT TRUE,
    metadata          JSON,
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_category_slug (slug),
    KEY idx_category_parent (parent_id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES categories (category_id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE brands (
    brand_id          SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name              VARCHAR(100)      NOT NULL,
    slug              VARCHAR(100)      NOT NULL,
    logo_blob         MEDIUMBLOB,
    website_url       VARCHAR(500),
    country_of_origin CHAR(2),
    is_active         BOOLEAN           NOT NULL DEFAULT TRUE,
    PRIMARY KEY (brand_id),
    UNIQUE KEY uk_brand_slug (slug)
) ENGINE=InnoDB;

CREATE TABLE products (
    product_id        BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    sku               VARCHAR(50)       NOT NULL,
    brand_id          SMALLINT UNSIGNED,
    name              VARCHAR(255)      NOT NULL,
    short_description VARCHAR(500),
    full_description  LONGTEXT,
    base_price        DECIMAL(12,4)     NOT NULL,
    cost_price        DECIMAL(12,4),
    weight_kg         DECIMAL(8,3),
    dimensions_json   JSON COMMENT 'length, width, height in cm',
    status            ENUM('draft', 'active', 'discontinued', 'out_of_stock') NOT NULL DEFAULT 'draft',
    tax_class         ENUM('standard', 'reduced', 'zero', 'exempt') NOT NULL DEFAULT 'standard',
    tags              SET('featured', 'bestseller', 'new', 'sale', 'clearance'),
    attributes        JSON,
    seo_metadata      JSON,
    created_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    published_at      DATETIME,
    PRIMARY KEY (product_id),
    UNIQUE KEY uk_product_sku (sku),
    KEY idx_product_brand (brand_id),
    KEY idx_product_status (status),
    CONSTRAINT fk_product_brand FOREIGN KEY (brand_id) REFERENCES brands (brand_id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE product_categories (
    product_id        BIGINT UNSIGNED   NOT NULL,
    category_id       MEDIUMINT UNSIGNED NOT NULL,
    is_primary        BOOLEAN           NOT NULL DEFAULT FALSE,
    sort_order        SMALLINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, category_id),
    KEY idx_pc_category (category_id),
    CONSTRAINT fk_pc_product FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE CASCADE,
    CONSTRAINT fk_pc_category FOREIGN KEY (category_id) REFERENCES categories (category_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE product_images (
    image_id          INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    product_id        BIGINT UNSIGNED   NOT NULL,
    image_url         VARCHAR(500)      NOT NULL,
    thumbnail_url     VARCHAR(500),
    alt_text          VARCHAR(255),
    sort_order        TINYINT UNSIGNED  NOT NULL DEFAULT 0,
    is_primary        BOOLEAN           NOT NULL DEFAULT FALSE,
    image_data        LONGBLOB COMMENT 'Optional embedded image data',
    PRIMARY KEY (image_id),
    KEY idx_pi_product (product_id),
    CONSTRAINT fk_pi_product FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================
-- Inventory Management
-- ============================================

CREATE TABLE warehouses (
    warehouse_id      TINYINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    code              CHAR(5)           NOT NULL,
    name              VARCHAR(100)      NOT NULL,
    address           VARCHAR(500)      NOT NULL,
    location          POINT             NOT NULL,
    service_area      POLYGON,
    timezone          VARCHAR(50)       NOT NULL DEFAULT 'UTC',
    is_active         BOOLEAN           NOT NULL DEFAULT TRUE,
    contact_email     VARCHAR(255),
    contact_phone     VARCHAR(20),
    PRIMARY KEY (warehouse_id),
    UNIQUE KEY uk_warehouse_code (code),
    SPATIAL INDEX idx_warehouse_location (location)
) ENGINE=InnoDB;

CREATE TABLE inventory (
    inventory_id      BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    product_id        BIGINT UNSIGNED   NOT NULL,
    warehouse_id      TINYINT UNSIGNED  NOT NULL,
    quantity_on_hand  INT               NOT NULL DEFAULT 0,
    quantity_reserved INT               NOT NULL DEFAULT 0,
    quantity_on_order INT               NOT NULL DEFAULT 0,
    reorder_point     INT               NOT NULL DEFAULT 0,
    reorder_quantity  INT               NOT NULL DEFAULT 0,
    bin_location      VARCHAR(50),
    last_counted_at   DATETIME,
    updated_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (inventory_id),
    UNIQUE KEY uk_inventory_product_warehouse (product_id, warehouse_id),
    KEY idx_inventory_warehouse (warehouse_id),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (warehouse_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================
-- Pricing and Promotions
-- ============================================

CREATE TABLE price_tiers (
    tier_id           TINYINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    name              VARCHAR(50)       NOT NULL,
    min_quantity      INT UNSIGNED      NOT NULL DEFAULT 1,
    discount_type     ENUM('percentage', 'fixed_amount', 'fixed_price') NOT NULL,
    discount_value    DECIMAL(10,4)     NOT NULL,
    PRIMARY KEY (tier_id)
) ENGINE=InnoDB;

CREATE TABLE product_prices (
    price_id          BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    product_id        BIGINT UNSIGNED   NOT NULL,
    tier_id           TINYINT UNSIGNED,
    price             DECIMAL(12,4)     NOT NULL,
    currency_code     CHAR(3)           NOT NULL DEFAULT 'USD',
    valid_from        DATE              NOT NULL,
    valid_to          DATE,
    PRIMARY KEY (price_id),
    KEY idx_pp_product (product_id),
    KEY idx_pp_tier (tier_id),
    KEY idx_pp_dates (valid_from, valid_to),
    CONSTRAINT fk_pp_product FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE CASCADE,
    CONSTRAINT fk_pp_tier FOREIGN KEY (tier_id) REFERENCES price_tiers (tier_id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE promotions (
    promotion_id      INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    code              VARCHAR(50)       NOT NULL,
    name              VARCHAR(200)      NOT NULL,
    description       TEXT,
    discount_type     ENUM('percentage', 'fixed_amount', 'buy_x_get_y', 'free_shipping') NOT NULL,
    discount_value    DECIMAL(10,4)     NOT NULL,
    min_order_amount  DECIMAL(12,4),
    max_uses          INT UNSIGNED,
    uses_count        INT UNSIGNED      NOT NULL DEFAULT 0,
    max_uses_per_customer TINYINT UNSIGNED,
    applicable_to     SET('all', 'categories', 'products', 'brands', 'customers'),
    rules_json        JSON COMMENT 'Complex eligibility rules',
    valid_from        DATETIME          NOT NULL,
    valid_to          DATETIME          NOT NULL,
    is_active         BOOLEAN           NOT NULL DEFAULT TRUE,
    created_at        DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (promotion_id),
    UNIQUE KEY uk_promotion_code (code),
    KEY idx_promotion_dates (valid_from, valid_to, is_active)
) ENGINE=InnoDB;

-- ============================================
-- Order Management
-- ============================================

CREATE TABLE orders (
    order_id          BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    order_number      VARCHAR(30)       NOT NULL,
    customer_id       BIGINT UNSIGNED   NOT NULL,
    order_status      ENUM('pending', 'confirmed', 'processing', 'shipped', 'delivered', 'cancelled', 'refunded') NOT NULL DEFAULT 'pending',
    payment_status    ENUM('pending', 'authorized', 'captured', 'partially_refunded', 'refunded', 'failed') NOT NULL DEFAULT 'pending',
    shipping_address_id INT UNSIGNED,
    billing_address_id INT UNSIGNED,
    subtotal          DECIMAL(14,4)     NOT NULL,
    shipping_cost     DECIMAL(10,4)     NOT NULL DEFAULT 0,
    tax_amount        DECIMAL(10,4)     NOT NULL DEFAULT 0,
    discount_amount   DECIMAL(10,4)     NOT NULL DEFAULT 0,
    total_amount      DECIMAL(14,4)     NOT NULL,
    currency_code     CHAR(3)           NOT NULL DEFAULT 'USD',
    promotion_id      INT UNSIGNED,
    notes             TEXT,
    internal_notes    MEDIUMTEXT,
    ip_address        INET6,
    user_agent        VARCHAR(500),
    ordered_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    confirmed_at      DATETIME(6),
    shipped_at        DATETIME(6),
    delivered_at      DATETIME(6),
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_order_number (order_number),
    KEY idx_order_customer (customer_id),
    KEY idx_order_status (order_status),
    KEY idx_order_date (ordered_at),
    CONSTRAINT fk_order_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id),
    CONSTRAINT fk_order_shipping_addr FOREIGN KEY (shipping_address_id) REFERENCES customer_addresses (address_id),
    CONSTRAINT fk_order_billing_addr FOREIGN KEY (billing_address_id) REFERENCES customer_addresses (address_id),
    CONSTRAINT fk_order_promotion FOREIGN KEY (promotion_id) REFERENCES promotions (promotion_id)
) ENGINE=InnoDB;

CREATE TABLE order_items (
    item_id           BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    order_id          BIGINT UNSIGNED   NOT NULL,
    product_id        BIGINT UNSIGNED   NOT NULL,
    sku               VARCHAR(50)       NOT NULL,
    product_name      VARCHAR(255)      NOT NULL,
    quantity          SMALLINT UNSIGNED NOT NULL,
    unit_price        DECIMAL(12,4)     NOT NULL,
    discount_amount   DECIMAL(10,4)     NOT NULL DEFAULT 0,
    tax_amount        DECIMAL(10,4)     NOT NULL DEFAULT 0,
    line_total        DECIMAL(14,4)     NOT NULL,
    fulfillment_status ENUM('pending', 'allocated', 'picked', 'packed', 'shipped', 'delivered', 'returned') NOT NULL DEFAULT 'pending',
    warehouse_id      TINYINT UNSIGNED,
    notes             TINYTEXT,
    PRIMARY KEY (item_id),
    KEY idx_oi_order (order_id),
    KEY idx_oi_product (product_id),
    KEY idx_oi_warehouse (warehouse_id),
    CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders (order_id) ON DELETE CASCADE,
    CONSTRAINT fk_oi_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_oi_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (warehouse_id)
) ENGINE=InnoDB;

CREATE TABLE order_history (
    history_id        BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    order_id          BIGINT UNSIGNED   NOT NULL,
    previous_status   ENUM('pending', 'confirmed', 'processing', 'shipped', 'delivered', 'cancelled', 'refunded'),
    new_status        ENUM('pending', 'confirmed', 'processing', 'shipped', 'delivered', 'cancelled', 'refunded') NOT NULL,
    changed_by        VARCHAR(100),
    change_reason     VARCHAR(500),
    metadata          JSON,
    created_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (history_id),
    KEY idx_oh_order (order_id),
    KEY idx_oh_date (created_at),
    CONSTRAINT fk_oh_order FOREIGN KEY (order_id) REFERENCES orders (order_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================
-- Shipping and Delivery
-- ============================================

CREATE TABLE shipping_carriers (
    carrier_id        TINYINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    code              VARCHAR(20)       NOT NULL,
    name              VARCHAR(100)      NOT NULL,
    tracking_url_template VARCHAR(500),
    api_config        JSON,
    is_active         BOOLEAN           NOT NULL DEFAULT TRUE,
    PRIMARY KEY (carrier_id),
    UNIQUE KEY uk_carrier_code (code)
) ENGINE=InnoDB;

CREATE TABLE shipments (
    shipment_id       BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    order_id          BIGINT UNSIGNED   NOT NULL,
    carrier_id        TINYINT UNSIGNED  NOT NULL,
    tracking_number   VARCHAR(100),
    shipping_method   VARCHAR(50)       NOT NULL,
    weight_kg         DECIMAL(8,3),
    dimensions_json   JSON,
    label_data        LONGBLOB,
    status            ENUM('pending', 'label_created', 'picked_up', 'in_transit', 'out_for_delivery', 'delivered', 'returned', 'exception') NOT NULL DEFAULT 'pending',
    estimated_delivery_date DATE,
    actual_delivery_at DATETIME(6),
    shipping_cost     DECIMAL(10,4)     NOT NULL,
    insurance_amount  DECIMAL(10,4),
    origin_warehouse_id TINYINT UNSIGNED,
    shipped_at        DATETIME(6),
    created_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (shipment_id),
    KEY idx_ship_order (order_id),
    KEY idx_ship_carrier (carrier_id),
    KEY idx_ship_tracking (tracking_number),
    KEY idx_ship_status (status),
    CONSTRAINT fk_ship_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_ship_carrier FOREIGN KEY (carrier_id) REFERENCES shipping_carriers (carrier_id),
    CONSTRAINT fk_ship_warehouse FOREIGN KEY (origin_warehouse_id) REFERENCES warehouses (warehouse_id)
) ENGINE=InnoDB;

-- ============================================
-- Payment Processing
-- ============================================

CREATE TABLE payment_methods (
    method_id         TINYINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    code              VARCHAR(30)       NOT NULL,
    name              VARCHAR(100)      NOT NULL,
    method_type       ENUM('credit_card', 'debit_card', 'bank_transfer', 'digital_wallet', 'buy_now_pay_later', 'crypto') NOT NULL,
    processor_config  JSON,
    is_active         BOOLEAN           NOT NULL DEFAULT TRUE,
    sort_order        TINYINT           NOT NULL DEFAULT 0,
    PRIMARY KEY (method_id),
    UNIQUE KEY uk_pm_code (code)
) ENGINE=InnoDB;

CREATE TABLE payments (
    payment_id        BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    order_id          BIGINT UNSIGNED   NOT NULL,
    method_id         TINYINT UNSIGNED  NOT NULL,
    transaction_id    VARCHAR(100),
    amount            DECIMAL(14,4)     NOT NULL,
    currency_code     CHAR(3)           NOT NULL DEFAULT 'USD',
    status            ENUM('pending', 'processing', 'authorized', 'captured', 'failed', 'cancelled', 'refunded') NOT NULL DEFAULT 'pending',
    processor_response JSON,
    error_message     VARCHAR(500),
    ip_address        INET6,
    created_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at      DATETIME(6),
    PRIMARY KEY (payment_id),
    KEY idx_pay_order (order_id),
    KEY idx_pay_method (method_id),
    KEY idx_pay_transaction (transaction_id),
    KEY idx_pay_status (status),
    CONSTRAINT fk_pay_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_pay_method FOREIGN KEY (method_id) REFERENCES payment_methods (method_id)
) ENGINE=InnoDB;

-- ============================================
-- Reviews and Ratings
-- ============================================

CREATE TABLE reviews (
    review_id         BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    product_id        BIGINT UNSIGNED   NOT NULL,
    customer_id       BIGINT UNSIGNED   NOT NULL,
    order_item_id     BIGINT UNSIGNED,
    rating            TINYINT UNSIGNED  NOT NULL,
    title             VARCHAR(200),
    content           TEXT,
    pros              JSON,
    cons              JSON,
    images            JSON COMMENT 'Array of image URLs',
    is_verified_purchase BOOLEAN        NOT NULL DEFAULT FALSE,
    is_approved       BOOLEAN           NOT NULL DEFAULT FALSE,
    helpful_votes     INT UNSIGNED      NOT NULL DEFAULT 0,
    unhelpful_votes   INT UNSIGNED      NOT NULL DEFAULT 0,
    admin_response    TEXT,
    responded_at      DATETIME,
    created_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (review_id),
    KEY idx_rev_product (product_id),
    KEY idx_rev_customer (customer_id),
    KEY idx_rev_rating (rating),
    CONSTRAINT fk_rev_product FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE CASCADE,
    CONSTRAINT fk_rev_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id),
    CONSTRAINT fk_rev_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (item_id),
    CONSTRAINT chk_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB;

-- ============================================
-- Audit Log
-- ============================================

CREATE TABLE audit_log (
    log_id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    table_name        VARCHAR(100)      NOT NULL,
    record_id         VARCHAR(100)      NOT NULL,
    action            ENUM('INSERT', 'UPDATE', 'DELETE') NOT NULL,
    old_values        JSON,
    new_values        JSON,
    changed_by        VARCHAR(100),
    changed_at        DATETIME(6)       NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    client_ip         INET6,
    session_id        VARBINARY(64),
    PRIMARY KEY (log_id),
    KEY idx_audit_table (table_name),
    KEY idx_audit_record (table_name, record_id),
    KEY idx_audit_date (changed_at)
) ENGINE=InnoDB;

-- ============================================
-- Views
-- ============================================

-- Customer summary view
CREATE VIEW v_customer_summary AS
SELECT
    c.customer_id,
    c.email,
    CONCAT(c.first_name, ' ', c.last_name) AS full_name,
    c.tier,
    c.status,
    c.created_at,
    c.last_login_at,
    COUNT(DISTINCT o.order_id) AS total_orders,
    COALESCE(SUM(o.total_amount), 0) AS lifetime_value,
    MAX(o.ordered_at) AS last_order_date
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id AND o.order_status NOT IN ('cancelled', 'refunded')
GROUP BY c.customer_id, c.email, c.first_name, c.last_name, c.tier, c.status, c.created_at, c.last_login_at;

-- Product catalog view with inventory
CREATE VIEW v_product_catalog AS
SELECT
    p.product_id,
    p.sku,
    p.name,
    p.short_description,
    p.base_price,
    p.status,
    p.tags,
    b.name AS brand_name,
    COALESCE(SUM(i.quantity_on_hand - i.quantity_reserved), 0) AS available_quantity,
    COALESCE(AVG(r.rating), 0) AS avg_rating,
    COUNT(DISTINCT r.review_id) AS review_count
FROM products p
LEFT JOIN brands b ON p.brand_id = b.brand_id
LEFT JOIN inventory i ON p.product_id = i.product_id
LEFT JOIN reviews r ON p.product_id = r.product_id AND r.is_approved = TRUE
WHERE p.status = 'active'
GROUP BY p.product_id, p.sku, p.name, p.short_description, p.base_price, p.status, p.tags, b.name;

-- Order details view
CREATE VIEW v_order_details AS
SELECT
    o.order_id,
    o.order_number,
    o.order_status,
    o.payment_status,
    o.total_amount,
    o.currency_code,
    o.ordered_at,
    c.email AS customer_email,
    CONCAT(c.first_name, ' ', c.last_name) AS customer_name,
    COUNT(oi.item_id) AS item_count,
    SUM(oi.quantity) AS total_quantity,
    s.tracking_number,
    s.status AS shipping_status,
    sc.name AS carrier_name
FROM orders o
JOIN customers c ON o.customer_id = c.customer_id
LEFT JOIN order_items oi ON o.order_id = oi.order_id
LEFT JOIN shipments s ON o.order_id = s.order_id
LEFT JOIN shipping_carriers sc ON s.carrier_id = sc.carrier_id
GROUP BY o.order_id, o.order_number, o.order_status, o.payment_status, o.total_amount,
         o.currency_code, o.ordered_at, c.email, c.first_name, c.last_name,
         s.tracking_number, s.status, sc.name;

-- Inventory status view
CREATE VIEW v_inventory_status AS
SELECT
    p.product_id,
    p.sku,
    p.name AS product_name,
    w.warehouse_id,
    w.code AS warehouse_code,
    w.name AS warehouse_name,
    i.quantity_on_hand,
    i.quantity_reserved,
    i.quantity_on_order,
    (i.quantity_on_hand - i.quantity_reserved) AS available,
    i.reorder_point,
    CASE
        WHEN (i.quantity_on_hand - i.quantity_reserved) <= 0 THEN 'out_of_stock'
        WHEN (i.quantity_on_hand - i.quantity_reserved) <= i.reorder_point THEN 'low_stock'
        ELSE 'in_stock'
    END AS stock_status,
    i.bin_location,
    i.last_counted_at
FROM inventory i
JOIN products p ON i.product_id = p.product_id
JOIN warehouses w ON i.warehouse_id = w.warehouse_id;

-- Daily sales summary view
CREATE VIEW v_daily_sales AS
SELECT
    DATE(o.ordered_at) AS order_date,
    COUNT(DISTINCT o.order_id) AS order_count,
    COUNT(DISTINCT o.customer_id) AS unique_customers,
    SUM(oi.quantity) AS items_sold,
    SUM(o.subtotal) AS gross_sales,
    SUM(o.discount_amount) AS total_discounts,
    SUM(o.shipping_cost) AS total_shipping,
    SUM(o.tax_amount) AS total_tax,
    SUM(o.total_amount) AS net_sales,
    AVG(o.total_amount) AS avg_order_value
FROM orders o
JOIN order_items oi ON o.order_id = oi.order_id
WHERE o.order_status NOT IN ('cancelled', 'refunded')
GROUP BY DATE(o.ordered_at);

-- Warehouse coverage view (using spatial)
CREATE VIEW v_warehouse_coverage AS
SELECT
    w.warehouse_id,
    w.code,
    w.name,
    w.address,
    ST_AsText(w.location) AS location_wkt,
    ST_AsText(w.service_area) AS service_area_wkt,
    w.timezone,
    w.is_active,
    COUNT(DISTINCT i.product_id) AS products_stocked,
    SUM(i.quantity_on_hand) AS total_inventory
FROM warehouses w
LEFT JOIN inventory i ON w.warehouse_id = i.warehouse_id
GROUP BY w.warehouse_id, w.code, w.name, w.address, w.location, w.service_area, w.timezone, w.is_active;
