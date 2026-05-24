-- Filter the all_scalar_types table by a handful of columns.
-- Tests: a parameter for each affinity (INTEGER, REAL, TEXT, DATE).

SELECT
    id,
    col_tinyint,
    col_smallint,
    col_integer,
    col_bigint,
    col_real,
    col_double,
    col_decimal,
    col_text,
    col_date,
    col_uuid,
    col_json
FROM all_scalar_types
WHERE
    (:"min_id?" IS NULL OR id >= :min_id)
    AND (:"text_pattern?" IS NULL OR col_text LIKE :text_pattern)
    AND (:"after_date?" IS NULL OR col_date >= :after_date)
ORDER BY id;
