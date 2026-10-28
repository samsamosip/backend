-- 알림 메일(하루 한 번 묶어서)로 보낸 시각. 보낸 알림은 다시 보내지 않는다.
ALTER TABLE notifications ADD COLUMN emailed_at TIMESTAMP;
