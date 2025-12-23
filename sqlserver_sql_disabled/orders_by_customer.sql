-- Get all orders for a customer with product details
-- Tests: INNER JOIN, multiple tables, foreign key navigation
SELECT
    o.order_id,
    o.order_date,
    o.total_amount,
    c.customer_id,
    c.name as customer_name!,
    c.email
FROM orders o
INNER JOIN customers c ON o.customer_id = c.customer_id
WHERE c.customer_id = :customerId:Int!
ORDER BY o.order_date DESC
