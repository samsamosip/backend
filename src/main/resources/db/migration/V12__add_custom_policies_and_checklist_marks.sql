-- 공고 직접 등록 (WF07): 사용자가 손으로 입력한 공고. source = USER, 등록한 사람(owner_id)에게만 보인다.
-- 목록·맞춤·새 공고 알림에는 나오지 않고, 준비함·마감 임박 알림에만 쓴다.
ALTER TABLE policies ADD COLUMN owner_id       UUID;
ALTER TABLE policies ADD COLUMN apply_end_time TIME;   -- 마감 시각 (예: 18:00). 공식 공고는 비어 있음
ALTER TABLE policies ADD COLUMN memo           TEXT;   -- 사용자 메모

CREATE INDEX idx_policies_owner ON policies (owner_id) WHERE owner_id IS NOT NULL;

-- 공고 서류가 바뀌었을 때 체크리스트 표시: ADDED 공고에 새로 추가됨 / DROPPED 공고에서 빠짐 (지우지 않는다)
ALTER TABLE checklist_items ADD COLUMN policy_change VARCHAR(10);
