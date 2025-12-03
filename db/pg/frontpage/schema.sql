-- Schema for Typo front page examples
CREATE SCHEMA IF NOT EXISTS frontpage;

-- PostgreSQL domains example (create first)
CREATE DOMAIN frontpage.email AS TEXT CHECK (VALUE ~ '^[^@]+@[^@]+\.[^@]+$');

-- Enum type example
CREATE TYPE frontpage.user_status AS ENUM ('active', 'inactive', 'suspended');
CREATE TYPE frontpage.order_status AS ENUM ('pending', 'active', 'shipped', 'cancelled');
CREATE TYPE frontpage.user_role AS ENUM ('admin', 'manager', 'employee');

-- Basic example tables
CREATE TABLE frontpage.department (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  budget DECIMAL(12,2)
);

CREATE TABLE frontpage.user (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email frontpage.email NOT NULL UNIQUE,
  name TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  department_id UUID REFERENCES frontpage.department(id),
  status frontpage.user_status DEFAULT 'active',
  verified BOOLEAN DEFAULT false
);

-- Relationships example tables
CREATE TABLE frontpage.product (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  in_stock BOOLEAN DEFAULT true,
  quantity INTEGER DEFAULT 0,
  last_restocked TIMESTAMP,
  last_modified TIMESTAMP DEFAULT NOW(),
  tags TEXT[] DEFAULT '{}',
  categories INTEGER[] DEFAULT '{}',
  prices DECIMAL[] DEFAULT '{}',
  attributes JSONB[] DEFAULT '{}'
);

CREATE TABLE frontpage.category (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL
);

CREATE TABLE frontpage.product_category (
  product_id UUID REFERENCES frontpage.product(id),
  category_id UUID REFERENCES frontpage.category(id),
  PRIMARY KEY (product_id, category_id)
);

CREATE TABLE frontpage.order (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES frontpage.user(id),
  product_id UUID REFERENCES frontpage.product(id),
  status frontpage.order_status DEFAULT 'pending',
  total DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  shipped_at TIMESTAMP
);

CREATE TABLE frontpage.order_item (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID REFERENCES frontpage.order(id),
  product_id UUID REFERENCES frontpage.product(id),
  quantity INTEGER NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  shipped_at TIMESTAMP
);

-- Customers table for joins
CREATE TABLE frontpage.customer (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES frontpage.user(id),
  company_name TEXT,
  credit_limit DECIMAL(10,2),
  verified BOOLEAN DEFAULT false
);

-- Many-to-many example
CREATE TABLE frontpage.role (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE frontpage.user_role (
  user_id UUID REFERENCES frontpage.user(id),
  role_id UUID REFERENCES frontpage.role(id),
  assigned_at TIMESTAMP DEFAULT NOW(),
  PRIMARY KEY (user_id, role_id)
);

-- Composite key example
CREATE TABLE frontpage.permission (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL UNIQUE
);

CREATE TABLE frontpage.user_permission (
  user_id UUID REFERENCES frontpage.user(id),
  permission_id UUID REFERENCES frontpage.permission(id),
  granted_at TIMESTAMP DEFAULT NOW(),
  PRIMARY KEY (user_id, permission_id)
);

-- Advanced PostgreSQL types example
CREATE TABLE frontpage.location (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  position POINT,
  area POLYGON,
  ip_range INET,
  metadata JSONB DEFAULT '{}'
);

-- Testing examples
CREATE TABLE frontpage.company (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL
);

ALTER TABLE frontpage.department ADD COLUMN company_id UUID REFERENCES frontpage.company(id);
ALTER TABLE frontpage.user ADD COLUMN manager_id UUID REFERENCES frontpage.user(id);
ALTER TABLE frontpage.user ADD COLUMN role frontpage.user_role DEFAULT 'employee';

-- Complex join examples
CREATE TABLE frontpage.employee (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  person_id UUID UNIQUE NOT NULL,
  salary DECIMAL(10,2)
);

CREATE TABLE frontpage.person (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  address_id UUID,
  created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE frontpage.address (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  city TEXT NOT NULL,
  country TEXT NOT NULL
);

ALTER TABLE frontpage.person ADD CONSTRAINT fk_address 
  FOREIGN KEY (address_id) REFERENCES frontpage.address(id);

ALTER TABLE frontpage.employee ADD CONSTRAINT fk_person 
  FOREIGN KEY (person_id) REFERENCES frontpage.person(id);