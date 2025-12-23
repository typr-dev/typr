package typr

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.funsuite.AnyFunSuite
import typr.generated.custom.domains.DomainsSqlRepoImpl
import typr.generated.custom.view_find_all.ViewFindAllSqlRepoImpl
import typr.generated.information_schema

import java.sql.{Connection, DriverManager}

class DbTest extends AnyFunSuite with TypeCheckedTripleEquals {
  test("works") {
    given conn: Connection = DriverManager.getConnection(
      "jdbc:postgresql://localhost:6432/postgres?user=postgres&password=password"
    )

    println((new information_schema.columns.ColumnsViewRepoImpl).selectAll.take(1))
    println((new information_schema.key_column_usage.KeyColumnUsageViewRepoImpl).selectAll.take(1))
    println((new information_schema.referential_constraints.ReferentialConstraintsViewRepoImpl).selectAll.take(1))
    println((new information_schema.table_constraints.TableConstraintsViewRepoImpl).selectAll.take(1))
    println((new information_schema.tables.TablesViewRepoImpl).selectAll.take(1))
    println((new ViewFindAllSqlRepoImpl).apply.take(1))
    println((new DomainsSqlRepoImpl).apply.take(1))
  }
}
