with 
productionproduct0 as (
  (select productionproduct0 from production.product productionproduct0 )
), productionproductmodel0 as (
  (select productionproductmodel0 from production.productmodel productionproductmodel0 )
), join_cte2 as (
  select productionproduct0, productionproductmodel0
  from productionproduct0
  join productionproductmodel0
  on ((productionproduct0)."productmodelid"  = (productionproductmodel0)."productmodelid" )
), productionproductsubcategory0 as (
  (select productionproductsubcategory0 from production.productsubcategory productionproductsubcategory0 )
), join_cte1 as (
  select productionproduct0, productionproductmodel0, productionproductsubcategory0
  from join_cte2
  join productionproductsubcategory0
  on ((productionproduct0)."productsubcategoryid"  = (productionproductsubcategory0)."productsubcategoryid" )
), productionproductcategory0 as (
  (select productionproductcategory0 from production.productcategory productionproductcategory0 )
), join_cte0 as (
  select productionproduct0, productionproductmodel0, productionproductsubcategory0, productionproductcategory0
  from join_cte1
  join productionproductcategory0
  on ((productionproductsubcategory0)."productcategoryid"  = (productionproductcategory0)."productcategoryid" )
)
select (productionproduct0)."productid",(productionproduct0)."name",(productionproduct0)."productnumber",(productionproduct0)."makeflag",(productionproduct0)."finishedgoodsflag",(productionproduct0)."color",(productionproduct0)."safetystocklevel",(productionproduct0)."reorderpoint",(productionproduct0)."standardcost",(productionproduct0)."listprice",(productionproduct0)."size",(productionproduct0)."sizeunitmeasurecode",(productionproduct0)."weightunitmeasurecode",(productionproduct0)."weight",(productionproduct0)."daystomanufacture",(productionproduct0)."productline",(productionproduct0)."class",(productionproduct0)."style",(productionproduct0)."productsubcategoryid",(productionproduct0)."productmodelid",(productionproduct0)."sellstartdate"::text,(productionproduct0)."sellenddate"::text,(productionproduct0)."discontinueddate"::text,(productionproduct0)."rowguid",(productionproduct0)."modifieddate"::text,(productionproductmodel0)."productmodelid",(productionproductmodel0)."name",(productionproductmodel0)."catalogdescription",(productionproductmodel0)."instructions",(productionproductmodel0)."rowguid",(productionproductmodel0)."modifieddate"::text,(productionproductsubcategory0)."productsubcategoryid",(productionproductsubcategory0)."productcategoryid",(productionproductsubcategory0)."name",(productionproductsubcategory0)."rowguid",(productionproductsubcategory0)."modifieddate"::text,(productionproductcategory0)."productcategoryid",(productionproductcategory0)."name",(productionproductcategory0)."rowguid",(productionproductcategory0)."modifieddate"::text from join_cte0