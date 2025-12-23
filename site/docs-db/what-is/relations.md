---
title: Generated code for relations
---

Typo takes the chore out of writing repository code to access your PostgreSQL database relations by automatically
generating it for you. Whether you're dealing with tables or views, Typo's generated code simplifies database
interaction, saving you time and effort. 

It incorporates crucial database information such as strongly typed primary key types, comments, check constraints, and foreign keys. 
This not only simplifies your code but also empowers developers with a deep understanding of the database structure. 

With Typo, you can spend more time focusing on your application logic and less time on repetitive database access code, all while
having the tools to perform CRUD operations efficiently. 


## Tables

For tables, Typo generates comprehensive repository code. Take for instance this table: 

```sql
create table address
(
    addressid       serial
        constraint "PK_Address_AddressID" primary key,
    addressline1    varchar(60)                          not null,
    addressline2    varchar(60),
    city            varchar(30)                          not null,
    stateprovinceid integer                              not null
        constraint "FK_Address_StateProvince_StateProvinceID" references stateprovince,
    postalcode      varchar(15)                          not null,
    spatiallocation bytea,
    rowguid         uuid      default uuid_generate_v1() not null,
    modifieddate    timestamp default now()              not null
);

comment on table address is 'Street address information for customers, employees, and vendors.';
comment on column address.addressid is 'Primary key for Address records.';
comment on column address.addressline1 is 'First street address line.';
comment on column address.addressline2 is 'Second street address line.';
comment on column address.city is 'Name of the city.';
comment on column address.stateprovinceid is 'Unique identification number for the state or province. Foreign key to StateProvince table.';
comment on column address.postalcode is 'Postal code for the street address.';
comment on column address.spatiallocation is 'Latitude and longitude of this address.';
```

### Primary Key Types

Typo generates strongly typed [primary key types](../type-safety/id-types.md), ensuring correct usage and enforcement of data integrity. 
You also get types for composite primary keys.

```scala
/** Type for the primary key of table `person.address` */
case class AddressId(value: Int) extends AnyVal
object AddressId {
  // ...instances
}
```

### Row Class

You'll receive a meticulously crafted row case class that precisely mirrors your table structure. 
The field names are beautified (see [Customize naming](../customization/customize-naming.md) for how to tweak naming), 
and the corresponding types are correct.

Relevant column comments, check constraints and foreign keys are clearly marked, 
making it easy to understand the purpose of every column.

```scala
import adventureworks.customtypes.*
import adventureworks.person.address.AddressId
import adventureworks.person.stateprovince.StateprovinceId
import java.util.UUID

/** Table: person.address
    Street address information for customers, employees, and vendors.
    Primary key: addressid */
case class AddressRow(
  /** Primary key for Address records.
      Default: nextval('person.address_addressid_seq'::regclass) */
  addressid: AddressId,
  /** First street address line. */
  addressline1: /* max 60 chars */ String,
  /** Second street address line. */
  addressline2: Option[/* max 60 chars */ String],
  /** Name of the city. */
  city: /* max 30 chars */ String,
  /** Unique identification number for the state or province. Foreign key to StateProvince table.
      Points to [[stateprovince.StateprovinceRow.stateprovinceid]] */
  stateprovinceid: StateprovinceId,
  /** Postal code for the street address. */
  postalcode: /* max 15 chars */ String,
  /** Latitude and longitude of this address. */
  spatiallocation: Option[TypoBytea],
  /** Default: uuid_generate_v1() */
  rowguid: TypoUUID,
  /** Default: now() */
  modifieddate: TypoLocalDateTime
)
```

### Repository interface

Typo generates a repository interface tailored to your table, providing methods efficient CRUD (Create, Read, Update,
Delete) operations at your fingertips.

```scala
import adventureworks.person.address.{AddressFields, AddressRow}
import java.sql.Connection
import typr.dsl.{DeleteBuilder, SelectBuilder, UpdateBuilder}

trait AddressRepo {
  def delete: DeleteBuilder[AddressFields, AddressRow]
  def deleteById(addressid: AddressId)(implicit c: Connection): Boolean
  def deleteByIds(addressids: Array[AddressId])(implicit c: Connection): Int
  def insert(unsaved: AddressRow)(implicit c: Connection): AddressRow
  def insert(unsaved: AddressRowUnsaved)(implicit c: Connection): AddressRow
  def insertStreaming(unsaved: Iterator[AddressRow], batchSize: Int)(implicit c: Connection): Long
  /* NOTE: this functionality requires PostgreSQL 16 or later! */
  def insertUnsavedStreaming(unsaved: Iterator[AddressRowUnsaved], batchSize: Int)(implicit c: Connection): Long
  def select: SelectBuilder[AddressFields, AddressRow]
  def selectAll(implicit c: Connection): List[AddressRow]
  def selectById(addressid: AddressId)(implicit c: Connection): Option[AddressRow]
  def selectByIds(addressids: Array[AddressId])(implicit c: Connection): List[AddressRow]
  def selectByIdsTracked(addressids: Array[AddressId])(implicit c: Connection): Map[AddressId, Option[AddressRow]]
  def update: UpdateBuilder[AddressFields, AddressRow]
  def update(row: AddressRow)(implicit c: Connection): Boolean
  def upsert(unsaved: AddressRow)(implicit c: Connection): AddressRow
}
```

### Simplified Insertion

Since Typo understands auto-increment IDs and default values, you can effortlessly insert new rows
without the need for complex code. A special structure is provided for creating unsaved rows with default values.

```scala
import adventureworks.customtypes.Defaulted


/** This class corresponds to a row in table `person.address` which has not been persisted yet */
case class AddressRowUnsaved(
  /** First street address line. */
  addressline1: /* max 60 chars */ String,
  /** Second street address line. */
  addressline2: Option[/* max 60 chars */ String],
  /** Name of the city. */
  city: /* max 30 chars */ String,
  /** Unique identification number for the state or province. Foreign key to StateProvince table.
      Points to [[stateprovince.StateprovinceRow.stateprovinceid]] */
  stateprovinceid: StateprovinceId,
  /** Postal code for the street address. */
  postalcode: /* max 15 chars */ String,
  /** Latitude and longitude of this address. */
  spatiallocation: Option[TypoBytea],
  /** Default: nextval('person.address_addressid_seq'::regclass)
      Primary key for Address records. */
  addressid: Defaulted[AddressId] = Defaulted.UseDefault(),
  /** Default: uuid_generate_v1() */
  rowguid: Defaulted[TypoUUID] = Defaulted.UseDefault(),
  /** Default: now() */
  modifieddate: Defaulted[TypoLocalDateTime] = Defaulted.UseDefault()
)
```

### Readonly repositories

If you have a bunch of tables you just want to read, you can [customize](../customization/overview.md) 
the repositories to only expose read methods.

## Views

Typo also excels at simplifying code generation for views. 
While less code is generated for views, it's still designed to make your life easier

So given the following view:

```sql
create view vemployee
            (businessentityid, title, firstname, middlename, lastname, suffix, jobtitle, phonenumber, phonenumbertype,
             emailaddress, emailpromotion, addressline1, addressline2, city, stateprovincename, postalcode,
             countryregionname, additionalcontactinfo)
as
SELECT e.businessentityid,
       p.title,
       p.firstname,
       p.middlename,
       p.lastname,
       p.suffix,
       e.jobtitle,
       pp.phonenumber,
       pnt.name AS phonenumbertype,
       ea.emailaddress,
       p.emailpromotion,
       a.addressline1,
       a.addressline2,
       a.city,
       sp.name  AS stateprovincename,
       a.postalcode,
       cr.name  AS countryregionname,
       p.additionalcontactinfo
FROM humanresources.employee e
         JOIN person.person p ON p.businessentityid = e.businessentityid
         JOIN person.businessentityaddress bea ON bea.businessentityid = e.businessentityid
         JOIN person.address a ON a.addressid = bea.addressid
         JOIN person.stateprovince sp ON sp.stateprovinceid = a.stateprovinceid
         JOIN person.countryregion cr ON cr.countryregioncode::text = sp.countryregioncode::text
         LEFT JOIN person.personphone pp ON pp.businessentityid = p.businessentityid
         LEFT JOIN person.phonenumbertype pnt ON pp.phonenumbertypeid = pnt.phonenumbertypeid
         LEFT JOIN person.emailaddress ea ON p.businessentityid = ea.businessentityid;

```

You get the following row type:

```scala
import adventureworks.customtypes.TypoXml
import adventureworks.person.businessentity.BusinessentityId
import adventureworks.public.Name
import adventureworks.public.Phone
import adventureworks.userdefined.FirstName

/** View: humanresources.vemployee */
case class VemployeeViewRow(
  /** Points to [[employee.EmployeeRow.businessentityid]] */
  businessentityid: BusinessentityId,
  /** Points to [[person.person.PersonRow.title]] */
  title: Option[/* max 8 chars */ String],
  /** Points to [[person.person.PersonRow.firstname]] */
  firstname: /* user-picked */ FirstName,
  /** Points to [[person.person.PersonRow.middlename]] */
  middlename: Option[Name],
  /** Points to [[person.person.PersonRow.lastname]] */
  lastname: Name,
  /** Points to [[person.person.PersonRow.suffix]] */
  suffix: Option[/* max 10 chars */ String],
  /** Points to [[employee.EmployeeRow.jobtitle]] */
  jobtitle: /* max 50 chars */ String,
  /** Points to [[person.personphone.PersonphoneRow.phonenumber]] */
  phonenumber: Option[Phone],
  /** Points to [[person.phonenumbertype.PhonenumbertypeRow.name]] */
  phonenumbertype: Option[Name],
  /** Points to [[person.emailaddress.EmailaddressRow.emailaddress]] */
  emailaddress: Option[/* max 50 chars */ String],
  /** Points to [[person.person.PersonRow.emailpromotion]] */
  emailpromotion: Int,
  /** Points to [[person.address.AddressRow.addressline1]] */
  addressline1: /* max 60 chars */ String,
  /** Points to [[person.address.AddressRow.addressline2]] */
  addressline2: Option[/* max 60 chars */ String],
  /** Points to [[person.address.AddressRow.city]] */
  city: /* max 30 chars */ String,
  /** Points to [[person.stateprovince.StateprovinceRow.name]] */
  stateprovincename: Name,
  /** Points to [[person.address.AddressRow.postalcode]] */
  postalcode: /* max 15 chars */ String,
  /** Points to [[person.countryregion.CountryregionRow.name]] */
  countryregionname: Name,
  /** Points to [[person.person.PersonRow.additionalcontactinfo]] */
  additionalcontactinfo: Option[TypoXml]
)
```

And this repository interface, focused on selecting rows from the view.

```scala
import adventureworks.humanresources.vemployee.{VemployeeViewFields, VemployeeViewRow}

trait VemployeeViewRepo {
  def select: SelectBuilder[VemployeeViewFields, VemployeeViewRow]
  def selectAll(implicit c: Connection): List[VemployeeViewRow]
}
```