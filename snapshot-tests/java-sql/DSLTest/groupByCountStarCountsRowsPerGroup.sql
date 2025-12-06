select personperson0."persontype" , COUNT(*)
from (select * from "person"."person" personperson0 where ((personperson0)."lastname"  = ?::"public"."Name")) personperson0
where (personperson0."lastname"  = ?::"public"."Name")
group by personperson0."persontype" 