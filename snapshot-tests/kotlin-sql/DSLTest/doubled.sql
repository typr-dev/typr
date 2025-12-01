with 
salessalesperson0 as (
  (select salessalesperson0 from sales.salesperson salessalesperson0 where ((salessalesperson0)."rowguid"  = ?::uuid))
), humanresourcesemployee0 as (
  (select humanresourcesemployee0 from humanresources.employee humanresourcesemployee0 )
), join_cte4 as (
  select salessalesperson0, humanresourcesemployee0
  from salessalesperson0
  join humanresourcesemployee0
  on ((salessalesperson0)."businessentityid"  = (humanresourcesemployee0)."businessentityid" )
), personperson0 as (
  (select personperson0 from person.person personperson0 )
), join_cte3 as (
  select salessalesperson0, humanresourcesemployee0, personperson0
  from join_cte4
  join personperson0
  on ((humanresourcesemployee0)."businessentityid"  = (personperson0)."businessentityid" )
), personbusinessentity0 as (
  (select personbusinessentity0 from person.businessentity personbusinessentity0 )
), join_cte2 as (
  select salessalesperson0, humanresourcesemployee0, personperson0, personbusinessentity0
  from join_cte3
  join personbusinessentity0
  on ((personperson0)."businessentityid"  = (personbusinessentity0)."businessentityid" )
), personemailaddress0 as (
  (select personemailaddress0 from person.emailaddress personemailaddress0 order by (personemailaddress0)."rowguid"  ASC)
), join_cte1 as (
  select salessalesperson0, humanresourcesemployee0, personperson0, personbusinessentity0, personemailaddress0
  from join_cte2
  join personemailaddress0
  on ((personemailaddress0)."businessentityid"  = (personbusinessentity0)."businessentityid" )
), salessalesperson1 as (
  (select salessalesperson1 from sales.salesperson salessalesperson1 where ((salessalesperson1)."rowguid"  = ?::uuid))
), humanresourcesemployee1 as (
  (select humanresourcesemployee1 from humanresources.employee humanresourcesemployee1 )
), join_cte8 as (
  select salessalesperson1, humanresourcesemployee1
  from salessalesperson1
  join humanresourcesemployee1
  on ((salessalesperson1)."businessentityid"  = (humanresourcesemployee1)."businessentityid" )
), personperson1 as (
  (select personperson1 from person.person personperson1 )
), join_cte7 as (
  select salessalesperson1, humanresourcesemployee1, personperson1
  from join_cte8
  join personperson1
  on ((humanresourcesemployee1)."businessentityid"  = (personperson1)."businessentityid" )
), personbusinessentity1 as (
  (select personbusinessentity1 from person.businessentity personbusinessentity1 )
), join_cte6 as (
  select salessalesperson1, humanresourcesemployee1, personperson1, personbusinessentity1
  from join_cte7
  join personbusinessentity1
  on ((personperson1)."businessentityid"  = (personbusinessentity1)."businessentityid" )
), personemailaddress1 as (
  (select personemailaddress1 from person.emailaddress personemailaddress1 order by (personemailaddress1)."rowguid"  ASC)
), join_cte5 as (
  select salessalesperson1, humanresourcesemployee1, personperson1, personbusinessentity1, personemailaddress1
  from join_cte6
  join personemailaddress1
  on ((personemailaddress1)."businessentityid"  = (personbusinessentity1)."businessentityid" )
), join_cte0 as (
  select salessalesperson0, humanresourcesemployee0, personperson0, personbusinessentity0, personemailaddress0, salessalesperson1, humanresourcesemployee1, personperson1, personbusinessentity1, personemailaddress1
  from join_cte1
  join join_cte5
  on ((personperson0)."businessentityid"  = (personperson1)."businessentityid" )
)
select (salessalesperson0)."businessentityid",(salessalesperson0)."territoryid",(salessalesperson0)."salesquota",(salessalesperson0)."bonus",(salessalesperson0)."commissionpct",(salessalesperson0)."salesytd",(salessalesperson0)."saleslastyear",(salessalesperson0)."rowguid",(salessalesperson0)."modifieddate"::text,(humanresourcesemployee0)."businessentityid",(humanresourcesemployee0)."nationalidnumber",(humanresourcesemployee0)."loginid",(humanresourcesemployee0)."jobtitle",(humanresourcesemployee0)."birthdate"::text,(humanresourcesemployee0)."maritalstatus",(humanresourcesemployee0)."gender",(humanresourcesemployee0)."hiredate"::text,(humanresourcesemployee0)."salariedflag",(humanresourcesemployee0)."vacationhours",(humanresourcesemployee0)."sickleavehours",(humanresourcesemployee0)."currentflag",(humanresourcesemployee0)."rowguid",(humanresourcesemployee0)."modifieddate"::text,(humanresourcesemployee0)."organizationnode",(personperson0)."businessentityid",(personperson0)."persontype",(personperson0)."namestyle",(personperson0)."title",(personperson0)."firstname",(personperson0)."middlename",(personperson0)."lastname",(personperson0)."suffix",(personperson0)."emailpromotion",(personperson0)."additionalcontactinfo",(personperson0)."demographics",(personperson0)."rowguid",(personperson0)."modifieddate"::text,(personbusinessentity0)."businessentityid",(personbusinessentity0)."rowguid",(personbusinessentity0)."modifieddate"::text,(personemailaddress0)."businessentityid",(personemailaddress0)."emailaddressid",(personemailaddress0)."emailaddress",(personemailaddress0)."rowguid",(personemailaddress0)."modifieddate"::text,(salessalesperson1)."businessentityid",(salessalesperson1)."territoryid",(salessalesperson1)."salesquota",(salessalesperson1)."bonus",(salessalesperson1)."commissionpct",(salessalesperson1)."salesytd",(salessalesperson1)."saleslastyear",(salessalesperson1)."rowguid",(salessalesperson1)."modifieddate"::text,(humanresourcesemployee1)."businessentityid",(humanresourcesemployee1)."nationalidnumber",(humanresourcesemployee1)."loginid",(humanresourcesemployee1)."jobtitle",(humanresourcesemployee1)."birthdate"::text,(humanresourcesemployee1)."maritalstatus",(humanresourcesemployee1)."gender",(humanresourcesemployee1)."hiredate"::text,(humanresourcesemployee1)."salariedflag",(humanresourcesemployee1)."vacationhours",(humanresourcesemployee1)."sickleavehours",(humanresourcesemployee1)."currentflag",(humanresourcesemployee1)."rowguid",(humanresourcesemployee1)."modifieddate"::text,(humanresourcesemployee1)."organizationnode",(personperson1)."businessentityid",(personperson1)."persontype",(personperson1)."namestyle",(personperson1)."title",(personperson1)."firstname",(personperson1)."middlename",(personperson1)."lastname",(personperson1)."suffix",(personperson1)."emailpromotion",(personperson1)."additionalcontactinfo",(personperson1)."demographics",(personperson1)."rowguid",(personperson1)."modifieddate"::text,(personbusinessentity1)."businessentityid",(personbusinessentity1)."rowguid",(personbusinessentity1)."modifieddate"::text,(personemailaddress1)."businessentityid",(personemailaddress1)."emailaddressid",(personemailaddress1)."emailaddress",(personemailaddress1)."rowguid",(personemailaddress1)."modifieddate"::text from join_cte0