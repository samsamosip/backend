-- 같은 공고가 번호(plcyNo)만 바꿔 다시 등록되는 경우가 있다 (예: 신혼부부 전세자금 대출이자 지원 505·522번).
-- 제목(띄어쓰기 무시)과 주관기관이 같으면 같은 공고로 보고, 조회할 때 신청 가능한 것 중 가장 최근 등록된 하나만 보여 준다.
-- 계산 규칙은 Java 의 Duplicates 와 같다. 이미 저장된 공고는 아래 UPDATE 로 채운다.
ALTER TABLE policies ADD COLUMN duplicate_key        TEXT;       -- 띄어쓰기 뺀 제목 | 주관기관
ALTER TABLE policies ADD COLUMN source_registered_at TIMESTAMP;  -- 온통청년 frstRegDt (최초 등록일)

UPDATE policies
SET duplicate_key = regexp_replace(title, '\s', '', 'g') || '|' || coalesce(organization, ''),
    source_registered_at = to_timestamp(nullif(trim(raw_payload ->> 'frstRegDt'), ''), 'YYYY-MM-DD HH24:MI:SS');

CREATE INDEX idx_policies_duplicate_key ON policies (duplicate_key);
