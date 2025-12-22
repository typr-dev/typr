-- Initialize Oracle database for Typo testing
-- This runs as SYSTEM user during container startup

-- Grant necessary privileges to typo user
ALTER SESSION SET CONTAINER = FREEPDB1;

-- Grant additional privileges to typo user for object types
GRANT CREATE TYPE TO typo;
GRANT CREATE TABLE TO typo;
GRANT CREATE VIEW TO typo;
GRANT CREATE SEQUENCE TO typo;

-- Grant SELECT on data dictionary views needed for metadata introspection
GRANT SELECT ON ALL_TABLES TO typo;
GRANT SELECT ON ALL_TAB_COLUMNS TO typo;
GRANT SELECT ON ALL_CONSTRAINTS TO typo;
GRANT SELECT ON ALL_CONS_COLUMNS TO typo;
GRANT SELECT ON ALL_VIEWS TO typo;
GRANT SELECT ON ALL_TYPES TO typo;
GRANT SELECT ON ALL_TYPE_ATTRS TO typo;
GRANT SELECT ON ALL_COLL_TYPES TO typo;
GRANT SELECT ON ALL_TAB_COMMENTS TO typo;
GRANT SELECT ON ALL_COL_COMMENTS TO typo;
GRANT SELECT ON ALL_TAB_IDENTITY_COLS TO typo;

-- Grant unlimited tablespace for testing
GRANT UNLIMITED TABLESPACE TO typo;

COMMIT;
