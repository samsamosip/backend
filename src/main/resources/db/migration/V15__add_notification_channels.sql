-- 알림 받는 방법 (최종 디자인 05-b "받는 방법"): 앱 알림(앱 안 알림함), 이메일(선택 동의, 켜면 계정 이메일로 보냄)
ALTER TABLE notification_settings ADD COLUMN app_enabled   BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE notification_settings ADD COLUMN email_enabled BOOLEAN NOT NULL DEFAULT FALSE;
