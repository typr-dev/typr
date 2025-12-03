-- sql/update-user-status.sql
UPDATE frontpage.user 
SET 
  status = :new_status:frontpage.user_status!,
  created_at = NOW() -- using created_at as modified_at since we don't have modified_at column
WHERE id = :user_id!
  AND status != :new_status
RETURNING 
  id,
  name,
  status,
  created_at as "modified_at:java.time.LocalDateTime!"