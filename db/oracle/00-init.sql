-- Initialize Oracle database for Typo testing
-- This runs as SYSTEM user during container startup

-- Grant necessary privileges to typr user
ALTER SESSION SET CONTAINER = FREEPDB1;

-- Grant additional privileges to typr user for object types
GRANT CREATE TYPE TO typr;
GRANT CREATE TABLE TO typr;
GRANT CREATE VIEW TO typr;
GRANT CREATE SEQUENCE TO typr;

-- Grant SELECT on data dictionary views needed for metadata introspection
GRANT SELECT ON ALL_TABLES TO typr;
GRANT SELECT ON ALL_TAB_COLUMNS TO typr;
GRANT SELECT ON ALL_CONSTRAINTS TO typr;
GRANT SELECT ON ALL_CONS_COLUMNS TO typr;
GRANT SELECT ON ALL_VIEWS TO typr;
GRANT SELECT ON ALL_TYPES TO typr;
GRANT SELECT ON ALL_TYPE_ATTRS TO typr;
GRANT SELECT ON ALL_COLL_TYPES TO typr;
GRANT SELECT ON ALL_TAB_COMMENTS TO typr;
GRANT SELECT ON ALL_COL_COMMENTS TO typr;
GRANT SELECT ON ALL_TAB_IDENTITY_COLS TO typr;

-- Grant unlimited tablespace for testing
GRANT UNLIMITED TABLESPACE TO typr;

COMMIT;
