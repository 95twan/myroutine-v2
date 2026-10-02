# Part 1. 뼈대와 첫 기능

> **끝나면**: 이메일로 가입·로그인하고, 가게를 열고, 상품을 올리고, 재고를 동시성 문제 없이 예약할 수 있다.
> **인프라**: Postgres 하나 (Docker). Kafka·Redis는 아직 없다.
> **릴리스**: Part 1 완료 시 develop → main, 태그 `v0.1.0`

| 단계 | 제목 | 크기 |
|---|---|---|
| 1-1 | 프로젝트 생성 · Git 저장소 · 로컬 실행 | S |
| 1-2 | 테스트 환경과 CI | S |
| 1-3 | 이메일 회원가입 (+ 공통 에러 응답, traceId) | M |
| 1-4 | 로그인과 JWT 인증 | M |
| 1-5 | 내 정보 · 배송지 | S |
| 1-6 | 가게 개설 · 조회 · 수정 (모듈 경계 도입) | M |
| 1-7 | 상품 등록 · 조회 | M |
| 1-8 | 상품 수정 · 상태 변경 · 재고 증감 | M |
| 1-9 | 재고 예약과 동시성 테스트 | M |

크기: S(1일 이내) · M(2~3일) · L(4~5일)

---

## 1-1. 프로젝트 생성 · Git 저장소 · 로컬 실행 (S)

**목표**
- 앱이 로컬 Postgres에 연결된 상태로 뜨고, `/actuator/health`가 `UP`을 응답한다.
- Git 저장소가 Git Flow(main + develop)로 구성되고 GitHub 보호 규칙이 걸려 있다.

**왜 지금**: 모든 단계의 바닥이다. 여기서는 기능을 만들지 않는다.

**새로 등장**

| 개념·도구 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| Spring Boot 4 / Java 25 | 3.x와 스타터 이름이 일부 다르다. start.spring.io로 생성해서 시작한다 | 개발 가이드 §1.1 |
| Docker Compose | 로컬 인프라를 파일 하나로 띄운다. 지금은 Postgres만 | |
| Flyway | DB 테이블을 SQL 파일로 버전 관리한다. 앱이 뜰 때 아직 실행 안 된 파일만 순서대로 실행 | 개발 가이드 §2 |
| 프로필 | `local`(내 PC), `test`(테스트), `prod`(운영)마다 설정을 나눈다 | 개발 가이드 §15 |
| Git Flow | main(릴리스) + develop(개발) + feature(단계별) | [Git 정책](../git-policy.md) |

**할 일**
1. [Git 정책 §9](../git-policy.md)대로 저장소를 만든다: `docs/` 커밋 → `develop` 생성 → GitHub push → 기본 브랜치 `develop`, 보호 규칙 설정.
2. `feature/1-1-project-setup` 브랜치를 만든다.
3. start.spring.io에서 생성한다.
   - Gradle - Groovy, Java 25, Spring Boot 4.x 최신 GA, Group `com.myroutine`, Artifact `myroutine`
   - **Package name을 `com.myroutine`으로 직접 고친다.** 자동으로 채워지는 `com.myroutine.myroutine`을 그대로 두면 안 된다. Spring Modulith는 메인 클래스 패키지의 바로 아래 패키지(`com.myroutine.member` 등)를 모듈로 인식한다
   - 생성된 메인 클래스 `MyroutineApplication`은 `MyRoutineApplication`으로 이름을 바꾼다 (문서 표기와 맞춤, 선택)
   - 의존성: Spring Web, Spring Data JPA, Validation, PostgreSQL Driver, Flyway Migration, Lombok, Spring Boot Actuator, Testcontainers
   - Security는 1-3에서 추가한다(지금 넣으면 모든 API가 막혀서 헷갈린다).
4. `build.gradle`에 Java toolchain 25를 고정하고 `./gradlew build`가 통과하는지 본다.
5. `docker/docker-compose.yml`에 Postgres 하나를 만든다.
   - 이미지는 `pgvector/pgvector:pg17` (Part 6 추천 기능에서 vector 확장을 쓰므로 처음부터 이 이미지)
   - 포트는 `127.0.0.1:5432:5432`처럼 내 PC에서만 접근하게, 데이터는 named volume
6. 설정 파일
   - `application.yml`: 공통 설정 (`spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`, actuator 관리 포트 `management.server.port: 8081`)
   - `application-local.yml`: DB 주소, 계정은 `${DB_USERNAME}` 같은 환경변수
   - `.env.example`: 환경변수 이름만, `.gitignore`: [Git 정책 §8](../git-policy.md)
7. 첫 Flyway 마이그레이션 `src/main/resources/db/migration/common/V{yyyyMMddHHmm}__common_init.sql`: `CREATE SCHEMA IF NOT EXISTS common;`
   - Flyway는 `classpath:db/migration` 아래 하위 폴더까지 읽는다. 모듈별 폴더를 쓸 수 있다.
8. (Claude) 개발 가이드 §1.1의 확정 버전 표에 실제 버전을 기록한다(나머지는 등장할 때 채운다).
9. (Claude) README에 실행 방법(인프라 → 앱 → 헬스체크)을 적는다.

**완료 확인**
```bash
docker compose -f docker/docker-compose.yml up -d
./gradlew bootRun --args='--spring.profiles.active=local'
curl -s localhost:8081/actuator/health          # {"status":"UP"}
```
- [ ] DB의 `flyway_schema_history`에 1건, `common` schema가 있다
- [ ] `git status`에 `.env`가 보이지 않는다
- [ ] PR이 develop으로 squash merge되고 브랜치가 삭제됐다

**리뷰 때 물어볼 것**
- `open-in-view`를 끈 이유는? 켜져 있으면 어떤 문제가 생기나?
- `ddl-auto`를 `update`가 아니라 `validate`로 둔 이유는?
- DB 비밀번호는 어디에 있고, 운영에서는 어떻게 주입하나?

---

## 1-2. 테스트 환경과 CI (S)

**목표**
- 테스트가 **진짜 Postgres**(Docker) 위에서 돈다.
- PR을 올리면 GitHub Actions가 빌드와 전체 테스트를 실행하고, 실패하면 머지할 수 없다.

**왜 지금**: 1-3부터 모든 단계가 테스트로 완료를 증명한다. 원 프로젝트는 CI가 테스트를 하나도 돌리지 않았다(INF-01).

**새로 등장**

| 개념·도구 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| Testcontainers | 테스트가 시작될 때 Docker로 Postgres를 띄우고, 끝나면 정리한다 | 개발 가이드 §13.3 |
| `@ServiceConnection` | 띄운 컨테이너의 주소를 Spring 설정에 자동으로 연결 | |
| GitHub Actions | PR·push마다 정해진 명령(`./gradlew build`)을 실행하는 CI | |

> **왜 H2(메모리 DB)가 아니라 진짜 Postgres인가**: 이 프로젝트는 Postgres 고유 기능(advisory lock, `ON CONFLICT`, jsonb, 부분 인덱스, pgvector)을 쓴다. H2에서 통과해도 Postgres에서 깨질 수 있다.

**할 일**
1. `src/test/.../support/IntegrationTestSupport.java`: Postgres 컨테이너를 **static 블록에서 한 번만** 시작하는 싱글턴(개발 가이드 §13.3). Kafka·Redis는 나중에 추가한다.
2. `application-test.yml`
3. 테이블 정리 유틸: 각 테스트 후 `flyway_schema_history`를 뺀 모든 테이블을 TRUNCATE. 통합 테스트에 `@Transactional`을 붙이지 않는 대신 이걸로 격리한다.
4. 샘플 통합 테스트: 앱이 뜨고 `common` schema가 있는지 확인
5. `.github/workflows/ci.yml`: develop·main 대상 PR과 push에서 실행. Java 25(temurin) 설정, Gradle 캐시, `./gradlew build`
6. (Claude) `.github/pull_request_template.md` ([Git 정책 §4.3](../git-policy.md))
7. GitHub 보호 규칙에서 CI 체크를 필수로 지정

**완료 확인**
- [ ] 로컬 `./gradlew test` 통과 (Docker 실행 중)
- [ ] CI 로그에서 실행된 테스트 수가 0보다 크다
- [ ] 일부러 실패하는 테스트를 push하면 CI가 빨간불이 되고 머지 버튼이 막힌다 (확인 후 되돌림, PR에 스크린샷)
- [ ] 테스트 클래스를 2개 만들어도 컨테이너가 한 번만 뜬다 (로그로 확인)

**리뷰 때 물어볼 것**
- 통합 테스트에 `@Transactional`을 붙이면 어떤 동작이 검증되지 않나?
- 컨테이너를 테스트 클래스마다 새로 띄우면 무엇이 문제인가?

---

## 1-3. 이메일 회원가입 (+ 공통 에러 응답, traceId) (M)

**목표**
- `POST /api/auth/signup`으로 회원이 생긴다. 비밀번호는 해시로 저장된다.
- 이메일 중복이면 409, 입력이 잘못되면 400과 필드별 오류를 준다.
- 모든 응답에 `X-Request-Id`(traceId)가 있고, 에러 응답 본문의 `traceId`와 같다.

**왜 지금**: 모든 기능의 주체가 회원이다. 첫 도메인이라 **엔티티 작성법과 패키지 구조**를 여기서 익히고, 이후 모든 모듈이 같은 방식을 따른다.

**새로 등장**

| 개념·도구 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| 모듈 4계층 패키지 | `web → application → domain ← infrastructure` | 개발 가이드 §3 |
| 엔티티 작성 규칙 | setter 금지, 정적 팩토리로 생성, 상태 변경은 의도가 드러나는 메서드로 | 개발 가이드 §5.1 |
| UUIDv7 + `@Version` | 앱에서 시간순 ID를 만든다. `@Version`이 있어야 `save()`가 불필요한 SELECT를 안 한다 | 개발 가이드 §5.1 |
| Spring Security `PasswordEncoder` | BCrypt로 비밀번호를 해시. 같은 비밀번호도 매번 다른 해시가 나온다(salt) | |
| 공통 에러 응답 | `ErrorCode` → `BusinessException` → `GlobalExceptionHandler`가 `{code, message, traceId, details}`로 변환 | 개발 가이드 §11 |
| traceId | 요청마다 ID를 만들어 로그(MDC)와 응답 헤더에 넣는다. 나중에 Kibana에서 이 값으로 검색한다 | 개발 가이드 §14.1 |

**할 일**
1. **Security 설정**: 의존성 추가 → `SecurityFilterChain`을 무상태(세션 없음)로, CSRF 비활성화(API 서버), `/api/auth/**`와 헬스체크만 허용. 나머지는 1-4에서 인증을 붙인다.
2. **common**
   - `Ids`(UUIDv7 생성), `ErrorCode` 인터페이스, `CommonErrorCode`(INVALID_REQUEST 등), `BusinessException`, `GlobalExceptionHandler`
   - `TraceIdFilter`: 헤더 `X-Request-Id`가 있으면 사용, 없으면 생성 → MDC + 응답 헤더 → 요청 끝에 MDC 비움
3. **마이그레이션** `db/migration/member/..__member_create_member.sql`
   - `member` schema, `member` 테이블([ERD member](../02-design/03-erd.md)): id, email(UK), nickname(UK), name, phone, password_hash, email_verified_at, status, roles(jsonb), token_version, withdrawn_at, version, created_at, updated_at
   - 제약 이름을 규칙대로 붙인다: `uk_member_email`, `uk_member_nickname`
4. **member.domain**
   - `Member`: `Member.signUp(email, encodedPassword, nickname, name)` 정적 팩토리. 이메일은 소문자로 정규화, roles = {USER}, status = ACTIVE
   - `MemberStatus`: 전이 규칙(ACTIVE⇄BANNED, ACTIVE→WITHDRAWN)을 enum 안에 (개발 가이드 §5.4)
   - `MemberRole`, `MemberRepository`(인터페이스), `MemberErrorCode`
   - roles jsonb 매핑: `@JdbcTypeCode(SqlTypes.JSON)`
5. **member.application** `SignupService`
   - 이메일·닉네임 중복 확인 → 비밀번호 해시 → 저장
   - **동시에 같은 이메일로 가입하면** 중복 확인을 둘 다 통과할 수 있다. 최종 방어는 DB unique 제약이고, 위반 예외는 트랜잭션 밖(`GlobalExceptionHandler`)에서 제약 이름을 보고 409로 바꾼다.
6. **member.web** `AuthController`
   - `SignupRequest`: `@Email`, 비밀번호 규칙(POL-18: 8~64자, 영문·숫자 포함), 닉네임 길이
   - 응답은 `{memberId}` (토큰은 1-4에서 추가)

**완료 확인**
- 단위 테스트
  - [ ] `Member.signUp`: 이메일 소문자 정규화, 기본 역할 USER, 상태 ACTIVE
  - [ ] `MemberStatus`: 허용·금지 전이 전체 (파라미터화 테스트)
- 통합 테스트
  - [ ] 가입 → 201, DB의 password_hash가 평문과 다르고 `PasswordEncoder.matches`가 true
  - [ ] 같은 이메일 재가입 → 409 `MEMBER_EMAIL_DUPLICATED`
  - [ ] **같은 이메일로 동시에 2건 → 1건 201, 1건 409** (500이 나면 안 됨)
  - [ ] 잘못된 이메일·짧은 비밀번호 → 400 `INVALID_REQUEST`, `details`에 필드별 메시지
  - [ ] 응답 헤더 `X-Request-Id`와 에러 본문 `traceId`가 같다
  - [ ] 예상 못 한 예외 → 500, 메시지에 내부 정보(SQL, 클래스명)가 없다
```bash
curl -i -X POST localhost:8080/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"buyer@test.com","password":"pass1234","nickname":"buyer","name":"김구매"}'
```

**리뷰 때 물어볼 것**
- setter 없이 회원 상태를 바꾸려면 어떻게 하나? setter가 있으면 무엇이 위험한가? (As-Is ORD-06)
- 중복 검사를 `existsByEmail`만으로 하면 왜 부족한가?
- BCrypt를 쓰는 이유는? SHA-256으로 해시하면 안 되나?
- traceId는 어디서 생겨서 어디까지 따라가나?

---

## 1-4. 로그인과 JWT 인증 (M)

**목표**
- `POST /api/auth/login`이 Access 토큰(JWT)을 준다. 가입 응답도 토큰을 준다.
- `Authorization: Bearer {토큰}`으로 `GET /api/members/me`를 호출할 수 있다.
- 비밀번호가 틀리든 이메일이 없든 **같은 401 응답**을 준다.

**왜 지금**: 이후 모든 API가 "누가 요청했는가"를 알아야 한다.

**새로 등장**

| 개념·도구 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| JWT (jjwt) | `헤더.내용.서명`. 서버가 비밀키로 서명해서, 내용을 위조하면 서명 검증에서 걸린다. 원 프로젝트 게이트웨이에서 쓴 jjwt 그대로 | [ADR-006](../adr/ADR-006-auth.md) |
| 인증 필터 | 요청마다 토큰을 검증해 "누구인지"를 SecurityContext에 넣는다 | |
| `@CurrentMember` | 컨트롤러 파라미터로 요청자 ID를 받는 리졸버. 요청 바디·헤더의 memberId는 믿지 않는다 | 개발 가이드 §7 |

> **이 단계의 한계 (일부러 남김)**: refresh 토큰이 없어서 Access 토큰(1시간)이 만료되면 다시 로그인해야 하고, 로그아웃·강제 차단이 안 된다. Part 4에서 Redis와 함께 완성한다.

**할 일**
1. `JwtProperties`(`@ConfigurationProperties` + `@Validated`): 서명 키(환경변수, 32바이트 이상), Access 만료 시간
2. `JwtProvider`: 발급(memberId, roles, tokenVersion 클레임) / 검증(만료·서명 오류를 구분). 시각은 `Clock`을 주입받는다(테스트에서 만료를 흉내 내기 위해).
3. `JwtAuthenticationFilter`: 헤더에서 토큰 추출 → 검증 → Authentication 설정. 토큰이 없으면 그냥 통과(인가 단계에서 막힘)
4. `AuthenticationEntryPoint`(401), `AccessDeniedHandler`(403)가 공통 에러 형식으로 응답하게 한다
5. `CurrentMemberArgumentResolver`
6. `LoginService`: 이메일로 조회 → 비밀번호 비교 → 상태 확인(BANNED → 403 `MEMBER_BANNED`, WITHDRAWN → `LOGIN_FAILED`) → 토큰 발급
7. `SecurityFilterChain`: `/api/auth/**` 허용, 나머지 인증 필요
8. `GET /api/members/me` (조회만, 수정은 1-5)

**완료 확인**
- 단위 테스트 `JwtProvider`
  - [ ] 발급한 토큰을 검증하면 같은 memberId·roles
  - [ ] 만료된 토큰 → 만료 오류 (`Clock`을 앞으로 돌려서)
  - [ ] 서명을 조작한 토큰, 다른 키로 서명한 토큰 → 검증 실패
- 통합 테스트
  - [ ] 로그인 성공 → 토큰 → `/me` 200
  - [ ] **틀린 비밀번호와 없는 이메일 → 둘 다 401 `LOGIN_FAILED`, 메시지 동일**
  - [ ] 토큰 없이 `/me` → 401, 공통 에러 형식
  - [ ] 만료 토큰 → 401 `TOKEN_EXPIRED`
```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"buyer@test.com","password":"pass1234"}' | jq -r .accessToken)
curl -s localhost:8080/api/members/me -H "Authorization: Bearer $TOKEN"
```

**리뷰 때 물어볼 것**
- 비밀번호가 틀린 경우와 이메일이 없는 경우를 같은 응답으로 주는 이유는?
- JWT는 서버에 상태가 없는데, 로그아웃이나 강제 차단은 어떻게 하나? (Part 4 예고)
- 서명 키가 유출되면 무슨 일이 생기고, 어떻게 대응하나? (As-Is MEM-01)
- 토큰에 역할을 넣었는데 역할이 바뀌면? (ADR-006)

---

## 1-5. 내 정보 · 배송지 (S)

**목표**: 내 정보 수정, 배송지 등록·수정·삭제·조회. 기본 배송지는 회원당 최대 1개.

**왜 지금**: 2-4 체크아웃에서 배송지를 쓴다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 소유권 조회 | `findByIdAndMemberId(id, me)` — 남의 데이터는 "없는 것"처럼 404 (INV-11, As-Is ORD-08) |
| 부분 unique 인덱스 | `CREATE UNIQUE INDEX ... ON member_address(member_id) WHERE is_default` — "기본 배송지는 1개"를 DB가 보장 |

**할 일**
1. `member_address` 마이그레이션 + 부분 unique 인덱스
2. `MemberAddress`(별도 애그리거트, memberId로 참조)
3. 기본 배송지 변경: 기존 기본 해제 → 새 기본 지정 순서로(인덱스 위반 방지)
4. `PATCH /api/members/me`(닉네임·이름·전화), `/api/members/me/addresses` CRUD

**완료 확인**
- [ ] 기본 배송지를 바꾸면 이전 기본이 해제된다
- [ ] 동시에 두 배송지를 기본으로 지정해도 기본은 1개다
- [ ] 다른 회원의 배송지 조회·수정·삭제 → 404
- [ ] 닉네임 중복 → 409

**리뷰 때 물어볼 것**
- 남의 리소스에 403이 아니라 404를 주는 이유는?
- "기본 배송지 1개"를 애플리케이션 코드로만 보장하면 무엇이 문제인가?

---

## 1-6. 가게 개설 · 조회 · 수정 (모듈 경계 도입) (M)

**목표**
- 회원이 가게를 열고(즉시 운영 상태), 자기 가게를 조회·수정한다.
- 모듈이 2개가 되는 시점이라 **모듈 경계를 테스트로 강제**하기 시작한다.

**왜 지금**: 상품(1-7)은 가게에 속한다. 그리고 모듈이 둘 이상이 되는 순간부터 "다른 모듈 내부를 건드리지 않는다"는 규칙이 의미가 생긴다.

**새로 등장**

| 개념·도구 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| Spring Modulith | 모듈 간 의존 규칙 위반을 테스트로 잡는다. 테스트 1개 + `package-info.java` | 개발 가이드 §4.1 |
| 모듈 공개 API | 다른 모듈은 `shop.api` 패키지의 인터페이스·DTO만 쓴다 | 개발 가이드 §4.2 |
| 소유권 기반 인가 | 판매자 기능은 "SELLER 역할이 있나"가 아니라 "이 가게의 주인인가"로 판단 | [ADR-006](../adr/ADR-006-auth.md) |

> SELLER 역할 부여는 Part 3(3-1)에서 이벤트로 붙인다. 소유권으로 인가하기 때문에 역할이 없어도 지금 기능은 모두 동작한다.

**할 일**
1. Spring Modulith 의존성(core, test) 추가, `ModularityTest`(`ApplicationModules.of(...).verify()`)
2. `package-info.java`: `common`은 OPEN, `member`·`shop`에 `allowedDependencies`, `shop/api/package-info.java`에 `@NamedInterface("api")`
3. `shop` 마이그레이션([ERD shop](../02-design/03-erd.md)): `uk_shop_business_number`, version
4. `Shop` 엔티티(상태 ACTIVE·CLOSED, 폐업은 3-2), 소유자 확인 메서드
5. API: `POST /api/shops`, `GET /api/shops/me`, `GET /api/shops/{id}`, `PATCH /api/shops/{id}`
6. `ShopApi`(shop.api): `getOwnerId(shopId)`, `isActive(shopId)`, `getActiveShops(ids)` — record DTO 반환, 구현은 application 패키지
7. 낙관적 락 충돌(`ObjectOptimisticLockingFailureException`) → 409 `CONFLICT_RETRY`

**완료 확인**
- [ ] `ModularityTest` 통과
- [ ] **일부러 shop에서 `member.domain.Member`를 import → verify 실패 확인** (커밋하지 않고 PR에 결과 기록)
- [ ] 개설 → 내 가게 목록에 보임
- [ ] 다른 회원이 수정 → 403
- [ ] 같은 가게를 동시에 수정 → 하나는 409
- [ ] 사업자번호 중복 → 409

**리뷰 때 물어볼 것**
- 모듈 경계를 테스트로 강제하지 않으면 시간이 지나 어떻게 되나? (As-Is BAT-03)
- 판매자 기능을 SELLER 역할이 아니라 소유권으로 인가하면 무엇이 좋은가?
- 낙관적 락 충돌이 나면 사용자는 무엇을 보게 되나?

---

## 1-7. 상품 등록 · 조회 (M)

**목표**
- 판매자가 자기 가게에 상품을 등록한다(초기 재고 포함).
- 누구나 판매 중인 상품 목록(커서 페이징)과 상세를 본다.

**왜 지금**: 주문할 대상이 필요하다.

**새로 등장**

| 개념 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| 모듈 간 호출 | product가 `ShopApi`로 가게 소유자·운영 상태를 확인 | 개발 가이드 §4.2 |
| `Money` 값 객체 | 금액을 `long`으로 들고 다니지 않고, 음수 불가·연산 규칙을 가진 객체로 | 개발 가이드 §5.3 |
| keyset(커서) 페이징 | `WHERE (created_at, id) < (:c, :id) ORDER BY ... LIMIT n`. offset과 달리 뒤 페이지도 빠르고, 중간에 데이터가 추가돼도 중복·누락이 없다 | 개발 가이드 §8.1 |
| DB CHECK로 불변식 | `CHECK (available + reserved + sold = received)` — 재고 등식(INV-03)을 DB가 보장 | [ADR-008](../adr/ADR-008-inventory-reservation.md) |

**할 일**
1. common에 `Money` + `MoneyConverter`(autoApply)
2. 마이그레이션: `product`, `product_image`, `stock`(수량 4개 + CHECK들), `stock_movement`
3. `Product` 엔티티(상태 ON_SALE·HIDDEN·DISCONTINUED, 전이는 1-8), `Stock`
4. 상품 등록: 가게 소유자 + 가게 ACTIVE 확인(ShopApi) → **상품·재고·입고 이력을 한 트랜잭션**으로 저장
5. API: `POST /api/shops/{shopId}/products`, `GET /api/shops/{shopId}/products`(판매자용), `GET /api/products`(ON_SALE, 카테고리, 커서), `GET /api/products/{id}`(재고 있음 여부 포함)
6. 커서는 `(createdAt, id)`를 Base64로 인코딩한 문자열. 응답 `{items, nextCursor}`
7. `product/package-info.java`에 `allowedDependencies = {"common", "shop::api"}`

> 상품 등록의 멱등 처리(`Idempotency-Key`)는 2-4에서 공통 장치를 만들 때 붙인다.

**완료 확인**
- 단위
  - [ ] `Money`: 음수 생성 불가, 뺄셈 결과 음수 불가, 곱셈 오버플로 검사
- 통합
  - [ ] 등록 → 상품·재고·이력 각 1건
  - [ ] 재고 저장 단계에서 예외를 내면 상품도 저장되지 않는다
  - [ ] 남의 가게에 등록 → 403, CLOSED 가게 → 422 `SHOP_NOT_ACTIVE`
  - [ ] 커서 페이징: 첫 페이지를 받은 뒤 새 상품을 추가해도 두 번째 페이지에 중복·누락이 없다
  - [ ] HIDDEN 상품은 목록에 없다
  - [ ] 목록 조회 쿼리 수가 상품 수와 무관하다 (N+1 없음)

**리뷰 때 물어볼 것**
- offset 페이징 대신 keyset을 쓴 이유는? keyset의 단점은?
- 금액을 `BigDecimal`이나 `long` 대신 `Money`로 감싸는 이유는?
- 상품과 재고를 한 트랜잭션에 넣지 않으면 무슨 일이 생기나? (As-Is CAT-04, `must.md` #6)

---

## 1-8. 상품 수정 · 상태 변경 · 재고 증감 (M)

**목표**: 상품 정보·가격 수정(가격 이력), 상태 변경, 재고 입고·조정.

**왜 지금**: 1-9 예약 전에 재고를 안전하게 바꾸는 방법(조건부 UPDATE)을 먼저 익힌다.

**새로 등장**

| 개념 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| 상태 전이 규칙 | 허용된 이전 상태에서만 바뀌게 enum에 전이표를 둔다 | 개발 가이드 §5.4, [상태머신](../02-design/04-state-machines.md) |
| 낙관적 락 vs 조건부 UPDATE | 상품 정보 수정은 `@Version`(충돌 시 409), 재고 수량은 **조건부 UPDATE 한 문장**(충돌 자체가 없음) | 개발 가이드 §10 |

**할 일**
1. `price_history` 마이그레이션
2. `PATCH /api/shops/{shopId}/products/{id}`: 가격이 바뀌면 같은 트랜잭션에서 이력 저장. DISCONTINUED는 수정 불가
3. `PATCH .../status`: ON_SALE⇄HIDDEN, 둘 다 → DISCONTINUED(되돌릴 수 없음)
4. `POST .../stock-adjustments` `{delta, reason}`
   - `UPDATE stock SET available = available + :d, received = received + :d WHERE product_id = :id AND available + :d >= 0`
   - 반영된 행 수가 0이면 422 (재고 부족)
   - `stock_movement`에 기록
5. 재고 정합성 점검 쿼리(리포지토리 메서드): 등식이 깨진 행 조회 (CHECK가 있으니 평소엔 0건)

> 가격 변경 이벤트(구독자 알림 등)는 Part 3(3-3)에서 붙인다.

**완료 확인**
- [ ] 상태 전이 허용·금지 전체 (파라미터화)
- [ ] 가격 변경 → 이력 1건, 이름만 변경 → 이력 없음
- [ ] DISCONTINUED 상품 수정 → 409
- [ ] **동시에 입고 100건 → 수량 정확히 합산**
- [ ] 감소 조정으로 음수가 되면 422, 수량 변화 없음

**리뷰 때 물어볼 것**
- 재고를 "현재 수량 = X"로 덮어쓰면 왜 위험한가? (As-Is CAT-06)
- `@Version`과 조건부 UPDATE는 각각 언제 쓰나?

---

## 1-9. 재고 예약과 동시성 테스트 (M)

**목표**
- 체크아웃이 쓸 재고 예약 API(`ProductApi`)를 만든다: 예약 · 확정 · 해제 · 만료 · 복구.
- **재고 100개 상품에 1,000명이 동시에 예약하면 정확히 100명만 성공**하는 것을 테스트로 증명한다.

**왜 지금**: 2-4 체크아웃 직전 준비이고, 이 프로젝트의 첫 번째 동시성 문제다. 이 테스트가 이력서의 첫 번째 증거가 된다.

**새로 등장**

| 개념 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| 재고 예약 | 결제 전에 재고를 임시로 잡아둔다. `available → reserved` 이동 | [ADR-008](../adr/ADR-008-inventory-reservation.md) |
| 멱등 연산 | 같은 orderId로 다시 호출해도 결과가 같다. 나중에 재시도가 안전해진다 | 개발 가이드 §4.2 |
| 데드락 회피 | 여러 상품을 잠글 때 항상 **상품 ID 오름차순**으로 | 개발 가이드 §10 |
| 동시성 테스트 | 스레드 N개를 `CountDownLatch`로 동시에 출발시키고 결과와 불변식을 검사 | 개발 가이드 §13.5 |

**할 일**
1. `stock_reservation` 마이그레이션: `uk_stock_reservation_order_product (order_id, product_id)`, 상태, expires_at
2. `ProductApi`
   - `getForCheckout(productIds)`: 가격·상태·shopId·이름·썸네일 (체크아웃 가격 확정용)
   - `reserve(orderId, items)`: ON_SALE 확인 → 상품 ID 정렬 → 상품마다 `UPDATE ... SET available = available - q, reserved = reserved + q WHERE available >= q` → 하나라도 0건이면 `OUT_OF_STOCK` 예외(호출한 쪽 트랜잭션이 롤백됨) → 예약 레코드·이력 저장. 같은 orderId 재호출은 무시
   - `commitReservation(orderId)`: HELD → COMMITTED, `reserved → sold`
   - `releaseReservation(orderId)` / `expireReservation(orderId)`: HELD → RELEASED/EXPIRED, `reserved → available`
   - `restore(orderId, productId, qty, refundId)`: `sold → available`, 이력 unique로 refundId당 한 번
   - 모든 상태 변경은 CAS(`WHERE status = 'HELD'`)
3. 아직 order 모듈이 없으므로 테스트에서 `ProductApi`를 직접 호출한다

**완료 확인**
- [ ] **재고 100에 동시 예약 1,000건 → 성공 100, available 0, `available + reserved + sold = received`**
- [ ] 주문 X는 [A, B], 주문 Y는 [B, A] 순서로 동시에 예약 → 데드락 없음 (50회 반복)
- [ ] commit 두 번 → 한 번만 반영
- [ ] 이미 COMMITTED인 예약을 release → 아무 일도 없음 (확정된 재고가 풀리면 안 됨)
- [ ] 같은 refundId로 restore 두 번 → 한 번만
- [ ] DISCONTINUED 상품 예약 → 실패

**리뷰 때 물어볼 것**
- 비관적 락(`SELECT FOR UPDATE`) 대신 조건부 UPDATE를 쓴 이유는?
- 상품 3개 중 3번째에서 재고가 부족하면, 이미 줄인 1·2번째 재고는 어떻게 되나?
- 데드락은 왜 생기고, 정렬하면 왜 안 생기나?
- Redis로 재고를 관리하면 무엇이 좋고 무엇이 어려운가? (Stage 2 실험 예고)

---

## Part 1 완료
- [ ] develop → main PR(merge commit), 태그 `v0.1.0`, GitHub Release에 완료 단계와 동시성 테스트 결과 요약
- [ ] Part 2 문서를 다시 읽고, Part 1에서 배운 점을 반영해 다듬는다(필요하면 Claude에게 요청)
