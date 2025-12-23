-- Find customer by email address
-- Tests: basic SELECT with WHERE clause, parameter binding
SELECT
    customer_id,
    name,
    email,
    created_at
FROM customers
WHERE email = :email:String!
