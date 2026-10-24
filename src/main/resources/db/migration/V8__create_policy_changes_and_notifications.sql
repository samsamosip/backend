-- 공고 변경 이력. 수집할 때 화면에 보이는 값(마감일·지원 내용·서류·자격 조건 등)이 바뀌면 바뀌기 전/후를 남긴다.
CREATE TABLE policy_changes (
    id           BIGSERIAL    PRIMARY KEY,
    policy_id    BIGINT       NOT NULL REFERENCES policies (id),
    kind         VARCHAR(20)  NOT NULL,   -- EXTENDED 마감 연장 / CORRECTED 정정 / CANCELLED 취소 / CLOSED 마감 / REMOVED 공고 내림 / RESTORED 다시 게시 / UPDATED 내용 변경
    changes      JSONB        NOT NULL,   -- [{"field", "label", "before", "after"}]
    detected_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_policy_changes_policy ON policy_changes (policy_id, detected_at DESC);

-- 알림함 (WF07). 준비함에 담은 공고가 바뀌면 담은 사람마다 하나씩 만든다. 사용자는 준비함과 같은 anonymous_id 로 구분한다.
CREATE TABLE notifications (
    id                BIGSERIAL  PRIMARY KEY,
    anonymous_id      UUID       NOT NULL,
    policy_id         BIGINT     NOT NULL REFERENCES policies (id),
    policy_change_id  BIGINT     NOT NULL REFERENCES policy_changes (id) ON DELETE CASCADE,
    created_at        TIMESTAMP  NOT NULL DEFAULT now(),
    read_at           TIMESTAMP,
    CONSTRAINT uk_notifications_owner_change UNIQUE (anonymous_id, policy_change_id)
);

CREATE INDEX idx_notifications_owner ON notifications (anonymous_id, id DESC);
