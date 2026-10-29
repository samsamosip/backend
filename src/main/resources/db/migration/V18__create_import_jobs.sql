-- 링크 불러오기 (WF07 2차): 사용자가 준 공고 링크를 불러오기 서버(크롤러, AI 팀원)에 맡긴 작업.
-- id 는 불러오기 서버에 보내는 requestId 그대로. 결과는 공고 직접 등록 입력칸 모양으로 저장하고, 사용자가 확인한 뒤 등록한다.
CREATE TABLE import_jobs (
    id             UUID PRIMARY KEY,
    owner_id       UUID        NOT NULL,
    type           VARCHAR(10) NOT NULL,   -- URL (캡처 IMAGE 는 담당 정해지면)
    source_url     TEXT,
    status         VARCHAR(10) NOT NULL,   -- QUEUED RUNNING SUCCEEDED FAILED
    title          TEXT,
    organization   TEXT,
    apply_end_date DATE,
    apply_end_time TIME,
    category       VARCHAR(20),
    memo           TEXT,
    apply_url      TEXT,
    error_code     VARCHAR(30),
    error_message  TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    polled_at      TIMESTAMP WITH TIME ZONE,   -- 불러오기 서버에 마지막으로 직접 물어본 시각
    completed_at   TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_import_jobs_owner ON import_jobs (owner_id);
CREATE INDEX idx_import_jobs_created ON import_jobs (created_at);
