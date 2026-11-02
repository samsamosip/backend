-- 발표·사용자 평가용 사용 통계. 날짜는 한국 날짜.
-- 하루에 한 번이라도 내 정보가 필요한 API(맞춤·프로필·준비함·검색 등)를 부른 사람. 로그인 전 임시 ID 와 계정 ownerId 를 같은 칸에 둔다.
CREATE TABLE owner_activity (
    day      DATE NOT NULL,
    owner_id UUID NOT NULL,
    PRIMARY KEY (day, owner_id)
);

CREATE INDEX idx_owner_activity_owner ON owner_activity (owner_id);

-- 날짜별 횟수 (search 검색, import_url 링크 불러오기, import_image 캡처 불러오기)
CREATE TABLE daily_counts (
    day   DATE        NOT NULL,
    name  VARCHAR(30) NOT NULL,
    count INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (day, name)
);
