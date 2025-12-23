-- Find customers by email pattern
SELECT
    customer_id,
    name as customer_name,
    email as customer_email,
    created_at
FROM customers
WHERE email LIKE :"email_pattern:String!"
ORDER BY created_at DESC
