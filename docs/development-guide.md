# 개발 가이드 (MyRoutin v2)

> 코드를 작성할 때 따르는 규칙이자 **코드 리뷰 기준**이다. 리뷰에서 지적하는 항목은 이 문서의 절 번호로 표시한다(예: `§6.2 위반`).
> 규칙을 바꿔야 할 이유가 생기면 코드보다 이 문서를 먼저 고친다.
> 근거: [02-design](02-design/README.md), [ADR](adr/)

## 목차
1. 기술 스택
2. 프로젝트 레이아웃
3. 모듈 내부 구조 (DDD 4계층)
4. 모듈 간 통신
5. 도메인 모델 작성법
6. 애플리케이션 서비스 작성법
7. 웹 계층 작성법
8. 인프라 계층 작성법
9. 이벤트 발행·소비
10. 동시성·정합성 패턴
11. 예외·에러 응답
12. 네이밍
13. 테스트
14. 로깅·관측
15. 설정·보안
16. Git·PR
17. 모듈 하나를 만드는 순서
18. 리뷰 체크리스트
19. As-Is에서 배운 금지 패턴

---

## 1. 기술 스택
| 영역 | 선택 | 비고 |
|---|---|---|
| 언어 | **Java 25 (LTS)** | record, sealed, pattern matching, 가상 스레드 |
| 프레임워크 | **Spring Boot 4.x 최신 GA** (Spring Framework 7) | 프로젝트 생성 시 start.spring.io의 최신 GA로 고정하고, 패치 버전만 따라간다 |
| 빌드 | Gradle 9.x (Groovy DSL), 단일 프로젝트 | Java 25 toolchain을 지원하는 버전. 원 프로젝트와 같은 DSL |
| 모듈 경계 | Spring Modulith 2.x | `verify()` 테스트, `@ApplicationModule`, `@NamedInterface` |
| 영속성 | Spring Data JPA (Hibernate 7, JPA 3.2) | 동적 검색은 Spring Data Specification 우선, 부족하면 QueryDSL(Hibernate 7 호환 포크) |
| JSON | Jackson 3 (Boot 4 기본) | 패키지가 `tools.jackson`으로 바뀜 (§1.1) |
| 마이그레이션 | Flyway | |
| DB | PostgreSQL 17 + pgvector | |
| 캐시·세션 | Redis (Spring Data Redis) | |
| 메시징 | Spring for Apache Kafka | |
| 검색 | Spring Data Elasticsearch | |
| AI | Spring AI (OpenAI) | |
| 스케줄 락 | Postgres 세션 advisory lock | 라이브러리 없이 직접 구현 (§10, 02-architecture §5.4) |
| API 문서 | springdoc-openapi | |
| HTTP 클라이언트 | Spring `RestClient` | 외부 API (Toss 등) |
| 테스트 | JUnit 5, AssertJ, Mockito, Testcontainers | 외부 API는 테스트 전용 가짜 컨트롤러 (§13.4) |
| 부하 테스트 | k6 | |
| 로그 | ELK (logstash-logback-encoder → Logstash → Elasticsearch → Kibana) | traceId는 필터 + MDC로 직접 전파 (§14) |
| 메트릭 | Micrometer(actuator 기본) → Prometheus → Grafana | |
| 보일러플레이트 | Lombok (제한적, §5.1) | |
| ID | `uuid-creator` (UUIDv7) | |
| 객체 저장소 | MinIO(로컬, S3 호환) + AWS SDK for Java v2 (`s3`) | 상품 이미지 presigned URL 업로드 (로드맵 1-10, 2026-10-02 승인) |

> **신규 학습 범위는 ELK, Prometheus/Grafana, k6로 한정한다.** Flyway, Spring Modulith(verify 테스트만), Testcontainers는 학습 부담이 거의 없어 포함한다. 그 외 도구(분산 트레이싱, ShedLock, WireMock, ArchUnit)는 이미 아는 방식으로 대체했다(ADR-010).

### 1.1 버전 정책과 Boot 4 / Java 25 주의점
**버전 정책**
- 오픈소스 Spring Boot에는 LTS가 없다. 마이너 버전마다 약 1년간 OSS 지원을 받으므로 **최신 GA 마이너**를 쓰고, 새 마이너가 나오면 Stage 경계에서 올린다.
- Java는 LTS인 25를 쓴다. Gradle toolchain으로 고정한다: `java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }`.

**Spring Boot 4 (Framework 7)에서 3.x와 달라진 점**

| 변경 | 영향 | 대응 |
|---|---|---|
| 자동 설정·스타터 모듈화 | 웹은 `spring-boot-starter-webmvc`로 바뀌는 등 스타터 이름이 바뀌고, `@DataJpaTest` 같은 테스트 슬라이스가 기술별 테스트 모듈로 이동 | 의존성은 start.spring.io로 생성해서 시작하고, 블로그의 3.x 예제를 그대로 복사하지 않는다 |
| Jackson 3 기본 | 패키지 `com.fasterxml.jackson` → `tools.jackson` (어노테이션은 기존 패키지 유지) | 이벤트 직렬화, Kafka serializer 설정, Redis 직렬화 설정을 Jackson 3 기준으로 작성 |
| Hibernate 7 / JPA 3.2 | 일부 deprecated API 제거 | Hibernate 6 시절 예제의 API가 남아 있는지 확인 |
| 코어 재시도 기능 | Framework 7에 `@Retryable`, `RetryTemplate`이 spring-core로 들어옴 | 외부 API 재시도에 별도 spring-retry 대신 우선 검토 |
| JSpecify null 안전성 | Spring API가 `@Nullable`/`@NonNull`을 JSpecify로 표기 | 공개 API(모듈 api 패키지)에 JSpecify 적용 검토 [선택] |

**서드파티 호환성은 착수 시 확인한다** (첫 티켓의 수용 기준): Spring AI, springdoc-openapi, Spring Modulith, Testcontainers(2.x는 모듈·패키지명 변경), QueryDSL 포크, logstash-logback-encoder. 호환 버전이 없는 라이브러리는 대안을 찾고 이 표에 기록한다.

**확정 버전** (1-1 기준, `./gradlew dependencies`로 확인. 새 라이브러리는 등장하는 단계에서 추가한다)

| 구성 | 버전 | 비고 |
|---|---|---|
| JDK (Gradle toolchain) | Temurin 25.0.4 | 시스템 기본 JDK와 무관하게 toolchain이 25를 사용. IntelliJ 프로젝트 SDK도 25로 맞춘다 |
| Gradle Wrapper | 9.7.1 | |
| Spring Boot | 4.1.1 | `io.spring.dependency-management` 1.1.7 |
| Spring Framework | 7.0.9 | |
| Hibernate ORM | 7.4.5.Final | |
| Flyway | 12.4.0 | `spring-boot-starter-flyway` + `flyway-database-postgresql` |
| PostgreSQL JDBC | 42.7.13 | DB 이미지 `pgvector/pgvector:pg17` |
| HikariCP | 7.0.2 | |
| Jackson | 3.1.5 (`tools.jackson`) | |
| Lombok | 1.18.46 | JDK 25 지원. 빌드 시 `sun.misc.Unsafe` 경고가 나오지만 동작에는 문제없음 |
| Testcontainers | 2.0.5 | 모듈명 `testcontainers-postgresql`, 패키지 `org.testcontainers.postgresql` |
| JUnit Jupiter | 6.0.3 | |
| Spring Modulith | (1-6에서 추가) | |
| jjwt | (1-4에서 추가) | |
| Spring AI, springdoc-openapi, spring-kafka, logstash-logback-encoder | (등장 단계에서 추가) | |

**Java 25에서 활용할 것**

| 기능 | 활용 |
|---|---|
| 가상 스레드 + `synchronized` 고정(pinning) 해소 (JEP 491, Java 24+) | Stage 2-1 가상 스레드 실험. Java 21 시절의 pinning 문제 없이 비교 가능 |
| Scoped Values (JEP 506, 정식) | 요청 컨텍스트(memberId 등) 전달 실험 [선택] |
| Compact Object Headers (JEP 519) | Stage 2 메모리·GC 실험 옵션 (`-XX:+UseCompactObjectHeaders`) |
| record 패턴, switch 패턴 매칭, sealed | `PgResult` 같은 결과 타입 분기 (§8.2) |

- Lombok은 JDK 25를 지원하는 최신 버전을 쓴다(구버전은 컴파일 실패).

## 2. 프로젝트 레이아웃
```
myroutine-v2/
├── build.gradle, settings.gradle
├── docker/                      # docker-compose, logstash·prometheus·grafana 설정
├── docs/                        # 이 문서들
├── k6/                          # 부하 테스트 스크립트
└── src/
    ├── main/
    │   ├── java/com/myroutine/
    │   │   ├── MyRoutineApplication.java
    │   │   ├── common/          # 공용 모듈 (OPEN)
    │   │   ├── member/
    │   │   ├── shop/
    │   │   ├── product/
    │   │   ├── order/
    │   │   ├── payment/
    │   │   ├── settlement/
    │   │   ├── review/
    │   │   ├── search/
    │   │   ├── recommendation/
    │   │   └── notification/
    │   └── resources/
    │       ├── application.yml, application-local.yml, application-test.yml
    │       └── db/migration/{module}/V202610021200__member_create_member.sql
    └── test/java/com/myroutine/{module}/...
```

**Flyway 파일명**: 하나의 이력 테이블을 공유하므로 모듈 간 버전 충돌을 피하기 위해 `V{yyyyMMddHHmm}__{module}_{설명}.sql`로 쓴다. 이미 적용된 파일은 절대 수정하지 않고 새 파일로 고친다.

## 3. 모듈 내부 구조 (DDD 4계층)

### 3.1 패키지
```
com.myroutine.order
├── package-info.java     # @ApplicationModule(allowedDependencies = ...)
├── api/                  # 다른 모듈에 공개: 인터페이스, DTO, 이벤트 타입
│   └── package-info.java # @NamedInterface("api")
├── web/                  # REST 컨트롤러, 요청/응답 DTO
├── application/          # 유스케이스(트랜잭션 경계), Command/Result
├── domain/               # 엔티티, 값 객체, 도메인 규칙, 리포지토리 인터페이스
└── infrastructure/       # JPA 구현, 외부 클라이언트, Kafka consumer, 스케줄러
```

### 3.2 계층별 책임과 의존 방향
```
web ──> application ──> domain <── infrastructure
              │
              └──> 다른 모듈의 api
```

| 계층 | 두는 것 | 의존 가능 | 금지 |
|---|---|---|---|
| domain | 엔티티, 값 객체, enum(상태·전이 규칙), 도메인 예외, 리포지토리 **인터페이스**, 도메인 서비스(여러 애그리거트에 걸친 순수 규칙) | Java, JPA 어노테이션 | Spring 빈 주입, HTTP, Kafka, 다른 모듈 |
| application | 유스케이스 서비스, Command/Result DTO, 다른 모듈 api 호출, outbox 발행, 소유권 검사, 트랜잭션 | domain, 다른 모듈 api, common | 컨트롤러 DTO, JPA 구현체 직접 사용 |
| web | 컨트롤러, Request/Response DTO, 검증 어노테이션 | application | domain 엔티티 반환, 리포지토리 호출 |
| infrastructure | Spring Data 구현, native 쿼리, 외부 API 클라이언트, Kafka listener, 스케줄러 | domain, application | 비즈니스 규칙 판단 |
| api | 다른 모듈용 인터페이스(구현은 application), 공개 DTO, 이벤트 record | common | 엔티티 노출 |

### 3.3 규칙이 거의 없는 모듈
notification, search처럼 비즈니스 규칙이 적은 모듈은 `domain`을 생략하고 `application` + `infrastructure`만 둬도 된다. 계층을 위한 계층은 만들지 않는다.

## 4. 모듈 간 통신

### 4.1 공개 범위 선언 (Spring Modulith)
```java
// src/main/java/com/myroutine/order/package-info.java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"common", "product::api", "payment::api", "shop::api", "member::api"}
)
package com.myroutine.order;

// src/main/java/com/myroutine/product/api/package-info.java
@org.springframework.modulith.NamedInterface("api")
package com.myroutine.product.api;

// src/main/java/com/myroutine/common/package-info.java
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.myroutine.common;
```
허용 의존은 [02-architecture-stage1.md §3](02-design/02-architecture-stage1.md)의 그래프와 같아야 한다. 그래프를 바꿀 때는 문서부터 고친다.

### 4.2 동기 호출: api 인터페이스
```java
// product/api/ProductApi.java — 다른 모듈이 보는 계약
public interface ProductApi {
    List<ProductForCheckout> getForCheckout(Collection<UUID> productIds);
    List<ProductForCheckout> getPurchasable(Collection<UUID> productIds);   // 판매 중이 아니면 예외
    void reserve(UUID orderId, List<ReserveItem> items, Instant expiresAt);   // 멱등: 같은 orderId 재호출 시 무시
    void commitReservation(UUID orderId);
    void releaseReservation(UUID orderId, ReleaseReason reason);
    void restore(UUID orderId, UUID productId, int quantity, UUID refundId);  // 멱등: refundId
}

// product/application/ProductApiImpl.java — 구현은 내부
@Service
@RequiredArgsConstructor
class ProductApiImpl implements ProductApi { ... }
```
- api 메서드의 쓰기 연산은 **비즈니스 키를 받아 멱등**하게 만든다(ADR-002). Stage 3에서 HTTP로 바뀌어도 재시도가 안전해야 한다.
- api DTO는 record로 만들고, 엔티티를 그대로 반환하지 않는다.

### 4.3 역방향이 필요할 때: 의존 역전
```java
// shop/api/ShopClosePrecondition.java — shop이 필요한 것을 정의
public interface ShopClosePrecondition {
    void check(UUID shopId);   // 위반 시 BusinessException
}

// order/application/OrderShopClosePrecondition.java — order가 구현 (order → shop::api 방향 유지)
@Component
class OrderShopClosePrecondition implements ShopClosePrecondition { ... }

// shop/application — 구현체를 모두 주입받아 실행
private final List<ShopClosePrecondition> preconditions;
```

### 4.4 비동기: 이벤트 (§9)
"상대 모듈이 즉시 성공해야 내 요청이 성공하는가?" → 예: 동기 api, 아니오: 이벤트.

## 5. 도메인 모델 작성법

### 5.1 엔티티
```java
@Entity
@Table(name = "order_line", schema = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)       // JPA 전용
public class OrderLine {

    @Id
    private UUID id;

    @Version
    private Long version;                                 // null이면 신규로 판단 → save()가 merge 대신 persist

    private UUID productId;
    private String productName;
    private Money unitPrice;
    private int quantity;
    private Money lineAmount;
    private Money refundedAmount;

    @Enumerated(EnumType.STRING)
    private OrderLineStatus status;

    static OrderLine create(UUID productId, String name, Money unitPrice, int quantity) {   // 정적 팩토리
        if (quantity <= 0) throw new InvalidOrderException("수량은 1 이상");
        OrderLine line = new OrderLine();
        line.id = Ids.newId();                            // UUIDv7
        ...
        line.status = OrderLineStatus.PENDING;
        return line;
    }

    Money cancel() {                                      // 의도를 드러내는 행위 메서드
        status = status.transitTo(OrderLineStatus.CANCELLED);
        return refundableAmount();
    }
}
```
규칙
- **setter 금지**. 상태 변경은 의도가 드러나는 메서드(`cancel()`, `ship()`, `confirm()`)로만 한다.
- 생성은 정적 팩토리로 하고, 불변식은 생성 시점에 검증한다.
- Lombok은 `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = PROTECTED)`, `@Builder`(테스트 픽스처·DTO 한정)만 쓴다. `@Data`, `@Setter`, 엔티티의 `@AllArgsConstructor`는 금지한다.
- ID는 앱에서 UUIDv7로 생성한다. 직접 할당한 ID에서 `save()`가 SELECT 후 merge하지 않도록 `@Version Long`(래퍼 타입)을 두거나 `Persistable`을 구현한다.
- 연관관계는 **같은 애그리거트 안에서만** 객체로 맺는다. 다른 애그리거트는 ID로 참조한다.

### 5.2 애그리거트
| 애그리거트 루트 | 포함 | 비고 |
|---|---|---|
| `Order` | `ShopOrder`, `OrderLine` | 품목 취소도 `order.cancelLine(lineId)`로. 가게주문 상태 재계산을 루트가 책임 |
| `Refund` | - | 주문과 별도 애그리거트 (독립적인 진행 상태) |
| `Subscription` | - | `SubscriptionCycle`은 별도 애그리거트 (회차가 계속 쌓임) |
| `Product` | `ProductImage` | `Stock`은 별도 (갱신 빈도·경합이 다름) |
| `Payment` | `PaymentCancel` | |

규칙
- 리포지토리는 **루트에만** 둔다. 자식 엔티티를 직접 조회·저장하지 않는다.
- 한 트랜잭션에서는 원칙적으로 애그리거트 하나만 변경한다. 예외는 문서화된 흐름(체크아웃, 환불 완료, 정산)뿐이다(ADR-002).
- 애그리거트 바깥으로 자식 엔티티의 변경 가능한 참조를 내보내지 않는다(조회용 DTO로 변환).

### 5.3 값 객체
```java
public record Money(long amount) implements Comparable<Money> {
    public static final Money ZERO = new Money(0);
    public Money {
        if (amount < 0) throw new IllegalArgumentException("금액은 음수가 될 수 없다");
    }
    public Money plus(Money o)  { return new Money(amount + o.amount); }
    public Money minus(Money o) { return new Money(amount - o.amount); }   // 음수면 생성자에서 예외
    public Money times(int q)   { return new Money(Math.multiplyExact(amount, q)); }
    ...
}

@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, Long> { ... }   // DB에는 bigint 한 컬럼
```
- 금액은 `long`/`BigDecimal`을 직접 들고 다니지 않고 `Money`를 쓴다.
- 배송지처럼 여러 필드로 된 값은 `@Embeddable` record 또는 jsonb 스냅샷으로 둔다.

### 5.4 상태와 전이 규칙
```java
public enum OrderLineStatus {
    PENDING, PAID, SHIPPED, DELIVERED, CONFIRMED, CANCELLED, RETURN_REQUESTED, RETURNED;

    private static final Map<OrderLineStatus, Set<OrderLineStatus>> ALLOWED = Map.of(
        PENDING,          EnumSet.of(PAID, CANCELLED),
        PAID,             EnumSet.of(SHIPPED, CANCELLED),
        SHIPPED,          EnumSet.of(DELIVERED),
        DELIVERED,        EnumSet.of(CONFIRMED, RETURN_REQUESTED),
        RETURN_REQUESTED, EnumSet.of(DELIVERED, RETURNED)
    );

    public OrderLineStatus transitTo(OrderLineStatus to) {
        if (!ALLOWED.getOrDefault(this, Set.of()).contains(to)) {
            throw new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION);
        }
        return to;
    }
}
```
- 전이표는 [04-state-machines.md](02-design/04-state-machines.md)와 1:1로 일치해야 한다. 상태머신 문서가 정답이고, 단위 테스트로 모든 허용·금지 전이를 검증한다.

### 5.5 시간과 시간대
**시각(언제 일어났나)과 업무 달력(몇 월 며칠인가)을 나눠서 다룬다.**

| 구분 | 예 | Java 타입 | 기준 |
|---|---|---|---|
| 시각 | `created_at`, `paid_at`, `expires_at`, 토큰 만료 | **`Instant`** | UTC. DB 컬럼은 `timestamptz` |
| 날짜·업무 규칙 | 구독 실행일, 정산 기간("전월"), "매일 06:00", "배송완료 후 7일" | `LocalDate`, `ZonedDateTime` | **업무 시간대 `Asia/Seoul`을 명시**해서 계산 |

규칙
- `application.yaml`(공통)에 `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`를 둔다. **local 프로필에만 두지 않는다** — 환경마다 시간이 다르게 저장되는 것을 막기 위해서다.
- 업무 시간대는 설정(`myroutine.business-zone: Asia/Seoul`)으로 받아 한 곳에서만 쓴다.
- 현재 시각은 **`Clock` 빈으로만** 얻는다(`Instant.now(clock)`). `LocalDateTime.now()`, `new Date()`처럼 JVM 기본 시간대에 기대는 코드는 금지한다. 테스트에서는 `Clock`을 바꿔 "15분 뒤", "7일 뒤"를 흉내 낸다.
- 엔티티의 시각 필드에 `LocalDateTime`을 쓰지 않는다. `timestamptz` 컬럼에 `LocalDateTime`을 넣으면 JDBC 세션 시간대로 해석되어 환경마다 저장값이 달라진다.
- 업무 날짜로 바꿀 때는 시간대를 명시한다: `instant.atZone(businessZone).toLocalDate()`.
- `@Scheduled`의 cron에는 `zone`을 명시한다: `@Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")`.
- API는 `Instant`를 ISO-8601 UTC(`2026-10-02T06:00:00Z`)로 내보낸다. 한국 시간 표시는 클라이언트가 한다.

### 5.6 도메인 예외
도메인 규칙 위반은 `BusinessException`(+ 모듈별 `ErrorCode`)으로 던진다. 도메인에서 HTTP 상태를 직접 다루지 않고, ErrorCode가 매핑을 가진다(§11).

## 6. 애플리케이션 서비스 작성법

### 6.1 기본 규칙
- **public 메서드 하나 = 유스케이스 하나 = 트랜잭션 하나.**
- 변경 유스케이스는 `@Transactional`, 조회는 `@Transactional(readOnly = true)`. readOnly는 Stage 2에서 replica 라우팅 기준이 되므로 정확히 붙인다(ADR-007).
- 반환은 엔티티가 아니라 `XxxResult` record다.
- **소유권 검사는 여기서** 한다: `orderRepository.findByIdAndMemberId(...)` (INV-11).
- 흐름이 큰 유스케이스(체크아웃, 결제 승인, 품목 취소)는 클래스 하나로 분리한다: `CheckoutService`, `ConfirmPaymentService`, `CancelOrderLineService`. 조회는 `OrderQueryService`로 모은다.
- 서비스는 흐름만 조율하고, 판단은 도메인 객체에 맡긴다. `if (line.getStatus() == ...)`가 서비스에 보이면 도메인으로 옮길 신호다.

### 6.2 외부 호출은 트랜잭션 밖에서 (NFR-REL-06)
```java
@Service
@RequiredArgsConstructor
public class ConfirmPaymentService {
    private final TransactionTemplate tx;
    private final TossClient toss;
    ...

    public ConfirmResult confirm(UUID orderId, UUID memberId, ConfirmCommand cmd) {
        // 1) 짧은 트랜잭션: 상태 선점 + payment(IN_PROGRESS) 저장 후 커밋
        PaymentAttempt attempt = tx.execute(s -> beginPayment(orderId, memberId, cmd));

        // 2) 트랜잭션 밖: 외부 호출
        TossResult result = toss.confirm(attempt.paymentId(), cmd);   // 타임아웃 설정 필수

        // 3) 짧은 트랜잭션: 결과 반영
        return tx.execute(s -> applyResult(attempt, result));
    }
}
```
- `@Transactional` 메서드 안에서 `RestClient`, 메일, OpenAI, MinIO(S3), ES를 호출하지 않는다.
- 같은 클래스의 `@Transactional` 메서드를 `this.method()`로 호출하면 프록시를 거치지 않아 트랜잭션이 적용되지 않는다. `TransactionTemplate`을 쓰거나 별도 빈으로 분리한다.
- 트랜잭션 전파는 기본(REQUIRED)만 쓴다. `REQUIRES_NEW`가 필요해 보이면 설계를 다시 보고, 쓴다면 이유를 주석으로 남긴다.

### 6.3 멱등 API
`@Idempotent` 어노테이션 + 인터셉터(common)로 `Idempotency-Key`를 처리한다. 서비스는 멱등키를 신경 쓰지 않되, **비즈니스 키 기반 멱등**(같은 orderId 재처리 무시 등)은 도메인·DB 제약으로 따로 보장한다.

## 7. 웹 계층 작성법
```java
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final CheckoutService checkoutService;

    @PostMapping("/checkout")
    @Idempotent
    public ResponseEntity<CheckoutResponse> checkout(@CurrentMember UUID memberId,
                                                     @Valid @RequestBody CheckoutRequest request) {
        CheckoutResult result = checkoutService.checkout(memberId, request.toCommand());
        return ResponseEntity.status(CREATED).body(CheckoutResponse.from(result));
    }
}
```
- 컨트롤러는 변환과 위임만 한다. 로직이 3줄을 넘으면 application으로 옮긴다.
- 입력 검증은 Bean Validation(`@NotNull`, `@Positive`, `@Size`)으로 하고, 비즈니스 검증은 도메인에서 한다.
- 요청자 ID는 `@CurrentMember`로만 받는다. 요청 바디나 헤더의 memberId를 신뢰하지 않는다(MEM-03).
- 응답에 엔티티를 쓰지 않는다. 날짜는 `Instant`(UTC)로 내보낸다.
- 클라이언트가 보낸 가격·금액은 받지 않거나, 받더라도 검증용으로만 쓴다(POL-17).

## 8. 인프라 계층 작성법

### 8.1 리포지토리
```java
// domain
public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findByIdAndMemberId(UUID id, UUID memberId);
    int transitStatus(UUID id, OrderStatus from, OrderStatus to);   // CAS 전이
}

// infrastructure — Spring Data 인터페이스가 도메인 인터페이스를 함께 구현
interface JpaOrderRepository extends JpaRepository<Order, UUID>, OrderRepository {
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = :to, o.version = o.version + 1 where o.id = :id and o.status = :from")
    int transitStatus(@Param("id") UUID id, @Param("from") OrderStatus from, @Param("to") OrderStatus to);
}
```
- enum은 문자열 리터럴로 비교하지 않고 파라미터로 바인딩한다.
- 벌크 UPDATE는 영속성 컨텍스트와 `@Version`을 우회한다. 같은 행을 엔티티로도 수정하는 흐름이면 version을 함께 올리고, `clearAutomatically`로 1차 캐시를 비운다.
- 조건부 UPDATE는 `@Modifying` + 반영된 행 수(`int`) 반환으로 만들고, 호출자가 0을 처리한다.
- N+1: 목록 조회에서 연관을 루프로 접근하지 않는다. fetch join, `@EntityGraph`, `default_batch_fetch_size`를 쓴다. 컬렉션 fetch join과 페이징을 같이 쓰지 않는다.
- 목록 페이징은 **keyset**(`WHERE (created_at, id) < (:c, :id) ORDER BY created_at DESC, id DESC LIMIT :n`)이 기본이다.
- 다른 모듈의 schema를 쿼리하지 않는다(ADR-002).

### 8.2 외부 API 클라이언트
- `RestClient`에 connect·read 타임아웃을 반드시 설정한다. 기본값(무한)을 쓰지 않는다.
- 응답은 **성공 / 명확한 실패 / 불확실** 세 가지로 분류해 반환한다(ADR-004).
  ```java
  sealed interface PgResult permits PgResult.Approved, PgResult.Rejected, PgResult.Unknown { ... }
  ```
- 모든 호출을 `pg_call_log`(결제) 또는 로그에 지연시간과 함께 남긴다.
- 호출 결과와 지연시간은 Micrometer `Timer`로 기록한다(`pg_call_seconds{operation, result}`).
- 테스트는 가짜 PG 서버로 한다(§13.4). 실제 Toss 테스트 키는 로컬 수동 확인용으로만 쓴다.

## 9. 이벤트 발행·소비

### 9.1 발행
```java
// application 서비스, 상태 변경과 같은 트랜잭션 안에서
outboxPublisher.publish(OrderTopics.ORDER_LINE_CONFIRMED, line.getId(),
        new OrderLineConfirmedEvent(line.getId(), order.getId(), shopId, ...));
```
- `KafkaTemplate`을 비즈니스 코드에서 직접 호출하지 않는다. 항상 Outbox를 거친다(ADR-003).
- 이벤트는 `api/event` 패키지의 record이고 이름은 과거형(`OrderLineConfirmedEvent`)이다. 필드 추가는 호환, 삭제·의미 변경은 새 버전 토픽으로 한다.
- 토픽명은 상수로 관리하고 [05-events.md](02-design/05-events.md)와 일치시킨다.

### 9.2 소비
```java
@Component
@RequiredArgsConstructor
class OrderLineConfirmedConsumer {
    private final InboxGuard inbox;                  // processed_message
    private final SettlementItemService service;

    @KafkaListener(topics = OrderTopics.ORDER_LINE_CONFIRMED, groupId = "settlement")
    public void on(EventEnvelope<OrderLineConfirmedEvent> envelope) {
        inbox.runOnce("settlement.order-line-confirmed", envelope.eventId(), () ->
            service.register(envelope.payload())     // 같은 트랜잭션에서 processed_message INSERT
        );
    }
}
```
- 모든 consumer는 멱등이어야 한다. `InboxGuard`(processed_message) 또는 비즈니스 unique 제약으로 보장한다.
- 재시도할 수 없는 실패(역직렬화 오류, 검증 실패)는 바로 DLT로 보낸다. 일시적 실패만 재시도한다.
- 이미 처리됐거나 무시해도 되는 이벤트(오래된 version 등)는 예외 없이 정상 종료한다.
- ack 모드는 공통 설정 하나로 통일한다(record 단위, 처리 성공 후 커밋). consumer마다 다르게 설정하지 않는다(SUP-02).

## 10. 동시성·정합성 패턴
| 상황 | 패턴 | 예 |
|---|---|---|
| 수량 차감·증감 | 조건부 UPDATE 한 문장 | 재고 예약 `WHERE available >= :q` |
| 상태 전이 경합 | CAS UPDATE (`WHERE status = :from`) | 주문 승인 시작 vs 만료 |
| 일반 수정 충돌 | `@Version` 낙관적 락 → 409 | 상품 수정, 구독 수정 |
| 잔액처럼 한 행에 연산 여러 개 | `SELECT ... FOR UPDATE` | 지갑 |
| 같은 회원의 연속 요청 직렬화 | `pg_advisory_xact_lock(hashtext(:key))` (트랜잭션 끝나면 해제) | 가게 개설·폐업 |
| 스케줄 작업 중복 실행 방지 | 전용 커넥션에서 `pg_try_advisory_lock` → 작업 → `pg_advisory_unlock` (세션 레벨) | 만료 잡, 대사 잡, Outbox 릴레이, 정산 |
| 중복 처리 방지 (최후 방어선) | UNIQUE 제약 + `INSERT ... ON CONFLICT DO NOTHING` | 재고 이력, 정산 item, processed_message |
| 여러 행을 잠글 때 | **항상 같은 순서**(ID 오름차순) | 여러 상품 예약 |

주의
- **UNIQUE 위반을 같은 트랜잭션 안에서 catch하고 계속 진행하지 않는다.** Postgres는 오류가 난 트랜잭션을 abort 상태로 만든다(SUP-05). 대신 `ON CONFLICT DO NOTHING`의 반영 행 수로 판단한다.
- 상태를 바꾸면서 같은 조건으로 offset 페이징하지 않는다(BAT-02). keyset 또는 대상 ID를 먼저 확정한다.
- 동시성 관련 코드는 반드시 동시성 테스트(§13.5)를 함께 작성한다.

## 11. 예외·에러 응답
```java
// common
public interface ErrorCode { String code(); HttpStatus status(); String message(); }
public class BusinessException extends RuntimeException { private final ErrorCode errorCode; private final Map<String, Object> details; ... }

// 모듈별
public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND(NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_EXPIRED(CONFLICT, "주문 유효시간이 지났습니다."),
    OUT_OF_STOCK(UNPROCESSABLE_ENTITY, "재고가 부족합니다.");
    ...
}
```
- `GlobalExceptionHandler`(common) 하나가 `{code, message, traceId, details}`로 변환한다. 컨트롤러에서 try-catch로 응답을 만들지 않는다.
- 4xx는 WARN 이하, 5xx는 ERROR로 스택트레이스와 함께 로그를 남긴다.
- 에러 코드는 [07-api-spec.md](02-design/07-api-spec.md)에 등록한다.
- 예외를 삼키지 않는다. `catch (Exception e) { log.error(...) }`로 끝내는 코드는 리뷰에서 반려한다(ORD-05).

**응답 규격 (`common.error.ErrorResponse`)**
```java
public record ErrorResponse(String code, String message, String traceId, Map<String, Object> details) {
    static ErrorResponse of(ErrorCode errorCode, Map<String, Object> details) { ... }   // traceId는 MDC에서
}
```
- `traceId`는 새로 만들지 않는다. `TraceIdFilter`가 MDC에 넣은 `traceId`를 꺼내 쓴다(§14.1). 그래야 응답 헤더 `X-Request-Id`와 같고 로그와도 같다.
- `details`는 없으면 빈 객체(`{}`)다. 검증 실패는 `{필드명: 메시지}`, 그 외는 코드별로 정한다(예: `OUT_OF_STOCK` → `{productIds: [...]}`).
- `message`는 사용자에게 보여줘도 되는 문구만 쓴다. SQL·클래스명·스택트레이스를 넣지 않는다.

**`GlobalExceptionHandler` 변환표**
| 예외 | HTTP | code | details | 로그 |
|---|---|---|---|---|
| `BusinessException` | `errorCode.status()` | `errorCode.code()` | `e.getDetails()` | WARN |
| `MethodArgumentNotValidException` (`@Valid` 실패) | 400 | `INVALID_REQUEST` | `{필드명: 메시지}` | WARN |
| `HandlerMethodValidationException` (쿼리 파라미터 검증 실패, 1-7) | 400 | `INVALID_REQUEST` | `{}` | WARN |
| `HttpMessageNotReadableException` (JSON 오류) | 400 | `INVALID_REQUEST` | `{}` | WARN |
| `NoResourceFoundException` | 404 | `NOT_FOUND` | `{}` | WARN |
| `HttpRequestMethodNotSupportedException` | 405 | `METHOD_NOT_ALLOWED` | `{}` | WARN |
| `OptimisticLockingFailureException` | 409 | `CONFLICT_RETRY` | `{}` | WARN |
| `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` | 400 | `INVALID_REQUEST` | `{파라미터명: 메시지}` | WARN |
| `DataIntegrityViolationException` + unique 위반(SQLState `23505`) | 409 | `DUPLICATE_RESOURCE` | 없음 | WARN |
| `DataIntegrityViolationException` + 그 외 | 500 | `INTERNAL_ERROR` | 없음 | ERROR + 스택트레이스 |
| 그 외 `Exception` | 500 | `INTERNAL_ERROR` | 없음 | ERROR + 스택트레이스 |

- 중복은 서비스가 먼저 조회해서 구체적인 코드(`MEMBER_EMAIL_DUPLICATED` 등)로 막는다. DB unique 제약까지 오는 것은 동시 요청 경합뿐이라 어떤 제약이든 409 `DUPLICATE_RESOURCE` 하나로 응답한다.
- 응답 `message`는 항상 `ErrorCode.message()`다. 예외 메시지를 응답에 넣지 않는다.
- 상태 전이 실패는 `new BusinessException(CommonErrorCode.INVALID_STATE_TRANSITION)`(409).
- 단계별 상세 규격은 [로드맵 1-3](03-roadmap/part1-foundation.md)이 기준이다.

**유틸 클래스**: `Ids`처럼 인스턴스를 만들지 않는 클래스는 `final` + private 생성자로 둔다.

## 12. 네이밍
| 대상 | 규칙 | 예 |
|---|---|---|
| 유스케이스 서비스 | `{동사}{명사}Service` / 조회 `{명사}QueryService` | `CheckoutService`, `OrderQueryService` |
| 모듈 공개 API | `{모듈}Api`, 구현 `{모듈}ApiImpl`(package-private) | `ProductApi` |
| DTO | web `XxxRequest`/`XxxResponse`, application `XxxCommand`/`XxxResult`, api `XxxInfo` | `CheckoutCommand` |
| 이벤트 | `{명사}{과거분사}Event` | `ShopClosedEvent` |
| 예외 | `BusinessException` + `{모듈}ErrorCode` | |
| 테이블·컬럼 | snake_case, 테이블은 단수(예약어면 복수 `orders`) | `order_line` |
| 제약·인덱스 | `pk_`, `uk_{table}_{cols}`, `ck_{table}_{rule}`, `idx_{table}_{cols}` | `uk_stock_movement_type_ref` |
| 테스트 메서드 | 한글 `@DisplayName` + 영문 메서드명 | `@DisplayName("배송 중인 품목은 취소할 수 없다")` |

## 13. 테스트

### 13.1 피라미드와 기준
| 종류 | 대상 | Spring 컨텍스트 | 비중 |
|---|---|---|---|
| 도메인 단위 | 엔티티·값 객체·상태 전이·금액 계산 | 없음 | 가장 많이 |
| 슬라이스 | 리포지토리 쿼리(`@DataJpaTest` + Testcontainers), 컨트롤러(`@WebMvcTest`) | 일부 | |
| 통합 | 유스케이스 흐름, Outbox/Inbox, Kafka | 전체 + Testcontainers | 핵심 흐름마다 |
| 시나리오 | Saga 정상·보상·중복·타임아웃 | 전체 + 가짜 PG 서버 | 티켓마다 필수 목록 |
| 동시성 | 초과판매·이중 차감·중복 결제 | 전체 | 동시성 코드마다 |
| 모듈 경계 | `ApplicationModules.verify()` | 없음 | 1개 |

### 13.2 도메인 단위 테스트
```java
@Test
@DisplayName("배송 중인 품목은 취소할 수 없다")
void cancel_shipped_line_fails() {
    OrderLine line = OrderLineFixture.shipped();

    assertThatThrownBy(line::cancel)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_STATE_TRANSITION);
}
```
- given-when-then 순서로 쓰고, 픽스처는 `XxxFixture`로 모은다.

### 13.3 통합 테스트 베이스
```java
@ActiveProfiles("test")
@SpringBootTest
public abstract class IntegrationTestSupport {
    @Autowired
    JdbcTemplate jdbc;

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg17");
    // Kafka·Redis는 해당 단계에서 필요해지는 단계에서 추가한다.
    // @ServiceConnection static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");   // org.testcontainers.kafka
    // @ServiceConnection(name = "redis") static final GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    static {   // 싱글턴: JVM 전체에서 한 번만 띄워 모든 테스트 클래스가 공유
        postgres.start();   // 컨테이너가 여러 개가 되면 Startables.deepStart(postgres, kafka, redis).join()
    }

    @AfterEach
    void truncateAllTables() {   // flyway_schema_history를 뺀 모든 스키마의 테이블을 비운다
        List<String> tables = jdbc.queryForList(
                "SELECT schemaname || '.' || tablename FROM pg_tables "
                        + "WHERE schemaname NOT IN ('pg_catalog', 'information_schema') "
                        + "AND tablename <> 'flyway_schema_history'",
                String.class);
        if (tables.isEmpty()) {
            return;   // 빈 목록으로 TRUNCATE를 실행하면 문법 오류
        }
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
    }
}
```
- `@ActiveProfiles("test")`가 없으면 `application-test.yaml`이 로드되지 않는다. datasource는 `@ServiceConnection`이 주입하므로 test 프로필에 적지 않는다.
- Testcontainers 2.x에서 Postgres 컨테이너는 `org.testcontainers.postgresql.PostgreSQLContainer`(제네릭 없음)를 쓴다. 구 `org.testcontainers.containers.PostgreSQLContainer<?>`는 deprecated다.
- Spring Initializr가 만든 `TestcontainersConfiguration`·`TestMyRoutineApplication`은 이 베이스와 역할이 겹쳐 삭제했다. 컨테이너 정의는 이 클래스 한 곳에만 둔다.
- 정리 쿼리는 스키마를 붙여 조회한다(`common` 등 `public`이 아닌 스키마가 있다). 테이블이 생기기 전에는 이 경로가 실행되지 않으므로, 첫 테이블이 생기는 단계(1-3)에서 실제로 비워지는지 확인한다.
- `@Testcontainers` + `@Container`를 쓰면 테스트 클래스마다 컨테이너가 재시작된다. 위처럼 static 블록에서 직접 시작하는 싱글턴 패턴으로 공유한다(속도). 종료는 Testcontainers의 Ryuk이 처리한다.
- 컨텍스트 캐시를 깨지 않도록 `@MockitoBean`(Boot 3.4+, 구 `@MockBean`) 남용을 피한다. 필요하면 테스트용 설정 클래스로 Fake 빈을 공통화한다.
- 통합 테스트에 `@Transactional`을 붙이지 않는다. 테스트가 끝나면 롤백돼서 **커밋 이후 동작(Outbox 발행, AFTER_COMMIT, 실제 락)**이 검증되지 않는다. 테이블 정리는 `@AfterEach`에서 TRUNCATE로 한다.

### 13.4 시나리오 테스트 (외부 장애 주입) — 가짜 PG 서버
외부 API(Toss)는 **JDK 내장 `com.sun.net.httpserver.HttpServer`로 만든 가짜 서버**로 대체한다. 라이브러리가 필요 없고, 실제 HTTP 호출이라 타임아웃·직렬화·헤더까지 검증된다.

```java
// src/test/java/com/myroutine/support/FakePgServer.java
public class FakePgServer {
    private final HttpServer server;
    private final Map<String, Behavior> behaviors = new ConcurrentHashMap<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    public static FakePgServer start() {
        FakePgServer fake = new FakePgServer(HttpServer.create(new InetSocketAddress(0), 0));  // 빈 포트 자동 할당
        fake.server.createContext("/", fake::handle);
        fake.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        fake.server.start();
        return fake;
    }

    public String baseUrl() { return "http://localhost:" + server.getAddress().getPort(); }
    public void on(String path, Behavior behavior) { behaviors.put(path, behavior); }
    public List<RecordedRequest> requests(String path) { ... }
    public void reset() { behaviors.clear(); requests.clear(); }

    private void handle(HttpExchange ex) throws IOException {
        requests.add(RecordedRequest.from(ex));                       // 헤더·바디 기록 (멱등키 검증용)
        Behavior b = behaviors.getOrDefault(matchPath(ex), Behavior.approve());
        sleep(b.delayMillis());                                       // 지연 → 클라이언트 read timeout 유발
        if (b.dropConnection()) { ex.close(); return; }               // 응답 없이 연결 종료
        byte[] body = b.body().getBytes(UTF_8);
        ex.sendResponseHeaders(b.status(), body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }
}

public record Behavior(int status, String body, long delayMillis, boolean dropConnection) {
    public static Behavior approve()        { return new Behavior(200, TossJson.DONE, 0, false); }
    public static Behavior reject(String c) { return new Behavior(400, TossJson.error(c), 0, false); }
    public static Behavior serverError()    { return new Behavior(500, "{}", 0, false); }
    public static Behavior delay(long ms)   { return new Behavior(200, TossJson.DONE, ms, false); }
    public static Behavior drop()           { return new Behavior(0, "", 0, true); }
}
```

```java
// 통합 테스트 베이스에 추가
static final FakePgServer pg = FakePgServer.start();

@DynamicPropertySource
static void pgProperties(DynamicPropertyRegistry registry) {
    registry.add("toss.base-url", pg::baseUrl);
    registry.add("toss.read-timeout", () -> "500ms");     // 테스트에서는 짧게
}
```

```java
@Test
@DisplayName("PG 승인 응답이 타임아웃이면 결제는 UNKNOWN, 주문은 만료되지 않고, 대사 후 PAID가 된다")
void confirm_timeout_then_reconcile() {
    pg.on("/v1/payments/confirm", Behavior.delay(1_000));              // read-timeout 500ms 초과
    ConfirmResult result = confirm(orderId);
    assertThat(result.status()).isEqualTo(PAYMENT_IN_PROGRESS);

    pg.on("/v1/payments/orders/*", Behavior.approve());                // 대사 시 조회하면 승인 상태
    reconcileJob.run();

    assertThat(orderStatus(orderId)).isEqualTo(PAID);
    assertThat(pg.requests("/v1/payments/confirm"))
        .allSatisfy(r -> assertThat(r.header("Idempotency-Key")).isNotBlank());
}
```
- 같은 `FakePgServer`에 `main` 메서드를 붙여 Docker로 띄우면 부하 테스트용 `pg-fake`가 된다(지연·실패 비율을 환경변수로 설정).
- Mockito로 `TossClient`를 mock하는 방식은 HTTP 계층(타임아웃 설정, JSON, 헤더)을 건너뛰므로 결제 시나리오 테스트에는 쓰지 않는다. 클라이언트를 쓰는 서비스의 단순 단위 테스트에는 써도 된다.

### 13.5 동시성 테스트
```java
@Test
@DisplayName("재고 100개 상품에 1,000명이 동시에 주문하면 정확히 100건만 성공한다")
void no_oversell() throws Exception {
    int threads = 1000;
    ExecutorService pool = Executors.newFixedThreadPool(64);
    CountDownLatch ready = new CountDownLatch(threads), start = new CountDownLatch(1), done = new CountDownLatch(threads);
    AtomicInteger success = new AtomicInteger();

    for (int i = 0; i < threads; i++) {
        pool.submit(() -> {
            ready.countDown();
            try { start.await(); checkout(...); success.incrementAndGet(); }
            catch (Exception ignored) {}
            finally { done.countDown(); }
        });
    }
    start.countDown();
    done.await(60, SECONDS);

    assertThat(success.get()).isEqualTo(100);
    assertThat(stock()).satisfies(s -> assertThat(s.available() + s.reserved() + s.sold()).isEqualTo(s.received()));  // INV-03
}
```
- 성공 건수뿐 아니라 **불변식(INV)을 함께 검증**한다.

### 13.6 필수 테스트
- 모든 상태 enum: 허용·금지 전이 전체 (파라미터화 테스트)
- 모든 금액 계산: 경계값(최소 금액, 부분 환불 누적, 누적 취소 = 승인액)
- 모든 consumer: 같은 이벤트 2번 → 결과 1번
- 모든 변경 API: 다른 회원이 호출 → 403/404 (INV-11)
- 모듈 경계 verify 테스트

## 14. 로깅·관측

### 14.1 traceId 전파 (ADR-010)
```java
// common.web: 요청마다 traceId를 만들어 MDC에 넣는다 (TraceIds도 같은 패키지)
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String traceId = TraceIds.newId();   // 항상 서버가 만든다 (클라이언트 헤더를 이어 쓰는 것은 로드맵 1-3의 제안)
        MDC.put(TraceIds.MDC_KEY, traceId);
        res.setHeader(TraceIds.HEADER, traceId);
        try { chain.doFilter(req, res); }
        finally { MDC.remove(TraceIds.MDC_KEY); }
    }
}
```

| 경로 | 전파 방법 |
|---|---|
| HTTP 요청 | `TraceIdFilter`가 생성 → MDC |
| Outbox 저장 | `OutboxPublisher`가 MDC의 traceId를 `outbox_event.trace_id`에 저장 → 봉투의 `traceId` |
| Kafka 소비 | consumer 공통 래퍼(`InboxGuard`)가 처리 전에 봉투의 traceId를 MDC에 넣고, 끝나면 지움 |
| 스케줄 작업 | 실행마다 새 traceId 생성 (`job=expireOrders` 같은 필드도 함께) |
| `@Async`·스레드 풀 | `TaskDecorator`로 MDC 복사 |

Kibana에서 `traceId:"..."`로 검색하면 한 요청의 HTTP → DB → Kafka → 알림 로그가 시간순으로 보인다. 에러 응답의 `traceId`(§11)로 바로 검색할 수 있다.

### 14.2 로그 규칙
- 로그는 JSON이고 MDC에 `traceId, memberId`를 둔다. 주문 관련 흐름은 `orderId`도 넣는다.
- INFO로 남길 것: 상태 전이(`order {} PENDING_PAYMENT -> PAID`), 외부 호출 결과와 지연시간, 스케줄 작업 처리 건수.
- 남기지 말 것: 토큰, 카드·계좌 번호, 이메일 인증코드, 요청 바디 전체.
- 비즈니스 메트릭 이름은 `{도메인}_{대상}_{단위}`로 하고 [ADR-010](adr/ADR-010-observability.md)의 목록을 따른다. 새 메트릭은 그 목록에 추가한다.

## 15. 설정·보안
- 프로필: `local`(docker-compose), `test`(Testcontainers), `prod`(Proxmox VM 운영 환경 연습, ADR-011).
- 시크릿(JWT 키, Toss 시크릿 키, OAuth 시크릿, OpenAI·AWS 키)은 환경변수로만 받는다. `.env`는 gitignore하고 `.env.example`에 키 이름만 둔다(MEM-01).
- 설정값은 `@ConfigurationProperties` + `@Validated` record로 묶는다. `@Value` 문자열을 여기저기 흩뿌리지 않는다.
- CORS 허용 origin은 설정으로 명시한다. `*`를 쓰지 않는다(MEM-04).
- actuator는 별도 관리 포트로 열고 외부에 노출하지 않는다(MEM-05). 운영 compose에서도 관리 포트(8081)는 호스트에 publish하지 않는다.
- 배포(ADR-011): 이미지는 **커밋 sha 태그**로만 배포한다(`latest` 금지). 운영 `.env`는 VM에만 두고 리포·GitHub Secrets·이미지에 넣지 않는다. `self-hosted` 러너를 쓰는 job은 `pull_request` 이벤트에서 실행되지 않게 한다(public 리포). 외부에서 달라지는 주소(CORS 오리진, 저장소 endpoint·공개 URL)는 환경변수로 받는다.

## 16. Git·PR
> 상세 규칙은 [Git 정책](git-policy.md)이 기준이다. 아래는 요약.

- 브랜치: Git Flow — `main`(릴리스), `develop`(통합, 기본 브랜치), `feature/{단계ID}-{요약}` (예: `feature/2-2-checkout`). feature → develop은 squash merge.
- 커밋 메시지: `{type}: {무엇을} ({왜})`, type은 `feat, fix, refactor, test, docs, chore`. 관련 없는 변경을 한 커밋에 섞지 않는다.
- PR은 티켓 하나 단위로 올리고, 본문에 다음을 쓴다.
  1. 티켓 ID와 수용 기준 체크
  2. 변경 요약과 설계 문서와 다르게 구현한 부분(있으면 이유)
  3. 테스트 결과(실행한 테스트, 필수 시나리오 체크)
  4. 리뷰어가 특히 봐줬으면 하는 곳
- **리뷰 요청**: "2-2 리뷰해줘"와 함께 브랜치명(`feature/2-2-checkout`) 또는 PR 번호를 알려준다. §18 체크리스트로 리뷰하고, 지적 사항과 질문을 심각도순으로 돌려준다.

## 17. 모듈 하나를 만드는 순서
1. 티켓의 수용 기준과 관련 설계(ERD, 상태머신, 시퀀스, API)를 다시 읽는다.
2. Flyway 마이그레이션 작성 (테이블, 제약, 인덱스)
3. 도메인 모델 + 단위 테스트 (상태 전이, 불변식) ← **여기서 가장 많은 시간을 쓴다**
4. 리포지토리 + 슬라이스 테스트 (커스텀 쿼리, 조건부 UPDATE)
5. 애플리케이션 서비스 + 통합 테스트 (트랜잭션 경계, 다른 모듈 api 연동)
6. 컨트롤러 + API 테스트 (검증, 인가, 에러 응답)
7. 이벤트 발행·소비 + 멱등 테스트
8. 동시성·시나리오 테스트 (해당 티켓의 필수 목록)
9. 설계와 달라진 부분을 PR 본문에 정리 (문서 갱신 — 설계, 에러 코드, 메트릭, README — 은 리뷰 때 Claude가 한다)
10. PR → 리뷰 → 반영 → 머지

## 18. 리뷰 체크리스트
리뷰는 이 순서로 보고, 위쪽 항목일수록 심각도가 높다.

| # | 관점 | 확인 질문 |
|---|---|---|
| 1 | 정합성 | 관련 불변식(INV)이 어떤 장치로 지켜지는가? 깨지는 경로는 없는가? |
| 2 | 트랜잭션 경계 | 트랜잭션 안에 외부 호출이 있는가? 커밋 실패 시 이미 일어난 부작용은? |
| 3 | 동시성 | 같은 자원에 동시 요청이 오면? 락 순서, CAS 반영 행 수 처리는? |
| 4 | 멱등성 | 같은 요청·이벤트가 두 번 오면? 재시도는 안전한가? |
| 5 | 실패 시나리오 | 외부 시스템이 타임아웃·실패·불확실이면? 앱이 중간에 죽으면 어디서 이어지는가? |
| 6 | 보안·인가 | 다른 회원의 리소스에 접근할 수 있는가? 클라이언트 값을 신뢰하는가? |
| 7 | 모듈 경계 | 다른 모듈 내부를 참조하는가? 의존 방향이 그래프와 같은가? |
| 8 | 도메인 모델 | 규칙이 서비스에 새어 나왔는가? setter, 빈약한 엔티티는 없는가? |
| 9 | 테스트 | 필수 시나리오가 있는가? 테스트가 실제로 실패할 수 있는가(의미 있는 단언)? |
| 10 | 성능 | N+1, 인덱스 없는 조회, offset 페이징, 불필요한 대량 로딩은? |
| 11 | 관측 | 장애가 나면 로그·메트릭·트레이스로 원인을 찾을 수 있는가? |
| 12 | 가독성 | 이름이 의도를 드러내는가? 불필요한 주석·죽은 코드는? |

## 19. As-Is에서 배운 금지 패턴
| 금지 | 대신 | 원 결함 |
|---|---|---|
| 클라이언트가 보낸 가격으로 결제 | 서버에서 가격 확정 | ORD-01 |
| `@Transactional` 안에서 원격·외부 호출 | 트랜잭션 분리 (§6.2) | ORD-02 |
| `jakarta.transaction.Transactional` 사용 | `org.springframework.transaction.annotation.Transactional` | ORD-03 |
| 주문 단위 금액으로 품목 환불 | 품목 금액 + 누적 환불액 관리 | ORD-04 |
| 실패를 로그만 남기고 진행 | 재시도 가능한 상태로 남기거나 예외 전파 | ORD-05 |
| 상태를 setter로 변경 | 전이 규칙을 가진 도메인 메서드 | ORD-06 |
| `findById`만으로 변경 | `findByIdAndMemberId` 소유권 조회 | ORD-08 |
| AFTER_COMMIT fire-and-forget 발행 | Outbox | ORD-09, SHOP-05 |
| 타임아웃을 실패로 단정 | UNKNOWN + 대사 | PAY-01, ORD-10 |
| 선택 후 트랜잭션 종료 → 그다음 처리 | 상태값 선점(PROCESSING + 리스) | PAY-03 |
| 중복 요청에 unique 예외를 그대로 반환 | 기존 결과를 멱등 응답 (`@Idempotent`, 비즈니스 키) | WAL-01 |
| DB 락을 잡은 채 외부 송금 | 트랜잭션 밖에서 멱등 키로 호출 → 결과 기록 (정산 지급) | WAL-02 |
| `final` 없는 필드 + `@RequiredArgsConstructor` | 의존성 필드는 모두 `private final` | SUP-01 |
| consumer마다 다른 ack 설정 | 공통 설정 하나 | SUP-02 |
| 트랜잭션 안에서 unique 위반 catch 후 계속 | `ON CONFLICT DO NOTHING` | SUP-05 |
| 상태를 바꾸며 같은 조건으로 offset 페이징 | keyset 또는 대상 확정 후 처리 | BAT-02 |
| 다른 모듈 schema 직접 조회 | 모듈 api 또는 이벤트 | BAT-03 |
| 테스트 없이 머지, CI가 테스트를 건너뜀 | CI에서 전체 테스트 필수 | INF-01 |
| 시크릿 커밋 | 환경변수 + `.env.example` | MEM-01 |
