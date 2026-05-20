-- Summary stats per customer over an order date range.
-- Tests: GROUP BY, aggregates with NULL-aware results, JOIN, date params.

SELECT
    c.customer_id,
    c.name AS customer_name,
    COUNT(o.order_id) AS order_count,
    COALESCE(SUM(o.total_amount), 0) AS total_revenue,
    MAX(o.order_date) AS last_order_date
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
WHERE o.order_date IS NULL
   OR (o.order_date >= :"from_date!" AND o.order_date <= :"to_date!")
GROUP BY c.customer_id, c.name
ORDER BY total_revenue DESC, c.customer_id;
