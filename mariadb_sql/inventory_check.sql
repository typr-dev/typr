-- Check inventory levels across warehouses
SELECT i.inventory_id,
       p.product_id,
       p.sku,
       p.name AS product_name,
       w.warehouse_id,
       w.code AS warehouse_code,
       w.name AS warehouse_name,
       i.quantity_on_hand,
       i.quantity_reserved,
       (i.quantity_on_hand - i.quantity_reserved) AS available,
       i.reorder_point,
       i.bin_location
FROM inventory i
JOIN products p ON i.product_id = p.product_id
JOIN warehouses w ON i.warehouse_id = w.warehouse_id
WHERE (:"warehouse_id?" IS NULL OR i.warehouse_id = :warehouse_id)
  AND (:"product_id?" IS NULL OR i.product_id = :product_id)
  AND (:"low_stock_only:java.lang.Boolean?" IS NULL OR (i.quantity_on_hand - i.quantity_reserved) <= i.reorder_point)
