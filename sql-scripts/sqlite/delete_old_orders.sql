-- Delete completed orders older than a cutoff date.
-- Tests: DELETE statement with required date param, no result columns.
--
-- SQLite stores DATE as ISO-8601 TEXT, so sqlglot can't infer that
-- order_date < :cutoff_date wants a LocalDate. We pin the type
-- explicitly with the `:name:Type!` annotation — same opt-in pattern
-- as the other dialects use for parameters not otherwise resolvable
-- from schema lineage.
DELETE FROM orders
WHERE status = 'completed' AND order_date < :"cutoff_date:LocalDate!";
