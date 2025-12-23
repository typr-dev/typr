-- Product Summary View
-- Shows product details with aggregated information
-- This tests DuckDB SQL file processing and view generation

SELECT
  p.product_id,
  p.name as product_name!,
  p.sku,
  p.price,
  COUNT(oi.order_item_id) as order_count!,
  SUM(oi.quantity) as total_quantity!,
  SUM(oi.quantity * oi.unit_price) as total_revenue
FROM products p
LEFT JOIN order_items oi ON p.product_id = oi.product_id
GROUP BY p.product_id, p.name, p.sku, p.price
