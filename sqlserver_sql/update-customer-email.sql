-- Update customer email
UPDATE customers
SET email = :"new_email:String!"
WHERE customer_id = :"customer_id:Int!"
