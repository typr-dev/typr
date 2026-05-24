-- Product details enriched with cumulative sales numbers.
-- Tests: subquery in SELECT, joining on FK, ordering by computed column.

SELECT
    p.product_id,
    p.sku,
    p.name AS product_name,
    p.price,
    COALESCE(SUM(oi.quantity), 0) AS total_units_sold,
    COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS total_revenue
FROM products p
LEFT JOIN order_items oi ON p.product_id = oi.product_id
WHERE (:"min_price?" IS NULL OR p.price >= :min_price)
GROUP BY p.product_id, p.sku, p.name, p.price
ORDER BY total_revenue DESC, p.product_id;
