with 
personemailaddress0 as (
  (select personemailaddress0 from person.emailaddress personemailaddress0 where ((personemailaddress0)."businessentityid" , (personemailaddress0)."emailaddressid" ) IN ((?::int4, ?::int4), (?::int4, ?::int4)))
)
select (personemailaddress0)."businessentityid",(personemailaddress0)."emailaddressid",(personemailaddress0)."emailaddress",(personemailaddress0)."rowguid",(personemailaddress0)."modifieddate"::text from personemailaddress0