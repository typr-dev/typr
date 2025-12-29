#!/bin/bash

export PGUSER=postgres
psql <<- SHELL
  CREATE USER docker;
  CREATE DATABASE "Adventureworks";
  GRANT ALL PRIVILEGES ON DATABASE "Adventureworks" TO docker;
SHELL

psql -d Adventureworks < /docker-entrypoint-initdb.d/install.sql
psql -d Adventureworks < /docker-entrypoint-initdb.d/test-tables.sql
psql -d Adventureworks < /docker-entrypoint-initdb.d/issue148.sql
psql -d Adventureworks < /docker-entrypoint-initdb.d/composite-types.sql
