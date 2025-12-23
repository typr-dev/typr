---
title: Testing with stubs
---

It can be incredibly tiring to write tests for the database layer.

Often you want to split you code in pure/effectful code and just test the pure parts,
but sometimes you want to observe mutations in the database as well.

Sometimes spinning up a real database for this is the right answer, sometimes it's not.
It is always slow, however, so it's way easier to get a fast test suite if you're not doing it.

The argument for the approach taken by Typo is that since the interaction between Scala
and PostgreSQL is guaranteed to be correct*, it is less important to back your tests with a real database.

This leads us to stubs (called mocks in the generated code), implementations of the repository
interfaces backed by a mutable `Map`. This can be generated for all tables with a primary key.

## DSL

Notable, these mocks work with the [dsl](../what-is/dsl.md), which lets you describe semi-complex joins, updates, where predicates,
string operations and so on in your code, and test it in-memory!

### *Note

Typo guarantees schema correctness, but you can still break constraints.
Or your tests need more advanced PostgreSQL functionality.

Stubs are obviously not a full replacement, but if they can be used for some non-zero percentage
of your tests it's still very beneficial!

## An example of a generated `RepoMock`:

```scala
import adventureworks.person.address.*
import typr.dsl.*
import typr.dsl.DeleteBuilder.DeleteBuilderMock
import typr.dsl.UpdateBuilder.UpdateBuilderMock
import java.sql.Connection
import scala.annotation.nowarn

class AddressRepoMock(toRow: Function1[AddressRowUnsaved, AddressRow],
                      map: scala.collection.mutable.Map[AddressId, AddressRow] = scala.collection.mutable.Map.empty) extends AddressRepo {
  override def delete: DeleteBuilder[AddressFields, AddressRow] = {
    DeleteBuilderMock(DeleteParams.empty, AddressFields.structure, map)
  }
  override def deleteById(addressid: AddressId)(implicit c: Connection): Boolean = {
    map.remove(addressid).isDefined
  }
  override def deleteByIds(addressids: Array[AddressId])(implicit c: Connection): Int = {
    addressids.map(id => map.remove(id)).count(_.isDefined)
  }
  override def insert(unsaved: AddressRow)(implicit c: Connection): AddressRow = {
    val _ = if (map.contains(unsaved.addressid))
      sys.error(s"id ${unsaved.addressid} already exists")
    else
      map.put(unsaved.addressid, unsaved)

    unsaved
  }
  override def insert(unsaved: AddressRowUnsaved)(implicit c: Connection): AddressRow = {
    insert(toRow(unsaved))
  }
  override def insertStreaming(unsaved: Iterator[AddressRow], batchSize: Int = 10000)(implicit c: Connection): Long = {
    unsaved.foreach { row =>
      map += (row.addressid -> row)
    }
    unsaved.size.toLong
  }
  /* NOTE: this functionality requires PostgreSQL 16 or later! */
  override def insertUnsavedStreaming(unsaved: Iterator[AddressRowUnsaved], batchSize: Int = 10000)(implicit c: Connection): Long = {
    unsaved.foreach { unsavedRow =>
      val row = toRow(unsavedRow)
      map += (row.addressid -> row)
    }
    unsaved.size.toLong
  }
  override def select: SelectBuilder[AddressFields, AddressRow] = {
    SelectBuilderMock(AddressFields.structure, () => map.values.toList, SelectParams.empty)
  }
  override def selectAll(implicit c: Connection): List[AddressRow] = {
    map.values.toList
  }
  override def selectById(addressid: AddressId)(implicit c: Connection): Option[AddressRow] = {
    map.get(addressid)
  }
  override def selectByIds(addressids: Array[AddressId])(implicit c: Connection): List[AddressRow] = {
    addressids.flatMap(map.get).toList
  }
  override def selectByIdsTracked(addressids: Array[AddressId])(implicit c: Connection): Map[AddressId, AddressRow] = {
    val byId = selectByIds(addressids).view.map(x => (x.addressid, x)).toMap
    addressids.view.flatMap(id => byId.get(id).map(x => (id, x))).toMap
  }
  override def update: UpdateBuilder[AddressFields, AddressRow] = {
    UpdateBuilderMock(UpdateParams.empty, AddressFields.structure, map)
  }
  override def update(row: AddressRow)(implicit c: Connection): Option[AddressRow] = {
    map.get(row.addressid).map { _ =>
      map.put(row.addressid, row): @nowarn
      row
    }
  }
  override def upsert(unsaved: AddressRow)(implicit c: Connection): AddressRow = {
    map.put(unsaved.addressid, unsaved): @nowarn
    unsaved
  }
  override def upsertBatch(unsaved: Iterable[AddressRow])(implicit c: Connection): List[AddressRow] = {
    unsaved.map { row =>
      map += (row.addressid -> row)
      row
    }.toList
  }
  override def upsertStreaming(unsaved: Iterator[AddressRow], batchSize: Int = 10000)(implicit c: Connection): Int = {
    unsaved.foreach { row =>
      map += (row.addressid -> row)
    }
    unsaved.size
  }
}
```

