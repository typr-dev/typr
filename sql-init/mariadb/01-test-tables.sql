-- MariaDB test tables covering all supported types
-- This mirrors the PostgreSQL test-tables.sql structure

-- Test table with all NOT NULL columns
CREATE TABLE mariatest (
    -- Integer types
    tinyint_col       TINYINT           NOT NULL,
    smallint_col      SMALLINT          NOT NULL,
    mediumint_col     MEDIUMINT         NOT NULL,
    int_col           INT               NOT NULL,
    bigint_col        BIGINT            NOT NULL,
    -- Unsigned variants
    tinyint_u_col     TINYINT UNSIGNED  NOT NULL,
    smallint_u_col    SMALLINT UNSIGNED NOT NULL,
    mediumint_u_col   MEDIUMINT UNSIGNED NOT NULL,
    int_u_col         INT UNSIGNED      NOT NULL,
    bigint_u_col      BIGINT UNSIGNED   NOT NULL,
    -- Fixed-point
    decimal_col       DECIMAL(10,2)     NOT NULL,
    numeric_col       NUMERIC(15,4)     NOT NULL,
    -- Floating-point
    float_col         FLOAT             NOT NULL,
    double_col        DOUBLE            NOT NULL,
    -- Boolean
    bool_col          BOOLEAN           NOT NULL,
    -- Bit
    bit_col           BIT(8)            NOT NULL,
    bit1_col          BIT(1)            NOT NULL,
    -- String types
    char_col          CHAR(10)          NOT NULL,
    varchar_col       VARCHAR(255)      NOT NULL,
    tinytext_col      TINYTEXT          NOT NULL,
    text_col          TEXT              NOT NULL,
    mediumtext_col    MEDIUMTEXT        NOT NULL,
    longtext_col      LONGTEXT          NOT NULL,
    -- Binary types
    binary_col        BINARY(16)        NOT NULL,
    varbinary_col     VARBINARY(255)    NOT NULL,
    tinyblob_col      TINYBLOB          NOT NULL,
    blob_col          BLOB              NOT NULL,
    mediumblob_col    MEDIUMBLOB        NOT NULL,
    longblob_col      LONGBLOB          NOT NULL,
    -- Date/Time types
    date_col          DATE              NOT NULL,
    time_col          TIME              NOT NULL,
    time_fsp_col      TIME(6)           NOT NULL,
    datetime_col      DATETIME          NOT NULL,
    datetime_fsp_col  DATETIME(6)       NOT NULL,
    timestamp_col     TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    timestamp_fsp_col TIMESTAMP(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    year_col          YEAR              NOT NULL,
    -- ENUM and SET types (inline definitions)
    -- enum_col          ENUM('a', 'b', 'c', 'd') NOT NULL,  -- TODO: implement enum support
    set_col           SET('x', 'y', 'z')       NOT NULL,
    -- JSON type
    json_col          JSON              NOT NULL,
    -- Network types (MariaDB specific)
    inet4_col         INET4             NOT NULL,
    inet6_col         INET6             NOT NULL,
    PRIMARY KEY (int_col)
) ENGINE=InnoDB;

-- Test table with all NULLABLE columns
CREATE TABLE mariatestnull (
    -- Integer types
    tinyint_col       TINYINT,
    smallint_col      SMALLINT,
    mediumint_col     MEDIUMINT,
    int_col           INT,
    bigint_col        BIGINT,
    -- Unsigned variants
    tinyint_u_col     TINYINT UNSIGNED,
    smallint_u_col    SMALLINT UNSIGNED,
    mediumint_u_col   MEDIUMINT UNSIGNED,
    int_u_col         INT UNSIGNED,
    bigint_u_col      BIGINT UNSIGNED,
    -- Fixed-point
    decimal_col       DECIMAL(10,2),
    numeric_col       NUMERIC(15,4),
    -- Floating-point
    float_col         FLOAT,
    double_col        DOUBLE,
    -- Boolean
    bool_col          BOOLEAN,
    -- Bit
    bit_col           BIT(8),
    bit1_col          BIT(1),
    -- String types
    char_col          CHAR(10),
    varchar_col       VARCHAR(255),
    tinytext_col      TINYTEXT,
    text_col          TEXT,
    mediumtext_col    MEDIUMTEXT,
    longtext_col      LONGTEXT,
    -- Binary types
    binary_col        BINARY(16),
    varbinary_col     VARBINARY(255),
    tinyblob_col      TINYBLOB,
    blob_col          BLOB,
    mediumblob_col    MEDIUMBLOB,
    longblob_col      LONGBLOB,
    -- Date/Time types
    date_col          DATE,
    time_col          TIME,
    time_fsp_col      TIME(6),
    datetime_col      DATETIME,
    datetime_fsp_col  DATETIME(6),
    timestamp_col     TIMESTAMP         NULL,
    timestamp_fsp_col TIMESTAMP(6)      NULL,
    year_col          YEAR,
    -- ENUM and SET types (inline definitions)
    -- enum_col          ENUM('a', 'b', 'c', 'd'),  -- TODO: implement enum support
    set_col           SET('x', 'y', 'z'),
    -- JSON type
    json_col          JSON,
    -- Network types (MariaDB specific)
    inet4_col         INET4,
    inet6_col         INET6
) ENGINE=InnoDB;

-- Test table with spatial/geometry types
CREATE TABLE mariatest_spatial (
    id                INT               NOT NULL AUTO_INCREMENT,
    geometry_col      GEOMETRY          NOT NULL,
    point_col         POINT             NOT NULL,
    linestring_col    LINESTRING        NOT NULL,
    polygon_col       POLYGON           NOT NULL,
    multipoint_col    MULTIPOINT        NOT NULL,
    multilinestring_col MULTILINESTRING NOT NULL,
    multipolygon_col  MULTIPOLYGON      NOT NULL,
    geometrycollection_col GEOMETRYCOLLECTION NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

-- Test table with spatial/geometry types (nullable)
CREATE TABLE mariatest_spatial_null (
    id                INT               NOT NULL AUTO_INCREMENT,
    geometry_col      GEOMETRY,
    point_col         POINT,
    linestring_col    LINESTRING,
    polygon_col       POLYGON,
    multipoint_col    MULTIPOINT,
    multilinestring_col MULTILINESTRING,
    multipolygon_col  MULTIPOLYGON,
    geometrycollection_col GEOMETRYCOLLECTION,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

-- Test table for AUTO_INCREMENT
CREATE TABLE mariatest_identity (
    id                INT               NOT NULL AUTO_INCREMENT,
    name              VARCHAR(250)      NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

-- Test table with unique constraints
CREATE TABLE mariatest_unique (
    id                INT               NOT NULL AUTO_INCREMENT,
    email             VARCHAR(255)      NOT NULL,
    code              VARCHAR(50)       NOT NULL,
    category          VARCHAR(50)       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_code_category (code, category)
) ENGINE=InnoDB;

-- ============================================================================
-- PRECISION TYPES TEST TABLES
-- These tables are used to test precision wrapper type generation.
-- The enablePreciseTypes selector should only include these tables.
-- ============================================================================

-- Precision types test table with all NOT NULL columns
CREATE TABLE precision_types (
    id                INT               NOT NULL AUTO_INCREMENT,
    -- String precision types
    string10          VARCHAR(10)       NOT NULL,
    string20          VARCHAR(20)       NOT NULL,
    string50          VARCHAR(50)       NOT NULL,
    string100         VARCHAR(100)      NOT NULL,
    string255         VARCHAR(255)      NOT NULL,
    -- Fixed char
    char10            CHAR(10)          NOT NULL,
    -- Decimal precision types
    decimal5_2        DECIMAL(5,2)      NOT NULL,
    decimal10_2       DECIMAL(10,2)     NOT NULL,
    decimal18_4       DECIMAL(18,4)     NOT NULL,
    -- Numeric precision types
    numeric8_2        NUMERIC(8,2)      NOT NULL,
    numeric12_4       NUMERIC(12,4)     NOT NULL,
    -- Binary precision types
    binary16          BINARY(16)        NOT NULL,
    binary32          BINARY(32)        NOT NULL,
    binary64          BINARY(64)        NOT NULL,
    -- Time precision types
    time0             TIME(0)           NOT NULL,
    time3             TIME(3)           NOT NULL,
    time6             TIME(6)           NOT NULL,
    -- Datetime precision types
    datetime0         DATETIME(0)       NOT NULL,
    datetime3         DATETIME(3)       NOT NULL,
    datetime6         DATETIME(6)       NOT NULL,
    -- Timestamp precision types (need defaults)
    ts0               TIMESTAMP(0)      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ts3               TIMESTAMP(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ts6               TIMESTAMP(6)      NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB;

-- Precision types test table with all NULLABLE columns
CREATE TABLE precision_types_null (
    id                INT               NOT NULL AUTO_INCREMENT,
    -- String precision types
    string10          VARCHAR(10),
    string20          VARCHAR(20),
    string50          VARCHAR(50),
    string100         VARCHAR(100),
    string255         VARCHAR(255),
    -- Fixed char
    char10            CHAR(10),
    -- Decimal precision types
    decimal5_2        DECIMAL(5,2),
    decimal10_2       DECIMAL(10,2),
    decimal18_4       DECIMAL(18,4),
    -- Numeric precision types
    numeric8_2        NUMERIC(8,2),
    numeric12_4       NUMERIC(12,4),
    -- Binary precision types
    binary16          BINARY(16),
    binary32          BINARY(32),
    binary64          BINARY(64),
    -- Time precision types
    time0             TIME(0),
    time3             TIME(3),
    time6             TIME(6),
    -- Datetime precision types
    datetime0         DATETIME(0),
    datetime3         DATETIME(3),
    datetime6         DATETIME(6),
    -- Timestamp precision types
    ts0               TIMESTAMP(0)      NULL,
    ts3               TIMESTAMP(3)      NULL,
    ts6               TIMESTAMP(6)      NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;
