-- 청년 정책 공고.
-- 온통청년 필드는 이름과 코드값을 그대로 저장한다 (조건 JSON 명세 v0.1: "API에 있는 건 그대로").
-- source 칸을 두어 2차에 학교 공지(SCHOOL)·사용자 등록(USER)도 같은 테이블에 넣는다.
CREATE TABLE policies (
    id                      BIGSERIAL    PRIMARY KEY,
    source                  VARCHAR(20)  NOT NULL,              -- ONTONG / SCHOOL / USER
    source_id               VARCHAR(64)  NOT NULL,              -- 온통청년: plcyNo

    -- 표시·검색
    title                   VARCHAR(300) NOT NULL,              -- plcyNm
    description             TEXT,                               -- plcyExplnCn
    support_content         TEXT,                               -- plcySprtCn
    category                VARCHAR(64),                        -- lclsfNm
    middle_category         VARCHAR(64),                        -- mclsfNm
    keywords                VARCHAR(300),                       -- plcyKywdNm
    organization            VARCHAR(255),                       -- sprvsnInstCdNm

    -- 신청 기간
    apply_period_code       VARCHAR(16),                        -- aplyPrdSeCd: 0057001 특정기간 / 0057002 상시 / 0057003 마감
    apply_period_raw        VARCHAR(100),                       -- aplyYmd 원문
    apply_start_date        DATE,
    apply_end_date          DATE,

    -- 자격 조건 (온통청년 코드 그대로)
    sprt_trgt_min_age       INTEGER,
    sprt_trgt_max_age       INTEGER,
    sprt_trgt_age_lmt_yn    VARCHAR(1),
    zip_cd                  TEXT,                               -- 시군구 코드 콤마 목록 (전국 정책도 전부 나열됨)
    earn_cnd_se_cd          VARCHAR(16),
    earn_min_amt            INTEGER,                            -- 만원
    earn_max_amt            INTEGER,                            -- 만원
    earn_etc_cn             TEXT,                               -- AI가 읽을 소득 조건 설명 글
    job_cd                  VARCHAR(100),
    school_cd               VARCHAR(100),
    mrg_stts_cd             VARCHAR(16),
    plcy_major_cd           VARCHAR(100),
    sbiz_cd                 VARCHAR(100),
    add_aply_qlfc_cnd_cn    TEXT,                               -- AI가 읽을 추가 자격 조건 글
    ptcp_prp_trgt_cn        TEXT,                               -- AI가 읽을 참여 제한 대상 글

    -- 신청·서류
    apply_url               VARCHAR(1000),                      -- aplyUrlAddr
    reference_url1          VARCHAR(1000),                      -- refUrlAddr1
    reference_url2          VARCHAR(1000),                      -- refUrlAddr2
    apply_method            TEXT,                               -- plcyAplyMthdCn
    screening_method        TEXT,                               -- srngMthdCn
    submission_documents    TEXT,                               -- sbmsnDcmntCn

    -- 변경 감지
    source_modified_at      TIMESTAMP,                          -- lastMdfcnDt
    raw_hash                VARCHAR(64)  NOT NULL,              -- 원본 JSON의 SHA-256. 바뀌었을 때만 갱신
    raw_payload             JSONB        NOT NULL,              -- API 응답 원본

    created_at              TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT uk_policies_source UNIQUE (source, source_id)
);

CREATE INDEX idx_policies_apply_end_date ON policies (apply_end_date);
CREATE INDEX idx_policies_category ON policies (category);
