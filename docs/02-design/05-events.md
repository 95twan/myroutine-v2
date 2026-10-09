# 05. 이벤트 카탈로그

> 2026-10-09 · 로드맵(3-x·5-4·6-x)과 consumer 목록을 맞춤: search·recommendation은 `product-discontinued`가 아니라 `product-upserted`의 status로 처리, `shop-closed`는 order(구독 종료)가 구독하고 notification은 구독하지 않음, `order-line-confirmed`는 settlement만(리뷰 자격은 `OrderApi` 동기 확인), `review-created`는 지금 구독자가 없다(6-5 제안용)

## 1. 규칙
| 항목 | 규칙 |
|---|---|
| 발행 | 상태 변경과 **같은 트랜잭션**에서 `common.outbox_event`에 저장 → 릴레이가 Kafka로 발행 (커밋된 변경만 발행, 유실 없음) |
| 토픽 | `{module}.{event}.v{n}` (예: `order.order-paid.v1`). 스키마가 호환되지 않게 바뀌면 v를 올리고 일정 기간 이중 발행 |
| 키 | 집계 ID(aggregate_id). 같은 집계의 이벤트는 같은 파티션 → 순서 보장 |
| 파티션 | Stage 1: 토픽당 3. Stage 2에서 consumer 처리량 측정 후 조정 |
| 봉투 | `eventId(UUIDv7, outbox 행 ID), eventType, occurredAt, aggregateId, traceId, payload`. 순서가 중요한 consumer용 버전은 payload에 둔다(예: `ProductUpsertedEvent.version`) |
| 전달 보장 | at-least-once. 모든 consumer는 `processed_message(consumer, eventId)`로 멱등 처리 (INV-10) |
| 재시도 | consumer별 지수 백오프(1초 → 2초 → 4초) 3회 → `{topic}.dlt`. DLT는 관리 API로 조회·재처리, 적재 시 알림 |
| 순서 의존 | 순서가 중요한 consumer는 payload의 `version`(집계 버전)을 보고 오래된 이벤트를 무시 |
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
3. 결과 기록: 성공 SENT / 실패 READY + attempts++ + next_attempt_at(10초 뒤) / 초과 DEAD
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
| `shop.shop-closed.v1` | 가게 폐업 | shopId, memberId | member(남은 가게 0이면 SELLER 회수), product(상품 DISCONTINUED), order(활성 구독 TERMINATED — 폐업 조건상 보통 0건, 5-4) |

- member는 처리 시점에 shop API로 **활성 가게 수를 재조회**해 SELLER 회수 여부를 판단한다(이벤트에 개수를 담지 않는다 — 순서·중복에 안전, As-Is SHOP-03 대응).

### product
| 토픽 | 발생 시점 | payload | consumer |
|---|---|---|---|
| `product.product-upserted.v1` | 상품 생성·수정·상태 변경 | productId, shopId, name, description, category, price, status, version | search(색인), recommendation(본문 해시 변경 시 재임베딩) |
| `product.product-price-changed.v1` | 가격 변경 | productId, oldPrice, newPrice, changedAt | order(구독 pending 가격 설정, POL-11 → `subscription-price-changed` 발행. 구독자 알림은 그 이벤트로) |
| `product.product-discontinued.v1` | 단종 | productId, shopId, reason(`SELLER`, `SHOP_CLOSED`) | order(구독 TERMINATED). search·recommendation은 함께 발행되는 `product-upserted`(status DISCONTINUED)로 처리한다(6-2·6-4) |
| `product.stock-depleted.v1` | 가용재고 0 | productId | search(품절 표시) [C] |

### order
| 토픽 | 발생 시점 | payload | consumer |
|---|---|---|---|
| `order.order-paid.v1` | 주문 PAID | orderId, memberId, totalAmount, shopOrders[{shopOrderId, shopId, lines[{orderLineId, productId, productName, quantity}]}] | notification(구매자·판매자), recommendation(취향 캐시 무효화) |
| `order.order-expired.v1` | 주문 만료·결제 실패 | orderId, memberId, reason(`EXPIRED`, `PAYMENT_FAILED`) | notification |
| `order.shop-order-shipped.v1` | 발송 | shopOrderId, orderId, memberId, carrier, trackingNumber | notification |
| `order.shop-order-delivered.v1` | 배송완료 | shopOrderId, orderId, memberId | notification |
| `order.order-line-confirmed.v1` | 구매확정 | orderLineId, orderId, shopId, productId, memberId, amount, confirmedAt | settlement(정산 대상 적재). 리뷰 작성 자격은 review가 `OrderApi.getConfirmedLine`으로 동기 확인한다(6-1) |
| `order.order-line-refunded.v1` | 환불 완료 | refundId, orderLineId, orderId, memberId, amount | notification |
| `order.subscription-status-changed.v1` | 구독 상태 변경 | subscriptionId, memberId, from, to, reason | notification |
| `order.subscription-cycle-failed.v1` | 회차 결제 실패·건너뜀 | subscriptionId, cycleId, memberId, consecutiveFailures, reason | notification |
| `order.subscription-price-changed.v1` | 구독 적용가 변경(인상 예정·인하) | subscriptionId, memberId, oldPrice, newPrice, effectiveDate | notification |

### payment
| 토픽 | 발생 시점 | payload | consumer |
|---|---|---|---|

- 결제 결과(정상 응답이든 대사로 늦게 확정되든)는 **이벤트가 아니라 동기 호출로 반영**한다. 주문 결제의 대사는 order가 주도한다(`PaymentApi.queryOutcome`, order → payment는 허용된 의존 방향, [06-sequences §2](06-sequences.md)).

### settlement / review
| 토픽 | 발생 시점 | payload | consumer |
|---|---|---|---|
| `settlement.settlement-paid.v1` | 정산금 지급 | settlementId, shopId, netAmount, periodStart, periodEnd | notification |
| `review.review-created.v1` | 리뷰 작성 | reviewId, productId, content | 없음 — 리뷰 임베딩(6-5 제안)을 만들 때 쓴다 |

## 3. consumer 요약 (모듈별 구독 토픽)
| 모듈 | 구독 |
|---|---|
| member | shop-opened, shop-closed |
| product | shop-closed |
| order | member-withdrawn, product-price-changed, product-discontinued, shop-closed |
| payment | member-withdrawn |
| settlement | order-line-confirmed |
| review | 없음 (작성 자격은 `OrderApi`로 동기 확인) |
| search | product-upserted (단종도 upserted의 status로 처리) |
| recommendation | product-upserted, order-paid, member-withdrawn |
| notification | 알림 대상 이벤트 전부 |

## 4. 동기 호출이 아니라 이벤트로 한 이유
| 연결 | 이유 |
|---|---|
| shop → member (역할) | 역할은 UI용이고 인가는 소유권 기반이라 지연이 무해함. 역방향 의존(member→shop은 조회만)을 피함 |
| order → settlement | 구매확정과 정산 적재가 분리돼도 됨. 정산 장애가 구매확정을 막으면 안 됨 |
| product → search, recommendation | 읽기 모델 갱신. ES·OpenAI 장애가 상품 등록을 막으면 안 됨 (NFR-AVL-02·03) |
| * → notification | 알림 실패가 비즈니스 흐름을 막으면 안 됨 |
| product → order (가격·단종) | product가 order에 의존하면 순환. 구독은 최종적 일관성으로 충분 |
