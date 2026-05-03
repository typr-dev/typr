package showcase

import java.sql.Connection
import scala.util.Random
import showcase.showcase.EmailAddress
import showcase.showcase.address.AddressId
import showcase.showcase.company.CompanyId
import showcase.showcase.customer.CustomerId
import showcase.showcase.customer_order.CustomerOrderId
import showcase.showcase.department.DepartmentId
import showcase.showcase.employee.EmployeeId
import showcase.showcase.product.ProductId
import showcase.showcase.project.ProjectId

def testInsertExample(using c: Connection): Unit =
  val testInsert = TestInsert(new Random(0L), DomainInsertImpl)

  // Insert with just a name - everything else is random
  val company1 = testInsert.showcaseCompany(
    name = "Acme Corp"
  )
  // => CompanyRow(id=CCzLNHBFHuRvbI1iI19W, name=Acme Corp,
  //      foundedYear=None, active=Some(true), createdAt=Some(2026-01-04T12:00:00))

  // Another company - all values random
  val company2 = testInsert.showcaseCompany()
  // => CompanyRow(id=Hfyqts0coJXQqPyuxbr5, name=bFbQPNB7ZuKSWpBejTwv,
  //      foundedYear=Some(1926371), active=Some(true), createdAt=Some(2026-01-04T12:00:00))

  // Department with FK to company - budget is random
  val dept1 = testInsert.showcaseDepartment(
    companyId = company1.id,
    name = "Engineering"
  )
  // => DepartmentRow(id=1NdpH7sjZu9W3pBe, companyId=CCzLNHBFHuRvbI1iI19W,
  //      name=Engineering, budget=Some(8472.19))

  // Another department - all values random except FK
  val dept2 = testInsert.showcaseDepartment(companyId = company1.id)
  // => DepartmentRow(id=jTwvKSWpQqPyuxbr, companyId=CCzLNHBFHuRvbI1iI19W,
  //      name=xQqPyuxbr5Hfyqts, budget=Some(3891.42))

  // Employee with FK to department - domains auto-generated
  val employee = testInsert.showcaseEmployee(
    departmentId = dept1.id,
    firstName = "John",
    lastName = "Doe"
  )
  // => EmployeeRow(id=9W3pBeKSWpQqPyux, departmentId=1NdpH7sjZu9W3pBe,
  //      email=EmailAddress(xK7mN2pQ@example.com), firstName=John, lastName=Doe,
  //      salary=Some(PositiveAmount(4721.83)), phone=Some(PhoneNumber(+1-555-847-2938)), ...)
