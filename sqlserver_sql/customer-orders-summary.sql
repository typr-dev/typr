-- Get order summary statistics for customers
SELECT
    c.customer_id,
    c.name as customer_name,
    c.email as customer_email,
    COUNT(o.order_id) as order_count,
    COALESCE(SUM(o.total_amount), 0) as total_spent,
    COALESCE(AVG(o.total_amount), 0) as avg_order_amount,
    MAX(o.order_date) as last_order_date
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
WHERE (:"customer_name_pattern:String?" IS NULL OR c.name LIKE :"customer_name_pattern:String?")
GROUP BY c.customer_id, c.name, c.email
HAVING COUNT(o.order_id) > 0
  AND (:"min_total:BigDecimal?" IS NULL OR COALESCE(SUM(o.total_amount), 0) >= :"min_total:BigDecimal?")
ORDER BY total_spent DESC
