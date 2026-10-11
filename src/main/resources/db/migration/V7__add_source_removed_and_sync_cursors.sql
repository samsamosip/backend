-- 출처에서 사라진 공고(학교 장학: Harvester 에서 합쳐짐·비공개·삭제). 지우지 않고 표시만 해서 목록·맞춤에서 뺀다
-- (준비함에 담긴 공고의 상세는 계속 열린다). 다시 공개되면 false 로 돌린다.
ALTER TABLE policies ADD COLUMN source_removed BOOLEAN NOT NULL DEFAULT FALSE;

-- 수집기가 어디까지 읽었는지. 학교 장학은 Harvester 변경 목록(/v1/changes)의 마지막 event_seq 를 저장한다.
CREATE TABLE sync_cursors (
    name        VARCHAR(50)  PRIMARY KEY,
    cursor      VARCHAR(200) NOT NULL,
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);
