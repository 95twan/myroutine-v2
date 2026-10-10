# Part 1. 뼈대와 첫 기능

> 버전 0.23 · 2026-10-10 · **Part 1 마무리 문서 정리**: 남은 "확인 필요" 4건을 직접 확인해 확정(감사 시각 `Instant`, S3 없는 키 삭제, 저장소 장애 시 기동 실패, cascade INSERT 전 select 없음), 공통 규칙 E에 검증 메시지 표준 문구를 추가(메시지 없는 제약이 영어 기본 문구·정규식을 응답에 내보내던 문제), "수정 사항" 절 구현 완료 표기, "Part 1 완료"에 Part 2 시작 전 결정(`subscribable`)과 Release 노트용 알려진 한계 추가
> 0.22 · 2026-10-10 · **구현으로 해소된 "확인 필요" 3건 정리**: 1-4 JSON 변환 주입 타입(`ObjectMapper`), 1-7 쿼리 파라미터 검증 예외 매핑(`HandlerMethodValidationException` → 400), 1-10 `TransactionTemplate` 생성자 주입. 실제 코드·테스트와 대조해 확인한 것만 고쳤다
> 0.21 · 2026-10-10 · **1-11 "할 일"을 S1~S8 순서(맥에서 확인 가능한 것부터)로 재구성**: 기존 규격(1~6)과 따라 하기 절을 하나로 합쳐 단계마다 규격·호출 형태·확인 명령·흔한 실패를 한곳에 두고, 맥 리허설(롤백 포함), 기본 브랜치가 `develop`이라 `cd.yml`은 머지 후에야 시험된다는 점, `.gitignore`의 `.env*` 때문에 `.env.ops.example`이 빠지는 문제, 운영 compose `name:`·`${IMAGE_TAG:?}`·이미지 `LABEL` 보강, 풀스택 curl 순서
> 0.20 · 2026-10-09 · **1-11 구체화·사실 확인**: 운영 MinIO는 `chainguard/minio`를 digest로 고정하고 `mc ready local`로 헬스체크(직접 실행해 확인), JRE 이미지(`eclipse-temurin:25-jre`)에 curl이 없음을 확인해 설치하도록 정함, compose 변수 치환에 `--env-file`이 필요한 점, 관리 포트(8081)를 호스트에서 못 부르는 점, `workflow_run`이 포크 PR의 CI 완료에도 실행되는 점과 거르는 조건, 배포 job에 checkout이 빠진 점, 운영 `.env` 키 이름(로컬과 같은 `SPRING_DATASOURCE_*`)을 고치고 deploy.sh·워크플로·CORS를 호출 형태 수준으로 풀어 적었다
> 0.19 · 2026-10-09 · **1-10 MinIO 이미지 교체·구체화**(실제 코드와 대조해 `ImageResult`의 url/key 불일치, `SellerProductResponse`에 없는 썸네일 필드, 단종 상품 이미지 삭제 규칙 누락, `TransactionTemplate`·`BucketInitializer`·상세 조회 이미지 로딩 설명 부족을 고쳤다). 1-1~1-9는 구현된 코드와 맞췄다(설정 파일 `.yaml`·환경변수 이름, 테스트 메서드 camelCase, `TestFixtures` 이름, 확정된 jjwt·Modulith·AWS SDK 버전, 가리키는 곳이 없는 `:113` 참조): 공식 `minio/minio`가 2026-09-11 Docker Hub에서 삭제되어(소스만 배포) `chainguard/minio`로 바꿨다(호환 확인 완료). 의존성 버전 확정, 단계의 목적·흐름과 `S3ObjectStorage`의 호출 형태·반환 타입·예외를 풀어 적었다
> 0.18 · 2026-10-09 · **1-9 멱등 INSERT 근거 보강**: `restore`에서 이력 INSERT를 UPDATE보다 먼저 하는 이유(행 락은 줄 세울 뿐 중복을 판정하지 못함, `ON CONFLICT DO NOTHING`은 예외·롤백이 없음)와 `insertReservation`은 일반 INSERT, `insertMovement`는 `ON CONFLICT DO NOTHING`인 이유를 적었다
> 0.17 · 2026-10-09 · **1-9 `ProductApiImplTest` 완료 확인에 release 성공 경로·restore 사전 조건 실패 추가**: 기존 케이스는 "COMMITTED를 release하면 변화 없음"뿐이라 `HELD → RELEASED/EXPIRED`와 `stock.release`(INV-04: 해제·만료된 예약의 재고는 가용재고로 복귀)를 실행하는 테스트가 없었다. `restore`의 사전 조건 실패(예약 상태, 수량 범위)도 구현 규격에 있으나 검증이 없어 추가했다
> 0.16 · 2026-10-09 · **CAS 전용 상태 예외를 개발 가이드와 맞춤**: 개발 가이드 §5.4 근처에 같은 예외를 추가했고(전이표·전이 테스트를 두지 않는 대신 CAS 호출 서비스의 통합 테스트로 검증), `ReservationStatus` 행의 문장을 읽기 쉽게 고쳤다
> 0.15 · 2026-10-09 · **1-9 `ProductApiImplTest` 정합성 점검 범위 명시**: "모든 케이스 끝에" `findProductIdsWithBrokenBalance()`를 재고를 다루는 케이스로 좁히고, 실패 케이스는 수량 직접 단언을 병행하도록 적었다
> 0.14 · 2026-10-09 · **1-9 `ReservationStatus` 전이표 제거**: 예약 상태는 native CAS로만 바뀌어 `transitTo`를 호출할 곳이 없으므로 enum은 값만 두고 `ReservationStatusTest`(16개 조합)를 완료 확인에서 뺐다. 공통 규칙 C에 "CAS로만 전이하는 상태는 전이표를 두지 않는다" 예외를 추가했다
> 0.13 · 2026-10-09 · **1-9 `commitReservation` 조회 정렬 이유 명시**: 예약 조회를 상품 ID 오름차순으로 하는 이유(`stock` 행 락 순서 통일 = 데드락 회피)를 한 줄 추가했다
> 0.12 · 2026-10-09 · **1-9 `restore` 누적 환불 책임 명시**: `restore`가 환불 수량의 누적 합을 검사하지 않는다는 점과, 그 방어가 주문 모듈(품목 종결 상태 + CHECK)의 책임임을 적었다
> 0.11 · 2026-10-09 · **1-9 `commitReservation` 분기 명확화**: 한 줄에 "0이면"이 두 번 나와(`transit` 결과 / `commit` 결과) 어느 쪽이 예외이고 어느 쪽이 건너뜀인지 모호했다. 반환값별로 나눠 적었다. `releaseReservation`은 같은 구조를 따른다
> 0.10 · 2026-10-09 · **1-9 체크아웃 조회 쿼리 구체화**: `getForCheckout`·`getPurchasable`·`reserve`가 쓰는 `ProductRepository.findCheckoutRowsByIds`와 projection `ProductCheckoutRow`, JPQL을 명시했다(본문에 "1-7의 엔티티 조인 방식"이라고만 있고 `할 일`에 쿼리·projection이 없어 어디에 무엇을 만들지 알 수 없었다. `ProductListRow`에는 `subscribable`이 없어 재사용도 불가)
> 0.9 · 2026-10-09 · **1-9 구현 위치 구체화**: 예약·재고 native 쿼리는 domain 인터페이스에 시그니처만 두고 `@Query`는 `JpaStockReservationRepository`·`JpaStockRepository`에 구현한다고 명시했다(표의 "위치" 열이 시그니처 선언 위치인지 구현 위치인지 모호했음). `existsByOrderId` 명명 주의 추가
> 0.8 · 2026-10-08 · **1-8 구현 위치·쿼리 구체화**: `Product.update` 동작 규칙, `insertPriceHistory`·`adjust`·`findProductIdsWithBrokenBalance`가 놓일 계층(domain 시그니처 ↔ infrastructure 쿼리)과 native 쿼리 SQL을 1-7 방식에 맞춰 명시했다(1-7에서 이미 선언된 `findByIdAndShopId` 반영)
> 0.7 · 2026-10-07 · **1-11 운영 환경 연습(VM 배포·자동 CD) 추가**: Proxmox VM + Docker Compose, GitHub Actions self-hosted runner, sha 태그 이미지·자동 롤백. 근거 [ADR-011](../adr/ADR-011-ops-practice-environment.md). 외부 접속(Cloudflare)은 보류. Part 1 릴리스(`v0.1.0`)는 1-11 완료 후
> 0.6 · 2026-10-06 · **1-6 `requireActiveShops` 구체화**: 중복 ID·빈 입력·문제 ID 수집 방식·`details.shopIds` 형식을 명시했다(1-6 리뷰에서 규격이 모호해 구현이 갈린 부분). 1-6 `ShopControllerTest` 완료 확인에 **수정 성공(null 유지)** 케이스를 추가했다(`Shop.update`의 null 유지 규칙이 규격에 있는데 검증하는 테스트가 없었다). `UpdateShopRequest`는 빈 문자열·공백만 있는 값을 400으로 거르도록 명시했다(수정 때만 빈 이름이 저장되던 문제)
> 0.5 · 2026-10-02 · **덜어내기**: `InvalidStateTransitionException`·`UniqueConstraintMapping`·`@CurrentMember` 리졸버·traceId 헤더 수용·토큰 만료 코드 구분을 제거하고, 미리 만들던 컬럼(이메일 인증·토큰 버전·탈퇴 시각)은 쓰는 단계로 미뤘다. 회원 역할은 jsonb 목록 → 단일 `role` 컬럼. 꼭 필요하지 않은 테스트·장치는 각 단계의 "제안"으로 옮겼다
> 0.4 · 1-10 상품 이미지 업로드(presigned URL + MinIO) 추가, 예치금 제거에 따른 Part 2 번호 변경 반영
> 0.3 · 1-3~1-9를 이 문서만 보고 개발할 수 있게 구체화(공통 규칙, 구현 규격, 테스트 클래스·케이스 명시)

> **끝나면**: 이메일로 가입·로그인하고, 가게를 열고, 상품(이미지 포함)을 올리고, 재고를 동시성 문제 없이 예약할 수 있다.
> **인프라**: Postgres (Docker), 1-10부터 MinIO, 1-11부터 Proxmox VM에 자동 배포. Kafka·Redis는 아직 없다.
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
| 1-10 | 상품 이미지 업로드 (presigned URL + MinIO) | M |
| 1-11 | 운영 환경 연습: VM 배포와 자동 CD | L |

크기: S(1일 이내) · M(2~3일) · L(4~5일)

---

## Part 1 공통 규칙 (모든 단계에 적용)

> **이 문서만 보고 개발할 수 있게** 필요한 규칙을 여기에 모았다. 원본은 [개발 가이드](../development-guide.md)이고, 두 문서가 다르면 Claude가 둘 다 맞춘다.
> **리뷰 기준**: 리뷰에서는 ① 단계의 "구현 규격"·"완료 확인", ② 아래 공통 규칙에 어긋나는 것만 지적한다. 근거가 없는 의견은 지적하지 않고 "제안"으로 따로 적으며, 반영 여부는 사용자가 정한다.
> **확인 필요** 표시는 Boot 4 / Hibernate 7에서 동작을 직접 확인하지 못한 부분이다. 막히면 우회하지 말고 알려 주면 문서를 고친다.
> **제안 (선택)**: 각 단계 끝의 "제안"은 해 보면 좋지만 **하지 않아도 그 단계는 완료**다. 리뷰에서도 하지 않았다고 지적하지 않는다.

### A. 패키지
```
com.myroutine
├── common                       모든 모듈이 쓸 수 있다 (Modulith OPEN)
│   ├── config                   ClockConfig, JpaAuditingConfig
│   ├── error                    ErrorCode, CommonErrorCode, BusinessException, ErrorResponse, GlobalExceptionHandler
│   ├── model                    Ids, Money, MoneyConverter, BaseTimeEntity
│   ├── storage                  ObjectStorage, S3ObjectStorage, ImageUrls (1-10)
│   ├── security                 SecurityConfig, JwtProperties, JwtProvider, AuthClaims, JwtAuthenticationFilter,
│   │                            RestAuthenticationEntryPoint, RestAccessDeniedHandler, CurrentMember
│   └── web                      TraceIds, TraceIdFilter, Cursor, CursorPage
└── member / shop / product      모듈
    ├── api                      다른 모듈에 공개하는 인터페이스·DTO (필요한 모듈만)
    ├── web                      XxxController, XxxRequest, XxxResponse
    ├── application              XxxService(@Transactional), XxxCommand, XxxResult
    ├── domain                   엔티티, enum, XxxRepository(인터페이스), {Module}ErrorCode
    └── infrastructure           JpaXxxRepository
```

| 계층 | 하는 일 | 하지 않는 일 |
|---|---|---|
| web | `@Valid` 입력 검증, Request → Command, Result → Response 변환 | 리포지토리 호출, 엔티티 반환, 비즈니스 판단 |
| application | 트랜잭션, 조회·저장 순서 조율, 소유권 확인, 다른 모듈 `api` 호출 | Request/Response 사용, 상태를 `if (x.getStatus() == ...)`로 직접 판단 |
| domain | 엔티티 생성·상태 변경 규칙, 리포지토리 인터페이스, 에러 코드 | Spring 빈 주입, HTTP |
| infrastructure | Spring Data 리포지토리, native 쿼리 | 비즈니스 판단 |

- 의존 방향: `web → application → domain ← infrastructure`. 다른 모듈은 `{module}.api` 패키지만 참조한다. `common`은 어떤 모듈도 참조하지 않는다.

### B. 엔티티
- Lombok은 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`만 쓴다. `@Setter`, `@Data`, `@AllArgsConstructor`, `@Builder`는 엔티티에 쓰지 않는다.
- 생성은 **정적 팩토리**(`Member.signUp(...)`)에서만 한다. 그 안에서 `id = Ids.newId()`, 초기 상태, 입력 불변식 검증을 한다.
- 상태 변경은 의도가 드러나는 메서드(`changeProfile(...)`, `markDefault()`)로만 한다.
- 수정 가능한 엔티티는 `@Version private Long version;`(래퍼 타입)을 둔다. 코드에서 직접 읽거나 쓰지 않는다. 직접 할당한 UUID라도 version이 null이면 `save()`가 SELECT 없이 INSERT한다.
- 생성·수정 시각은 `BaseTimeEntity`(created_at + updated_at)를 상속해 자동으로 채운다(1-3에서 만든다). 수정이 없는 테이블의 엔티티는 `BaseTimeEntity`를 상속하지 않고, 클래스에 `@EntityListeners(AuditingEntityListener.class)`를 붙이고 `@CreatedDate Instant createdAt` 필드 하나만 둔다.
- 시각 타입은 `Instant`다. `LocalDateTime`은 쓰지 않는다. 현재 시각은 `Clock` 빈에서 `Instant.now(clock)`로 얻는다.
- enum 컬럼은 `@Enumerated(EnumType.STRING)`. 테이블 매핑은 `@Table(name = "member", schema = "member")`처럼 schema를 명시한다.
- 다른 애그리거트·다른 모듈은 UUID로만 참조한다. `@OneToMany`는 **같은 애그리거트 안에서만** 쓴다(Part 1에서는 1-10의 `Product` → `ProductImage`뿐). `@ManyToOne`은 쓰지 않는다.

### C. 상태 enum
```java
public enum XxxStatus {
    A, B, C;

    private static final Map<XxxStatus, Set<XxxStatus>> ALLOWED = Map.of(
            A, EnumSet.of(B, C),
            B, EnumSet.of(A));

    public XxxStatus transitTo(XxxStatus to) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);   // 409
        }
        return to;
    }
}
```
- 같은 상태로의 전이(A → A)도 금지다(표에 없으므로).
- **예외**: 엔티티 메서드로 상태를 바꾸지 않고 native CAS로만 전이하는 상태(1-9 `ReservationStatus`)는 전이표를 두지 않는다. 호출처가 없는 코드가 되기 때문이다. 엔티티 메서드와 CAS를 함께 쓰는 상태(Part 2 `OrderStatus` 등)는 엔티티 경로가 있으므로 전이표를 둔다.
- 테스트: 모든 (from, to) 조합을 `@ParameterizedTest` + `@CsvSource`로 검증한다(3개 상태면 9건).

### D. 애플리케이션 서비스
- `@Service` + `@RequiredArgsConstructor`, 의존성 필드는 모두 `private final`.
- public 메서드 1개 = 유스케이스 1개 = 트랜잭션 1개. 변경은 `@Transactional`, 조회는 `@Transactional(readOnly = true)`. 반드시 `org.springframework.transaction.annotation.Transactional`을 import한다(`jakarta.transaction.Transactional` 금지).
- 반환은 `XxxResult` record. 엔티티를 반환하지 않는다.
- **소유권**: 비공개 리소스(배송지)는 `findByIdAndMemberId`로 조회하고, 남의 것이면 없는 것처럼 **404**. 공개 조회가 있는 리소스(가게·상품)는 존재가 이미 공개돼 있으므로 남이 수정하면 **403**.
- 중복은 **먼저 조회로 확인**해서 구체적인 에러 코드(`MEMBER_EMAIL_DUPLICATED` 등)를 던진다. 동시 요청으로 그 확인을 둘 다 통과하면 DB unique 제약이 막고, `GlobalExceptionHandler`가 409 `DUPLICATE_RESOURCE`로 바꾼다. unique 위반 예외를 서비스에서 catch하지 않는다.
- "있으면 무시"처럼 중복을 허용하는 INSERT는 `INSERT ... ON CONFLICT DO NOTHING`의 반영 행 수(0/1)로 판단한다.

### E. 컨트롤러·DTO
- 경로는 `/api/...`. 상태 코드: 생성 **201**, 조회·수정 **200**, 삭제 **204**.
- Request/Response/Command/Result는 모두 record. 변환 메서드 이름은 `request.toCommand()`, `XxxResponse.from(result)`로 통일한다.
- 요청자 ID는 `@CurrentMember UUID memberId`(1-4부터)로만 받는다. 바디·쿼리의 memberId는 받지 않는다.
- 금액은 응답에서 `long`(원)으로 내보낸다. `Money`를 JSON에 그대로 내보내지 않는다.
- 시각은 `Instant` 그대로 내보낸다(ISO-8601 UTC, 예: `2026-10-02T06:00:00Z`).
- PATCH 요청의 필드는 모두 선택이고, **null이면 바꾸지 않는다**.
- 검증 메시지는 한국어로 `message` 속성에 적는다. **모든 제약**(`@NotBlank`·`@NotNull`·`@Size`·`@Pattern`·`@Email`·`@Min`·`@Max`, 쿼리 파라미터 포함)에 붙인다. 붙이지 않으면 오류 응답 `details`에 영어 기본 문구와 정규식(`must match ".*\S.*"`)이 그대로 나간다.
  - 표준 문구: 필수 `필수 값입니다.` / 공백만 있는 값 `공백만 입력할 수 없습니다.` / 길이 `{항목}은 {n}자 이하여야 합니다.` / 범위 `{n} 이상이어야 합니다.`·`{n} 이하여야 합니다.` / 이메일 `이메일 형식이 아닙니다.` / 전화번호 `전화번호는 숫자와 하이픈으로 9~20자입니다.` / 우편번호 `우편번호는 5자리 숫자입니다.`

### F. 에러 응답
```json
{ "code": "INVALID_REQUEST", "message": "요청 값이 올바르지 않습니다.", "traceId": "0199a1b2-...", "details": { "email": "이메일 형식이 아닙니다." } }
```
- `traceId`는 응답 헤더 `X-Request-Id`와 같은 값이다(MDC에서 꺼낸다). `details`는 없으면 `{}`.
- 변환 규칙과 에러 코드 전체는 1-3 "구현 규격"의 표가 기준이다.

**Part 1 에러 코드 전체** (등장 단계)

| 코드 | HTTP | 메시지 | 정의 위치 | 단계 |
|---|---|---|---|---|
| `INVALID_REQUEST` | 400 | 요청 값이 올바르지 않습니다. | `CommonErrorCode` | 1-3 |
| `UNAUTHORIZED` | 401 | 인증이 필요합니다. | `CommonErrorCode` | 1-3 |
| `FORBIDDEN` | 403 | 접근 권한이 없습니다. | `CommonErrorCode` | 1-3 |
| `NOT_FOUND` | 404 | 요청한 리소스를 찾을 수 없습니다. | `CommonErrorCode` | 1-3 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 요청 방식입니다. | `CommonErrorCode` | 1-3 |
| `CONFLICT_RETRY` | 409 | 다른 요청과 충돌했습니다. 다시 시도해 주세요. | `CommonErrorCode` | 1-3 |
| `DUPLICATE_RESOURCE` | 409 | 이미 존재하는 데이터입니다. | `CommonErrorCode` | 1-3 |
| `INVALID_STATE_TRANSITION` | 409 | 현재 상태에서는 처리할 수 없습니다. | `CommonErrorCode` | 1-3 |
| `INTERNAL_ERROR` | 500 | 서버 오류가 발생했습니다. | `CommonErrorCode` | 1-3 |
| `MEMBER_EMAIL_DUPLICATED` | 409 | 이미 가입된 이메일입니다. | `MemberErrorCode` | 1-3 |
| `MEMBER_NICKNAME_DUPLICATED` | 409 | 이미 사용 중인 닉네임입니다. | `MemberErrorCode` | 1-3 |
| `LOGIN_FAILED` | 401 | 이메일 또는 비밀번호가 올바르지 않습니다. | `MemberErrorCode` | 1-4 |
| `MEMBER_BANNED` | 403 | 이용이 제한된 회원입니다. | `MemberErrorCode` | 1-4 |
| `MEMBER_NOT_FOUND` | 404 | 회원을 찾을 수 없습니다. | `MemberErrorCode` | 1-4 |
| `MEMBER_ADDRESS_NOT_FOUND` | 404 | 배송지를 찾을 수 없습니다. | `MemberErrorCode` | 1-5 |
| `SHOP_NOT_FOUND` | 404 | 가게를 찾을 수 없습니다. | `ShopErrorCode` | 1-6 |
| `SHOP_BUSINESS_NUMBER_DUPLICATED` | 409 | 이미 등록된 사업자번호입니다. | `ShopErrorCode` | 1-6 |
| `SHOP_NOT_ACTIVE` | 422 | 운영 중인 가게가 아닙니다. | `ShopErrorCode` | 1-6 |
| `PRODUCT_NOT_FOUND` | 404 | 상품을 찾을 수 없습니다. | `ProductErrorCode` | 1-7 |
| `PRODUCT_DISCONTINUED` | 409 | 단종된 상품은 수정할 수 없습니다. | `ProductErrorCode` | 1-8 |
| `OUT_OF_STOCK` | 422 | 재고가 부족합니다. | `ProductErrorCode` | 1-8 |
| `PRODUCT_NOT_ON_SALE` | 422 | 판매 중인 상품이 아닙니다. | `ProductErrorCode` | 1-9 |
| `PRODUCT_IMAGE_LIMIT_EXCEEDED` | 422 | 상품 이미지는 최대 10장입니다. | `ProductErrorCode` | 1-10 |
| `IMAGE_NOT_UPLOADED` | 422 | 이미지가 업로드되지 않았습니다. | `ProductErrorCode` | 1-10 |
| `PRODUCT_IMAGE_NOT_FOUND` | 404 | 이미지를 찾을 수 없습니다. | `ProductErrorCode` | 1-10 |

- 모듈 에러 코드 enum은 `{module}.domain` 패키지에 두고, 1-3에서 만든 `CommonErrorCode`와 같은 모양(`status`, `message` 필드 + `code()`는 `name()`)으로 만든다.

### G. DB·마이그레이션
- 파일: `src/main/resources/db/migration/{module}/V{yyyyMMddHHmm}__{module}_{설명}.sql` (예: `V202610031000__member_create_member.sql`). **이미 적용된 파일은 수정하지 않고** 새 파일을 추가한다.
- 모듈마다 schema를 만든다: 첫 파일에 `CREATE SCHEMA IF NOT EXISTS member;`. 테이블은 항상 `member.member`처럼 schema를 붙인다.
- 타입: PK `uuid`, 금액 `bigint`, 수량 `int`, 시각 `timestamptz`, enum `varchar(20)` + CHECK.
- 모든 테이블에 `created_at timestamptz NOT NULL`, 수정되는 테이블에 `updated_at timestamptz NOT NULL`.
- 제약 이름은 규칙대로 붙인다: `pk_{table}`, `uk_{table}_{cols}`, `fk_{table}_{참조table}`, `ck_{table}_{규칙}`, `idx_{table}_{cols}`(에러 로그에서 어떤 제약인지 바로 알아보기 위해).
- FK는 같은 schema 안에서만 건다. 다른 모듈의 ID(`shop.member_id` 등)는 값만 저장한다.
- 아래 단계별 표의 "NULL" 열이 `X`면 `NOT NULL`이다.

### H. 테스트
| 종류 | 위치 · 이름 | 베이스 |
|---|---|---|
| 도메인 단위 | `src/test/java/com/myroutine/{module}/domain/{클래스}Test` | 없음 (Spring 없이 순수 JUnit) |
| 공통 단위 | `src/test/java/com/myroutine/common/{패키지}/{클래스}Test` | 없음 |
| API 통합 | `src/test/java/com/myroutine/{module}/web/{컨트롤러}Test` | `extends IntegrationTestSupport` + `@AutoConfigureMockMvc` |
| 서비스 통합 | `src/test/java/com/myroutine/{module}/application/{클래스}Test` | `extends IntegrationTestSupport` |
| 동시성 | `src/test/java/com/myroutine/{module}/application/{주제}ConcurrencyTest` | `extends IntegrationTestSupport` |

- 메서드 이름은 영문 camelCase(예: `registerProductOfOtherMemberShop`), 설명은 `@DisplayName("한글 문장")`. 성공은 "~한다.", 실패는 "~를 실패한다. (사유)". 본문은 `// Given` / `// When` / `// Then` 주석으로 나눈다.
- 단언은 AssertJ(`assertThat`), MockMvc 응답은 `jsonPath(...)`. Hamcrest 매처는 `org.hamcrest.Matchers`에서 import한다(같은 이름의 Mockito 매처와 혼동 주의).
- 통합 테스트에 `@Transactional`을 붙이지 않는다. 데이터는 `IntegrationTestSupport`가 테스트마다 TRUNCATE한다.
- `@MockitoBean`/`@MockitoSpyBean`은 단계 문서에서 쓰라고 한 곳에서만 쓴다(쓰면 Spring 컨텍스트가 새로 떠서 느려진다).
- 테스트에서 회원·토큰이 필요하면 `src/test/java/com/myroutine/support/TestFixtures`(1-4에서 만든다)의 헬퍼를 쓴다.
- actuator는 관리 포트(8081)에서 열리므로 MockMvc로는 호출할 수 없다. 헬스체크 허용은 각 단계의 curl 확인(토큰 없이 `UP`)으로 검증한다.

### I. 확인된 Boot 4 이름
| 대상 | 패키지 / 좌표 |
|---|---|
| `@AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@MockitoSpyBean` / `@MockitoBean` | `org.springframework.test.context.bean.override.mockito` |
| Testcontainers Postgres | `org.testcontainers.postgresql.PostgreSQLContainer` |
| Jackson 3 | `tools.jackson.*` (어노테이션은 `com.fasterxml.jackson.annotation` 그대로) |
| uuid-creator | `com.github.f4b6a3:uuid-creator:6.1.1`, UUIDv7은 `UuidCreator.getTimeOrderedEpoch()` |
| AWS SDK v2 (1-10) | `software.amazon.awssdk:s3` (BOM `software.amazon.awssdk:bom:2.55.13`), 패키지 `software.amazon.awssdk.services.s3` |

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
   - `application.yaml`: 공통 설정 (`spring.jpa.hibernate.ddl-auto: validate`, `spring.jpa.open-in-view: false`, actuator 관리 포트 `management.server.port: 8081`)
   - `application-local.yaml`: DB 주소·계정은 `${SPRING_DATASOURCE_URL}`·`${SPRING_DATASOURCE_USERNAME}`·`${SPRING_DATASOURCE_PASSWORD}` 환경변수. 로컬에서는 `spring.config.import: optional:file:.env.local[.properties]`로 프로젝트 루트의 `.env.local`(커밋하지 않음)에서 읽는다
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
2. `application-test.yaml`
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
- 이메일·닉네임 중복이면 409, 입력이 잘못되면 400과 필드별 오류를 준다.
- 모든 응답에 `X-Request-Id`(traceId)가 있고, 에러 응답 본문의 `traceId`와 같다.

**왜 지금**: 모든 기능의 주체가 회원이다. 첫 도메인이라 **엔티티 작성법과 패키지 구조**를 여기서 익히고, 이후 모든 모듈이 같은 방식을 따른다.

**새로 등장**

| 개념·도구 | 한 줄 설명 |
|---|---|
| 모듈 4계층 패키지 | `web → application → domain ← infrastructure` (공통 규칙 A) |
| 엔티티 작성 규칙 | setter 금지, 정적 팩토리로 생성, 상태 변경은 의도가 드러나는 메서드로 (공통 규칙 B) |
| UUIDv7 + `@Version` | 앱에서 시간순 ID를 만든다. `@Version`(null)이 있어야 `save()`가 불필요한 SELECT를 안 한다 |
| Spring Security `PasswordEncoder` | BCrypt로 비밀번호를 해시. 같은 비밀번호도 매번 다른 해시가 나온다(salt) |
| JPA Auditing | Spring Data가 저장·수정 시 `created_at`, `updated_at`을 자동으로 채운다(`@CreatedDate`, `@LastModifiedDate`). 시각은 `Clock` 빈에서 가져온다 |
| 공통 에러 응답 | `ErrorCode` → `BusinessException` → `GlobalExceptionHandler`가 `{code, message, traceId, details}`로 변환 |
| traceId | 요청마다 ID를 만들어 로그(MDC)와 응답 헤더에 넣는다. 나중에 Kibana에서 이 값으로 검색한다 |

### 할 일

**1) Security 설정** — `common.security.SecurityConfig`
- 의존성: `implementation 'org.springframework.boot:spring-boot-starter-security'`
- `@Configuration`, `@Bean SecurityFilterChain securityFilterChain(HttpSecurity http)`
  - `csrf` 비활성화, 세션 `STATELESS`
  - `permitAll`: `/api/auth/**`, `/actuator/health/**` / 그 외 `anyRequest().authenticated()`
  - formLogin·httpBasic은 직접 정의한 체인에서는 켜지지 않으므로 따로 끄지 않아도 된다
- `@Bean PasswordEncoder passwordEncoder()` → `new BCryptPasswordEncoder()` (기본 strength 10)
- **관리 포트(8081)에도 이 체인이 적용된다.** Boot가 관리 포트용 컨텍스트에도 `springSecurityFilterChain`을 등록하기 때문이다(Boot 4.1.1 `ServletManagementChildContextConfiguration`에서 확인). 그래서 헬스체크를 `permitAll`로 열어야 한다. `/actuator/health/**`는 `/actuator/health` 자체도 매칭한다.

**2) common.model / common.config — ID와 시각**

| 클래스 | 규격 |
|---|---|
| `common.model.Ids` | `public final class`, private 생성자. `public static UUID newId()` → `UuidCreator.getTimeOrderedEpoch()`. 의존성 `implementation 'com.github.f4b6a3:uuid-creator:6.1.1'` |
| `common.config.ClockConfig` | `@Configuration`. `@Bean Clock clock()` → `Clock.systemUTC()` |
| `common.config.JpaAuditingConfig` | `@Configuration @EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")`. `@Bean DateTimeProvider auditingDateTimeProvider(Clock clock)` → `() -> Optional.of(Instant.now(clock))` |
| `common.model.BaseTimeEntity` | `@MappedSuperclass @EntityListeners(AuditingEntityListener.class) @Getter public abstract class`. `@CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;`, `@LastModifiedDate @Column(name = "updated_at", nullable = false) private Instant updatedAt;` |

- `DateTimeProvider`를 직접 주는 이유: 기본값은 `LocalDateTime.now()`(JVM 시간대)라서 "현재 시각은 `Clock` 빈으로만"(공통 규칙 B) 규칙과 어긋난다. 한 줄로 `Clock`에 맞춘다.
- `@EnableJpaAuditing`은 메인 클래스가 아니라 이 설정 클래스에 둔다(웹 슬라이스 테스트에서 JPA 설정이 없어 깨지는 것을 막기 위해).
- 확인함(1-3 구현): `DateTimeProvider`가 돌려준 `Instant`가 `Instant` 필드에 그대로 들어간다. `AuthControllerTest.signUp`이 `created_at`·`updated_at`이 채워짐을 검증한다.

**3) common.error — 에러 응답**

`ErrorCode`(인터페이스, 완료): `String code(); HttpStatus status(); String message();`

`CommonErrorCode`(enum, `implements ErrorCode`): 공통 규칙 F의 표에서 정의 위치가 `CommonErrorCode`인 것 전부(1-3 시점 9개). `code()`는 `name()`을 반환한다.

| 클래스 | 규격 |
|---|---|
| `BusinessException` | `public class ... extends RuntimeException`, `@Getter`. 필드 `private final ErrorCode errorCode;`, `private final Map<String, Object> details;`. 생성자 2개: `(ErrorCode errorCode)` → details는 `Map.of()` / `(ErrorCode errorCode, Map<String, Object> details)`. 둘 다 `super(errorCode.message())` |
| `ErrorResponse` | `public record ErrorResponse(String code, String message, String traceId, Map<String, Object> details)`. 정적 팩토리 `static ErrorResponse of(ErrorCode errorCode, Map<String, Object> details)` → `traceId = MDC.get(TraceIds.MDC_KEY)` |
| `GlobalExceptionHandler` | `@RestControllerAdvice @Slf4j` |

`GlobalExceptionHandler` 변환표 (메서드 하나당 한 줄, 반환 타입 `ResponseEntity<ErrorResponse>`)

| 잡는 예외 | HTTP | code | details | 로그 |
|---|---|---|---|---|
| `BusinessException` | `errorCode.status()` | `errorCode.code()` | `e.getDetails()` | WARN |
| `MethodArgumentNotValidException` (`@Valid` 실패) | 400 | `INVALID_REQUEST` | `{필드명: 메시지}` — `e.getBindingResult().getFieldErrors()`, 같은 필드에 오류가 여러 개면 첫 번째만 | WARN |
| `HttpMessageNotReadableException` (JSON 문법 오류, 타입 불일치, 없는 enum 값) | 400 | `INVALID_REQUEST` | `{}` | WARN |
| `MethodArgumentTypeMismatchException` (경로 변수·쿼리 파라미터 타입 오류, 예: UUID 자리에 `abc`) | 400 | `INVALID_REQUEST` | `{파라미터명: "형식이 올바르지 않습니다."}` | WARN |
| `MissingServletRequestParameterException`, `MissingRequestHeaderException` | 400 | `INVALID_REQUEST` | `{파라미터명: "필수 값입니다."}` | WARN |
| `NoResourceFoundException` (없는 경로) | 404 | `NOT_FOUND` | `{}` | WARN |
| `HttpRequestMethodNotSupportedException` | 405 | `METHOD_NOT_ALLOWED` | `{}` | WARN |
| `OptimisticLockingFailureException` (`@Version` 충돌) | 409 | `CONFLICT_RETRY` | `{}` | WARN |
| `DataIntegrityViolationException` — unique 위반 | 409 | `DUPLICATE_RESOURCE` | `{}` | WARN |
| `DataIntegrityViolationException` — 그 외(NOT NULL·CHECK 위반 등, 코드 버그) | 500 | `INTERNAL_ERROR` | `{}` | ERROR + 스택트레이스 |
| `Exception` (그 외 전부) | 500 | `INTERNAL_ERROR` | `{}` | ERROR + 스택트레이스 (`log.error("unexpected error", e)`) |

- unique 위반 판별: 원인 체인(`getCause()`를 따라감)에 `java.sql.SQLException`이 있고 `getSQLState()`가 `"23505"`(Postgres unique_violation)면 unique 위반이다.
- 왜 제약별로 다른 코드를 주지 않나: 평소 중복은 서비스의 사전 조회가 구체적인 코드(`MEMBER_EMAIL_DUPLICATED`)로 막는다. DB 제약까지 오는 것은 **동시 요청 경합**뿐이라, 어떤 제약이든 "이미 있다" 409 하나면 충분하다.
- 응답 `message`는 **항상 `ErrorCode.message()`**를 쓴다. 예외 메시지(`e.getMessage()`)를 응답에 넣지 않는다(SQL·클래스명 노출 방지).
- 401·403 중 Spring Security가 직접 막는 경우는 이 핸들러를 거치지 않는다 → 1-4에서 처리한다.
- 패키지: `NoResourceFoundException`은 `org.springframework.web.servlet.resource`, `OptimisticLockingFailureException`·`DataIntegrityViolationException`은 `org.springframework.dao`.

**4) common.web — traceId**

| 클래스 | 규격 |
|---|---|
| `TraceIds` | `public final class`, private 생성자. 상수 `HEADER = "X-Request-Id"`, `MDC_KEY = "traceId"`. `static String newId()` → `Ids.newId().toString()` (잡·consumer도 이걸로 만든다) |
| `TraceIdFilter` | `@Component @Order(Ordered.HIGHEST_PRECEDENCE)`, `extends OncePerRequestFilter`. `doFilterInternal`: ① `traceId = TraceIds.newId()` ② `MDC.put(MDC_KEY, traceId)` ③ `response.setHeader(HEADER, traceId)` ④ `try { chain.doFilter(...) } finally { MDC.remove(MDC_KEY); }` |

- traceId는 **항상 서버가 만든다**(클라이언트가 보낸 `X-Request-Id`는 쓰지 않는다).
- **왜 HIGHEST_PRECEDENCE인가**: Spring Security 필터보다 먼저 돌아야 401·403 응답에도 헤더가 붙는다.
- **왜 finally에서 지우나**: 톰캣은 스레드를 재사용한다. 안 지우면 다음 요청 로그에 이전 traceId가 찍힌다.
- `application.yaml`에 로그 패턴 추가: `logging.pattern.level: "%5p [%X{traceId:-}]"` → 콘솔 로그에 traceId가 찍힌다(JSON 로그는 Part 7).

**5) 마이그레이션** — `db/migration/member/V{yyyyMMddHHmm}__member_create_member.sql`
- `CREATE SCHEMA IF NOT EXISTS member;` 후 `member.member` 테이블
- 지금 쓰는 컬럼만 만든다. 이메일 인증 시각(4-2), 토큰 버전(4-4), 탈퇴 시각(3-6)은 그 단계에서 새 마이그레이션으로 추가한다.

| 컬럼 | 타입 | NULL | 제약·기본값 | 설명 |
|---|---|---|---|---|
| id | uuid | X | `pk_member` | |
| email | varchar(255) | X | `uk_member_email` | 소문자로 저장 |
| nickname | varchar(20) | X | `uk_member_nickname` | |
| name | varchar(50) | X | | |
| phone | varchar(20) | O | | 가입 시 null, 1-5에서 수정 |
| password_hash | varchar(100) | O | | BCrypt(60자). OAuth 전용 회원은 null (Part 4) |
| status | varchar(20) | X | `ck_member_status`: `status IN ('ACTIVE','BANNED','WITHDRAWN')` | |
| role | varchar(20) | X | `ck_member_role`: `role IN ('USER','SELLER','ADMIN')` | 가게를 열면 SELLER (3-1) |
| version | bigint | X | | `@Version` |
| created_at | timestamptz | X | | |
| updated_at | timestamptz | X | | |

**6) member.domain**

| 클래스 | 규격 |
|---|---|
| `MemberStatus` | `ACTIVE, BANNED, WITHDRAWN`. 전이(공통 규칙 C): `ACTIVE → {BANNED, WITHDRAWN}`, `BANNED → {ACTIVE}`, `WITHDRAWN → {}` |
| `MemberRole` | `USER, SELLER, ADMIN` — 회원당 하나. SELLER는 USER가 할 수 있는 것을 모두 할 수 있다 |
| `MemberErrorCode` | `MEMBER_EMAIL_DUPLICATED`, `MEMBER_NICKNAME_DUPLICATED` (공통 규칙 F 표) |
| `Member` | `@Entity @Table(name = "member", schema = "member")`, `extends BaseTimeEntity`. 필드는 위 컬럼과 1:1 (`@Id UUID id`, `String email`, `nickname`, `name`, `phone`, `passwordHash`, `MemberStatus status`, `MemberRole role`, `@Version Long version`) |
| `MemberRepository` | 인터페이스: `Member save(Member member)`, `Optional<Member> findById(UUID id)`, `boolean existsByEmail(String email)`, `boolean existsByNickname(String nickname)` |

`Member`의 메서드
- `public static String normalizeEmail(String raw)` → `raw.strip().toLowerCase(Locale.ROOT)`
- `public static Member signUp(String email, String encodedPassword, String nickname, String name)` → `id = Ids.newId()`, `email = normalizeEmail(email)`, `passwordHash = encodedPassword`, `phone = null`, `status = ACTIVE`, `role = USER`

**7) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaMemberRepository` | package-private `interface JpaMemberRepository extends JpaRepository<Member, UUID>, MemberRepository {}` — Spring Data가 구현체를 만들고, 서비스는 `MemberRepository`로 주입받는다 |

**8) member.application**

| 클래스 | 규격 |
|---|---|
| `SignupCommand` | `record (String email, String password, String nickname, String name)` |
| `SignupResult` | `record (UUID memberId)` |
| `SignupService` | 의존성 `MemberRepository`, `PasswordEncoder`. `@Transactional public SignupResult signUp(SignupCommand command)` |

`signUp` 순서
1. `email = Member.normalizeEmail(command.email())`
2. `existsByEmail(email)`이면 `throw new BusinessException(MEMBER_EMAIL_DUPLICATED)`
3. `existsByNickname(command.nickname())`이면 `throw new BusinessException(MEMBER_NICKNAME_DUPLICATED)`
4. `passwordEncoder.encode(command.password())`
5. `Member.signUp(...)` → `memberRepository.save(...)` → `new SignupResult(member.getId())`

- **동시에 같은 이메일로 가입하면** 2번을 둘 다 통과한다. INSERT는 커밋할 때 실행되므로 늦은 쪽이 커밋 시점에 unique 위반으로 실패하고, `DataIntegrityViolationException`이 `GlobalExceptionHandler`까지 올라가 409 `DUPLICATE_RESOURCE`가 된다. **서비스에서 catch하지 않는다.**

**9) member.web**

`SignupRequest` record

| 필드 | 검증 | 메시지 예 |
|---|---|---|
| email | `@NotBlank @Email @Size(max = 255)` | 이메일 형식이 아닙니다. |
| password | `@NotBlank @Size(min = 8, max = 64) @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$")` | 비밀번호는 8~64자, 영문과 숫자를 포함해야 합니다. |
| nickname | `@NotBlank @Size(min = 2, max = 20)` | 닉네임은 2~20자입니다. |
| name | `@NotBlank @Size(max = 50)` | 이름을 입력해 주세요. |

- `SignupRequest.toCommand()`, `SignupResponse(UUID memberId)`, `SignupResponse.from(SignupResult)`
- `AuthController`: `@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor`. `@PostMapping("/signup")` → `ResponseEntity.status(HttpStatus.CREATED).body(...)`

**10) 테스트용 에러 컨트롤러** — `src/test/java/com/myroutine/support/ErrorTestController.java`
- `@RestController`, `@GetMapping("/api/auth/test/unexpected-error")` → `throw new IllegalStateException("SELECT password_hash FROM member.member")`
- 테스트 소스도 `com.myroutine` 아래라 `@SpringBootTest`의 컴포넌트 스캔에 잡힌다. 500 응답에 내부 정보가 새지 않는지 검증하는 데 쓴다.

### 완료 확인

| 테스트 클래스 | 종류 | 케이스 |
|---|---|---|
| `member/domain/MemberTest` | 단위 | `signUp`: 이메일 `" Buyer@Test.COM "` → `"buyer@test.com"`, role = USER, status = ACTIVE, id가 null이 아님 |
| `member/domain/MemberStatusTest` | 단위 | 9개 조합 파라미터화: 허용 3건은 반환값 확인, 나머지 6건은 `BusinessException`이고 코드가 `INVALID_STATE_TRANSITION` |
| `member/web/AuthControllerTest` | API 통합 | 아래 목록 |
| `support/TableCleanupTest` | 통합 | 아래 목록 |

`AuthControllerTest`
- [ ] 가입 → 201, 응답 `memberId`. DB의 `password_hash`가 평문과 다르고 `passwordEncoder.matches(평문, hash)`가 true. `created_at`·`updated_at`이 채워져 있다
- [ ] 같은 이메일 재가입(대소문자만 다르게) → 409 `MEMBER_EMAIL_DUPLICATED`
- [ ] 같은 닉네임 → 409 `MEMBER_NICKNAME_DUPLICATED`
- [ ] **같은 이메일로 동시에 2건(닉네임은 다르게) → 1건 201, 1건 409**(`MEMBER_EMAIL_DUPLICATED` 또는 `DUPLICATE_RESOURCE`, 500이 나면 안 됨). 스레드 2개 + `CountDownLatch`로 동시에 `mockMvc.perform`, 상태 코드를 모아 `containsExactlyInAnyOrder(201, 409)`. 10회 반복(`@RepeatedTest(10)`)
- [ ] 잘못된 이메일·7자 비밀번호 → 400 `INVALID_REQUEST`, `details.email`, `details.password`가 있다
- [ ] 깨진 JSON(`{"email":`) → 400 `INVALID_REQUEST`
- [ ] 에러 응답의 헤더 `X-Request-Id`와 본문 `traceId`가 같다
- [ ] `GET /api/auth/test/unexpected-error` → 500 `INTERNAL_ERROR`, 응답 본문 문자열에 `SELECT`, `IllegalStateException`이 없다

`TableCleanupTest` (1-2에서 이월)
- [ ] `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)`. 1번 테스트에서 가입 → 2번 테스트에서 `SELECT count(*) FROM member.member` = 0, `SELECT count(*) FROM flyway_schema_history` > 0

```bash
curl -s localhost:8081/actuator/health          # 토큰 없이 {"status":"UP"} — Security 체인이 관리 포트에도 적용되므로 허용 설정 확인
curl -i -X POST localhost:8080/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"buyer@test.com","password":"pass1234","nickname":"buyer","name":"김구매"}'
```

**제안 (선택)**
- 클라이언트가 보낸 `X-Request-Id`를 traceId로 이어 쓰기 — 프런트·게이트웨이와 같은 ID로 추적할 수 있다. 단, 받은 값이 그대로 로그에 들어가므로 형식 검사(`^[A-Za-z0-9-]{1,64}$`)를 같이 한다
- `SecurityConfigTest`: 보호된 경로가 302(로그인 페이지)나 `WWW-Authenticate`(Basic)를 내리지 않는지 확인
- 상태 전이 실패 응답의 `details`에 `{from, to}`를 담기

**리뷰 때 물어볼 것**
- setter 없이 회원 상태를 바꾸려면 어떻게 하나? setter가 있으면 무엇이 위험한가? (As-Is ORD-06)
- 중복 검사를 `existsByEmail`만으로 하면 왜 부족한가? unique 위반은 정확히 어느 시점에 발생하나?
- BCrypt를 쓰는 이유는? SHA-256으로 해시하면 안 되나?
- traceId는 어디서 생겨서 어디까지 따라가나?
- 동시 가입에서 DB 제약까지 온 중복을 왜 구체적인 코드(`MEMBER_EMAIL_DUPLICATED`)가 아니라 `DUPLICATE_RESOURCE`로 줘도 되나?

---

## 1-4. 로그인과 JWT 인증 (M)

**목표**
- `POST /api/auth/login`이 Access 토큰(JWT)을 준다. 가입 응답도 토큰을 준다.
- `Authorization: Bearer {토큰}`으로 `GET /api/members/me`를 호출할 수 있다.
- 비밀번호가 틀리든 이메일이 없든 **같은 401 응답**을 준다.
- 인증 실패(401)·권한 부족(403)도 공통 에러 형식과 `X-Request-Id`를 가진다. 토큰이 없든 틀렸든 만료됐든 401 `UNAUTHORIZED` 하나다.

**왜 지금**: 이후 모든 API가 "누가 요청했는가"를 알아야 한다.

**새로 등장**

| 개념·도구 | 한 줄 설명 |
|---|---|
| JWT (jjwt) | `헤더.내용.서명`. 서버가 비밀키로 서명해서, 내용을 위조하면 서명 검증에서 걸린다 |
| 인증 필터 | 요청마다 토큰을 검증해 "누구인지"를 SecurityContext에 넣는다 |
| `AuthenticationEntryPoint` | 인증이 필요한데 없을 때 Security가 호출. 여기서 공통 에러 형식으로 401을 쓴다 |
| `@AuthenticationPrincipal` | Spring Security가 SecurityContext의 principal을 컨트롤러 파라미터로 넣어 준다. 이것을 감싼 `@CurrentMember`로 요청자 ID를 받는다. 요청 바디·헤더의 memberId는 믿지 않는다 |
| `@ConfigurationProperties` | 설정값을 record로 묶고 `@Validated`로 시작 시점에 검증한다 |

> **이 단계의 한계 (일부러 남김)**: refresh 토큰이 없어서 Access 토큰(1시간)이 만료되면 다시 로그인해야 하고, 로그아웃·강제 차단이 안 된다. Part 4에서 Redis와 함께 완성하며 그때 Access 만료를 15분으로 줄인다.

### 할 일

**1) 의존성**
```groovy
implementation 'io.jsonwebtoken:jjwt-api:{버전}'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:{버전}'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:{버전}'
```
- 버전은 Maven Central에서 최신 안정판(0.12.x 이상)을 확인해 넣는다(실제 사용: `0.13.0`, 세 아티팩트 모두 같은 버전). jjwt-jackson은 내부적으로 Jackson 2(`com.fasterxml`)를 끌어오지만 jjwt 안에서만 쓰이므로 그대로 둔다.

**2) 설정** — `common.security.JwtProperties`
- `@ConfigurationProperties("myroutine.jwt") @Validated public record JwtProperties(@NotBlank String secret, @NotNull Duration accessTokenTtl)`
- compact 생성자에서 `Base64.getDecoder().decode(secret).length < 32`이면 `IllegalArgumentException` → 앱이 시작되지 않는다(HS256 키 길이).
- 등록: `SecurityConfig`에 `@EnableConfigurationProperties(JwtProperties.class)`
- `application.yaml`: `myroutine.jwt.secret: ${JWT_SECRET}`, `myroutine.jwt.access-token-ttl: 1h`
- `application-test.yaml`: `myroutine.jwt.secret`에 테스트 전용 Base64 값(32바이트 이상)을 직접 적는다. 운영 키가 아니므로 커밋해도 된다.
- `.env.example`에 `JWT_SECRET=` 추가. 값 생성: `openssl rand -base64 32`

**3) common.security — 토큰**

| 클래스 | 규격 |
|---|---|
| `AuthClaims` | `public record AuthClaims(UUID memberId, String role)` |
| `JwtProvider` | `@Component`, 생성자 `(JwtProperties properties, Clock clock)`. 키는 생성자에서 `Keys.hmacShaKeyFor(Base64 decode)`로 한 번 만든다 |

`JwtProvider` 메서드
- `public String issue(UUID memberId, String role)`: `subject = memberId.toString()`, 클레임 `role`, `issuedAt = now`, `expiration = now + accessTokenTtl`. now는 `clock.instant()`
- `public Optional<AuthClaims> parse(String token)`: 파서에 `.clock(() -> Date.from(clock.instant()))`를 지정(만료 판단도 `Clock` 기준). 만료·서명 오류·형식 오류(`JwtException`, `IllegalArgumentException`)는 모두 `Optional.empty()`
- `public Duration accessTokenTtl()`
- role을 `String`으로 받는 이유: common이 `MemberRole`(member 모듈)을 import하지 않기 위해서다.

**4) common.security — 필터와 401/403**

| 클래스 | 규격 |
|---|---|
| `JwtAuthenticationFilter` | `extends OncePerRequestFilter`. **`@Component`를 붙이지 않는다**(붙이면 서블릿 필터로도 등록돼 두 번 돈다). `SecurityConfig`에서 `new JwtAuthenticationFilter(jwtProvider)`로 만들어 `http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)` |
| `RestAuthenticationEntryPoint` | `@Component`, `implements AuthenticationEntryPoint`. 401 |
| `RestAccessDeniedHandler` | `@Component`, `implements AccessDeniedHandler`. 403 `FORBIDDEN` |

`JwtAuthenticationFilter.doFilterInternal`
1. `Authorization` 헤더가 없거나 `"Bearer "`로 시작하지 않으면 → 그냥 `chain.doFilter` (인증 없이 진행, 막을지는 인가 단계가 정함)
2. `jwtProvider.parse(token)`이 값이 있으면 → `UsernamePasswordAuthenticationToken.authenticated(claims, null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())))`를 `SecurityContextHolder.getContext().setAuthentication(...)`
3. 비어 있으면(토큰이 틀림·만료) 인증을 설정하지 않고 `chain.doFilter` — 인증이 필요한 경로면 Security가 entry point를 불러 401

`RestAuthenticationEntryPoint.commence`
- 응답: status 401, `Content-Type: application/json;charset=UTF-8`, 본문 `ErrorResponse.of(UNAUTHORIZED, Map.of())`를 JSON으로 써서 내보낸다
- JSON 변환은 Boot가 만든 Jackson 3 빈을 생성자 주입으로 받는다. 주입 타입은 `tools.jackson.databind.ObjectMapper`다(1-4 구현에서 이 타입으로 주입·동작함을 확인했다. `JsonMapper`로 받을 수 있는지는 확인하지 않았다)
- `RestAccessDeniedHandler`도 같은 방식, 코드는 `FORBIDDEN`

`SecurityConfig` 변경
- `.exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))`
- 허용 목록은 그대로 `/api/auth/**`. Part 4에서 로그인이 필요한 `/api/auth/logout`이 생길 때 경로별로 좁힌다.

**5) common.security — `@CurrentMember`** (Spring Security 기능을 감싼 어노테이션 하나, 리졸버를 직접 만들지 않는다)
```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(expression = "memberId")
public @interface CurrentMember {}
```
- 컨트롤러: `public ... me(@CurrentMember UUID memberId)` — principal(`AuthClaims`)의 `memberId()`가 들어온다
- 인증이 필요한 경로에만 쓴다(인증 없는 요청은 Security가 먼저 401로 막는다)

**6) member — 로그인과 내 정보**

| 클래스 | 규격 |
|---|---|
| `MemberErrorCode` | `LOGIN_FAILED`, `MEMBER_BANNED`, `MEMBER_NOT_FOUND` 추가 |
| `MemberRepository` | `Optional<Member> findByEmail(String email)` 추가 |
| `Member` | `public void verifyCanLogin()`: BANNED → `BusinessException(MEMBER_BANNED)`, WITHDRAWN → `BusinessException(LOGIN_FAILED)` |
| `TokenResult` (member.application) | `record (String accessToken, long expiresInSeconds)` |
| `SignupResult` | `record (UUID memberId, TokenResult token)`으로 변경 |
| `LoginCommand` | `record (String email, String password)` |
| `LoginService` | 의존성 `MemberRepository`, `PasswordEncoder`, `JwtProvider`. `@Transactional(readOnly = true) public TokenResult login(LoginCommand command)` |
| `MemberQueryService` | `@Transactional(readOnly = true) public MeResult getMe(UUID memberId)` → 없으면 `MEMBER_NOT_FOUND` |
| `MeResult` | `record (UUID id, String email, String nickname, String name, String phone, MemberRole role, MemberStatus status, Instant createdAt)` |

`LoginService.login` 순서
1. `email = Member.normalizeEmail(command.email())`
2. `findByEmail(email)` 없음 → `LOGIN_FAILED`
3. `passwordHash`가 null이거나 `!passwordEncoder.matches(command.password(), passwordHash)` → `LOGIN_FAILED`
4. `member.verifyCanLogin()` — **비밀번호가 맞은 뒤에만** 제재 여부를 알려준다
5. `jwtProvider.issue(member.getId(), member.getRole().name())` → `new TokenResult(token, ttl.toSeconds())`
- `SignupService`도 저장 후 같은 방식으로 토큰을 발급해 `SignupResult`에 담는다.

**7) member.web**

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/auth/signup` | 1-3과 같음 | 201 `SignupResponse(UUID memberId, String accessToken, String tokenType, long expiresIn)` — tokenType은 항상 `"Bearer"` |
| `POST /api/auth/login` | `LoginRequest(@NotBlank @Email String email, @NotBlank String password)` — 로그인에서는 비밀번호 길이 규칙을 검사하지 않는다 | 200 `TokenResponse(String accessToken, String tokenType, long expiresIn)` |
| `GET /api/members/me` | `@CurrentMember UUID memberId` | 200 `MeResponse(UUID id, String email, String nickname, String name, String phone, String role, String status, Instant createdAt)` |

- `MemberController`: `@RestController @RequestMapping("/api/members")`

**8) 테스트 헬퍼** — `src/test/java/com/myroutine/support/TestFixtures.java`
- `@Component`. 의존성 `SignupService`, `JdbcTemplate`
- `UUID signup(String email)`: 닉네임은 이메일 앞부분, 비밀번호 `"pass1234"`, 이름 `"테스터"`로 가입하고 memberId 반환
- `String token(String email)`: 가입 후 accessToken 반환
- `void changeStatus(UUID memberId, String status)`: `UPDATE member.member SET status = ? WHERE id = ?` (제재 기능이 아직 없으므로 테스트에서 직접 바꾼다)
- 통합 테스트에서 `@Autowired TestFixtures fixtures;`로 쓴다. 이후 단계에서 헬퍼가 늘어난다: `login(email)`(1-4 이후), `openShop(memberId, businessNumber)`·`closeShop(shopId)`(1-6), `registerProduct(memberId, shopId[, category, initialStock])`·`hideProduct(productId)`·`discontinueProduct(productId)`(1-7·1-8).

### 완료 확인

| 테스트 클래스 | 종류 | 케이스 |
|---|---|---|
| `common/security/JwtProviderTest` | 단위 (`new JwtProvider(props, Clock.fixed(...))`) | 아래 |
| `member/web/AuthControllerTest` | API 통합 | 로그인 케이스 추가 |
| `member/web/MemberControllerTest` | API 통합 | `/me` 케이스 |
| `member/domain/MemberTest` | 단위 | `verifyCanLogin` 3가지 상태 |

`JwtProviderTest`
- [ ] 발급한 토큰을 검증하면 같은 memberId·role
- [ ] 만료: `Clock.fixed(t0)`로 발급, `Clock.fixed(t0 + 1h + 1s)`인 다른 `JwtProvider`로 검증 → `Optional.empty()`
- [ ] 서명 한 글자를 바꾼 토큰, 다른 키로 서명한 토큰, `"abc"` → `Optional.empty()`

`AuthControllerTest` (추가)
- [ ] 가입 응답에 `accessToken`, `tokenType = "Bearer"`, `expiresIn = 3600`
- [ ] 로그인 성공 → 200, 토큰으로 `/me` 200
- [ ] **틀린 비밀번호와 없는 이메일 → 둘 다 401 `LOGIN_FAILED`, `message` 동일**
- [ ] BANNED 회원(`fixtures.changeStatus`)이 올바른 비밀번호로 로그인 → 403 `MEMBER_BANNED` / 틀린 비밀번호 → 401 `LOGIN_FAILED`

`MemberControllerTest`
- [ ] 토큰으로 `/me` → 200, email·nickname·role(`USER`)·status(`ACTIVE`)
- [ ] 토큰 없이 `/me` → 401 `UNAUTHORIZED`, 본문이 공통 에러 형식, 헤더 `X-Request-Id` = 본문 `traceId`
- [ ] `Authorization: Bearer abc` → 401 `UNAUTHORIZED`
- [ ] 만료 토큰 → 401 `UNAUTHORIZED`. 만료 토큰은 테스트에서 `new JwtProvider(주입받은 JwtProperties, Clock.fixed(지금 - 2시간))`으로 발급한다

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"buyer@test.com","password":"pass1234"}' | jq -r .accessToken)
curl -s localhost:8080/api/members/me -H "Authorization: Bearer $TOKEN"
```

**제안 (선택)**
- 만료된 토큰에만 401 `TOKEN_EXPIRED`를 따로 주기(클라이언트가 "다시 로그인" 대신 "갱신"을 고를 수 있다) — refresh가 생기는 4-1 이후에 의미가 있다. 필터가 만료 여부를 요청 속성에 담고 entry point가 읽는 방식

**리뷰 때 물어볼 것**
- 비밀번호가 틀린 경우와 이메일이 없는 경우를 같은 응답으로 주는 이유는? 응답 시간 차이로도 구분할 수 있지 않나?
- 제재 여부를 비밀번호 확인 **뒤에** 알려주는 이유는?
- JWT는 서버에 상태가 없는데, 로그아웃이나 강제 차단은 어떻게 하나? (Part 4 예고)
- 서명 키가 유출되면 무슨 일이 생기고, 어떻게 대응하나? (As-Is MEM-01)
- 토큰에 역할을 넣었는데 역할이 바뀌면? (ADR-006)
- `JwtAuthenticationFilter`에 `@Component`를 붙이면 무슨 일이 생기나?

---

## 1-5. 내 정보 · 배송지 (S)

**목표**: 내 정보(닉네임·이름·전화) 수정, 배송지 등록·수정·삭제·조회. 기본 배송지는 회원당 최대 1개이고, DB가 이를 보장한다.

**왜 지금**: 2-2 체크아웃에서 배송지를 쓴다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 소유권 조회 | `findByIdAndMemberId(id, me)` — 남의 데이터는 "없는 것"처럼 404 (INV-11, As-Is ORD-08) |
| 부분 unique 인덱스 | `CREATE UNIQUE INDEX ... ON member.member_address (member_id) WHERE is_default` — "기본 배송지는 1개"를 DB가 보장 |
| `flush()` 순서 | JPA는 UPDATE 순서를 보장하지 않는다. "기존 기본 해제"가 "새 기본 지정"보다 먼저 DB에 반영돼야 부분 unique 인덱스에 걸리지 않으므로, 해제 후 `flush()`를 직접 부른다 |

**정책 (이 단계에서 정함)**
- 첫 배송지는 요청과 상관없이 자동으로 기본 배송지가 된다.
- 기본 배송지를 삭제하면 기본 배송지가 없는 상태가 된다(다른 배송지를 자동 지정하지 않음).
- 기본 배송지에 `isDefault: false`를 보내면 해제된다(기본 없음).

### 할 일

**1) 마이그레이션** — `db/migration/member/V{...}__member_create_member_address.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_member_address` |
| member_id | uuid | X | `fk_member_address_member` → `member.member(id)` |
| recipient | varchar(50) | X | |
| phone | varchar(20) | X | |
| zipcode | varchar(10) | X | |
| address1 | varchar(200) | X | |
| address2 | varchar(200) | O | |
| is_default | boolean | X | DEFAULT false |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 인덱스: `idx_member_address_member_id (member_id)`, 부분 unique `uk_member_address_default ON member.member_address (member_id) WHERE is_default`

**2) member.domain**

| 클래스 | 규격 |
|---|---|
| `MemberAddress` | `@Entity @Table(name = "member_address", schema = "member")`, `extends BaseTimeEntity`. 필드는 컬럼과 1:1 (`boolean isDefault`) |
| `MemberAddressRepository` | `MemberAddress save(MemberAddress a)`, `Optional<MemberAddress> findByIdAndMemberId(UUID id, UUID memberId)`, `List<MemberAddress> findAllByMemberId(UUID memberId)`, `Optional<MemberAddress> findByMemberIdAndIsDefaultTrue(UUID memberId)`, `boolean existsByMemberId(UUID memberId)`, `void delete(MemberAddress a)`, `void flush()` |
| `MemberErrorCode` | `MEMBER_ADDRESS_NOT_FOUND` 추가 |
| `Member` | `public void changeProfile(String nickname, String name, String phone)` — null인 인자는 바꾸지 않는다 |

`MemberAddress` 메서드
- `static MemberAddress register(UUID memberId, String recipient, String phone, String zipcode, String address1, String address2)` → `isDefault = false`
- `void update(String recipient, String phone, String zipcode, String address1, String address2)` — null은 유지
- `void markDefault()`, `void unmarkDefault()`

**3) member.infrastructure**
- `JpaMemberAddressRepository extends JpaRepository<MemberAddress, UUID>, MemberAddressRepository`

**4) member.application**

| 클래스 | 메서드 |
|---|---|
| `MemberProfileService` | `@Transactional MeResult changeProfile(UUID memberId, ChangeProfileCommand command)` |
| `MemberAddressService` | `@Transactional UUID register(UUID memberId, AddressCommand command)` / `@Transactional AddressResult update(UUID memberId, UUID addressId, AddressCommand command)` / `@Transactional void delete(UUID memberId, UUID addressId)` / `@Transactional(readOnly = true) List<AddressResult> getAddresses(UUID memberId)` |
| `ChangeProfileCommand` | `record (String nickname, String name, String phone)` |
| `AddressCommand` | `record (String recipient, String phone, String zipcode, String address1, String address2, Boolean isDefault)` |
| `AddressResult` | `record (UUID id, String recipient, String phone, String zipcode, String address1, String address2, boolean isDefault, Instant createdAt)` |

`changeProfile`: 회원 조회(없으면 `MEMBER_NOT_FOUND`) → nickname이 null이 아니고 현재와 다르며 `existsByNickname`이면 `MEMBER_NICKNAME_DUPLICATED` → `member.changeProfile(...)` → `MeResult`

기본 배송지 바꾸기 (private `changeDefault(UUID memberId, MemberAddress target)`, 등록·수정이 같이 쓴다)
1. `findByMemberIdAndIsDefaultTrue(memberId)`가 있고 `target`이 아니면 → `unmarkDefault()` → **`flush()`**
2. `target.markDefault()`

`register` 순서: `first = !existsByMemberId(memberId)` → `MemberAddress.register(...)` → `save` → `first`이거나 `isDefault == TRUE`면 `changeDefault` → id 반환
`update` 순서: `findByIdAndMemberId`(없으면 `MEMBER_ADDRESS_NOT_FOUND`) → `update(...)` → `isDefault == TRUE`면 `changeDefault`, `FALSE`면 `unmarkDefault()`, null이면 그대로

`getAddresses`: 기본 배송지가 먼저, 그다음 `createdAt` 내림차순으로 정렬해서 반환(자바에서 정렬해도 된다)

**5) member.web**

| API | 요청 | 응답 |
|---|---|---|
| `PATCH /api/members/me` | `ChangeProfileRequest(@Size(min=2,max=20) String nickname, @Size(min=1,max=50) String name, @Pattern(regexp="^[0-9-]{9,20}$") String phone)` 모두 선택 | 200 `MeResponse` |
| `GET /api/members/me/addresses` | | 200 `List<AddressResponse>` |
| `POST /api/members/me/addresses` | `AddressRequest` (아래, 필수 검증 그룹) | 201 `AddressIdResponse(UUID addressId)` |
| `PATCH /api/members/me/addresses/{id}` | `UpdateAddressRequest` (같은 필드, 모두 선택) | 200 `AddressResponse` |
| `DELETE /api/members/me/addresses/{id}` | | 204 |

`AddressRequest`: `recipient @NotBlank @Size(max=50)`, `phone @NotBlank @Pattern(regexp="^[0-9-]{9,20}$")`, `zipcode @NotBlank @Pattern(regexp="^\\d{5}$")`, `address1 @NotBlank @Size(max=200)`, `address2 @Size(max=200)`, `isDefault Boolean`
`UpdateAddressRequest`: 같은 필드에서 `@NotBlank`만 뺀다.
- 컨트롤러: `MemberController`에 PATCH `/me` 추가, 배송지는 `MemberAddressController` (`@RequestMapping("/api/members/me/addresses")`)

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `member/web/MemberControllerTest` | [ ] 닉네임·전화 수정 → 200, 이름은 그대로 / [ ] 다른 회원의 닉네임으로 변경 → 409 `MEMBER_NICKNAME_DUPLICATED` / [ ] 자기 닉네임 그대로 보내면 200 |
| `member/web/MemberAddressControllerTest` | [ ] 첫 배송지는 `isDefault: false`로 보내도 기본 / [ ] 두 번째를 `isDefault: true`로 등록 → 첫 번째 해제, 기본은 1개 / [ ] PATCH로 기본 변경 → 이전 기본 해제 / [ ] 다른 회원의 배송지 PATCH·DELETE → 404 `MEMBER_ADDRESS_NOT_FOUND` / [ ] 목록은 기본 배송지가 첫 번째 / [ ] 우편번호 4자리 → 400 |
| `member/application/DefaultAddressConcurrencyTest` | [ ] 배송지 A(기본), B, C가 있을 때 B와 C를 동시에 기본으로 지정(서비스 직접 호출, 스레드 2개 + latch) → 매번 `SELECT count(*) FROM member.member_address WHERE is_default` = 1. 실패한 호출이 있다면 낙관적 락 충돌이나 unique 위반이어야 한다(API에서는 409). `@RepeatedTest(10)` |

**리뷰 때 물어볼 것**
- 남의 리소스에 403이 아니라 404를 주는 이유는? 가게는 왜 403인가?
- "기본 배송지 1개"를 애플리케이션 코드로만 보장하면 무엇이 문제인가?
- 기존 기본을 해제한 뒤 `flush()` 없이 새 기본을 지정하면 무슨 일이 생길 수 있나?

---

## 1-6. 가게 개설 · 조회 · 수정 (모듈 경계 도입) (M)

**목표**
- 회원이 가게를 열고(즉시 운영 상태), 자기 가게를 조회·수정한다.
- 모듈이 2개가 되는 시점이라 **모듈 경계를 테스트로 강제**하기 시작한다.

**왜 지금**: 상품(1-7)은 가게에 속한다. 그리고 모듈이 둘 이상이 되는 순간부터 "다른 모듈 내부를 건드리지 않는다"는 규칙이 의미가 생긴다.

**새로 등장**

| 개념·도구 | 한 줄 설명 |
|---|---|
| Spring Modulith | 모듈 간 의존 규칙 위반을 테스트로 잡는다. 테스트 1개 + `package-info.java` |
| 모듈 공개 API | 다른 모듈은 `shop.api` 패키지의 인터페이스·DTO만 쓴다 |
| 소유권 기반 인가 | 판매자 기능은 "SELLER 역할이 있나"가 아니라 "이 가게의 주인인가"로 판단 |
| 낙관적 락 | 같은 버전을 읽은 두 트랜잭션 중 늦게 커밋하는 쪽이 실패 → 409 `CONFLICT_RETRY` (핸들러는 1-3에서 만듦) |

> SELLER 역할 부여는 Part 3(3-1)에서 이벤트로 붙인다. 소유권으로 인가하기 때문에 역할이 없어도 지금 기능은 모두 동작한다.
> 가게 개설의 멱등 처리(`Idempotency-Key`)는 2-2에서 공통 장치를 만들 때 붙인다.

**정책 (이 단계에서 정함)**
- 사업자번호는 하이픈 없는 숫자 10자리. 체크섬 검증은 하지 않는다. 등록 후 변경할 수 없다.
- 한 회원이 여러 가게를 열 수 있다(개수 제한 없음).
- CLOSED 가게는 수정할 수 없다(422 `SHOP_NOT_ACTIVE`). 폐업 기능은 3-2.

### 할 일

**1) Spring Modulith**
```groovy
dependencyManagement {
    imports { mavenBom "org.springframework.modulith:spring-modulith-bom:2.1.1" }
}
implementation 'org.springframework.modulith:spring-modulith-starter-core'
testImplementation 'org.springframework.modulith:spring-modulith-starter-test'
```
- 확인함: Spring Boot 4.1.1과 Modulith `2.1.1` 조합이 동작한다.
- `src/test/java/com/myroutine/ModularityTest.java`: Spring 없이 `ApplicationModules.of(MyRoutineApplication.class).verify();`

`package-info.java`

| 파일 | 내용 |
|---|---|
| `common/package-info.java` | `@ApplicationModule(type = ApplicationModule.Type.OPEN)` |
| `member/package-info.java` | `@ApplicationModule(allowedDependencies = {"common"})` |
| `shop/package-info.java` | `@ApplicationModule(allowedDependencies = {"common"})` |
| `shop/api/package-info.java` | `@NamedInterface("api")` |

**2) 마이그레이션** — `db/migration/shop/V{...}__shop_create_shop.sql` (`CREATE SCHEMA IF NOT EXISTS shop;`)

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_shop` |
| member_id | uuid | X | (다른 모듈이라 FK 없음) |
| name | varchar(50) | X | |
| business_number | varchar(10) | X | `uk_shop_business_number` |
| email | varchar(255) | X | |
| phone | varchar(20) | X | |
| address | varchar(255) | X | |
| status | varchar(20) | X | `ck_shop_status`: `IN ('ACTIVE','CLOSED')` |
| closed_at | timestamptz | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 인덱스 `idx_shop_member_id (member_id)`

**3) shop.domain**

| 클래스 | 규격 |
|---|---|
| `ShopStatus` | `ACTIVE, CLOSED`. 전이 `ACTIVE → {CLOSED}` |
| `ShopErrorCode` | `SHOP_NOT_FOUND`, `SHOP_BUSINESS_NUMBER_DUPLICATED`, `SHOP_NOT_ACTIVE` |
| `Shop` | `@Entity @Table(name = "shop", schema = "shop")`, `extends BaseTimeEntity`, 필드는 컬럼과 1:1 |
| `ShopRepository` | `Shop save(Shop s)`, `Optional<Shop> findById(UUID id)`, `List<Shop> findAllByMemberIdOrderByCreatedAtDesc(UUID memberId)`, `List<Shop> findAllByIdIn(Collection<UUID> ids)`, `boolean existsByBusinessNumber(String businessNumber)` |

`Shop` 메서드
- `static Shop open(UUID memberId, String name, String businessNumber, String email, String phone, String address)` → ACTIVE
- `boolean isActive()`
- `void verifyOwner(UUID memberId)` → 소유자가 아니면 `BusinessException(CommonErrorCode.FORBIDDEN)`
- `void verifyActive()` → ACTIVE가 아니면 `BusinessException(SHOP_NOT_ACTIVE)`
- `void update(String name, String email, String phone, String address)` → 먼저 `verifyActive()`, null은 유지

**4) shop.infrastructure**: `JpaShopRepository`

**5) shop.api — 다른 모듈용 계약**
```java
public interface ShopApi {
    void verifyOwnerOfActiveShop(UUID shopId, UUID memberId);   // 상품 등록·수정용: 404 → 403 → 422 순서로 검사
    void verifyOwner(UUID shopId, UUID memberId);               // 판매자 조회용: CLOSED여도 통과. 404 → 403
    List<ShopInfo> requireActiveShops(Collection<UUID> shopIds); // 2-2 체크아웃용. 하나라도 없거나 CLOSED면 SHOP_NOT_ACTIVE(details.shopIds)
}
public record ShopInfo(UUID shopId, UUID ownerId, String name) {}
```
- 구현 `ShopApiImpl`은 `shop.application`에 **package-private** 클래스로 둔다(`@Service @RequiredArgsConstructor class ShopApiImpl implements ShopApi`), 메서드는 `@Transactional(readOnly = true)`.
- 검사 실패 시 shop의 에러 코드로 예외를 던진다. 그래서 product는 shop의 에러 코드를 몰라도 된다.
- `requireActiveShops` 규칙
  - 호출하는 쪽(주문 품목·구독 상품에서 가게 ID를 뽑는 곳)은 같은 가게 ID를 여러 번 넘길 수 있다. **중복을 제거한 뒤** 처리하고, 반환도 가게당 하나다.
  - 입력이 비어 있으면 빈 리스트를 반환한다(빈 주문 같은 검증은 호출하는 쪽 책임).
  - 반환 순서는 보장하지 않는다(`IN` 조회에 정렬이 없다). 호출하는 쪽은 `shopId`로 찾아 쓴다.
  - `findAllByIdIn`으로 한 번에 조회한 뒤, **조회되지 않은 ID**와 **ACTIVE가 아닌 ID**를 모두 모은다. 첫 번째 문제에서 바로 던지지 않는다.
  - 문제 ID가 하나라도 있으면 `BusinessException(SHOP_NOT_ACTIVE, Map.of("shopIds", 문제 ID 목록))`을 던진다. 값은 UUID 리스트이고, 응답 JSON에서는 배열이다.

**6) shop.application**

| 클래스 | 메서드 |
|---|---|
| `OpenShopService` | `@Transactional UUID open(UUID memberId, OpenShopCommand command)`: `existsByBusinessNumber` → 409 / `Shop.open` → save |
| `UpdateShopService` | `@Transactional ShopResult update(UUID memberId, UUID shopId, UpdateShopCommand command)`: `findById` 없으면 `SHOP_NOT_FOUND` → `verifyOwner` → `update` |
| `ShopQueryService` | `@Transactional(readOnly = true) List<ShopResult> getMyShops(UUID memberId)` / `ShopResult getShop(UUID shopId)` |
| `OpenShopCommand` | `record (String name, String businessNumber, String email, String phone, String address)` |
| `UpdateShopCommand` | `record (String name, String email, String phone, String address)` |
| `ShopResult` | `record (UUID id, UUID memberId, String name, String businessNumber, String email, String phone, String address, ShopStatus status, Instant createdAt)` |

**7) shop.web** — `ShopController` (`/api/shops`)

| API | 권한 | 요청 | 응답 |
|---|---|---|---|
| `POST /api/shops` | 로그인 | `OpenShopRequest` | 201 `ShopIdResponse(UUID shopId)` |
| `GET /api/shops/me` | 로그인 | | 200 `List<MyShopResponse>` (ShopResult 전체 필드) |
| `GET /api/shops/{id}` | **공개** | | 200 `ShopResponse(UUID id, String name, String phone, String address, String status)` |
| `PATCH /api/shops/{id}` | 소유자 | `UpdateShopRequest` | 200 `MyShopResponse` |

- `OpenShopRequest`: `name @NotBlank @Size(max=50)`, `businessNumber @NotBlank @Pattern(regexp="^\\d{10}$")`, `email @NotBlank @Email @Size(max=255)`, `phone @NotBlank @Pattern(regexp="^[0-9-]{9,20}$")`, `address @NotBlank @Size(max=255)`
- `UpdateShopRequest`: businessNumber를 뺀 같은 필드. 각 필드는 `null`(변경 없음)이거나 값이 있어야 한다. **빈 문자열·공백만 있는 문자열은 400 `INVALID_REQUEST`**(그대로 저장되면 이름이 빈 값이 되어 개설 때의 `@NotBlank`와 어긋난다).
  - `@NotBlank`는 `null`도 막으므로 쓰지 않는다. `name`·`email`·`address`에 `@Pattern(regexp = ".*\\S.*")`를 더해 공백만 있는 값을 거른다(`null`은 통과). `phone`은 기존 `@Pattern`이 이미 빈 문자열을 거른다.
  - 이 정규식은 `""`·`" "`을 거르고 `"a"`·`" a "`는 통과시킨다. 줄바꿈이 들어간 값(`"a\nb"`)도 거절된다(가게 이름·이메일·주소에 줄바꿈은 허용하지 않는다).
- `SecurityConfig`: `GET /api/shops/*`를 공개로 열되 **`/api/shops/me`를 먼저 인증 필요로 선언**한다(순서대로 매칭되므로 `/me`가 `*`에 먼저 걸리면 공개가 된다).
```java
.requestMatchers("/api/shops/me").authenticated()
.requestMatchers(HttpMethod.GET, "/api/shops/*").permitAll()
```

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `ModularityTest` | [ ] 통과 / [ ] **일부러 shop에서 `member.domain.Member`를 import → verify 실패 확인** (커밋하지 않고 PR에 결과 기록) |
| `shop/domain/ShopStatusTest` | [ ] 4개 조합 파라미터화 |
| `shop/domain/ShopTest` | [ ] `open` → ACTIVE, id 생성 / [ ] `verifyOwner` 다른 회원 → `FORBIDDEN` (CLOSED 상태는 폐업 기능 전이라 만들 수 없으므로 아래 통합 테스트에서 확인) |
| `shop/web/ShopControllerTest` | [ ] 개설 → 201, 내 가게 목록에 보임 / [ ] 토큰 없이 `GET /api/shops/{id}` → 200 / [ ] 토큰 없이 `GET /api/shops/me` → 401 / [ ] 소유자가 일부 필드(예: `name`)만 PATCH → 200, 보낸 필드만 바뀌고 나머지는 유지(`null`은 유지 규칙) / [ ] `name`을 빈 문자열로 PATCH → 400 `INVALID_REQUEST` / [ ] 다른 회원이 PATCH → 403 `FORBIDDEN` / [ ] 사업자번호 중복 → 409 / [ ] CLOSED 가게(JDBC로 상태 변경) PATCH → 422 `SHOP_NOT_ACTIVE` / [ ] 사업자번호 9자리 → 400 |
| `shop/application/ShopApiImplTest` | [ ] `verifyOwnerOfActiveShop`: 없는 가게 404 / 남의 가게 403 / CLOSED 422 / 정상 통과 / [ ] `requireActiveShops`: 모두 ACTIVE면 전부 반환 / CLOSED 하나 → `SHOP_NOT_ACTIVE`, `details.shopIds`에 그 ID / **없는 ID 하나(나머지는 ACTIVE)** → 같은 에러, `details.shopIds`에 그 ID / CLOSED와 없음이 섞이면 둘 다 `details.shopIds`에 / 중복 ID를 넘겨도 반환은 가게당 하나 / 빈 입력 → 빈 리스트 |

**제안 (선택)**
- 낙관적 락 충돌을 결정적으로 재현하는 테스트: 바깥 `TransactionTemplate`에서 가게를 읽고, 안쪽 `PROPAGATION_REQUIRES_NEW` 트랜잭션이 같은 가게를 먼저 수정·커밋한 뒤 바깥에서 수정·커밋 → `ObjectOptimisticLockingFailureException`

**리뷰 때 물어볼 것**
- 모듈 경계를 테스트로 강제하지 않으면 시간이 지나 어떻게 되나? (As-Is BAT-03)
- 판매자 기능을 SELLER 역할이 아니라 소유권으로 인가하면 무엇이 좋은가?
- 낙관적 락 충돌이 나면 사용자는 무엇을 보게 되나? 재시도는 누가 하나?
- `ShopApi`가 `isActive()` 같은 boolean 대신 `verify...()`로 예외를 던지게 한 이유는?

---

## 1-7. 상품 등록 · 조회 (M)

**목표**
- 판매자가 자기 가게에 상품을 등록한다(초기 재고 포함, 한 트랜잭션).
- 누구나 판매 중인 상품 목록(커서 페이징)과 상세를 본다.

**왜 지금**: 주문할 대상이 필요하다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 모듈 간 호출 | product가 `ShopApi`로 가게 소유자·운영 상태를 확인 |
| `Money` 값 객체 | 금액을 `long`으로 들고 다니지 않고, 음수 불가·연산 규칙을 가진 record로 |
| keyset(커서) 페이징 | `WHERE (created_at, id) < (:c, :id) ORDER BY created_at DESC, id DESC LIMIT n`. offset과 달리 뒤 페이지도 빠르고, 중간에 데이터가 추가돼도 중복·누락이 없다 |
| DB CHECK로 불변식 | `CHECK (available + reserved + sold = received)` — 재고 등식(INV-03)을 DB가 보장 |
| native INSERT | 저장만 하고 다시 고치지 않는 행(재고 이력)과 조건부 UPDATE로만 바뀌는 행(재고)은 엔티티 `save()` 대신 native 쿼리로 쓴다 |

> 상품 등록의 멱등 처리(`Idempotency-Key`)는 2-2에서, 이미지 업로드(presigned URL)는 1-10에서 붙인다. 이번 단계의 상품에는 이미지가 없다(`thumbnail_key`는 null).

**정책 (이 단계에서 정함)**
- 카테고리: `FOOD, HEALTH, BEAUTY, LIVING, PET, ETC`
- 등록 직후 상태는 `ON_SALE`. 가격은 1원 이상 1억 원 이하, 초기 재고는 0~1,000,000.
- 공개 목록 정렬은 최신순만 지원한다(다른 정렬은 Part 6 검색에서).
- 상세 조회: HIDDEN은 404, ON_SALE·DISCONTINUED는 보인다(상태를 함께 내려준다).

### 할 일

**1) common.model — Money**
```java
public record Money(long amount) implements Comparable<Money> { ... }
```
- `static final Money ZERO`, `static Money of(long amount)`
- compact 생성자: `amount < 0`이면 `IllegalArgumentException`
- `Money plus(Money)` → `Math.addExact`, `Money minus(Money)` → 결과가 음수면 생성자에서 예외, `Money times(int quantity)` → `Math.multiplyExact` (오버플로 시 `ArithmeticException`). 세 메서드 모두 자기 자신을 바꾸지 않고 새 `Money`를 반환한다(불변)
- `boolean isGreaterThan(Money)`, `int compareTo(Money)`
- 예외를 `BusinessException`이 아닌 표준 예외(`IllegalArgumentException`·`ArithmeticException`)로 던지는 이유: 사용자 입력 오류는 web 검증이 먼저 400으로 막는다. `Money`까지 잘못된 값이 왔다면 비즈니스 규칙 위반이 아니라 프로그래밍 오류이므로, 잡지 않고 500으로 드러나게 한다. `common.model`이 `ErrorCode`에 의존하지 않게 하는 효과도 있다.
- `MoneyConverter`: `@Converter(autoApply = true) public class MoneyConverter implements AttributeConverter<Money, Long>` — null은 null로

**2) common.web — 커서**

| 클래스 | 규격 |
|---|---|
| `Cursor` | `public record Cursor(Instant createdAt, UUID id)`. `String encode()` → `createdAt.toString() + "\|" + id`를 Base64 URL-safe(패딩 없음)로. `static Cursor decode(String value)` → 해석 실패 시 `BusinessException(INVALID_REQUEST)` |
| `CursorPage<T>` | `public record CursorPage<T>(List<T> items, String nextCursor)` — 마지막 페이지면 `nextCursor = null` |

- `decode`의 실패 지점은 네 곳이며 모두 같은 `BusinessException(INVALID_REQUEST)`로 바꿔 던진다: ① Base64 디코드 실패(`IllegalArgumentException`) ② `\|`로 나눈 조각이 정확히 2개가 아님(예외가 저절로 나지 않으므로 길이를 직접 검사) ③ `Instant.parse` 실패(`DateTimeParseException`) ④ `UUID.fromString` 실패(`IllegalArgumentException`). `catch (Exception)`처럼 넓게 잡지 않고 위 예외 종류만 잡는다(NPE 같은 실제 버그를 400으로 덮지 않기 위해). 깨진 커서는 클라이언트 입력 오류(400)이므로 원인 예외는 넘기지 않고(`BusinessException`에 cause 생성자가 없다), 응답 메시지에도 내부 사정을 쓰지 않는다.
- `null`·빈 커서는 `decode`에 넘기지 않는다. 커서가 없으면 서비스가 첫 페이지 쿼리를 부른다.
- 다음 페이지가 있는지는 `size + 1`개를 조회해서 판단한다. `size + 1`번째가 있으면 `size`개만 내려주고, `size`번째 항목으로 커서를 만든다.

**3) 마이그레이션** — `db/migration/product/V{...}__product_create_product.sql` (`CREATE SCHEMA IF NOT EXISTS product;`, 네 테이블을 한 파일에)

`product.product`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_product` |
| shop_id | uuid | X | (FK 없음) |
| name | varchar(100) | X | |
| description | text | X | |
| category | varchar(20) | X | `ck_product_category` |
| price | bigint | X | `ck_product_price`: `price > 0` |
| status | varchar(20) | X | `ck_product_status`: `IN ('ON_SALE','HIDDEN','DISCONTINUED')` |
| subscribable | boolean | X | DEFAULT false |
| thumbnail_key | varchar(255) | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 인덱스: `idx_product_status_created_at (status, created_at DESC, id DESC)`, `idx_product_status_category_created_at (status, category, created_at DESC, id DESC)`, `idx_product_shop_id_created_at (shop_id, created_at DESC, id DESC)`

`product.product_image` (이번 단계에서는 테이블만): id `pk_product_image`, product_id `fk_product_image_product`, object_key varchar(255) X, sort_order int X, created_at X

`product.stock`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| product_id | uuid | X | `pk_stock`, `fk_stock_product` |
| available | int | X | `ck_stock_available`: `>= 0` |
| reserved | int | X | `ck_stock_reserved`: `>= 0` |
| sold | int | X | `ck_stock_sold`: `>= 0` |
| received | int | X | `ck_stock_balance`: `available + reserved + sold = received` |
| created_at, updated_at | timestamptz | X | |

`product.stock_movement`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_stock_movement` |
| product_id | uuid | X | `fk_stock_movement_product` |
| type | varchar(20) | X | `ck_stock_movement_type`: `IN ('RECEIVE','ADJUST','RESERVE','RELEASE','COMMIT','RESTORE')` |
| quantity | int | X | 증감량(부호 포함) |
| ref_type | varchar(30) | X | `ck_stock_movement_ref_type`: `IN ('PRODUCT_REGISTER','ADJUSTMENT','ORDER','REFUND')` (이후 Part에서 값이 늘면 그 단계의 마이그레이션에서 CHECK를 고쳐 만든다) |
| ref_id | uuid | X | |
| reason | varchar(200) | O | 조정 사유 (1-8) |
| created_at | timestamptz | X | |

- unique `uk_stock_movement_type_ref (type, ref_type, ref_id, product_id)`

**4-1) product.domain**

| 클래스 | 규격 |
|---|---|
| `ProductStatus` | `ON_SALE, HIDDEN, DISCONTINUED`. 전이 `ON_SALE → {HIDDEN, DISCONTINUED}`, `HIDDEN → {ON_SALE, DISCONTINUED}`, `DISCONTINUED → {}` |
| `ProductCategory` | 위 정책의 6개 |
| `StockMovementType` | `RECEIVE, ADJUST, RESERVE, RELEASE, COMMIT, RESTORE` |
| `StockRefType` | `PRODUCT_REGISTER, ADJUSTMENT, ORDER, REFUND` (`stock_movement.ref_type`. DB의 `ck_stock_movement_ref_type`과 값이 같아야 한다) |
| `ProductErrorCode` | `PRODUCT_NOT_FOUND` (1-8·1-9에서 추가) |
| `Product` | `@Entity @Table(name = "product", schema = "product")`, `extends BaseTimeEntity`. `Money price`(컨버터 자동 적용), `@Version Long version`. `static Product register(UUID shopId, String name, String description, ProductCategory category, Money price, boolean subscribable)` → ON_SALE. `boolean isVisibleToPublic()` → HIDDEN이 아니면 true |
| `Stock` | `@Entity @Table(name = "stock", schema = "product")`, `extends BaseTimeEntity`. `@Id UUID productId`, `int available, reserved, sold, received`. **읽기 전용**: `save()`를 쓰지 않고 아래 native 쿼리로만 쓴다(그래서 `@Version`이 없다). `boolean inStock()` → `available > 0` |
| `ProductListRow` | 목록 조회 결과용 record(필드는 아래 4-2 목록 쿼리 참고). 엔티티가 아니다 |
| `ProductRepository` | **순수 인터페이스**(Spring Data 어노테이션 없음, 가이드 §8.1). `Product save(Product p)`, `Optional<Product> findById(UUID id)`, `List<ProductListRow> findPublicFirstPage(...)`·`findPublicNextPage(...)`·`findByShopFirstPage(...)`·`findByShopNextPage(...)` (파라미터는 4-2 목록 쿼리) |
| `StockRepository` | **순수 인터페이스**. `Optional<Stock> findById(UUID productId)`, `int insertStock(UUID productId, int quantity, Instant now)`, `int insertMovement(UUID id, UUID productId, String type, int quantity, String refType, UUID refId, String reason, Instant now)` |

**4-2) product.infrastructure** — `JpaProductRepository`, `JpaStockRepository`
- 구조는 1-6과 같다: `public interface JpaProductRepository extends JpaRepository<Product, UUID>, ProductRepository`, `public interface JpaStockRepository extends JpaRepository<Stock, UUID>, StockRepository`.
- `@Query`·`@Modifying`·`@Param` 같은 Spring Data 어노테이션과 native 쿼리는 **여기에만** 둔다. `domain`은 Spring Data에 의존하지 않는다(개발 가이드 §8.1, 의존 방향 `domain ← infrastructure`).
- 1-8·1-9에서 `StockRepository`에 추가되는 쿼리도 같은 방식으로 `JpaStockRepository`에 구현한다.

`JpaStockRepository` native 쿼리 (모두 `@Modifying @Query(nativeQuery = true, ...)`, 반환 `int`)
- `insertStock(UUID productId, int quantity, Instant now)`: `INSERT INTO product.stock (product_id, available, reserved, sold, received, created_at, updated_at) VALUES (:productId, :quantity, 0, 0, :quantity, :now, :now)`
- `insertMovement(UUID id, UUID productId, String type, int quantity, String refType, UUID refId, String reason, Instant now)`: `INSERT INTO product.stock_movement (...) VALUES (...) ON CONFLICT ON CONSTRAINT uk_stock_movement_type_ref DO NOTHING` — 반환값 1 = 새로 기록, 0 = 이미 있음
- type은 `StockMovementType.name()`, refType은 `StockRefType.name()`을 넘긴다(문자열 리터럴을 직접 쓰지 않는다 — 오타를 컴파일 때 잡기 위해). `now`는 서비스에서 `Instant.now(clock)`

목록 쿼리 (`JpaProductRepository`, JPQL — `@Query("...")`만 쓰고 `nativeQuery`·`@Modifying`은 쓰지 않는다. 반환은 모두 `List<ProductListRow>`)
- 조회 결과용 record `ProductListRow(UUID id, UUID shopId, String name, ProductCategory category, Money price, ProductStatus status, String thumbnailKey, Instant createdAt, int available, int reserved, int sold, int received)` — `product.domain`에 둔다. `select new`의 인자는 이 순서·타입 그대로(앞 8개 `p.`, 뒤 4개 `s.`)
- JPQL에서 `Product`와 `Stock`은 연관관계가 없으므로 `join Stock s on s.productId = p.id`(엔티티 조인)로 묶고, `select new com.myroutine.product.domain.ProductListRow(...)`로 받는다. 한 번의 쿼리로 재고까지 가져온다(N+1 방지)
- 공개 목록: `findPublicFirstPage(ProductStatus status, ProductCategory category, Limit limit)`, `findPublicNextPage(ProductStatus status, ProductCategory category, Instant cursorCreatedAt, UUID cursorId, Limit limit)`
  - 조건: `p.status = :status and (:category is null or p.category = :category)`, 다음 페이지는 추가로 `and (p.createdAt < :cursorCreatedAt or (p.createdAt = :cursorCreatedAt and p.id < :cursorId))`
  - 정렬: `order by p.createdAt desc, p.id desc`
  - `status`는 항상 `ProductStatus.ON_SALE`을 파라미터로 넘긴다(쿼리에 문자열로 쓰지 않음)
- 판매자 목록: `findByShopFirstPage(UUID shopId, Limit limit)`, `findByShopNextPage(UUID shopId, Instant cursorCreatedAt, UUID cursorId, Limit limit)` — 상태 조건 없음
- `org.springframework.data.domain.Limit`는 메서드 파라미터로 넘기면 LIMIT가 적용된다(JPQL 문자열에 `limit`을 쓰지 않는다). 호출하는 쪽은 `Limit.of(size + 1)`.
- 확인함(1-7 구현): `(:category is null or ...)`는 Hibernate 7 + Postgres에서 카테고리를 생략한 호출(null)과 지정한 호출 모두 정상 동작한다. 메서드를 나눌 필요 없다.

**5) product.application**

| 클래스 | 메서드 |
|---|---|
| `RegisterProductService` | 의존성 `ShopApi`, `ProductRepository`, `StockRepository`, `Clock`. `@Transactional UUID register(UUID memberId, UUID shopId, RegisterProductCommand command)` |
| `ProductQueryService` | 의존성 `ShopApi`(`getShopProducts`의 소유자 확인용), `ProductRepository`, `StockRepository`. `@Transactional(readOnly = true)`: `CursorPage<ProductSummaryResult> getPublicProducts(ProductCategory category, String cursor, int size)` / `ProductDetailResult getProduct(UUID productId)` / `CursorPage<SellerProductResult> getShopProducts(UUID memberId, UUID shopId, String cursor, int size)` |
| `RegisterProductCommand` | `record (String name, String description, ProductCategory category, long price, int initialStock, boolean subscribable)` |
| `ProductSummaryResult` | `record (UUID id, UUID shopId, String name, ProductCategory category, long price, String thumbnailKey, boolean inStock, Instant createdAt)` |
| `ProductDetailResult` | `record (UUID id, UUID shopId, String name, String description, ProductCategory category, long price, ProductStatus status, boolean subscribable, String thumbnailKey, boolean inStock, Instant createdAt)` |
| `SellerProductResult` | `record (UUID id, String name, ProductCategory category, long price, ProductStatus status, int available, int reserved, int sold, int received, Instant createdAt)` |

`register` 순서 (전부 한 트랜잭션)
1. `shopApi.verifyOwnerOfActiveShop(shopId, memberId)`
2. `Product.register(...)` → `productRepository.save`
3. `stockRepository.insertStock(productId, initialStock, now)`
4. `stockRepository.insertMovement(Ids.newId(), productId, StockMovementType.RECEIVE.name(), initialStock, StockRefType.PRODUCT_REGISTER.name(), productId, null, now)`
- 3·4 중 하나라도 실패하면 2도 롤백된다(As-Is CAT-04).

`getProduct`: 없거나 `!isVisibleToPublic()`이면 `PRODUCT_NOT_FOUND`. 재고는 `stockRepository.findById`. 재고가 없으면 `IllegalStateException`을 던진다(500). 등록이 상품·재고를 한 트랜잭션으로 저장하므로 재고 없는 상품은 불변식이 깨진 상황이지 클라이언트 오류가 아니다. 그래서 `STOCK_NOT_FOUND` 같은 에러 코드를 만들지 않고, `inStock = false`로 조용히 넘기지도 않는다.
`getShopProducts`: 먼저 `shopApi.verifyOwner(shopId, memberId)`. 이후 페이징은 아래 `getPublicProducts`와 같고, 쿼리만 `findByShopFirstPage`·`findByShopNextPage`를 쓴다.

`getPublicProducts` / `getShopProducts` 페이징 순서
1. 첫 페이지 판단: `cursor == null || cursor.isBlank()`면 첫 페이지다. `?cursor=`는 `null`이 아니라 `""`로 바인딩되므로 둘 다 "커서 없음"으로 본다. 이때만 `Cursor.decode`를 건너뛴다.
2. 쿼리 선택: 커서 없음 → `findPublicFirstPage(ProductStatus.ON_SALE, category, Limit.of(size + 1))`, 커서 있음 → `Cursor.decode(cursor)`로 `(createdAt, id)`를 복원해 `findPublicNextPage(ProductStatus.ON_SALE, category, c.createdAt(), c.id(), Limit.of(size + 1))`. `status`는 항상 `ON_SALE`을 파라미터로 넘긴다.
3. 다음 페이지 판단: 받은 `List<ProductListRow>`가 `size`보다 많으면 다음 페이지가 있다. `size`개만 남기고, **`size`번째 행**의 `createdAt`·`id`로 `new Cursor(...).encode()`를 만들어 `nextCursor`에 넣는다. 없으면 `nextCursor = null`.
4. 변환: 남긴 행을 Result로 바꾼다. `price`는 `Money.amount()`로 꺼내고, `inStock`은 `available > 0`(`Stock.inStock()`과 같은 규칙).
5. `new CursorPage<>(items, nextCursor)`를 반환한다. 두 목록의 `size + 1` 처리·커서 생성이 겹치므로 공통 메서드로 뽑을지는 구현 때 정한다.

**6) product.web** — 컨트롤러 2개: 공개 조회용 `ProductController`(`/api/products`)와 판매자용 `SellerProductController`(`/api/shops/{shopId}/products`). 1-8의 수정·재고 조정 API는 `SellerProductController`에 추가한다.

| 컨트롤러 | API | 권한 | 요청 | 응답 |
|---|---|---|---|---|
| `SellerProductController` | `POST /api/shops/{shopId}/products` | 소유자 | `RegisterProductRequest` | 201 `ProductIdResponse(UUID productId)` |
| `ProductController` | `GET /api/products?category=&cursor=&size=` | **공개** | size 기본 20, `@Min(1) @Max(50)` | 200 `CursorPage<ProductSummaryResponse>` |
| `ProductController` | `GET /api/products/{id}` | **공개** | | 200 `ProductDetailResponse` |
| `SellerProductController` | `GET /api/shops/{shopId}/products?cursor=&size=` | 소유자 | size 규칙은 공개 목록과 같다 | 200 `CursorPage<SellerProductResponse>` |

- `RegisterProductRequest`: `name @NotBlank @Size(max=100)`, `description @NotNull @Size(max=5000)`, `category @NotNull ProductCategory`, `price @NotNull @Min(1) @Max(100_000_000) Long`, `initialStock @NotNull @Min(0) @Max(1_000_000) Integer`, `subscribable boolean`
- 쿼리 파라미터 검증(`@Min`, `@Max`)은 컨트롤러 메서드 파라미터에 붙이기만 하고, **컨트롤러 클래스에는 `@Validated`를 붙이지 않는다.** 클래스에 `@Validated`가 있으면 AOP 프록시 검증이 먼저 동작해 `jakarta.validation.ConstraintViolationException`이 던져지고(`GlobalExceptionHandler`에 핸들러가 없어 500), Spring MVC 내장 검증이 던지는 `HandlerMethodValidationException`이 나오지 않는다(1-7 구현 중 `size=0` 테스트에서 확인). 내장 검증의 위반 예외는 `HandlerMethodValidationException`이므로 `GlobalExceptionHandler`에 **400 `INVALID_REQUEST`** 핸들러를 추가한다(`@Validated`를 뺀 컨트롤러에서 Spring 7이 이 예외를 던지는 것을 1-7 구현에서 확인했다). `details`에는 다른 검증 핸들러와 같게 **파라미터명 → 검증 메시지**를 담는다(예: `{"size": "1 이상이어야 합니다"}`). 파라미터 이름·메시지는 `e.getValueResults()`의 `getMethodParameter().getParameterName()`과 `getResolvableErrors()`로 꺼낸다(확인함).
- 응답 record는 Result 필드를 그대로 옮긴다(`ProductSummaryResponse` 등).
- `SecurityConfig`: `.requestMatchers(HttpMethod.GET, "/api/products", "/api/products/*").permitAll()`
- `product/package-info.java`: `@ApplicationModule(allowedDependencies = {"common", "shop::api"})`

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `common/model/MoneyTest` | [ ] 음수 생성 불가 / [ ] `minus` 결과 음수 → 예외 / [ ] `times` 오버플로 → `ArithmeticException` / [ ] `plus`·`times` 정상값 |
| `common/web/CursorTest` | [ ] encode → decode 왕복 시 같은 값 / [ ] 실패 지점별 decode → `INVALID_REQUEST` (Base64가 아닌 문자열 / 구분자 없음 / 구분자 2개 이상 / 시각 형식 오류 / UUID 형식 오류) |
| `product/web/ProductControllerTest` | 아래 (공개 조회) |
| `product/web/SellerProductControllerTest` | 아래 (등록·판매자 목록) |

`SellerProductControllerTest`
- [ ] 등록 → 201, `product` 1건, `stock`(available = received = 초기 재고) 1건, `stock_movement`(RECEIVE) 1건
- [ ] 남의 가게에 등록 → 403, CLOSED 가게 → 422 `SHOP_NOT_ACTIVE`, 없는 가게 → 404 `SHOP_NOT_FOUND`
- [ ] 가격 0 → 400
- [ ] 판매자 목록: 남의 가게 → 403, CLOSED 가게 소유자는 조회 가능
- [ ] 판매자 목록 `size=0`, `size=51` → 400 `INVALID_REQUEST`, `details`에 `size` 키가 있다 (공개 목록과 같은 규칙. 컨트롤러 클래스에 `@Validated`가 남아 있으면 500이 된다)

`ProductControllerTest`
- [ ] `size=0`, `size=51` → 400 `INVALID_REQUEST`, `details`에 `size` 키가 있다 (쿼리 파라미터 검증 예외가 500이 아니라 400으로 매핑되는지 확인한다. 위 6)의 `HandlerMethodValidationException` 핸들러가 이 케이스를 처리한다)
- [ ] 커서 페이징: 상품 5개, size 2로 첫 페이지 → 새 상품 1개 추가 → 이어서 끝까지 조회 → 처음 5개가 중복·누락 없이 정확히 한 번씩 나오고, 모아 둔 순서가 등록 순서의 역순(최신순)이다. 순서가 가끔 깨지면 `created_at` 동률이 원인이므로 JDBC로 `created_at`을 서로 다르게 덮어써서 결정적으로 만든다
- [ ] HIDDEN 상품(JDBC로 상태 변경)은 목록에 없고 상세 404
- [ ] 상세 조회 응답에 `status`(`ON_SALE`)와 `inStock`(초기 재고가 있으면 true)이 내려온다
- [ ] 카테고리 필터
- [ ] **N+1 없음**: 상품 3개일 때와 10개일 때 목록 조회의 SQL 실행 수가 같다. `application-test.yaml`에 `spring.jpa.properties.hibernate.generate_statistics: true`, 테스트에서 `entityManagerFactory.unwrap(SessionFactory.class).getStatistics()`를 `clear()` 후 요청하고 `getPrepareStatementCount()` 비교

**제안 (선택)**
- 재고 저장 실패 시 상품도 롤백되는지 증명하는 테스트(`@MockitoSpyBean StockRepository`로 `insertStock`이 예외를 던지게) — 원 프로젝트에서 상품·재고가 따로 저장되던 결함(CAT-04)의 반증

**리뷰 때 물어볼 것**
- offset 페이징 대신 keyset을 쓴 이유는? keyset의 단점은?
- 커서에 `created_at`만 넣으면 무엇이 문제인가? 왜 `id`도 넣나?
- 금액을 `BigDecimal`이나 `long` 대신 `Money`로 감싸는 이유는?
- 상품과 재고를 한 트랜잭션에 넣지 않으면 무슨 일이 생기나? (As-Is CAT-04)
- `Stock`을 `save()` 대신 native 쿼리로만 쓰는 이유는?

---

## 1-8. 상품 수정 · 상태 변경 · 재고 증감 (M)

**목표**: 상품 정보·가격 수정(가격 이력), 상태 변경, 재고 입고·조정.

**왜 지금**: 1-9 예약 전에 재고를 안전하게 바꾸는 방법(조건부 UPDATE)을 먼저 익힌다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 상태 전이 규칙 | 허용된 이전 상태에서만 바뀌게 enum에 전이표를 둔다 (공통 규칙 C) |
| 낙관적 락 vs 조건부 UPDATE | 상품 정보 수정은 `@Version`(충돌 시 409), 재고 수량은 **조건부 UPDATE 한 문장**(충돌 자체가 없음) |

> 가격 변경 이벤트(구독자 알림 등)는 Part 3(3-3)에서 붙인다.

**정책 (이 단계에서 정함)**
- 수정·상태 변경·재고 조정은 ACTIVE 가게의 소유자만 할 수 있다.
- DISCONTINUED 상품은 정보 수정 불가(409 `PRODUCT_DISCONTINUED`). 재고 조정은 허용한다(재고 정리용).
- 재고 조정 delta는 0이 아니어야 하고 -1,000,000 ~ 1,000,000.

### 할 일

**1) 마이그레이션** — `db/migration/product/V{...}__product_create_price_history.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_price_history` |
| product_id | uuid | X | `fk_price_history_product` |
| old_price | bigint | X | |
| new_price | bigint | X | |
| changed_at | timestamptz | X | |
| created_at | timestamptz | X | |

- 인덱스 `idx_price_history_product_id_changed_at (product_id, changed_at DESC)`

**2) product.domain**

| 대상 | 규격 |
|---|---|
| `ProductErrorCode` | `PRODUCT_DISCONTINUED`, `OUT_OF_STOCK` 추가 |
| `PriceChange` | `record (Money oldPrice, Money newPrice)` |
| `Product.update(String name, String description, ProductCategory category, Money price, Boolean subscribable)` | 1) **맨 앞에서** DISCONTINUED면 `BusinessException(PRODUCT_DISCONTINUED)` — 던지기 전에 어떤 필드도 바꾸지 않는다. 2) 인자가 null인 필드는 기존 값 유지. 3) 가격은 `price != null`이고 현재 가격과 값이 다를 때만 `new PriceChange(현재 가격, 새 가격)`을 만들고 반영한다. 4) 반환 `Optional<PriceChange>` — 가격이 실제로 바뀐 경우에만 값이 있다(같은 가격·가격 null·이름만 변경은 `Optional.empty()`). 가격 이력 저장은 엔티티가 아니라 `UpdateProductService`가 한다 |
| `Product.changeStatus(ProductStatus to)` | `status = status.transitTo(to)` |
| `ProductRepository` (**순수 인터페이스**, Spring Data 어노테이션 없음) | `Optional<Product> findByIdAndShopId(UUID id, UUID shopId)`(1-7에서 이미 선언했다면 건너뛴다. 아래 공통 앞단과 1-10 이미지 등록·삭제가 쓴다), `int insertPriceHistory(UUID id, UUID productId, long oldPrice, long newPrice, Instant now)` — `changed_at`, `created_at` 모두 `now` |
| `StockRepository` (**순수 인터페이스**) | `int adjust(UUID productId, int delta, Instant now)`, `List<UUID> findProductIdsWithBrokenBalance()` 추가 |

**구현 위치 (infrastructure)** — 1-7의 `insertStock`·`insertMovement`와 같은 방식이다. 시그니처는 `domain` 인터페이스에, `@Modifying @Query(nativeQuery = true, ...)`는 `JpaProductRepository`·`JpaStockRepository`에만 둔다. `PriceHistory` 엔티티는 만들지 않는다(쓰기 전용이고 조회 API가 없다).

| 구현체 | 메서드 | 비고 |
|---|---|---|
| `JpaProductRepository` | `insertPriceHistory` | 아래 INSERT. 가격이 바뀔 때마다 한 건씩 쌓으므로 `ON CONFLICT`는 쓰지 않는다. 반환 1 |
| `JpaStockRepository` | `adjust` | 아래 UPDATE. 반환 0 = 재고 부족 |
| `JpaStockRepository` | `findProductIdsWithBrokenBalance` | 아래 SELECT(`nativeQuery = true`, `@Modifying` 없음) |

```sql
-- insertPriceHistory(UUID id, UUID productId, long oldPrice, long newPrice, Instant now): 반환 1
INSERT INTO product.price_history (id, product_id, old_price, new_price, changed_at, created_at)
VALUES (:id, :productId, :oldPrice, :newPrice, :now, :now)

-- adjust(UUID productId, int delta, Instant now): 반환 행 수 0이면 재고 부족
UPDATE product.stock
   SET available = available + :delta, received = received + :delta, updated_at = :now
 WHERE product_id = :productId AND available + :delta >= 0

-- findProductIdsWithBrokenBalance(): List<UUID>, CHECK가 있으니 평소엔 0건
SELECT product_id FROM product.stock WHERE available + reserved + sold <> received
```

- `adjust`는 native UPDATE라 영속성 컨텍스트를 거치지 않는다. 같은 트랜잭션에서 `Stock`을 UPDATE 전에 읽어 두면 1차 캐시에 이전 값이 남는다. 그래서 서비스는 UPDATE 전에 `Stock`을 읽지 않고, UPDATE 뒤의 `findById`가 첫 조회가 되게 한다(아래 `adjust` 4단계).

**3) product.application**

| 클래스 | 메서드 |
|---|---|
| `UpdateProductService` | `@Transactional ProductDetailResult update(UUID memberId, UUID shopId, UUID productId, UpdateProductCommand command)` / `@Transactional ProductDetailResult changeStatus(UUID memberId, UUID shopId, UUID productId, ProductStatus to)` |
| `AdjustStockService` | `@Transactional StockResult adjust(UUID memberId, UUID shopId, UUID productId, int delta, String reason)` |
| `UpdateProductCommand` | `record (String name, String description, ProductCategory category, Long price, Boolean subscribable)` |
| `StockResult` | `record (UUID productId, int available, int reserved, int sold, int received)` |

공통 앞단: `shopApi.verifyOwnerOfActiveShop(shopId, memberId)` → `productRepository.findByIdAndShopId(productId, shopId)` 없으면 `PRODUCT_NOT_FOUND`

`update`: `product.update(...)`의 반환값이 있으면 `insertPriceHistory(Ids.newId(), productId, old, new, now)`

`adjust`
1. 앞단 검사
2. `stockRepository.adjust(productId, delta, now)` → 0이면 `BusinessException(OUT_OF_STOCK)` (수량은 바뀌지 않았다)
3. `insertMovement(Ids.newId(), productId, StockMovementType.ADJUST.name(), delta, StockRefType.ADJUSTMENT.name(), Ids.newId(), reason, now)`
4. `stockRepository.findById(productId)`로 읽어 `StockResult` 반환 — 이 메서드에서 `Stock`을 읽는 건 이 조회가 처음이어야 한다. `adjust`가 native UPDATE라 영속성 컨텍스트를 갱신하지 않으므로, UPDATE 전에 `Stock`을 조회하면 이 조회가 캐시의 이전 값을 돌려준다

**4) product.web** — `SellerProductController` (`/api/shops/{shopId}/products`, 1-7에서 만든 클래스에 아래 API를 추가한다)

| API | 요청 | 응답 |
|---|---|---|
| `PATCH /api/shops/{shopId}/products/{id}` | `UpdateProductRequest` (1-7 등록 요청과 같은 검증, `@NotNull`·`@NotBlank` 없음, initialStock 없음. `subscribable`은 `Boolean`(원시 `boolean`이면 생략 시 `false`로 덮어쓴다). **`name`은 `@Pattern(regexp = ".*\\S.*")`로 빈 문자열·공백만 있는 값을 400** — 수정 요청 공통 규칙, 문서 하단 "수정 사항" 참고) | 200 `ProductDetailResponse` |
| `PATCH /api/shops/{shopId}/products/{id}/status` | `ChangeProductStatusRequest(@NotNull ProductStatus status)` | 200 `ProductDetailResponse` |
| `POST /api/shops/{shopId}/products/{id}/stock-adjustments` | `AdjustStockRequest(@NotNull @Min(-1_000_000) @Max(1_000_000) Integer delta, @NotBlank @Size(max=200) String reason)` | 200 `StockResponse` |

- delta = 0 검사: `AdjustStockRequest`에 `@AssertTrue(message = "변경 수량은 0이 아니어야 합니다.") boolean isDeltaNonZero()` → 400의 `details.deltaNonZero`

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `product/domain/ProductStatusTest` | [ ] 9개 조합 파라미터화 |
| `product/domain/ProductTest` | [ ] 가격 변경 → `PriceChange` 반환 / [ ] 같은 가격·이름만 변경 → `Optional.empty()` / [ ] DISCONTINUED에서 update → `PRODUCT_DISCONTINUED` |
| `product/web/SellerProductControllerTest` (1-7에서 만든 클래스에 추가) | [ ] 가격 변경 → `price_history` 1건(old·new 확인) / [ ] 이름만 변경 → 이력 없음 / [ ] DISCONTINUED 상품 수정 → 409 / [ ] DISCONTINUED → ON_SALE → 409 `INVALID_STATE_TRANSITION` / [ ] 재고 -5(가용 3) → 422 `OUT_OF_STOCK`, 수량 변화 없음, 이력 없음 / [ ] delta 0 → 400 / [ ] 다른 회원의 가게 ID로 접근 → 403 `FORBIDDEN` / [ ] 내 가게 ID + 다른 가게의 상품 ID → 404 `PRODUCT_NOT_FOUND` |
| `product/application/StockAdjustmentConcurrencyTest` | [ ] **동시에 +1 입고 100건**(서비스 직접 호출, 스레드 풀 + latch) → received·available이 정확히 +100, ADJUST 이력 100건, `findProductIdsWithBrokenBalance()` 빈 목록 |

**리뷰 때 물어볼 것**
- 재고를 "현재 수량 = X"로 덮어쓰면 왜 위험한가? (As-Is CAT-06)
- `@Version`과 조건부 UPDATE는 각각 언제 쓰나?
- 조건부 UPDATE에서 반영 행 수 0을 무시하면 무슨 일이 생기나?

---

## 1-9. 재고 예약과 동시성 테스트 (M)

**목표**
- 체크아웃이 쓸 재고 예약 API(`ProductApi`)를 만든다: 조회 · 예약 · 확정 · 해제(만료 포함) · 복구.
- **재고 100개 상품에 1,000명이 동시에 예약하면 정확히 100명만 성공**하는 것을 테스트로 증명한다.

**왜 지금**: 2-2 체크아웃 직전 준비이고, 이 프로젝트의 첫 번째 동시성 문제다. 이 테스트가 이력서의 첫 번째 증거가 된다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 재고 예약 | 결제 전에 재고를 임시로 잡아둔다. `available → reserved` 이동 |
| 멱등 연산 | 같은 orderId로 다시 호출해도 결과가 같다. 나중에 재시도가 안전해진다 |
| CAS 전이 | `UPDATE ... SET status = :to WHERE id = :id AND status = :from` — 반영 행 수 1이면 내가 전이, 0이면 이미 처리됨 |
| 데드락 회피 | 여러 상품을 잠글 때 항상 **같은 순서(상품 ID 오름차순)**로 |
| 동시성 테스트 | 스레드 N개를 `CountDownLatch`로 동시에 출발시키고 결과와 불변식을 검사 |

### 할 일

**1) 마이그레이션** — `db/migration/product/V{...}__product_create_stock_reservation.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_stock_reservation` |
| order_id | uuid | X | (다른 모듈, FK 없음) |
| product_id | uuid | X | `fk_stock_reservation_product` |
| quantity | int | X | `ck_stock_reservation_quantity`: `> 0` |
| status | varchar(20) | X | `ck_stock_reservation_status`: `IN ('HELD','COMMITTED','RELEASED','EXPIRED')` |
| expires_at | timestamptz | X | |
| created_at, updated_at | timestamptz | X | |

- unique `uk_stock_reservation_order_product (order_id, product_id)`, 인덱스 `idx_stock_reservation_status_expires_at (status, expires_at)` (2-5 만료 잡용)

**2) product.api — 다른 모듈용 계약** (`product/api/package-info.java`에 `@NamedInterface("api")`)
```java
public interface ProductApi {
    List<ProductForCheckout> getForCheckout(Collection<UUID> productIds);     // 조회만. 없는 ID는 결과에서 빠짐 (장바구니용)
    List<ProductForCheckout> getPurchasable(Collection<UUID> productIds);     // 하나라도 없거나 ON_SALE이 아니면 PRODUCT_NOT_ON_SALE(details.productIds) (체크아웃용)
    void reserve(UUID orderId, List<ReserveItem> items, Instant expiresAt);   // 멱등: 같은 orderId 재호출 시 무시
    void commitReservation(UUID orderId);                                     // 멱등
    void releaseReservation(UUID orderId, ReleaseReason reason);              // 멱등, COMMITTED는 건드리지 않음
    void restore(UUID orderId, UUID productId, int quantity, UUID refundId);  // 멱등: refundId
}
public record ProductForCheckout(UUID productId, UUID shopId, String name, String thumbnailKey, Money price, boolean onSale, boolean subscribable, boolean inStock) {}
public record ReserveItem(UUID productId, int quantity) {}
public enum ReleaseReason { PAYMENT_FAILED, EXPIRED }   // PAYMENT_FAILED → RELEASED, EXPIRED → EXPIRED
```

**3) product.domain**

| 대상 | 규격 |
|---|---|
| `ReservationStatus` | `HELD, COMMITTED, RELEASED, EXPIRED` — **값만 둔다. `transitTo`·전이표는 만들지 않는다**(공통 규칙 C의 예외). 이 상태는 엔티티가 아니라 native CAS(`transit`의 `WHERE status = :from`)로만 바뀌어 enum 전이표를 호출할 곳이 없기 때문이다. 허용 전이(`HELD → {COMMITTED, RELEASED, EXPIRED}`, 나머지는 종결)는 [상태머신 문서](../02-design/04-state-machines.md)와 CAS 조건이 지킨다 |
| `StockReservation` | `@Entity @Table(name = "stock_reservation", schema = "product")`, `extends BaseTimeEntity`. **읽기 전용**(쓰기는 native). 필드는 컬럼과 1:1 |
| `ProductErrorCode` | `PRODUCT_NOT_ON_SALE` 추가 |
| `ProductCheckoutRow` | `record (UUID id, UUID shopId, String name, String thumbnailKey, Money price, ProductStatus status, boolean subscribable, int available)` — 체크아웃 조회 전용 projection. 1-7의 `ProductListRow`에는 `subscribable`이 없어 재사용하지 못한다. `product.api`의 `ProductForCheckout`을 `domain`이 참조하면 안 되므로 `domain`에 따로 둔다 |
| `ProductRepository` | `List<ProductCheckoutRow> findCheckoutRowsByIds(Collection<UUID> ids)` 추가 |
| `StockReservationRepository` | `boolean existsByOrderId(UUID orderId)`, `List<StockReservation> findAllByOrderIdAndStatusOrderByProductIdAsc(UUID orderId, ReservationStatus status)`, `Optional<StockReservation> findByOrderIdAndProductId(UUID orderId, UUID productId)` + native 아래 |

**구현 위치 (infrastructure)** — 1-8과 같은 방식이다. 아래 표의 "위치"는 **시그니처를 선언하는 `domain` 인터페이스**다. `@Modifying @Query(nativeQuery = true, ...)`는 `JpaStockRepository`와 새로 만드는 `JpaStockReservationRepository`(`public interface JpaStockReservationRepository extends JpaRepository<StockReservation, UUID>, StockReservationRepository`)에만 둔다. `domain`은 Spring Data 어노테이션에 의존하지 않는다.
- 파생 쿼리 메서드는 이름이 규칙을 따라야 한다: 존재 확인은 `existsBy…`(`existBy…`로 쓰면 기동 시 쿼리 생성에 실패한다).

native 쿼리 (반환 `int`, 모두 `updated_at = :now` 포함)

| 메서드 | SQL 핵심 | 선언 위치(domain) |
|---|---|---|
| `insertReservation(id, orderId, productId, quantity, expiresAt, now)` | status `'HELD'`로 INSERT | `StockReservationRepository` |
| `transit(UUID id, String from, String to, Instant now)` | `UPDATE ... SET status = :to WHERE id = :id AND status = :from` | `StockReservationRepository` |
| `reserve(productId, q, now)` | `SET available = available - :q, reserved = reserved + :q WHERE product_id = :productId AND available >= :q` | `StockRepository` |
| `commit(productId, q, now)` | `SET reserved = reserved - :q, sold = sold + :q WHERE ... AND reserved >= :q` | `StockRepository` |
| `release(productId, q, now)` | `SET reserved = reserved - :q, available = available + :q WHERE ... AND reserved >= :q` | `StockRepository` |
| `restore(productId, q, now)` | `SET sold = sold - :q, available = available + :q WHERE ... AND sold >= :q` | `StockRepository` |

- 상태 문자열은 `ReservationStatus.X.name()`으로 넘긴다.

JPQL 쿼리 (`JpaProductRepository`, 1-7의 `findPublicFirstPage`와 같은 방식)

```java
@Query("""
        select new com.myroutine.product.domain.ProductCheckoutRow(p.id, p.shopId, p.name, p.thumbnailKey, p.price, p.status, p.subscribable, s.available)
        from Product p
        join Stock s on s.productId = p.id
        where p.id in :ids
        """)
List<ProductCheckoutRow> findCheckoutRowsByIds(Collection<UUID> ids);
```
- 상품 조회와 재고 조회를 따로 하지 않고 한 번에 가져온다(N+1 방지). `inner join`이므로 DB에 없는 ID는 결과 행이 생기지 않는다 = "없는 ID는 결과에서 빠진다"의 구현이다. 빈 컬렉션이 들어오면 `in ()`이 되어 DB마다 동작이 다르니, 호출 쪽에서 빈 입력은 쿼리 전에 빈 목록을 반환한다.

**4) product.application — `ProductApiImpl`** (package-private, `@Service`, 의존성 `ProductRepository`, `StockRepository`, `StockReservationRepository`, `Clock`)

`getForCheckout` (`@Transactional(readOnly = true)`): `productRepository.findCheckoutRowsByIds(ids)`(위 JPQL 쿼리, 상품+재고 한 번에) → `ProductForCheckout`로 변환: `onSale = (status == ON_SALE)`, `inStock = available > 0`. 재고 수량 자체는 모듈 밖으로 내보내지 않는다. 없는 ID는 결과에서 빠진다(예외 없음). `ids`가 비어 있으면 쿼리 없이 빈 목록

`getPurchasable` (`@Transactional(readOnly = true)`): `getForCheckout` 결과에서 빠진 ID와 `onSale == false`인 ID를 모아, 하나라도 있으면 `BusinessException(PRODUCT_NOT_ON_SALE, Map.of("productIds", 그 목록))`

`reserve` (`@Transactional` — 호출자 트랜잭션에 합류)
1. 입력 검증: items가 비었거나, quantity ≤ 0이거나, 같은 productId가 두 번 나오면 `IllegalArgumentException` (내부 API 계약 위반 = 버그)
2. `existsByOrderId(orderId)`면 그대로 `return` (멱등)
3. `findCheckoutRowsByIds`로 상품을 모두 조회(`getPurchasable`과 같은 쿼리). 없거나 ON_SALE이 아닌 상품이 있으면 `BusinessException(PRODUCT_NOT_ON_SALE, Map.of("productIds", 그 ID 목록))`
4. items를 **productId 오름차순**(`Comparator.comparing(ReserveItem::productId)`)으로 정렬
5. 상품마다 `stockRepository.reserve(...)` → 0이면 `BusinessException(OUT_OF_STOCK, Map.of("productIds", List.of(productId)))` — 예외가 트랜잭션을 롤백하므로 앞에서 줄인 재고도 되돌아간다
6. 상품마다 `insertReservation(Ids.newId(), ...)`, `insertMovement(..., StockMovementType.RESERVE.name(), q, StockRefType.ORDER.name(), orderId, null, now)`
- 같은 orderId로 **동시에** 두 번 호출되면 둘 다 2번을 통과할 수 있다. 늦은 쪽은 `uk_stock_reservation_order_product` 위반으로 실패하고 전체가 롤백된다(재고는 한 번만 줄어든다).
  - `insertReservation`이 `ON CONFLICT DO NOTHING` **없는 일반 INSERT**인 이유: 이 경우는 `existsByOrderId`를 뚫고 들어온 "있어서는 안 되는 경쟁"이라 예외로 전체를 롤백시켜야 한다. 반대로 `restore`의 `insertMovement`는 재시도가 정상 흐름이라 예외 없이 반환값(0/1)으로 중복을 알려 주는 `ON CONFLICT DO NOTHING`을 쓴다. **중복이 정상 흐름이면 `ON CONFLICT DO NOTHING`+반환값, 있어서는 안 되는 위반이면 일반 INSERT+예외**로 가른다(공통 규칙 D).

`commitReservation` (`@Transactional`)
1. `findAllByOrderIdAndStatusOrderByProductIdAsc(orderId, HELD)` — 상품 ID 오름차순으로 읽는 이유: 아래 반복문이 `stock` 행 락을 잡는 순서가 `reserve`와 같아져 교차 주문 간 데드락을 막는다
2. 각 예약마다 `transit(id, HELD, COMMITTED, now)`를 호출하고 **그 반환값**으로 갈린다.
   - **`transit`이 1**: 내가 전이했다. 이어서 ① `stockRepository.commit(productId, q, now)` — **이 반환값이 0이면 `IllegalStateException`**(예약은 있는데 `reserved`가 모자란 불변식 위반이므로 전체 롤백), ② `insertMovement(..., StockMovementType.COMMIT.name(), q, StockRefType.ORDER.name(), orderId, null, now)`
   - **`transit`이 0**: 다른 호출이 먼저 전이했다(동시 중복 호출). 이 예약은 **아무것도 하지 않고 다음 예약으로 넘어간다**(예외 아님). 멱등 계약이다.
   - 같은 `orderId`를 순차로 두 번 호출하면 두 번째는 1단계에서 HELD 목록이 비어 아무 일도 하지 않는다.

`releaseReservation` (`@Transactional`): commit과 같은 구조. 전이 대상은 `reason`에 따라 RELEASED 또는 EXPIRED, 재고는 `release`, 이력 `insertMovement(..., StockMovementType.RELEASE.name(), q, StockRefType.ORDER.name(), orderId, null, now)`. HELD만 대상이므로 COMMITTED 예약은 건드리지 않는다.

`restore` (`@Transactional`)
1. `findByOrderIdAndProductId` → 없거나 COMMITTED가 아니면 `BusinessException(INVALID_STATE_TRANSITION)`
2. `quantity`가 0 이하이거나 예약 수량보다 크면 `IllegalArgumentException`
3. `insertMovement(..., StockMovementType.RESTORE.name(), quantity, StockRefType.REFUND.name(), refundId, ...)` → **0이면 `return`**(같은 refundId로 이미 복구함)
4. `stockRepository.restore(...)` → 0이면 `IllegalStateException`
- 이력 INSERT를 먼저 하는 이유: unique 제약이 "이 환불로 복구했는가"의 기록이자 잠금 역할을 한다.
  - **재고 행 락으로는 중복을 못 막는다.** `UPDATE`의 행 락은 트랜잭션이 끝날 때까지 유지되지만 같은 재고 행을 쓰는 `UPDATE`들을 **줄 세울 뿐**, 같은 `refundId`의 중복인지는 모른다. 순서가 UPDATE → INSERT면 동시에 온 같은 `refundId`의 두 번째 호출이 락 대기 후 갱신된 `sold`로 `WHERE sold >= :q`를 다시 통과해 재고를 한 번 더 바꾼다. 중복 여부를 아는 것은 `refundId`가 들어 있는 이력 unique뿐이다.
  - **`ON CONFLICT DO NOTHING`은 충돌해도 예외를 던지지 않는다**(반환 0). 그래서 충돌했다고 트랜잭션이 롤백되지 않는다. UPDATE를 먼저 했다면 두 번째 호출은 이미 바꾼 재고를 그대로 두고 `return`해 커밋되므로 재고가 두 번 복구된다. 이력 INSERT를 먼저 하면 0을 받은 시점에 아직 재고를 건드리지 않았으므로 `return`해도 되돌릴 것이 없다.
  - 반대로 첫 호출이 이후 단계(재고 `restore`가 0이라 예외)에서 롤백되면 이력 INSERT도 함께 롤백되어 기록이 남지 않으므로, 같은 `refundId`의 재시도가 정상 처리된다.
- 같은 주문·상품에 서로 다른 `refundId`가 여러 번 들어올 때 **복구 수량의 누적 합이 예약 수량을 넘는지는 `restore`가 검사하지 않는다**(각 호출이 "예약 수량 이하"만 본다). 이 방어는 호출자(주문 모듈)의 책임이다: 환불은 품목 단위이고 품목은 `CANCELLED`·`RETURNED`가 종결 상태라 같은 품목을 두 번 환불할 수 없으며, `order_line.refunded_amount <= line_amount` CHECK가 한 번 더 막는다(2-2·2-8).

**5) 테스트에서 호출**: 아직 order 모듈이 없으므로 테스트에서 `ProductApi`를 주입받아 직접 호출한다(`ProductApiImpl`은 package-private이라 인터페이스로 주입). orderId·refundId는 `Ids.newId()`로 만든다.

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `product/application/ProductApiImplTest` | [ ] `getForCheckout`: 없는 ID는 빠지고 재고 0이면 `inStock = false` / [ ] `getPurchasable`: HIDDEN 상품 포함 시 `PRODUCT_NOT_ON_SALE` / [ ] reserve → available·reserved 변화, 예약 HELD, RESERVE 이력 / [ ] 같은 orderId reserve 두 번 → 한 번만 반영 / [ ] 상품 3개 중 3번째 재고 부족 → `OUT_OF_STOCK`, **1·2번째 재고 그대로**, 예약 0건 / [ ] DISCONTINUED·HIDDEN 상품 → `PRODUCT_NOT_ON_SALE`, `details.productIds` / [ ] commit 두 번 → 한 번만(sold 확인) / [ ] COMMITTED 예약을 release → 아무 변화 없음 / [ ] **HELD 예약을 release → `reserved`가 `available`로 복구(INV-04)**, 예약 상태 `PAYMENT_FAILED` → RELEASED / `EXPIRED` → EXPIRED, `RELEASE` 이력 1건, 두 번 호출해도 한 번만 / [ ] 같은 refundId로 restore 두 번 → 한 번만 / [ ] restore 사전 조건 실패: COMMITTED가 아닌(HELD) 예약 → `INVALID_STATE_TRANSITION`, 예약 수량보다 큰 수량 → `IllegalArgumentException`, 둘 다 재고 변화 없음 / [ ] 재고를 바꾸거나 바꾸지 않아야 하는 케이스(reserve·commit·release·restore와 그 실패·중복 케이스) 끝에 `findProductIdsWithBrokenBalance()` 빈 목록. 읽기 전용인 `getForCheckout`·`getPurchasable`에는 넣지 않는다. 실패 케이스는 합 불변식만으로 롤백을 증명하지 못하므로 수량을 직접 단언한다 |
| `product/application/StockReservationConcurrencyTest` | [ ] **재고 100에 서로 다른 orderId 1,000건 동시 예약(각 1개) → 성공 100, available 0, reserved 100, `available + reserved + sold = received`**. 스레드 풀 64 + `CountDownLatch` 3개(ready·start·done), `done.await(60, SECONDS)` |
| `product/application/ReservationDeadlockTest` | [ ] 상품 A·B(재고 충분), 주문 X는 [A, B], 주문 Y는 [B, A] 순서로 동시에 예약 → 둘 다 성공, 데드락 예외(`CannotAcquireLockException`, `PessimisticLockingFailureException`) 없음. `@RepeatedTest(50)` |

**리뷰 때 물어볼 것**
- 비관적 락(`SELECT FOR UPDATE`) 대신 조건부 UPDATE를 쓴 이유는?
- 상품 3개 중 3번째에서 재고가 부족하면, 이미 줄인 1·2번째 재고는 어떻게 되나?
- 데드락은 왜 생기고, 정렬하면 왜 안 생기나? 자바의 UUID 정렬 순서와 DB의 uuid 정렬 순서가 달라도 괜찮은가?
- `restore`에서 이력 INSERT를 재고 UPDATE보다 먼저 하는 이유는?
- Redis로 재고를 관리하면 무엇이 좋고 무엇이 어려운가? (Stage 2 실험 예고)

---

## 1-10. 상품 이미지 업로드 (presigned URL + MinIO) (M)

**목표**
- 판매자가 상품 이미지를 올린다. 파일은 **앱 서버를 거치지 않고** 클라이언트가 저장소(MinIO)에 직접 올린다.
- 상품 목록·상세 응답에 이미지 URL이 나온다.

**왜 지금**: 상품 기능(1-7·1-8)이 끝났고, 주문(Part 2)의 품목 스냅샷에 썸네일이 들어간다.

**흐름**: 사진은 서버가 받지 않고 판매자가 보관소(MinIO)에 직접 올린다. 서버는 임시 업로드 허가증(presigned URL)을 발급하고, 올린 뒤 실제로 올라왔는지 확인해 DB에 등록한다.

```
① 판매자 → 서버     "올릴게요"(형식·크기)        서버: 조건 검사 후 허가증 발급      (5의 issueUploadUrl)
② 판매자 → 보관소   허가증 주소로 파일을 직접 PUT  서버는 관여하지 않는다
③ 판매자 → 서버     "올렸어요"(objectKey)        서버: 보관소에 있는지 확인 → DB 등록 (5의 register)
삭제: 서버가 DB에서 제거 → 보관소 파일도 삭제                                      (5의 delete)
```

**새로 등장**

| 개념·도구 | 한 줄 설명 |
|---|---|
| 객체 저장소 (S3 호환) | 파일을 "버킷 + 키"로 저장하는 저장소. 로컬은 **MinIO**(docker), 운영이라면 AWS S3로 바꿔도 코드가 같다 |
| presigned URL | 서버가 서명해 준 **유효시간 있는 업로드 주소**. 클라이언트는 이 주소로 바로 PUT한다. 서명에 Content-Type·Content-Length를 넣어 다른 파일을 못 올리게 한다 |
| AWS SDK for Java v2 (S3) | `S3Client`(HEAD·DELETE·버킷 관리), `S3Presigner`(업로드 URL 서명) |
| 업로드와 등록 분리 | ① URL 발급 → ② 클라이언트가 직접 업로드 → ③ "올렸다"고 서버에 등록. 서버는 ③에서 실제로 올라왔는지 HEAD로 확인한다 |

**정책 (이 단계에서 정함)**
- 허용 형식: `image/jpeg`(확장자 jpg), `image/png`, `image/webp`. 크기 1바이트 ~ 5MB. 상품당 최대 10장.
- 객체 키: `products/{productId}/{UUIDv7}.{확장자}`. 등록 요청의 키가 이 상품의 접두어(`products/{productId}/`)로 시작하지 않으면 400.
- 업로드 URL 유효시간 10분.
- 첫 번째(정렬 순서가 가장 앞) 이미지가 썸네일(`product.thumbnail_key`). 썸네일을 지우면 다음 이미지가 썸네일이 된다.
- 이미지는 공개 읽기: 버킷 정책으로 `products/*`의 GET을 누구나 허용한다. 응답의 URL = `{publicBaseUrl}/{objectKey}`.
- DISCONTINUED 상품은 이미지를 바꿀 수 없다(409 `PRODUCT_DISCONTINUED`). ACTIVE 가게 주인만.
- 한계(일부러 남김): URL만 받고 등록하지 않은 객체는 저장소에 남는다. 정리(버킷 수명 주기 규칙 등)는 Stage 1 범위 밖이고 회고에 적는다.
- 한계(일부러 남김): 파일의 **내용**은 검증하지 않는다. 서명은 `Content-Type`과 `Content-Length`만 묶으므로, 형식·크기만 맞으면 PNG가 아닌 데이터도 올라갈 수 있다(매직 바이트 확인은 Stage 1 범위 밖이고 회고에 적는다).

### 할 일

**1) 인프라**
- **이미지**: `chainguard/minio:latest`. 공식 `minio/minio`는 2026-09-11 Docker Hub에서 삭제됐다(MinIO가 소스만 배포). Chainguard는 같은 MinIO 소스를 빌드한 이미지라 API·정책 JSON·`S3Client` 코드가 그대로 맞는다. Docker Hub `chainguard/minio`에는 `latest`·`latest-dev` 태그만 있어(2026-10-09 확인) 버전을 고정하지 못한다 — 로컬·테스트 용도라 감수한다. 운영(1-11)은 같은 이미지를 **digest로 고정**한다
- `docker-compose`: 위 이미지, `command: server /data --console-address ":9001"`, 환경변수 `MINIO_ROOT_USER=${MINIO_ROOT_USER}`, `MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD}`, 포트 `127.0.0.1:9000:9000`(API), `127.0.0.1:9001:9001`(콘솔), named volume
- `.env.example`에 `MINIO_ROOT_USER=`, `MINIO_ROOT_PASSWORD=`
- 의존성: `implementation platform('software.amazon.awssdk:bom:2.55.13')`(2026-10-09 Maven Central 최신), `implementation 'software.amazon.awssdk:s3'`(버전은 BOM이 정한다), `testImplementation 'org.testcontainers:testcontainers-minio'`(버전 생략: Boot 4.1.1 BOM이 import하는 `testcontainers-bom` 2.0.5에 들어 있다, 2026-10-09 Maven Central의 pom으로 확인). AWS SDK는 Spring Boot BOM이 관리하지 않아 BOM을 직접 import한다
- **호환 확인 결과**(2026-10-09, `chainguard/minio`, SDK 기본 설정): 기동·`/data` 쓰기 정상, presigned PUT 서명 그대로 200 / 다른 Content-Type·다른 크기 403(`SignatureDoesNotMatch`), `putBucketPolicy`, 익명 GET(`products/*` 200, 밖 403), HEAD(없는 키 `NoSuchKeyException` 404), DELETE 정상. 서명 헤더는 `content-length`·`host`·`content-type`. SDK 기본 체크섬 때문에 서명이 깨지는 문제는 없었다. 다른 저장소로 바꿀 때 같은 항목을 다시 확인한다

**2) common.storage** (모든 모듈이 쓸 수 있는 저장소 포트)

목적: 서버가 보관소와 대화하는 부분. `StorageProperties`(접속 정보) → `StorageConfig`(통신 도구 `S3Client`·`S3Presigner` 생성) → `S3ObjectStorage`(그 도구로 실제 일함). `ObjectStorage`는 서비스가 시킬 일의 목록(계약)이라, 서비스가 S3 SDK를 몰라도 되고 저장소를 바꿔도 `S3ObjectStorage`만 고치면 된다.

| 클래스 | 규격 |
|---|---|
| `StorageProperties` | `@ConfigurationProperties("myroutine.storage") @Validated record (@NotNull URI endpoint, @NotBlank String region, @NotBlank String accessKey, @NotBlank String secretKey, @NotBlank String bucket, @NotBlank String publicBaseUrl, @NotNull Duration uploadUrlTtl, @Positive long maxUploadBytes)` |
| `StorageConfig` | `@Configuration @EnableConfigurationProperties(StorageProperties.class)`. `@Bean S3Client`: `endpointOverride(endpoint)`, `region(Region.of(region))`, `credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))`, `serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())`(MinIO는 경로 방식 주소), `overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(5)))` / `@Bean S3Presigner`: 같은 endpoint·region·credentials·path style |
| `ObjectStorage` (인터페이스) | `PresignedUpload presignPut(String key, String contentType, long contentLength)`, `boolean exists(String key)`, `void delete(String key)` |
| `PresignedUpload` | `record (String url, Map<String, String> headers, Instant expiresAt)` — `headers`는 클라이언트가 PUT 때 **그대로** 보내야 하는 서명된 헤더(Content-Type 등) |
| `S3ObjectStorage` (`@Component`) | `ObjectStorage` 구현. `S3Client`·`S3Presigner`·`StorageProperties`를 생성자로 주입받는다. 메서드별 순서는 표 아래 **S3ObjectStorage 구현 순서** |
| `BucketInitializer` (`@Component`) | 앱이 뜰 때 버킷을 준비한다. 순서는 표 아래 **BucketInitializer 구현 순서** |
| `ImageUrls` (`@Component`) | `StorageProperties`를 주입받아 저장된 키를 클라이언트가 쓸 URL로 바꾼다. `String toUrl(String objectKey)`: `objectKey == null ? null : properties.publicBaseUrl() + "/" + objectKey` |

**S3ObjectStorage 구현 순서** — 저장소와 통신만 한다. 형식·크기·키 접두어 검사 같은 정책은 `ProductImageService`가 한다. `key`는 저장소 안의 파일 경로다(예: `products/{상품ID}/{파일ID}.png`).

`presignPut(key, contentType, contentLength)` — 판매자가 올릴 수 있는 임시 업로드 허가증을 만든다(① 단계). 서명을 로컬에서 계산할 뿐 네트워크 호출은 없다.

| 순서 | 할 일 | 코드 형태 |
|---|---|---|
| 1 | 업로드 조건을 적은 요청을 만든다. **`contentType`·`contentLength`를 반드시 넣는다**(넣어야 서명에 포함되어 다른 형식·크기로는 못 올린다) | `PutObjectRequest.builder().bucket(properties.bucket()).key(key).contentType(contentType).contentLength(contentLength).build()` |
| 2 | 유효시간을 붙여 서명 요청으로 감싼다 | `PutObjectPresignRequest.builder().signatureDuration(properties.uploadUrlTtl()).putObjectRequest(요청).build()` |
| 3 | 서명한다. **반환값을 변수에 받는다** | `PresignedPutObjectRequest presigned = presigner.presignPutObject(서명요청)` |
| 4 | `presigned`에서 꺼내 `PresignedUpload`를 만들어 반환한다 | 아래 표 |

| `PresignedUpload` 칸 | 꺼내는 곳 | 꺼낸 타입 | 변환 |
|---|---|---|---|
| `url` | `presigned.url()` | `URL` | `.toString()` |
| `headers` | `presigned.signedHeaders()` | `Map<String, List<String>>` | 값이 리스트(원소 1개)이므로 각 항목의 `getValue().get(0)`만 꺼내 새 `Map<String, String>`에 담는다 |
| `expiresAt` | `presigned.expiration()` | `Instant` | 그대로. `Instant.now().plus(ttl)`로 직접 계산하지 않는다(SDK가 서명에 쓴 시각과 어긋날 수 있다) |

- 변환 예: `{"content-type": ["image/png"], "content-length": ["1234"], "host": ["localhost:9000"]}` → `{"content-type": "image/png", "content-length": "1234", "host": "localhost:9000"}`. `host`도 그대로 돌려준다.
- 클라이언트(브라우저·JDK `HttpClient`)는 `Host`·`Content-Length`를 직접 설정하지 못하는 경우가 많다. 같은 값이 자동으로 나가므로 문제없고, 테스트에서 `headers`를 요청에 넣을 때 이 둘은 건너뛴다.
- presigned URL은 가진 사람이 유효시간 동안 업로드할 수 있는 열쇠다. 로그에 남기지 않는다.

`exists(key)` — 파일이 저장소에 실제로 올라와 있는지 확인한다(③ 단계). 네트워크 호출이 있다. 반환 타입 `boolean`.

| 순서 | 할 일 | 코드 형태 |
|---|---|---|
| 1 | HEAD 요청을 보낸다(본문 없이 메타데이터만 돌아오므로 응답 값은 쓰지 않는다). 예외 없이 끝나면 있다 | `s3Client.headObject(b -> b.bucket(properties.bucket()).key(key)); return true;` |
| 2 | 파일이 없으면 `1`에서 예외가 난다. 이때 `false`를 반환한다 | `1`을 `try`로 감싸고 `catch (NoSuchKeyException e) { return false; }` (`software.amazon.awssdk.services.s3.model.NoSuchKeyException`) |
| 3 | **그 외 예외(타임아웃·연결 실패·403)는 잡지 않고 던진다.** 저장소 장애를 "없음"으로 바꾸면 안 된다 | `catch`는 `NoSuchKeyException` 하나만 둔다 |

- 버킷이 없어도 HEAD는 404라서 "업로드 안 됨"으로 보인다. `BucketInitializer`가 버킷을 항상 보장하는 이유다.

`delete(key)` — 저장소에서 파일을 지운다(이미지 삭제 때). 네트워크 호출이 있다. 반환 타입 `void`.
- 호출 한 줄: `s3Client.deleteObject(b -> b.bucket(properties.bucket()).key(key));`
- S3 규약상 없는 키를 지워도 예외 없이 성공한다(확인함 2026-10-10: `chainguard/minio`에서 없는 키를 지워도 예외가 나지 않는다). `try-catch`를 쓰지 않고 예외를 그대로 던진다. 삼킬지(`log.warn`)는 호출하는 서비스가 정한다(아래 5의 `delete`).

공통: 이 클래스에는 `@Transactional`을 붙이지 않고, 서비스에서도 `head`·`delete`를 트랜잭션 안에서 부르지 않는다.

`BucketInitializer` — 앱이 뜰 때 버킷(보관소의 최상위 폴더)이 없으면 만들고 공개 읽기 정책을 건다. 이게 없으면 첫 업로드·HEAD가 버킷 없음 404로 실패한다. `@EventListener(ApplicationReadyEvent.class)` 메서드 하나이고, 항상 실행해도 안전(멱등)하다.

| 순서 | 할 일 | 코드 형태 |
|---|---|---|
| 1 | 버킷이 있는지 보고, 없으면 만든다 | `try { s3Client.headBucket(b -> b.bucket(bucket)); } catch (NoSuchBucketException e) { s3Client.createBucket(b -> b.bucket(bucket)); }` (없는 버킷에 `headBucket`하면 `NoSuchBucketException`이 나는 것을 확인했다) |
| 2 | 공개 읽기 정책을 건다. 이미 있어도 같은 정책으로 덮어쓴다 | `s3Client.putBucketPolicy(b -> b.bucket(bucket).policy(정책JSON))` — 아래 JSON의 `{bucket}`을 `properties.bucket()`으로 치환한 문자열 |

- 저장소가 꺼져 있으면 이 단계에서 예외가 나 기동이 실패한다(확인함 2026-10-10: 저장소에 닿지 못하면 `SdkClientException`, 원인 연결 거부로 앱이 기동에 실패한다). 로컬에서는 MinIO를 먼저 띄운다.

버킷 정책 (공개 읽기, `products/` 아래만)
```json
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::{bucket}/products/*"]}]}
```

- `application.yaml`: `myroutine.storage.endpoint: ${STORAGE_ENDPOINT:http://localhost:9000}`, `region: us-east-1`, `access-key: ${MINIO_ROOT_USER}`, `secret-key: ${MINIO_ROOT_PASSWORD}`, `bucket: myroutine`, `public-base-url: ${STORAGE_PUBLIC_BASE_URL:http://localhost:9000/myroutine}`, `upload-url-ttl: 10m`, `max-upload-bytes: 5242880`
- **외부 호출 규칙**: presign은 네트워크 호출이 없지만, `head`·`delete`·버킷 관리는 네트워크 호출이다 → `@Transactional` 안에서 부르지 않는다

**3) 마이그레이션** — `db/migration/product/V{yyyyMMddHHmm}__product_add_product_image_constraints.sql` (기존 `V202610090100__product_create_stock_reservation.sql`처럼 12자리 일시. 1-7의 마이그레이션 파일은 고치지 않는다)
- `ALTER TABLE product.product_image ADD CONSTRAINT uk_product_image_object_key UNIQUE (object_key)`
- `CREATE INDEX idx_product_image_product_id ON product.product_image (product_id)` — PostgreSQL은 FK 컬럼에 인덱스를 자동으로 만들지 않는다. 상세 조회가 `product_id`로 이미지를 읽으므로(`@EntityGraph`) 없으면 상품이 늘수록 `product_image` 전체를 훑는다.

**4) product.domain** — `ProductImage`는 `Product` 애그리거트의 자식이다(1-7 공통 규칙의 예외: 같은 애그리거트 안이라 `@OneToMany`를 쓴다)

| 대상 | 규격 |
|---|---|
| `ProductImage` | `@Entity @Table(name = "product_image", schema = "product")`, `@EntityListeners(AuditingEntityListener.class)`, `UUID id`, `String objectKey`, `int sortOrder`, `@CreatedDate Instant createdAt`. `static ProductImage of(String objectKey, int sortOrder)`가 `id = Ids.newId()`를 채운다. 다른 엔티티처럼 `@NoArgsConstructor(access = PROTECTED)`·`@Getter`. **`BaseTimeEntity`는 상속하지 않는다**(테이블에 `updated_at`이 없다). `product_id` 컬럼은 필드로 두지 않고 `Product`의 `@JoinColumn`이 채운다 |
| `Product` 추가 | `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true) @JoinColumn(name = "product_id", nullable = false, updatable = false) @OrderBy("sortOrder") private List<ProductImage> images = new ArrayList<>();` |
| `Product.MAX_IMAGES` | `public static final int MAX_IMAGES = 10` — 서비스의 사전 검사와 `addImage`가 같은 값을 쓴다 |
| `Product.addImage(String objectKey)` | **`Product` 클래스 안에 만드는 메서드.** 상품에 이미지 한 장을 붙인다. 규칙(단종 불가·최대 10장·첫 이미지는 썸네일)을 엔티티가 스스로 지킨다. 순서는 표 아래 **addImage·removeImage 순서** |
| `Product.removeImage(UUID imageId)` | **`Product` 클래스 안에 만드는 메서드.** 상품에서 이미지 한 장을 뺀다. 순서는 표 아래 |
| `static String objectKeyPrefix(UUID productId)` | `"products/" + productId + "/"` |
| `ProductErrorCode` 추가 | `PRODUCT_IMAGE_LIMIT_EXCEEDED`(422), `IMAGE_NOT_UPLOADED`(422), `PRODUCT_IMAGE_NOT_FOUND`(404) |

**addImage·removeImage 순서** (둘 다 `Product` 안의 `public` 메서드)

`ProductImage addImage(String objectKey)` — 입력: 올라간 파일의 키 / 출력: 새로 만든 `ProductImage`(서비스가 응답에 이미지 id를 줄 때 쓴다)
1. `status == DISCONTINUED`면 `BusinessException(PRODUCT_DISCONTINUED)`
2. `images.size() >= MAX_IMAGES`면 `BusinessException(PRODUCT_IMAGE_LIMIT_EXCEEDED)`
3. `sortOrder`를 정한다: `images`가 비었으면 `0`, 아니면 마지막 이미지의 `sortOrder + 1`
4. `ProductImage image = ProductImage.of(objectKey, sortOrder)` → `images.add(image)`
5. 첫 이미지였으면(4 이전에 `images`가 비어 있었으면) `this.thumbnailKey = objectKey`
6. `image` 반환

`String removeImage(UUID imageId)` — 입력: 지울 이미지 id / 출력: 지운 이미지의 `objectKey`(서비스가 저장소의 파일을 지울 때 쓴다)
1. `status == DISCONTINUED`면 `BusinessException(PRODUCT_DISCONTINUED)`
2. `images`에서 `id`가 `imageId`인 것을 찾는다. 없으면 `BusinessException(PRODUCT_IMAGE_NOT_FOUND)`
3. `images.remove(찾은 것)` (`orphanRemoval = true`라서 DB 행도 같이 지워진다)
4. 남은 `images`의 첫 번째 `objectKey`를 `thumbnailKey`에 넣는다. 남은 게 없으면 `null`
5. 찾았던 이미지의 `objectKey` 반환

- 두 메서드 모두 DB를 직접 건드리지 않는다. `images` 목록만 바꾸면, 서비스의 트랜잭션이 끝날 때 JPA가 INSERT·DELETE를 알아서 한다.

- 이미지 추가·삭제는 `Product`의 컬렉션을 바꾸므로 `Product.version`이 올라간다(확인함 2026-10-10: Hibernate가 소유 컬렉션 변경 시 `Product.version`을 올린다 — 아래 동시성 테스트에서 매번 `ObjectOptimisticLockingFailureException`으로 한쪽이 실패했다). 그래서 동시에 11번째 이미지를 두 명이 등록해도 한쪽은 409 `CONFLICT_RETRY`가 되어 10장을 넘지 않는다.

**5) product.application** — `ProductImageService`: 위 조각들을 엮어 이미지 업로드의 **업무 흐름**(허가증 발급 → 등록 → 삭제)을 만든다. 상품 DB와 보관소를 함께 다루므로 아래 규칙을 지킨다.

- **클래스에 `@Transactional`을 붙이지 않는다.** 붙이면 메서드 전체가 한 트랜잭션이라 `storage.exists`·`delete`(네트워크 호출)가 DB 커넥션을 쥔 채 실행된다. 그 대신 DB를 쓰는 구간만 `TransactionTemplate`으로 감싼다. `TransactionTemplate`은 Spring Boot가 자동 등록하는 빈이라 생성자 주입으로 받는다(1-10 구현에서 그대로 주입됨을 확인했다). 반환값이 있으면 `transactionTemplate.execute(status -> { ...; return 값; })`, 없으면 `transactionTemplate.executeWithoutResult(status -> { ... })`. 읽기 구간도 같은 템플릿을 쓴다.
- **의존성**: `ShopApi`, `ProductRepository`, `ObjectStorage`, `StorageProperties`, `TransactionTemplate` (`@Service @RequiredArgsConstructor`)
- **서비스 안의 상수 2개** (`private static final`)
  - `Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp")` — 허용 형식 검사(`containsKey`)와 키 끝의 확장자(`get`)에 쓴다
  - `Pattern OBJECT_KEY_PATTERN = Pattern.compile("^products/[0-9a-f-]{36}/[0-9a-f-]{36}\\.(jpg|png|webp)$")` — `register`의 키 모양 검사(`matcher(objectKey).matches()`)에 쓴다
- 상수로 두지 않는 값: 최대 이미지 수는 `Product.MAX_IMAGES`, 최대 업로드 크기는 설정값 `properties.maxUploadBytes()`, 키 접두어는 `Product.objectKeyPrefix(productId)`
- 결과 타입(모두 application): `UploadUrlResult(String uploadUrl, Map<String,String> headers, String objectKey, Instant expiresAt)`, `ImageResult(UUID imageId, String objectKey, int sortOrder)`. **Result는 키를 들고, URL 변환은 web에서 한다**(아래 6).

`UploadUrlResult issueUploadUrl(UUID memberId, UUID shopId, UUID productId, String contentType, long contentLength)` — ① 업로드 허가증 발급

1. `shopApi.verifyOwnerOfActiveShop(shopId, memberId)`
2. 형식·크기 검사: `contentType`이 허용 Map에 없거나, `contentLength`가 1 미만 또는 `maxUploadBytes` 초과면 `BusinessException(CommonErrorCode.INVALID_REQUEST)`
3. (트랜잭션) `findByIdAndShopId`로 상품 조회(없으면 `PRODUCT_NOT_FOUND`) → DISCONTINUED면 `PRODUCT_DISCONTINUED` → `product.getImages().size() >= Product.MAX_IMAGES`면 `PRODUCT_IMAGE_LIMIT_EXCEEDED`
4. `key = Product.objectKeyPrefix(productId) + Ids.newId() + "." + 확장자`
5. `PresignedUpload presigned = storage.presignPut(key, contentType, contentLength)`
6. `new UploadUrlResult(presigned.url(), presigned.headers(), key, presigned.expiresAt())` 반환

`ImageResult register(UUID memberId, UUID shopId, UUID productId, String objectKey)` — ③ 올린 파일을 확인하고 DB에 등록

1. `shopApi.verifyOwnerOfActiveShop`
2. 키 검사: `objectKey.startsWith(Product.objectKeyPrefix(productId))`이고 정규식 `^products/[0-9a-f-]{36}/[0-9a-f-]{36}\.(jpg|png|webp)$`에 맞아야 한다. 아니면 `INVALID_REQUEST`(남의 상품 경로나 이상한 경로를 등록하지 못하게 한다)
3. **(트랜잭션 밖)** `storage.exists(objectKey)`가 `false`면 `IMAGE_NOT_UPLOADED`
4. (트랜잭션) `findByIdAndShopId`(없으면 `PRODUCT_NOT_FOUND`) → `ProductImage image = product.addImage(objectKey)` → `new ImageResult(image.getId(), image.getObjectKey(), image.getSortOrder())` 반환. `ProductImage`는 `save()`를 따로 부르지 않는다. 영속 상태의 `product.images`에 넣어 두면 트랜잭션이 끝날 때(커밋 직전 flush) JPA가 cascade로 INSERT한다. INSERT는 `addImage` 호출 시점이 아니라 커밋 시점에 나가고, UNIQUE 위반 같은 DB 오류도 그때 난다(`transactionTemplate.execute` 밖으로 전파되어 409로 변환). `id`는 `ProductImage.of`가 이미 채우므로 `image.getId()`는 바로 쓸 수 있다. 확인함(2026-10-10, 쿼리 로그): `id`를 직접 채운 엔티티를 cascade로 저장해도 INSERT 앞에 `select ... where id=?`는 나가지 않는다. 나가는 것은 `images`를 `product_id`로 읽는 select 한 번과 INSERT 한 번이다

`void delete(UUID memberId, UUID shopId, UUID productId, UUID imageId)` — 이미지 삭제

1. `shopApi.verifyOwnerOfActiveShop`
2. (트랜잭션) `findByIdAndShopId`(없으면 `PRODUCT_NOT_FOUND`) → `String key = product.removeImage(imageId)` → 트랜잭션을 끝낸다(커밋)
3. **(트랜잭션 밖)** `storage.delete(key)`. 실패하면 `catch (RuntimeException e)`로 `log.warn`만 남긴다. DB에서는 이미 빠졌으므로 사용자에게 실패로 보이면 안 되고, 남은 객체는 위 "한계"와 같다

**6) product.web** — `ProductImageController` (`/api/shops/{shopId}/products/{productId}/images`)

| API | 요청 | 응답 |
|---|---|---|
| `POST .../images/presigned-url` | `UploadUrlRequest(@NotBlank String contentType, @NotNull @Min(1) Long contentLength)` | 200 `UploadUrlResponse(uploadUrl, headers, objectKey, expiresAt)` |
| `POST .../images` | `RegisterImageRequest(@NotBlank String objectKey)` | 201 `ImageResponse(imageId, url, sortOrder)` |
| `DELETE .../images/{imageId}` | | 204 |

- 컨트롤러는 기존 `SellerProductController`처럼 `@RestController @RequiredArgsConstructor`, 회원은 `@CurrentMember UUID memberId`, 경로는 `@PathVariable UUID shopId, productId`로 받는다. 요청 DTO는 `@RequestBody @Valid`.
- **기존 응답 변경** (키 대신 URL을 내려준다):
  - `ProductSummaryResponse`·`ProductDetailResponse`의 `thumbnailKey` → **`thumbnailUrl`**. `SellerProductResponse`는 썸네일 필드가 없으므로 바꾸지 않는다.
  - URL 변환은 `ImageUrls`가 하는데 `from(result)`는 정적 메서드라 빈에 접근하지 못한다. 그래서 `from(result, imageUrls)`로 `ImageUrls`를 인자로 받고, 컨트롤러가 주입받은 `ImageUrls`를 넘긴다(호출하는 곳을 모두 함께 고친다: `ProductController`와, `ProductDetailResponse.from`을 쓰는 `SellerProductController`의 `update`·`changeStatus`. 이 컨트롤러에도 `ImageUrls`를 주입한다).
  - `ProductDetailResponse`에 `images: List<ImageResponse>` 추가. `ImageResponse(UUID imageId, String url, int sortOrder)`는 `ImageResponse.from(ImageResult, imageUrls)`로 만든다.
- **상세 조회가 이미지를 읽게 하기**: 현재 `ProductQueryService.getProduct`는 `productRepository.findById`로 상품만 읽는다. `ProductRepository`에 `Optional<Product> findDetailById(UUID id)`를 추가하고, `JpaProductRepository`에서 `@EntityGraph(attributePaths = "images")`를 붙여 구현한다(`@EntityGraph`는 순수 인터페이스 `ProductRepository`가 아니라 `JpaProductRepository`에 둔다). `getProduct`는 이 메서드를 쓰고, `ProductDetailResult`에 `List<ImageResult> images`를 추가해 `product.getImages()`를 옮긴다. 이제 `ProductRepository.findById`를 쓰는 곳이 없으므로 그 선언을 지운다(구현체 `JpaProductRepository`는 `JpaRepository`가 `findById`를 이미 제공하므로 영향 없다).
- 클라이언트 업로드 방법(README·PR에 적기): `curl -X PUT "{uploadUrl}" -H "Content-Type: image/png" --data-binary @a.png` — `headers`의 값을 그대로 넣는다.

**7) 테스트 환경** — 목적: 통합 테스트에서도 진짜 저장소(MinIO 컨테이너)로 업로드·조회를 해 본다. `application.yaml`의 `myroutine.storage.*`가 필수 값이고 `BucketInitializer`가 앱이 뜰 때 저장소에 접속하므로, **이 설정이 없으면 기존 통합 테스트도 모두 기동에 실패한다.** `IntegrationTestSupport`에 세 가지를 한다.

1. 컨테이너 필드 추가 (Postgres 옆, 같은 싱글턴 방식). 클래스는 `org.testcontainers.containers.MinIOContainer`(Testcontainers 2.0.5에서 확인)
```java
static final MinIOContainer minio = new MinIOContainer(
        DockerImageName.parse("chainguard/minio:latest").asCompatibleSubstituteFor("minio/minio"));
```
`asCompatibleSubstituteFor`가 필요한 이유: 이미지 이름이 `minio/minio`가 아니라서 이게 없으면 Testcontainers가 호환 이미지로 인정하지 않는다.
2. 기존 `static { postgres.start(); }` 블록에 `minio.start();`를 추가한다. 시작 명령(`server --console-address :9001 /data`)과 대기 전략(`/minio/health/live`), 기본 계정(`minioadmin`/`minioadmin`)은 `MinIOContainer`가 알아서 설정한다(`chainguard/minio`에서 같은 명령으로 `live` 200을 확인했다).
3. 컨테이너 주소·계정을 앱 설정에 주입한다. 컨테이너 포트는 매번 달라서 `@DynamicPropertySource`가 필요하다.
```java
@DynamicPropertySource
static void storageProperties(DynamicPropertyRegistry registry) {
    registry.add("myroutine.storage.endpoint", minio::getS3URL);
    registry.add("myroutine.storage.access-key", minio::getUserName);
    registry.add("myroutine.storage.secret-key", minio::getPassword);
    registry.add("myroutine.storage.public-base-url", () -> minio.getS3URL() + "/myroutine");
}
```
`getS3URL()`은 `http://호스트:포트` 형태다. 버킷 `myroutine`과 공개 읽기 정책은 앱 기동 시 `BucketInitializer`가 만든다. 테스트 후 `TRUNCATE`는 DB만 비우고 저장소 객체는 지우지 않는다(키가 매번 달라서 문제없다).

업로드는 테스트에서 `java.net.http.HttpClient`로 presigned URL에 실제 PUT한다. 서명된 헤더를 요청에 넣을 때 `host`·`content-length`는 건너뛴다(클라이언트가 자동으로 붙인다).

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `product/domain/ProductImageTest` | [ ] 첫 이미지 → 썸네일 / [ ] 썸네일 삭제 → 다음 이미지가 썸네일, 모두 삭제 → null / [ ] 11번째 → `PRODUCT_IMAGE_LIMIT_EXCEEDED` / [ ] DISCONTINUED → `addImage`·`removeImage` 모두 `PRODUCT_DISCONTINUED` |
| `product/web/ProductImageControllerTest` (MinIO 컨테이너) | [ ] URL 발급 → `HttpClient`로 PUT(서명된 헤더 그대로) → 200 → 등록 → 201 → 응답 `url`로 GET(인증 없이) → 200, 같은 바이트 / [ ] 상품 상세에 `images`, 목록에 `thumbnailUrl` / [ ] **서명과 다른 Content-Type으로 PUT → MinIO가 403** / [ ] 업로드 없이 등록 → 422 `IMAGE_NOT_UPLOADED` / [ ] **다른 상품의 파일 키를 내 상품에 등록 → 400** (예: 상품 B의 이미지 키를 상품 A의 등록 API에 보낸다. "이 상품 폴더의 파일만 이 상품에 등록할 수 있다"를 지키고, 다른 상품 파일을 참조하는 것을 막는다. 이미 B에 등록된 키는 `object_key` UNIQUE 제약도 막지만 그 경우는 409 `DUPLICATE_RESOURCE`이므로, 이 테스트가 400 `INVALID_REQUEST`를 요구하면 접두어 검사를 확인할 수 있다) / [ ] `image/gif` 요청 → 400, 6MB 요청 → 400 / [ ] **가게 주인이 아닌 회원의 토큰으로 요청 → 403** (예: 다른 회원이 남의 가게의 `presigned-url`을 요청. 세 API 모두 첫 단계가 소유권 확인이라 한 API로 확인하면 된다) / [ ] 삭제 → 204, 저장소에서도 사라짐(`storage.exists`가 `false`) |

- 테스트에서 객체 키는 매번 새 UUID로 만든다. 저장소 객체는 `TRUNCATE`로 지워지지 않아 고정 키를 쓰면 앞 테스트의 파일 때문에 `exists`가 오탐한다.

```bash
curl -s -X POST localhost:8080/api/shops/$SHOP/products/$PRODUCT/images/presigned-url \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"contentType":"image/png","contentLength":12345}'
# → uploadUrl로 PUT 후 objectKey 등록
```

**제안 (선택)**
- `product/application/ProductImageConcurrencyTest`: 9장인 상품에 업로드해 둔 이미지 2개를 스레드 2개가 `CountDownLatch`로 동시에 `register`하면 **DB에 정확히 10건**, 성공 1건, 실패 1건이다. 실패한 쪽은 `ObjectOptimisticLockingFailureException`(두 트랜잭션이 겹친 경우)이나 `PRODUCT_IMAGE_LIMIT_EXCEEDED`(한쪽이 먼저 커밋한 뒤 읽은 경우) 둘 다 정상이므로 핵심 단언은 "10건, 성공 1건"이다. 겹침이 매번 일어나지 않으므로 `@RepeatedTest(10)`. (Hibernate가 컬렉션 변경 시 `Product.version`을 올리는지 확인하는 의미도 있다. 확인함: 10회 모두 실패한 쪽이 `ObjectOptimisticLockingFailureException`이었다)

**리뷰 때 물어볼 것**
- 파일을 서버로 받아서 저장하지 않고 presigned URL을 쓴 이유는? 대가는(등록되지 않은 객체)?
- 서명에 Content-Type·Content-Length를 넣지 않으면 무슨 일이 생기나?
- 등록할 때 HEAD로 다시 확인하는 이유는? 클라이언트가 "올렸다"고만 하면 안 되나?
- 이미지 개수 제한(10장)은 동시 등록에서 무엇으로 지켜지나?

---

## 1-11. 운영 환경 연습: VM 배포와 자동 CD (L)

> 결정과 대안은 [ADR-011](../adr/ADR-011-ops-practice-environment.md). 여기에는 구현 규격과 완료 확인만 둔다.

**목표**
- `develop`에 머지하면 **CI 통과 후 Proxmox VM에 자동 배포**된다.
- 배포가 실패하면(앱이 안 뜨거나 헬스체크 실패) **직전 버전으로 자동 복구**된다. 원하는 버전을 수동으로 다시 배포할 수도 있다.
- LAN 안의 다른 PC에서 Part 1의 기능(가입 → 로그인 → 가게 → 상품 → 이미지 업로드)이 동작한다.

**왜 지금**: Postgres(1-1)와 MinIO(1-10)까지 있어 "최소 풀스택"이 갖춰졌다. 여기서 배포 파이프라인을 만들어 두면 Kafka(Part 3)·Redis(Part 4)·ES(Part 6)는 compose에 서비스를 추가하는 것만으로 같은 파이프라인에 올라탄다. 늦추면 환경 차이 문제(접속 주소, 시크릿 주입)를 한꺼번에 만난다.

**흐름**: 아래 할 일 S2~S7이 각각 한 칸이다.
```
develop 머지 → CI(ci.yml, GitHub 호스티드 러너) 성공
  → cd.yml 시작 (workflow_run)                                             (S7)
     ① build-image  호스티드 러너: 그 sha로 이미지 빌드 → ghcr.io/95twan/myroutine:{sha} push   (S2)
     ② deploy       VM의 self-hosted 러너: 리포 checkout → scripts/deploy.sh {sha}            (S4)
          → 운영 compose로 새 이미지 기동, 헬스체크 통과까지 대기                             (S3)(S4)
          → 스모크 성공: deployed-sha 갱신 / 실패: 직전 sha로 다시 기동, Actions 빨간불
```

**새로 등장**

| 개념·도구 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| Dockerfile (멀티 스테이지) | 빌드용 이미지에서 jar를 만들고, 실행용 작은 이미지에는 jar만 복사한다 | |
| GHCR | GitHub의 컨테이너 이미지 저장소. 이미지에 커밋 sha를 태그로 붙여 **같은 태그는 항상 같은 내용**이 되게 한다 | |
| **self-hosted runner** (새로 배우는 도구) | 내 VM에 설치하는 Actions 실행기. VM이 GitHub에 먼저 접속해 job을 받아가므로 포트를 열 필요가 없다 | ADR-011 §3 |
| GitHub Environment | 배포 job이 쓰는 환경 이름(`ops`). 배포할 수 있는 브랜치를 제한한다 | |
| 헬스체크 + 롤백 | 배포 직후 앱이 정상인지 확인하고, 아니면 직전 이미지로 되돌린다 | |

> **왜 public 리포에서 self-hosted runner가 위험한가**: 누구나 포크해서 PR을 올릴 수 있고, 그 PR의 워크플로가 **내 VM에서** 실행되면 외부 코드가 집 네트워크 안에서 돈다. 그래서 배포 job은 `pull_request`로 실행되지 않게 하고, `workflow_run`의 원인이 develop **push**인지 확인하고(S7), 브랜치를 제한한다.

**정책 (이 단계에서 정함)**
- 배포 대상: `develop` push. 트리거는 `develop` push로 돈 CI가 성공한 뒤(`workflow_run`)와 수동(`workflow_dispatch`, 입력 `sha`). `main` 릴리스(태그)는 배포와 별개다.
- 이미지: `ghcr.io/95twan/myroutine:{전체 sha}`(GHCR 이름은 소문자만). 배포는 항상 **sha 태그**로 한다(`latest`로 배포하지 않는다 — 어떤 버전이 떠 있는지 모호해지고 롤백이 안 된다).
- 프로필: 기존 `prod`를 쓴다(새 이름을 만들지 않는다). 이 VM이 `prod` 설정의 대상이다.
- 시크릿: VM의 `/opt/myroutine/.env`(사람이 한 번 만든다). 리포·GitHub Secrets·이미지에 넣지 않는다. 키 이름 목록은 `.env.ops.example`에 둔다.
- 노출: 앱(8080)과 MinIO API(9000)만 LAN에 연다. **Postgres·actuator 관리 포트(8081)·MinIO 콘솔은 호스트에 publish하지 않는다.**
- 롤백: 이미지만 되돌린다. Flyway는 되돌리지 않으므로 **파괴적 마이그레이션(컬럼 삭제·이름 변경)은 한 번의 배포에 넣지 않는다** (ADR-011 §7).
- 외부 접속은 이 단계에 없다(ADR-011 "보류").

### 할 일

**S1 → S8 순서로 한다.** 내 맥에서 확인할 수 있는 것부터라, 문제가 생겼을 때 원인이 맥·GitHub·VM 중 어디인지 좁힐 수 있다. `{VM_IP}`는 VM의 고정 IP다.

| 순서 | 만드는 것 | 확인하는 곳 |
|---|---|---|
| S1 | CORS (코드+테스트) | 맥, `./gradlew test` |
| S2 | `.dockerignore`, `Dockerfile` | 맥, `docker build` |
| S3 | `docker-compose.ops.yml`, `.env.ops.example` | 맥 리허설 |
| S4 | `deploy.sh`, `smoke.sh` | 맥 리허설 (롤백 포함) |
| S5 | VM 준비 | VM. S1~S4와 **병행해도 된다** |
| S6 | GitHub 저장소 설정 | GitHub |
| S7 | `cd.yml` → 첫 배포 | GitHub + VM |
| S8 | 완료 확인 실행 | LAN의 다른 PC |

커밋은 S1 / S2 / S3 / S4 / S7을 각각 따로 한다(관련 없는 변경 섞지 않기. `.gitignore`의 `.claude/worktrees/` 줄은 이 단계와 무관하니 별도 커밋).

#### S1. CORS

**목적**: 브라우저는 다른 주소(오리진)의 API 호출을 기본으로 막는다. 프론트가 `http://192.168.0.20:5173`, API가 `http://{VM_IP}:8080`이면 오리진이 달라서 서버가 "이 오리진은 허용"이라고 응답해야 한다. 지금까지는 같은 곳에서만 불러서 필요 없었다. `*`는 쓰지 않는다(NFR-SEC-04, 개발 가이드 §15).

1. `common.security.CorsProperties` — `@ConfigurationProperties("myroutine.web") @Validated record (List<String> corsAllowedOrigins)`. `JwtProperties`와 같은 모양이다. 환경변수의 쉼표 구분 문자열은 Boot가 `List`로 바꿔 준다. 환경변수가 비어 있으면 `null`로 들어오므로, compact constructor에서 `null`이면 `List.of()`로 바꾼다.
2. `SecurityConfig`의 `@EnableConfigurationProperties`에 `CorsProperties.class`를 추가한다(`{ JwtProperties.class, CorsProperties.class }`).
3. `SecurityConfig`에 `@Bean CorsConfigurationSource corsConfigurationSource(CorsProperties properties)`를 만든다. 타입은 `org.springframework.web.cors` 패키지다.

   | 호출 | 값 |
   |---|---|
   | `CorsConfiguration config = new CorsConfiguration()` | |
   | `config.setAllowedOrigins(properties.corsAllowedOrigins())` | 목록이 비어 있으면 어떤 오리진도 허용되지 않는다 |
   | `config.setAllowedMethods(List.of("GET","POST","PATCH","DELETE","OPTIONS"))` | |
   | `config.setAllowedHeaders(List.of("Authorization","Content-Type","Idempotency-Key"))` | |
   | `config.setExposedHeaders(List.of("X-Request-Id"))` | 브라우저 JS가 읽을 수 있는 응답 헤더 |
   | `UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource()` | |
   | `source.registerCorsConfiguration("/api/**", config)` → `return source` | `/api/**`에만 적용 |
4. `securityFilterChain` 안에 `.cors(Customizer.withDefaults())`를 추가한다(`org.springframework.security.config.Customizer`). `withDefaults()`는 같은 타입의 빈(3번)을 찾아 쓴다. **이게 없으면 3번 빈을 만들어도 적용되지 않는다.** CORS 필터는 인증 검사보다 먼저 돌기 때문에 `Authorization` 헤더 없이 오는 preflight가 401로 막히지 않는다.
5. `src/main/resources/application-prod.yaml`을 **새로 만든다**(지금은 없다). 내용은 `myroutine.web.cors-allowed-origins: ${CORS_ALLOWED_ORIGINS:}` 한 값(YAML 중첩으로). `application-local.yaml`의 값은 가져오지 않는다. `.env.local` import는 운영이 실제 환경변수를 쓰므로, `datasource.*`는 Boot가 환경변수 `SPRING_DATASOURCE_URL` 등을 `spring.datasource.*`에 자동으로 바인딩(relaxed binding)하므로 필요 없고, `format_sql`과 `org.hibernate.SQL`·`bind` 로그는 개발용이며 바인딩 값(개인정보)이 로그에 남으므로 넣지 않는다. `JWT_SECRET`·`MINIO_ROOT_*`·`STORAGE_*`는 `application.yaml`이 이미 환경변수로 읽는다. graceful shutdown은 Boot 4.1.1 기본값(`server.shutdown=graceful`, 단계별 대기 `spring.lifecycle.timeout-per-shutdown-phase=30s`, 2026-10-09 jar 메타데이터로 확인)이라 설정하지 않는다.
6. 테스트(`SecurityConfigTest`의 빈 메서드 두 개를 채운다):
   - test 프로필에는 허용 오리진이 없다. 클래스에 `@TestPropertySource(properties = "myroutine.web.cors-allowed-origins=http://localhost:5173")`를 붙여 값을 준다.
   - preflight 요청의 모양: `mockMvc.perform(options("/api/products").header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "GET"))` (`MockMvcRequestBuilders.options`).
   - 허용: `status().isOk()` + `header().string("Access-Control-Allow-Origin", "http://localhost:5173")`. 불허(`Origin: http://evil.example`): `status().isForbidden()` (거절된 preflight에 Spring이 403을 준다).

**확인**: `./gradlew test --tests '*SecurityConfigTest'` 통과 → 전체 `./gradlew test` 통과(다른 통합 테스트가 깨지지 않았는지).

#### S2. 앱 이미지

**목적**: jar를 실행에 필요한 것만 담은 작은 이미지로 포장한다. 빌드용 이미지(JDK)에서 jar를 만들고, 실행용 이미지(JRE)에는 jar만 복사한다(멀티 스테이지).

1. **`.dockerignore`** (리포 루트): 한 줄에 하나씩 `.git`, `build`, `.gradle`, `.env*`, `.idea`, `docs`. `COPY . .`가 이 목록을 뺀 나머지를 이미지 빌드에 보낸다. `.env*`가 빠지면 시크릿이 이미지에 구워진다.
2. **`docker/Dockerfile`** — 줄 단위:

   | 줄 | 왜 |
   |---|---|
   | `FROM eclipse-temurin:25-jdk AS build` | 빌드용 스테이지 시작(JDK는 크다) |
   | `WORKDIR /workspace` / `COPY . .` | 소스 전체 복사 |
   | `RUN ./gradlew bootJar --no-daemon` | `build`가 아니라 `bootJar`. 테스트는 CI가 이미 돌렸고, Testcontainers는 이미지 빌드 안에서 돌 수도 없다. `--no-daemon`은 일회용 컨테이너라 Gradle 데몬이 필요 없어서. 결과는 `build/libs/myroutine-0.0.1-SNAPSHOT.jar`(`rootProject.name` + version). 같은 폴더의 `*-plain.jar`는 실행할 수 없는 jar라 복사하지 않는다 |
   | `FROM eclipse-temurin:25-jre` | **새 스테이지**. 앞 스테이지는 `COPY --from`으로 가져온 것만 남고 버려진다 |
   | `LABEL org.opencontainers.image.source=https://github.com/95twan/myroutine-v2` | GHCR 패키지를 이 리포에 연결한다(확인 필요: GitHub 문서 "Working with the Container registry"). 이미지 이름(`myroutine`)과 리포 이름(`myroutine-v2`)이 달라서 필요하다 |
   | `RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*` | 헬스체크용 curl. `eclipse-temurin:25-jre`(2026-10-09 기준 Ubuntu 26.04 기반)에는 curl·wget이 없다(직접 확인). 한 `RUN`에 묶어야 임시 파일이 레이어에 남지 않는다 |
   | `RUN useradd --system --uid 10001 app` | 비root 실행 사용자. 이미지에 기본으로 있는 `ubuntu` 사용자는 sudo 그룹이라 쓰지 않는다 |
   | `WORKDIR /app` / `COPY --from=build /workspace/build/libs/myroutine-0.0.1-SNAPSHOT.jar app.jar` | 파일명을 정확히 써서 `-plain.jar`가 섞이지 않게 한다. 버전을 바꾸면 이 줄도 고친다 |
   | `USER app` | 이 줄 아래부터 비root |
   | `EXPOSE 8080 8081` | 문서 용도(실제 publish는 compose가 한다) |
   | `ENTRYPOINT ["java", "-jar", "/app/app.jar"]` | **배열 형태**여야 종료 신호(SIGTERM)가 java에 바로 전달돼 graceful shutdown이 동작한다. 문자열 형태는 셸을 거친다 |

   이미지에 설정값·시크릿을 굽지 않는다(전부 환경변수). `SPRING_PROFILES_ACTIVE`도 compose에서 준다.

**확인** (맥, Docker Desktop은 켜 둔다. 처음 빌드는 의존성을 받느라 몇 분 걸린다):
```bash
docker build -f docker/Dockerfile -t myroutine:local .
docker run --rm --entrypoint id myroutine:local                    # uid=10001(app) — root가 아니다
docker run --rm --entrypoint sh myroutine:local -c 'which curl; ls -a /app'   # curl 경로, /app에 app.jar만
docker history --no-trunc myroutine:local | grep -i -E 'secret|password' ; echo "grep exit=$?"   # 1이면 없음
```
> 이 맥은 arm64이고 GitHub 호스티드 러너와 VM은 보통 amd64(x86_64)다(VM에서 `uname -m`으로 확인 필요). 맥에서 만든 이미지는 **로컬 리허설용**이다. GHCR에는 올리지 않는다(CD가 amd64 러너에서 다시 빌드한다).

#### S3. 운영 compose와 설정

**목적**: 앱·Postgres·MinIO를 한 번에 켜고 끄는 주문서다. 로컬 `docker-compose.yml`과 분리한 `docker/docker-compose.ops.yml`을 만든다. 최상단에 `name: myroutine-ops`를 둔다. 로컬이 `name: myroutine`이라 이름이 같으면 **볼륨과 컨테이너가 섞여** 로컬 DB에 다른 비밀번호로 붙으려다 인증이 실패한다.

| 서비스 | 규격 |
|---|---|
| `app` | `image: ghcr.io/95twan/myroutine:${IMAGE_TAG:?IMAGE_TAG를 지정하세요}`(GHCR 이름은 소문자. `:?`는 변수가 비었을 때 즉시 에러를 내서 태그 없이 엉뚱한 이미지가 뜨는 것을 막는다), `env_file: /opt/myroutine/.env`, `environment: SPRING_PROFILES_ACTIVE: prod`, 포트 `8080:8080`만, `restart: unless-stopped`, `depends_on`: `postgres`·`minio` 모두 `condition: service_healthy`(MinIO가 늦게 뜨면 1-10의 `BucketInitializer`가 실패해 앱이 안 뜬다), `healthcheck: test: ["CMD", "curl", "-fsS", "http://localhost:8081/actuator/health"]`, `interval: 10s`, `timeout: 3s`, `retries: 6`, `start_period: 60s`(Flyway·기동 시간) |
| `postgres` | 로컬과 같은 `pgvector/pgvector:pg17`, `restart: unless-stopped`, 환경변수는 로컬 compose와 같게 `POSTGRES_USER: ${SPRING_DATASOURCE_USERNAME}`, `POSTGRES_PASSWORD: ${SPRING_DATASOURCE_PASSWORD}`, `POSTGRES_DB: my_routine`, named volume, **`ports` 없음**, `healthcheck: test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d my_routine"]` |
| `minio` | 로컬과 같은 이미지를 **digest로 고정**: `chainguard/minio@sha256:f74600a1a46330cdbda1ef760d17a96bd6e0f4a6f0a2c49792ca3ee7e4c6fa18`(2026-10-09의 `latest`, MinIO `RELEASE.2026-09-22T19-25-18Z`). `command: server /data --console-address ":9001"`, `MINIO_ROOT_USER: ${MINIO_ROOT_USER}`, `MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}`, named volume `/data`, `restart: unless-stopped`, 포트 `9000:9000`만(콘솔 9001은 publish하지 않는다), `healthcheck: test: ["CMD", "mc", "ready", "local"]` |

- **세 서비스 모두 `restart: unless-stopped`**: VM이 재부팅되면 Docker는 이 정책이 있는 컨테이너만 다시 띄운다. `app`에만 있으면 재부팅 뒤 앱만 올라와 DB 없이 죽고 다시 시작하기를 반복한다(`depends_on`은 `up` 때만 순서를 지키고 재시작에는 적용되지 않는다). 완료 확인의 "재부팅 복구"가 이걸 본다.
- **digest로 고정하는 이유**: `chainguard/minio`는 `latest`·`latest-dev` 태그만 있고 매일 다시 빌드된다. 태그로 두면 재배포·롤백 때마다 다른 MinIO가 뜰 수 있다. 올릴 때는 `docker pull chainguard/minio:latest` → `docker image inspect --format '{{index .RepoDigests 0}}' chainguard/minio:latest`로 새 digest를 얻어 이 파일을 고치는 커밋을 만든다(로컬 compose는 `latest` 그대로).
- MinIO 이미지 확인 결과(2026-10-09, 위 digest): 실행 사용자 uid 65532(비root)이고 named volume `/data` 쓰기는 정상이다. 이미지에 `mc`는 있고 `curl`은 없다. `mc ready local`은 서버가 준비될 때까지 기다렸다가 0으로 끝나는 것을 직접 확인했다(`local`은 `mc`에 기본으로 들어 있는 `http://localhost:9000` 별칭).
- **변수 치환 주의**: `env_file:`은 **컨테이너 안**의 환경변수만 채운다. compose 파일 안의 `${SPRING_DATASOURCE_USERNAME}` 같은 치환은 셸 환경변수나 `--env-file`로 준 파일에서만 읽는다(로컬에서 `--env-file .env.local`을 붙이는 이유와 같다). 그래서 S4의 스크립트는 항상 `--env-file /opt/myroutine/.env`를 붙인다. `IMAGE_TAG`는 셸 환경변수로 주며, 셸 값이 `--env-file` 값보다 우선한다. **`up`뿐 아니라 `ps`·`logs`·`exec`·`down`도 파일을 읽을 때 `${IMAGE_TAG:?}`를 치환하므로 `IMAGE_TAG`가 없으면 전부 같은 에러로 막힌다.** 그래서 스크립트는 `export IMAGE_TAG=...`로 하위 프로세스(`smoke.sh`)까지 물려주고, 사람이 VM에서 확인할 때는 `IMAGE_TAG=$(cat /opt/myroutine/deployed-sha)`를 앞에 붙인다.
- Part 3 이후 인프라가 늘어나면 이 파일에 서비스를 추가한다. 관측 스택은 `profiles: ["observability"]`로 분리한다([아키텍처 §6](../02-design/02-architecture-stage1.md)).

**운영 설정 — `.env.ops.example`** (키 이름만 적어 커밋하는 파일. 진짜 값은 VM의 `/opt/myroutine/.env`에만 둔다)

1. **`.gitignore` 확인**: 지금 `.env*`가 무시되고 `!.env.example`만 예외다. 이대로면 `.env.ops.example`이 **커밋에서 조용히 빠진다.** `!.env.ops.example`을 추가하고, `git check-ignore .env.ops.example`이 **아무것도 출력하지 않고 종료 코드 1**인 것을 확인한다(`-v`를 붙이면 예외 규칙 `!.env.ops.example` 줄이 출력되어 헷갈린다).
2. `.env.ops.example`에 아래 키를 적는다(`application.yaml`이 이미 읽는 이름을 그대로 쓴다. 새 이름을 만들지 않는다). 값은 비워 두거나 형태만 적는다.

| 키 | 운영 값의 형태 |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/my_routine` (compose 서비스 이름이 호스트 이름) |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | 앱 접속과 postgres 컨테이너 초기화에 함께 쓴다 |
| `JWT_SECRET` | `openssl rand -base64 48` |
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | 앱의 `myroutine.storage.access-key`·`secret-key`와 minio 컨테이너가 함께 쓴다 |
| `STORAGE_ENDPOINT`, `STORAGE_PUBLIC_BASE_URL` | `http://{VM_IP}:9000`, `http://{VM_IP}:9000/myroutine` (아래 ⚠) |
| `CORS_ALLOWED_ORIGINS` | 쉼표로 구분한 오리진 목록, 예: `http://192.168.0.20:5173` |

- ⚠ **presigned URL의 호스트는 `STORAGE_ENDPOINT`로 서명된다.** 컨테이너 내부 주소(`http://minio:9000`)로 두면 클라이언트가 받은 업로드 URL을 열 수 없다. 앱도 같은 LAN 주소로 MinIO에 접근한다(포트 9000이 publish돼 있으므로 컨테이너에서도 닿는다). **단, VM에 ufw가 있으면 막힌다**: 컨테이너에서 호스트의 LAN IP로 가는 패킷은 출발지가 Docker 브리지 대역(`172.x`)이라 "LAN에서만 허용" 규칙에 걸려 조용히 차단되고, 앱은 기동 때 `BucketInitializer`에서 `ApiCallTimeoutException`(5000 millis)으로 죽는다. 그래서 ufw에 `172.16.0.0/12`에서 9000으로 오는 것을 허용하는 규칙이 필요하다([`vm-setup.md` §2-4](../ops/vm-setup.md)). 맥 리허설은 ufw가 없어 이 문제를 못 잡는다.

**맥 리허설** (VM 없이 같은 파일을 맥에서 그대로 돌려 본다. S4도 이 준비를 쓴다)
```bash
sudo mkdir -p /opt/myroutine && sudo chown "$(whoami)" /opt/myroutine   # 운영과 같은 경로
cp .env.ops.example /opt/myroutine/.env     # 열어서 값을 채운다 (아래 표)
docker compose -f docker/docker-compose.yml down     # 로컬 개발 compose가 9000 포트를 쥐고 있으면 끈다 (-v 금지: 로컬 데이터 유지). bootRun도 끈다
```
| 키 | 리허설 값 |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/my_routine` |
| `SPRING_DATASOURCE_USERNAME/PASSWORD`, `MINIO_ROOT_*` | 아무 값(8자 이상) |
| `JWT_SECRET` | `openssl rand -base64 48` |
| `STORAGE_ENDPOINT`, `STORAGE_PUBLIC_BASE_URL` | 맥의 LAN IP 사용: `http://$(ipconfig getifaddr en0):9000`, 뒤에 `/myroutine`을 붙인 것 (유선이면 `en0`이 아닐 수 있다 — 확인 필요). `localhost`는 **컨테이너 안에서** 자기 자신이라 MinIO에 닿지 않는다 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |

```bash
docker build -t ghcr.io/95twan/myroutine:rehearsal -f docker/Dockerfile .      # S2에서 만든 Dockerfile
# 변수가 아니라 함수로 만든다: zsh(맥 기본 셸)는 따옴표 없는 $변수를 공백으로 쪼개지 않아 "no such file or directory"가 난다
compose() { docker compose --env-file /opt/myroutine/.env -f docker/docker-compose.ops.yml "$@"; }
export IMAGE_TAG=rehearsal                                     # 이 터미널에서 ps·logs·down 등에도 필요하다
compose config > /dev/null && echo OK                          # 문법·변수 치환 오류 확인
compose up -d --wait --wait-timeout 180
compose ps                                                    # 세 서비스 모두 healthy
curl -fsS localhost:8080/api/products                          # 공개 API → 200
compose exec -T app curl -fsS http://localhost:8081/actuator/health   # {"status":"UP"...}
nc -vz -w 3 localhost 5432; nc -vz -w 3 localhost 8081; nc -vz -w 3 localhost 9001   # 모두 실패해야 한다
```
정리: `compose down -v` (이름이 `myroutine-ops`라 로컬 볼륨은 지워지지 않는다).

| 증상 | 확인 | 흔한 원인 |
|---|---|---|
| `up`이 실패, `app`이 `unhealthy`/종료 | `compose logs app` | `.env` 값 누락(JWT_SECRET 비어 있음 등), MinIO 접속 실패(`STORAGE_ENDPOINT`가 `localhost`) |
| `postgres` 인증 실패 | `compose logs postgres` | Postgres는 **볼륨을 처음 만들 때만** `POSTGRES_PASSWORD`를 읽는다. `.env`의 비밀번호를 바꿨다면 `down -v`로 볼륨을 지우고 다시 |
| `healthy`가 안 된다 | `docker inspect --format '{{json .State.Health}}' <컨테이너>` | 헬스체크 명령의 오타, `start_period` 안에 기동이 안 끝남 |

#### S4. 배포 스크립트

**목적**: 새 이미지를 올리고, 정상인지 확인하고, 아니면 직전 버전으로 되돌리는 일을 스크립트 하나로 한다. `scripts/deploy.sh {sha}`, `scripts/smoke.sh` (리포 루트 기준 경로). 공통: `COMPOSE="docker compose --env-file /opt/myroutine/.env -f docker/docker-compose.ops.yml"`

**`deploy.sh`의 흐름**
1. `PREV=$(cat /opt/myroutine/deployed-sha 2>/dev/null || true)` — 비어 있으면 첫 배포
2. `export IMAGE_TAG={sha}` 후 `$COMPOSE up -d --wait --wait-timeout 180` — `--wait`는 모든 서비스가 running(헬스체크가 있으면 healthy)이 될 때까지 기다리고, 실패하면 0이 아닌 종료 코드를 낸다(로컬 Docker Compose v5.5.1의 `up --help`로 확인. VM에 설치한 버전에서 `docker compose up --help | grep wait`로 한 번 더 본다)
3. `scripts/smoke.sh` — 관리 포트 8081은 호스트에 publish하지 않으므로 호스트에서 `curl localhost:8081`은 안 된다. 컨테이너 안에서 부른다: `$COMPOSE exec -T app curl -fsS http://localhost:8081/actuator/health`, 공개 API는 호스트에서 `curl -fsS http://localhost:8080/api/products`
4. 2·3이 모두 성공하면 `echo {sha} > /opt/myroutine/deployed-sha`, 종료 코드 0
5. 2·3 중 하나라도 실패하면: `PREV`가 있으면 `export IMAGE_TAG=$PREV` 후 `$COMPOSE up -d --wait` → 종료 코드 1(Actions 빨간불, `deployed-sha`는 그대로). `PREV`가 없으면 되돌릴 버전이 없으므로 그냥 종료 코드 1

**셸 스크립트를 처음 쓴다면** — 스크립트는 터미널에서 칠 명령을 파일에 순서대로 적어 둔 것이다. 이 단계에 쓰는 문법은 아래가 전부다.

| 문법 | 뜻 | 예 |
|---|---|---|
| `#!/usr/bin/env bash` | 파일 맨 첫 줄. "이 파일은 bash로 실행하라"는 표시. 맥 터미널은 zsh라서 이 줄이 없으면 동작이 달라진다 | |
| `set -euo pipefail` | 안전장치 한 줄. `-e` 명령이 하나라도 실패하면 거기서 스크립트 종료, `-u` 정의 안 한 변수를 쓰면 에러, `pipefail` 파이프(`a \| b`) 중간 실패도 실패로 본다 | |
| `변수=값` / `$변수` | 값을 저장하고 꺼내 쓴다. **`=` 양옆에 공백을 넣지 않는다.** 값에 공백이 있을 수 있으면 `"$변수"`처럼 따옴표로 감싼다 | `SHA=abc` → `echo "$SHA"` |
| `$1` | 스크립트를 부를 때 준 첫 번째 인자 | `scripts/deploy.sh good`이면 `$1`은 `good` |
| `$(명령)` | 그 명령의 **출력**을 값으로 쓴다 | `PREV=$(cat 파일)` |
| `A && B` / `A \|\| B` | A가 성공하면 B 실행 / A가 실패하면 B 실행 | `cat 파일 \|\| true` → 파일이 없어도 "성공"으로 친다 |
| `if 명령; then ... fi` | 명령이 **성공(종료 코드 0)** 하면 안쪽 실행. 안쪽이 아닌 조건 자리에서 실패해도 `set -e`로 종료되지 않는다 | `if [ -n "$PREV" ]; then ... fi` (`-n` = 비어 있지 않다) |
| 종료 코드 | 모든 명령은 끝나면 숫자를 남긴다. 0 = 성공, 그 외 = 실패. `$?`로 직전 값을 본다. `exit 숫자`로 스크립트가 직접 정한다. **GitHub Actions는 이 숫자로 초록/빨강을 정한다** | `exit 1` |
| `>`, `>&2` | `echo x > 파일`은 파일에 덮어쓰기, `echo x >&2`는 에러 출력 쪽으로 보내기 | |

**`deploy.sh`를 조각으로 쓰기** — 위에서 아래로 한 조각씩 이어 붙인다. 각 조각이 위 "흐름"의 몇 번인지 같이 적었다.

1. **머리 (준비)**
   ```bash
   #!/usr/bin/env bash
   set -euo pipefail
   cd "$(dirname "$0")/.."
   SHA=${1:?usage: scripts/deploy.sh <sha>}
   ```
   - `$0`은 이 스크립트 자신의 경로, `dirname`은 그 폴더(`scripts`), `/..`로 한 단계 위인 리포 루트로 이동한다. 어디서 부르든 `docker/docker-compose.ops.yml` 같은 상대 경로가 맞게 하려는 것이다.
   - `${1:?메시지}`는 인자가 없으면 메시지를 찍고 종료한다.
2. **compose 명령 정해 두기**
   ```bash
   export COMPOSE="docker compose --env-file /opt/myroutine/.env -f docker/docker-compose.ops.yml"
   ```
   `export`를 붙이면 이 스크립트가 부르는 `smoke.sh`도 같은 변수를 쓸 수 있다. **빼먹으면** `up`은 성공하는데 `smoke.sh: line 4: COMPOSE: unbound variable`로 실패해 정상 배포가 롤백(또는 첫 배포면 실패)으로 끝난다. `IMAGE_TAG`도 마찬가지로 `export`로 준다. 이후 `$COMPOSE up ...`처럼 **따옴표 없이** 쓰면 bash가 공백으로 쪼개 `docker compose --env-file ...`로 펼친다(zsh는 안 쪼개므로 터미널에서 직접 칠 때는 S3의 `compose` 함수를 쓴다).
3. **흐름 1 — 직전 버전 기억**
   ```bash
   PREV=$(cat /opt/myroutine/deployed-sha 2>/dev/null || true)
   ```
   파일이 없으면(첫 배포) `cat`이 실패하는데, `|| true` 덕분에 `set -e`로 죽지 않고 `PREV`가 빈 값이 된다. `2>/dev/null`은 "파일 없음" 에러 메시지를 버린다.
4. **흐름 2~4 — 배포하고 성공하면 기록**
   ```bash
   export IMAGE_TAG=$SHA
   if $COMPOSE up -d --wait --wait-timeout 180 && scripts/smoke.sh; then
     echo "$SHA" > /opt/myroutine/deployed-sha
     echo "deployed $SHA"
     exit 0
   fi
   ```
   - `export IMAGE_TAG`를 먼저 하는 이유: 한 줄 접두(`IMAGE_TAG=x 명령`)는 그 명령에만 적용돼서 뒤따르는 `smoke.sh`의 `$COMPOSE exec`가 `IMAGE_TAG is missing`으로 실패한다.
   - `up`이 실패하거나 `smoke.sh`가 실패하면 `if`가 거짓이라 안쪽을 건너뛰고 다음 조각(롤백)으로 간다. `if`로 감싸야 `set -e`로 여기서 죽지 않는다.
5. **흐름 5 — 실패하면 되돌리고 실패로 끝내기**
   ```bash
   echo "deploy failed: $SHA" >&2
   if [ -n "$PREV" ]; then
     export IMAGE_TAG=$PREV
     $COMPOSE up -d --wait
     echo "rolled back to $PREV" >&2
   else
     echo "no previous version to roll back to" >&2
   fi
   exit 1
   ```
   롤백을 했어도 **반드시 `exit 1`로 끝난다.** 0으로 끝나면 Actions가 초록불이라 배포가 실패한 걸 모른다. 롤백 `up`이 로컬에 없는 이미지를 쓰면 GHCR에서 받으므로 `docker login`이 `deploy.sh` 앞에 있어야 한다(S7의 `cd.yml`이 한다).

**`smoke.sh`** — "정말 동작하나?" 확인만 한다. 하나라도 실패하면 스크립트가 실패한다(`set -e`).
```bash
#!/usr/bin/env bash
set -euo pipefail
$COMPOSE exec -T app curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8080/api/products > /dev/null
```
- `exec -T app`은 실행 중인 `app` 컨테이너 **안에서** 명령을 실행한다(`-T`는 터미널을 붙이지 않는 옵션. Actions에는 터미널이 없어서 필요). 8081 관리 포트는 호스트에 열려 있지 않아서 컨테이너 안에서 불러야 한다.
- `curl -fsS`: `-f` HTTP 에러(4xx/5xx)면 실패 코드, `-sS` 진행바는 끄고 에러 메시지는 보여준다.
- `COMPOSE`와 `IMAGE_TAG`는 `deploy.sh`가 `export`한 값을 쓴다. 그래서 `smoke.sh`만 단독으로 부르면 `$COMPOSE: command not found`가 난다(정상. 단독 테스트는 `export`를 먼저 해 준다).

**만든 뒤**
1. 문법만 먼저 검사(실행하지 않는다): `bash -n scripts/deploy.sh && bash -n scripts/smoke.sh` → 아무것도 안 나오면 통과.
2. 무슨 일이 일어나는지 보고 싶으면 `bash -x scripts/deploy.sh good`: 실행되는 명령을 `+`로 한 줄씩 보여준다.
3. 실행 권한: `chmod +x scripts/*.sh`. 커밋 전에 `git ls-files -s scripts/deploy.sh`(add 이후)가 `100755`로 나오는지 본다. 권한이 git에 안 들어가면 VM에서 `Permission denied`.

**확인 — 맥 리허설** (S3의 준비를 쓴다. 롤백이 실제로 도는지 이 단계에서 확인한다)
```bash
docker build -t ghcr.io/95twan/myroutine:good -f docker/Dockerfile .
printf 'FROM eclipse-temurin:25-jre\nCMD ["false"]\n' | docker build -t ghcr.io/95twan/myroutine:bad -   # 뜨자마자 종료하는 "고장난" 이미지

rm -f /opt/myroutine/deployed-sha
scripts/deploy.sh good; echo "exit=$?"        # exit=0, cat /opt/myroutine/deployed-sha → good
scripts/deploy.sh bad;  echo "exit=$?"        # exit=1, deployed-sha 여전히 good. 확인: IMAGE_TAG=$(cat /opt/myroutine/deployed-sha) compose ps → app 이미지 태그 good·healthy
rm /opt/myroutine/deployed-sha; scripts/deploy.sh bad; echo "exit=$?"   # 첫 배포 실패: 되돌릴 버전 없음 → exit=1
```
세 경우가 위 결과와 다르면 S5로 넘어가지 말고 고친다. 정리는 `down -v`.

#### S5. VM 준비 (사용자, 절차는 [`vm-setup.md`](../ops/vm-setup.md))

- Proxmox에 Ubuntu Server VM 생성(RAM 16GB·vCPU 8·디스크 100GB, 호스트 RAM이 24GB라 8GB를 호스트에 남긴다), 고정 IP(DHCP 예약), Docker Engine + compose 플러그인 설치
- 전용 사용자(비root, docker 그룹)로 GitHub Actions runner를 설치하고 **systemd 서비스**로 등록, 러너 라벨 `ops`
- `/opt/myroutine/.env` 작성(권한 600), 배포 상태 파일 경로 `/opt/myroutine/deployed-sha`

`vm-setup.md`를 §1부터 §8 "준비 완료 확인"까지 따른다. 막히면 어느 절의 어느 명령인지 알려 달라(문서를 고친다). 이 단계의 끝은 §8 표의 모든 항목 체크 + `uname -m`(amd64면 `x86_64`) 확인이다.

#### S6. GitHub 저장소 설정 (사람이 한 번)

1. Settings → Environments → **New environment**, 이름 `ops` → *Deployment branches and tags*를 *Selected branches and tags*로 바꾸고 `develop`, `main` 추가. 확인: `gh api repos/95twan/myroutine-v2/environments/ops`의 `deployment_branch_policy`가 비어 있지 않다.
2. Settings → Actions → General → *Approval for running fork pull request workflows from contributors* → *Require approval for all external contributors*(2026-10-09 GitHub 문서로 메뉴 이름 확인). 이 리포의 현재 값은 `first_time_contributors`이고 `gh api repos/95twan/myroutine-v2/actions/permissions/fork-pr-contributor-approval`로 확인한다. 바꾼 뒤 값은 `all_external_contributors`(2026-10-10 직접 확인).
3. 두 화면을 스크린샷으로 남긴다(완료 확인 "포크 PR 안전"에 쓴다).

`environment: ops`를 쓰는 job은 환경이 없으면 **제한 없이 자동 생성**되므로 `cd.yml`을 올리기 전에 이 단계를 끝내야 한다.

#### S7. 워크플로 `cd.yml`과 첫 배포

**목적**: develop 머지 → 이미지 빌드 → VM 배포를 자동으로 잇는다. `.github/workflows/cd.yml` (YAML은 들여쓰기가 문법이다. 공백 2칸, 탭 금지)

```yaml
name: CD

on:
  workflow_run:
    workflows: ["CI"]
    types: [completed]
    branches: [develop]
  workflow_dispatch:
    inputs:
      sha:
        description: "배포할 커밋의 전체 sha (40자리)"
        required: true

env:
  SHA: ${{ github.event.workflow_run.head_sha || inputs.sha }}

concurrency:
  group: deploy
  cancel-in-progress: false

jobs:
  build-image:
    if: github.event_name == 'workflow_dispatch' || (github.event.workflow_run.conclusion == 'success' && github.event.workflow_run.event == 'push')
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ env.SHA }}
      - name: GHCR 로그인
        run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
      - name: 이미지 빌드
        run: docker build -f docker/Dockerfile -t ghcr.io/95twan/myroutine:$SHA .
      - name: 이미지 push
        run: docker push ghcr.io/95twan/myroutine:$SHA

  deploy:
    needs: build-image
    if: github.event_name == 'workflow_dispatch' || (github.event.workflow_run.conclusion == 'success' && github.event.workflow_run.event == 'push')
    runs-on: [self-hosted, ops]
    environment: ops
    permissions:
      contents: read
      packages: read
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ env.SHA }}
      - name: GHCR 로그인
        run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin
      - name: 배포
        run: scripts/deploy.sh $SHA
```

| 항목 | 뜻과 이유 |
|---|---|
| `name: CD` | Actions 탭에 보이는 이름 |
| `on.workflow_run` | **CI 워크플로가 끝나면** 이 워크플로를 시작한다. `workflows: ["CI"]`는 `ci.yml`의 `name: CI`와 글자까지 같아야 한다. `types: [completed]`는 성공·실패 모두 "끝남"이라 아래 `if`에서 성공만 거른다. `branches: [develop]`은 develop에서 돈 CI만 |
| `on.workflow_dispatch.inputs.sha` | Actions 화면의 *Run workflow* 버튼으로 **수동 실행**. 이전 sha를 넣어 그 버전으로 되돌릴 때 쓴다 |
| `env.SHA` | 배포할 커밋. `workflow_run`일 때는 앞쪽 값(CI를 돌린 커밋)이 있고, 수동일 때는 그 값이 비어 `\|\|` 뒤의 `inputs.sha`가 쓰인다. 이후 모든 단계는 이 하나만 쓴다 |
| `concurrency` | 같은 `group`의 실행은 **한 번에 하나**. 머지가 연달아 일어나도 배포 둘이 겹치지 않는다. `cancel-in-progress: false`는 진행 중인 배포를 도중에 끊지 않는다(끊으면 반쯤 배포된 상태가 된다). 대기 중인 실행이 여러 개면 최신 것만 남고 중간 것은 건너뛴다 |
| `if: ...` (두 job 모두) | 수동 실행이거나, **CI가 성공했고 그 CI가 `push`로 돈 것**일 때만. `workflow_run.event == 'push'`가 보안의 핵심이다(아래 ⚠). `needs`가 있는 `deploy`에도 따로 넣는다 |
| `build-image.runs-on: ubuntu-latest` | GitHub가 빌려 주는 일회용 서버에서 이미지를 빌드한다(내 VM의 자원을 안 쓴다) |
| `permissions` | 이 job의 `GITHUB_TOKEN` 권한을 **필요한 만큼만**. 빌드는 이미지를 올려야 하니 `packages: write`, 배포는 받기만 하니 `packages: read`, 둘 다 코드 내려받기용 `contents: read` |
| `actions/checkout@v4` + `ref: ${{ env.SHA }}` | 소스를 내려받는다. 기본값은 최신 develop이라, CI를 통과한 **그 커밋**을 정확히 쓰려면 `ref`를 지정한다. `deploy`에도 필요하다: 운영 compose 파일과 `deploy.sh`가 리포 안에 있고, VM에는 그 파일들이 없기 때문 |
| GHCR 로그인 | `GITHUB_TOKEN`을 stdin(`--password-stdin`)으로 넘겨 로그인한다. 명령행 인자로 주면 프로세스 목록·로그에 남는다. 별도 비밀번호를 만들 필요가 없다 |
| `docker build ... -t ghcr.io/95twan/myroutine:$SHA .` | 이미지 이름 끝에 sha를 붙인다(소문자만 허용). 맨 끝 `.`은 `.dockerignore`를 적용한 빌드 컨텍스트 |
| `docker push` | GHCR에 올린다. 패키지가 처음이면 이때 만들어지고, Dockerfile의 `LABEL`로 리포와 연결된다 |
| `deploy.runs-on: [self-hosted, ops]` | 내 VM의 러너. **두 라벨을 모두 가진** 러너만 일을 받는다 |
| `needs: build-image` | 이미지가 push된 뒤에 시작. 앞이 실패하면 배포는 건너뛴다 |
| `environment: ops` | S6에서 만든 환경. 배포 가능한 브랜치(develop·main)를 여기서 제한한다. `workflow_run`으로 돈 job의 브랜치는 기본 브랜치(develop)라 통과한다 |
| `scripts/deploy.sh $SHA` | S4에서 만든 스크립트가 실제 배포, 확인, 롤백을 한다. 실패하면 종료 코드 1로 끝나 이 단계가 빨간불이 된다 |

- ⚠ **`workflow_run`은 포크에서 온 PR의 CI가 끝나도 실행된다.** 이때 워크플로 파일은 기본 브랜치(develop)의 것이 쓰이고 저장소 권한으로 돈다. 그래서 위 실행 조건의 `workflow_run.event == 'push'`가 필수다(PR로 돈 CI는 `event`가 `pull_request`). `branches: [develop]` 필터만으로는 부족하다 — 포크의 브랜치 이름도 `develop`일 수 있다.
- `cd.yml`에는 `pull_request`·`pull_request_target` 트리거를 두지 않는다.

**먼저 알아둘 것**: 이 리포의 기본 브랜치는 `develop`이다. `workflow_run`과 `workflow_dispatch`는 **기본 브랜치에 있는 워크플로 파일**로 동작한다. 그래서 feature 브랜치에서는 `cd.yml`을 시험할 수 없고(YAML 오류도 머지 후에야 Actions 탭에 `workflow file issue`로 보인다), **PR을 develop에 머지하는 순간이 첫 실행**이다.

머지 전 체크리스트 (하나라도 빠지면 첫 실행이 실패하거나 무한 대기한다)
- [ ] S1~S4가 맥에서 통과했고, S5 VM 준비가 끝났다(러너가 Idle이 아니면 `deploy`가 *Waiting for a runner*로 멈춘다)
- [ ] `/opt/myroutine/.env`가 VM에 있다
- [ ] S6의 Environment `ops`가 있다
- [ ] `cd.yml` 점검: `workflows: ["CI"]`의 문자열이 `ci.yml`의 `name: CI`와 정확히 같다 / `deploy` job에 `actions/checkout`이 있다 / `pull_request` 트리거가 없다 / 실행 조건 `workflow_run.event == 'push'`가 두 job 모두에 있다 / 이미지 이름이 전부 소문자다

머지 후
```bash
gh run list --workflow=cd.yml --limit 3     # 상태
gh run watch                                 # 진행 보기
gh run view --log-failed                     # 실패 로그
```
| 실패 지점 | 흔한 원인 |
|---|---|
| `build-image`의 push `denied` | `permissions: packages: write` 누락, 이미지 이름 대문자 |
| `deploy`가 시작을 안 함 | 러너 Offline/라벨 불일치(`ops`), Environment 브랜치 제한에서 거절 |
| `deploy`의 `docker login`/pull 실패 | `permissions: packages: read` 누락, 패키지가 리포에 연결되지 않음(S2의 `LABEL`) |
| `deploy.sh` `Permission denied` | 실행 권한이 git에 없음(S4-1) |
| `up --wait` 실패 | VM에서 `IMAGE_TAG=$SHA docker compose ... logs app` — 대부분 `.env` 값 문제 |

> 수동 배포·롤백 확인에는 **이전 sha**가 필요하다. 이전 sha는 **Dockerfile이 이미 있는 커밋**이어야 한다(그 전 커밋은 이미지를 빌드할 수 없다). 그러니 첫 배포 이후 작은 PR을 하나 더 머지해 배포를 **두 번** 만들어 둔다.

#### S8. 완료 확인 실행

아래 "완료 확인" 표의 항목별 실행 방법이다. 모두 **VM이 아닌 다른 PC**에서 한다(LAN 접근이 진짜 되는지 보는 것이므로).

**풀스택** — `jq`가 필요하다(없으면 응답을 눈으로 읽어 값을 옮긴다)
```bash
BASE=http://{VM_IP}:8080
TOKEN=$(curl -s -X POST $BASE/api/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"ops@test.com","password":"pass1234","nickname":"opsuser","name":"운영테스트"}' | jq -r .accessToken)   # 가입 응답에 토큰이 있다
curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ops@test.com","password":"pass1234"}' | jq .accessToken    # 로그인 API도 운영에서 한 번 확인(위 TOKEN으로 계속 쓰면 된다)
SHOP=$(curl -s -X POST $BASE/api/shops -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"운영가게","businessNumber":"1234567890","email":"shop@test.com","phone":"010-1234-5678","address":"서울"}' | jq -r .shopId)
PRODUCT=$(curl -s -X POST $BASE/api/shops/$SHOP/products -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"테스트상품","description":"설명","category":"FOOD","price":1000,"initialStock":10,"subscribable":false}' | jq -r .productId)
SIZE=$(wc -c < a.png)                       # a.png: 아무 이미지 파일 (서버는 내용을 검증하지 않는다)
R=$(curl -s -X POST $BASE/api/shops/$SHOP/products/$PRODUCT/images/presigned-url -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d "{\"contentType\":\"image/png\",\"contentLength\":$SIZE}")
UPLOAD=$(echo "$R" | jq -r .uploadUrl); KEY=$(echo "$R" | jq -r .objectKey)
echo "$UPLOAD"                              # 호스트가 http://{VM_IP}:9000 인지 본다 (minio 면 STORAGE_ENDPOINT 오류)
curl -i -X PUT "$UPLOAD" -H "Content-Type: image/png" --data-binary @a.png      # 200
IMG=$(curl -s -X POST $BASE/api/shops/$SHOP/products/$PRODUCT/images -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d "{\"objectKey\":\"$KEY\"}" | jq -r .url)
curl -s -o /dev/null -w '%{http_code}\n' "$IMG"                                  # 200
```
(같은 이메일·사업자번호로 두 번 돌리면 중복 에러가 난다. 다시 돌릴 땐 값을 바꾼다.)

**노출 제한**: `nc -vz -w 3 {VM_IP} 5432`, `8081`, `9001`은 실패, `8080`·`9000`은 성공.

**자동 롤백**: ① 새 브랜치에서 `application-prod.yaml`에 **바인딩이 실패하는 값**을 넣는다: `myroutine.jwt.access-token-ttl: invalid`(기존 `cors-allowed-origins` 줄은 그대로). 이 값은 `Duration`으로 변환되지 않아 `Failed to bind properties under 'myroutine.jwt.access-token-ttl'`로 기동이 실패한다(2026-10-10 로컬 `bootRun`으로 확인). `application-prod.yaml`은 `application.yaml`보다 우선하고, 이 속성을 덮는 환경변수는 `.env`에 없다. test 프로필은 이 파일을 읽지 않아 **CI는 통과**하고 운영(`prod`)에서만 실패한다. **플레이스홀더(`${없는변수}`)로는 실패시킬 수 없다**: `@ConfigurationProperties` 바인딩은 해석하지 못한 `${...}`를 문자열 그대로 두고 기동한다(`cors-allowed-origins`에 넣어 보니 `Started … in 3.93 seconds`였고 운영에서도 배포가 두 번 연속 성공했다). 아무 코드도 읽지 않는 속성도 마찬가지다.  ② develop에 머지. ③ CD가 빨간불, VM에서 `cat /opt/myroutine/deployed-sha`는 이전 sha 그대로, `IMAGE_TAG=$(cat /opt/myroutine/deployed-sha) docker compose --env-file /opt/myroutine/.env -f docker/docker-compose.ops.yml ps`의 앱 이미지 태그도 이전 sha, 위 풀스택 `curl`의 `/api/products`는 계속 200. ④ Actions 로그를 PR에 붙이고 revert PR로 되돌린다.

**수동 배포**: Actions → CD → *Run workflow*에 이전 40자리 sha 입력 → VM의 앱 이미지 태그가 그 sha로 바뀐다. 이후 다시 최신 sha로 수동 배포해 원상 복구한다.

**마이그레이션**: VM에서 `runner`로 전환해 리포 작업 폴더에서 실행한다(`/opt/myroutine`는 `runner` 전용이라 `vmadmin`은 `deployed-sha`와 `.env`를 못 읽고, `sudo`는 `-E`가 막혀 있어 `IMAGE_TAG`가 전달되지 않는다).
```bash
sudo -iu runner
cd ~/actions-runner/_work/myroutine-v2/myroutine-v2
IMAGE_TAG=$(cat /opt/myroutine/deployed-sha) \
docker compose --env-file /opt/myroutine/.env -f docker/docker-compose.ops.yml exec -T postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d my_routine -c "select version, description, success from flyway_schema_history order by installed_rank"'
```
마지막 줄이 최신 버전이고 `success`가 `t`.

**시크릿**: `git ls-files | grep -i '\.env'`는 `.env.example`, `.env.ops.example`만. VM에서 `docker history --no-trunc ghcr.io/95twan/myroutine:{sha}`와 `docker image inspect --format '{{.Config.Env}}' ...`에 `.env`의 값이 없다. GitHub Secrets 목록에 운영 값이 없다.

**재부팅 복구**: VM에서 `sudo reboot` → 2~3분 뒤 GitHub Runners에서 Idle, 다른 PC에서 `curl $BASE/api/products` 200(사람이 `compose up`을 치지 않았는데 올라와 있어야 한다. `restart: unless-stopped`와 `systemctl enable docker`가 이걸 한다).

**포크 PR 안전**: 파일 점검(`grep -n "self-hosted" .github/workflows/*.yml`에서 `ci.yml`이 없고 `cd.yml`만, `grep -n "workflow_run.event" .github/workflows/cd.yml`) + 같은 리포의 브랜치로 PR을 올려 CI가 끝나도 **CD 실행이 생기지 않는 것** 보기(`gh run list --workflow=cd.yml`에 그 PR 커밋 sha가 없다. `workflow_run`의 `branches: [develop]` 필터가 CI를 돌린 브랜치 이름으로 먼저 거른다. `event == 'push'`는 그 필터를 통과하는 경우, 즉 포크의 브랜치 이름이 `develop`인 PR을 막는 두 번째 방어선이라 같은 리포 PR로는 직접 볼 수 없다) + S6의 스크린샷.

**러너 권한**: [`vm-setup.md` §5](../ops/vm-setup.md)의 두 점검 명령.

**문서 (Claude)**: `docs/ops/vm-setup.md`(VM 준비 절차), 7-5의 README에 "운영 환경과 배포 흐름" 절 포함

### 완료 확인

| 확인 | 방법 |
|---|---|
| [ ] 머지 → 자동 배포 | develop에 머지 → Actions에서 CI 후 CD가 순서대로 성공 → VM에서 `IMAGE_TAG=$(cat /opt/myroutine/deployed-sha) docker compose ... ps`의 앱 이미지 태그 = 머지 sha |
| [ ] 풀스택 동작 | **LAN의 다른 PC**에서 `curl`로 가입 → 로그인 → 가게 → 상품 → presigned URL 발급 → **그 URL로 PUT** → 등록 → 이미지 URL GET 200 |
| [ ] 노출 제한 | 다른 PC에서 `nc -vz {VM IP} 5432`, `8081`, `9001` 모두 실패 / `8080`, `9000`은 성공 |
| [ ] 자동 롤백 | 일부러 부팅이 실패하는 커밋(예: 필수 환경변수 누락)을 머지 → 헬스체크 실패 → Actions 빨간불 → 앱은 직전 sha로 계속 응답, `deployed-sha` 그대로 (확인 후 되돌림, PR에 로그) |
| [ ] 수동 배포 | `workflow_dispatch`에 이전 sha 입력 → 그 버전으로 교체 |
| [ ] 마이그레이션 | 마이그레이션이 든 배포 후 `flyway_schema_history`에 새 버전이 있다 |
| [ ] 포크 PR 안전 | `pull_request`로 실행되는 워크플로에 `self-hosted` job이 없고, `cd.yml`의 실행 조건에 `workflow_run.event == 'push'`가 있다(파일 점검) + 같은 리포의 PR을 올려 CI가 끝나도 CD 실행이 생기지 않는 것 + Environment 배포 브랜치 제한·포크 승인 설정 스크린샷 |
| [ ] 시크릿 | `git ls-files`·GitHub Secrets·`docker history`에 운영 `.env` 값이 없다 |
| [ ] 재부팅 복구 | VM 재부팅 후 러너 서비스와 compose가 사람 개입 없이 올라온다 |
| [ ] 러너 권한 | 러너 서비스가 root가 아닌 전용 사용자로 돈다 |

**제안 (선택)**
- 배포 결과(성공·실패, sha)를 Actions Job Summary에 남기기 — Part 7에서 배포 이력을 보기 쉬워진다

**리뷰 때 물어볼 것**
- public 리포에서 self-hosted runner가 위험한 이유는? 어떤 설정이 그걸 막나? 그래도 남는 위험은?
- `workflow_run`에 `branches: [develop]`만 걸고 `event == 'push'` 조건을 빼면 어떤 PR이 VM에 배포될 수 있나?
- 이미지를 `latest`가 아니라 sha로 배포하는 이유는? 롤백이 어떻게 달라지나?
- 새 버전이 마이그레이션을 포함하고 헬스체크에 실패해 이미지가 롤백되면, DB는 어떤 상태인가? 이전 앱은 그 DB에서 동작하나?
- presigned URL의 호스트를 컨테이너 내부 주소로 두면 무슨 일이 생기나?
- 시크릿을 GitHub Secrets 대신 VM 파일에 둔 이유와 대가는?

---

## Part 1 완료
- [ ] develop → main PR(merge commit), 태그 `v0.1.0`, GitHub Release에 완료 단계와 동시성 테스트 결과 요약, **배포 환경에서 돌려본 결과(1-11 완료 확인)** 링크
- [ ] Part 2 문서를 다시 읽고, Part 1에서 배운 점을 반영해 다듬는다(필요하면 Claude에게 요청)
- [ ] **Part 2 시작 전에 결정**: 상품 등록 요청에서 `subscribable`을 생략하면 400이고 `details`가 `{}`다(원시 `boolean`이 역직렬화 단계에서 거절된다). (a) `Boolean`+`@NotNull`로 필수 처리 / (b) 생략 시 `false`(DB 기본값과 같음, 권장). 정하면 1-7 `RegisterProductRequest` 규격부터 고친다

### Release 노트에 적을 알려진 한계

| 한계 | 내용 | 해소 |
|---|---|---|
| 토큰 즉시 차단 불가 | refresh·로그아웃·강제 차단이 없고 Access 토큰은 만료(1시간)까지 유효하다 | Part 4 |
| `subscribable` 생략 시 400 | `details`가 비어 있어 원인을 알 수 없다 | Part 2 시작 전 결정 |
| 지원하지 않는 `Content-Type`이 500 | `text/plain` 등으로 요청하면 415가 아니라 500 `INTERNAL_ERROR`와 ERROR 로그가 나간다 | 선택(415 매핑) |
| 저장소 객체 잔류 | URL만 받고 등록하지 않은 객체, 삭제 후 저장소 삭제에 실패한 객체가 남는다. 파일 내용(매직 바이트)도 검증하지 않는다 | Stage 1 범위 밖(회고) |
| 앱이 MinIO root 계정으로 접속 | 서비스 계정을 분리하지 않았다 | Stage 1 범위 밖(회고) |
| 로컬·테스트 MinIO 버전 미고정 | `chainguard/minio:latest`(운영은 digest로 고정) | — |
| 롤백은 이미지만 | 마이그레이션은 앞으로만 간다. 이전 앱이 새 스키마에서 동작해야 롤백이 안전하다 | 1-11 문서 참고 |

---

## 수정 사항 (1-6 리뷰에서 발견)

> 2026-10-06 · "null은 유지" 방식의 부분 수정 API는 `@NotBlank`를 쓸 수 없어서(`null`도 막는다), `@Size(max)`만으로는 **빈 문자열이 통과해 `NOT NULL` 컬럼에 빈 값이 저장**된다. 1-6(가게 수정)은 규격에 반영했고(`UpdateShopRequest`), 이미 끝난 1-5의 두 수정 API에도 같은 정책을 적용한다. 구현은 사용자가 직접 했다(2026-10-10 완료, PR #21).

**공통 규칙**: 수정 요청의 각 필드는 `null`(변경 없음)이거나 값이 있어야 한다. 빈 문자열·공백만 있는 문자열은 400 `INVALID_REQUEST`.

| 대상 | 현재 검증 | 빈 값이 통과하는 필드 | 수정 방향 |
|---|---|---|---|
| `PATCH /api/members/me` (`ChangeProfileRequest`) | `nickname @Size(min=2,max=20)`, `name @Size(min=1,max=50)`, `phone @Pattern` | `""`은 `min`으로 이미 막힌다. **공백만 있는 값**(`nickname = "  "`, `name = " "`)은 통과한다 | `nickname`·`name`에 `@Pattern(regexp = ".*\\S.*")`를 더한다. `phone`은 패턴이 이미 거른다 |
| `PATCH /api/members/me/addresses/{id}` (`UpdateAddressRequest`) | `recipient @Size(max=50)`, `address1 @Size(max=200)` 등 | `recipient`·`address1`은 `""`도 통과한다(둘 다 `NOT NULL` 컬럼). `phone`·`zipcode`는 패턴이 이미 거른다 | `recipient`·`address1`에 같은 `@Pattern`을 더한다 |
| `address2` (같은 요청) | `@Size(max=200)` | 컬럼이 NULL 허용(선택 입력)이라 `""`이 통과해 `NULL`과 `""` 두 값으로 저장된다 | **비우기를 허용하고 `NULL`로 통일한다.** 아래 "address2 규칙" 참고 |

**address2 규칙** (선택 입력이라 다른 필드와 다르다)
- 수정 요청: 생략·`null`은 변경 없음(기존과 같다). `""`은 **"비움"**으로 보고 `NULL`로 저장한다. 공백만 있는 값(`"  "`)은 400 `INVALID_REQUEST`. 검증은 `@Pattern(regexp = "^$|.*\\S.*")`(빈 문자열이거나 공백이 아닌 문자를 포함)로 한다.
- 변환 위치: `null`이 "변경 없음"이라서 `null`로 비울 수 없으므로, `MemberAddress.update`가 `""`을 받으면 `address2`를 `null`로 저장한다(`null`이면 건드리지 않는다).
- 등록 요청(`AddressRequest`)에서 `address2`가 `""`이어도 `NULL`로 저장해, 저장 값을 `NULL` 하나로 통일한다.

**테스트(완료 확인에 추가)**

| 테스트 클래스 | 케이스 |
|---|---|
| `member/web/MemberControllerTest` | [ ] `name`을 공백만 있는 값으로 PATCH → 400 `INVALID_REQUEST` |
| `member/web/MemberAddressControllerTest` | [ ] `recipient`를 빈 문자열로 PATCH → 400 `INVALID_REQUEST` / [ ] `address2`를 `""`로 PATCH → 200, 응답 `address2`가 `null` / [ ] `address2`를 생략하고 PATCH → 기존 값 유지 / [ ] `address2`를 공백만 있는 값으로 PATCH → 400 / [ ] 등록 때 `address2: ""` → 목록 조회 시 `null` |

**리뷰 때 물어볼 것**
- `@NotBlank` 대신 `@Pattern`을 쓰는 이유는? `@NotBlank`를 수정 요청에 쓰면 무엇이 달라지나?
- "null은 유지"와 "값을 비운다"를 한 요청 형식으로 구분할 수 있나? (`address2`는 `""`를 "비움"으로 약속해서 구분한다. 이 약속의 대가는?)
