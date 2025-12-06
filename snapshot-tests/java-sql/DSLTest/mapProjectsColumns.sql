select personperson0."firstname" , personperson0."lastname" 
from (SELECT * FROM "person"."person" personperson0 WHERE ((personperson0)."businessentityid"  = ?::int4)) personperson0