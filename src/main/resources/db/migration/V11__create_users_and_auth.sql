-- 로그인 (자체 이메일 + 카카오). 사용자 데이터(프로필·준비함·알림)는 지금처럼 anonymous_id 칸으로 묶고,
-- 계정마다 owner_id(UUID)를 따로 둔다. 로그인하면 그 브라우저의 임시 ID 데이터를 owner_id 로 옮긴다.
CREATE TABLE users (
    id                  BIGSERIAL     PRIMARY KEY,
    owner_id            UUID          NOT NULL UNIQUE,      -- 프로필·준비함·알림을 묶는 키 (임시 ID 헤더로는 열 수 없음)
    email               VARCHAR(255)  UNIQUE,               -- 소문자로 저장. 카카오만 쓰는 계정은 NULL
    password_hash       VARCHAR(100),                       -- BCrypt. 카카오만 쓰는 계정은 NULL
    email_verified      BOOLEAN       NOT NULL DEFAULT FALSE,
    nickname            VARCHAR(30)   NOT NULL,
    kakao_id            BIGINT        UNIQUE,
    failed_login_count  INTEGER       NOT NULL DEFAULT 0,
    locked_until        TIMESTAMP,                          -- 비밀번호 5번 틀리면 15분
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now()
);

-- 로그인 유지용 갱신 토큰. 원문은 저장하지 않고 SHA-256 만. 쓸 때마다 새 토큰으로 바꾸고 옛것은 revoked.
CREATE TABLE refresh_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expires_at  TIMESTAMP    NOT NULL,
    revoked_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- 이메일 인증·비밀번호 찾기 6자리 코드. 코드는 SHA-256 만 저장, 10분 유효, 5번 틀리면 무효.
-- 비밀번호 찾기는 코드가 맞으면 10분짜리 재설정 통행증(ticket)을 준다.
CREATE TABLE verification_codes (
    id                 BIGSERIAL    PRIMARY KEY,
    email              VARCHAR(255) NOT NULL,
    purpose            VARCHAR(20)  NOT NULL,     -- SIGNUP / RESET
    code_hash          VARCHAR(64)  NOT NULL,
    expires_at         TIMESTAMP    NOT NULL,
    attempts           INTEGER      NOT NULL DEFAULT 0,
    consumed_at        TIMESTAMP,
    ticket_hash        VARCHAR(64)  UNIQUE,
    ticket_expires_at  TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_verification_codes_email ON verification_codes (email, purpose, id DESC);
