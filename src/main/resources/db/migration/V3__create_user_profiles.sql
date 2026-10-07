-- 사용자 프로필 (WF01 온보딩, WF08 내 정보). 변수는 조건 JSON 명세 v0.1 을 따른다:
-- 온통청년에 있는 조건은 온통청년 코드 그대로, 없는 것만 새 이름.
-- 모든 항목은 비워 둘 수 있다. NULL = 아직 입력 안 함(판정 시 "확인 필요"),
-- sbiz_cd 의 빈 문자열 = "해당 없음"으로 답함.
CREATE TABLE user_profiles (
    id                          BIGSERIAL    PRIMARY KEY,
    anonymous_id                UUID         NOT NULL,   -- 로그인 전 임시 사용자 식별자 (X-Anonymous-Id)

    birth_date                  DATE,                    -- 만 나이 계산용
    zip_cd                      VARCHAR(5),              -- 주민등록 시군구 코드
    actual_zip_cd               VARCHAR(5),              -- 실거주 시군구 코드
    job_cd                      VARCHAR(16),             -- 온통청년 jobCd
    school_cd                   VARCHAR(16),             -- 온통청년 schoolCd
    mrg_stts_cd                 VARCHAR(16),             -- 온통청년 mrgSttsCd
    plcy_major_cd               VARCHAR(16),             -- 온통청년 plcyMajorCd
    sbiz_cd                     VARCHAR(100),            -- 온통청년 sbizCd 콤마 목록, '' = 해당 없음
    annual_income               INTEGER,                 -- 본인 연소득(만원), 공고 earnMaxAmt 와 같은 단위
    household_median_income_pct INTEGER,                 -- 가구 기준중위소득 %
    household_income_year       INTEGER,                 -- 위 비율의 산정 연도
    housing_type                VARCHAR(20),             -- MONTHLY_RENT / JEONSE / OWN / WITH_PARENTS
    homeowner                   BOOLEAN,                 -- 주택 소유 여부
    interest_categories         VARCHAR(100),            -- 관심 대분류 콤마 목록 (일자리, 주거, 교육, 복지문화, 참여권리)

    created_at                  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT uk_user_profiles_anonymous UNIQUE (anonymous_id)
);
