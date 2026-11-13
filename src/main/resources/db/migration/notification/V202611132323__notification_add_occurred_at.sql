ALTER TABLE notification.notification ADD COLUMN occurred_at timestamptz;
UPDATE notification.notification SET occurred_at = created_at;
ALTER TABLE notification.notification ALTER COLUMN occurred_at SET NOT NULL;
ALTER TABLE notification.notification DROP CONSTRAINT ck_notification_status;
ALTER TABLE notification.notification ADD CONSTRAINT ck_notification_status CHECK (status IN ('PENDING','SENT','FAILED','EXPIRED'));
