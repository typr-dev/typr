---
title: Streaming inserts with COPY API
---

If you [customize](../customization/overview.md) Typr with `enableStreamingInserts = true`,
you can use the `insertStreaming` and `insertStreamingUnsaved` method on your repositories to insert a stream of data
into the database.

Under the covers this uses the [Copy API](https://www.postgresql.org/docs/current/sql-copy.html) in Postgres, which
is SUPER FAST compared to other ways of inserting data!

NOTE: `insertStreamingUnsaved` requires PostgreSQL 16 or later!

The signatures varies for each database library, because there are different stream representations

### anorm

```scala
def insertStreaming(unsaved: Iterator[PersonRow], batchSize: Int)(implicit c: Connection): Long
/* NOTE: this functionality requires PostgreSQL 16 or later! */
def insertUnsavedStreaming(unsaved: Iterator[PersonRowUnsaved], batchSize: Int)(implicit c: Connection): Long
```

### doobie

```scala
def insertStreaming(unsaved: Stream[ConnectionIO, PersonRow], batchSize: Int): ConnectionIO[Long]
/* NOTE: this functionality requires PostgreSQL 16 or later! */
def insertUnsavedStreaming(unsaved: Stream[ConnectionIO, PersonRowUnsaved], batchSize: Int): ConnectionIO[Long]
```

### zio

```scala
def insertStreaming(unsaved: ZStream[ZConnection, Throwable, PersonRow], batchSize: Int): ZIO[ZConnection, Throwable, Long]
/* NOTE: this functionality requires PostgreSQL 16 or later! */
def insertUnsavedStreaming(unsaved: ZStream[ZConnection, Throwable, PersonRowUnsaved], batchSize: Int): ZIO[ZConnection, Throwable, Long]
```
