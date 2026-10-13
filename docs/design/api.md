# 일일 정산 기록 서비스 — API 명세서

Oct 6, 2026 | @최승혁

## 1. 개요

엔드포인트는 사용자용 `/api/**` 23개, 운영자용 `/admin/**` 3개, 가용성 점검용 1개로 모두 27개다. 금액은 소수 2자리 문자열, 날짜는 `YYYY-MM-DD` 문자열로만 주고받고, 오류는 한 가지 형식에 한국어 문구를 담아 돌려준다.

| 항목 | 내용 |
| --- | --- |
| 문서명 | 일일 정산 기록 서비스 API 명세서 |
| 버전 | v0.3 |
| 작성일 | 2026-10-06 |
| 작성자 | (본인) |
| 기준 문서 | 요구사항명세서 v1.3 (2026-10-06), 시스템 아키텍처 설계서 v0.6 (2026-10-06), DB 설계서 v0.2 (2026-10-06), 화면 설계서 v0.2 (2026-10-06) |
| 상태 | 초안. DB 설계서, 화면 설계서와 대조를 마쳤다. 열린 확인 사항 4건과 다른 문서에 반영할 사항은 7장에 모았다 |

> **문서의 역할** — 경로, 파라미터, 요청과 응답 필드, 상태 코드, 오류 코드는 이 문서가 기준이다. 무엇을 만들지는 요구사항명세서(이하 SRS)가, 어떤 구성으로 만들지는 시스템 아키텍처 설계서(이하 설계서)가 정한다. 문서가 어긋나면 SRS, 설계서, 이 문서 순으로 따르고 이 문서를 고친다.

> **근거 표기** — 엔드포인트와 규칙에는 근거가 된 요구사항 ID(FR, NFR, CAL, VAL)나 설계서의 결정 ID(AD, DI)와 절 번호를 붙였다. 설계서가 정하지 않아 이 문서가 정한 필드명, 기본값, 오류 코드는 SD-xx로 표기하고 7장에 모았다.

### 1.1 범위

| 구분 | 포함 | 호출하는 주체 |
| --- | --- | --- |
| 사용자 API | 세션과 인증, 계정, 시드 이력, 일괄 입력, 정산 기록, 거래내역, 통계 | 같은 출처의 화면(PWA) |
| 운영자 API | 초대 링크 발급, 기기 목록, 기기 폐기 | 운영자 (curl 등 서버 밖 도구) |
| 가용성 점검 | 헬스 체크 | 외부 점검 서비스 (5분 간격) |
| MVP 이후 | 금액과 메모 검색, CSV 내보내기, 연도별 집계 | 수용 방식만 5.9에 둔다 |

### 1.2 참고 문서

| 문서 | 버전 | 이 문서가 참조하는 곳 |
| --- | --- | --- |
| 요구사항명세서 | v1.3 | 3장 기능 요구사항, 4.2 계산 규칙, 4.3 무결성 규칙 |
| 시스템 아키텍처 설계서 | v0.6 (프로젝트 파일) | 5장 인증, 7장 날짜와 시간대, 8장 금액과 수익률, 9장 API 설계 |
| DB 설계서 | v0.2 | 6.3 제약 위반과 오류 코드, 6.4 잠금, 7장 조회 SQL |
| 화면 설계서 | v0.2 | MVP 화면 8개의 호출 API, 표시 항목, 상태와 예외 |

## 2. 공통 규약

모든 API는 화면과 같은 출처에서 HTTPS로만 제공하고, 사용자 API는 기기 토큰 쿠키로, 운영자 API는 운영자 시크릿 헤더로 인증한다. 금액은 요청과 응답 모두 문자열이라 어느 구간에서도 부동소수점을 거치지 않는다.

### 2.1 요청과 응답 형식

| 항목 | 규약 | 근거 |
| --- | --- | --- |
| 출처 | `https://{도메인}` 하나. 화면(PWA)과 API가 같은 출처라 CORS를 열지 않는다 | AD-02, NFR-301 |
| 경로 | 사용자 API `/api/**`, 운영자 API `/admin/**`, 헬스 체크 `/actuator/health`. 화면과 API가 한 jar로 함께 배포되므로 경로에 버전을 넣지 않는다 | SD-01 |
| 본문 형식 | `application/json; charset=utf-8`. 본문이 있는 쓰기 요청의 형식이 다르면 415. 본문이 없는 요청(운용 종료, 로그아웃, 삭제 등)은 형식을 검사하지 않는다 | 설계서 9.1, SD-02 |
| 정의되지 않은 필드 | 요청 본문에 명세에 없는 필드가 있으면 400. 정산 기록 수정에 `settlementDate`를 보내는 식의 실수를 조용히 넘기지 않기 위해서다 | DI-07, SD-02 |
| 성공 응답 | 리소스를 감싸지 않고 그대로 준다. 목록은 이름 있는 배열 필드(`accounts`, `items` 등)에 담는다. 값이 없는 필드는 빼지 않고 `null`로 보낸다 | SD-02 |
| 필드 이름 | camelCase. 식별자는 JSON 숫자다 | — |
| 문자열 | 글자 수는 유니코드 코드 포인트로 세어 DB의 `VARCHAR` 길이와 기준을 맞춘다. 계정명과 메모는 앞뒤 공백을 지운 뒤 검사하고, 지운 결과가 빈 메모는 `null`로 저장한다 | VAL-04, VAL-05, SD-03 |
| 부분 수정(PATCH) | 보낸 필드만 바꾼다. `null`과 생략은 변경 없음이며, 메모를 지우려면 빈 문자열을 보낸다 | SD-03 |
| 캐시 | API 응답에는 캐시 금지 헤더를 붙인다(Spring Security 기본값 유지). 서비스 워커도 `/api/**`를 캐시하지 않는다 | AD-03 |

### 2.2 값 형식

| 값 | JSON | 예 | 규칙 | 근거 |
| --- | --- | --- | --- | --- |
| 금액 | 문자열 | `"1234.56"` | 요청은 `^\d{1,13}(\.\d{1,2})?$`에 맞아야 하고, JSON 숫자로 온 금액은 400이다. 응답은 항상 소수 2자리이며 지수 표기를 쓰지 않는다. 부호가 붙을 수 있는 금액은 전월 대비 증감액뿐이다 | AD-09, VAL-13, 설계서 8.1 |
| 비율(%) | 문자열 | `"13.00"` | 수익률, 비중, 증감률. 100을 곱한 퍼센트 값을 마지막에 한 번 HALF\_UP으로 소수 2자리에 맞춘다 | CAL-07, 설계서 8.2 |
| 수수료율 | 문자열 | `"0.2000"` | 0\~1 사이 비율, 소수 4자리. 수수료 제안값 계산에만 쓴다 | FR-206, CAL-03 |
| 날짜 | 문자열 | `"2026-09-29"` | `YYYY-MM-DD`. 시간대 변환을 하지 않는다 | CON-11, 설계서 7.2 |
| 시각 | 문자열 | `"2026-09-29T06:31:05+09:00"` | ISO 8601, 항상 `+09:00`. 클라이언트는 기기 시간대로 바꾸지 않는다 | 설계서 7.2 |
| 오늘 | — | — | 서버의 Asia/Seoul `Clock`만 판정한다. 클라이언트는 `GET /api/session`이 준 `today`와 `presets`만 쓴다 | AD-08 |

### 2.3 인증

| 대상 | 방식 | 실패 응답 | 근거 |
| --- | --- | --- | --- |
| 사용자 API `/api/**` | 기기 토큰 쿠키 `__Host-dt`. 서버는 매 요청 쿠키 원문의 SHA-256 해시로 기기를 찾고 세션을 만들지 않는다 | 401 `DEVICE_UNAUTHORIZED` | FR-101, NFR-303 |
| 인증 예외 | `GET /api/session`(인증 여부만 응답), `POST /api/auth/invite/exchange`, `GET /actuator/health` | — | 설계서 13 |
| 쿠키 속성 | `__Host-dt=<원문>; Max-Age=7776000; Path=/; Secure; HttpOnly; SameSite=Lax` | — | NFR-305, 설계서 5.1 |
| 쿠키 갱신 | 마지막 갱신 후 하루가 지난 첫 요청의 응답에 같은 값의 쿠키를 새 `Max-Age`로 다시 내린다. 클라이언트가 할 일은 없다 | — | FR-108, 설계서 5.3 |
| 운영자 API `/admin/**` | `Authorization: Bearer <ADMIN_SECRET>`. 같은 IP에서 5회 연속 실패하면 15분간 거부한다 | 401 `ADMIN_UNAUTHORIZED`, 429 `ADMIN_LOCKED` | FR-103, 설계서 5.4 |
| CSRF | `/api/**`의 POST, PUT, PATCH, DELETE는 `Origin` 헤더가 `APP_BASE_URL`과 같아야 한다. 헤더가 없어도 거부한다 | 403 `ORIGIN_REJECTED` | 설계서 5.3, SD-04 |

### 2.4 목록 분할

거래내역 목록(QRY-01)만 나눠 보내며 커서 방식을 쓴다(FR-408, AD-11).

| 항목 | 규약 |
| --- | --- |
| 크기 | `size` 기본 30, 1\~100 |
| 다음 페이지 | 응답의 `nextCursor`를 다음 요청의 `cursor`로 그대로 보낸다. `null`이면 마지막 페이지다 |
| 커서 내용 | 마지막 행의 (정산일자, 계정 id)를 Base64URL로 담은 불투명 문자열. 클라이언트는 해석하거나 만들지 않는다 |
| 조건 변경 | 기간, 계정, 정렬이 바뀌면 커서 없이 첫 페이지부터 다시 요청한다 |
| 잘못된 커서 | 400 `VALIDATION_FAILED`, `fieldErrors[].field`는 `cursor` |

### 2.5 구현 메모

- Jackson 기본 설정은 JSON 숫자를 `String` 필드에 그대로 넣어 준다. 문자열 대상(`LogicalType.Textual`)에 대한 정수와 실수 입력의 강제 변환을 실패로 설정해야 T-13(JSON 숫자 금액 → 400)이 통과한다.
- 정의되지 않은 필드 거부는 `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES`로 처리한다. Spring의 기본 구성은 이 기능을 끈 채로 시작하므로 명시적으로 켠다.
- 두 설정 모두 프레임워크 버전에 따라 기본값이 달라질 수 있으므로 MockMvc 테스트(T-13)로 고정한다.

## 3. 오류 응답과 오류 코드

오류는 상태 코드와 관계없이 `code`, `message`, `fieldErrors`, `details` 네 필드로 돌려주고, 화면은 `message`를 그대로 보여 준다(NFR-106). 오류 코드는 설계서 9.1의 8개에 이 문서가 8개를 더해 16개다(SD-04).

### 3.1 성공 상태 코드

| 상태 | 쓰는 곳 | 본문 |
| --- | --- | --- |
| 200 | 조회, 수정, 운용 종료 | 리소스 또는 조회 결과 |
| 201 | 생성 | 만든 리소스. 단건 생성은 `Location` 헤더를 붙인다 |
| 204 | 삭제, 로그아웃, 초대 교환, 기기 폐기 | 없음 |

### 3.2 오류 본문

```json
{
  "code": "SETTLEMENT_DUPLICATE",
  "message": "8월 3일 A계정 기록이 이미 있습니다. 수정하시겠습니까?",
  "fieldErrors": [],
  "details": { "settlementId": 1187, "accountId": 1, "settlementDate": "2026-08-03" }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `code` | string | 오류 코드(3.3). 화면의 분기는 이 값으로만 한다 |
| `message` | string | 원인과 다음 행동을 담은 한국어 문장. 화면이 그대로 보인다 |
| `fieldErrors` | array | 입력란별 사유 `{ "field", "message" }`. `field`는 본문 경로(`entries[0].fee`)나 쿼리 파라미터 이름이다. 없으면 빈 배열 |
| `details` | object | 화면이 다음 동작에 쓰는 값(3.3의 `details` 열). 없으면 빈 객체 |

### 3.3 오류 코드

| 코드 | 상태 | 상황 | `details` | 근거 |
| --- | --- | --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 형식과 범위 위반, 정의되지 않은 필드, JSON 숫자 금액, 잘못된 커서 | — | VAL-01\~05, VAL-07, VAL-12, VAL-13 |
| `DEVICE_UNAUTHORIZED` | 401 | 기기 토큰 없음, 만료, 폐기 | — | FR-110 |
| `INVITE_INVALID` | 401 | 초대 토큰 불일치, 사용됨, 만료. 원인을 구분하지 않는다 | — | FR-110, SD-05 |
| `ADMIN_UNAUTHORIZED` | 401 | 운영자 시크릿 없음, 불일치 | — | FR-103, SD-04 |
| `ORIGIN_REJECTED` | 403 | 쓰기 요청의 `Origin`이 서비스 출처와 다르거나 없음 | — | 설계서 5.3, SD-04 |
| `NOT_FOUND` | 404 | 경로, 본문, 쿼리의 id에 해당하는 리소스가 없음. 삭제된 정산 기록 포함 | — | SD-04 |
| `SETTLEMENT_DUPLICATE` | 409 | 같은 계정, 같은 정산일자의 기록이 있음 | `settlementId`, `accountId`, `settlementDate` | FR-304 |
| `ACCOUNT_INACTIVE` | 409 | 운용 종료한 계정에 신규 기록, 이미 운용 종료한 계정의 운용 종료 요청 | `accountId`, `deactivatedOn` | VAL-06, SD-04 |
| `SEED_NOT_APPLICABLE` | 409 | 첫 시드 적용 시작일보다 앞선 정산일자, 또는 기존 기록보다 첫 시드 시작일이 늦어지는 시드 수정과 삭제 | `accountId`, `earliestSeedStartDate`, `earliestSettlementDate` | VAL-10 |
| `SEED_DUPLICATE` | 409 | 같은 적용 시작일의 시드 이력이 있음 | `accountId`, `seedId`, `startDate` | VAL-09 |
| `SEED_LAST_ONE` | 409 | 하나 남은 시드 이력 삭제 | `accountId` | VAL-11, FR-210, SD-04 |
| `ACCOUNT_NAME_DUPLICATE` | 409 | 이미 쓰는 계정명. 운용 종료한 계정 포함 | `accountId`, `active` | Q-01 |
| `ACCOUNT_HAS_SETTLEMENTS` | 409 | 기록이 있는 계정 삭제. 삭제된 기록도 센다 | `accountId` | FR-205, DI-08 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 본문이 있는데 형식이 JSON이 아님 | — | 설계서 9.1, SD-04 |
| `ADMIN_LOCKED` | 429 | 운영자 인증 연속 실패로 잠김. `Retry-After` 헤더에 남은 초 | — | 설계서 5.4, SD-04 |
| `INTERNAL_ERROR` | 500 | 처리하지 못한 예외. 운영자 메신저로 알림이 간다 | — | NFR-503, SD-04 |

`SEED_NOT_APPLICABLE`의 `details`는 두 날짜를 항상 함께 채운다. 현재 첫 시드의 적용 시작일과 삭제되지 않은 가장 이른 정산일자이며, 기록이 없으면 뒤의 값은 `null`이다. `ACCOUNT_NAME_DUPLICATE`의 `accountId`와 `active`는 그 이름을 쓰고 있는 계정의 값이며, 화면은 `active`가 `false`일 때 이름을 먼저 바꾸라는 문구를 낸다(Q-01).

### 3.4 문구 예

| 코드 (입력란) | 문구 |
| --- | --- |
| `VALIDATION_FAILED` (`grossProfit`) | 총수익은 0보다 커야 합니다. 이 계정을 건너뛰려면 칸을 비워 두세요. |
| `VALIDATION_FAILED` (`fee`) | 수수료는 총수익보다 작아야 합니다. 정산 내역의 수수료를 다시 확인해 주세요. |
| `VALIDATION_FAILED` (금액 자릿수) | 금액은 소수 둘째 자리까지 입력할 수 있습니다. |
| `VALIDATION_FAILED` (`settlementDate`) | 오늘(9월 29일) 이후 날짜는 입력할 수 없습니다. |
| `DEVICE_UNAUTHORIZED`, `INVITE_INVALID` | 접속 권한이 없습니다. 운영자에게 연락해 새 링크를 받으세요 |
| `SETTLEMENT_DUPLICATE` | 8월 3일 A계정 기록이 이미 있습니다. 수정하시겠습니까? |
| `ACCOUNT_INACTIVE` | B계정은 운용을 종료한 계정이라 새 기록을 넣을 수 없습니다. |
| `SEED_NOT_APPLICABLE` | A계정의 첫 시드 적용 시작일(7월 1일)보다 앞선 날짜입니다. 시드 이력에서 적용 시작일을 먼저 앞당겨 주세요. |
| `SEED_LAST_ONE` | 시드 이력이 하나뿐이라 삭제할 수 없습니다. 금액이나 날짜가 틀렸다면 수정해 주세요. |
| `ACCOUNT_NAME_DUPLICATE` (운용 종료 계정) | 운용을 종료한 A계정이 이 이름을 쓰고 있습니다. 그 계정의 이름을 먼저 바꿔 주세요. |
| `ACCOUNT_HAS_SETTLEMENTS` | 정산 기록이 있는 계정은 삭제할 수 없습니다. 더 쓰지 않는 계정이면 운용 종료를 눌러 주세요. |

### 3.5 처리 규칙

- 400은 입력란별 오류를 모두 모아 한 번에 돌려준다. 409는 첫 충돌 하나만 돌려준다.
- 동시 요청이 사전 조회를 함께 통과해 DB 제약에 걸리면 제약 이름으로 같은 코드를 만든다(DB 설계서 6.3). `uq_settlement_account_date`는 `SETTLEMENT_DUPLICATE`, `uq_seed_account_start`는 `SEED_DUPLICATE`, `uq_account_user_name`은 `ACCOUNT_NAME_DUPLICATE`, `fk_settlement_account`는 `ACCOUNT_HAS_SETTLEMENTS`다. 마지막은 계정 삭제와 기록 등록이 엇갈려 삭제 쪽이 외래키에 걸린 경우다.
- 변환은 `@RestControllerAdvice` 한 곳에서 제약 이름과 SQLSTATE로 고르며 메시지 문자열은 파싱하지 않는다. 제약 위반 뒤에는 트랜잭션이 중단되므로 `details`는 롤백 뒤 새 트랜잭션에서 조회해 채운다.
- 그 밖의 제약 위반은 서버 검증 누락이라 500으로 두고 버그로 다룬다(설계서 6.4).
- 화면은 401이면서 `DEVICE_UNAUTHORIZED`일 때만 권한 없음 화면으로 간다. 연결 실패와 5xx는 별도 문구를 보이고 입력값을 지우지 않는다(설계서 10.4).

## 4. API 목록

MVP 필수(M) 엔드포인트는 24개이고, 로그아웃과 시드 이력 수정, 삭제 3개가 MVP 이후 우선(S)이다. ID는 5장 상세의 제목과 같다.

| ID | 메서드 | 경로 | 용도 | 인증 | 순위 | 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| AUTH-01 | GET | `/api/session` | 인증 여부, 오늘, 기간 프리셋 | 없음 | M | FR-108, FR-302, FR-404 |
| AUTH-02 | POST | `/api/auth/invite/exchange` | 초대 토큰을 기기 토큰 쿠키로 교환 | 없음 | M | FR-107 |
| AUTH-03 | POST | `/api/auth/logout` | 현재 기기 토큰 폐기 | 기기 토큰 | S | FR-112 |
| ACC-01 | GET | `/api/accounts` | 계정 목록 | 기기 토큰 | M | FR-202 |
| ACC-02 | POST | `/api/accounts` | 계정 등록 (최초 시드 포함) | 기기 토큰 | M | FR-201, VAL-11 |
| ACC-03 | PATCH | `/api/accounts/{id}` | 계정명, 메모 수정 | 기기 토큰 | M | FR-203 |
| ACC-04 | POST | `/api/accounts/{id}/deactivate` | 운용 종료(비활성 처리) | 기기 토큰 | M | FR-204 |
| ACC-05 | DELETE | `/api/accounts/{id}` | 기록 없는 계정 삭제 | 기기 토큰 | M | FR-205, DI-08 |
| SEED-01 | GET | `/api/accounts/{id}/seeds` | 시드 이력 목록 | 기기 토큰 | M | FR-209 |
| SEED-02 | POST | `/api/accounts/{id}/seeds` | 시드 변경 등록 | 기기 토큰 | M | FR-207, FR-208 |
| SEED-03 | PATCH | `/api/accounts/{id}/seeds/{seedId}` | 시드 이력 수정 | 기기 토큰 | S | FR-210 |
| SEED-04 | DELETE | `/api/accounts/{id}/seeds/{seedId}` | 시드 이력 삭제 | 기기 토큰 | S | FR-210 |
| ENT-01 | GET | `/api/daily-entry` | 일괄 입력 화면 데이터 | 기기 토큰 | M | FR-309, FR-310-1 |
| ENT-02 | POST | `/api/daily-entry` | 일괄 저장 | 기기 토큰 | M | FR-309, FR-310 |
| STL-01 | POST | `/api/settlements` | 단건 등록 (소급 입력) | 기기 토큰 | M | FR-305, FR-311 |
| STL-02 | GET | `/api/settlements/{id}` | 단건 조회 | 기기 토큰 | M | FR-307 |
| STL-03 | PUT | `/api/settlements/{id}` | 수정 (총수익, 수수료, 메모) | 기기 토큰 | M | FR-307, DI-07 |
| STL-04 | DELETE | `/api/settlements/{id}` | 논리 삭제 | 기기 토큰 | M | FR-308, AD-05 |
| QRY-01 | GET | `/api/settlements` | 거래내역 목록 (누적 순수익 포함) | 기기 토큰 | M | FR-401\~406, FR-408 |
| QRY-02 | GET | `/api/settlements/summary` | 목록과 같은 조건의 합계와 건수 | 기기 토큰 | M | FR-407 |
| STAT-01 | GET | `/api/stats/home` | 이번 달 순수익, 전월 같은 기간 대비 | 기기 토큰 | M | FR-501, DI-03 |
| STAT-02 | GET | `/api/stats/monthly` | 월별 집계 | 기기 토큰 | M | FR-502, DI-04 |
| STAT-03 | GET | `/api/stats/accounts` | 계정별 집계, 시드 구간별 수익률, 통합 수익률 | 기기 토큰 | M | FR-503, FR-507\~509 |
| ADM-01 | POST | `/admin/invites` | 초대 링크 발급 | 운영자 시크릿 | M | FR-103, FR-104 |
| ADM-02 | GET | `/admin/devices` | 기기 목록 | 운영자 시크릿 | M | FR-109 |
| ADM-03 | POST | `/admin/devices/{id}/revoke` | 기기 토큰 폐기 | 운영자 시크릿 | M | FR-109 |
| SYS-01 | GET | `/actuator/health` | 가용성 점검 | 없음 | M | NFR-503 |

경로의 `{id}`와 `{seedId}`는 숫자다. `/api/settlements/summary`는 리터럴 경로라 `/api/settlements/{id}`보다 먼저 매칭된다. AUTH-01은 인증이 없어도 응답하지만, 유효한 쿠키가 있으면 다른 사용자 API와 같이 갱신한다.

## 5. API 상세

엔드포인트마다 요청(경로 변수, 쿼리, 본문), 성공 응답, 해당 오류를 적었다. 모든 사용자 API에 공통인 401, 403, 415, 500은 반복하지 않는다. 예시의 오늘은 2026-09-29(화)이고 금액은 화면 설계서 v0.1 시안과 맞췄다. 여러 엔드포인트가 함께 쓰는 응답 객체는 6장에 정의했다.

### 5.1 세션과 인증

앱 셸은 AUTH-01 하나로 인증 여부와 오늘 날짜를 함께 받고, 초대 링크 진입(SCR-01)만 AUTH-02를 부른다. AUTH-03을 부를 설정 화면(SCR-07)은 MVP에서 빠져 있다.

#### AUTH-01 세션 조회 — `GET /api/session`

앱 셸이 시작할 때, 앱이 다시 보일 때(`visibilitychange`), 입력 화면에 들어올 때 부른다(설계서 7.1). 파라미터는 없다.

응답 200, 인증된 경우:

```json
{
  "authenticated": true,
  "today": "2026-09-29",
  "presets": {
    "THIS_MONTH":   { "from": "2026-09-01", "to": "2026-09-29" },
    "LAST_MONTH":   { "from": "2026-08-01", "to": "2026-08-31" },
    "LAST_7_DAYS":  { "from": "2026-09-23", "to": "2026-09-29" },
    "LAST_30_DAYS": { "from": "2026-08-31", "to": "2026-09-29" },
    "THIS_YEAR":    { "from": "2026-01-01", "to": "2026-09-29" }
  }
}
```

응답 200, 인증되지 않은 경우: `{ "authenticated": false }`. 쿠키가 없거나 만료, 폐기된 경우 모두 같다. 401을 쓰지 않는 이유는 앱 셸이 이 값으로 초대 화면과 권한 없음 안내를 고르기 때문이다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `authenticated` | boolean | 유효한 기기 토큰이 있는지 |
| `today` | 날짜 | Asia/Seoul 기준 오늘. 인증됐을 때만 준다 |
| `presets` | object | 프리셋 이름별 `{ from, to }`. 경계는 설계서 7.3(DI-02)이며 인증됐을 때만 준다 |

#### AUTH-02 초대 토큰 교환 — `POST /api/auth/invite/exchange`

&#91;이 휴대폰 등록\] 버튼이 URL 조각(`#` 뒤)의 토큰을 보낸다. 서버는 초대 행을 `SELECT ... FOR UPDATE`로 잠근 트랜잭션에서 해시 일치, 미사용, 미만료를 검증하고 사용 처리와 기기 토큰 생성을 끝낸다(FR-105, FR-107, DI-01).

| 본문 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `token` | string | 예 | 초대 토큰 원문. Base64URL 43자 |

```json
{ "token": "<초대 토큰 원문 43자>" }
```

응답 204, 본문 없음. 응답 헤더에 기기 토큰 쿠키를 싣는다.

```text
Set-Cookie: __Host-dt=<기기 토큰 원문>; Max-Age=7776000; Path=/; Secure; HttpOnly; SameSite=Lax
```

- 기기 표시명은 요청의 `User-Agent`에서 OS와 브라우저 이름만 뽑아 저장한다(설계서 5.4).
- 같은 토큰의 동시 요청은 1건만 성공하고 나머지는 401이다(T-11).
- 오류: 401 `INVITE_INVALID`. 토큰 형식 오류, 불일치, 사용됨, 만료를 구분하지 않고, 원인은 토큰 값 없이 서버 로그에만 남긴다(FR-110, 설계서 12.4).

#### AUTH-03 로그아웃 — `POST /api/auth/logout` (S)

현재 요청의 기기 토큰을 폐기하고 쿠키를 지운다(FR-112). 본문은 없다.

응답 204, 본문 없음.

```text
Set-Cookie: __Host-dt=; Max-Age=0; Path=/; Secure; HttpOnly; SameSite=Lax
```

### 5.2 계정

계정 관리(SCR-06)가 다섯 개를 모두 쓰고, 거래내역(SCR-04)의 계정 칩과 단건 입력(SCR-03-1)의 계정 선택도 ACC-01을 부른다. 계정 응답은 6.1의 Account 객체다.

#### ACC-01 계정 목록 — `GET /api/accounts`

| 쿼리 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | string | 아니오 | `active`(기본)는 운용 중인 계정만, `all`은 운용 종료한 계정까지 |

정렬은 운용 중인 계정이 먼저, 같은 상태끼리는 id 오름차순이다. 운용 종료한 계정을 목록 맨 아래에 두는 화면 설계(SCR-06)를 따른다(SD-06). 계정 수가 적어 나누지 않는다. `deletable`은 삭제된 기록까지 포함해 기록이 한 건도 없을 때만 `true`이고, 화면은 이 값으로 \[삭제\]를 보인다(FR-205, DI-08).

응답 200:

```json
{
  "accounts": [
    {
      "id": 1,
      "name": "A계정",
      "defaultFeeRate": "0.2000",
      "active": true,
      "deactivatedOn": null,
      "deletable": false,
      "memo": null,
      "currentSeed": { "id": 3, "amount": "160000.00", "startDate": "2026-09-15" },
      "createdAt": "2026-07-01T09:12:40+09:00",
      "updatedAt": "2026-07-01T09:12:40+09:00"
    },
    {
      "id": 2,
      "name": "B계정",
      "defaultFeeRate": "0.2000",
      "active": true,
      "deactivatedOn": null,
      "deletable": false,
      "memo": null,
      "currentSeed": { "id": 2, "amount": "100000.00", "startDate": "2026-07-01" },
      "createdAt": "2026-07-01T09:12:40+09:00",
      "updatedAt": "2026-07-01T09:12:40+09:00"
    }
  ]
}
```

#### ACC-02 계정 등록 — `POST /api/accounts`

계정과 최초 시드를 한 트랜잭션에서 만든다(VAL-11). 수수료율은 받지 않고 0.2000으로 만든다(FR-206).

| 본문 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `name` | string | 예 | 앞뒤 공백을 지운 뒤 1\~30자. 운용 종료한 계정을 포함해 유일 (VAL-04, Q-01) |
| `memo` | string | 아니오 | 200자 이하 (VAL-05) |
| `initialSeed.amount` | 금액 | 예 | 0보다 큼 (VAL-07, VAL-13) |
| `initialSeed.startDate` | 날짜 | 예 | 오늘 이하 (VAL-12). 화면은 오늘로 미리 채운다 (SD-07) |
| `initialSeed.memo` | string | 아니오 | 200자 이하 |

```json
{
  "name": "C계정",
  "memo": null,
  "initialSeed": { "amount": "50000.00", "startDate": "2026-09-29", "memo": "최초 입금" }
}
```

응답 201, `Location: /api/accounts/3`, 본문은 만든 Account 객체다. 오류는 400 `VALIDATION_FAILED`, 409 `ACCOUNT_NAME_DUPLICATE`.

#### ACC-03 계정 수정 — `PATCH /api/accounts/{id}`

계정명과 메모만 바꾼다. 계정명을 바꾸면 지난 기록의 표시 이름도 함께 바뀐다(FR-203). 운용 종료한 계정도 수정할 수 있다. 옛 이름을 새 계정에 쓰려면 운용 종료한 계정의 이름을 먼저 바꿔야 하기 때문이다(Q-01).

| 본문 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `name` | string | 아니오 | ACC-02와 같음 |
| `memo` | string | 아니오 | 200자 이하. 빈 문자열이면 메모를 지운다 (SD-03) |

```json
{ "name": "A계정(메인)" }
```

응답 200, 수정된 Account 객체. 오류는 400 `VALIDATION_FAILED`, 404 `NOT_FOUND`, 409 `ACCOUNT_NAME_DUPLICATE`.

#### ACC-04 운용 종료 — `POST /api/accounts/{id}/deactivate`

계정을 비활성으로 바꾸고 비활성일자를 서버의 오늘로 기록한다(FR-204). 되돌리는 API는 없고 본문도 없다. 계정 행을 `SELECT ... FOR UPDATE`로 잠근 뒤 처리해, 같은 계정의 기록 입력과 엇갈려도 VAL-06이 깨지지 않게 한다(설계서 6.4).

응답 200, 바뀐 Account 객체(`active: false`, `deactivatedOn: "2026-09-29"`). 오류는 404 `NOT_FOUND`, 409 `ACCOUNT_INACTIVE`(이미 운용 종료한 계정).

#### ACC-05 계정 삭제 — `DELETE /api/accounts/{id}`

정산 기록이 한 건도 없는(삭제된 기록 포함) 계정만 시드 이력과 함께 물리 삭제한다(FR-205, DI-08). 잘못 등록한 계정을 지우는 용도이며 운용 상태는 따지지 않는다.

응답 204. 오류는 404 `NOT_FOUND`, 409 `ACCOUNT_HAS_SETTLEMENTS`.

### 5.3 시드 이력

시드 이력 화면(SCR-06-1)이 쓴다. 시드를 바꿔도 이전 구간의 수익률은 당시 시드로 남는다(FR-208, CAL-11). `{seedId}`가 경로의 계정에 속한 이력이 아니면 404다.

#### SEED-01 시드 이력 목록 — `GET /api/accounts/{id}/seeds`

적용 시작일 최신순으로 준다(FR-209, SCR-06-1). 이력마다 적용 기간을 보이도록 `endDate`를 주고, 화면이 계정 이름과 상태, 날짜 입력 상한(오늘과 비활성일자 중 이른 날)을 한 번에 그리도록 Account 객체도 함께 준다(SD-06, SD-08).

응답 200:

```json
{
  "account": {
    "id": 1, "name": "A계정", "defaultFeeRate": "0.2000",
    "active": true, "deactivatedOn": null, "deletable": false, "memo": null,
    "currentSeed": { "id": 3, "amount": "160000.00", "startDate": "2026-09-15" },
    "createdAt": "2026-07-01T09:12:40+09:00", "updatedAt": "2026-07-01T09:12:40+09:00"
  },
  "seeds": [
    {
      "id": 3, "accountId": 1, "amount": "160000.00", "startDate": "2026-09-15", "endDate": null,
      "memo": "원금 추가 입금", "createdAt": "2026-09-15T07:02:11+09:00"
    },
    {
      "id": 1, "accountId": 1, "amount": "150000.00", "startDate": "2026-07-01", "endDate": "2026-09-14",
      "memo": null, "createdAt": "2026-07-01T09:12:40+09:00"
    }
  ]
}
```

#### SEED-02 시드 변경 등록 — `POST /api/accounts/{id}/seeds`

기존 이력을 덮어쓰지 않고 새 이력을 추가한다(FR-208). 원금 입출금은 입금일부터 반영되므로 적용 시작일에는 보통 입금일을 넣는다(FR-207).

| 본문 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `amount` | 금액 | 예 | 0보다 큼 (VAL-07, VAL-13) |
| `startDate` | 날짜 | 예 | 오늘 이하, 운용 종료한 계정은 비활성일자 이하 (VAL-12). 화면은 오늘(운용 종료한 계정은 비활성일자)로 미리 채운다 (SD-07) |
| `memo` | string | 아니오 | 사유 메모, 200자 이하 |

```json
{ "amount": "160000.00", "startDate": "2026-09-15", "memo": "원금 추가 입금" }
```

계정 행을 잠근 뒤 VAL-12의 비활성일자를 검사한다. 추가는 가장 이른 적용 시작일을 늦추지 않으므로 VAL-10 검사는 필요 없다.

응답 201, `Location: /api/accounts/1/seeds/3`, 본문은 만든 Seed 객체(6.2). 오류는 400 `VALIDATION_FAILED`, 404 `NOT_FOUND`, 409 `SEED_DUPLICATE`.

#### SEED-03 시드 이력 수정 — `PATCH /api/accounts/{id}/seeds/{seedId}` (S)

잘못 등록한 이력을 고친다(FR-210). 금액이나 적용 시작일을 고치면 그 구간의 지난 수익률도 바뀐다. 수익률을 저장하지 않고 조회할 때 계산하기 때문이다(CAL-11).

| 본문 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `amount` | 금액 | 아니오 | SEED-02와 같음 |
| `startDate` | 날짜 | 아니오 | SEED-02와 같음. 바꾼 결과 계정의 가장 이른 적용 시작일이 삭제되지 않은 가장 이른 정산일자보다 늦어지면 409 (VAL-10) |
| `memo` | string | 아니오 | 빈 문자열이면 지운다 (SD-03) |

응답 200, 수정된 Seed 객체. 오류는 400 `VALIDATION_FAILED`, 404 `NOT_FOUND`, 409 `SEED_DUPLICATE`, 409 `SEED_NOT_APPLICABLE`.

#### SEED-04 시드 이력 삭제 — `DELETE /api/accounts/{id}/seeds/{seedId}` (S)

이력이 하나만 남은 계정에서는 삭제할 수 없다(FR-210, VAL-11). 첫 이력을 지워 가장 이른 적용 시작일이 기존 기록보다 늦어지는 경우도 거부한다(VAL-10). 두 검사 모두 계정 행을 잠근 뒤 한다.

응답 204. 오류는 404 `NOT_FOUND`, 409 `SEED_LAST_ONE`, 409 `SEED_NOT_APPLICABLE`.

### 5.4 일괄 입력

일일 일괄 입력(SCR-03)이 쓴다. 홈의 \[오늘 입력\]에서 금액을 넣고 저장하면 끝나는 아침 입력(NFR-102)이 이 두 호출로 이루어진다.

#### ENT-01 일괄 입력 데이터 — `GET /api/daily-entry`

| 쿼리 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `date` | 날짜 | 아니오 | 기본값은 서버의 오늘. 오늘 이후면 400 (VAL-03). 화면은 날짜를 오늘로 고정하므로 생략해 부른다 |

운용 중인 계정 전체를 id 오름차순으로 나열하고 계정마다 입력 상태를 준다(FR-310-1). 아래 예는 A계정이 입력을 마친 상태다.

응답 200:

```json
{
  "date": "2026-09-29",
  "accounts": [
    {
      "accountId": 1, "name": "A계정", "defaultFeeRate": "0.2000", "status": "DONE",
      "settlement": { "id": 1532, "grossProfit": "1234.56", "fee": "246.90", "netProfit": "987.66" }
    },
    { "accountId": 2, "name": "B계정", "defaultFeeRate": "0.2000", "status": "OPEN", "settlement": null }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `date` | 날짜 | 입력 대상 정산일자 |
| `accounts[].accountId`, `name` | number, string | 계정 id와 현재 이름 |
| `accounts[].defaultFeeRate` | 수수료율 | 수수료 제안값 계산용 (CAL-03) |
| `accounts[].status` | string | `OPEN` 입력 대상, `DONE` 그날 기록 있음(입력 대상에서 제외), `UNAVAILABLE` 그날 적용되는 시드가 없음(VAL-10) |
| `accounts[].settlement` | object | `DONE`일 때 저장된 기록의 `id`, `grossProfit`, `fee`, `netProfit`. 그 밖에는 `null`. \[수정\] 버튼이 이 `id`로 단건 수정 화면에 간다 |

#### ENT-02 일괄 저장 — `POST /api/daily-entry`

| 본문 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `date` | 날짜 | 예 | ENT-01이 준 `date`. 오늘 이하 (VAL-03) |
| `entries` | array | 예 | 금액을 채운 계정만 담는다. 비었거나 같은 `accountId`가 두 번 나오면 400 (SD-09) |
| `entries[].accountId` | number | 예 | 운용 중인 계정 (VAL-06) |
| `entries[].grossProfit` | 금액 | 예 | 0보다 큼. 0은 건너뛰기가 아니라 오류다 (VAL-01, FR-310) |
| `entries[].fee` | 금액 | 예 | 0 이상, 총수익 미만 (VAL-02) |
| `entries[].memo` | string | 아니오 | 200자 이하 (VAL-05) |

요청:

```json
{
  "date": "2026-09-29",
  "entries": [
    { "accountId": 2, "grossProfit": "845.20", "fee": "169.04", "memo": null }
  ]
}
```

응답 201:

```json
{
  "date": "2026-09-29",
  "created": [
    { "id": 1533, "accountId": 2, "grossProfit": "845.20", "fee": "169.04", "netProfit": "676.16" }
  ]
}
```

검증 실패 400. `field`의 인덱스는 요청의 `entries` 순서이며, 화면은 이 순서로 계정 입력란을 찾는다.

```json
{
  "code": "VALIDATION_FAILED",
  "message": "입력값을 확인해 주세요.",
  "fieldErrors": [
    { "field": "entries[0].fee", "message": "수수료는 총수익보다 작아야 합니다. 정산 내역의 수수료를 다시 확인해 주세요." }
  ],
  "details": {}
}
```

일괄 저장은 다음 순서로 처리하며, 어느 단계에서 실패하든 아무 기록도 만들지 않는다(DI-06, 설계서 9.4).

1. 형식과 범위를 먼저 모두 검사하고, 틀린 입력란을 모두 `fieldErrors`에 담아 400으로 돌려준다.
2. 대상 계정 행을 id 오름차순으로 `SELECT ... FOR UPDATE` 잠근다. 여러 계정을 잠글 때 교착을 막는 순서다(설계서 6.4).
3. 운용 상태(VAL-06), 첫 시드(VAL-10), 중복(FR-304)을 검사해 첫 충돌에서 409로 끝낸다. 다른 기기에서 같은 기록이 먼저 생긴 경우 화면은 ENT-01을 다시 불러 그 계정을 완료 상태로 바꾼다.
4. 순수익을 `총수익 - 수수료`로 계산해 함께 저장한다(CAL-02). 요청에 `netProfit`을 넣으면 정의되지 않은 필드라 400이다.

오류는 400 `VALIDATION_FAILED`, 404 `NOT_FOUND`(없는 계정), 409 `ACCOUNT_INACTIVE`, 409 `SEED_NOT_APPLICABLE`, 409 `SETTLEMENT_DUPLICATE`. 수수료 이상치 경고(FR-303-2)는 화면이 저장 전에 띄우며 이 API는 바뀌지 않는다.

### 5.5 정산 기록

단건 입력과 수정(SCR-03-1)이 쓴다. 지난 날짜의 소급 입력과 개별 기록의 수정, 삭제를 맡고, 당일 입력은 5.4의 일괄 입력이 맡는다(FR-311). 응답은 6.3의 Settlement 객체다.

#### STL-01 단건 등록 — `POST /api/settlements`

| 본문 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `accountId` | number | 예 | 운용 중인 계정 (VAL-06) |
| `settlementDate` | 날짜 | 예 | 오늘 이하 (VAL-03), 계정의 첫 시드 적용 시작일 이상 (VAL-10) |
| `grossProfit` | 금액 | 예 | 0보다 큼 (VAL-01) |
| `fee` | 금액 | 예 | 0 이상, 총수익 미만 (VAL-02) |
| `memo` | string | 아니오 | 200자 이하 (VAL-05) |

```json
{ "accountId": 2, "settlementDate": "2026-09-14", "grossProfit": "792.40", "fee": "158.48", "memo": "늦게 입력" }
```

계정 행을 잠근 뒤 VAL-06, VAL-10, 중복을 검사하고 순수익을 계산해 저장한다. 응답 201, `Location: /api/settlements/1534`, 본문은 만든 Settlement 객체(이 예의 `netProfit`은 `"633.92"`)다.

이미 있는 기록이면 409 `SETTLEMENT_DUPLICATE`이고, 화면은 `details.settlementId`로 수정 화면 이동 버튼을 보인다(FR-304, 3.2의 예). 오류는 400 `VALIDATION_FAILED`, 404 `NOT_FOUND`, 409 `ACCOUNT_INACTIVE`, 409 `SEED_NOT_APPLICABLE`, 409 `SETTLEMENT_DUPLICATE`.

#### STL-02 단건 조회 — `GET /api/settlements/{id}`

Settlement 객체에 그 계정의 `defaultFeeRate`를 더해 준다. 수정 화면의 \[제안값으로 되돌리기\]가 제안값을 다시 계산하는 데 쓴다(DI-09, SD-08).

응답 200:

```json
{
  "id": 1532,
  "accountId": 1,
  "accountName": "A계정",
  "settlementDate": "2026-09-29",
  "grossProfit": "1234.56",
  "fee": "246.90",
  "netProfit": "987.66",
  "memo": null,
  "createdAt": "2026-09-29T06:42:10+09:00",
  "updatedAt": "2026-09-29T06:42:10+09:00",
  "defaultFeeRate": "0.2000"
}
```

삭제된 기록은 없는 것으로 보고 404다(AD-05).

#### STL-03 수정 — `PUT /api/settlements/{id}`

총수익, 수수료, 메모만 바꾼다(DI-07). 계정이나 정산일자가 틀렸으면 삭제한 뒤 다시 입력한다. 본문에 `accountId`나 `settlementDate`가 있으면 정의되지 않은 필드라 400이다(SD-02).

| 본문 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `grossProfit` | 금액 | 예 | 0보다 큼 (VAL-01) |
| `fee` | 금액 | 예 | 0 이상, 총수익 미만 (VAL-02) |
| `memo` | string | 아니오 | 200자 이하. PUT은 전체 교체라 `null`이거나 빠지면 메모가 지워진다 |

```json
{ "grossProfit": "1234.56", "fee": "246.91", "memo": "수수료 정정" }
```

응답 200, 수정된 Settlement 객체(이 예의 `netProfit`은 `"987.65"`). 운용 종료한 계정의 기록도 고칠 수 있다. VAL-06은 신규 기록만 막는다. 오류는 400 `VALIDATION_FAILED`, 404 `NOT_FOUND`.

#### STL-04 삭제 — `DELETE /api/settlements/{id}`

`deleted_at`을 채우는 논리 삭제다(AD-05, NFR-404). 삭제한 날짜에는 같은 계정으로 다시 입력할 수 있고, 삭제 전 확인은 화면이 맡는다(FR-308).

응답 204. 오류는 404 `NOT_FOUND`(없거나 이미 삭제됨).

### 5.6 거래내역 조회

거래내역(SCR-04)이 같은 조건으로 두 API를 함께 부른다. 목록의 누적 순수익은 조회 구간 전체를 (정산일자, 계정 id) 오름차순으로 누적한 값이라, 페이지와 정렬이 바뀌어도 같은 기록은 같은 값을 갖는다(AD-11). 삭제된 기록은 목록, 누적, 합계 어디에도 들어가지 않는다(T-08).

#### QRY-01 거래내역 목록 — `GET /api/settlements`

| 쿼리 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `from` | 날짜 | 예 | 조회 시작일 (FR-403) |
| `to` | 날짜 | 예 | 조회 종료일. `from` 이상이며 오늘 이후여도 된다 |
| `accountId` | number | 아니오 | 생략하면 전체 계정 통합 조회 (FR-405, FR-406). 운용 종료한 계정도 된다. 없는 계정이면 404 (SD-10) |
| `order` | string | 아니오 | `desc`(기본) 또는 `asc` (FR-402) |
| `cursor` | string | 아니오 | 이전 응답의 `nextCursor` |
| `size` | number | 아니오 | 기본 30, 1\~100 |

정렬은 (정산일자, 계정 id)를 둘 다 `order` 방향으로 한다. 계정명은 바뀌는 값이라 순서 기준으로 쓰지 않는다(설계서 8.5). 아래는 9월 통합 조회의 첫 페이지를 `size=3`으로 받은 예다.

```text
GET /api/settlements?from=2026-09-01&to=2026-09-29&order=desc&size=3
```

응답 200:

```json
{
  "items": [
    {
      "id": 1532, "accountId": 1, "accountName": "A계정", "settlementDate": "2026-09-29",
      "grossProfit": "1234.56", "fee": "246.90", "netProfit": "987.66",
      "cumulativeNetProfit": "47122.21", "memo": null
    },
    {
      "id": 1531, "accountId": 2, "accountName": "B계정", "settlementDate": "2026-09-28",
      "grossProfit": "888.33", "fee": "177.66", "netProfit": "710.67",
      "cumulativeNetProfit": "46134.55", "memo": "오후에 늦게 정산됨"
    },
    {
      "id": 1530, "accountId": 1, "accountName": "A계정", "settlementDate": "2026-09-28",
      "grossProfit": "1231.02", "fee": "246.20", "netProfit": "984.82",
      "cumulativeNetProfit": "45423.88", "memo": null
    }
  ],
  "nextCursor": "MjAyNi0wOS0yOHwx"
}
```

다음 페이지는 같은 조건에 `cursor=MjAyNi0wOS0yOHwx`를 더해 부르고 9월 27일 B계정부터 이어진다. 내림차순 첫 행의 누적값은 QRY-02의 `netProfitSum`과 같아야 한다(T-09, T-10). 계정을 하나 고르면 그 계정 기록만으로 누적하므로 통합 조회의 누적값과 다른 것이 정상이다.

오류는 400 `VALIDATION_FAILED`(날짜 누락이나 역전, 잘못된 커서, `size` 범위), 404 `NOT_FOUND`.

#### QRY-02 거래내역 합계 — `GET /api/settlements/summary`

목록 상단의 구간 합계다(FR-407). 쿼리는 QRY-01의 `from`, `to`, `accountId`를 같은 규칙으로 받고, 나머지 파라미터는 무시해 화면이 쿼리 문자열을 그대로 나눠 쓸 수 있다. 합계는 저장된 값의 단순 합이다(CAL-05).

응답 200:

```json
{
  "count": 57,
  "grossProfitSum": "58902.26",
  "feeSum": "11780.05",
  "netProfitSum": "47122.21"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `count` | number | 기록 건수 |
| `grossProfitSum` | 금액 | 총수익 합계 |
| `feeSum` | 금액 | 수수료 합계 |
| `netProfitSum` | 금액 | 순수익 합계 |

기록이 없으면 `count`는 0, 합계는 모두 `"0.00"`이다. 오류는 QRY-01과 같다.

### 5.7 통계

홈(SCR-02)이 STAT-01을, 통계(SCR-05)가 STAT-02와 STAT-03을 부른다. 통계 화면은 조회 기간(프리셋과 직접 지정, 기본은 이번 달)으로 통합 수익률과 계정별 영역을 보이고, 월별 집계는 연도 선택으로 따로 보인다(화면 설계서 SCR-05). 수익률은 저장하지 않고 정산일자에 유효했던 시드로 조회할 때 계산한다(CAL-06, CAL-11).

#### STAT-01 홈 요약 — `GET /api/stats/home`

파라미터는 없다. 이번 달 1일부터 오늘까지의 순수익 합계를 전월 1일부터 같은 날짜(없으면 전월 말일)까지와 비교한다(FR-501, DI-03).

응답 200:

```json
{
  "thisMonth": { "from": "2026-09-01", "to": "2026-09-29", "netProfitSum": "47122.21" },
  "lastMonthSamePeriod": { "from": "2026-08-01", "to": "2026-08-29", "netProfitSum": "45780.78" },
  "change": { "amount": "1341.43", "rate": "2.93" },
  "todayStatus": [
    { "accountId": 1, "name": "A계정", "status": "DONE" },
    { "accountId": 2, "name": "B계정", "status": "OPEN" }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `thisMonth` | object | `from`, `to`(오늘), `netProfitSum` |
| `lastMonthSamePeriod` | object | 비교 구간과 그 합계. 오늘이 3월 31일이면 2월 1일\~2월 28일이다 (T-07) |
| `change.amount` | 금액 | 이번 달 합계 - 전월 합계. 줄었으면 음수다 (`"-120.50"`) |
| `change.rate` | 비율 | 증감액 ÷ 전월 합계 × 100, 소수 2자리 HALF\_UP. 전월 합계가 0이면 `null` |
| `todayStatus` | array | (S) 운용 중인 계정 전체와 오늘 입력 여부(`DONE`, `OPEN`). FR-312의 오늘 입력 현황이며 MVP 이후에 더한다 (SD-13) |

#### STAT-02 월별 집계 — `GET /api/stats/monthly`

| 쿼리 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `year` | number | 예 | 집계할 연도. 화면은 처음에 `today`의 연도를 보낸다 (SD-10) |

그 해에 기록이 있는 달만 최신순으로 준다. 행의 `from`, `to`는 그 달의 첫날과 마지막 날이고, 진행 중인 달은 `to`가 오늘이라 화면이 이 값으로 진행 중인 달을 구분한다(SD-11, FR-505). 오류는 400 `VALIDATION_FAILED`(`year` 누락이나 형식 오류)뿐이다.

```text
GET /api/stats/monthly?year=2026
```

응답 200:

```json
{
  "year": 2026,
  "months": [
    {
      "month": "2026-09", "from": "2026-09-01", "to": "2026-09-29",
      "grossProfitSum": "58902.26", "feeSum": "11780.05", "netProfitSum": "47122.21", "recordDays": 29
    },
    {
      "month": "2026-08", "from": "2026-08-01", "to": "2026-08-31",
      "grossProfitSum": "61217.37", "feeSum": "12243.07", "netProfitSum": "48974.30", "recordDays": 31
    },
    {
      "month": "2026-07", "from": "2026-07-01", "to": "2026-07-31",
      "grossProfitSum": "62361.12", "feeSum": "12471.92", "netProfitSum": "49889.20", "recordDays": 31
    }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `year` | number | 요청한 연도 |
| `months[].month` | string | `YYYY-MM` |
| `months[].from`, `to` | 날짜 | 그 달의 첫날과 마지막 날. 진행 중인 달은 `to`가 오늘이다 |
| `months[].recordDays` | number | 기록이 있는 서로 다른 정산일자 수. 같은 날 두 계정의 기록은 1일로 센다 (DI-04) |

#### STAT-03 계정별 집계와 수익률 — `GET /api/stats/accounts`

쿼리는 거래내역과 같은 기간 규칙의 `from`, `to`(필수)다. 계정 행 아래에 시드 구간 행을 두는 2단 구조로, 계정 행에는 합계와 비중을, 구간 행에는 적용 기간, 적용 시드, 순수익, 수익률을 둔다(FR-503, 설계서 8.3). 조회 구간이 둘 이상의 시드 구간에 걸치면 수익률을 합치지 않고 행을 나눈다(FR-508, CAL-08). 통합 수익률은 일자별 합산으로 한 값만 준다(FR-509, CAL-10).

아래는 SRS 4.2의 통합 수익률 예시(A 시드 10,000.00, B 시드 5,000.00, B는 2026-03-31 운용 종료)에 A의 1\~3월 순수익 1,100.00과 4월 300.00, B의 1\~3월 순수익 400.00을 넣은 응답이다. 통합 수익률은 13.00%다. 계정별 수익률의 평균(11.00%)이나 현재 운용 중인 시드로 나눈 값(18.00%)이 나오면 구현 오류다(T-04).

```text
GET /api/stats/accounts?from=2026-01-01&to=2026-04-30
```

응답 200:

```json
{
  "accounts": [
    {
      "accountId": 1, "name": "A계정", "active": true, "deactivatedOn": null,
      "grossProfitSum": "1750.00", "feeSum": "350.00", "netProfitSum": "1400.00", "shareRate": "77.78",
      "segments": [
        { "from": "2026-01-01", "to": "2026-04-30", "seedAmount": "10000.00", "netProfitSum": "1400.00", "profitRate": "14.00" }
      ]
    },
    {
      "accountId": 2, "name": "B계정", "active": false, "deactivatedOn": "2026-03-31",
      "grossProfitSum": "500.00", "feeSum": "100.00", "netProfitSum": "400.00", "shareRate": "22.22",
      "segments": [
        { "from": "2026-01-01", "to": "2026-03-31", "seedAmount": "5000.00", "netProfitSum": "400.00", "profitRate": "8.00" }
      ]
    }
  ],
  "total": {
    "grossProfitSum": "2250.00", "feeSum": "450.00", "netProfitSum": "1800.00",
    "combinedProfitRate": "13.00",
    "seedBasis": { "type": "DAILY_SUM", "amount": null, "fromAmount": "15000.00", "toAmount": "10000.00" }
  }
}
```

시드 구간이 둘인 계정은 `segments`가 두 행이다. SRS 4.2의 시드 변경 예시를 1\~12월로 조회하면 다음과 같다(T-03).

```json
"segments": [
  { "from": "2026-01-01", "to": "2026-06-30", "seedAmount": "5000000.00", "netProfitSum": "500000.00", "profitRate": "10.00" },
  { "from": "2026-07-01", "to": "2026-12-31", "seedAmount": "10000000.00", "netProfitSum": "500000.00", "profitRate": "5.00" }
]
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `accounts[]` | array | 조회 구간과 겹치는 시드 구간이 있는 계정만, id 오름차순 (SD-12) |
| `accounts[].shareRate` | 비율 | 계정 순수익 ÷ 전체 순수익 × 100. 합이 100.00이 되도록 보정하지 않는다 (DI-10). 전체 순수익이 0이면 `null` |
| `accounts[].segments[]` | array | 시드 구간과 조회 구간의 교집합. 운용 종료한 계정은 비활성일자에서 끝난다 (DI-05) |
| `segments[].profitRate` | 비율 | 구간 순수익 ÷ 적용 시드 × 100 (CAL-07) |
| `total.combinedProfitRate` | 비율 | 통합 수익률 (CAL-10, 설계서 8.4). 유효 시드 합계가 양수인 날이 없으면 `null` |
| `total.seedBasis` | SeedBasis | 통합 수익률의 기준 시드 표시 (FR-507, 6.5). `combinedProfitRate`가 `null`이면 `null` |

구간 행이 하나인 계정은 화면이 한 줄로 합친다(설계서 8.3). STAT-03의 오류는 400 `VALIDATION_FAILED`(날짜 누락이나 역전)뿐이다.

### 5.8 운영자 API와 헬스 체크

운영자 API는 화면이 부르지 않고 운영자가 서버 밖에서 curl 등으로 부른다. 모든 요청에 `Authorization: Bearer <ADMIN_SECRET>` 헤더가 필요하고, 기기 토큰과 분리된 필터 체인에서 처리한다(설계서 5.4). 세 API에 공통인 오류는 401 `ADMIN_UNAUTHORIZED`와 429 `ADMIN_LOCKED`다.

#### ADM-01 초대 링크 발급 — `POST /admin/invites`

본문은 없다. 32바이트 난수 토큰을 만들어 해시만 저장하고, 원문은 이 응답에서 한 번만 준다(FR-104).

```text
curl -X POST https://{도메인}/admin/invites -H "Authorization: Bearer $ADMIN_SECRET"
```

응답 201:

```json
{
  "inviteUrl": "https://{도메인}/invite#<초대 토큰 원문 43자>",
  "expiresAt": "2026-09-29T07:31:05+09:00"
}
```

유효기간은 발급 후 1시간이며 1회 사용하면 만료된다(FR-105). 링크는 문자 등 서비스 밖 수단으로 보낸다(FR-106).

#### ADM-02 기기 목록 — `GET /admin/devices`

분실 신고를 받으면 이 목록에서 기기를 찾아 폐기한다(FR-109). 등록 최신순으로 준다(SD-14).

응답 200:

```json
{
  "devices": [
    {
      "id": 1,
      "deviceLabel": "Android / Samsung Internet",
      "status": "ACTIVE",
      "createdAt": "2026-09-29T06:35:12+09:00",
      "lastUsedAt": "2026-09-29T06:35:12+09:00",
      "expiresAt": "2026-12-28T06:35:12+09:00",
      "revokedAt": null
    }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `deviceLabel` | string | 등록할 때 `User-Agent`에서 뽑은 OS와 브라우저 이름 |
| `status` | string | `ACTIVE`, `EXPIRED`(만료 시각 지남), `REVOKED`(폐기됨) |
| `lastUsedAt` | 시각 | 마지막 갱신 시각. 하루 1회만 갱신하므로 실제 마지막 사용과 최대 하루 차이 난다 (설계서 5.3) |
| `expiresAt`, `revokedAt` | 시각 | 만료 예정 시각과 폐기 시각. 폐기하지 않았으면 `revokedAt`은 `null` |

#### ADM-03 기기 토큰 폐기 — `POST /admin/devices/{id}/revoke`

본문은 없다. 폐기 시각을 기록하며 그 기기의 다음 요청부터 401이다. 이미 폐기한 기기에 다시 보내도 204다(SD-14).

응답 204. 오류는 404 `NOT_FOUND`.

#### SYS-01 헬스 체크 — `GET /actuator/health`

인증 없이 상태만 준다. 정상이면 200 `{ "status": "UP" }`, 이상이면 503 `{ "status": "DOWN" }`이다. 구성 요소별 세부 정보는 보이지 않고, 웹으로 여는 Actuator 엔드포인트는 `health` 하나다(Spring Boot 기본값 유지). 외부 점검 서비스가 5분마다 부르고, 배포 스크립트도 이 값으로 배포 성공을 판정한다(설계서 12.2).

### 5.9 MVP 이후 확장

S와 C 항목 가운데 엔드포인트가 아직 없는 것은 수용 방식만 정해 두고, 요청과 응답 형식은 구현할 때 이 문서에 더한다(설계서 9.5).

| 요구사항 | 순위 | 수용 방식 | 남은 결정 |
| --- | --- | --- | --- |
| FR-303-2 수수료 이상치 경고 | S | 화면이 제안값과 입력값의 차이로 확인 창을 띄운다. API 변경 없음 | — |
| FR-312 오늘 입력 현황 | S | STAT-01의 `todayStatus` | — |
| FR-409, FR-410 금액과 메모 검색 | S | QRY-01과 QRY-02에 검색 쿼리를 더한다. 이름은 `minNetProfit`, `maxNetProfit`, `memo`(부분 일치)를 제안한다 | 검색할 때 누적 순수익의 기준 (AQ-03) |
| FR-411 조회 결과 CSV | S | QRY-01과 같은 쿼리를 받는 내려받기 엔드포인트를 더한다. UTF-8 BOM, `YYYY-MM-DD`, 쉼표 없는 소수 2자리, 계정은 계정명으로 표기한다 (SRS 5.4) | 경로 |
| NFR-403 전체 데이터 CSV | S | 정산 기록과 시드 이력을 내려받는다. 버튼은 계정 탭 아래에 두며(화면 설계서 SCR-06), 기존 스프레드시트 기록을 그만두기 전에 제공한다 | 파일 구성 (AQ-05) |
| FR-504 일평균 순수익 | S | STAT-03의 `total`에 `dailyAverageNetProfit`을 더해 통계 화면의 통합 수익률 아래에 보인다(SCR-05). 분모는 달력 일수이고 종료일이 오늘 이후면 오늘까지로 자른다(CAL-12). 구간 전체가 오늘 이후면 `null` | 반올림 규칙 (AQ-04) |
| FR-505 월별 추이 그래프 | S | STAT-02 응답을 그대로 막대로 그린다. 진행 중인 달은 `to`가 오늘이라 화면이 흐린 색과 마지막 날짜 라벨로 구분한다(SCR-05) | — |
| FR-510 계정별 시드와 누적 순수익의 합 | S | STAT-03 계정 행에 현재 적용 시드를 더하는 방식을 제안한다 | 표시 위치 |
| FR-506 연도별 집계 | C | 별도 엔드포인트 또는 STAT-02의 연 단위 합계 | 방식 |
| FR-313 수정과 삭제 이력 | C | 수정, 삭제 직전 값을 남기는 이력 테이블을 더한다. 조회 API는 화면이 생길 때 정한다 | 조회 방식 |

## 6. 공통 객체

여러 엔드포인트가 함께 쓰는 응답 객체는 다섯 개다. 한 엔드포인트에서만 쓰는 필드는 5장의 해당 API에, 오류 본문은 3.2에 적었다. 모든 필드는 응답에 항상 들어 있고, null 열이 가능인 필드만 `null`이 될 수 있다(SD-02).

### 6.1 Account

ACC-01\~04와 SEED-01이 준다.

| 필드 | 타입 | null | 설명 |
| --- | --- | --- | --- |
| `id` | number | 불가 | 계정 id |
| `name` | string | 불가 | 계정명. 화면이 계정을 구분하는 유일한 표기다 (FR-203) |
| `defaultFeeRate` | 수수료율 | 불가 | 수수료 제안값 계산용. 생성 시 `"0.2000"` (FR-206) |
| `active` | boolean | 불가 | 운용 중이면 `true` |
| `deactivatedOn` | 날짜 | 가능 | 운용 종료한 날. 운용 중이면 `null` (FR-204) |
| `deletable` | boolean | 불가 | 삭제된 기록을 포함해 기록이 한 건도 없으면 `true`. 화면은 이 값으로 \[삭제\]를 보인다 (FR-205, DI-08, SD-06) |
| `memo` | string | 가능 | 메모 |
| `currentSeed` | object | 불가 | 오늘 적용되는 시드(CAL-06)의 `id`, `amount`, `startDate`. 계정 카드의 현재 시드와 적용 시작일에 쓴다 (SD-06) |
| `createdAt`, `updatedAt` | 시각 | 불가 | 생성 시각과 수정 시각 |

시드 적용 시작일은 항상 오늘 이하이고(VAL-12) 계정마다 시드가 하나 이상 있으므로(VAL-11) `currentSeed`는 비지 않는다.

### 6.2 Seed

SEED-01\~03이 준다.

| 필드 | 타입 | null | 설명 |
| --- | --- | --- | --- |
| `id` | number | 불가 | 시드 이력 id |
| `accountId` | number | 불가 | 계정 id |
| `amount` | 금액 | 불가 | 시드 금액 |
| `startDate` | 날짜 | 불가 | 적용 시작일 |
| `endDate` | 날짜 | 가능 | 적용 종료일. 저장하지 않고 다음 이력의 적용 시작일 전날로 계산한다(SRS 4.1). 가장 늦은 이력은 `null` (SD-08) |
| `memo` | string | 가능 | 사유 메모 |
| `createdAt` | 시각 | 불가 | 등록 시각 |

### 6.3 Settlement

STL-01\~03이 준다. STL-02는 여기에 계정의 `defaultFeeRate`를 더한다.

| 필드 | 타입 | null | 설명 |
| --- | --- | --- | --- |
| `id` | number | 불가 | 정산 기록 id |
| `accountId` | number | 불가 | 계정 id |
| `accountName` | string | 불가 | 계정의 현재 이름. 기록에 저장하지 않는다 (FR-203) |
| `settlementDate` | 날짜 | 불가 | 정산일자 |
| `grossProfit` | 금액 | 불가 | 총수익. 사용자 입력값 |
| `fee` | 금액 | 불가 | 수수료. 사용자 입력값 |
| `netProfit` | 금액 | 불가 | 순수익. 저장할 때 서버가 계산한 값을 그대로 준다 (CAL-02, VAL-08) |
| `memo` | string | 가능 | 메모 |
| `createdAt`, `updatedAt` | 시각 | 불가 | 생성 시각과 수정 시각 |

### 6.4 SettlementRow

QRY-01의 `items` 행이다. Settlement에서 `createdAt`, `updatedAt`을 빼고 아래 필드를 더했으며, 화면 목록 행의 표시 항목(FR-401)과 같다.

| 필드 | 타입 | null | 설명 |
| --- | --- | --- | --- |
| `cumulativeNetProfit` | 금액 | 불가 | 조회 구간 전체를 (정산일자, 계정 id) 오름차순으로 누적했을 때 이 행까지의 순수익 합계 (AD-11) |

### 6.5 SeedBasis

STAT-03의 `total.seedBasis`로, 통합 수익률이 어떤 시드 합계로 산출됐는지 화면에 보이기 위한 값이다(FR-507). 인접 구간의 유효 시드 합계가 같으면 합친 뒤 구간이 하나면 `FIXED`, 둘 이상이면 `DAILY_SUM`이며, 합계가 0인 구간도 변화로 센다(설계서 8.4).

```json
{ "type": "FIXED", "amount": "15000.00", "fromAmount": null, "toAmount": null }
{ "type": "DAILY_SUM", "amount": null, "fromAmount": "15000.00", "toAmount": "10000.00" }
```

| 필드 | 타입 | null | 설명 |
| --- | --- | --- | --- |
| `type` | string | 불가 | `FIXED` 또는 `DAILY_SUM` |
| `amount` | 금액 | 가능 | `FIXED`일 때 유효 시드 합계. 화면 표시는 "유효 시드 합계 15,000.00 기준" |
| `fromAmount`, `toAmount` | 금액 | 가능 | `DAILY_SUM`일 때 조회 시작일과 종료일의 유효 시드 합계. 화면은 일자별 합산임을 함께 밝힌다 (CAL-09) |

## 7. 이 문서가 정한 사항과 확인 필요 사항

설계서가 정하지 않아 이 문서가 정한 사항은 14건(SD)이다. 확인 필요 사항 7건(AQ) 가운데 AQ-01과 AQ-02는 결정했고 AQ-06은 해소했으며, 나머지 4건이 열려 있다. 다른 문서에 반영할 내용과 반영 여부는 7.3에 모았다.

### 7.1 이 문서가 정한 사항 (SD)

| ID | 결정 | 이유 | 절 |
| --- | --- | --- | --- |
| SD-01 | 경로에 버전을 넣지 않는다 | 화면과 API가 한 jar로 함께 배포되어 둘의 버전이 어긋나지 않는다 | 2.1 |
| SD-02 | 본문이 없는 쓰기 요청은 형식을 검사하지 않고, 요청 본문의 정의되지 않은 필드는 400으로 거부한다. 성공 응답은 감싸지 않으며 값이 없는 필드도 `null`로 보낸다 | 본문 없는 운용 종료 요청이 415로 막히지 않게 하고, 바꿀 수 없는 필드를 보낸 실수(DI-07)를 드러낸다 | 2.1, 2.5 |
| SD-03 | 글자 수는 코드 포인트로 센다. 계정명과 메모는 앞뒤 공백을 지운 뒤 검사하고 빈 메모는 `null`로 저장한다. PATCH에서 `null`과 생략은 변경 없음, 빈 문자열은 메모 지움이다 | 서버 검증과 DB `VARCHAR`의 길이 기준을 맞추고, 래퍼 타입 없이 일반 DTO로 부분 수정을 받는다 | 2.1 |
| SD-04 | 오류 코드 8개(`INVITE_INVALID`, `ADMIN_UNAUTHORIZED`, `ORIGIN_REJECTED`, `NOT_FOUND`, `SEED_LAST_ONE`, `UNSUPPORTED_MEDIA_TYPE`, `ADMIN_LOCKED`, `INTERNAL_ERROR`)와 상태 403, 429를 더한다. `ACCOUNT_INACTIVE`는 이미 운용 종료한 계정의 운용 종료 요청에도 쓴다 | 설계서 9.1이 상태만 두고 코드를 정하지 않은 경우(404, 415, 500)와 다루지 않은 경우(초대 교환 실패, 운영자 인증, Origin 거부, 마지막 시드 삭제)를 채운다 | 3.3 |
| SD-05 | 초대 교환 성공은 204와 `Set-Cookie`, 실패는 원인 구분 없이 401 `INVITE_INVALID`다 | 성공 화면에 필요한 것은 쿠키뿐이고, FR-110이 실패 원인을 드러내지 않게 한다. 권한 없음 화면으로 가는 `DEVICE_UNAUTHORIZED`와도 구분된다 | 5.1 |
| SD-06 | 계정 목록은 운용 중인 계정을 먼저, 같은 상태끼리는 id 오름차순으로 주고 `currentSeed`와 `deletable`을 함께 준다. 시드 이력은 적용 시작일 최신순으로 준다 | 화면 설계서의 계정 카드(현재 시드, 운용 종료 계정은 맨 아래, 기록 없는 계정에만 \[삭제\])와 시드 이력 목록(최신순)을 한 번의 호출로 그린다. FR-209의 "시간순"은 화면 설계서대로 최신순으로 해석한다 | 5.2, 5.3, 6.1 |
| SD-07 | 계정 등록과 시드 등록의 `startDate`는 API에서 필수다. 설계서 9.2의 "적용 시작일 기본값은 오늘"은 화면이 서버의 `today`(운용 종료한 계정은 비활성일자)로 미리 채우는 값으로 해석한다 | 서버가 날짜를 몰래 채우지 않아 요청만 보고 결과를 알 수 있고, 일괄 저장의 `date`와 방식이 같다 | 5.2, 5.3 |
| SD-08 | 시드 이력 목록에 Account 객체와 이력별 `endDate`를, 정산 기록 단건 조회에 계정의 `defaultFeeRate`를 함께 준다 | 적용 기간 표시와 시드 날짜 입력 상한(VAL-12), 수정 화면의 제안값 되돌리기(DI-09)를 화면의 날짜 계산이나 추가 호출 없이 처리한다 | 5.3, 5.5, 6.2 |
| SD-09 | 일괄 저장의 `entries`가 비었거나 같은 `accountId`가 두 번 나오면 400이다 | 빈 저장과 같은 계정의 이중 입력을 트랜잭션 전에 막는다 | 5.4 |
| SD-10 | 거래내역과 계정별 집계의 `from`, `to`, 월별 집계의 `year`는 필수이고, 쿼리의 `accountId`가 없는 계정이면 404다 | 화면은 항상 프리셋, 직접 지정한 기간, 고른 연도를 보낸다. 서버가 기본값을 따로 두면 화면 표시와 어긋날 수 있다 | 5.6, 5.7 |
| SD-11 | 월별 집계는 `year`로 받아 기록이 있는 달만 최신순으로 주고, 진행 중인 달은 `to`를 오늘로 준다 | 화면 설계서 SCR-05가 월별 집계를 조회 기간이 아니라 연도 선택으로 보이고, 추이 그래프(FR-505)가 진행 중인 달을 구분한다 | 5.7 |
| SD-12 | 계정별 집계는 조회 구간과 겹치는 시드 구간이 있는 계정만 id 오름차순으로 준다. 유효 시드 합계가 양수인 날이 없으면 `combinedProfitRate`와 `seedBasis`는 `null`이다 | 조회 구간 전에 운용을 끝낸 계정을 카드에서 빼는 화면 설계(SCR-05)와 맞추고, 0으로 나누는 경우를 값으로 드러낸다 | 5.7 |
| SD-13 | 홈의 오늘 입력 현황(S)은 운용 중인 계정 전체를 `DONE`, `OPEN`으로 준다 | 화면 설계서 SCR-02 시안이 입력 완료 계정도 함께 보인다. 설계서 9.5는 미입력 계정 목록만 적고 있다 | 5.7 |
| SD-14 | 운영자 기기 목록은 등록 최신순으로 `status`(`ACTIVE`, `EXPIRED`, `REVOKED`)와 함께 주고, 기기 폐기는 이미 폐기된 기기에도 204다 | 분실 대응 때 상태를 바로 읽고, 폐기 명령을 다시 보내도 안전하다 | 5.8 |

### 7.2 확인 필요 사항 (AQ)

| ID | 항목 | 확인할 내용 | 영향 | 상태 |
| --- | --- | --- | --- | --- |
| AQ-01 | 운용 종료 계정에서 허용할 조작 | 화면 설계서 v0.1은 운용 종료한 계정의 이름 변경(설계서 Q-01)과 비활성일자까지의 시드 등록(SRS v1.2 FR-207, VAL-12, DB 설계서 DQ-01)을 막고 있었다. 화면을 SRS에 맞추기로 정해(2026-10-06), 운용 종료 계정 카드에 \[정보 수정\]을 남기고 시드 이력 화면은 등록 영역을 보이되 적용 시작일의 기본값과 상한을 비활성일자로 둔다. 화면 설계서 v0.2에 반영했다 | SCR-06, SCR-06-1, ACC-03, SEED-02 | 결정 (v0.3) |
| AQ-02 | 오늘 입력 현황의 형태 | 화면 시안대로 운용 중인 계정 전체와 입력 여부를 준다(SD-13). 설계서 9.5에 반영할 내용은 7.3에 있다 | STAT-01 | 결정 (v0.2) |
| AQ-03 | 검색할 때의 누적 순수익 | 금액과 메모 검색(FR-409, FR-410)을 걸었을 때 누적 순수익을 은행 잔액처럼 검색 전 구간 전체 기준으로 둘지, 검색 결과만으로 다시 누적할지 정한다. 앞의 방식이면 "내림차순 첫 행의 누적값 = 순수익 합계"라는 T-09, T-10의 검증식이 검색 조건에서는 성립하지 않는다 | QRY-01, QRY-02, T-09, T-10 | 열림 |
| AQ-04 | 일평균 순수익의 반올림 | CAL-12는 분모만 정하고 소수 셋째 자리 이하의 처리를 정하지 않았다. 저장값이 아닌 표시용 통계이므로 수익률(CAL-07)과 같은 HALF\_UP 소수 2자리를 제안하며, 정해지면 SRS에 반영한다 | FR-504, STAT-03 | 열림 |
| AQ-05 | 전체 데이터 CSV의 구성 | NFR-403은 정산 기록과 시드 이력을 함께 요구하지만 두 표의 열이 다르다. 파일 두 개로 낼지 압축 파일 하나로 낼지 정한다. 내려받기 위치는 화면 설계서가 계정 탭 아래로 정했다(SCR-06) | NFR-403 | 열림 |
| AQ-06 | DB 설계서와 대조 | DB 설계서 v0.1과 대조해 409로 바꾸는 제약 1건(`fk_settlement_account`)을 3.5에 더했다. SQL-01과 SQL-06은 DB 설계서 v0.2에 반영했다 | 3.5, 7.3 | 해소 (v0.2) |
| AQ-07 | 인증 흐름 변경 | 설계서 Q-03의 1주차 실기기 검증에서 흐름이 바뀌면(설치된 앱의 첫 실행에서 교환) AUTH-02를 부르는 시점과 SCR-01이 바뀐다. 요청과 응답 형식은 그대로 둘 수 있다 | AUTH-02, SCR-01 | 열림 |

### 7.3 다른 문서에 반영할 사항

이 문서와 맞추려면 아래 네 문서를 고쳐야 한다. DB 설계서와 화면 설계서는 v0.2로 반영했다. 설계서와 기획서는 프로젝트 파일이라 반영본(설계서 v0.5, 기획서 v0.7)을 파일로 만들었으므로, 프로젝트 파일을 바꾸면 끝난다. 같은 이름의 설계서 아티팩트는 v0.3에 머물러 있어 기준으로 쓰지 않는다.

| 문서 | 위치 | 반영할 내용 | 근거 | 상태 |
| --- | --- | --- | --- | --- |
| 설계서 | 9장 도입부 | "엔드포인트별 요청과 응답의 필드 단위 정의는 API 명세서가 정한다"를 더한다 | 1장 | v0.5 파일 작성 |
| 설계서 | 9.1 공통 규약과 오류 코드 표 | 상태 403, 429와 오류 코드 8개(`INVITE_INVALID`, `ADMIN_UNAUTHORIZED`, `ORIGIN_REJECTED`, `NOT_FOUND`, `SEED_LAST_ONE`, `UNSUPPORTED_MEDIA_TYPE`, `ADMIN_LOCKED`, `INTERNAL_ERROR`)를 더하고, `ACCOUNT_INACTIVE`를 재비활성 요청에도 쓴다 | SD-04 | v0.5 파일 작성 |
| 설계서 | 9.2 엔드포인트 표 | 계정 목록은 "운용 중인 계정 먼저, 삭제 가능 여부 포함", 시드 이력은 "적용 시작일 최신순, 적용 종료일 포함", 시드 변경 등록은 "적용 시작일 필수, 화면이 오늘(운용 종료 계정은 비활성일자)로 미리 채움", 월별 집계는 "`year`로 연도 지정, 기록 있는 달만 최신순"으로 고친다 | SD-06, SD-07, SD-08, SD-11 | v0.5 파일 작성 |
| 설계서 | 9.5 MVP 이후 항목 | FR-312를 "운용 중인 계정 전체의 오늘 입력 여부를 `todayStatus`로 더한다"로 고치고, FR-504 행("계정별 집계 응답에 일평균 순수익을 더한다")을 더한다 | SD-13, 5.9 | v0.5 파일 작성 |
| DB 설계서 | SQL-01 | SELECT에 `default_fee_rate`, `created_at`, `updated_at`, 현재 시드 id, 삭제 가능 여부를 더하고 정렬을 `ORDER BY a.active DESC, a.id`로 바꾼다. 삭제 가능 여부는 삭제된 기록까지 세야 하므로 DB 설계서 7.3의 DI-08 검사처럼 `settlement` 테이블을 읽는다 | SD-06, DI-08 | 반영 (v0.2) |
| DB 설계서 | SQL-06 | 서버가 `year`의 1월 1일과 12월 31일을 넘기고 `ORDER BY 1 DESC`로 최신 달부터 준다 | SD-11 | 반영 (v0.2) |
| 화면 설계서 | SCR-06, SCR-06-1 | 운용 종료 계정 카드에 \[정보 수정\]을 남기고, 시드 이력의 등록 영역을 비활성일자 상한으로 보인다(AQ-01). 기준 문서를 SRS v1.2, 설계서 v0.4, DB 설계서 v0.2, API 명세서 v0.3으로 바꾼다 | AQ-01 | 반영 (v0.2) |
| 기획서 | 5.1 진행 단계와 산출물 | 요구사항명세서 v1.2, 설계서 v0.5, DB 설계서 v0.2, 화면 설계서 v0.2, API 명세서 v0.3으로 상태를 맞춘다 | — | v0.7 파일 작성 |

## 부록 A. 변경 이력

| 버전 | 일자 | 변경 내용 |
| --- | --- | --- |
| v0.1 | 2026-10-06 | 최초 작성. 요구사항명세서 v1.2, 시스템 아키텍처 설계서 v0.4, 화면 설계서 v0.1 기준 |
| v0.2 | 2026-10-06 | DB 설계서 v0.1, 화면 설계서 v0.1(2026-09-30 수정본)과 대조. 409로 바꾸는 제약 1건 추가(3.5), 계정에 `deletable` 추가(6.1), 시드 이력을 최신순으로 바꾸고 `endDate` 추가(SEED-01, 6.2), 월별 집계를 연도 단위로 변경(STAT-02), 일평균 순수익의 수용 위치를 STAT-03으로 변경(5.9). AQ-02 결정, AQ-06 해소, AQ-01 범위 확장, 7.3 신설 |
| v0.3 | 2026-10-06 | AQ-01 결정. 운용 종료한 계정의 이름 변경과 비활성일자까지의 시드 등록을 화면에서도 허용하기로 하고, 7.3 가운데 DB 설계서와 화면 설계서 항목을 각 v0.2에 반영했다. 7.3에 반영 상태 열 추가. 같은 날 기준 문서와 1.2 참고 문서의 SRS를 v1.3, 설계서를 v0.6으로 갱신(버전 유지) |
