-- Test CTE tracking
WITH customer_totals AS (
    SELECT
        o.customer_id,
        COUNT(*) as order_count,
        SUM(o.total_amount) as total_spent
    FROM orders o
    GROUP BY o.customer_id
),
top_customers AS (
    SELECT
        c.customer_id,
        c.email,
        c.first_name,
        ct.order_count,
        ct.total_spent
    FROM customers c
    INNER JOIN customer_totals ct ON c.customer_id = ct.customer_id
    WHERE ct.total_spent > 100
)
SELECT
    tc.customer_id,
    tc.email,
    tc.first_name,
    tc.order_count,
    tc.total_spent,
    b.name as favorite_brand
FROM top_customers tc
LEFT JOIN orders o ON tc.customer_id = o.customer_id
LEFT JOIN order_items oi ON o.order_id = oi.order_id
LEFT JOIN products p ON oi.product_id = p.product_id
LEFT JOIN brands b ON p.brand_id = b.brand_id
