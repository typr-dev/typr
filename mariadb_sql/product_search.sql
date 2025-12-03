-- Search products with optional filters
SELECT p.product_id,
       p.sku,
       p.name,
       p.short_description,
       p.base_price,
       p.status,
       b.name AS brand_name
FROM products p
LEFT JOIN brands b ON p.brand_id = b.brand_id
WHERE (:"brand_id?" IS NULL OR p.brand_id = :brand_id)
  AND (:"min_price?" IS NULL OR p.base_price >= :min_price)
  AND (:"max_price?" IS NULL OR p.base_price <= :max_price)
  AND (:"status?" IS NULL OR p.status = :status)
ORDER BY p.name
LIMIT :"limit!"
