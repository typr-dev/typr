with 
productionproduct0 as (
  (select productionproduct0 from production.product productionproduct0 where ((((productionproduct0)."class"  = ?) AND (((productionproduct0)."daystomanufacture"  > ?::int4) OR ((productionproduct0)."daystomanufacture"  <= ?::int4))) AND ((productionproduct0)."productline"  = ?)))
), productionunitmeasure0 as (
  (select productionunitmeasure0 from production.unitmeasure productionunitmeasure0 where ((productionunitmeasure0)."name"  LIKE ?))
), join_cte0 as (
  select productionproduct0, productionunitmeasure0
  from productionproduct0
  join productionunitmeasure0
  on ((productionproduct0)."sizeunitmeasurecode"  = (productionunitmeasure0)."unitmeasurecode" )
), productionproductmodel0 as (
  (select productionproductmodel0 from production.productmodel productionproductmodel0 )
), left_join_cte0 as (
  select productionproduct0, productionunitmeasure0, productionproductmodel0
  from join_cte0
  left join productionproductmodel0
  on ((productionproduct0)."productmodelid"  = (productionproductmodel0)."productmodelid" )
  where ((productionproduct0)."productmodelid"  = (productionproductmodel0)."productmodelid" ) order by (productionproduct0)."productmodelid"  ASC, (productionproductmodel0)."name"  DESC NULLS FIRST
)
select (productionproduct0)."productid",(productionproduct0)."name",(productionproduct0)."productnumber",(productionproduct0)."makeflag",(productionproduct0)."finishedgoodsflag",(productionproduct0)."color",(productionproduct0)."safetystocklevel",(productionproduct0)."reorderpoint",(productionproduct0)."standardcost",(productionproduct0)."listprice",(productionproduct0)."size",(productionproduct0)."sizeunitmeasurecode",(productionproduct0)."weightunitmeasurecode",(productionproduct0)."weight",(productionproduct0)."daystomanufacture",(productionproduct0)."productline",(productionproduct0)."class",(productionproduct0)."style",(productionproduct0)."productsubcategoryid",(productionproduct0)."productmodelid",(productionproduct0)."sellstartdate"::text,(productionproduct0)."sellenddate"::text,(productionproduct0)."discontinueddate"::text,(productionproduct0)."rowguid",(productionproduct0)."modifieddate"::text,(productionunitmeasure0)."unitmeasurecode",(productionunitmeasure0)."name",(productionunitmeasure0)."modifieddate"::text,(productionproductmodel0)."productmodelid",(productionproductmodel0)."name",(productionproductmodel0)."catalogdescription",(productionproductmodel0)."instructions",(productionproductmodel0)."rowguid",(productionproductmodel0)."modifieddate"::text from left_join_cte0