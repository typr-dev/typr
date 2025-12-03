-- sql/user-analytics.sql
SELECT 
  u.name,
  u.email,
  COUNT(o.id) as order_count,
  SUM(o.total) as lifetime_value,
  MAX(o.created_at) as last_order_date
FROM frontpage.user u
LEFT JOIN frontpage.order o ON u.id = o.user_id
WHERE u.created_at >= :start_date:LocalDate!
  AND u.status = :status:frontpage.user_status?
GROUP BY u.id, u.name, u.email
HAVING SUM(o.total) > :min_value:BigDecimal!
ORDER BY lifetime_value DESC
LIMIT :limit:Int!