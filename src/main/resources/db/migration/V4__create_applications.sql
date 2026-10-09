-- 준비함 (WF05). 사용자가 관심 저장한 공고와 서류 체크리스트.
-- 사용자는 프로필과 같은 anonymous_id 로 구분한다(로그인 전 임시).
CREATE TABLE applications (
    id            BIGSERIAL    PRIMARY KEY,
    anonymous_id  UUID         NOT NULL,
    policy_id     BIGINT       NOT NULL REFERENCES policies (id),
    status        VARCHAR(20)  NOT NULL,      -- INTERESTED 관심 / PREPARING 준비 중 / APPLIED 신청 완료(본인 표시)
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_applications_owner_policy UNIQUE (anonymous_id, policy_id)
);

CREATE INDEX idx_applications_owner ON applications (anonymous_id);

CREATE TABLE checklist_items (
    id              BIGSERIAL    PRIMARY KEY,
    application_id  BIGINT       NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    content         VARCHAR(300) NOT NULL,
    optional        BOOLEAN      NOT NULL DEFAULT FALSE,  -- [선택], (해당자만) 서류
    source          VARCHAR(10)  NOT NULL,                -- POLICY 공고에서 자동 / USER 사용자가 추가
    checked         BOOLEAN      NOT NULL DEFAULT FALSE,
    position        INTEGER      NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_checklist_items_application ON checklist_items (application_id);
