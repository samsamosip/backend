-- 알림 종류 추가: CHANGE(담은 공고 변경, 기존) / NEW_MATCHES(새 맞춤 공고 하루 1회 묶음) / DEADLINE(담은 공고 마감 임박).
-- 새 공고 묶음은 공고 하나에 매이지 않아서 policy_id·policy_change_id 를 비울 수 있게 하고, 내용은 payload 에 둔다.
ALTER TABLE notifications ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'CHANGE';
ALTER TABLE notifications ALTER COLUMN policy_id DROP NOT NULL;
ALTER TABLE notifications ALTER COLUMN policy_change_id DROP NOT NULL;
ALTER TABLE notifications ADD COLUMN payload JSONB;
-- 같은 알림을 두 번 만들지 않기 위한 키: "deadline:{공고}:{며칠 전}", "new-matches:{날짜}"
ALTER TABLE notifications ADD COLUMN dedupe_key VARCHAR(100);
CREATE UNIQUE INDEX uk_notifications_owner_dedupe ON notifications (anonymous_id, dedupe_key) WHERE dedupe_key IS NOT NULL;

-- 알림 수신 설정 (WF05-b). 행이 없으면 전부 켜진 기본값으로 본다.
CREATE TABLE notification_settings (
    anonymous_id        UUID         PRIMARY KEY,
    new_matches         BOOLEAN      NOT NULL DEFAULT TRUE,              -- 새 맞춤 공고 (하루 1회 묶음)
    new_match_verdicts  VARCHAR(50)  NOT NULL DEFAULT 'MATCH,NEEDS_CHECK', -- 어떤 판정까지 알릴지
    deadline            BOOLEAN      NOT NULL DEFAULT TRUE,              -- 준비함 공고 마감 임박
    deadline_days       VARCHAR(30)  NOT NULL DEFAULT '7,3,1,0',         -- 마감 며칠 전에 (0 = 당일)
    changes             BOOLEAN      NOT NULL DEFAULT TRUE,              -- 담은 공고 변경 (마감 연장·정정·취소)
    quiet_start         TIME,                                            -- 방해금지 시작 (앱 밖 발송이 생기면 쓴다)
    quiet_end           TIME,
    updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);
