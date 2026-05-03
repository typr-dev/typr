---
title: Constraints
---

Typr covers a lot of ground in that it should generate all types correctly.
However, to safely insert or update data into PostgreSQL, you also need to satisfy contraints.
Typr does a good job a helping out with foreign keys, but there are other types of constraints as well.

For now the constraints are picked up and output as field comments for the `Row` types.

```scala
import adventureworks.person.businessentity.BusinessentityId

case class PersonRow(
  /** Primary key for Person records.
      Points to [[businessentity.BusinessentityRow.businessentityid]] */
  businessentityid: BusinessentityId,
  /** Primary type of person: SC = Store Contact, IN = Individual (retail) customer, SP = Sales person, EM = Employee (non-sales), VC = Vendor contact, GC = General contact
      Constraint CK_Person_PersonType affecting columns "persontype":  (((persontype IS NULL) OR (upper((persontype)::text) = ANY (ARRAY['SC'::text, 'VC'::text, 'IN'::text, 'EM'::text, 'SP'::text, 'GC'::text])))) */
  /** 0 = Contact does not wish to receive e-mail promotions, 1 = Contact does wish to receive e-mail promotions from AdventureWorks, 2 = Contact does wish to receive e-mail promotions from AdventureWorks and selected partners.
      Constraint CK_Person_EmailPromotion affecting columns "emailpromotion":  (((emailpromotion >= 0) AND (emailpromotion <= 2))) */
  emailpromotion: Int,
)
```

In the future, we can generate types for check constraints such as these to force you to use them correctly.
it's not implemented yet, however.
