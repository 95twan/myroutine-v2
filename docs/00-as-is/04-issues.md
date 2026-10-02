# 04. 결함·리스크 목록 (As-Is)

심각도
- **C (Critical)**: 돈·재고 정합성이 깨지거나 보안 침해 가능
- **H (High)**: 특정 장애/동시성 상황에서 정합성 붕괴 또는 기능 마비
- **M (Medium)**: 운영 리스크, 확장성·유지보수 문제
- **L (Low)**: 코드 품질, 정리 필요

검증: ✅ 코드 직접 확인 / 🔎 탐색으로 식별(경로 기재, 착수 전 재확인 권장)

경로 접두어 생략: `order/` = `order-service/src/main/java/com/node5/orderservice/` 등

---

## 주문 (order)
| ID | 심각도 | 결함 | 근거 | 검증 | v2 방향 |
|---|---|---|---|---|---|
| ORD-01 | C | 주문 단가·총액을 **클라이언트 요청값 그대로 사용**. 서버가 catalog 가격과 대조하지 않음 → 임의 가격 결제 가능 | `order/order/presentation/dto/OrderCreateRequest.java:31-33`, `OrderService#create` | ✅ | 서버가 catalog에서 가격·판매상태·가게 조회 후 스냅샷 확정 |
| ORD-02 | C | `create` 전체가 하나의 DB 트랜잭션이고 그 안에서 hold·withdraw·commit 원격 호출. 원격 성공 후 로컬 커밋 실패(예: subscriptionKey unique 위반이 flush 시점에 발생) 시 **돈은 빠지고 주문은 없음** | `OrderService#create` | ✅ | 로컬 tx는 짧게, 원격 호출은 tx 밖. 주문을 먼저 PENDING으로 커밋 후 Saga 진행 |
| ORD-03 | H | 결제 실패 시 `PAYMENT_FAILED` 기록이 `jakarta.transaction.Transactional`(REQUIRED)로 외부 tx에 합류 → 이후 예외로 함께 롤백, 실패 이력이 남지 않음 | `order/order/application/OrderTransactionService.java:9,33` | ✅ | 상태 전이를 Saga 단계별 독립 커밋으로 |
| ORD-04 | C | 품목 단위 환불인데 **주문 총액을 환불** 요청. wallet은 출금 로그를 1회만 REFUNDED 처리 → 두 번째 품목은 환불 불가 | `OrderService#refund`, `wallet/.../WalletService.java:153-167` | ✅ | 품목(OrderLine) 단위 금액 환불, 환불 원장 기록 |
| ORD-05 | H | 재고 commit/release 실패를 **로그만 남기고 무시** → 예약 HELD 영구 잔존(재고 증발) | `OrderService#commitStock/releaseStock` | ✅ | 재시도 가능한 Saga 단계 + 예약 TTL(CAT-01) |
| ORD-06 | H | 취소 가능 조건을 `Order.status == PAID`만 확인. Order 상태는 PAID 이후 전진하지 않으므로 **배송 중 품목도 전액 취소 가능** | `OrderService#cancel` | ✅ | 주문/품목 상태머신 정의, 품목 상태 기준 판단 |
| ORD-07 | M | `OrderScheduler`가 3분마다 **모든 품목** 진행상태 일괄 전진(시간 조건 없음, 데모용), 다중 인스턴스 락 없음 → 정산 적재 중복 | `order/order/application/OrderScheduler.java`, `OrderItemJpaRepository#updateStatusByCreatedAtBefore` | ✅ | 판매자 API로 배송 상태 변경 + 자동 구매확정(N일) 배치, ShedLock |
| ORD-08 | H | 구독 조회·수정·일시정지·재개·취소가 `findById`만으로 처리 — **소유자 검증 없음(IDOR)** | `order/subscription/application/SubscriptionService.java:45,97,134,146,158` | ✅ | memberId 조건 조회, 인가 테스트 |
| ORD-09 | M | stock-restore를 AFTER_COMMIT으로 fire-and-forget → 발행 실패 시 재고 영구 미복구 | `OrderService#cancel/refund` | ✅ | Outbox |
| ORD-10 | H | wallet 출금이 **타임아웃이지만 실제로는 성공**한 경우, 주문은 실패 처리·재고 release → 돈만 빠짐. 재시도해도 WAL-01 때문에 실패 | `OrderService#create` | ✅ | 결제 결과 불확실(UNKNOWN) 상태 + 멱등 재조회/재시도 |
| ORD-11 | M | `order` 테이블 DDL에 **PK 제약 없음**, `order_item`에 shopId 없음 → 가게별 주문 조회 불가 | `docs/ddl/order-ddl.sql:3-36` | ✅ | PK/FK/인덱스 정비, 품목에 shopId 스냅샷 |

## 재고·상품 (catalog)
| ID | 심각도 | 결함 | 근거 | 검증 | v2 방향 |
|---|---|---|---|---|---|
| CAT-01 | H | 재고 예약 **만료 없음** (`@Scheduled` 전무). 주문 서비스가 hold 후 죽으면 재고 영구 점유 | `catalog/inventory/` | ✅ | `expires_at` + 만료 스윕 잡, 만료 시 release |
| CAT-02 | M | hold 시 상품 `ON_SALE` 여부 미검증 → 단종 상품 주문 가능 | `InventoryReservationService` | 🔎 | hold 조건에 판매상태 포함 (or 주문 시 검증) |
| CAT-03 | M | 상품 이벤트를 `@Transactional` 내부에서 직접 발행 → 롤백돼도 이벤트 발행(유령 이벤트) | `catalog/product/application/ProductService.java` | 🔎 | Outbox |
| CAT-04 | M | 상품 생성 실패 시 `markIdempotencyFailed`가 롤백 중인 tx 안에서 실행 → FAILED 기록 유실. 상품은 생성됐는데 재고 생성 실패 시 중복 생성 위험(본인 메모 must.md #6) | `ProductService` | 🔎 | 상품+재고를 한 tx로 생성, 멱등키 결과 별도 tx |
| CAT-05 | M | 가게 삭제 시 상품 일괄 단종이 bulk update로만 처리 → 검색 인덱스·임베딩 이벤트 미발행 | `ProductDiscontinueService#discontinueByShopId` | 🔎 | 단종 이벤트 발행 (Outbox) |
| CAT-06 | M | 판매자 재고 수정이 절대값 덮어쓰기(`@Version` 없음) → 동시 hold와 경합 시 차감 유실 | `Stock#updateQuantity` | 🔎 | 증감(delta) 방식 조건부 UPDATE (must.md #7) |

✔ 유지할 점: `decreaseIfEnough` 원자적 조건부 UPDATE, 예약 상태 CAS 전이, `processed_event` 멱등 소비, 동시성 테스트.

## 결제 (payment)
| ID | 심각도 | 결함 | 근거 | 검증 | v2 방향 |
|---|---|---|---|---|---|
| PAY-01 | C | Toss confirm 호출에서 **모든 예외(타임아웃 포함)를 실패로 처리**. 승인됐는데 실패 처리되거나, 승인 후 로컬 처리 실패 시 지갑 미충전. 대사(reconciliation) 없음, Toss 멱등키 미사용 | `payment/payment/application/PaymentFacade.java:121-128` | ✅ | UNKNOWN 상태 + Toss 조회 API 대사 잡 + Idempotency-Key |
| PAY-02 | H | confirm 후 Redis 키 미삭제, `payment.order_id` unique 아님 → 중복 confirm 시 PENDING 중복 행 | `PaymentConfirmService`, `docs/ddl/payment-ddl.sql` | 🔎 | orderId unique + 상태 기반 멱등 응답 |
| PAY-03 | M | Outbox: `getBatchForUpdate` tx가 끝난 뒤 발행 → **락이 발행 전에 해제**, 다중 인스턴스 중복 발행. 실패 행 1초마다 재시도(백오프 없음) | `payment/payment/batch/PaymentOutboxScheduler.java` | ✅ | 공통 Outbox 모듈(PROCESSING 선점 + 리스 만료 + 지수 백오프) |
| PAY-04 | M | 충전 취소: 지갑 차감 후 Toss 취소 거절 → MANUAL 처리만, 자동 재적립 없음 | `PaymentFacade#cancel` | 🔎 | 순서 재설계(Toss 취소 성공 후 차감 or 보상) |
| PAY-05 | L | 존재하지 않는 wallet 엔드포인트를 가리키는 Feign 메서드 잔존 | `payment/payment/client/` | 🔎 | 삭제 |

## 지갑 (wallet)
| ID | 심각도 | 결함 | 근거 | 검증 | v2 방향 |
|---|---|---|---|---|---|
| WAL-01 | H | 출금이 멱등하지 않음: 같은 orderId 재요청 시 unique 위반 **예외** → 호출자는 실패로 인식(실제론 이미 출금됨) | `wallet/wallet/application/WalletService.java:122-148` | ✅ | 기존 로그 있으면 동일 결과 반환(멱등 성공) |
| WAL-02 | H | 송금 시 **DB 락을 잡은 채 외부 은행 호출** → 송금 성공 후 커밋 실패 시 미차감 송금 | `WalletService#transferWallet` | 🔎 | 차감(보류) 커밋 → 외부 호출 → 확정/보상 |
| WAL-03 | H | 충전 취소(`withdrawRequest`)가 업데이트 건수·금액 미검증 → 멱등성/정합성 없음 | `WalletService` | 🔎 | 충전 건 단위 상태 전이 + 금액 검증 |
| WAL-04 | M | soft-delete 지갑 사용 가능, 금액 양수 검증 없음 | `Wallet` | 🔎 | 도메인 불변식 |
| WAL-05 | M | 잔액 컬럼 직접 증감 + 용도별 로그 4종 분산 → 잔액 = Σ거래 검증 불가 | `docs/ddl/wallet-ddl.sql` | ✅ | 단일 원장(ledger) + 잔액 스냅샷, 정합성 점검 쿼리 |

## 가게 (shop) — 본인 구현
| ID | 심각도 | 결함 | 근거 | 검증 | v2 방향 |
|---|---|---|---|---|---|
| SHOP-01 | H | shop측 completed/failed/dead consumer 실패 → `*.dlt`로 가지만 **소비자 없음** → Saga가 REQUESTED로 영구 정체, 알림 없음 | `shop/config/KafkaRetryConfig.java` | 🔎 | DLT 소비 + 정체 Saga 탐지 잡 + 알림 |
| SHOP-02 | M | FAILED/DEAD 삭제가 이후 요청을 영구 차단(재시도 경로 없음), PROCESSING outbox 회수 없음 | `ShopService#deleteMyShop`, `shop_delete_saga_design.md` | 🔎 | 재시도 API / 리스 만료 회수 |
| SHOP-03 | M | "마지막 가게" 판정 카운트에 등록 FAILED/DEAD/REQUESTED 가게까지 포함 → SELLER 권한 미회수 가능 | `ShopJpaRepository#countByMemberIdAndDeletedAtIsNullAndIdNot` | 🔎 | 등록 COMPLETED 가게만 카운트 |
| SHOP-04 | M | 회원 탈퇴 경로(`deleteAllMyShop`)는 락·ShopDeletion·shop-deleted 이벤트 없이 처리 | `ShopService` | 🔎 | 동일 Saga 경로로 통합 |
| SHOP-05 | M | shop-deleted를 AFTER_COMMIT fire-and-forget | `ShopDeletedProducer` | 🔎 | Outbox |
| SHOP-06 | L | 다중 인스턴스 SKIP LOCKED 시 같은 memberId 이벤트 순서 역전 가능, completed(outbox)와 failed(직접) 경로 불일치 | `ShopOutboxScheduler`, member consumer | 🔎 | 키 단위 순차 발행 or 순서 무관 설계(상태 버전) |

## 회원·인증·게이트웨이
| ID | 심각도 | 결함 | 근거 | 검증 | v2 방향 |
|---|---|---|---|---|---|
| MEM-01 | C | **JWT HMAC 서명 키가 리포에 커밋** → 토큰 위조 가능 | `config/config/application.yml:2` | ✅ | 환경변수/Secret, 키 로테이션, 비대칭키 검토 |
| MEM-02 | H | 이메일 인증 코드(6자리) 확인에 **시도 횟수 제한 없음** | `AuthService` email verify | 🔎 | 시도 제한·락아웃 (optional.md #3,#4) |
| MEM-03 | H | 클라이언트가 보낸 `Member-Id` 헤더를 게이트웨이가 제거하지 않음(permitAll 경로), compose에서 서비스 포트 직접 노출 → 게이트웨이 우회 | `apigateway/.../filter/AuthenticationFilter.java`, `docker-compose.yaml` | 🔎 | 헤더 strip + 내부망 격리 + 내부 호출 인증(optional.md #1) |
| MEM-04 | M | CORS `*` + `allowCredentials=true` | `apigateway/.../config/WebFluxSecurityConfig.java` | 🔎 | 허용 origin 명시 |
| MEM-05 | M | 모든 서비스 actuator 게이트웨이 경유 공개 | `WebFluxSecurityConfig` | 🔎 | 관리 포트 분리 |
| MEM-06 | L | 회원당 refresh 토큰 1개 → 다른 기기 로그인 시 기존 기기 로그아웃 | `AuthService#refreshToken` | 🔎 | 디바이스(세션) 단위 refresh + 재사용 탐지 |
| MEM-07 | M | 회원 탈퇴 시 tx 안에서 Feign 3회 + Redis 삭제 | `member/member/application/MemberService.java#deleteMember` | 🔎 | 탈퇴 Saga(요청→검증→확정) |
| MEM-08 | M | 매 요청 게이트웨이→member 동기 authorize → member SPOF, 지연 추가 (캐시로 +16% 개선 실험 있음) | `apigateway/.../filter/AuthorizationFilter.java` | ✅ | ADR: 인가 위치 재결정 |
| MEM-09 | L | Eureka `admin/admin` 하드코딩 | `discovery/src/main/resources/application.yaml` | 🔎 | 제거 or Secret |

## 지원 (support)
| ID | 심각도 | 결함 | 근거 | 검증 | v2 방향 |
|---|---|---|---|---|---|
| SUP-01 | H | `ReviewService reviewService;`가 final이 아니라 `@RequiredArgsConstructor` 주입 안 됨 → **모든 메시지 NPE** | `support/review/infrastructure/kafka/consumer/ProductDiscontinuedEventCunsumer.java:16` | ✅ | 테스트로 잡혔어야 할 결함 (INF-01) |
| SUP-02 | H | manual ack 모드인데 `Acknowledgment` 미사용 → 오프셋 미커밋, 재시작 시 재처리 폭주. 토픽명 하드코딩 | `support/search/infrastructure/kafka/KafkaProductIndexEventConsumer.java:28` | 🔎 | ack 정책 통일, 설정값 사용 |
| SUP-03 | M | RabbitMQ DLQ 없음 + requeue false → 실패 메일 유실 | `support/notification/` | 🔎 | DLX/DLQ + 재시도 |
| SUP-04 | M | ES 전체 재색인이 전체 삭제 후 적재 → 재색인 중 검색 결과 없음 | `support/search/application/reindex/ProductReindexService.java` | 🔎 | 새 인덱스 생성 → alias swap |
| SUP-05 | M | `@Transactional` 내부에서 `DataIntegrityViolationException` catch 후 진행 → Postgres tx abort 상태 | `support/review/application/ReviewService.java#createReviewDetail` | 🔎 | 사전 조회 or ON CONFLICT |
| SUP-06 | L | `review_detail.embedding NOT NULL`인데 비동기 채움 | `docs/ddl/support-ddl.sql` | 🔎 | nullable + 상태 컬럼 |

## 배치 (batch)
| ID | 심각도 | 결함 | 근거 | 검증 | v2 방향 |
|---|---|---|---|---|---|
| BAT-01 | H | 정산 step의 chunk tx 매니저는 primary인데 reader/writer는 shop EntityManager → 결과 저장과 원천 상태 변경이 원자적이지 않음 | `batch/settlement/batch/SettlementBatchConfig.java:51,81,108,180` | ✅ | 서비스 경계 준수(정산 데이터 소유 서비스로 이동) |
| BAT-02 | H | `PENDING` 조건 offset 페이징 reader로 읽으면서 같은 행을 `COMPLETED`로 변경 → 다음 페이지가 밀려 **일부 가게 정산 누락** (가게 100개 초과 시) | `SettlementBatchConfig.java:107-117,159` | ✅ | 커서/키셋 페이징 or 처리 대상 스냅샷 |
| BAT-03 | M | batch가 shop schema에 두 번째 DataSource로 직접 접속 (서비스 경계 침범) | `batch/settlement/batch/ShopDbConfig.java` | ✅ | API/이벤트 또는 정산 소유 서비스 내 배치 |
| BAT-04 | M | 구독 주문이 구독 시점 가격으로 생성 → 가격 변경 미반영(정책 미정) | `batch/subscription/batch/SubscriptionOrderItemProcessor.java` | 🔎 | 정책 결정 (Phase 1) |
| BAT-05 | L | 모든 스케줄러 기본 비활성(`batch.scheduler.enabled=false`), k8s CronJob과 이중 경로 | `config/config/batch-service.yaml`, `k8s/cronjobs/` | 🔎 | 실행 주체 단일화 |

## 인프라·품질
| ID | 심각도 | 결함 | 근거 | 검증 | v2 방향 |
|---|---|---|---|---|---|
| INF-01 | C | 루트 `useJUnitPlatform()` 주석 → catalog 외 **CI 테스트 0개 실행**. 대부분 서비스는 `contextLoads()`만 존재 | `build.gradle:50` | ✅ | Testcontainers 통합 테스트 CI 필수 |
| INF-02 | H | 트레이싱·메트릭·구조화 로그 없음 → Saga 장애 추적 불가 | 전 서비스 의존성 | ✅ | OTel + Prometheus/Grafana + traceId 로그 |
| INF-03 | M | 단일 Postgres·schema 분리, DDL 수동, 일부 `sql.init.mode: always` | `docs/ddl/`, `config/config/*.yaml` | ✅ | 서비스별 DB(또는 계정 분리) + Flyway |
| INF-04 | M | Feign 순환 의존(catalog↔shop, member↔shop, order↔shop) | 01-service-map | ✅ | 의존 방향 정리, 조회용 데이터는 이벤트로 복제 |
| INF-05 | M | k8s replicas 1, `:latest`, hostPath, HPA/PDB 없음, NodePort로 DB 노출 | `k8s/` | 🔎 | 버전 태그, 리소스/프로브 정비 |
| INF-06 | M | Redis 비밀번호 없음, ES security off, 인프라 포트 호스트 노출 | `docker-compose-infra.yaml` | 🔎 | 내부망 + 인증 |
| INF-07 | L | CI 매트릭스에 batch 누락, `run-local.sh`에 shop 누락(Windows 전용) | `.github/workflows/`, `run-local.sh` | 🔎 | 스크립트 정비 |
| INF-08 | L | `common`에 shop 정산 enum 위치, 공통 `@RestControllerAdvice`/성공 응답 규약 없음 | `common/` | 🔎 | 공통 모듈 범위 재정의 (must.md #5 응답 정리) |

---

## 심각도별 집계
| 심각도 | 개수 | ID |
|---|---|---|
| C | 6 | ORD-01, ORD-02, ORD-04, PAY-01, MEM-01, INF-01 |
| H | 18 | ORD-03,05,06,08,10 / CAT-01 / PAY-02 / WAL-01,02,03 / SHOP-01 / MEM-02,03 / SUP-01,02 / BAT-01,02 / INF-02 |

## 결함에서 도출한 v2 핵심 설계 주제
1. **주문-재고-결제 Saga 재설계** (ORD-01~06,10, CAT-01, WAL-01): 상태머신, 짧은 로컬 tx, 불확실 결과 처리, 예약 TTL, 멱등 API
2. **발행·소비 신뢰성 표준화** (ORD-09, CAT-03,05, PAY-03, SHOP-01,05, SUP-02,03): 공통 Outbox/Inbox, DLT 운영
3. **돈의 정합성** (PAY-01,02,04, WAL-02~05, ORD-04): 원장 모델, 대사, 품목 단위 환불
4. **경계·보안** (MEM-01~05,08, BAT-03, INF-03,04): 인가 위치, 헤더 신뢰 모델, DB 경계
5. **검증 가능성** (INF-01,02, SUP-01): 테스트 피라미드, 관측성
