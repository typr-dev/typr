package adventureworks

import adventureworks.humanresources.department.*
import adventureworks.public.Name
import dev.typr.foundations.scala.Tuples
import org.junit.Assert.*
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
            java.util.List.of(
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
        d.name.tupleWith(d.groupname).in(java.util.List.of())
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
      val query = deptRepo.select.where { d =>
        d.name
          .tupleWith(d.groupname)
          .in(
            deptRepo.select
              .where(inner => inner.groupname.isEqual(Name("Group A")))
              .map(inner => Tuples.of(inner.name, inner.groupname))
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
            java.util.List.of(
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
}
