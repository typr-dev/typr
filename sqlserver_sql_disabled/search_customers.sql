-- Search customers by name pattern
-- Tests: LIKE operator, string matching, NULL handling
SELECT
    customer_id,
    name,
    email,
    created_at
FROM customers
WHERE name LIKE :namePattern:String!
ORDER BY name ASC
