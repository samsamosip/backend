-- 지원 금액·한 줄 요약을 LLM 으로 뽑은 결과 (홈 카드 "월 최대 20만원"). 학교 장학은 Harvester 가 정리해 준 금액을 쓰므로 비어 있다.
-- ai_support_hash 는 뽑을 때의 지원 내용 글 + 프롬프트 버전 해시 — 글이 바뀌면 쓰지 않고 다시 뽑는다.
ALTER TABLE policies ADD COLUMN ai_support      JSONB;
ALTER TABLE policies ADD COLUMN ai_support_hash VARCHAR(64);
