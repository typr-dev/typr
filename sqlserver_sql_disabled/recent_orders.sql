-- Get most recent orders
-- Tests: TOP clause, ORDER BY, date filtering
SELECT TOP (:limit:Int!)
    o.order_id,
    o.customer_id,
    o.order_date,
    o.total_amount,
    c.name as customer_name!,
    c.email
FROM orders o
INNER JOIN customers c ON o.customer_id = c.customer_id
WHERE o.order_date >= :sinceDate:java.time.LocalDateTime?
ORDER BY o.order_date DESC
