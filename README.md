# Youth Benefit Backend

청년혜택 길잡이의 Spring 백엔드다. 온통청년 청년정책 API 전체를 주기적으로 수집해 원본 그대로 보관하고,
화면에 필요한 값(지역, 대분류)을 정리해 "지금 신청할 수 있는 공고" API로 제공한다. 앞으로 사용자 프로필,
자격 판정(조건 일치 / 확인 필요 / 조건 불일치), 준비함, 알림을 이 서버에 붙인다.

가장 중요한 원칙: **온통청년 필드는 이름과 코드값을 그대로 저장한다.** 화면용으로 계산한 값은 별도 칸에 두고
원본은 바꾸지 않는다. 학교 장학 공고는 [policy-harvester](https://github.com/samsamosip/policy-harvester)가
수집·구조화하고, 이 서버는 그 API를 받아 합친다(예정).

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

- 공고 목록: `http://localhost:8080/api/v1/policies?size=5`
- **API 문서(Swagger)**: `http://localhost:8080/docs` — 모든 API의 요청·응답 모양을 보고 Try it out으로 바로 호출해 볼 수 있다
- JDK 21이 없어도 Gradle toolchain이 자동으로 받는다. VS Code는 `.vscode/extensions.json`의 추천 확장을 설치한다.

## 설정

`.env`(비밀값 포함, git ignore 대상)에서 읽는다. 비밀값(API key)은 로그·문서·commit에 복사하지 않는다.

| 키 | 기본값 | 비고 |
|---|---|---|
| `ONTONG_API_KEY` | (없음) | 온통청년 오픈 API 인증키. youthcenter.go.kr 마이페이지에서 발급. 없으면 수집 API가 502 |
| `ONTONG_SYNC_SCHEDULED` | `false` | `true`면 6시간마다 자동 수집(`ontong.sync.cron`, Asia/Seoul) |
| `HARVESTER_BASE_URL`, `HARVESTER_API_KEY` | (없음) | policy-harvester API. 관리자 화면 API key 메뉴에서 발급. **아직 코드에서 쓰지 않는다** |
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

인증은 아직 없다. 수집 API(`/admin`)도 로그인 기능이 생기면 관리자만 호출하도록 막는다.

| 경로 | 내용 |
|---|---|
| `GET /api/v1/policies?category=&page=&size=` | 지금 신청할 수 있는 공고. 마감 임박순, 마감일 없는(상시) 공고는 뒤로. `size` 최대 100 |
| `GET /api/v1/policies/{id}` | 공고 상세. 자격 조건은 온통청년 코드 그대로(`jobCd`, `schoolCd` 등) |
| `POST /api/v1/admin/ontong/sync` | 온통청년 전체 수집. 결과 `{fetched, created, updated, unchanged}` |
| `GET /api/v1/codes` | 온보딩 선택지(`jobCd`, `schoolCd`, `mrgSttsCd`, `plcyMajorCd`, `sbizCd`, `housingType`, `interestCategories`)의 code·label |
| `GET /api/v1/me/profile` | 내 프로필. 만 나이(`age`), 주민등록 시도(`residenceSido`), 아직 비어 있는 항목(`missingFields`) 포함. 없으면 404 |
| `PUT /api/v1/me/profile` | 내 프로필 전체 저장. 보내지 않은 항목은 "모름"으로 비워진다 |
| `DELETE /api/v1/me` | 내 데이터 삭제 (프로필과 준비함) |
| `GET /api/v1/me/matches?category=&verdict=&includeNoMatch=&page=&size=` | 맞춤 공고(WF02). 신청 가능한 공고를 내 프로필로 판정. 기본은 조건 일치 + 확인 필요, 마감 임박순. `counts`는 전체 판정 건수 |
| `POST /api/v1/me/applications` | 준비함에 관심 저장(WF05). `{"policyId": 185}`. 공고 제출 서류로 체크리스트를 만든다. 이미 있으면 200 |
| `GET /api/v1/me/applications` | 준비함 목록. 카드마다 상태, 마감 D-day, 준비율 |
| `GET /api/v1/me/applications/{id}` | 준비함 상세. 체크리스트, 제출 서류 원문, 신청 링크 |
| `PATCH /api/v1/me/applications/{id}` | 상태 변경 `{"status": "PREPARING"}` (INTERESTED / PREPARING / APPLIED) |
| `DELETE /api/v1/me/applications/{id}` | 관심 해제 |
| `POST`·`PATCH`·`DELETE /api/v1/me/applications/{id}/items[/{itemId}]` | 서류 추가 `{"content"}` · 체크 `{"checked": true}`·이름 수정 · 삭제 |
| `GET /api/v1/policies/{id}/eligibility` | 공고 하나의 조건별 판정(WF03): 공고 요구사항, 내 값, 참/거짓/미확인, 근거, 채우면 다시 판정되는 항목 |

`/me` 경로는 로그인이 생기기 전까지 `X-Anonymous-Id` 헤더(UUID)로 사람을 구분한다. 프론트가 처음 방문 때
UUID를 만들어 localStorage에 두고 매 요청에 보낸다. 헤더가 없거나 UUID가 아니면 400.

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
| `policy/Policy`, `PolicyRepository` | 공고 엔티티, "지금 신청 가능" 조회 |
| `policy/Regions`, `Categories` | 시도 계산·전국 판정, 대분류 통일 규칙 |
| `policy/PolicyController` | 목록·상세 API |
| `profile/` | 사용자 프로필 엔티티·저장·검증, `/me` API |
| `code/OntongCodes`, `CodeController` | 공식 코드정의서의 사용자 선택 코드와 이름, 선택지 API |
| `application/` | 준비함: 관심 저장, 상태, 서류 체크리스트, 제출 서류 글 → 항목 쪼개기 |
| `eligibility/` | 3값 판정: 조건 나무(`Condition`), 판정기(`ConditionEvaluator`), 온통청년 → 조건 변환, 맞춤 목록 API |
| `common/ApiExceptionHandler` | 오류 응답 형식 통일 (`{message, errors}`) |
| `common/WebConfig` | CORS 허용 주소, API 문서 제목 |
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

### 대분류

공식 코드정의서의 대분류는 일자리·주거·교육·복지문화·참여권리 5개인데, 실데이터에는 새 이름(`금융･복지･문화`,
`교육･직업훈련`, `참여･기반`; 가운뎃점은 U+FF65)이 섞여 있고 한 정책에 여러 개가 콤마로 들어오기도 한다(`일자리,교육`
49건). 공식 5개로 묶어 `category_group`에 저장하고 응답은 `categories` 목록으로 준다. 원본은 `category`에 남는다.

### 사용자 프로필

- 변수는 "조건 JSON 명세" v0.1을 따른다. 온통청년에 있는 조건은 온통청년 이름·코드 그대로(`zipCd`, `jobCd`, `schoolCd`,
  `mrgSttsCd`, `plcyMajorCd`, `sbizCd`), 없는 것만 새 이름(`birthDate`→`age`, `annualIncome`, `householdMedianIncomePct`,
  `housingType`, `homeowner`, `actualZipCd`).
- 모든 항목은 선택이다. **비어 있음(null) = 아직 모름**이고 판정에서 "확인 필요"가 된다. 특화 대상 `sbizCd`는
  `null`(모름)과 `[]`(해당 없음)을 구분해 저장한다(DB에는 NULL과 빈 문자열).
- "제한없음" 코드(`0013010` 등)는 공고 쪽에서만 쓰는 값이라 사용자 값으로 받지 않는다.
- 만 나이는 저장하지 않고 생년월일로 매번 계산한다(한국 시간 기준 오늘). 생일이 지나면 저절로 바뀐다.

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

### 준비함과 서류 체크리스트

- 관심 저장할 때 온통청년 제출 서류 글(`sbmsnDcmntCn`)을 체크리스트로 쪼갠다(`application/DocumentListParser`). 줄 단위로 나누고,
  번호 없는 줄은 괄호 밖 쉼표로 다시 나눈다. `※`·`☞` 안내문, "붙임파일 확인"·"별도 문의" 같은 안내, `○ 공통` 같은 소제목,
  콜론 뒤 설명("주민등록초본(최근 5년): 행정정보…")은 뺀다. `[선택]`, `(해당자만)`, "해당자에 한함 :"은 선택 서류로 표시한다.
- 실측: 제출 서류가 적힌 공고 1,029건 중 758건(74%)에서 항목이 나온다. 나머지는 대부분 "붙임파일 확인"이라 빈 목록이고,
  사용자가 직접 서류를 추가한다. 상세 응답은 원문(`submissionDocumentsRaw`)도 함께 준다.
- 체크리스트를 다 채워도 제출 완료가 아니다. 신청 완료(`APPLIED`)는 실제 제출 후 사용자가 직접 표시한다(기획안 7쪽).
- 다른 사람의 준비함은 404로 보인다. `DELETE /api/v1/me`는 준비함도 지운다.

### 알려진 원본 문제

- 같은 공고가 다른 `plcyNo`로 두 번 등록된 경우가 있다(예: 전남광주 신혼부부 전세자금 대출이자 지원). 아직 합치지 않는다.
- 주관 기관 지역과 신청 가능 지역이 다를 수 있다(은평구 행사인데 전국 대상). 지역 판정은 기관명이 아니라 `zipCd`로 한다.

## 남은 작업

**데이터**
- policy-harvester 장학 데이터 합치기 (응답 형식 확정 후)
- 중복 공고 정리, 공고 버전 이력(현재는 최신 값만 유지)

**기능**
- 글로 된 조건(`earnEtcCn`, `addAplyQlfcCndCn`, `ptcpPrpTrgtCn`)을 AI로 조건 나무로 바꾸기 — 판정 엔진은 같은 `Condition` 나무를 그대로 판정한다
- 프로필 항목 하나만 고치는 PATCH(WF03에서 부족한 정보 입력), 지역 선택용 시군구 목록 API
- 서류 마감·발급일 제한 같은 복수 일정(WF05), 알림(WF07), 로그인(현재 `X-Anonymous-Id` 임시)과 관리자 권한

**운영**
- 배포, CI

## 참고 자료

- [온통청년 오픈 API 제공목록·코드정의서](https://www.youthcenter.go.kr/cmnFooter/openapiIntro/oaiDoc)
- [policy-harvester](https://github.com/samsamosip/policy-harvester)
