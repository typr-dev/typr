-- Get order summary statistics per customer
-- Tests: GROUP BY, aggregation functions, HAVING clause
SELECT
    c.customer_id,
    c.name,
    c.email,
    COUNT(o.order_id) as order_count!,
    SUM(o.total_amount) as total_spent!,
    AVG(o.total_amount) as average_order!,
    MAX(o.order_date) as last_order_date
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
GROUP BY c.customer_id, c.name, c.email
HAVING COUNT(o.order_id) >= :minOrders:Int?
ORDER BY total_spent DESC
