# PostgreSQL with PostGIS and pgvector extensions
ARG PG_MAJOR
FROM postgis/postgis:$PG_MAJOR-3.4

RUN apt-get update \
    && apt-get install -y postgresql-$PG_MAJOR-pgvector \
    && rm -rf /var/lib/apt/lists/*

