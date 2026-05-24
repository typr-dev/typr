-- Customer search with multiple optional filters.
-- Tests: optional parameters, LIKE patterns, complex WHERE, ORDER BY/LIMIT.

SELECT
    customer_id,
    name,
    email,
    created_at
FROM customers
WHERE
    (:"name_pattern?" IS NULL OR name LIKE :name_pattern)
    AND (:"email_pattern?" IS NULL OR email LIKE :email_pattern)
    AND (:"created_after?" IS NULL OR created_at >= :created_after)
ORDER BY created_at DESC, customer_id
LIMIT :"max_results!";
