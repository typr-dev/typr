-- Update order status
UPDATE orders
SET order_status = :"new_status:java.lang.String!",
    confirmed_at = CASE WHEN :new_status = 'confirmed' THEN NOW(6) ELSE confirmed_at END,
    shipped_at = CASE WHEN :new_status = 'shipped' THEN NOW(6) ELSE shipped_at END,
    delivered_at = CASE WHEN :new_status = 'delivered' THEN NOW(6) ELSE delivered_at END
WHERE order_id = :"order_id:testdb.orders.OrdersId!"
