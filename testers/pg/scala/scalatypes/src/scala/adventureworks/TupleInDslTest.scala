package adventureworks

import adventureworks.humanresources.department.*
import adventureworks.public.Name
import dev.typr.dslsc.{TupleExpr2, TupleExpr3, Tuples}
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test

/** Tests for tuple IN operations using the DSL.
  *   - tupleWith().in(): Check if a tuple is in a list of values
  *   - tupleWith().in(subquery): Check if a tuple is in a subquery result
  */
class TupleInDslTest extends SnapshotTest {

  @Test
  def testTupleInWithTwoColumns(): Unit = {
    WithConnection {
      val deptRepo = DepartmentRepoImpl()

      // Create test departments
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("Engineering"), Name("Research and Development")))
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("Sales"), Name("Sales and Marketing")))
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("HR"), Name("Executive General and Administration")))

      // Query using tupleWith().in() with (name, groupname) pairs
      val query = deptRepo.select.where { d =>
        d.name
          .tupleWith(d.groupname)
          .in(
            List(
              Tuples.of(Name("Engineering"), Name("Research and Development")),
              Tuples.of(Name("HR"), Name("Executive General and Administration"))
            )
          )
      }

      val result = query.toList
      compareFragment("tupleIn2", query.sql())

      assertEquals(2, result.size)
      val resultNames = result.map(_.name).toSet
      assertEquals(Set(Name("Engineering"), Name("HR")), resultNames)
    }
  }

  @Test
  def testTupleInWithEmptyList(): Unit = {
    WithConnection {
      val deptRepo = DepartmentRepoImpl()

      // Create test department
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("Test"), Name("Test Group")))

      // Query with empty list should return no results
      val result = deptRepo.select.where { d =>
        d.name.tupleWith(d.groupname).in(List.empty)
      }.toList

      assertEquals(0, result.size)
    }
  }

  @Test
  def testTupleInSubqueryWithTwoColumns(): Unit = {
    WithConnection {
      val deptRepo = DepartmentRepoImpl()

      // Create departments
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("Active Dept 1"), Name("Group A")))
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("Active Dept 2"), Name("Group B")))
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("Inactive Dept"), Name("Group C")))

      // Test using tupleWith().in(subquery) with 2 columns
      // Find departments where (name, groupname) is in the subquery result
      import TupleExpr2.bijection
      val query = deptRepo.select.where { d =>
        d.name
          .tupleWith(d.groupname)
          .in(
            deptRepo.select
              .where(inner => inner.groupname.isEqual(Name("Group A")))
              .map(inner => inner.name.tupleWith(inner.groupname))
              .subquery
          )
      }

      val result = query.toList
      compareFragment("tupleInSubquery2", query.sql())

      assertEquals(1, result.size)
      assertEquals(Name("Active Dept 1"), result.head.name)
    }
  }

  @Test
  def testTupleInSubqueryCombinedWithOtherConditions(): Unit = {
    WithConnection {
      val deptRepo = DepartmentRepoImpl()

      // Create departments
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("Dept A"), Name("Group X")))
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("Dept B"), Name("Group X")))
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("Dept C"), Name("Group Y")))

      // Query combining tupleWith().in(subquery) with other conditions
      import TupleExpr2.bijection
      val query = deptRepo.select.where { d =>
        d.name
          .tupleWith(d.groupname)
          .in(
            deptRepo.select
              .where(inner => inner.groupname.isEqual(Name("Group X")))
              .map(inner => inner.name.tupleWith(inner.groupname))
              .subquery
          )
          .and(d.name.isNotEqual(Name("Dept A")))
      }

      val result = query.toList
      compareFragment("tupleInSubqueryCombined", query.sql())

      assertEquals(1, result.size)
      assertEquals(Name("Dept B"), result.head.name)
    }
  }

  @Test
  def testTupleInWithThreeColumns(): Unit = {
    WithConnection {
      val deptRepo = DepartmentRepoImpl()

      // Create test departments
      val dept1 = deptRepo.insert(DepartmentRowUnsaved(Name("Eng"), Name("R&D")))
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("Sales"), Name("Marketing")))

      // Query using tupleWith() with 3 columns (name, groupname, departmentid)
      val query = deptRepo.select.where { d =>
        d.name
          .tupleWith(d.groupname, d.departmentid)
          .in(
            List(
              Tuples.of(dept1.name, dept1.groupname, dept1.departmentid)
            )
          )
      }

      val result = query.toList
      compareFragment("tupleIn3", query.sql())

      assertEquals(1, result.size)
      assertEquals(dept1.departmentid, result.head.departmentid)
    }
  }

  @Test
  def testTupleInSubqueryWithThreeColumns(): Unit = {
    WithConnection {
      val deptRepo = DepartmentRepoImpl()

      // Create departments
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("Dept1"), Name("GroupA")))
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("Dept2"), Name("GroupA")))
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("Dept3"), Name("GroupB")))

      // Query using tupleWith().in(subquery) with 3 columns
      import TupleExpr3.bijection
      val query = deptRepo.select.where { d =>
        d.departmentid
          .tupleWith(d.name, d.groupname)
          .in(
            deptRepo.select
              .where(inner => inner.groupname.isEqual(Name("GroupA")))
              .map(inner => inner.departmentid.tupleWith(inner.name, inner.groupname))
              .subquery
          )
      }

      val result = query.toList
      compareFragment("tupleInSubquery3", query.sql())

      assertEquals(2, result.size)
      val resultNames = result.map(_.name).toSet
      assertEquals(Set(Name("Dept1"), Name("Dept2")), resultNames)
    }
  }

  // ==================== Nested Tuple Tests ====================

  @Test
  @Ignore("Needs refactoring before it works")
  def testNestedTupleIn(): Unit = {
    WithConnection {
      val deptRepo = DepartmentRepoImpl()

      // Create test departments
      val dept1 = deptRepo.insert(DepartmentRowUnsaved(Name("Nest1"), Name("GroupN1")))
      val dept2 = deptRepo.insert(DepartmentRowUnsaved(Name("Nest2"), Name("GroupN2")))
      val dept3 = deptRepo.insert(DepartmentRowUnsaved(Name("Nest3"), Name("GroupN3")))

      // Test truly nested tuple: ((name, groupname), departmentid)
      // This calls tupleWith twice to create Tuple2[Tuple2[Name, Name], DepartmentId]
      val query = deptRepo.select.where { d =>
        d.name
          .tupleWith(d.groupname)
          .tupleWith(d.departmentid)
          .in(
            List(
              Tuples.of(Tuples.of(dept1.name, dept1.groupname), dept1.departmentid),
              Tuples.of(Tuples.of(dept3.name, dept3.groupname), dept3.departmentid)
            )
          )
      }

      val result = query.toList
      compareFragment("nestedTupleIn", query.sql())

      assertEquals("Should find 2 departments matching nested tuple pattern", 2, result.size)

      // Test that non-matching nested tuple returns empty
      val queryNoMatch = deptRepo.select.where { d =>
        d.name
          .tupleWith(d.groupname)
          .tupleWith(d.departmentid)
          .in(
            List(
              Tuples.of(Tuples.of(dept1.name, dept1.groupname), dept2.departmentid) // Wrong: id doesn't match
            )
          )
      }

      val resultNoMatch = queryNoMatch.toList
      assertEquals("Should not match misaligned nested tuple", 0, resultNoMatch.size)
    }
  }

  @Test
  def testNestedTupleInSubquery(): Unit = {
    WithConnection {
      val deptRepo = DepartmentRepoImpl()

      // Create departments
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("SubNest1"), Name("SubGroupN")))
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("SubNest2"), Name("SubGroupN")))
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("SubNest3"), Name("OtherGroup")))

      // Query using nested tuple with subquery
      import TupleExpr2.bijection
      val query = deptRepo.select.where { d =>
        d.name
          .tupleWith(d.groupname)
          .tupleWith(d.departmentid)
          .in(
            deptRepo.select
              .where(inner => inner.groupname.isEqual(Name("SubGroupN")))
              .map(inner => inner.name.tupleWith(inner.groupname).tupleWith(inner.departmentid))
              .subquery
          )
      }

      val result = query.toList
      compareFragment("nestedTupleInSubquery", query.sql())

      assertEquals(2, result.size)
      val resultNames = result.map(_.name).toSet
      assertEquals(Set(Name("SubNest1"), Name("SubNest2")), resultNames)
    }
  }

  // ==================== Read Nested Tuple from Database Tests ====================

  @Test
  def testReadNestedTupleFromDatabase(): Unit = {
    WithConnection {
      val deptRepo = DepartmentRepoImpl()

      // Insert test data
      val dept1 = deptRepo.insert(DepartmentRowUnsaved(Name("ReadNest1"), Name("ReadGroup1")))
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("ReadNest2"), Name("ReadGroup2")))
      val _ = deptRepo.insert(DepartmentRowUnsaved(Name("ReadNest3"), Name("ReadGroup3")))

      // Select nested tuple: ((name, groupname), departmentid)
      val query = deptRepo.select
        .where(d => d.name.in(Name("ReadNest1"), Name("ReadNest2"), Name("ReadNest3")))
        .orderBy(d => d.departmentid.asc)
        .map(d => d.name.tupleWith(d.groupname).tupleWith(d.departmentid))

      val result = query.toList
      compareFragment("readNestedTuple", query.sql())

      assertEquals("Should read 3 nested tuples", 3, result.size)

      // Verify the nested tuple structure
      val first = result.head
      assertEquals("First tuple's inner first element", Name("ReadNest1"), first._1._1)
      assertEquals("First tuple's inner second element", Name("ReadGroup1"), first._1._2)
      assertEquals("First tuple's outer second element", dept1.departmentid, first._2)
    }
  }
}
