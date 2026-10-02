# 05. 이벤트 카탈로그

## 1. 규칙
| 항목 | 규칙 |
|---|---|
| 발행 | 상태 변경과 **같은 트랜잭션**에서 `common.outbox_event`에 저장 → 릴레이가 Kafka로 발행 (커밋된 변경만 발행, 유실 없음) |
| 토픽 | `{module}.{event}.v{n}` (예: `order.order-paid.v1`). 스키마가 호환되지 않게 바뀌면 v를 올리고 일정 기간 이중 발행 |
| 키 | 집계 ID(aggregate_id). 같은 집계의 이벤트는 같은 파티션 → 순서 보장 |
| 파티션 | Stage 1: 토픽당 3. Stage 2에서 consumer 처리량 측정 후 조정 |
| 봉투 | `eventId(UUIDv7), eventType, occurredAt, aggregateId, version, traceId, payload` |
| 전달 보장 | at-least-once. 모든 consumer는 `processed_message(consumer, eventId)`로 멱등 처리 (INV-10) |
| 재시도 | consumer별 지수 백오프 3회 → `{topic}.dlt`. DLT는 관리 API로 조회·재처리, 적재 시 알림 |
| 순서 의존 | 순서가 중요한 consumer는 이벤트의 `version`(집계 버전)을 보고 오래된 이벤트를 무시 |
| 추적 | 봉투의 `traceId`를 consumer가 MDC에 복원 → Kibana에서 traceId로 전체 흐름 검색 (NFR-OBS-01, ADR-010) |

### 릴레이 동작
```
1. 선점 (짧은 tx):
   UPDATE outbox_event SET status='PROCESSING', locked_until=now()+'30s'
   WHERE id IN (SELECT id FROM outbox_event
                WHERE status='READY' AND next_attempt_at <= now()
                ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED)
   RETURNING *;
2. 발행 (tx 밖): KafkaTemplate.send(...).get(timeout)  — 동기 확인
3. 결과 기록: 성공 SENT / 실패 READY + attempts++ + next_attempt_at(백오프) / 초과 DEAD
4. 회수: status='PROCESSING' AND locked_until < now() → READY
```
- As-Is PAY-03(락이 발행 전에 풀림)과 달리, 상태값(PROCESSING + 리스)으로 선점하므로 다른 인스턴스가 같은 행을 가져가지 않는다.
- 리스 만료 후 회수되면 중복 발행이 생길 수 있다 → consumer 멱등이 전제다.
- 같은 키의 순서: 다중 릴레이 인스턴스에서는 순서가 바뀔 수 있다. Stage 1은 릴레이 1개(advisory lock을 잡은 인스턴스만 실행)로 시작하고, Stage 2에서 처리량이 부족하면 키 해시 기반으로 나눈다.

## 2. 이벤트 목록

### member
| 토픽 | 발생 시점 | payload 주요 필드 | consumer |
|---|---|---|---|
| `member.member-registered.v1` | 가입 완료 | memberId, email, nickname | notification(환영) |
| `member.member-withdrawn.v1` | 탈퇴 완료 | memberId | order(구독 TERMINATED, 장바구니 삭제), payment(빌링키 삭제), recommendation(캐시 삭제) |
| `member.member-banned.v1` | 제재 | memberId, reason | notification |

### shop
| 토픽 | 발생 시점 | payload | consumer |
|---|---|---|---|
| `shop.shop-opened.v1` | 가게 개설 | shopId, memberId | member(SELLER 부여) |
| `shop.shop-closed.v1` | 가게 폐업 | shopId, memberId, remainingActiveShopCount | member(남은 가게 0이면 SELLER 회수), product(상품 DISCONTINUED), notification |

- member는 이벤트의 `remainingActiveShopCount`를 믿지 않고 shop API로 **재조회**해 SELLER 회수 여부를 판단한다(이벤트 순서·중복에 안전, As-Is SHOP-03 대응).

### product
| 토픽 | 발생 시점 | payload | consumer |
|---|---|---|---|
| `product.product-upserted.v1` | 상품 생성·수정·상태 변경 | productId, shopId, name, description, category, price, status, version | search(색인), recommendation(본문 해시 변경 시 재임베딩) |
| `product.product-price-changed.v1` | 가격 변경 | productId, oldPrice, newPrice, changedAt | order(구독 pending 가격 설정, POL-11), notification(구독자 알림) |
| `product.product-discontinued.v1` | 단종 | productId, shopId, reason | order(구독 TERMINATED), search(색인 제거), recommendation(INACTIVE) |
| `product.stock-depleted.v1` | 가용재고 0 | productId | search(품절 표시) [C] |

### order
| 토픽 | 발생 시점 | payload | consumer |
|---|---|---|---|
| `order.order-paid.v1` | 주문 PAID | orderId, memberId, totalAmount, shopOrders[{shopOrderId, shopId, lines[{orderLineId, productId, qty}]}] | notification(구매자·판매자), recommendation(취향 캐시 무효화) |
| `order.order-expired.v1` | 주문 만료·결제 실패 | orderId, memberId, reason | notification |
| `order.shop-order-shipped.v1` | 발송 | shopOrderId, orderId, memberId, carrier, trackingNumber | notification |
| `order.shop-order-delivered.v1` | 배송완료 | shopOrderId, orderId, memberId | notification |
| `order.order-line-confirmed.v1` | 구매확정 | orderLineId, orderId, shopId, productId, memberId, amount, confirmedAt | settlement(정산 대상 적재), review(작성 가능 표시) |
| `order.order-line-refunded.v1` | 환불 완료 | refundId, orderLineId, orderId, memberId, amount, pgAmount, walletAmount | notification |
| `order.subscription-status-changed.v1` | 구독 상태 변경 | subscriptionId, memberId, from, to, reason | notification |
| `order.subscription-cycle-failed.v1` | 회차 결제 실패 | subscriptionId, cycleId, attempt, reason | notification |

### payment
| 토픽 | 발생 시점 | payload | consumer |
|---|---|---|---|
| `payment.wallet-charge-completed.v1` | 충전 반영 완료 | chargeId, memberId, amount | notification |

- 결제 결과(정상 응답이든 대사로 늦게 확정되든)는 **이벤트가 아니라 동기 호출로 반영**한다. 주문 결제의 대사는 order가 주도하고(`PaymentApi.reconcile`), 충전의 대사는 payment가 wallet을 직접 호출한다. 둘 다 허용된 의존 방향이다([06-sequences §2](06-sequences.md)).
- 지갑은 가입 이벤트로 만들지 않고 **처음 쓸 때 생성**한다(`WalletApi.getOrCreate`, `member_id` unique). 이벤트와 consumer를 하나 줄이고, Kafka 도입 전(로드맵 Part 2)에도 동작한다.

### wallet / settlement / review
| 토픽 | 발생 시점 | payload | consumer |
|---|---|---|---|
| `wallet.withdrawal-completed.v1` | 출금 완료·실패 | withdrawalId, memberId, amount, result | notification |
| `settlement.settlement-paid.v1` | 정산금 지급 | settlementId, shopId, netAmount, period | notification |
| `review.review-created.v1` | 리뷰 작성 | reviewId, productId, content | review(임베딩 생성, 비동기) |

## 3. consumer 요약 (모듈별 구독 토픽)
| 모듈 | 구독 |
|---|---|
| member | shop-opened, shop-closed |
| wallet | (구독 없음) |
| product | shop-closed |
| order | member-withdrawn, product-price-changed, product-discontinued |
| payment | member-withdrawn |
| settlement | order-line-confirmed |
| review | order-line-confirmed, review-created |
| search | product-upserted, product-discontinued, stock-depleted |
| recommendation | product-upserted, product-discontinued, order-paid, member-withdrawn |
| notification | 알림 대상 이벤트 전부 |

## 4. 동기 호출이 아니라 이벤트로 한 이유
| 연결 | 이유 |
|---|---|
| shop → member (역할) | 역할은 UI용이고 인가는 소유권 기반이라 지연이 무해함. 역방향 의존(member→shop은 조회만)을 피함 |
| order → settlement | 구매확정과 정산 적재가 분리돼도 됨. 정산 장애가 구매확정을 막으면 안 됨 |
| product → search, recommendation | 읽기 모델 갱신. ES·OpenAI 장애가 상품 등록을 막으면 안 됨 (NFR-AVL-02·03) |
| * → notification | 알림 실패가 비즈니스 흐름을 막으면 안 됨 |
| product → order (가격·단종) | product가 order에 의존하면 순환. 구독은 최종적 일관성으로 충분 |
