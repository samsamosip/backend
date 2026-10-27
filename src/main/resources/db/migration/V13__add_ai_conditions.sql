-- 글로 된 자격 조건(earnEtcCn, addAplyQlfcCndCn, ptcpPrpTrgtCn)을 LLM 으로 판정 규칙으로 바꾼 결과.
-- ai_conditions_hash 는 변환할 때의 글 + 프롬프트 버전 해시 — 글이 바뀌면 맞지 않아서 원래 방식(확인 필요)으로 돌아가고 다시 변환한다.
ALTER TABLE policies ADD COLUMN ai_conditions      JSONB;
ALTER TABLE policies ADD COLUMN ai_conditions_hash VARCHAR(64);
ALTER TABLE policies ADD COLUMN ai_converted_at    TIMESTAMP;
