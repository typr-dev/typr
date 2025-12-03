-- Advanced parameter syntax example
SELECT p.*, a.city, e.salary
FROM frontpage.person p
JOIN frontpage.address a ON p.address_id = a.id
LEFT JOIN frontpage.employee e ON p.id = e.person_id
WHERE p.id = :person_id!
  AND p.created_at >= :since!
  AND a.country = :country:String?
  AND (:max_salary? IS NULL OR e.salary <= :max_salary)