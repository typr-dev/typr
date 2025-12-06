select personperson0."firstname" , personemailaddress0."emailaddress" 
from (SELECT * FROM "person"."person" personperson0 WHERE ((personperson0)."businessentityid"  = ?::int4)) personperson0
 join "person"."emailaddress" personemailaddress0 on (personperson0."businessentityid"  = personemailaddress0."businessentityid" )