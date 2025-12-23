-- Get orders with full customer details
SELECT
    o.order_id,
    o.order_date,
    o.total_amount,
    c.customer_id,
    c.name as customer_name,
    c.email as customer_email
FROM orders o
INNER JOIN customers c ON o.customer_id = c.customer_id
WHERE (:"min_amount:BigDecimal?" IS NULL OR o.total_amount >= :min_amount)
  AND (:"start_date:LocalDateTime?" IS NULL OR o.order_date >= :start_date)
ORDER BY o.order_date DESC
