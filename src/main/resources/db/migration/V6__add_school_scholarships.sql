-- 학교 장학 공고(policy-harvester)를 같은 policies 테이블에 source = 'SCHOOL' 로 넣는다.
ALTER TABLE policies ADD COLUMN source_version      VARCHAR(64);  -- Harvester version_id. 바뀌었을 때만 상세를 다시 받는다
ALTER TABLE policies ADD COLUMN checklist_documents JSONB;        -- 정리된 서류 목록 [{"name", "optional"}]. 있으면 글 쪼개기 대신 쓴다

-- 학교 장학 판정용 프로필 항목 (조건 JSON 명세 v0.1 의 학교용 2차 변수). 모두 비워 둘 수 있다.
ALTER TABLE user_profiles ADD COLUMN school_name          VARCHAR(100);  -- 학교 이름 (예: 인하대학교)
ALTER TABLE user_profiles ADD COLUMN enrollment_status    VARCHAR(20);   -- ENROLLED 재학 / ON_LEAVE 휴학 / INCOMING 입학 예정 / RETURNING 복학 예정 / COMPLETED 수료·졸업
ALTER TABLE user_profiles ADD COLUMN grade_level          INTEGER;       -- 학년
ALTER TABLE user_profiles ADD COLUMN gpa                  NUMERIC(5, 2); -- 평점 (직전 학기 또는 누적)
ALTER TABLE user_profiles ADD COLUMN gpa_scale            NUMERIC(4, 1); -- 만점 (4.5 / 4.3 / 100)
ALTER TABLE user_profiles ADD COLUMN kosaf_income_bracket INTEGER;       -- 한국장학재단 학자금 지원구간 0~10 (0 = 기초·차상위)
