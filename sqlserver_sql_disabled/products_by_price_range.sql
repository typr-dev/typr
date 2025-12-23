-- Find products within a price range
-- Tests: BETWEEN operator, MONEY type, optional parameters
SELECT
    product_id,
    name,
    price,
    description
FROM products
WHERE price BETWEEN :minPrice:java.math.BigDecimal? AND :maxPrice:java.math.BigDecimal?
ORDER BY price ASC
