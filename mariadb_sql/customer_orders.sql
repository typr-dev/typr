-- Query customers with their orders
SELECT c.customer_id,
       c.email,
       c.first_name,
       c.last_name,
       c.tier,
       o.order_id,
       o.order_number,
       o.order_status,
       o.total_amount,
       o.ordered_at
FROM customers c
LEFT JOIN orders o ON c.customer_id = o.customer_id
WHERE c.customer_id = :"customer_id:testdb.customers.CustomersId!"
  AND (:"order_status?" IS NULL OR o.order_status = :order_status)
