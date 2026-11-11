# 알려진 문제

코드와 문서에서 찾은 문제와 알려진 한계를 한곳에 모은 목록이다. 로드맵 단계와 상관없이 찾으면 더하고, 고치면 지운다. 서브 단계로 옮긴 것은 그 단계 링크를 남기고, 고치지 않기로 한 것은 "고치는 방향"에 "고치지 않는다"와 이유를 적어 둔다. Release 노트의 알려진 한계는 이 목록에서 고른다.

## 위험도와 상태

| 위험도 | 기준 |
|---|---|
| 상 | 돈·재고·권한이 틀어지고 저절로 바로잡히지 않는다 |
| 중 | 돈·권한에는 닿지 않지만, 데이터나 상태가 틀어진 채 남거나 일부러 악용하면 다른 사용자가 피해를 본다 |
| 하 | 표시가 잠깐 틀리거나 운영자가 찾아 처리할 수 있는 동작, 운영 설정, 쓰지 않는 코드·데이터, 성능, 문서 정리 |

| 상태 | 뜻 |
|---|---|
| 재현됨 | 테스트나 실험으로 실제로 일어나는 것을 확인했다 |
| 확인됨 | 코드·문서를 대조해 사실로 확인했다. 쓰지 않는 코드나 의도한 동작처럼 실행하지 않아도 정해지는 것이다 |
| 추론 | 코드를 읽고 추론했고 재현하지 않았다 |

절은 위험도로 나누고, 같은 위험도 안에서는 재현됨 → 확인됨 → 추론 순서로 적는다.

## 1. 위험도 상

| 항목 | 상태 | 문제 | 근거 | 위치 | 고치는 방향 |
|---|---|---|---|---|---|
| 정산이 끝난 뒤 적재된 전월 품목 | 확인됨 | 정산(매월 5일 03:00) 뒤에 적재된 전월 품목은 그 정산에도 다음 달 정산 기간에도 들어가지 않아 `settlement_id`가 NULL로 남는다. 관리자 수동 실행도 그 가게·기간의 정산이 이미 있어 `insertIfAbsent`가 0을 돌려주고 건너뛴다. 판매자는 그 금액을 받지 못하고, `SettlementWithdrawalPrecondition`이 `PENDING_SETTLEMENT`를 돌려줘 탈퇴도 계속 막힌다. 정산 consumer 처리가 DLT로 갔다가 그 달 정산 뒤에 `AdminDltService.replay`로 처리되거나, 발행이 실패해 `DEAD`가 된 `order-line-confirmed`를 그 뒤에 `AdminOutboxService.retry`로 다시 보낼 때 생긴다 | INV-07(정산되지 않은 구매확정 품목이 남지 않는다). 3-4a 완료 확인 3번이 품목이 남는 동작을 확인한다 | `JpaSettlementItemRepository.findShopIdsToSettle`·`assign`, `SettlementService.settleShop`, `SettlementWithdrawalPrecondition` | [SUB-18](04-sub/settlement-late-items.md)에서 그 기간의 다음 회차 또는 다음 달 정산으로 넣는다 |
| PG에서 취소된 결제를 승인으로 읽음 | 추론 | PG 조회 결과의 `CANCELED`·`PARTIAL_CANCELED`를 `APPROVED`로 매핑한다. 우리 쪽이 `UNKNOWN`인 결제가 토스 관리자 화면 등 밖에서 이미 취소됐으면 대사가 결제를 `APPROVED`, 주문을 `PAID`로 닫아 판매자가 발송한다. 드러나는 지표가 없어 토스 관리자 화면과 대조해야 안다 | 2-6 대사의 PG 조회 결과 처리 | `TossClient.queryByOrderId` | 고치지 않는다(`v1.0.0`에 안고 감) |

## 2. 위험도 중

| 항목 | 상태 | 문제 | 근거 | 위치 | 고치는 방향 |
|---|---|---|---|---|---|
| 재고 점유 남용 | 확인됨 | 회원당 결제 대기 주문 수와 체크아웃 횟수에 상한이 없어, 한 회원이 체크아웃을 반복하면 인기 상품 재고를 15분씩 계속 붙잡을 수 있다. 점유는 주문 만료 때 풀린다. 막는 장치가 없고, 반복되면 주문 만료 수(`stock_reservation_expired_total`, payment 대시보드 패널, 알림 없음)가 늘어 뒤늦게 보인다 | 2-2 정책 4번 | `CheckoutService` | 2-2 "추가 항목"의 회원당 결제 대기 주문 수 상한(409)이나 회원·IP별 체크아웃 속도 제한(429)을 넣는다 |
| DLT 보존 7일 | 확인됨 | 토픽에 보존 설정이 없어 브로커 기본값(`apache/kafka:4.2.2` 이미지 설정의 `log.retention.hours=168`, 7일)이 `.dlt` 토픽에도 적용된다. "DLT 적재" 알림을 받고 7일 안에 리플레이하지 않으면 실패한 이벤트가 사라진다. 정산 품목 이벤트면 판매자가 그 금액을 받지 못하고(INV-07), 가게 개설 이벤트면 SELLER가 부여되지 않는다 | 3-2 DLT, 6-2 알림 "DLT 적재", 6-3 DLT 리플레이 | `*TopicConfig`의 `.dlt` 토픽, `docker/docker-compose.ops.yml`의 `kafka` | `.dlt` 토픽만 `TopicBuilder`의 `config(TopicConfig.RETENTION_MS_CONFIG, ...)`로 길게(예: 30일) 둔다. 이미 만들어진 토픽의 설정을 `KafkaAdmin`이 바꾸는지는 확인 필요다. 늘리지 않으면 "7일 안에 리플레이"를 운영 절차로 적는다 |
| SELLER 부여의 처리 순서 | 추론 | `shop-opened`과 `shop-closed`는 토픽이 달라 같은 가게라도 처리 순서가 보장되지 않는데, `grantSeller`는 활성 가게 수를 보지 않고 올린다. ① 가게 개설 처리가 DB 오류로 재시도를 다 쓰고 DLT로 간 뒤 가게가 폐업되고(새 가게는 진행 중 주문이 없어 바로 폐업된다), 폐업 처리는 활성 가게 0개라 그대로 USER로 둔다. 그 뒤 `AdminDltService.replay`로 개설 이벤트를 다시 보내면, 실패할 때 inbox 행도 함께 롤백됐으므로 처음 본 이벤트로 처리되어 활성 가게 0개인 SELLER가 남는다. 가게는 `ACTIVE → CLOSED`뿐이라 그 회원이 다른 가게를 열고 닫기 전까지 바로잡히지 않는다. 발행이 10번 실패해 `DEAD`가 된 개설 이벤트를 폐업 뒤에 `AdminOutboxService.retry`로 다시 보내도 같다. ② 가게 A를 폐업하고 B를 개설할 때, 폐업 처리가 활성 가게 수를 0으로 읽은 뒤(B 커밋 전) 커밋하기 전에 B의 개설 처리가 회원을 SELLER로 읽고 아무것도 쓰지 않고 끝나면, 활성 가게가 있는 USER가 남는다. 폐업 처리 트랜잭션이 릴레이 주기(1초)와 소비 시간보다 오래 열려 있어야 해서 사실상 생기지 않는다. 역할 값을 읽는 곳은 `/api/members/me` 응답의 `role`뿐이고 인가는 소유권 기반(`hasRole`은 `"ADMIN"` 하나)이라 권한에는 닿지 않는다 | INV-09(SELLER 여부 = 활성 가게 1개 이상, 최종적 일관성). Part 3 공통 규칙 P의 "키" 행: 순서는 같은 토픽·같은 키 안에서만 지켜진다 | `MemberRoleService.grantSeller`, `MemberShopEventConsumer`, 3-1 `MemberRoleService` 행, 3-2 `MemberRoleService` 행 | ① 두 consumer가 같은 메서드를 부르게 해서 처리 시점의 `countActiveShops`가 0보다 크면 SELLER, 0이면 USER로 맞춘다 ② 그 메서드가 회원 행을 `FOR UPDATE`로 먼저 읽으면 두 처리가 차례로 실행되어 ②의 경합도 막힌다. ②를 고치지 않기로 하면 그 결정을 이 행에 적는다 ③ 체인을 없애고 `/me`에서 가게 수로 `role`을 파생하면 이벤트 없이 같은 값을 준다. Outbox 학습용 첫 사례로 남기려면 ①·②로 고친다. 확인 케이스: 가게를 JDBC로 `CLOSED`로 만든 뒤 `ShopOpenedEvent`를 보내면 역할이 USER로 남는다 |
| 토큰 버전 캐시 쓰기 실패 | 추론 | 비밀번호 변경·재설정, 전체 로그아웃, 제재, 탈퇴는 `member.token_version`을 올리고 커밋 뒤(`afterCommit`) Redis `auth:token-version:{memberId}`에 새 값을 쓴다. 이 쓰기만 실패하면 Redis에는 옛 값이 키 만료(1일)까지 남는다. JWT 필터는 토큰의 버전이 Redis 값과 같을 때만 인증하므로, 그 뒤 새 버전으로 발급된 토큰이 모두 거부된다. 비밀번호를 바꾼 회원은 다시 로그인해도 최대 하루 동안 인증이 필요한 요청을 할 수 없다. 옛 버전 토큰은 액세스 토큰 만료(15분)까지만 통한다(세션 갱신은 세션 삭제와 `verifyCanLogin`이 막는다). 같은 요청에서 앞선 Redis 호출(세션 삭제)은 성공하고 커밋 뒤 쓰기만 실패해야 해서 창은 좁다 | 4-4 `TokenVersionService` 규격은 이 실패를 처리하지 않기로 했지만 영향을 "이전 토큰이 1일 통과"로만 적었다. 새 토큰 거부는 적지 않았다 | `TokenVersionService.bump`, `RedisTokenVersionProvider.currentVersion`, `JwtAuthenticationFilter`, 4-4 `TokenVersionService` 행 | 필터에서 토큰 버전이 Redis 값보다 크면 DB를 다시 읽어 Redis를 고친다. 토큰 버전은 DB가 올린 뒤에만 커지므로 옛 토큰을 거부하는 판단은 그대로다. 옛 토큰이 15분 통하는 쪽은 액세스 토큰 만료로 끝나므로 받아들인다. 4-4 문서의 영향 설명도 고친다 |
| 서로 다른 키로 동시에 온 PG 취소 | 추론 | `PaymentApiImpl.cancel`의 상태·잔액 검사가 `payment` 행 락 밖(첫 트랜잭션)이다. 서로 다른 키로 동시에 온 두 취소가 모두 통과할 수 있고, 두 번째는 `applyCancel`에서 예외로 롤백돼 그 `payment_cancel` 행이 `REQUESTED`/`UNKNOWN`으로 남는다. 드러나는 지표가 없다 | 2-6 PG 취소 | `PaymentApiImpl.cancel` | 고치지 않는다(`v1.0.0`에 안고 감) |
| Toss API별 에러 표 미대조 | 추론 | 불확실 코드 목록(`UNCERTAIN_CODES`)은 Toss 전체 에러 목록과만 대조했다. 승인·취소 API에만 나오는 "처리 중" 4xx 코드가 목록에 빠졌다면 그 응답을 조회 대신 거절로 처리해, PG에서 처리 중인 결제를 우리 쪽에서 실패로 닫는다. 빠진 코드가 있는지는 확인하지 못했다 | 2-3 "확인 필요" | `TossClient`의 `UNCERTAIN_CODES` | 개발자센터의 API별 에러 표와 대조한다 |
| 이메일 발송 횟수 키의 만료 누락 | 추론 | `RedisEmailCodeStore.checkSendLimit`은 `auth:email-hourly:{email}`을 `INCR`한 뒤 값이 1일 때만 따로 `EXPIRE`(1시간)를 건다. 그 사이에 실패하면 만료 없는 카운터가 남아, 5번을 넘은 뒤로 그 이메일은 가입·비밀번호 재설정 코드를 계속 받지 못한다. 로그인 잠금(`login-attempt.lua`)은 `TTL < 0`이면 만료를 다시 거는 방어가 있는데 이 키에는 없다. 인증 코드(`auth:email-code:*`, 5분)와 소셜 가입 토큰(`auth:oauth-signup:*`, 10분)도 `putAll` 뒤 따로 만료를 걸어, 같은 실패면 만료되지 않는다 | 4-2 발송 제한, 4-4 `login-attempt.lua` | `RedisEmailCodeStore.checkSendLimit`·`save`, `RedisSignupTokenStore.issue` | `INCR`과 만료를 Lua 하나로 묶거나 `login-attempt.lua`처럼 `TTL < 0`이면 다시 건다. 해시 키는 값 저장과 만료를 한 스크립트로 묶는다 |

## 3. 위험도 하

### 3.1 동작

| 항목 | 상태 | 문제 | 근거 | 위치 | 고치는 방향 |
|---|---|---|---|---|---|
| S3에 남는 이미지 객체 | 확인됨 | 이미지 삭제는 DB에서 행을 지운 뒤 트랜잭션 밖에서 `objectStorage.delete`를 부르고, 실패하면 경고 로그만 남긴다. 업로드 URL을 받아 올린 뒤 등록 API를 부르지 않은 객체도 남는다. 남은 객체를 찾거나 지우는 잡이 없어 버킷에 쌓인다. 화면과 데이터에는 영향이 없고 저장 공간만 쓴다 | 1-10 상품 이미지 | `ProductImageService.delete`·`issueUploadUrl`, `S3ObjectStorage` | 정리 잡이 `products/` 아래 객체 중 `product_image.object_key`에 없고 만든 지 하루가 지난 것을 지운다(올리는 중인 객체를 지우지 않게) |
| 업로드 파일 내용 미검증 | 확인됨 | 업로드 URL은 `Content-Type`과 크기만 제한하고, 등록 때 객체가 실제 이미지인지(파일 앞 몇 바이트의 형식 표시, 매직 바이트) 확인하지 않는다 | 1-10 상품 이미지 | `ProductImageService.register` | 등록 때 객체 앞부분을 읽어 이미지 형식인지 확인한다 |
| 지원하지 않는 `Content-Type`이 500 | 확인됨 | `text/plain` 등으로 요청하면 415가 아니라 500 `INTERNAL_ERROR`와 ERROR 로그가 나간다. `GlobalExceptionHandler`에 `HttpMediaTypeNotSupportedException` 처리가 없다 | Part 1 공통 규칙 F(에러 응답) | `GlobalExceptionHandler` | 415로 매핑한다 |
| 대사가 닫지 못하는 주문 | 확인됨 | `PAYMENT_IN_PROGRESS` 주문의 payment 행이 없거나 `PARTIAL_CANCELLED`이면 대사가 에러 로그만 남기고 매번 다시 시도한다. 주문이 `PAYMENT_IN_PROGRESS`로 남는다 | 2-6 대사 | `OrderReconcileService.reconcileOne` | 고치지 않는다(`v1.0.0`에 안고 감). 알림 `정체 Saga`(`saga_stuck_count`)와 에러 로그로 찾아 운영자가 처리한다 |
| `UNKNOWN` 재조회 상한 없음 | 확인됨 | `payment.reconcile_attempts`는 늘기만 하고 자동 종결로 이어지지 않아, PG가 답하지 않으면 계속 다시 조회한다 | 2-6 대사 | `JpaPaymentRepository`, `AdminPaymentResponse` | 고치지 않는다(`v1.0.0`에 안고 감). 알림 `결제 확인 지연`(`payment_unknown_oldest_age_seconds`)과 관리자 결제 조회의 `reconcileAttempts`로 찾는다 |
| 같은 장바구니로 중복 주문 | 확인됨 | 같은 장바구니로 결제 대기 주문이 둘 생기고, 둘 다 결제하면 주문 2건이 된다 | 2-2 | `CheckoutService` | 고치지 않는다. 결제 전이면 주문 취소(2-4)로, 결제됐으면 발송 전 품목 취소·환불(2-8)로 푼다. 같은 주문의 이중 결제는 막힌다. 방어 선택지는 2-2 "채택하지 않은 선택지" |
| 결제 중 장바구니 표시 | 확인됨 | 결제 대기 중인 항목이 장바구니에 구매 가능 또는 `OUT_OF_STOCK`으로 보인다 | 2-2 | `CartService.getCart` | 고치지 않는다(표시만 영향) |
| 차감이 상품 단위 | 확인됨 | 결제 중에 장바구니 항목을 지우고 다시 담으면, 결제 완료 때 새로 담은 수량에서 차감한다 | 2-2 | `CartService.deductOrdered` | 고치지 않는다(드문 경우) |
| 지난 주문의 썸네일 | 확인됨 | 판매자가 상품 이미지를 지우면 지난 주문의 썸네일 URL이 404가 된다. 주문은 이미지 키만 저장하고 이미지를 복사하지 않는다 | 2-2 | 주문 품목의 썸네일 키 | 고치지 않는다(의도한 한계) |
| 외부 호출에 서킷브레이커 없음 | 확인됨 | NFR-REL-05는 외부 시스템 호출에 타임아웃·재시도·서킷브레이커를 요구하지만 서킷브레이커는 없다. 타임아웃은 PG(연결 3초·읽기 10초), ES(2초), 운영 메일(연결 5초), OAuth(연결 3초)에 있다. PG가 계속 느리면 결제 요청마다 타임아웃까지 기다린다 | NFR-REL-05 | `TossClient`, `ProductIndexer`·검색, `SmtpMailSender`, `OAuthConfig` | 도입하려면 Resilience4j 같은 새 라이브러리라 CLAUDE.md 3절에 따라 학습 비용과 대안을 먼저 정한다. 들이지 않으면 요구사항에서 서킷브레이커를 뺀다 |
| 응답 빈칸 3곳 | 확인됨 | 공개 상세가 숨긴 상품을 404로 돌려 판매자 수정 화면에 설명·이미지를 채울 수 없다. 실패 환불 목록에 구매자와 Toss 주문번호가 없어 PG 밖에서 돌려줄 대상을 찾기 어렵다. 판매자 거절과 관리자 종결이 같은 `reject_reason`에 쓰여 구매자 응답에 거절 사유를 내보낼 수 없다 | 화면을 붙이며 드러남 | `ProductQueryService`, `AdminRefundResponse`, `Refund` | [서브 SUB-17](04-sub/response-gaps.md) |
| 로그 빈 곳 3곳 | 확인됨 | 알림 발송 실패, 이벤트를 내지 않는 도메인 상태 전이, JWT 거부 사유(만료·변조·토큰 버전 불일치)가 로그에 남지 않아 Kibana로 원인을 찾을 수 없다 | 6-1 로그 규격 | `NotificationDispatcher.dispatchBatch`, 상태를 바꾸는 서비스, `JwtProvider.parse` | [서브 SUB-8](04-sub/log-coverage.md) |
| 멱등 키가 `PROCESSING`으로 남음 | 확인됨 | `@Idempotent` API(체크아웃·결제 승인 등)를 처리하던 중 앱이 죽으면 키가 `PROCESSING`으로 남아, 같은 키로 다시 보내면 키가 만료될 때까지(24시간) 409 `IDEMPOTENCY_IN_PROGRESS`다. 클라이언트는 새 키로 다시 요청하고, 체크아웃이면 먼저 만든 주문은 15분 뒤 만료된다 | 2-2 채택하지 않은 선택지 2 | `IdempotencyAspect`, `IdempotencyRepository` | 고치지 않는다. 일정 시간 뒤 다시 점유하게 하면 비즈니스 트랜잭션과 `complete`가 서로 다른 트랜잭션이라 "실행 전에 죽음"과 "커밋 후에 죽음"을 구분할 수 없어 같은 요청이 두 번 처리된다. 하려면 `complete`를 비즈니스 트랜잭션과 한 트랜잭션으로 묶는 설계가 먼저 필요하다 |
| 폐업과 동시에 등록된 상품 | 추론 | 상품 등록은 `verifyOwnerOfActiveShop`으로 가게가 ACTIVE인지 락 없이 읽는다. 등록 트랜잭션이 확인 뒤 커밋하기 전에 폐업이 커밋되고, 그 `shop-closed`를 product consumer가 등록 커밋 전에 처리하면 새 상품은 단종 대상 조회에 잡히지 않아 폐업한 가게에 `ON_SALE`로 남는다. 체크아웃은 `shopApi.requireActiveShops`로 가게 상태를 확인하므로 이 상품은 결제되지 않고, 공개 목록·검색에 판매 중으로 보이기만 한다. 등록 트랜잭션이 릴레이 주기(1초)와 소비 시간보다 오래 열려 있어야 해서 사실상 생기지 않는다. 상품 수정은 단종과 같은 행을 바꾸므로 `@Version` 충돌로 둘 중 하나가 실패해 남지 않는다 | 3-2 폐업 → 가게 전 상품 단종 | `ShopApiImpl.verifyOwnerOfActiveShop`, `RegisterProductService`, `ProductDiscontinueService.discontinueAllByShop`, `CheckoutService`(`requireActiveShops`) | 표시만 틀리고 사실상 생기지 않으므로 고치지 않기를 권한다. 고친다면 등록의 가게 확인에서 가게 행을 `FOR SHARE`로 읽어 폐업의 UPDATE와 차례로 실행되게 한다 |
| 알림 메일 중복 발송 | 추론 | `NotificationDispatcher`는 메일을 보낸 뒤 별도 트랜잭션으로 `markSent`를 기록한다. 보낸 뒤 이 기록이 실패하거나 서버가 그 사이에 멈추면 알림이 `PENDING`으로 남아 다음 주기(10초)에 다시 보낸다. 같은 알림이 두 번 갈 수 있다 | 3-5 발송 순서(트랜잭션 밖 발송 → 결과 기록). 문서에 중복 가능성이 적혀 있지 않다 | `NotificationDispatcher.dispatchBatch` | 메일은 보낸 뒤 되돌릴 수 없어 한 번 이상 보내기를 받아들인다. 3-5 문서에 "결과 기록이 실패하면 다시 보낸다"를 적는다 |

### 3.2 운영·설정

| 항목 | 상태 | 문제 | 근거 | 위치 | 고치는 방향 |
|---|---|---|---|---|---|
| 앱이 MinIO root 계정으로 접속 | 확인됨 | 앱의 `myroutine.storage.access-key`·`secret-key`가 `MINIO_ROOT_USER`·`MINIO_ROOT_PASSWORD`다. 앱 설정이 새면 모든 버킷과 MinIO 관리 권한이 함께 넘어간다 | 1-10 | `application.yaml`, `docker/docker-compose.ops.yml` | 상품 이미지 버킷에 읽기·쓰기만 가진 서비스 계정을 만들어 앱에 준다 |
| 로컬·테스트 MinIO 버전 미고정 | 확인됨 | 로컬 compose와 Testcontainers가 `chainguard/minio:latest`를 쓴다(운영은 digest로 고정). 이미지가 바뀌면 로컬·CI 동작이 운영과 달라질 수 있다 | 1-10 | `docker/docker-compose.yml`, `IntegrationTestSupport` | 운영과 같은 digest로 고정한다 |
| 롤백은 이미지만 | 확인됨 | 마이그레이션은 앞으로만 간다. 이전 앱이 새 스키마에서 동작해야 이미지 롤백이 안전하다 | 1-11 | Flyway 마이그레이션, CD 워크플로 | 고치지 않는다. 파괴적 마이그레이션(컬럼 삭제·이름 변경)은 한 번의 배포에 넣지 않는다(1-11 정책) |
| 재색인 기능 없음 | 확인됨 | 검색 문서는 `product-upserted` 이벤트로만 채워진다. 분석기·매핑을 바꾸거나 ES 데이터를 잃으면 인덱스를 지우고 앱을 다시 시작해야 하는데, 새 인덱스는 비어 있어 기존 상품은 다음에 수정될 때까지 검색되지 않는다 | 5-2 | `ProductIndexer`, `ProductEventConsumer` | [서브 SUB-7](04-sub/search-reindex.md) |

### 3.3 쌓이는 데이터와 주기 작업

| 항목 | 상태 | 문제 | 근거 | 위치 | 고치는 방향 |
|---|---|---|---|---|---|
| 결제 지표 쿼리의 전체 읽기 | 재현됨 | `PaymentMetrics`가 30초마다 `status = 'UNKNOWN'` 조건으로 `count(*)`와 `min(created_at)`을 실행하는데, `payment.payment`에 상태 인덱스가 없어 결제 전체를 읽는다. Stage 2 시드(결제 약 900만 건)에서 한 번에 약 0.24~0.46초가 걸렸고 모든 측정에서 상위 쿼리로 나왔다. 결제는 지우지 않는 업무 기록이라 주문이 늘수록 계속 느려진다 | [s2-1a 기준선 리포트](perf/s2-1a-baseline.md)의 "모니터링이 30초마다 결제 900만 건을 읽는다" | `PaymentMetrics`, `JpaPaymentRepository.countByStatus`·`findOldestCreatedAtByStatus` | `payment (created_at) WHERE status = 'UNKNOWN'` 부분 인덱스를 둔다. `UNKNOWN`은 몇 건뿐이라 두 쿼리가 이 인덱스만 읽는다 |
| outbox 발행 지연 | 재현됨 | 이벤트 저장부터 Kafka 발행까지 p95 1,475ms로 NFR-PERF-06(1초)을 넘었다. 릴레이가 보낸 뒤 1초를 쉬고(`fixedDelay = 1000`), 한 건씩 Kafka 응답을 기다린다 | [s2-1a 기준선 리포트](perf/s2-1a-baseline.md), NFR-PERF-06 | `OutboxRelayJob`, `OutboxRelay` | [서브 SUB-14](04-sub/stage2-leftovers.md) |
| 운영 테이블 정리 없음 | 확인됨 | `outbox_event`, `processed_message`, `idempotency_key`, `notification`은 넣기만 하고 지우는 잡이 없다. 업무 기록(주문·결제·환불·정산·재고 이동)과 달리 처리가 끝나면 쓰지 않는 행이다. 자주 도는 쿼리(릴레이의 `READY`, 발송 잡의 `PENDING`)는 부분 인덱스를 써서 영향이 없지만, 디스크와 아래 `DEAD` 지표 쿼리가 행 수만큼 커진다. `idempotency_key`는 같은 키가 다시 올 때만 만료된 행을 지워서 `expires_at` 인덱스를 쓰는 곳이 없다 | - | `common.outbox_event`, `common.processed_message`, `common.idempotency_key`, `notification.notification` | 하루 한 번 도는 정리 잡(`JobRunner`)이 묶음(`LIMIT`)으로 지운다. 조건: `idempotency_key`는 `expires_at < now`(만료된 키는 지금도 다시 쓰일 때 지우므로 동작이 같다), `outbox_event`는 `SENT`이고 7일 지난 것, `processed_message`와 `SENT` 알림은 DLT 보존 기간보다 길게 둔 뒤. inbox 행을 너무 일찍 지우면 그 뒤 리플레이된 이벤트를 다시 처리한다. outbox `DEAD` 재시도는 그보다 늦게 올 수 있으므로 `DEAD` 처리 기한을 함께 정한다 |
| outbox `DEAD` 지표 쿼리의 전체 읽기 | 추론 | `OutboxMetrics`가 30초마다 `count(*) ... WHERE status = 'DEAD'`를 실행한다. `READY`·`PROCESSING`에는 부분 인덱스가 있지만 `DEAD`에는 없어, 쌓이는 `SENT` 행까지 전부 읽는다. 관리자 outbox 목록(`status`별 `created_at` 순)도 같다. 결제 지표 쿼리와 같은 모양이고, Stage 2 시드는 outbox 행을 넣지 않아 드러나지 않았다 | 결제 지표 쿼리와 같은 원인 | `OutboxMetrics.refresh`, `AdminOutboxService.getEvents` | `(created_at, id) WHERE status = 'DEAD'` 부분 인덱스를 둔다. 위 정리로 `SENT` 행을 지우면 커지지 않는다 |

### 3.4 쓰지 않는 코드·데이터

모두 확인됨이다(읽는 곳을 검색해 0건).

| 항목 | 문제 | 근거 | 위치 | 고치는 방향 |
|---|---|---|---|---|
| `price_history` 테이블 | 가격 변경마다 INSERT만 하고 읽는 곳이 없다. 조회 API·쿼리·consumer·요구사항이 없다. `product-price-changed` 이벤트가 `oldPrice`·`newPrice`·`changedAt`을 이미 담는다 | 요구사항 FR-PRD-07은 구독자 알림이고 이력을 요구하지 않는다 | `V202610081540__product_create_price_history.sql`, `UpdateProductService`, `JpaProductRepository`, `ProductRepository.insertPriceHistory`, 1-8 완료 확인("price_history 1건") | 테이블·`insertPriceHistory`·완료 확인 케이스 삭제. 이력이 필요하면 요구사항에 먼저 올린다. 남기면 `changed_at`과 `created_at`이 같은 값이라 하나로 합친다 |
| `stock_reservation.expires_at`과 인덱스 | 아무도 읽지 않는다. 만료 잡은 `orders.expires_at`을 읽는다. 인덱스 주석 "(2-5 만료 잡용)"이 틀렸다 | `04-state-machines.md`도 "예약의 expires_at은 참고값"이라고 적었다 | `V202610090100__product_create_stock_reservation.sql`, `StockReservation`, `ProductApi.reserve(..., expiresAt)`, `ProductApiImpl`, `StockReservationRepository`, 테스트의 `reserve` 호출 | 컬럼·인덱스·`reserve`의 `expiresAt` 인자 삭제(마이그레이션은 새 파일). 남기면 거짓 설명을 지운다 |
| `StockRepository.findProductIdsWithBrokenBalance` | 항상 빈 목록이다. `ck_stock_balance` CHECK가 같은 등식의 위반을 만들 수 없게 막는다. 6-2 `StockCheckJob`은 이 메서드를 쓰지 않는다. 1-9 완료 확인의 "끝에 빈 목록" 단언이 아무것도 증명하지 못한다 | 운영 코드 호출 0건, 테스트 14곳 | `StockRepository`, `JpaStockRepository`, 테스트 4개 파일, `08-stock-design-evolution.md` §3 | 메서드와 `StockStateCheck` 단언 삭제. `available`·`reserved`·`sold`를 직접 단언한다 |
| `StoredKey.updatedAt` | SELECT·매핑만 하고 읽는 곳이 없다 | `updatedAt()` 접근 0건 | `IdempotencyRepository`, `StoredKey` | 레코드 필드와 SELECT 컬럼 삭제. 컬럼은 갱신 감사용으로 둘지 정한다 |
| `shop.closed_at` | 쓰기만 하고 읽지 않는다 | 응답·쿼리·이벤트 어디에도 없다 | `Shop.closedAt`, `V202610061735__shop_create_shop.sql` | 컬럼 삭제 또는 폐업 시각을 응답·정산에 쓸 요구를 문서에 올린다 |
| `PgResult.Approved.totalAmount`·`balanceAmount`, `PgResult.Rejected.message`, `TossProperties.clientKey` | 2-3에서 정의했지만 읽는 곳이 없다. `PgOutcome`은 `method`·`approvedAt`·`code`만 옮긴다 | `TossClient`가 만들기만 한다 | `PgResult`, `TossPaymentResponse`, `TossProperties` | 삭제할지 정한다 |

### 3.5 성능·중복 처리

모두 확인됨이다.

| 항목 | 문제 | 근거 | 위치 | 고치는 방향 |
|---|---|---|---|---|
| `cart` 테이블 | `member_id` 한 컬럼뿐인 껍데기인데 모든 장바구니 동작이 먼저 `cart`를 읽거나 만든다. 담기 한 번에 `cart`·`cart_item`에 문장 4개가 나가고, 마지막 select는 방금 쓴 행의 id를 얻으려는 재조회다 | `cart.id`를 참조하는 곳은 `cart_item.cart_id`뿐이다 | `V202610111013__order_create_cart.sql`, `CartService`, `CartRepository`, 체크아웃의 `findByMemberId` | `cart_item.member_id`를 직접 두고 `UK(member_id, product_id)`로 바꿔 `cart`를 없앤다. 최소한 `INSERT … RETURNING id`로 마지막 재조회만 없앤다 |
| 첫 배송지 등록 | "기존 기본 해제" 경로(`changeDefault`)를 재사용해서, 기존 배송지가 없다는 이미 아는 사실을 다시 조회하고 같은 행을 두 번 쓴다. 문장이 4개 나간다 | `first`가 true인 순간 기본 배송지가 없음은 확정이다 | `MemberAddressService.register`, `MemberAddress.register`, 1-5 순서 | 첫 배송지는 `isDefault=true`로 바로 INSERT하고 해제 경로를 건너뛴다 |
| 체크아웃의 `getPurchasable` 이중 호출 | 체크아웃이 `getPurchasable`을 부른 직후 `reserve` 안에서 같은 상품 ID로 같은 JOIN 조회를 한 번 더 하고 결과를 버린다 | 문서의 이유는 "조회 시점과 예약 시점 사이에 상태가 바뀔 수 있다"인데 같은 트랜잭션·READ COMMITTED라 틈이 거의 없다 | `CheckoutService`, `ProductApiImpl.reserve` | 모듈 API의 독립 호출 계약 때문이라면 "의도적 중복 조회"로 문서에 명시한다. 아니면 `reserve`는 재고 UPDATE만 하고 호출자가 `getPurchasable`을 책임진다 |
| `stock_movement`의 `RESERVE`·`COMMIT`·`RELEASE` 행 | 쓰기만 하고 읽지 않는다. 읽는 곳은 `RECEIVE`·`ADJUST` 합계(6-2 `StockCheckJob`)와 `RESTORE`의 unique 멱등뿐이다. 재고 핫스팟 경로에서 상품당 INSERT가 예약 1회 + 확정 또는 해제 1회 더 나간다 | ADR-008 "모든 수량 변화는 기록"이 유일한 이유다 | `ProductApiImpl.reserve`·`commit`·`release`, ERD, 1-9 규격 | 감사 목적이면 읽는 소비자(점검 쿼리)를 ADR에 명시한다. 아니면 이 세 종류를 빼고 `type`을 `RECEIVE`·`ADJUST`·`RESTORE`로 줄인다 |
| PG 취소의 재조회 | `PaymentApiImpl.cancel`이 `insertIfAbsent` 뒤 항상 행을 다시 읽는다. 1행이면 방금 만든 `REQUESTED` 행이고 `id`도 알아서 재조회가 필요 없다 | 2-6 `cancel` A-2·A-3 | `PaymentApiImpl.cancel` | 0행일 때만 읽으면 취소마다 SELECT가 하나 준다. 분기가 늘어난다 |
| 인기 상품 재고 행의 처리량 | 한 상품에 주문이 몰리면 그 재고 행을 고치는 트랜잭션이 하나씩 지나가 초당 약 290건에서 멈춘다. 조건부 UPDATE, JPA 비관적 락, Redis 선착순 게이트 모두 같았다 | S2-5 측정([리포트](perf/s2-5-stock-reservation.md)), [ADR-008](adr/ADR-008-inventory-reservation.md) | `ProductApiImpl.reserve`, `StockRepository.reserve` | 고치지 않는다. 이 한계를 넘어야 하면 재고 버킷 분할이나 Redis 차감 + DB 비동기 확정을 본다(ADR-008 "다시 검토할 때") |

### 3.6 외부 계약에서 읽는 곳이 없는 것

DB 컬럼, API 응답 필드, 이벤트 페이로드는 소비자가 다른 모듈일 수 있어서 문서만으로 지우지 않았다. 모두 확인됨이고, 소비자가 없다고 확정되면 삭제한다.

#### 이벤트·Kafka

| 항목 | 문제 |
|---|---|
| 폐업 → 구독 종료 경로 | order의 `shop-closed` consumer(`terminate("SHOP_CLOSED")`)와 `shop-closed` → product 전 상품 단종 → 상품별 `product-discontinued` → order `terminate("PRODUCT_DISCONTINUED")`가 겹친다. 구독은 반드시 어떤 상품의 구독이라 뒤쪽이 앞쪽을 포함한다. 앞쪽은 문서가 "폐업 조건상 보통 0건, 방어용"이라고 적었다. `ProductDiscontinuedEvent.shopId`·`reason`을 order가 읽지 않는다 |
| `member-registered`의 `email`·`nickname` | 알림은 `memberApi.getContact`로 이메일을 따로 가져온다. 이메일(개인정보)이 토픽에 실려 나간다 |
| `product-upserted.subscribable` | search는 문서 필드로 넣지 않고 recommendation도 읽지 않는다 |
| `product-price-changed.oldPrice` | order는 `newPrice`·`changedAt`만 쓴다 |
| `order-paid`의 `PaidLine.productName`·`quantity`·`orderLineId`, `PaidShopOrder.shopOrderId`, `orderId` | 알림은 `memberId`·`totalAmount`·가게별 `shopId`를, recommendation은 `memberId`·`productId`를 쓴다. 알림 본문에 상품명을 넣는지는 문서에 없다 |
| `order-line-confirmed.memberId` | settlement 적재가 쓰지 않는다. 리뷰 자격은 동기 `OrderApi`로 확인한다 |
| `ShopOrderShippedEvent.shopOrderId`, `ShopOrderDeliveredEvent.shopOrderId`, `OrderLineRefundedEvent.refundId`·`orderLineId`, `ShopClosedEvent.memberId` | 읽는 consumer가 없다 |
| 봉투의 `eventType`·`occurredAt`, `outbox_event.aggregate_type`과 `publish`의 `aggregateType` 인자 | 토픽마다 consumer 메서드가 따로 있어 `eventType`을 쓰지 않는다. `aggregate_type`은 "조회·로그용"이라고만 적혀 있고 관리 API 응답에도 없다 |
| `ReleaseReason`·`ReservationStatus.EXPIRED` | `RELEASED`와 `EXPIRED`를 구분해 읽는 코드가 없다. 만료 카운터는 order가 센다. 결제 전 구매자 취소도 `PAYMENT_FAILED` 사유로 부른다 |

#### DB 컬럼

| 컬럼 | 문제 |
|---|---|
| `member.ban_reason`, `member.withdrawn_at`, `social_account.provider_email` | 쓰기만 하고 읽지 않는다(`withdrawn_at`은 테스트에서만 확인) |
| `shop_order.completed_at` | 판매자 응답은 `shippedAt`·`deliveredAt`만 내려준다 |
| `refund.completed_at`, `refund.member_id`, `payment_cancel.reason` | 쓰기만 하고 읽지 않는다 |
| `notification.version`·`channel`·`member_id` | 쓰기가 모두 native SQL이라 `version`은 쓰는 곳이 없다 |
| `subscription.end_reason`, `billing_key.deleted_at`, `product_embedding.model`, `review_summary.source_review_count`·`model` | 서브 설계에서 쓰기만 하고 읽지 않는다 |

#### API

| 항목 | 문제 |
|---|---|
| `payment.method`와 `GET /api/payments`(FR-PAY-08) | `payment.method`(그리고 이를 나르는 `PgResult.Approved.method`·`PgOutcome.method`)는 이 API에서만 읽을 수 있는데 이 API를 구현하는 단계가 없다 |
| `GET /api/billing-keys/{id}` | 단건 조회는 서브 SUB-1에 구현이 없다(목록·삭제·등록만 있다) |
| `SubscriptionResult`의 `consecutiveFailures`·`pendingUnitPrice`·`pendingEffectiveDate` 등 | 응답 필드가 문서에 정해져 있지 않다 |

### 3.7 문서·테스트 확인

설계·로드맵 문서에 "확인 필요"로 남았거나 표기가 틀린 곳, 증거가 약한 테스트다.

| 문서 | 항목 | 위치 | 풀 방법 |
|---|---|---|---|
| 로드맵 1-2 | CI가 테스트를 실제로 돌렸는지 보는 방법(`--info` 또는 test-results 업로드)을 정하지 않음 | 1-2 완료 확인 "CI가 테스트를 실제로 돌림" 행 | 하나 골라 적는다 |
| 로드맵 1-2 | Testcontainers 컨테이너 시작 로그 문구가 버전마다 다를 수 있음 | 1-2 완료 확인 "컨테이너는 한 번만 시작" 행 | `--info` 로그로 확인 |
| 로드맵 1-11 | VM이 amd64인지, 맥 LAN 인터페이스가 `en0`인지, `sudo -l` 거부 메시지 문구 | 1-11 할 일 S2(amd64)·S3 맥 리허설(`en0`), 완료 확인 "러너 권한" 행 | VM·맥에서 실행해 확인 |
| 개발 가이드 | springdoc-openapi의 Boot 4 호환 버전(사용하는 단계 없음) | development-guide 1.1절 "이후 단계에서 쓸 버전" 표 | 쓰게 되면 확인 |
| 10-state-combinations | 7절 2건(서브의 `billing_key`·`product_embedding` 상태머신, 구독 영역의 문서 어긋남) | 10 문서 7절 | 해당 서브 단계 착수 때 같이 푼다 |
| ops nas-minio | NAS로 옮길 때: Container Manager 패키지명·메모리 기본값, 시놀로지 ACL이 `chown 65532`를 막는지, 프로젝트·방화벽·HDD 절전·스냅샷 메뉴, 호스트 9000 포트 충돌, VM의 기존 이미지 데이터를 옮길지 | 1절(준비), 2절(데이터 폴더), 3절(컨테이너 사양), 5절(전환 순서), 7절(한계와 알려진 점) | 옮길 때 NAS 화면·실행으로 확인 |
| ops vm-setup | Proxmox/Ubuntu 설치 화면·LVM 체크 해제 이유, GHCR 패키지 연결·가시성, Proxmox 스토리지 추가 메뉴 | 1-1절(만드는 순서), 6절(GitHub 저장소 설정), 7절(스냅샷과 백업) | VM·GitHub·Proxmox 화면에서 확인 |
| 로드맵 2-10 | `ConfirmConcurrencyTest`는 이상 상태가 없는지만 본다. 수동·자동 구매확정이 같은 품목을 동시에 확정할 때 `OrderLine`의 `@Version`이 막는다는 증거는 아니다 | 2-10 완료 확인 | spy나 작업 안의 대기로 두 작업이 같은 품목을 읽는 구간을 겹치게 하고 "확정에 성공한 쪽은 하나"를 단언한다 |
| 로드맵 6-1 | 로그 인덱스 7일 삭제 정책(ILM `7d`)이 운영 VM에서 실제로 지우는지 확인하지 않음. 임시 ES에서 `min_age`를 10초로 줄여 삭제되는 것은 확인했고, 운영에서는 첫 인덱스가 `managed: true`로 잡히는 것까지 확인함 | 6-1 로그 보존 절 | 운영 VM에서 만든 지 7일이 지난 인덱스가 사라졌는지 `GET _cat/indices`로 확인 |
| ADR-001 | 제목과 결정 3이 "Stage 3에서 MSA로 전환한다"로 남아 있다. Stage 3는 하지 않기로 했다 | ADR-001 제목, 결정 3, 대안 표 | 상태 줄과 결정에 하지 않기로 한 것을 적는다([01-architecture-evolution](02-design/01-architecture-evolution.md)의 Stage 3 절) |
| 개발 가이드 | 716행이 `FakePgServer`에 main을 붙인 `pg-fake` 컨테이너가 있는 것처럼 적었지만 만들지 않았다 | development-guide 가짜 PG 서버 절 | "만들 때의 방법"으로 고친다 |
| 요구사항 | NFR-PERF-04(인증 오버헤드)·05(정산 배치)·07(상품 검색)을 아직 재지 않아 목표를 충족하는지 모른다 | stage2-plan S-04·S-05·S-07 | [서브 SUB-16](04-sub/stage2-leftovers.md)에서 잰다 |
