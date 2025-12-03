-- Get order with all items
SELECT o.order_id,
       o.order_number,
       o.order_status,
       o.payment_status,
       o.subtotal,
       o.shipping_cost,
       o.tax_amount,
       o.discount_amount,
       o.total_amount,
       o.ordered_at,
       oi.item_id,
       oi.product_id,
       oi.sku,
       oi.product_name,
       oi.quantity,
       oi.unit_price,
       oi.line_total
FROM orders o
JOIN order_items oi ON o.order_id = oi.order_id
WHERE o.order_id = :"order_id:testdb.orders.OrdersId!"
