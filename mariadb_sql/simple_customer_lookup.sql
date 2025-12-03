-- Simple customer lookup by email
SELECT customer_id,
       email,
       first_name,
       last_name,
       tier,
       status,
       created_at
FROM customers
WHERE email = :"email!"
