# Youth Benefit Backend

청년혜택 길잡이의 Spring 백엔드다. 온통청년 청년정책 API 전체를 주기적으로 수집해 원본 그대로 보관하고,
화면에 필요한 값(지역, 대분류)을 정리해 "지금 신청할 수 있는 공고" API로 제공한다. 앞으로 사용자 프로필,
자격 판정(조건 일치 / 확인 필요 / 조건 불일치), 준비함, 알림을 이 서버에 붙인다.

가장 중요한 원칙: **온통청년 필드는 이름과 코드값을 그대로 저장한다.** 화면용으로 계산한 값은 별도 칸에 두고
원본은 바꾸지 않는다. 학교 장학 공고는 [policy-harvester](https://github.com/samsamosip/policy-harvester)가
수집·구조화하고, 이 서버는 그 API를 받아 같은 공고 목록·판정·준비함에 합친다(`source=SCHOOL`).

## 빠른 시작

Docker(Docker Desktop 또는 OrbStack)를 켠 뒤:

```bash
cp .env.example .env          # ONTONG_API_KEY 설정
docker compose up --build -d  # 애플리케이션과 PostgreSQL 실행
docker compose logs -f app    # 시작 로그 확인
```

애플리케이션은 `http://localhost:8080`에서 실행된다. 종료할 때는 `docker compose down`을 사용한다.
DB 데이터는 `postgres-data` 볼륨에 남으며, 데이터까지 지우려면 `docker compose down -v`를 사용한다.

로컬 JVM에서 개발할 때는 다음처럼 실행한다. 이 경우 Spring Boot가 Compose의 PostgreSQL만 자동으로 띄운다.

```bash
./gradlew bootRun
```

다른 터미널에서 첫 수집을 한다(약 10초, 3천여 건):

```bash
curl -X POST localhost:8080/api/v1/admin/ontong/sync
```

학교 장학도 받으려면(`.env`에 `HARVESTER_*` 필요). 빈 DB에서 처음 받을 때는 상세 요청 한도 때문에 30분 넘게 걸리고,
그다음부터는 Harvester 변경 목록에서 바뀐 것만 받아 1초 안쪽이다. 목록 전체와 다시 비교하려면 `?full=true`:

```bash
curl -X POST localhost:8080/api/v1/admin/harvester/sync
```

- 공고 목록: `http://localhost:8080/api/v1/policies?size=5`
- **API 문서(Swagger)**: `http://localhost:8080/docs` — 모든 API의 요청·응답 모양을 보고 Try it out으로 바로 호출해 볼 수 있다
- JDK 21이 없어도 Gradle toolchain이 자동으로 받는다. VS Code는 `.vscode/extensions.json`의 추천 확장을 설치한다.

## 설정

`.env`(비밀값 포함, git ignore 대상)에서 읽는다. 비밀값(API key)은 로그·문서·commit에 복사하지 않는다.

| 키 | 기본값 | 비고 |
|---|---|---|
| `ONTONG_API_KEY` | (없음) | 온통청년 오픈 API 인증키. youthcenter.go.kr 마이페이지에서 발급. 없으면 수집 API가 502 |
| `ONTONG_SYNC_SCHEDULED` | `false` | `true`면 6시간마다 자동 수집(`ontong.sync.cron`, Asia/Seoul) |
| `NOTIFICATIONS_DAILY` | `true` | 매일 아침 8시 새 맞춤 공고·마감 임박 알림 생성(`notifications.daily.cron`). `false`면 끈다 |
| `HARVESTER_BASE_URL`, `HARVESTER_API_KEY` | (없음) | policy-harvester API. 관리자 화면 API key 메뉴에서 발급(90일 만료). 없으면 학교 장학 수집 API가 502 |
| `HARVESTER_SYNC_SCHEDULED` | `false` | `true`면 매일 새벽 4시 학교 장학 자동 수집(`harvester.sync.cron`, Asia/Seoul) |
| `ADMIN_API_KEY` | (없음) | 관리자 API(`/api/v1/admin/**`) 키. 넣으면 `X-Admin-Key` 헤더가 같아야만 호출된다(틀리면 401). **로컬 개발에서는 비워 두고 배포 서버에만 넣는다** |
| `JWT_SECRET` | (없음) | 로그인 토큰 서명 키(32자 이상 아무 문자열). 비우면 실행할 때마다 새 키라 서버를 다시 켜면 모두 로그아웃된다. **배포 서버에는 꼭 넣는다** |
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | (없음) | 카카오 디벨로퍼스 REST API 키, Client Secret. 없으면 카카오 로그인이 503 `KAKAO_NOT_CONFIGURED` |
| `KAKAO_REDIRECT_URIS` | `http://localhost:5173/auth/kakao/callback` | 프론트 카카오 콜백 주소(콤마 구분). 카카오 콘솔 Redirect URI와 같아야 하고, 여기 없는 주소는 400 |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | (없음) | 인증 코드 메일(Gmail이면 보내는 주소와 앱 비밀번호, `MAIL_HOST`·`MAIL_PORT`로 바꿀 수 있음). **비우면 메일 대신 서버 로그에 코드를 찍는다(개발용)** |
| `PWC_LLM_BASE_URL`, `PWC_LLM_API_KEY` | (없음) | PwC LLM 게이트웨이(OpenAI 방식). 글 조건 AI 변환에 쓴다. 주간 사용 한도가 있다. 없으면 변환 API가 503, 판정은 원래대로 |
| `PWC_LLM_MODEL`, `PWC_LLM_REVIEW_MODEL` | `openai.gpt-4.1-mini`, `openai.gpt-4.1` | 1차 변환 모델, 애매한 것만 다시 보는 2차 모델. 이름 앞에 `openai.`가 붙는다(목록 API는 막혀 있음, 실측으로 확인: `openai.gpt-4o(-mini)`, `openai.gpt-4.1(-mini)`, `openai.gpt-5(-mini/-nano)`) |
| `PWC_LLM_DAILY_TOKEN_LIMIT` | `3000000` | 하루(한국 시간) AI 토큰 한도. 넘으면 다음 날까지 AI를 안 부른다(검색은 단어로, 변환은 다음 수집 때). 주간 $50 보호용. 0이면 제한 없음 |
| `SEARCH_RATE_PER_MINUTE`, `SEARCH_RATE_PER_IP_PER_MINUTE`, `SEARCH_AI_PER_HOUR` | `30`, `300`, `30` | 검색 사용량 제한: 한 사람(IP+기기) 1분, 한 IP 전체(같은 와이파이) 1분 — 넘으면 429 `RATE_LIMITED`. 한 사람 1시간 AI 해석 수 — 넘으면 단어 검색(오류 아님, `notice`로 안내) |
| `APP_WEB_URL` | `http://localhost:5173` | 프론트 주소. 알림 메일의 "알림함에서 보기" 링크에 쓴다. 배포하면 프론트 주소로 |
| `MAIL_DAILY_LIMIT` | `450` | 하루(한국 시각) 보낼 메일 수. Gmail은 하루 약 500통이 넘으면 계정을 막아서 그 전에 멈춘다(가입은 503 `MAIL_BUSY`) |
| `AUTH_MAIL_PER_HOUR`, `AUTH_ATTEMPTS_PER_10_MINUTES` | `20`, `60` | 한 사람(IP)의 인증 메일 요청(가입·코드 다시 받기·비밀번호 찾기) 1시간 수, 로그인·코드 확인 시도 10분 수. 넘으면 429 `MAIL_RATE_LIMITED` / `TOO_MANY_ATTEMPTS` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | API를 부를 수 있는 프론트 주소(콤마 구분). 배포하면 프론트 주소를 추가 |
| `DOCKER_COMPOSE_ENABLED` | `true` | Docker 없이 직접 띄운 PostgreSQL을 쓸 때 `false` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 로컬 기본값 | `DOCKER_COMPOSE_ENABLED=false`일 때 쓰는 접속 정보 |
| `APP_PORT`, `POSTGRES_PORT` | `8080`, `5432` | Compose에서 호스트에 공개할 포트 |
| `APP_IMAGE` | `youth-benefit-backend:local` | Compose가 빌드하고 실행할 이미지 이름과 태그 |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | `youthbenefit` | Compose PostgreSQL 및 앱 접속 정보 |

### 이미지 빌드(CI)와 배포

`.github/workflows/docker.yml`은 모든 push에서 테스트를 실행한 뒤 GHCR에 이미지를 발행한다.

- 모든 브랜치: `ghcr.io/samsamosip/backend:sha-<커밋 앞 12자리>`
- 기본 브랜치: 같은 이미지에 `ghcr.io/samsamosip/backend:latest` 태그 추가
- 인증: workflow의 `GITHUB_TOKEN`을 사용하므로 별도 저장소 secret은 필요 없다.

서버에서는 `.env`의 `APP_IMAGE`를 GHCR 이미지로 설정하고 Compose로 받는다. 패키지가 비공개라면 먼저
`read:packages` 권한이 있는 토큰으로 `docker login ghcr.io`를 실행한다.

```bash
docker compose pull
docker compose up -d --no-build
```

## Public API

관리자 API(`/admin`)는 `ADMIN_API_KEY`로 잠근다. 사용자 로그인은 아래 [로그인](#로그인) 참고.

| 경로 | 내용 |
|---|---|
| `GET /api/v1/policies?category=&page=&size=` | 지금 신청할 수 있는 공고. 마감 임박순, 마감일 없는(상시) 공고는 뒤로. `size` 최대 100. 카드마다 `source`(ONTONG 정부 공고 / SCHOOL 학교 장학) |
| `GET /api/v1/policies/{id}` | 공고 상세. 자격 조건은 온통청년 코드 그대로(`jobCd`, `schoolCd` 등). `sourceUrl` 원문 보기 링크(온통청년 상세 페이지·학교 장학 원문·직접 등록 링크), `documents` 제출 서류 목록(준비함 체크리스트와 같은 쪼개기), `support` 지원 금액(매월 지원이면 "월 최대 20만원 · 최대 24개월") |
| `POST /api/v1/admin/ontong/sync` | 온통청년 전체 수집. 결과 `{fetched, created, updated, unchanged}` |
| `POST /api/v1/admin/harvester/sync` | 학교 장학 수집(`?full=true`면 목록 전체 비교). 결과 `{mode, seen, skippedOld, fetched, created, updated, unchanged, removed, failed}` |
| `GET /api/v1/codes` | 온보딩 선택지(`jobCd`, `schoolCd`, `mrgSttsCd`, `plcyMajorCd`, `sbizCd`, `housingType`, `interestCategories`, 학교용 `enrollmentStatus`, `gpaScale`, `kosafIncomeBracket`)의 code·label |
| `GET /api/v1/regions` | 지역 선택지: 시도 16개와 각 시군구 `{code, name, sigungu: [{code, label}]}`. 프로필 `zipCd`에는 시군구 code(5자리)를 저장 |
| `GET /api/v1/me/profile` | 내 프로필. 만 나이(`age`), 주민등록 시도(`residenceSido`), 아직 비어 있는 항목(`missingFields`) 포함. 없으면 404 |
| `PUT /api/v1/me/profile` | 내 프로필 전체 저장. 보내지 않은 항목은 "모름"으로 비워진다 |
| `PATCH /api/v1/me/profile` | 보낸 항목만 수정 `{"annualIncome": 2400}`. 안 보낸 항목은 그대로, `null`은 "모름"으로. 프로필이 없으면 새로 만든다 |
| `DELETE /api/v1/me` | 내 데이터 삭제 (프로필과 준비함) |
| `GET /api/v1/me/matches?category=&verdict=&includeNoMatch=&page=&size=` | 맞춤 공고(WF02). 신청 가능한 공고를 내 프로필로 판정. 기본은 조건 일치 + 확인 필요, 마감 임박순. `counts`는 전체 판정 건수. `sort=recommended`(기본, 추천순: 조건 일치 → 확인 필요 → 불일치, 같으면 마감 임박) / `deadline`(마감순). `closingWithinDays=7`이면 7일 안에 마감하는 공고만(홈 "이번 주 마감", 상시 모집 제외). 카드마다 `dDay` |
| `GET /api/v1/me/suggestions` | 홈 배너: 비어 있는 내 정보마다 채우면 판정이 확정되는 공고 수 `{field, label, resolvable, affected, message}`, 효과 큰 순. 예: "소득을 입력하면 공고 2개가 더 판정돼요". 글로 된 조건이 남아 채워도 확인 필요인 공고는 `resolvable`에 세지 않는다 |
| `POST /api/v1/me/applications` | 준비함에 담기 `{"policyId": 1}`(관심) 또는 `{"policyId": 1, "status": "PREPARING"}`("신청 준비 시작"). 이미 담겨 있으면 200으로 기존 것을 주고, status를 보냈으면 상태만 바꾼다. 응답의 `appliedAt`은 신청 완료로 표시한 시각 |
| `GET /api/v1/me/applications` | 준비함 목록. 카드마다 상태, 마감 D-day, 준비율 |
| `GET /api/v1/me/applications/{id}` | 준비함 상세. 체크리스트, 제출 서류 원문, 신청 링크. 체크리스트 서류마다 `guide`: 어디서 발급받는지 `{issuer, fee, online, url, label: "정부24 · 무료 · 온라인 발급"}`, 모르면 null(`application/DocumentGuides`: 자주 나오는 서류 40여 종, 실데이터 서류 1,002개 중 약 65%) |
| `PATCH /api/v1/me/applications/{id}` | 상태 변경 `{"status": "PREPARING"}` (INTERESTED / PREPARING / APPLIED) |
| `GET /api/v1/me/notifications?type=&tab=` | 알림함(WF05): 최신순. `tab`은 디자인 탭: NEW 새 공고 / EXTENDED 마감 연장 / CORRECTED 정정(내용 변경 포함) / CANCELLED 취소(공고 내림 포함). `type`: CHANGE(담은 공고 변경) / NEW_MATCHES(새 맞춤 공고, 하루 1회 묶음) / DEADLINE(담은 공고 마감 임박). `unreadCount`(배지용), 알림마다 `label`(탭·배지 이름: 마감 연장·정정·취소·새 공고·마감 임박 등)과 한 줄 `summary` |
| `GET /api/v1/me/notifications/{id}` | 알림 상세. 종류에 따라 `changes`(바뀐 항목 전/후 `[{field, label, before, after}]`), `newPolicies`(`{total, policies: [{policyId, title, verdict, verdictLabel}]}`), `deadline`(`{applyEndDate, daysLeft}`) 중 하나. 열면 읽음 |
| `POST /api/v1/me/notifications/read-all`, `DELETE /api/v1/me/notifications/{id}` | 모두 읽음, 알림 삭제 |
| `GET`·`PUT /api/v1/me/notification-settings` | 알림 설정(WF05-b): `{newMatches, newMatchVerdicts: ["MATCH","NEEDS_CHECK"], deadline, deadlineDays: [7,3,1,0], changes, quietStart, quietEnd, app, email}`. `app`·`email`은 받는 방법(기본 앱 켬·이메일 끔, 보내지 않으면 그대로). 저장한 적 없으면 전부 켜짐. 방해금지 시간은 앱 밖 발송이 생기면 쓴다 |
| `POST /api/v1/admin/ai/conditions?limit=50` | 글 조건 AI 변환: 신청 가능한 온통청년 공고 중 아직 안 했거나 글이 바뀐 것만. 결과 `{pending, converted, reviewed, failed, tokens}`. 정기 수집(`ONTONG_SYNC_SCHEDULED`) 뒤에도 자동으로 돈다 |
| `POST /api/v1/admin/ai/support?limit=50`, `/recheck` | 지원 금액·한 줄 요약 AI 뽑기 / 저장된 답 다시 검사(비용 없음). 정기 수집 뒤에도 자동 |
| `POST /api/v1/admin/ai/conditions/recheck` | 검사 규칙을 고친 뒤 저장된 AI 답을 다시 검사 (AI 호출·비용 없음) |
| `POST /api/v1/admin/notifications/email-digest` | 알림 메일 지금 보내기. 보통은 매일 아침 8시 매일 알림 뒤에 자동 |
| `POST /api/v1/admin/notifications/run-daily` | 매일 알림(새 맞춤 공고·마감 임박) 지금 실행. 보통은 매일 아침 8시 자동 |
| `GET /api/v1/policies/{id}/changes` | 공고 변경 이력(누구나) |
| `DELETE /api/v1/me/applications/{id}` | 관심 해제 |
| `POST`·`PATCH`·`DELETE /api/v1/me/applications/{id}/items[/{itemId}]` | 서류 추가 `{"content"}` · 체크 `{"checked": true}`·이름 수정 · 삭제 |
| `POST /api/v1/me/custom-policies` | 공고 직접 등록(WF07 1차) `{title, organization, applyEndDate, applyEndTime: "18:00", category, memo, applyUrl, documents: ["재학증명서"], status}` — 공고명만 필수. 나만 보이는 공고가 생기고 준비함에 바로 담김(응답은 준비함 상세, `source: "USER"`). 판정 없음, D-day·마감 임박 알림·체크리스트는 됨 |
| `PUT`·`DELETE /api/v1/me/custom-policies/{policyId}` | 직접 등록 공고 수정(보낸 값 전체로, 남은 서류의 체크는 유지)·삭제. 준비함에서 빼도 공고째 지워진다 |
| `GET /api/v1/search?q=서울 사는 대학생 월세&includeNoMatch=&page=&size=` | 자연어 검색(WF06). 지금 신청 가능한 공고를 찾아 내 프로필로 판정. `understood`는 알아들은 조건(화면 칩): `keywords`(찾는 것 단어 묶음), `audience`(누구), `categories`, `region`, `age`, `closingSoon`, `ai`(false면 AI 없이 단어로 찾음). 기본은 불일치 제외. 결과가 없으면 `message`(WF06-b: "부산에서는 못 찾았어요. 지역을 빼면 n개 있어요") |
| `GET /api/v1/policies/{id}/eligibility` | 공고 하나의 조건별 판정(WF03): 공고 요구사항, 내 값, 참/거짓/미확인, 근거, 채우면 다시 판정되는 항목. `summary`는 카드용 짧은 이유(최종 디자인 "기관 · 소득 정보 확인 필요"의 "·" 뒤: "나이·지역 조건 일치", "지역 조건 불일치", "소득 정보 확인 필요", "예술인 확인 필요"), `explanation`은 상세용 한 문장("소득을 입력하면 바로 확인돼요", "예술인은 공고 원문에서 확인해 주세요") |

`/me` 경로는 로그인했으면 `Authorization: Bearer {accessToken}`, 아니면 `X-Anonymous-Id` 헤더(UUID)로 사람을 구분한다.
프론트가 처음 방문 때 UUID를 만들어 localStorage에 두고 매 요청에 보낸다. 둘 다 없거나 UUID가 아니면 400.
토큰이 틀리거나 만료되면 익명으로 넘어가지 않고 401(`TOKEN_EXPIRED`면 `/auth/refresh`).

### 로그인

| 경로 | 내용 |
|---|---|
| `POST /api/v1/auth/signup` | 이메일 가입 `{email, password, nickname}` → 인증 코드 메일(6자리, 10분) 202. 이미 인증된 이메일은 409 `EMAIL_TAKEN` |
| `POST /api/v1/auth/email/resend` | 가입 코드 다시 받기 `{email}`. 1분 안이면 429 `CODE_COOLDOWN` |
| `POST /api/v1/auth/email/verify` | 코드 확인 `{email, code}` → 바로 로그인(토큰). 400 `CODE_MISMATCH`(남은 횟수) / `CODE_EXPIRED` |
| `POST /api/v1/auth/login` | 이메일 로그인 `{email, password}`. 401 `BAD_CREDENTIALS`(남은 횟수), 5번 틀리면 423 `ACCOUNT_LOCKED`(15분), 인증 전이면 403 `EMAIL_NOT_VERIFIED` |
| `POST /api/v1/auth/kakao` | 카카오 로그인 `{code, redirectUri}`(카카오가 콜백으로 준 인가 코드). 처음이면 자동 가입. 401 `KAKAO_LOGIN_FAILED` |
| `POST /api/v1/auth/refresh` | 토큰 갱신 `{refreshToken}`. 갱신 토큰도 새것으로 바뀐다. 이미 바꾼 토큰을 다시 쓰면 401 `TOKEN_REUSED` + 그 계정 전체 로그아웃 |
| `POST /api/v1/auth/logout` | `{refreshToken}` 끊기 204 |
| `POST /api/v1/auth/password/forgot` → `/password/verify-code` → `/password/reset` | 비밀번호 찾기 3단계: 코드 받기(가입 여부와 상관없이 202) → 코드 확인 `{email, code}` → `resetToken`(10분) → 새 비밀번호 `{resetToken, newPassword}` → 로그인. 다른 기기 로그인은 끊긴다. 400 `RESET_EXPIRED` |
| `GET`·`PATCH /api/v1/me/account` | 내 계정 `{id, email, nickname, emailVerified, kakaoLinked, hasPassword}`, 닉네임 바꾸기 `{nickname}`. 로그인 필요(없으면 401 `LOGIN_REQUIRED`) |
| `POST /api/v1/me/account/password` | 비밀번호 바꾸기 `{currentPassword, newPassword}`. 카카오만 쓰는 계정은 400 `NO_PASSWORD` |
| `DELETE /api/v1/me/account` | 회원 탈퇴: 계정, 프로필, 준비함, 알림 모두 삭제 |

- 로그인 응답: `{accessToken, accessTokenExpiresIn: 1800, refreshToken, refreshTokenExpiresIn: 1209600, account}`.
  출입증(access) 30분, 갱신 토큰 14일. 오류는 `{message, errors, code}` — 화면 분기는 `code`로 한다.
- 남용 막기: 한 사람(IP) 인증 메일 요청 1시간 20번, 로그인·코드 확인 10분 60번(행사장 와이파이처럼 여럿이 한 IP를 써도 되게 넉넉히),
  한 주소당 하루 코드 10통(429 `CODE_DAILY_LIMIT`, 남의 주소로 메일 폭탄 막기), 서버 전체 하루 메일 450통(503 `MAIL_BUSY`, Gmail 계정 막힘 막기).
  `AuthRateLimitInterceptor`, 서버 메모리에서 센다.
- 비밀번호: 8~64자, 영문과 숫자를 함께. 특수문자는 써도 되고 안 써도 된다. BCrypt로 저장.
- 로그인 전에 쓰던 데이터 옮기기: 가입 완료·로그인·카카오·비밀번호 재설정 요청에 `X-Anonymous-Id`를 같이 보내면 그 UUID의 프로필·알림 설정
  (계정에 없을 때만)·준비함(이미 담은 공고는 계정 것 유지)·알림을 계정으로 옮긴다.
- 프론트: 로그인하면 토큰을 저장하고 `Authorization`만 보낸다. 로그아웃하면 토큰을 지우고 **새** UUID를 만든다.
  계정의 UUID를 `X-Anonymous-Id`로 보내면 401 `LOGIN_REQUIRED`(UUID만 알아서는 계정 데이터에 못 들어온다).
- 카카오 흐름: 프론트가 `https://kauth.kakao.com/oauth/authorize?response_type=code&client_id={REST API 키}&redirect_uri={콜백}`로 보냄 →
  콜백 `?code=` → `POST /auth/kakao`. 닉네임이 없으면 "카카오 사용자".

`category`는 공식 대분류 5개 중 하나다: `일자리`, `주거`, `교육`, `복지문화`, `참여권리`.

목록 응답 예:

```json
{
  "id": 185,
  "title": "2026년 고성형 청년 월세 지원사업",
  "categories": ["주거"],
  "organization": "강원특별자치도 고성군",
  "applyPeriodCode": "0057001",
  "applyStartDate": "2026-10-01",
  "applyEndDate": "2026-10-16",
  "nationwide": false,
  "regions": ["강원"]
}
```

### 프로필 저장 예

```bash
curl -X PUT localhost:8080/api/v1/me/profile \
  -H "X-Anonymous-Id: 6f1c2b7e-1d1a-4c55-9a6b-0d6a3a1e2f00" -H "Content-Type: application/json" \
  -d '{"birthDate":"2003-06-12","zipCd":"11620","jobCd":"0013003","schoolCd":"0049005",
       "housingType":"MONTHLY_RENT","sbizCd":[],"interestCategories":["주거"]}'
```

잘못된 값은 항목별 이유와 함께 400으로 돌려준다: `{"message":"입력값을 확인해 주세요.","errors":{"jobCd":"알 수 없는 코드: 0013010"}}`

## 속도

맞춤 홈·홈 배너·검색·공고 목록은 신청 가능한 공고와 판정 조건·카드 모양을 메모리에 들고 있다가 판정만 매번 한다(`eligibility/PolicyCatalog`).
공고 데이터가 바뀌면(개수 + 모든 수정 시각의 합으로 요청마다 확인, 약 1ms) 또는 날짜가 바뀌면 다시 만든다(1,226건에 약 0.3초).

| 실측(10/28, 노트북, 공고 1,226건) | 전 | 후 |
|---|---|---|
| 맞춤 홈 1명 | 0.23초 | 0.04초 |
| 공고 목록 / 검색 | 0.14초 / 0.16초 | 0.01초 / 0.01초 |
| 30명이 동시에 맞춤 홈 (평균 / 느린 5%) | 0.85초 / 1.6초 | 0.15초 / 0.27초 |
| 초당 처리 | 32건 | 188건 |

## 테스트

```bash
./gradlew test
```

단위 테스트는 외부 호출 없이 돈다(MockRestServiceServer, 실제 API 응답으로 만든 fixture). DB 통합 테스트는
Testcontainers로 PostgreSQL 17을 띄우며, Docker가 꺼져 있으면 건너뛴다.

## 코드 구조

| 경로 | 역할 |
|---|---|
| `ontong/OntongApiClient` | API 호출. 500건씩 전체 페이지, 실패 시 2초·5초 뒤 재시도(최대 3번), 연결 10초·응답 60초 제한 |
| `ontong/OntongPolicyMapper` | 원본 Map → `PolicyContent`. 공백 정리, 신청 기간 파싱, 지역·대분류 계산, 변경 감지 해시 |
| `ontong/OntongSyncService` | 저장. 새 공고는 추가, 기존 공고는 해시가 바뀐 경우에만 갱신 |
| `ontong/OntongSyncScheduler`, `OntongSyncController` | 정기 수집(선택), 수동 수집 API |
| `harvester/` | 학교 장학(policy-harvester) 수집: API 호출(429면 `Retry-After`만큼 대기), 상세 → `PolicyContent`, 변경 목록으로 바뀐 것만 받기, 읽은 위치(`SyncCursor`) |
| `policy/Policy`, `PolicyRepository` | 공고 엔티티, "지금 신청 가능" 조회 |
| `policy/Regions`, `Categories` | 시도·시군구 이름표, 전국 판정, 옛 코드 → 새 코드, 거주지 이름 → 코드, 대분류 통일 규칙 |
| `policy/PolicyController` | 목록·상세 API |
| `profile/` | 사용자 프로필 엔티티·저장·검증, `/me` API |
| `code/OntongCodes`, `CodeController` | 공식 코드정의서의 사용자 선택 코드와 이름, 선택지 API |
| `application/` | 준비함: 관심 저장, 상태, 서류 체크리스트, 제출 서류 글 → 항목 쪼개기, 서류 변경 반영(`ChecklistSync`), 공고 직접 등록(`CustomPolicyService`) |
| `notification/` | 공고 변경 감지(`PolicyDiff`), 변경 이력·알림 저장(`PolicyChangeRecorder`), 알림함 API |
| `eligibility/` | 3값 판정: 조건 나무(`Condition`), 판정기(`ConditionEvaluator`), 온통청년·학교 장학 → 조건 변환, 글 조건 AI 변환(`AiConditionConverter`·`AiConditionService`), 맞춤 목록 API |
| `ai/` | PwC LLM 게이트웨이 호출(`LlmClient`: JSON 모양 고정, 재시도 1번) |
| `support/` | 지원 금액·한 줄 요약 AI 뽑기(`AiSupportExtractor`·`AiSupportService`). 카드 표시는 `policy/Support` |
| `search/` | 자연어 검색: 문장 해석(`QueryInterpreter`, AI·기억·단어 대체), 찾기·정렬(`SearchService`) |
| `auth/` | 이메일 가입·로그인, 카카오 로그인, 비밀번호 찾기, 토큰(`TokenService`), 로그인 전 데이터 옮기기(`AccountMergeService`), `@CurrentOwner`(토큰 또는 `X-Anonymous-Id`) |
| `common/ApiExceptionHandler` | 오류 응답 형식 통일 (`{message, errors, code}`) |
| `common/WebConfig`, `AdminKeyInterceptor` | CORS 허용 주소, API 문서 제목, 관리자 API 잠금 |
| `resources/db/migration/` | Flyway SQL. JPA는 `validate`만 한다 |

## 데이터 처리 규칙

### 저장과 변경 감지

- `policies` 한 행 = 공고 한 건. `(source, source_id)`가 유일하다. 온통청년은 `source=ONTONG`, `source_id=plcyNo`.
  학교 공고·사용자 등록 공고도 같은 테이블에 `SCHOOL`, `USER`로 넣는다.
- API 응답 원본은 `raw_payload`(jsonb)에 그대로 둔다. `raw_hash`는 원본에서 **조회수(`inqCnt`)를 뺀** SHA-256이고,
  이 값이 바뀐 공고만 다시 저장한다. 조회수까지 넣으면 하루 만에 3,235건 중 1,950건이 "수정"으로 잡혔다.
- 마감(`aplyPrdSeCd=0057003`) 공고도 저장하고 조회에서 뺀다. 수집 단계에서 버리면 이미 저장된 공고가 나중에 마감으로
  바뀐 것을 반영하지 못한다.

### 온통청년 형식 정리 (2026-10 실측)

| 원본 | 처리 |
|---|---|
| 빈 값이 공백 문자열(`"        "`)로 옴 | `null` |
| 신청 기간 `20260101 ~ 20261231`, 드물게 여러 구간(`…\N20261201 ~ 20261231`, 2건) | 첫 시작일·마지막 마감일 |
| 나이에 숫자가 아닌 값(4건) | `null`. `0`은 "제한 없음"이라 그대로 |
| 신청기간 코드 | `0057001` 특정기간 · `0057002` 상시 · `0057003` 마감 (공식 코드정의서) |

### 지역

`zipCd`(시군구 코드 5자리 콤마 목록)의 앞 2자리가 시도다. 전국 정책도 "전국"이 아니라 시군구 코드를 전부 나열하고
(정책당 238~256개), 실제로 쓰이는 시도는 16개다(전남 46·광주 29가 전남광주 `12`로 통합). 그래서 **16개 시도를 모두
포함하면 전국**으로 본다(`nationwide`, 2026-10 기준 638건). 목록 응답은 `zipCd` 대신 `regions`(`["전국"]` 또는
`["서울", "대전"]`)를 준다.

### 시군구 코드

- `policy/Regions`에 온통청년 `zipCd`에 쓰이는 시군구 코드 261개 전부의 이름이 있다(2026-10 실측). 2026년에 바뀐 코드
  (전남광주 12, 강원 51, 전북 52)는 옛 코드와 같은 순서이고, 시군구 하나만 대상인 공고 690건의 기관 이름·제목과 대조해 확인했다.
  화성시 4개 구(41591 만세구, 41593 효행구, 41595 병점구, 41597 동탄구)만 데이터에 근거가 없어 미확인이다.
- 인천 2026-07 재편(중구·동구 → 제물포구 28125·영종구 28155, 서구 → 서해구 28275·검단구 28290)과 구로 나뉜 시(화성시 41590,
  천안시 44130)의 코드는 공고에 남아 있다. 사용자 선택지(`/api/v1/regions`)에서는 빼고, 판정할 때는 새 코드·구 코드까지 넓혀 본다
  (화성시 공고 → 동탄구 주민도 해당). 그래서 고를 수 있는 시군구는 256개다.
- 프로필 `zipCd`, `actualZipCd`는 고를 수 있는 코드만 받는다.

### 대분류

공식 코드정의서의 대분류는 일자리·주거·교육·복지문화·참여권리 5개인데, 실데이터에는 새 이름(`금융･복지･문화`,
`교육･직업훈련`, `참여･기반`; 가운뎃점은 U+FF65)이 섞여 있고 한 정책에 여러 개가 콤마로 들어오기도 한다(`일자리,교육`
49건). 공식 5개로 묶어 `category_group`에 저장하고 응답은 `categories` 목록으로 준다. 원본은 `category`에 남는다.

### 사용자 프로필

- 소득은 **본인 연소득(만원) 숫자 입력**만 받는다(10/28 팀 결정). 가구 기준중위소득 %는 화면에서 받지 않아서, 그 조건을 모를 때는
  "입력하면 확인돼요" 대신 "공고 원문에서 확인해 주세요"로 안내하고 홈 배너·`missingFields`에도 넣지 않는다(`ConditionEvaluator.NOT_ASKED`).
  판정 이름은 본인 연소득 "소득", 중위소득 "가구 소득"으로 나눴다(설명이 섞이지 않게).
- 주택 소유는 "내 명의로 된 집이 있어요" 체크칸(`homeowner`)으로 받는다(10/28 추가). 체크 전이라도 주거 형태가 자가(OWN)면 소유로 본다.

- 변수는 "조건 JSON 명세" v0.1을 따른다. 온통청년에 있는 조건은 온통청년 이름·코드 그대로(`zipCd`, `jobCd`, `schoolCd`,
  `mrgSttsCd`, `plcyMajorCd`, `sbizCd`), 없는 것만 새 이름(`birthDate`→`age`, `annualIncome`, `householdMedianIncomePct`,
  `housingType`, `homeowner`, `actualZipCd`).
- 모든 항목은 선택이다. **비어 있음(null) = 아직 모름**이고 판정에서 "확인 필요"가 된다. 특화 대상 `sbizCd`는
  `null`(모름)과 `[]`(해당 없음)을 구분해 저장한다(DB에는 NULL과 빈 문자열).
- "제한없음" 코드(`0013010` 등)는 공고 쪽에서만 쓰는 값이라 사용자 값으로 받지 않는다.
- 만 나이는 저장하지 않고 생년월일로 매번 계산한다(한국 시간 기준 오늘). 생일이 지나면 저절로 바뀐다.
- 학교 장학용 항목(명세의 학교용 2차 변수): `schoolName`(학교 이름), `enrollmentStatus`(ENROLLED 재학 / ON_LEAVE 휴학 /
  INCOMING 입학 예정 / RETURNING 복학 예정 / COMPLETED 수료·졸업), `gradeLevel`(1~6), `gpa`+`gpaScale`(함께, 만점 4.0·4.3·4.5·100),
  `kosafIncomeBracket`(한국장학재단 학자금 지원구간 0~10, 0 = 기초·차상위). 명세의 `schoolId`는 학교 코드 목록이 없어 이름으로 대신한다.
  학생만 답하는 항목이라 프로필의 `missingFields`에는 넣지 않고, 학교 장학 판정의 `missingFields`에만 나온다.

### 자격 판정 (3값)

LLM 없이 규칙으로 판정한다. 조건 하나는 **참 / 거짓 / 미확인**이고, AND는 하나라도 거짓이면 거짓·모두 참이면 참·그 외 미확인,
OR는 하나라도 참이면 참·모두 거짓이면 거짓·그 외 미확인이다. 최종 결과가 참이면 **조건 일치**, 거짓이면 **조건 불일치**,
미확인이면 **확인 필요**다. 확정 불일치가 하나라도 있으면 다른 조건을 몰라도 불일치다.

온통청년 공고 → 조건(`eligibility/OntongConditions`):

| 공고 칸 | 조건 | 비고 |
|---|---|---|
| `sprtTrgtMinAge`, `sprtTrgtMaxAge` | 만 나이(신청일=오늘 기준) 범위 | 0은 제한 없음, 최대 99 이상(99·100·120·999 실측)도 상한 없음("만 19세 이상"). `sprtTrgtAgeLmtYn`은 실측상 믿을 수 없어 쓰지 않는다(Y인데 나이가 있는 공고 673건) |
| `zipCd` | 내 주민등록 시군구가 목록에 있는가 | 전국(16개 시도 포함)이면 조건 없음 |
| `earnCndSeCd=0043002` + `earnMaxAmt` | 연소득 상한 | |
| `earnCndSeCd=0043003` | 미확인(소득 조건) | 글로만 적힘 → AI 추출 전까지 확인 필요 |
| `jobCd`, `schoolCd`, `mrgSttsCd`, `plcyMajorCd` | 내 코드가 목록에 있는가 | "제한없음" 코드가 있으면 조건 없음 |
| `sbizCd` | 내 특화 대상 중 하나라도 목록에 있는가 | 모름(null)은 미확인, 해당 없음([])은 불일치 |
| `addAplyQlfcCndCn` | 미확인(추가 자격 조건) | 원문을 근거로 보여준다 |
| `ptcpPrpTrgtCn` | NOT 미확인(참여 제한 대상) | |

해석하지 못한 조건이 남으면 다른 조건이 다 맞아도 조건 일치로 단정하지 않는다. 그래서 신청 가능한 공고의 약 40%(글로 된
추가 자격 조건이 있는 공고)는 AI 추출이 붙기 전까지 확인 필요로 나온다. 프로필이 없으면(헤더 없음 포함) 빈 프로필로 판정해
조건이 있는 공고는 모두 확인 필요가 된다. 실데이터 1,043건 판정에 약 0.1초.

학교 장학 공고 → 조건(`eligibility/HarvesterConditions`). Harvester는 항목마다 `*_rule_status`(restricted / unrestricted /
unknown)를 준다. restricted이고 값이 확실할 때만 비교하고, 정리 안 된 글 조건(`residual_conditions_text`)은 미확인으로 둔다.

| Harvester 칸 | 조건 | 비고 |
|---|---|---|
| `academic_levels` | 내 학력 코드(`schoolCd`)가 해당하는가 | 학부 → 대학 재학·대졸 예정, 대학원 → 석·박사, 고교 → 고교 재학·고졸 예정. 입학 예정자를 받으면 고교 졸업(예정)·대학 졸업도 인정 |
| `enrollment_states` | 내 학적(`enrollmentStatus`)이 목록에 있는가 | 목록에 `other`가 섞이면 내 값이 없을 때 불일치 대신 미확인 |
| `grade_min`, `grade_max` | 학년 범위 | |
| `gpa_threshold`, `gpa_scale` | 평점 이상 | 만점이 다르면 환산하지 않고 미확인(학교마다 환산 규칙이 다름) |
| `income_max`(`kosaf_support_bracket`) | 학자금 지원구간 이하 | 다른 소득 기준은 미확인 |
| `residency_region_codes` | 주민등록 시도·시군구 | "인천", "춘천시", "울산 남구", "용인시"(구 전부) 같은 이름을 코드로 바꿔 비교. 동 단위("석남1동")나 여러 곳에 있는 이름("남구", "고성군")은 미확인. 실측 이름 72종 중 59종(사용 341/367회)을 바꾼다. 보호자 거주도 인정(`self_or_parent`)하면 내 주소가 달라도 불일치 대신 미확인 |
| `eligible_universities` | 내 학교 이름 | "인하대"·"인하 대학교"도 같게 본다. "국내 4년제 대학"처럼 범위로 적힌 건 미확인 |
| `residual_conditions_text` | 미확인(기타 자격 조건) | 실측상 거의 모든 장학에 있어 대부분 확인 필요로 나온다 |

### 글 조건 AI 변환

온통청년 글 조건(소득 기타 `earnEtcCn`, 추가 자격 `addAplyQlfcCndCn`, 참여 제한 `ptcpPrpTrgtCn`)은 원래 "원문 확인 필요"였다.
PwC LLM(`openai.gpt-4.1-mini`)으로 판정 규칙으로 바꿔 `policies.ai_conditions`에 저장하고, 판정 때 원래 Unparsed 대신 쓴다(`eligibility/AiConditionConverter`).

- **원칙: 틀린 "불일치"보다 "확인 필요"가 낫다.** 규칙은 "만족 못 하면 신청할 수 없는" 필수 조건만. AI 답은 그대로 믿지 않고 코드로 다시 검사해서 하나라도
  안 맞으면 그 조건은 확인 필요로 남긴다. 확신이 낮거나 검사에서 떨어진 게 있으면 `openai.gpt-4.1`이 한 번 더 본다.
- 규칙 변수: 나이, 거주 지역(이름 → 시도·시군구 코드), 본인 연소득, 가구 기준중위소득 %, 무주택, 주거 형태, 학력, 결혼 여부. 취업 상태는 말과 코드가 잘 안 맞아서 뺐다.
- 검사(실데이터에서 나온 실수를 막는 것들, `AiConditionConverterTest`):
  근거 구절이 원문에 그대로 있어야 함 / "연 매출"·"중위소득 %"를 연소득으로 읽지 않기 / "4~12세 아동", "생후 24개월"은 신청자 나이가 아님 /
  규칙의 숫자가 근거에 있어야 함 / "또는·혹은·~거나·전입하고자·우대·한함·가산"이 근처에 있으면 필수 조건이 아님 / "대구 외 타지역", 학교·기관 소재지는 거주 조건이 아님 /
  지역 이름·코드 이름이 근거에 있어야 함("미취업자"를 미혼으로 읽은 경우) / 참여 제한 글에서는 무주택만 / 유형별 기준이 여럿이면 가장 느슨한 값만, "소득 무관"이 있으면 소득 규칙 없음 /
  "조건 없음"은 원문이 뻔히 빈 글("해당없음", "무관", "미정")일 때만(아니면 "트랙별 상이" 같은 글이 "조건 일치"로 잘못 나감)
- 판정 화면의 근거는 "AI가 원문에서 읽음: …"으로 보여 준다. 변환할 때의 글 해시(`ai_conditions_hash`)가 지금 글과 다르면 쓰지 않고 원래대로 돌아간다.
  공고 변경 비교(`PolicyDiff`)는 AI 없이 원래 조건으로 한다 — AI 결과가 붙은 걸 공고 변경으로 알리지 않게.
- 모델 답 원문도 저장해서, 검사 규칙을 고치면 `/recheck`로 다시 검사한다(비용 없음). 프롬프트를 바꾸면 `PROMPT_VERSION`을 올린다(전부 다시 변환).
- **학교 장학**(10/28)도 같은 방식: Harvester가 정리하지 못한 기타 자격 조건 글(`residual_conditions_text`)을 바꾼다. 학교용 규칙 변수를 더했다 —
  학년(`gradeLevel`, "진학 예정" 학년은 안 씀), 평점(`gpa`, 기준과 만점 함께, 만점은 4.0·4.3·4.5·5.0), 학자금 지원구간(`kosafIncomeBracket`, "구간"이 근거에 있어야),
  학적(`enrollmentStatus`). "만 30세 미만"은 29세 이하, "초과"는 한 칸 위로(실측: AI가 미만을 이하로 읽음). 신청 가능 25건 중 글이 있는 13건 변환,
  규칙은 평점·학자금 구간·나이·학년 5개(나머지는 국적·추천서·이수 학점 같은 확인 필요).
- 실측(10/27, 신청 가능 1,226건 중 글 조건 있는 562건): 562건 변환, 실패 0, 약 125만 토큰(전부 다시 해도 몇 달러). 규칙이 생긴 공고 46건(규칙 56개),
  글 조건이 다 풀려 끝까지 판정되는 공고 142건(그중 134건은 "해당없음" 같은 빈 글). 나머지 420건은 예술인·병역·창업 업력처럼 프로필로 못 보는 조건이라 확인 필요로 남는다.
  예시 프로필 두 개에서 바뀐 판정 16건을 하나씩 확인 — 모두 맞음(부산 주거 지원 7건은 무주택·중위소득으로 불일치, 빈 글 5건은 일치).

### 지원 금액 (카드의 "월 최대 20만원")

모든 공고 응답(목록·맞춤·검색 카드, 상세)에 `support`가 붙는다:
`{label: "월 최대 30만원" | null, amountWon, period: MONTH|YEAR|SEMESTER|ONCE|TOTAL, upTo, kind: CASH|LOAN|INTEREST|VOUCHER|SERVICE|OTHER, summary: "창업 교육·컨설팅", source: SCHOOL|AI|TEXT}`.
화면은 `label`이 있으면 금액, 없으면 `summary`를 보여 주면 된다.

- 학교 장학: Harvester가 정리한 금액(`benefits.amount_max`, 지급 주기) — "학기당 350만원", "등록금 전액", 학자금 대출은 "대출 연 최대 200만원"
- 온통청년: 지원 내용 글에서 AI(`openai.gpt-4.1-mini`)가 1인당 받는 금액·주기·한 줄 요약을 뽑고 코드가 검사(`support/AiSupportExtractor`):
  금액이 근거 구절에 실제로 적혀 있어야 하고(한국어 금액 읽기 `Support.amounts`: "5백만원", "1.8백만원", "40,000천원", "1.5억원"), 근거는 원문에 있어야 하고,
  사업 예산·기업 지원액·보험 보장 한도는 버린다. 근거에 금액이 하나뿐인데 AI 숫자가 다르면 원문 숫자를 쓴다("연60백만원"을 600만원으로 읽은 경우).
  이자 지원은 "2억원 한도 이자 지원"(대출 원금 한도라서), 바우처는 "20만원 상당".
- 지원 내용이 바뀌면(`ai_support_hash`) 옛 결과는 안 쓰고 첫 줄로 돌아간 뒤 다시 뽑는다.
- 실측(10/27): 신청 가능 온통청년 1,213건 모두 뽑음, 실패 0, 약 86만 토큰. 금액 332건, 한 줄 요약 1,211건. 무작위 40건·5천만원 이상 18건 수동 확인.

### 자연어 검색

- AI(`openai.gpt-4.1-mini`)는 문장을 알아듣는 데만 쓰고, 공고는 코드가 찾는다(`search/`). 같은 문장은 해석을 기억(최근 500개)해서 두 번째부터 0.1초.
  AI가 안 되면(키 없음·한도·오류) 띄어쓰기로 나눈 단어로 찾는다 — 검색이 멈추지 않게. 실측 1.2~2.5초(첫 요청 ~6초).
- 해석: **찾는 것**(월세, 학원비, 창업)과 **누구**(대학생, 신혼부부)를 나눈다. 찾는 것 단어 묶음 하나는 공고에 나와야 하고, 누구는 점수만 더한다
  (자격은 프로필 판정이 본다). 찾는 것 없이 상황만 말하면("결혼했는데 받을 수 있는 거") 누구로 찾는다. "지원", "청년" 같은 흔한 말은 뺀다.
- 점수: 제목 3, 키워드·분류 2, 설명·지원 내용·자격 글 1. 지역을 말했으면 그 지역 공고 +2(전국 공고보다 앞). 분류·지역·나이·마감 임박은 거른다.
- 정렬: 찾는 것 묶음이 몇 개 나왔나 → 점수 → 판정(일치·확인 필요·불일치) → 마감 임박.
- **사용량 제한**(배포하면 누구나 검색할 수 있어서 PwC 주간 한도를 지키려고): 한 사람(IP+기기) 1분 30번 넘으면 429, 같은 IP 전체 1분 300번(행사장 와이파이처럼
  여러 사람이 한 IP를 써도 막히지 않게 넉넉히), 한 사람 1시간 AI 해석 30번 넘으면 단어 검색으로(기억해 둔 문장은 그대로 AI 해석). 서버 전체 하루 토큰 한도
  (`PWC_LLM_DAILY_TOKEN_LIMIT`)를 넘으면 모든 AI 기능이 다음 날까지 쉰다. 제한은 서버 메모리에서 센다(서버 한 대 기준, 다시 켜면 처음부터).
  진짜 사용자 IP는 앞단 프록시(사설 IP)가 준 `X-Forwarded-For`로 본다(`server.forward-headers-strategy: native`).
- 문장에 지역이 없으면 내 프로필 지역(시도)으로 찾는다(`understood.regionFromProfile`, 화면 칩 "내 지역 · 인천"). 칩을 ×로 빼면 같은 `q`에
  `noRegion`·`noCategory`·`noAge`·`noClosingSoon=true`, 단어 묶음은 `drop=대표어`를 붙여 다시 부른다(디자인 06).
- 처음엔 "대학"이 "대학중점연구소"에 걸리거나 "지원"이 모든 공고에 걸려서 엉뚱한 공고가 위로 왔다 → 찾는 것/누구를 나누고 흔한 말을 빼서 고침.

### 학교 장학 수집

- 목록(`/v1/opportunities`)은 요약만 주고 자격·기간·서류는 상세에 있다. 상세는 키당 분당 요청 한도가 있어서 필요한 것만 받는다.
- **처음(읽은 위치 없음)이나 `?full=true`**: 목록 전체를 보고 처음 보는 공고·`version_id`가 바뀐 공고의 상세를 받는다. 처음 보는 공고 중
  지난 학년도보다 이전 것(2026년이면 2024학년도 이하)은 이미 끝난 장학이라 받지 않는다. 목록에서 빠진 공고는 사라짐(`source_removed`)으로
  표시한다. 끝나면 변경 목록의 마지막 위치(`event_seq`)를 `sync_cursors`에 저장한다.
- **그다음부터**: 변경 목록(`/v1/changes`)에서 저장한 위치 뒤의 변경만 본다. 한 공고에 변경이 여러 번이면 마지막 것만 처리한다.
  `visible_after=false`(합쳐짐·비공개·삭제)면 사라짐으로 표시하고, 새로 생기거나 버전이 바뀐 공고만 상세를 받는다. 다시 공개되면 되살린다.
  상세 받기가 실패하면 그 앞까지만 위치를 저장해 다음 수집 때 이어서 받는다.
- 사라짐으로 표시한 공고는 지우지 않는다. 목록·맞춤에서만 빠지고, 준비함에 담긴 공고의 상세는 계속 열린다.
- 실측(2026-10-12): 빈 DB 첫 수집은 목록 1,630건 중 872건 건너뜀, 상세 758건(약 35분). 이미 받아 둔 상태에서 전체 비교 12초, 변경만 0.25초.
  신청 가능 15건.
- 신청 기간은 `application_windows` 중 신청(application·additional_application)의 가장 이른 시작일~가장 늦은 마감일.
  상태가 closed·cancelled 등이면 마감, 날짜가 있으면 특정기간, 없으면 상시로 온통청년 신청기간 코드에 맞춘다(목록 조회가 같은 규칙).
- 대분류는 `교육`, 원본 분류는 `장학금`. 거주지 조건이 시도 이름이면 화면 지역에도 쓴다.
- 서류는 Harvester가 정리한 `required_documents`를 그대로 체크리스트로 쓴다(`checklist_documents`). 필수가 아닌 것(해당자·선택)은
  선택 서류. 상세 화면용 글(`submissionDocuments`)도 만들어 둔다.

### 준비함과 서류 체크리스트

- 관심 저장할 때 온통청년 제출 서류 글(`sbmsnDcmntCn`)을 체크리스트로 쪼갠다(`application/DocumentListParser`). 줄 단위로 나누고,
  번호 없는 줄은 괄호 밖 쉼표로 다시 나눈다. `※`·`☞` 안내문, "붙임파일 확인"·"별도 문의" 같은 안내, `○ 공통` 같은 소제목,
  콜론 뒤 설명("주민등록초본(최근 5년): 행정정보…")은 뺀다. `[선택]`, `(해당자만)`, "해당자에 한함 :"은 선택 서류로 표시한다.
- 실측: 제출 서류가 적힌 공고 1,029건 중 701건(68%)에서 항목이 나온다. 나머지는 "붙임파일 확인", "별도 문의",
  "해당없음"처럼 서류 이름이 없는 글이라 빈 목록이고, 사용자가 직접 서류를 추가한다. 서류 이름이 있는데 못 뽑는 공고는 10건 안팎.
- 소제목("신청인 제출 서류", "가) 일반근로자"), 각주("*공고일 이후 발급분만 인정"), 안내 문장("담당자 확인필요")은 뺀다. 상세 응답은 원문(`submissionDocumentsRaw`)도 함께 준다.
- 체크리스트를 다 채워도 제출 완료가 아니다. 신청 완료(`APPLIED`)는 실제 제출 후 사용자가 직접 표시한다(기획안 7쪽).
- 다른 사람의 준비함은 404로 보인다. `DELETE /api/v1/me`는 준비함도 지운다.

### 공고 변경과 알림

- 수집할 때 원본이 바뀐 공고는 덮어쓰기 직전에 화면에 보이는 값을 비교한다(`notification/PolicyDiff`): 공고 제목, 신청 시작일·마감일,
  신청 상태, 지원 내용, 제출 서류, 신청 방법, 신청 링크, 자격 조건. 자격 조건은 판정 엔진의 설명("만 19~34세")끼리 조건 이름별로 비교해서
  온통청년·학교 장학 모두 같은 문장으로 나온다. 공백·줄바꿈·HTML 기호(`&gt;`) 차이는 변경으로 보지 않는다.
- 바뀐 값이 있으면 `policy_changes`에 전/후를 남기고, 그 공고를 준비함에 담은 사람마다 `notifications`를 만든다. 조회수·수정 시각만 바뀌면
  남기지 않는다. 공고 내림(학교 장학 사라짐)은 바뀐 값이 없어도 남긴다.
- 변경 종류: 학교 장학은 Harvester 변경 목록의 `event_kind`(extended·corrected·cancelled)를 쓰고, 나머지는 짐작한다 — 마감으로 바뀜 = 마감,
  마감에서 다시 열림 = 다시 게시, 마감일이 늦어짐 = 마감 연장, 그 밖 = 내용 변경.
- 실측(10/7 → 10/23 온통청년): 같은 공고 3,228건 중 원본이 바뀐 482건, 화면에 보이는 값이 바뀐 29건(마감 11, 마감 연장 3, 다시 게시 2, 내용 변경 13).
- **새 맞춤 공고**(매일 아침 8시): 지난번 이후 새로 저장된 신청 가능 공고를 프로필이 있는 사람마다 판정해, 설정한 판정(기본 조건 일치·확인 필요)에
  해당하는 것을 하루 1회 한 알림으로 묶는다(최대 20건, 조건 일치 먼저). 처음 실행은 기준 시점(`sync_cursors`의 `new-matches`)만 잡는다 —
  기존 공고 수천 건이 새 공고로 쏟아지지 않게. 기준 시각은 공고 저장 시각과 같은 서버 시계를 쓴다(서버가 UTC여도 빠뜨리지 않게).
- **마감 임박**(매일 아침 8시): 준비함의 관심·준비 중 공고(신청 완료·마감 처리·공고 내림 제외)가 마감 7·3·1·0일 전(설정)이면 알린다.
- 같은 날 두 번 돌려도 알림이 늘지 않는다(`dedupe_key`). 설정에서 끈 종류는 만들지 않는다(공고 변경 알림 포함).
- 실측(10/24): 온통청년 하루 재수집에서 원본 해시가 바뀐 480건은 모두 화면에 안 보이는 값만 바뀌어 변경 기록·알림 0건.
- **제출 서류가 바뀌면 체크리스트에 반영**: 새 서류는 추가하고 `policyChange: "ADDED"`(새로 추가), 빠진 서류는 지우지 않고(이미 체크했을 수 있어서)
  `"DROPPED"`(공고에서 빠짐)로 표시한다. 띄어쓰기·대소문자만 다르면 같은 서류. 사용자가 추가한 항목은 건드리지 않고, 빠졌던 서류가 다시 들어오면 표시를 뗀다.
  준비함 카드의 `documentChanges`는 표시된 항목 수("서류 변경" 배지).
- **알림 메일**: 알림 설정에서 이메일 받기(`email`)를 켠, 이메일 인증을 마친 계정에 하루 한 번(아침 8시 매일 알림 뒤) 아직 안 보낸 알림을 한 통으로 묶어
  보낸다(`NotificationEmailService`). 방해금지 시간이면 다음 날로 미루고, 보낸 알림은 `emailed_at`으로 표시해 다시 보내지 않는다. 3일 넘은 알림은 메일로 보내지 않는다.
  메일의 "알림함에서 보기" 링크는 `APP_WEB_URL`(프론트 주소) + `/notifications`. 보내는 주소는 `MAIL_USERNAME`(지금 `youthbenefit.guide@gmail.com`).
- 앱 밖 알림은 메일만 있다(웹 푸시·카톡은 없음). 카톡·이메일 발송은 채널이 정해지면 알림 생성 시점에 붙인다.

### 공고 직접 등록

- 같은 `policies` 테이블에 `source = USER`, `owner_id`(등록한 사람)로 저장한다. 공개 목록·맞춤·새 공고 알림 조회(`PolicyRepository.OPEN`)는
  `owner_id is null`만 보므로 나오지 않고, 상세·판정·준비함 담기는 주인이 아니면 404.
- 서류 목록은 학교 장학처럼 정리된 목록(`checklist_documents`)으로 저장해 체크리스트가 그대로 만들어진다.
- 로그인하면 계정으로 옮겨지고, 내 데이터 삭제·탈퇴 때 같이 지워진다. 링크 불러오기·캡처 읽기(WF07 2차)는 AI가 이 입력값을 채워 주는 방식으로 붙인다.

### 알려진 원본 문제

- 같은 공고가 다른 `plcyNo`로 다시 등록된 경우가 있다(예: 전남광주 신혼부부 전세자금 대출이자 지원 505·522번).
  제목(띄어쓰기 무시)과 주관기관(`sprvsnInstCdNm`)이 같으면 같은 공고로 보고, 목록·맞춤 공고에는 신청 가능한 것 중
  가장 최근 등록(`frstRegDt`)된 하나만 보여 준다. 나머지도 지우지 않아서 상세·준비함에서는 그대로 열린다.
  기관이 다르면 이름이 같아도 지역이 다른 별개 사업이라 묶지 않는다. 실측: 신청 가능 1,043건 → 1,015건.
- 서산시 공고 16건이 태안군 코드(44825)로 등록돼 있다(서산시는 44210). 원본 그대로 판정하므로 서산시 주민에게는 불일치로 나온다.
- 주관 기관 지역과 신청 가능 지역이 다를 수 있다(은평구 행사인데 전국 대상). 지역 판정은 기관명이 아니라 `zipCd`로 한다.

## 남은 작업

**데이터**
- 학교 장학: 동 단위 거주지 판정, `residual_conditions_text` AI 변환, Harvester `/v1/changes`로 바뀐 것만 받기
- 공고 버전 이력(현재는 최신 값만 유지)

**기능**
- 글 조건 AI 변환 2차: 학교 장학 `residual_conditions_text`, 프로필에 없는 변수(거주 기간, 재산, 병역 등) 추가 검토
- 서류 마감·발급일 제한 같은 복수 일정(WF05), 알림 외부 발송(카톡·이메일), 카카오·이메일 계정 연결

**운영**
- 배포, CI
- 배포 전: `JWT_SECRET` 넣기, Gmail 앱 비밀번호(`MAIL_*`) 넣기, 카카오 Client Secret 재발급, 카카오 콘솔·`KAKAO_REDIRECT_URIS`에 배포 주소 추가

## 참고 자료

- [온통청년 오픈 API 제공목록·코드정의서](https://www.youthcenter.go.kr/cmnFooter/openapiIntro/oaiDoc)
- [policy-harvester](https://github.com/samsamosip/policy-harvester)
