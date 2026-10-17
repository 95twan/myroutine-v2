# Part 2. 주문과 돈

> 버전 0.71 · 2026-10-17 · **2-7 구현을 마치며 jsonb "확인 필요"를 닫았다**: `ShopOrderListRow`의 `select new`에 `o.shippingAddress`(jsonb)를 넣어도 목록 테스트가 통과해(배송지가 비면 `ShippingAddressResult.from`에서 NPE) 대안(`findAllById` 한 번)을 지웠다. 규격 외 변경 없음
> 0.70 · 2026-10-17 · **2-7 `SellerShopOrderResult`를 만드는 법을 표로 풀었다**(구현 중 지적 "이 내용은 대체 뭐냐"): 표 한 칸에 길게 이어 쓴 getter 나열을 `from` 메서드 3개(상세용, 목록용, 품목용)로 나누고 `amount` 계산을 따로 적었다. 규격은 0.69와 같다
> 0.69 · 2026-10-17 · **2-7 조회 쿼리의 위치를 `OrderRepository`로 정했다**(구현 중 지적 "어느 쪽이든이 무슨 말이냐"): `ShopOrderQueryRepository`와의 양자택일을 없앴다. 규격 외 변경 없음
> 0.68 · 2026-10-17 · **2-7의 "확인 필요" 4건 중 3건을 정했다**(구현 중 요청): ① `ShopOrderListRow`에 `carrier`·`trackingNumber`·`shippedAt`·`deliveredAt` 칸을 더해 목록과 상세가 같은 모양이 되게 했다. ② `SellerShopOrderResult`의 배송지를 2-2의 `ShippingAddressResult`로 고쳤다(web이 domain을 import하지 않는 규칙). ③ 목록 `size`는 `getMyOrders`와 같은 기본 20, 1~50으로 정했다. ④ jsonb 배송지를 생성자 식에 넣는 문제는 실행해 봐야 알 수 있어 "확인 필요"로 남기고, 안 될 때의 대안(`findAllById` 한 번)을 적었다. 규격 외 변경 없음
> 0.67 · 2026-10-17 · **2-7 `OrderLine`·`ShopOrder` 메서드 이름 문장을 고쳤다**(구현 중 질문 "무슨 소리냐"): "정해져 있지 않다"가 필수 사항처럼 읽혀, 권장안이며 기존 `mark` + 과거분사 규칙을 따랐다는 것과 테스트가 부르는 이름은 `Order.ship`·`Order.deliver`뿐이라는 것을 적었다. 규격은 0.66과 같다
> 0.66 · 2026-10-16 · **2-6 `cancel`의 "같은 키는 같은 금액"을 호출자 책임으로 정했다**(리뷰 질문): 같은 키로 `UNKNOWN` 행을 다른 금액으로 다시 부르면 호출자 금액으로 Toss를 부르고 `applyCancel`도 호출자 금액을 쓰지만, 호출자(보상·환불 복구)가 모두 금액을 고정해 넘기므로 `cancel`은 확인하지 않는다. 리뷰 질문 4번을 "보상 `DONE` 뒤 `failOrder` 실패"로 바꿨다. 그 밖의 규격은 0.65와 같다
> 0.65 · 2026-10-16 · **2-6 "결과 반영 경쟁" 테스트를 대사 구조에 맞췄다**(구현 중 질문): `reconcileBatch`가 `apply`를 쓰지 않고 `applyOutcome`과 `applyPaymentState`를 별도 트랜잭션으로 부르므로, 경쟁 테스트도 그 두 단계를 두 스레드에서 동시에 부른다. `apply`로는 `payment` 행 락이 주문 처리까지 직렬화해 경쟁이 생기지 않는다. 그 밖의 규격은 0.64와 같다
> 0.64 · 2026-10-16 · **2-6 `PaymentCancelTest`의 "같은 키로 두 번"을 부분 취소로 못 박았다**(구현 중 질문 "전액 취소 뒤 같은 키 케이스와 뭐가 다른가"): 전액 취소를 두 번 부르면 두 번째 호출 때 payment가 이미 `CANCELLED`라 "전액 취소 뒤 같은 키" 케이스와 같아진다. 부분 취소로 하면 payment가 취소 가능한 상태로 남아 있어, 재생 확인이 없을 때 금액이 두 번 올라가는 결함(PG 요청 2건)을 잡는다. 그 밖의 규격은 0.63과 같다
> 0.63 · 2026-10-16 · **2-6 `reconcileBatch`의 조회 경로를 두 트랜잭션으로 나눴다**(구현 중 제안 "꼭 그렇게 만들어야 하나"): `queryPgOutcome` 뒤 `applyOutcome`으로 payment만 먼저 확정하고, 이어서 확정된 결제와 같은 `applyPaymentState`로 주문을 처리한다. `apply`는 2-6에서 쓰지 않고 2-4 `ConfirmPaymentService`만 쓴다. 보상 앞의 "승인 사실 먼저 기록" 단계가 없어졌고 보상 코드는 하나다. "경로 A·B·C" 이름을 결제 상태 이름으로 풀었다. 그 밖의 규격은 0.62와 같다
> 0.62 · 2026-10-16 · **2-6 `failOrder` 호출을 `tx.executeWithoutResult`로 고쳤다**: `failOrder`가 `void`라서 `tx.execute`에 넘기면 컴파일되지 않는다. 그 밖의 규격은 0.61과 같다
> 0.61 · 2026-10-16 · **2-6 `queryOutcome`을 `queryPgOutcome`으로 바꾸고 확정 분기를 지웠다**: 이미 확정된 결제는 `reconcileBatch`가 `view.state`로 먼저 걸러서 호출하지 않으므로 Toss 조회만 남겼다. 취소된 결제(`CANCELLED`)로 주문이 `PAYMENT_IN_PROGRESS`에 남는 경우(보상 취소 뒤 `failOrder` 실패)가 영구 정체되던 문제를 `reconcileBatch` 경로 C(`failOrder`)로 풀고 완료 확인에 케이스를 더했다. 쓰이지 않는 `PaymentView.cancelledAmount`를 지웠다. 그 밖의 규격은 0.60과 같다
> 0.60 · 2026-10-16 · **쓰이지 않는 전이와 불필요한 조회를 걷어냈다**(사용자 지적: 쓰지 않는 요소, 재사용하려고 억지로 맞춘 경로): ① 2-8 `RefundStatus`의 `APPROVED → COMPLETED`를 뺐다 — `complete`는 `PG_CANCELLED`에서만 부르고(`readyToComplete`), 상태머신 문서에도 없는 전이였다. `RefundProcessor.process` 2번의 "처음부터 필요 없던 경우" 설명도 같은 이유로 고쳤다. ② 2-10 `confirmBatch`는 읽은 건수가 100 미만이면 마지막 페이지라 다음 조회 없이 끝낸다(항상 빈 결과를 읽는 쿼리 1회를 없앴다). 규격·동작은 0.59와 같다
> 0.59 · 2026-10-16 · **2-6 `reconcileBatch`에 이미 확정된 결제의 직접 경로를 더했다**(구현 중 제안): `PaymentView`에 `failureCode`를 더하고, `view.state()`가 `APPROVED`·`FAILED`면 `queryOutcome`·`applyOutcome` 없이 `applyPaymentState`로 주문만 처리한다. 그 밖의 상태는 기존 `queryOutcome` → `apply` 경로다. 보상은 직접 경로에서 `view.approvedAt()`을 쓰고 승인 사실 기록(`applyOutcome`)을 생략한다. 완료 확인에 직접 경로 3건을 더했다. 그 밖의 규격은 0.58과 같다
> 0.58 · 2026-10-16 · **2-6 구현물 표 8번(`Payment`의 취소 반영)을 엔티티 메서드로 정했다**(사용자 결정): `Payment.applyCancel(Money)`가 `cancelled_amount` 증가와 status 전이를 한다. 호출 위치(트랜잭션 B에서 다시 읽은 엔티티)와 동시성(`@Version`, 주문 행 잠금, DB CHECK)을 적었다. 규격·동작은 0.57과 같다
> 0.57 · 2026-10-16 · **헬퍼 이름을 코드에 맞췄다**: 문서의 `setOrderExpiresAt`을 구현된 `setOrderExpired`로 고쳤다(2-4~2-10 전체). **2-6 "새로 등장" 표의 복구 잡 설명을 2-8b 규격에 맞췄다**: "5분 단위로 돈다"를 "1분마다 돌며 5분 넘게 멈춘 환불을 처리한다"로 정정했다(2-8b `RefundRecoveryJob`은 `fixedDelay = 60_000`). 규격·동작은 0.56과 같다
> 0.56 · 2026-10-16 · **2-6~2-10 "할 일"을 구현 순서대로 다시 정리했다**(사용자 지적: 숨은 구현물·순서·중복 표현): ① 단계 설명·문단·괄호 안에만 있던 구현물을 표로 올렸다 — 2-6 `PaymentCancelRepository` 신설, 2-7 `OrderLine`·`ShopOrder` 메서드와 쿼리 시그니처·응답 record, 2-8 `OrderRepository` 잠금 조회와 `OrderLine`·`ShopOrder` 메서드·`ShipmentService`·`OrderQueryService`·`OrderDetailResponse` 수정·DB CHECK 테스트 케이스, 2-9 `OrderLine` 메서드·`OrderErrorCode`, 2-10 `OrderLine` 메서드·`AutoConfirmTarget`·첫 페이지 쿼리. ② 2-7~2-10 "할 일" 맨 앞에 구현 순서 한 줄을 더하고, 2-8a는 `RefundCompleter` → `RefundProcessor` → `CancelOrderLineService` 순으로, 2-8의 `Refund` 메서드를 `RefundRepository`보다 앞에 놓았다. ③ 중복을 걷어냈다 — 2-6 `queryOutcome`·`cancel`의 쿼리 설명(구현물 표 1·3~8과 같은 내용), 보상 테스트의 "완료 실패" 설명(세 곳에서 한 곳으로), `cancel` 4번과 끝 문단의 같은 이유, 2-8 `pgIdempotencyKey`와 3) payment, 2-7 가게주문·품목 책임 문장, 2-10 keyset 이유. 규격·동작은 0.55와 같다
> 0.55 · 2026-10-16 · **2-6 구현물 표 1번에 쿼리 규격을 직접 적었다**(구현 중 지적 "구현하려고 밑으로 내려가라는 거냐"): `increaseReconcileAttempts`의 `set`·`where`·`markUnknown`을 쓰지 않는 이유를 표 안에 두고, `queryOutcome` 4번에는 호출 방식과 0행 처리만 남겼다. 규격·동작은 0.54와 같다
> 0.54 · 2026-10-16 · **2-6 "할 일"을 구현 순서대로 다시 놓았다**(구현 중 지적 "먼저 구현해야지"): 구현물 체크리스트 표를 `PaymentApi`·`queryOutcome`·`cancel` 설명 앞으로 옮기고 표 행을 의존 순서(엔티티 → 리포지토리 → `Payment` 반영)로 재정렬했으며, "할 일" 맨 앞에 구현 순서 한 줄을 더했다. 규격·동작은 0.53과 같다
> 0.53 · 2026-10-16 · **2-6 payment 쪽 구현물을 체크리스트 표로 풀었다**(구현 중 지적 "repository 추가 항목이 단계 설명에 숨어 있다"): `cancel` 1-2·1-3·3번에 흩어져 있던 `payment_cancel` 리포지토리 작업(`insertIfAbsent`·`findByIdempotencyKey`·`markDone`·`markFailed`·`markUnknown`)과 `increaseReconcileAttempts`, `Payment` 취소 반영을 시그니처·쿼리 조건·쓰이는 곳과 함께 한 표에 모았다. `cancelled_amount` 증가 방식은 여전히 확인 필요. 규격·동작은 0.52와 같다
> 0.52 · 2026-10-16 · **2-6 payment 쪽 구현물 목록(1374행)에 `PaymentRepository.increaseReconcileAttempts`를 더했다**(구현 중 지적 "repository 수정이 숨어 있다"): `queryOutcome` 4번 설명 안에만 있어 목록만 보면 놓치기 쉬웠다. 규격·동작은 0.51과 같다
> 0.51 · 2026-10-16 · **2-6 `queryOutcome` 4번의 `reconcile_attempts` 증가 방식을 CAS 쿼리로 정했다**(구현 중 질문 "setter가 없는데?"): `PaymentRepository.increaseReconcileAttempts`를 더하고, status 조건·0행 처리·`executeWithoutResult` 호출을 적었다. `cancelled_amount` 증가 방식은 아직 정하지 않았다(2-6 `cancel` 구현 때 결정). 그 밖의 규격은 0.50과 같다
> 0.50 · 2026-10-15 · **2-5 `expireOne` 2번에서 `ReleaseReason.PAYMENT_FAILED` 비교 문장을 지웠다**(구현에 필요 없는 참고 설명). 그 밖의 규격은 0.49와 같다
> 0.49 · 2026-10-15 · **2-5 `expire`가 0행이 되는 경우를 정확히 고쳤다**(구현 중 질문): "후보를 읽은 뒤 승인을 시작했다"는 `startPayment`의 `expires_at > :now`에 막혀 일어나지 않는다. 실제 경우는 구매자 취소, 커밋 전이던 승인 시작(행 락 대기 후 재평가), 다른 실행의 선처리다. 그 밖의 규격은 0.48과 같다
> 0.48 · 2026-10-15 · **2-5 만료 조건을 `expiresAt <= :now`로 바꿨다**(착수 중 결정): `expires_at`은 "그 시각에 만료"이므로 정확히 같은 시각도 만료로 본다. 결제 시작 CAS의 `expires_at > :now`와 한 시각도 겹치지 않고 빠지지 않게 맞물린다(`findExpiredIds`·`expire`). 그 밖의 규격은 0.47과 같다
> 0.47 · 2026-10-15 · **2-4 교차 락 테스트를 `ProductApi` 직접 호출로 바꿨다**(테스트 작성 중 확인): HTTP 경로는 항목을 HashMap으로 모아 넘겨 요청 순서가 락 순서를 정하지 못하므로, `reserve`의 정렬을 지워도 통과했다. `releaseReservation`과 `reserve(orderB, [y, x])`를 동시에 실행하는 형태로 정했다(정렬을 지우면 20회 모두 실패). 그 밖의 규격은 0.46과 같다
> 0.46 · 2026-10-15 · **2-4 장바구니 변경 케이스의 수량을 2→5에서 2→3으로 바꿨다**(테스트 작성 중 확인): 차감 순서(`deleteIfQuantityAtMost` 먼저)를 뒤집어도 2→5는 통과해 순서 규칙을 보호하지 못했다. 남는 수량(1)이 주문 수량(2) 이하가 되는 2→3으로 바꿨다. 그 밖의 규격은 0.45와 같다
> 0.45 · 2026-10-15 · **2-4 `ConfirmPaymentService` 트랜잭션 1의 0행 처리를 구체화했다**(구현 중 질문): `startPayment`의 `from`은 `PENDING_PAYMENT` 고정, 람다가 돌려줄 값(`paymentId` 또는 조기 결과), `EXPIRED`·`PAYMENT_FAILED`는 409 `INVALID_STATE_TRANSITION`으로 정했다. 그 밖의 규격은 0.44와 같다
> 0.44 · 2026-10-15 · **공통 규칙 J 표의 payment 허용 의존에 `member::api`(5-1부터)를 더했다**(리뷰 결정). 그 밖의 규격은 0.43과 같다
> 0.43 · 2026-10-15 · **2-6 `cancel`의 재생 확인을 상태 검사보다 앞에 두고, 보상 테스트의 완료 실패 주입 방법과 환불 PG 취소의 시도별 멱등 키를 정했다**(리뷰 결정): ① `cancel`: 같은 키의 `DONE`·`FAILED` 재생을 payment 상태 검사보다 먼저 한다(전액 취소 뒤 복구 잡이 `DONE`을 받는다). ② 보상 테스트: `breakReservedStock`은 보상의 재고 반환도 막으므로 가게주문을 JDBC로 `CANCELLED`로 만들어 `markPaid`가 실패하게 한다. ③ `refund.pg_attempts`와 `Refund.pgIdempotencyKey()`를 더해 관리자 재시도가 새 키로 PG까지 가게 했다(7-3). ④ 빌링 결제의 `paymentKey`는 5-3에서 `PgOutcome`에 더하는 것으로 정했고 2-4 구현은 지금 바꾸지 않는다. 2-4·2-6 외 규격·동작은 0.42와 같다
> 0.42 · 2026-10-15 · **2-5~2-10 클래스·메서드 설명을 풀어 썼다**(구현 중 "뭔지 모르겠다" 지적): 표 한 줄 요약이던 `JobLock`·`JobRunner`·`expireBatch`·`queryOutcome`·`cancel`·`reconcileBatch`·`Order.ship`·`Refund`·`Order`의 환불 메서드·`RefundProcessor`·`RefundCompleter`·`CancelOrderLineService`·`ReturnService`·`autoConfirm`·`confirmBatch`에 목적, 누가 부르는지, 입력과 반환, 단계별 타입·예외, 함정(품목이 이미 취소된 가게주문에 `ShopOrder.markCancelled()`를 부르면 예외 등)을 적었다. 문서에 정해져 있지 않아 "확인 필요"로 남긴 것: `payment_cancel` 엔티티·리포지토리와 `reconcile_attempts` 증가 방식, 같은 키로 전액 취소를 다시 부를 때 `cancel`의 검사 순서, 보상 취소(`failOrder`)가 테스트의 "재고 망가뜨리기"에서도 실패하는 문제, 판매자 목록 행의 빠진 칸, `RefundResponse` 정의, 반품 승인의 가게 확인 순서. 규격·동작은 0.41과 같다
> 0.41 · 2026-10-15 · **2-4 `OrderPaymentApplier`를 메서드별로 풀어 썼다**(구현 중 "뭔지 모르겠다" 지적): 표 한 줄 요약이던 `apply`·`applyPaymentState`·`failOrder`·`complete`·`fail`에 목적, 호출하는 곳, 단계별 반환 타입과 예외, 가드(`PAYMENT_IN_PROGRESS`가 아니면 그대로 반환)의 이유를 적었다. `order` 없음은 `IllegalStateException`으로 정했다(0.39와 같은 이유). 규격·동작은 0.40과 같다
> 0.40 · 2026-10-15 · **2-4 `payment.api`의 `@NamedInterface("api")`를 항목으로 적었다**(구현 중 질문): 공통 규칙 J에는 있었지만 2-4 `payment.api` 항목에는 빠져 있어(2-2 `member.api`는 적혀 있음) 놓치기 쉬웠다. 규격·동작은 0.39와 같다
> 0.39 · 2026-10-14 · **2-4 `callConfirm`의 payment 없음 처리를 적었다**(구현 중 질문): 2-4 규격에 payment를 못 찾았을 때가 없었다. `IllegalStateException`으로 두고(2-6과 같음), `PaymentErrorCode`는 만들지 않는다고 적었다. 규격·동작은 0.38과 같다
> 0.38 · 2026-10-14 · **2-4~2-10 표현·구조 정리**(설계·정책 변경 없음): 2-4에 승인 흐름(`ConfirmPaymentService`)의 순서와 `ConfirmPaymentCommand`·`ConfirmResult`를 적었다(테스트 준비에서만 쓰이고 정의가 없던 이름. 0행일 때 상태별 응답은 2-4 기존 서술·[주문 흐름](../02-design/09-order-flow.md)을 모은 것이고, `EXPIRED`·`PAYMENT_FAILED` 상태는 문서에 없어 "확인 필요"로 남겼다). 2-4 장바구니 차감 표, 2-5 `OrderRepository` 표, 2-6 `cancel` 순서, 2-7 공통 앞단·`ship`/`deliver`, 2-9 `request`/`reject`를 번호 목록으로 풀었다. 2-5·2-6 서비스의 의존성(`tx`, `applier`)과 2-10 `OrderLineResult`를 정의하고, 2-10의 150건 테스트 설명 중복을 합쳤다
> 0.37 · 2026-10-14 · **2-3 `TossClient` 설명을 `classify` → public 메서드 순으로 정리했다**(구현 중 질문): `classify`가 호출 형태 표에 이름만 나오고 정의가 없었고, 이후 순서표에 `classify` 내부 단계가 섞여 어디까지가 무엇의 일인지 알 수 없었다. ① `classify`를 시그니처와 위에서 아래로 읽는 한 흐름(상태 코드별 분기, 작업별 2xx 규칙 포함)으로 적고, ② public 메서드 5단계 순서표에는 `classify` 호출만 남겼다. 조회의 `Rejected(status)`는 필드 두 개인 `PgResult.Rejected`에 맞춰 `Rejected(status, status)`로, `Approved`를 만들 때 `approvedAt`이 `null`이면 `Unknown("INVALID_BODY")`로 분류하도록 적었다. `classify`에 `throws IOException`을 적고 `TossClientConfig`의 `var`는 명시 타입으로 맞췄다. 같은 기준으로 `TossClientConfig`를 단계 목록으로, 불확실 코드 목록을 `classify` 뒤로, public 메서드의 작업별 요청(`cancel` 바디 포함)을 목록으로, 가짜 PG 서버를 "요청 하나를 처리하는 순서"와 `Behavior` 팩토리 표로 정리했다. `PgResult` 결과별 뜻과 시크릿 키 안내를 더했다. `bodyTo`가 `null`(빈 본문)이면 2xx·4xx 모두 `Unknown("INVALID_BODY")`로 분류한다고 적었다. 로그에 남기는 식별자를 `orderId`에서 `paymentId`로 바꿨다(`cancel`에는 `pgOrderId`가 없고, `cancel`·`queryByOrderId`의 `paymentId`는 이 로그용이다). 4xx 본문에 `code`가 없을 때(`null`) `Unknown("HTTP_4xx")`로 분류한다고 적었다. 로그의 `status={}`를 `reason={}`로 바꾸고 `result`·`reason`의 값을 정의했다(HTTP 상태 코드는 `classify` 밖에서 알 수 없어서). 가짜 서버 `Behavior`의 필드 `drop`·`approve`가 팩토리 `drop()`·`approve()`와 record accessor로 충돌해 `dropConnection`·`defaultHandling`으로 바꿨다. `FakePgServer`는 두 곳 이상에서 쓰는 `ok`·`notFound`만 따로 두고 나머지는 `handle` 안에 쓰도록 했다(한 곳에서만 쓰는 함수는 나누지 않는다). 요청 바디 읽기(`readValue(Map)`)·`mapper` 필드·`StoredResponse` 용도·`InterruptedException` 처리를 적었다. `FakePgServer`를 필드·메서드·핸들러 처리 순서 세 덩어리로 정리하고, 구현에 필요한 JDK `HttpServer` 사용법, 내부 타입(`ApprovedPayment`·`StoredResponse`), 멱등 키 저장 상태, 작업을 가르는 경로 규칙, 결제 객체 JSON 필드값을 처리 순서 안에 적었다. 정의 없이 쓰던 `handler`를 `handle(HttpExchange)` 메서드로 정의했다. 테스트 준비에서 `pg.requests`(받은 요청 기록)와 PG의 승인 기록(`queryByOrderId`로 확인)을 구분했다. 분류 결과는 0.34와 같다
> 0.34 · 2026-10-14 · `deleteExpired` 구현 반영: 반환 타입을 `void`로 맞췄다(호출하는 `around`는 반환값을 쓰지 않고 뒤따르는 `claim`의 결과로 판단한다). 규격·동작은 0.33과 같다
> 0.33 · 2026-10-13 · **멱등 키 만료 처리의 경합을 막는 `deleteExpired`를 규격에 더했다**(최종 리뷰 질문): 만료된 키를 두 요청이 동시에 읽으면 조건 없는 `delete`가 먼저 새로 점유한 요청의 키를 지워 같은 키가 두 번 처리될 수 있었다. `around` 4-2는 만료 조건이 SQL에 있는 `deleteExpired`를 쓴다(예외 시 `delete`는 그대로)
> 0.32 · 2026-10-13 · **2-2 마이그레이션에 `idx_order_line_shop_order_id (shop_order_id)`를 더했다**(최종 리뷰 지적): 주문 상세가 배치 로딩으로 `order_line`을 `shop_order_id IN (...)`으로 읽는데 FK는 인덱스가 자동으로 생기지 않는다. `ERD`의 인덱스 목록도 맞췄다
> 0.31 · 2026-10-13 · **2-2 `IdempotencyAspectTest`의 "테스트 준비"를 적었다**: 만료된 키를 만드는 법(JDBC로 `expires_at`을 과거로), "새로 처리됨"을 보는 법(같은 바디는 사업자번호 중복 409, 다른 바디는 새 `shopId`의 201), `JdbcTemplate` 주입
> 0.30 · 2026-10-13 · **결제 실패와 취소의 층을 명확히 했다**: `markPaymentFailed` 뒤 주문은 `PAYMENT_FAILED`, 가게주문·품목은 원인과 무관한 `CANCELLED`임을 `Order` 메서드 표 아래에 적었다(상태머신 문서 핵심 규칙에도 같은 내용). 구현 변경 없음
> 0.29 · 2026-10-13 · **2-2 `OrderTest`의 "같은 가게 상품은 한 가게주문으로" 케이스를 첫 케이스에 합쳤다**: 가게 2곳·품목 3개 케이스가 이미 "가게별 품목 수 맞음"(같은 가게 2품목)을 확인해 따로 둘 이유가 없었다. `OrderTest`는 7개에서 6개가 된다
> 0.28 · 2026-10-13 · **`TestFixtures` 헬퍼 표를 각 단계의 "테스트 준비"로 옮겼다**: N-1 표를 위로 올라가 보지 않아도 단계마다 "이 단계에서 추가하는 헬퍼"(메서드·만드는 방법·쓰는 곳)와 "이미 있는 헬퍼"가 보인다. N-1에는 원칙과 이름 색인만 남겼다. 내용은 그대로다
> 0.27 · 2026-10-13 · **2-2 `OrderDetailResult` 정의를 채웠다**("주문 필드 전체"만 적혀 있던 것): `OrderDetailResult`·`ShopOrderDetailResult`·`OrderLineDetailResult`·`ShippingAddressResult`의 record, 칸별 출처 표, 내려주지 않는 필드와 이유, 쿼리 수
> 0.26 · 2026-10-13 · 2-2 "추가 항목"에 **주문 목록이 품목을 전부 읽는 낭비**(Stage 2 후보, 주문에 `first_product_name`·`line_count` 저장)를 남겼다
> 0.25 · 2026-10-13 · **구매자 취소는 `PAYMENT_FAILED`가 아니라 새 상태 `CANCELLED`로**: 결제 전 주문 취소가 "결제 실패"로 보이고 결제 실패율에 섞이는 문제를 없앴다. `OrderStatus`에 `CANCELLED`를 더하고(`PENDING_PAYMENT → CANCELLED`), `PENDING_PAYMENT → PAYMENT_FAILED`는 쓰는 곳이 없어져 뺐다(`PAYMENT_FAILED`는 승인 시작 뒤의 PG 거절·보상만). `ck_orders_status`에 `CANCELLED` 추가, 취소 CAS에서 `failure_reason`을 뺐다. 2-2 마이그레이션·`OrderStatus` 규격과 2-4 취소 규격·테스트를 맞췄다
> 0.24 · 2026-10-13 · **결제 전 주문 취소 API를 2-4 필수로 승격**: `POST /api/orders/{id}/cancel`(구매자 본인, `PENDING_PAYMENT`만). 2-4 "추가 항목"이던 결제창 실패 통지 `payment/fail`을 이 API로 합쳤다(결제창을 닫았을 때와 마음이 바뀌었을 때가 같은 동작). CAS로 상태를 바꾼 뒤 `releaseReservation`(상품 ID 오름차순)으로 재고를 풀어 `reserve`·확정·만료와 `stock` 락 순서가 같다. 2-4 완료 확인에 `OrderCancelTest`, 리뷰 질문을 더했다. 2-2 "채택하지 않은 선택지"·중복 주문 한계의 문구도 맞췄다
> 0.23 · 2026-10-12 · **2-2 `checkout` 1·2번 구체화**(구현하며 막힌 곳 반영): 1번에서 "내 `cartId`"를 구하는 조회(`findByMemberId`), 중복 ID 처리, 항목을 `Map<UUID, Integer> quantityByProduct`로 바꾸는 코드와 이 Map을 쓰는 단계를 적었다. 2번의 50은 품목 종류 수이고 수량 합이 아님을 밝혔다. 5번 `OWN_SHOP_PRODUCT`의 `details.productIds`를 만드는 법을 적었다
> 0.22 · 2026-10-12 · 2-2 `CheckoutResult` 변환의 "서비스 안에서"가 **호출 시점(트랜잭션 안)**을 뜻하고 변환 코드의 위치(`from` 팩토리 등)는 자유임을 명확히 했다
> 0.21 · 2026-10-12 · 2-2 `CheckoutResult`가 참조하던 **`ShopOrderResult`의 정의가 없던 것**을 채웠다(API 명세 §3.1의 `shopOrders[]`와 같은 형태, 품목은 `CheckoutLineResult`). `CheckoutResult`를 `Order`에서 만드는 표를 더했다
> 0.20 · 2026-10-12 · **2-2 `OrderRepository`·`OrderQueryService` 목록 구체화**: 메서드별로 누가 쓰는지와 `JpaOrderRepository`에서 더할 것(JPQL 포함)을 표로, 목록에 `@EntityGraph`를 붙이지 않는 이유, `default_batch_fetch_size` 위치와 조회별 쿼리 수, 커서 페이징을 만드는 법(`size + 1`건 읽기, 다음 커서는 돌려주는 마지막 항목)을 적었다
> 0.19 · 2026-10-12 · **2-2 도메인(4) 설명 보강**(구현하며 막힌 곳 반영): `Order` 메서드를 "누가·언제 부르나 / 전제 상태 / 하는 일" 표로 바꾸고 `markPaid`의 전제(`PAYMENT_IN_PROGRESS`), `cancelPendingChildren`이 `PENDING`만 건드리는 이유, 200자 제한의 이유를 적었다. 규격에 없던 `ShopOrder`·`OrderLine` 메서드, `order_id` 주입 시점과 `@Version` 타입, `OrderTest`의 상태 맞추기를 더했다. `OrderTest`에 두 케이스를 더했다
> 0.18 · 2026-10-12 · `@Idempotent`를 붙일 때 헤더를 더해야 하는 기존 테스트 수를 구현 결과에 맞췄다(`ShopControllerTest` 2곳, `SellerProductControllerTest` 7곳 = 상품 등록 6 + 재고 조정 1, 검증 오류 요청은 제외)
> 0.17 · 2026-10-12 · 2-2 멱등 `around` 4번의 판단 순서를 **만료 → 해시 → 상태**로 바꿨다(만료된 키는 다른 바디여도 새로 점유한다는 정책과 맞춤). 완료 확인에 "만료된 키 + 다른 바디" 케이스를 더했다
> 0.16 · 2026-10-12 · 2-2 멱등 `around` 4번에 `find`가 비어 있는 경우(`claim` 실패 직후 다른 요청이 키를 지움)를 `IDEMPOTENCY_IN_PROGRESS`로 정했다(구현하다 확인)
> 0.15 · 2026-10-12 · **용어 정리**: 단계 끝 "제안 (선택)"을 "추가 항목"으로 바꿨다. "제안"은 리뷰어의 의견(근거 없는 지적)에만 쓴다. 내용은 그대로다. 결정이 끝난 **중복 체크아웃 방지 선택지 3안과 멱등 키 재점유**를 단계 끝 칸에서 2-2 정책의 "채택하지 않은 선택지"로 옮기고 이유·지금의 동작을 함께 적었다
> 0.14 · 2026-10-12 · 2-2 멱등 키의 정리 잡과 재점유를 나누고, 재점유는 중복 처리 위험 때문에 채택하지 않았음을 적었다
> 0.13 · 2026-10-12 · **중복 체크아웃을 막지 않는 것으로 확정**: 같은 장바구니로 결제 대기 주문이 둘 이상 생길 수 있고(중복 주문), 둘 다 결제하면 별개의 주문 두 건이 된다. 의도하지 않았다면 발송 전 품목 취소·환불(2-8)로 정리한다. 회원당 결제 대기 1건(대체·잠금·구매자 취소)과 항목 단위 잠금은 비용 대비 효과가 작아 채택하지 않았고 "제안"에 선택지를 남겼다. 2-2 정책에 한계 4가지를 명시했다
> 0.12 · 2026-10-12 · **장바구니는 체크아웃 때 건드리지 않고 결제 완료 때 주문 수량만큼 차감**: 체크아웃은 장바구니 항목을 주문 품목(스냅샷)으로 복사만 하고 지우지 않는다(2-2). 결제가 완료되면 `(cart_id, product_id)`로 주문 수량만큼 뺀다(2-4, 주문 수량 이상이면 삭제). 실패·만료는 장바구니에 할 일이 없다. 주문과 장바구니 사이에 연결 컬럼을 두지 않는다. 2-2에서 쓰이지 않게 된 `CartItemRepository.deleteAll`을 2-1 규격에서 뺐다
> 0.11 · 2026-10-11 · 공통 규칙 P-5에 **필터 경로의 null 확인**(`contains(nullValue())`)을 더했다(2-1 테스트를 쓰다 확인)
> 0.10 · 2026-10-11 · 공통 규칙 N-1에 **단계별 `TestFixtures` 추가**를 정리했다(2-2 이후 "완료 확인" 테스트가 필요로 하는 준비 상태 헬퍼, 처음 필요한 단계 기준). `checkout`은 `token`이 아니라 `memberId`를 받는 서비스 직접 호출로 바꿨다. 2-1~2-10 각 "완료 확인" 아래에 **테스트 준비**를 적었고, `ConcurrencyRunner`에 서로 다른 작업을 돌리는 오버로드를 더했다(latch는 2개, 결과는 `Future`로 수집)
> 0.9 · 2026-10-11 · 2-1에 `Jpa*Repository`(infrastructure) 구조를 명시했다(Part 1과 같은 구조인데 규격 표에 빠져 있었다). `deleteAll` 시그니처를 `Iterable<? extends CartItem>`으로 바로잡았다(구현하다 컴파일 오류로 확인)
> 0.8 · 2026-10-11 · 시작 전 할 일을 `subscribable` 결정(생략 시 `false`)에 맞춰 바꿨다
> 0.7 · 2026-10-10 · **Part 1에서 배운 것 반영**: 공통 규칙 P(검증 메시지, 요청 DTO 원시 타입, `@Validated`, `TestFixtures.token`, 응답 경로 단언, 일부러 깨뜨려 확인, Hibernate 컬렉션 fetch, jsonb의 Jackson 2, 배포 전 환경변수, 롤백과 마이그레이션 호환, 422 상수) 추가. 직접 실행해 확인한 것으로 2-2 상세 조회의 "확인 필요"를 바꾸고(`List` 두 개 동시 fetch는 `MultipleBagFetchException`) N+1 테스트를 더했다. `@Idempotent`를 붙이면 고쳐야 하는 기존 테스트·curl, 체크아웃 `orderId`와 1-9 멱등 규칙의 관계, 2-3 Toss 키를 운영 `.env`에 먼저 넣는 순서, JSON 변환 타입을 `ObjectMapper`로 통일(Part 1 코드와 같은 타입, Part 3도 같음), 수동 결제 확인 페이지의 인증, 시작 전 결정 항목(`subscribable`)을 적었다
> 0.6 · 2026-10-09 · **구현된 Part 1 코드와 정합·사실 확인**: `TestFixtures`의 실제 헬퍼 이름, `ProductApi`·`ShopApi` 반환 필드에서 `CheckoutLine`·`ReserveItem`으로 옮기는 방법, 1-10 규칙(키는 Result, URL은 web)에 따른 장바구니·주문 상세의 `thumbnailUrl`, 결제 모듈 API에 남아 있던 `(ORDER, orderId)` 인자 잔재 제거, AOP 스타터 이름(`spring-boot-starter-aspectj`)·Jackson 3의 모르는 필드 무시·Toss API(엔드포인트·Basic 인증·멱등키 15일)·불확실 코드 목록을 확인해 확정, `RestClient.exchange`로 응답을 분류하는 호출 형태를 적었다
> 0.5 · 2026-10-02 · **덜어내기**: 주문번호·바로 구매·멱등 키 재점유와 정리 잡·PG 호출 로그 테이블·트랜잭션 안 호출 가드·결제창 실패 통지 API·대사 백오프·판매자 주문 기간 필터를 "제안"으로 옮기고, 제약 이름 매핑을 없앴다
> 0.4 · 2026-10-02 · **예치금(지갑) 제거**: 2-1·2-2(지갑)·2-9(충전) 삭제, 결제는 Toss 단일 수단, 환불은 PG 부분취소만. 단계 번호를 다시 매김(이전 2-3 → 2-1 …)
> 0.3 · 이 문서만 보고 개발할 수 있게 구체화

> **끝나면**: 여러 가게의 상품을 카드(Toss)로 한 번에 결제하고, 판매자가 발송하고, 구매자가 취소·반품·구매확정까지 할 수 있다. 결제 응답이 불확실해도 결제와 재고가 어긋나지 않는다.
> **인프라 추가**: 없음 (Toss는 테스트용 가짜 PG 서버로 대체, 실제 Toss 테스트 키는 수동 확인용)
> **이 Part가 프로젝트의 핵심이다.** 이력서의 "정합성" 이야기는 대부분 여기서 나온다.
> **릴리스**: `v0.2.0`
> **시작 전에 할 것**: 상품 등록 요청의 `subscribable`을 `Boolean`으로 바꾸고 생략·`null`이면 `false`로 등록한다(2026-10-11 결정, [Part 1 수정 사항](part1-foundation.md#수정-사항-part-2-시작-전)). Part 2의 요청 DTO는 같은 문제가 생기지 않게 공통 규칙 P-2를 따른다.

| 단계 | 제목 | 크기 |
|---|---|---|
| 2-1 | 장바구니 | S |
| 2-2 | 체크아웃: 주문 생성과 재고 예약 (+ 멱등 API, 주문 조회) | L |
| 2-3 | Toss 클라이언트와 가짜 PG 서버 | M |
| 2-4 | PG 결제 승인 | L |
| 2-5 | 주문 만료 잡 (+ 잡 락) | M |
| 2-6 | 결제 대사 (+ PG 취소) | M |
| 2-7 | 판매자 주문 관리: 발송 · 배송완료 | M |
| 2-8 | 품목 취소와 환불 | L |
| 2-9 | 반품 | S |
| 2-10 | 구매확정 (수동 · 자동) | M |

---

## Part 2 공통 규칙 (Part 1 공통 규칙에 더해서)

> [Part 1 공통 규칙](part1-foundation.md#part-1-공통-규칙-모든-단계에-적용) A~I는 그대로 적용된다. 아래는 Part 2에서 추가되는 것이다.
> 리뷰 기준도 같다: 이 문서의 구현 규격·완료 확인·공통 규칙에 어긋나는 것만 지적하고, 나머지는 "제안"으로 분리한다.

### J. 모듈과 허용 의존 (Part 2 끝 기준)

| 모듈 | `package-info.java`의 `allowedDependencies` | 공개 API (`{module}.api`) |
|---|---|---|
| member | `common` | `MemberApi` (2-2) |
| shop | `common` | `ShopApi` |
| product | `common`, `shop::api` | `ProductApi` |
| payment | `common`, `member::api`(5-1부터. 탈퇴 consumer가 `MemberTopics`를 쓴다) | `PaymentApi` (2-4) |
| order | `common`, `product::api`, `shop::api`, `member::api`, `payment::api` | 없음 (Part 3에서 추가) |

- `{module}/api/package-info.java`에 `@NamedInterface("api")`. 구현체 `XxxApiImpl`은 `{module}.application`의 **package-private** 클래스다.
- 모듈 API의 쓰기 메서드는 비즈니스 키(orderId, refundId)를 받아 **같은 키로 다시 불러도 결과가 같게** 만든다.
- 모듈 API 메서드는 `@Transactional`(REQUIRED)이라 호출자 트랜잭션에 합류한다. 예외로 표시한 메서드(외부 호출을 하는 `PaymentApi.callConfirm` 등)만 트랜잭션 밖에서 불러야 한다.

### K. 외부 호출과 트랜잭션
- `@Transactional` 메서드 안에서 Toss를 부르지 않는다. 외부 호출이 있는 유스케이스 서비스는 **클래스에 `@Transactional`을 붙이지 않고**, Boot가 만들어 주는 `TransactionTemplate` 빈으로 구간을 나눈다.
```java
Started started = tx.execute(status -> begin(...));       // 트랜잭션 1: 상태 선점 + 커밋
PgOutcome outcome = paymentApi.callConfirm(started.paymentId());   // 트랜잭션 밖
return tx.execute(status -> applier.apply(...));          // 트랜잭션 2: 결과 반영
```
- 같은 클래스의 `@Transactional` 메서드를 `this.method()`로 부르면 트랜잭션이 적용되지 않는다. 결과 반영처럼 여러 곳에서 재사용하는 트랜잭션 로직은 **별도 빈**으로 뺀다.

### L. 상태 전이 방식 (두 가지)
| 방식 | 언제 | 구현 |
|---|---|---|
| CAS UPDATE | 엔티티를 읽지 않고 "지금 이 상태일 때만" 바꿔야 할 때 (결제 시작, 만료, 결제 결과 반영) | `@Modifying(clearAutomatically = true) @Query("update X x set x.status = :to, x.version = x.version + 1, ... where x.id = :id and x.status = :from")` → 반환 `int`. 1이면 내가 바꿈, 0이면 이미 다른 흐름이 바꿈 |
| 엔티티 메서드 + `@Version` | 그 외 (애그리거트 안의 여러 행을 함께 바꿀 때) | `order.markPaid(now)` 같은 도메인 메서드. 동시 수정은 낙관적 락으로 한쪽 실패 |

- CAS 쿼리 뒤에 엔티티가 필요하면 **CAS 다음에** 조회한다(`clearAutomatically`가 1차 캐시를 비우므로, 앞서 읽은 엔티티는 준영속이 된다).
- enum은 쿼리에 문자열로 쓰지 않고 파라미터로 바인딩한다.

### M. 시간 흉내 내기
- 테스트에서 "15분 경과", "7일 경과"는 `Clock`을 바꾸지 않고 **DB의 시각 컬럼을 과거로 UPDATE**해서 만든다(예: `UPDATE orders.orders SET expires_at = now() - interval '1 minute' WHERE id = ?`). 컨텍스트를 새로 띄우지 않아 빠르다.
- 잡은 테스트에서 직접 호출한다. `application-test.yaml`에 `myroutine.scheduling.enabled: false`(2-5에서 만듦)를 둬서 스케줄러가 테스트 중에 끼어들지 않게 한다.

### N. 테스트 지원 (Part 2에서 추가)
| 클래스 | 위치 | 단계 |
|---|---|---|
| `TestFixtures` 확장 | `support/TestFixtures` | 이미 있는 것(Part 1): `signup(email)` → memberId, `token(email)`(가입 + access 토큰), `login(email)`, `changeStatus(memberId, status)`, `openShop(memberId, businessNumber)`, `closeShop(shopId)`, `registerProduct(memberId, shopId)`(가격 10,000·재고 10), `registerProduct(memberId, shopId, category, initialStock)`, `hideProduct`·`discontinueProduct(productId)`, `registerProductImage(memberId, shopId, productId)`(1-10, 업로드·등록까지), `putToStorage(uploadUrl, headers, body)`. 이미 가입한 이메일의 토큰은 `token`이 아니라 `login`으로 받는다(공통 규칙 P-4). 단계마다 추가하는 헬퍼는 각 단계의 "테스트 준비"에 표로 적었고, [N-1](#n-1-단계별-testfixtures-추가)에 이름 색인이 있다. 같은 이름의 오버로드는 인자 타입이 겹치지 않게 한다 |
| `FakePgServer` | `support/FakePgServer`, `support/Behavior` | 2-3 |
| `ConcurrencyRunner` | `support/ConcurrencyRunner` | 2-1(장바구니 동시성 테스트에서 처음 사용): `static <T> List<Result<T>> run(int threads, Callable<T> task)` — `record Result<T>(T value, Throwable error) { boolean ok() { return error == null; } }`. 스레드 풀 + latch 2개(ready·start)로 동시에 출발시키고, 각 작업이 돌려준 `Future`를 **제출 순서대로** 모아 반환한다(여러 스레드가 공유 리스트에 `add`하지 않는다). **서로 다른 작업**을 동시에 돌리는 `static <T> List<Result<T>> run(List<Callable<T>> tasks)` 오버로드도 둔다 — 2-4(서로 다른 `Idempotency-Key`), 2-8(서로 다른 품목), 2-10(수동 확정과 배치)이 쓴다. `run(int, Callable)`은 `Collections.nCopies`로 이 메서드를 부른다. 1-9의 `StockReservationConcurrencyTest`에 있는 latch 코드를 여기로 옮겨 재사용한다(1-9 테스트를 바꿀지는 선택) |

#### N-1. 단계별 `TestFixtures` 추가

각 단계의 "완료 확인" 테스트를 읽고, **처음 필요해지는 단계**에 붙였다. 메서드별 만드는 방법·쓰는 곳은 위로 올라오지 않아도 보이게 **각 단계의 "테스트 준비"**에 적었고, 여기에는 원칙과 이름 색인만 둔다. 메서드 이름·인자는 권장안이다(테스트를 쓰다 바꿔도 된다).

**원칙**
- 상태는 **서비스를 직접 호출**해서 만든다(`signup`·`registerProduct`와 같다). MockMvc로 만들지 않는다. 그래서 `checkout`도 `token`이 아니라 `memberId`를 받는다.
- 시각을 옮기거나 상태를 망가뜨리는 조작은 **JDBC**로 한다(`hideProduct`·`closeShop`과 같다). 로드맵 테스트 케이스가 "JDBC로 …"라고 적은 것들이다.
- HTTP 응답을 단언하는 테스트의 **요청 자체**(체크아웃·승인·취소 등)는 테스트가 MockMvc로 보낸다. 아래 헬퍼는 그 **준비 상태**를 만든다.
- 조합 헬퍼(`createPendingOrder` 등)는 아래 헬퍼를 이어 붙인 것이다. 한 단계에서 하나씩만 더한다.

| 단계 | 추가하는 헬퍼 (자세한 표는 해당 단계의 "테스트 준비") |
|---|---|
| 2-1 | `registerProduct`, `ConcurrencyRunner` |
| 2-2 | `address`, `addToCart`, `checkout`, `stockOf`, `StockSnapshot` |
| 2-3 | 없음 (`FakePgServer`·`Behavior`) |
| 2-4 | `createPendingOrder`, `confirmPayment`, `setOrderExpired` |
| 2-5 | `breakReservedStock` |
| 2-6 | `setOrderUpdatedAt`, `createPaidOrder` |
| 2-7 | `shopOrderIdOf`, `shipShopOrder`, `deliverShopOrder` |
| 2-8, 2-8b | `orderLineIdOf`, `refundIdOf`, `setRefundUpdatedAt` |
| 2-9 | `createDeliveredOrder` |
| 2-10 | `setShopOrderDeliveredAt` |

### O. Part 2 에러 코드

| 코드 | HTTP | 메시지 | 정의 위치 | 단계 |
|---|---|---|---|---|
| `CART_ITEM_NOT_FOUND` | 404 | 장바구니 항목을 찾을 수 없습니다. | `OrderErrorCode` | 2-1 |
| `CART_PRODUCT_NOT_FOUND` | 404 | 상품을 찾을 수 없습니다. | `OrderErrorCode` | 2-1 |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | Idempotency-Key 헤더가 필요합니다. | `CommonErrorCode` | 2-2 |
| `IDEMPOTENCY_IN_PROGRESS` | 409 | 같은 요청을 처리하고 있습니다. | `CommonErrorCode` | 2-2 |
| `IDEMPOTENCY_KEY_REUSED` | 422 | 같은 Idempotency-Key로 다른 요청을 보냈습니다. | `CommonErrorCode` | 2-2 |
| `ORDER_NOT_FOUND` | 404 | 주문을 찾을 수 없습니다. | `OrderErrorCode` | 2-2 |
| `OWN_SHOP_PRODUCT` | 422 | 자기 가게 상품은 주문할 수 없습니다. | `OrderErrorCode` | 2-2 |
| `PAYMENT_FAILED` | 402 | 결제에 실패했습니다. | `CommonErrorCode` | 2-4 |
| `PG_ERROR` | 502 | 결제사 처리 중 오류가 발생했습니다. | `CommonErrorCode` | 2-4 |
| `ORDER_EXPIRED` | 409 | 주문 유효시간이 지났습니다. | `OrderErrorCode` | 2-4 |
| `ORDER_ALREADY_PAID` | 409 | 이미 결제된 주문입니다. | `OrderErrorCode` | 2-4 |
| `AMOUNT_MISMATCH` | 422 | 결제 금액이 주문 금액과 다릅니다. | `CommonErrorCode` | 2-4 |
| `SHOP_ORDER_NOT_FOUND` | 404 | 가게 주문을 찾을 수 없습니다. | `OrderErrorCode` | 2-7 |
| `ORDER_LINE_NOT_FOUND` | 404 | 주문 품목을 찾을 수 없습니다. | `OrderErrorCode` | 2-8 |
| `REFUND_IN_PROGRESS` | 409 | 진행 중인 환불이 있습니다. | `OrderErrorCode` | 2-8 |
| `REFUND_NOT_FOUND` | 404 | 환불 요청을 찾을 수 없습니다. | `OrderErrorCode` | 2-9 |
| `RETURN_NOT_ALLOWED` | 409 | 반품을 요청할 수 없는 품목입니다. | `OrderErrorCode` | 2-9 |

- `PAYMENT_FAILED`·`PG_ERROR`·`AMOUNT_MISMATCH`는 order와 payment가 함께 쓰므로 `CommonErrorCode`에 둔다.

### P. Part 1에서 확인된 함정 (구현·테스트 때 함께 지킨다)

> Part 1 구현·리뷰에서 실제로 걸렸거나 직접 실행해 확인한 것이다. 리뷰 기준에 포함된다.

| # | 함정 | 규칙 | 근거 |
|---|---|---|---|
| 1 | 검증 어노테이션에 `message`가 없으면 오류 응답 `details`에 영어 기본 문구와 정규식이 그대로 나간다 | 이 문서 규격 표의 제약(`@NotNull`·`@NotBlank`·`@Size`·`@Pattern`·`@Min`·`@Max`, 쿼리 파라미터 포함)에는 [공통 규칙 E](part1-foundation.md#e-컨트롤러dto)의 표준 문구를 `message`로 붙인다. 규격 표에서는 문구를 생략한다 | Part 1 최종 리뷰(#22) |
| 2 | 요청 DTO의 원시 타입(`boolean`·`int`·`long`)은 생략하면 역직렬화 단계에서 400이 되고 `details`가 비어 원인을 알 수 없다 | 요청 DTO 필드는 래퍼 타입(`Boolean`·`Integer`·`Long`)으로 받는다. 필수면 `@NotNull`, 선택이면 생략했을 때의 값을 규격에 적는다 | 1-7 `subscribable` |
| 3 | 컨트롤러 클래스에 `@Validated`를 붙이면 쿼리 파라미터 검증 실패가 500이 된다 | `@Min`·`@Max`는 메서드 파라미터에만 붙인다(Part 1의 `HandlerMethodValidationException` 핸들러가 400으로 바꾼다) | 1-7 |
| 4 | `TestFixtures.token(email)`은 **가입까지** 한다. 이미 `signup(email)`한 이메일로 부르면 중복 가입 예외가 난다 | 가입한 회원의 토큰은 `login(email)`로 받는다 | Part 1 테스트 |
| 5 | `jsonPath(...).doesNotExist()`는 경로가 응답 모양과 맞지 않아도 통과한다(객체 응답에 `$[0].x`, 배열 응답에 `$.x`) | 값은 `value(...)`로 단언한다. null 확인은 `value(nullValue())` — 경로가 틀리면 실패한다(2026-10-10 확인). 응답이 객체인지 배열인지 먼저 본다. **필터 경로(`$.items[?(@.productId == '%s')].x`)는 결과가 리스트**다: 값 비교(`value(false)`)는 원소가 하나면 풀어서 통과하지만 매처는 리스트 그대로 받으므로 null 확인은 `value(contains(nullValue()))`로 한다(`value(nullValue())`는 `was <[null]>`로 실패, 2026-10-11 확인) | 1-5 재수정(#21), 2-1 |
| 6 | 통과하는 테스트가 실제로 결함을 막는지는 통과만으로 알 수 없다 | 핵심 장치(정렬·CAS 조건·멱등 키·검증 어노테이션)를 일부러 지우고 테스트가 실패하는지 본다. 1-9 데드락 테스트는 정렬을 지우면 50/50회 실패했다 | [공통 완료 기준](README.md#4-공통-완료-기준-모든-단계) |
| 7 | 한 쿼리에서 `List` 컬렉션 두 개 이상(중첩 포함)을 fetch하면 `MultipleBagFetchException`이 난다 | `@EntityGraph`에는 한 단계의 `List`만 넣고, 그 아래는 `default_batch_fetch_size`로 `IN` 한 번에 읽는다(2-2 상세 조회) | 2026-10-10 확인(Hibernate 7.4.5) |
| 8 | `@JdbcTypeCode(SqlTypes.JSON)` jsonb 매핑은 Boot의 Jackson 3이 아니라 Hibernate가 찾은 **Jackson 2**(`JacksonJsonFormatMapper`, `jjwt-jackson`이 끌어온 `jackson-databind` 2.19.2)로 직렬화한다 | 문자열 필드만 있는 record(`ShippingAddress`)는 그대로 저장·조회된다(확인함). 날짜·enum을 jsonb에 넣거나 jjwt 의존성이 바뀌면 다시 확인한다 | 2026-10-10 확인 |
| 9 | develop에 머지하면 VM에 자동 배포된다. 새 **필수 설정**(`@NotBlank` 프로퍼티·환경변수)이 VM `.env`에 없으면 기동 실패 → 자동 롤백 → CD 빨간불 | 필수 설정을 추가하는 PR은 **머지 전에** VM `/opt/myroutine/.env`와 `.env.ops.example`에 키를 넣는다(2-3 `TOSS_SECRET_KEY`) | 1-11 롤백 확인 |
| 10 | 자동 롤백은 이미지만 되돌리고 마이그레이션은 되돌리지 않는다 | 이전 버전 앱이 새 스키마에서 그대로 동작하게 만든다. 새 테이블·인덱스 추가는 안전하고, 기존 테이블에 `NOT NULL` 컬럼을 넣을 때는 `DEFAULT`를 둔다 | 1-11 |
| 11 | 422 상수 이름이 바뀌었다 | 422는 `HttpStatus.UNPROCESSABLE_CONTENT`(Spring 7, Part 1 코드와 같음). 402는 `PAYMENT_REQUIRED`, 502는 `BAD_GATEWAY` | Part 1 코드 |

---

## 2-1. 장바구니 (S)

**목표**: 장바구니 담기·수량 변경·삭제·조회. 조회할 때 **현재** 가격·판매 상태·재고 여부를 보여준다.

**왜 지금**: 체크아웃의 입력이다. order 모듈의 첫 코드다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| upsert | `INSERT ... ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = ...` — "없으면 넣고 있으면 수량 합산"을 한 문장으로. 동시에 담아도 행이 하나 |
| 배치 조회 | 항목마다 상품을 조회하지 않고 ID를 모아 `ProductApi.getForCheckout(ids)` **한 번** |

**정책 (이 단계에서 정함)**
- 항목당 수량 1~99. 합산 결과가 99를 넘으면 99로 맞춘다.
- 담을 때는 상품이 존재하는지만 확인한다(판매 중이 아니어도 담을 수는 있고, 조회 시 "구매 불가"로 표시).
- 장바구니 행은 처음 담을 때 만든다.

### 할 일

**1) 마이그레이션** — `db/migration/order/V{...}__order_create_cart.sql` (`CREATE SCHEMA IF NOT EXISTS orders;`)

`orders.cart`: id `pk_cart`, member_id uuid X `uk_cart_member`, created_at, updated_at

`orders.cart_item`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_cart_item` |
| cart_id | uuid | X | `fk_cart_item_cart` |
| product_id | uuid | X | |
| quantity | int | X | `ck_cart_item_quantity`: `quantity BETWEEN 1 AND 99` |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- unique `uk_cart_item_cart_product (cart_id, product_id)`

**2) order.domain**

| 클래스 | 규격 |
|---|---|
| `Cart` | `@Entity @Table(name = "cart", schema = "orders")`, `extends BaseTimeEntity`, `@Id UUID id`, `UUID memberId`. 생성은 native INSERT |
| `CartItem` | `@Entity`, `extends BaseTimeEntity`. `UUID cartId`, `UUID productId`, `int quantity`, `@Version Long version`. `void changeQuantity(int quantity)` (1~99가 아니면 `IllegalArgumentException`) |
| `CartRepository` | native `int insertIfAbsent(UUID id, UUID memberId, Instant now)` (`ON CONFLICT ON CONSTRAINT uk_cart_member DO NOTHING`), `Optional<Cart> findByMemberId(UUID memberId)` |
| `CartItemRepository` | 아래 |
| `OrderErrorCode` | `CART_ITEM_NOT_FOUND`, `CART_PRODUCT_NOT_FOUND` |

`CartItemRepository`
- native `int addQuantity(UUID id, UUID cartId, UUID productId, int quantity, Instant now)`:
```sql
INSERT INTO orders.cart_item (id, cart_id, product_id, quantity, version, created_at, updated_at)
VALUES (:id, :cartId, :productId, :quantity, 0, :now, :now)
ON CONFLICT ON CONSTRAINT uk_cart_item_cart_product
DO UPDATE SET quantity = LEAST(orders.cart_item.quantity + EXCLUDED.quantity, 99),
              version = orders.cart_item.version + 1, updated_at = EXCLUDED.updated_at
```
- `Optional<CartItem> findByIdAndCartId(UUID id, UUID cartId)`, `Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId)`, `List<CartItem> findAllByCartIdOrderByCreatedAtDesc(UUID cartId)`, `List<CartItem> findAllByIdInAndCartId(Collection<UUID> ids, UUID cartId)`, `void delete(CartItem item)`

**구현 위치 (infrastructure)** — Part 1과 같은 구조다([1-6](part1-foundation.md), [개발 가이드 §8.1](../development-guide.md)). 위 `CartRepository`·`CartItemRepository`는 `order/domain`의 **순수 인터페이스**(시그니처만, Spring Data 어노테이션 없음)이고, 구현은 `order/infrastructure`에 둔다.
- `public interface JpaCartRepository extends JpaRepository<Cart, UUID>, CartRepository` — `insertIfAbsent`의 `@Modifying @Query(nativeQuery = true, ...)`를 여기에 둔다. `findByMemberId`는 이름 규칙으로 만들어진다.
- `public interface JpaCartItemRepository extends JpaRepository<CartItem, UUID>, CartItemRepository` — `addQuantity`의 native upsert를 여기에 둔다. `find...`·`delete`는 이름 규칙·`JpaRepository`가 제공한다.
- `CartService`는 `CartRepository`·`CartItemRepository`(domain 인터페이스)로 주입받는다. `Jpa*`를 직접 참조하지 않는다.

**3) order.application** — `CartService`

| 메서드 | 규격 |
|---|---|
| `@Transactional UUID addItem(UUID memberId, UUID productId, int quantity)` | `productApi.getForCheckout(List.of(productId))`가 비면 `CART_PRODUCT_NOT_FOUND` → `cartRepository.insertIfAbsent` → `findByMemberId` → `addQuantity(Ids.newId(), ...)` → `findByCartIdAndProductId`로 항목 ID 반환 |
| `@Transactional CartResult changeQuantity(UUID memberId, UUID cartItemId, int quantity)` | 내 장바구니의 항목이 아니면 `CART_ITEM_NOT_FOUND` |
| `@Transactional void removeItem(UUID memberId, UUID cartItemId)` | 같음 |
| `@Transactional(readOnly = true) CartResult getCart(UUID memberId)` | 장바구니가 없으면 빈 결과. 항목들의 productId를 모아 `getForCheckout` **1번** → 항목별로 합침 |

`CartResult(List<CartLineResult> items, long totalAmount)` — totalAmount는 구매 가능한 항목만 합산
`CartLineResult(UUID cartItemId, UUID productId, UUID shopId, String name, String thumbnailKey, long unitPrice, int quantity, long lineAmount, boolean purchasable, String unavailableReason)` — 값은 `ProductForCheckout`(`productId, shopId, name, thumbnailKey, price(Money), onSale, subscribable, inStock`)에서 옮긴다. `unitPrice = price.amount()`
- `unavailableReason`: 상품이 결과에 없으면 `"NOT_FOUND"`(shopId 등은 null, 가격 0), `onSale == false`면 `"NOT_ON_SALE"`, `inStock == false`면 `"OUT_OF_STOCK"`, 구매 가능하면 null

**4) order.web** — `CartController`

| API | 요청 | 응답 |
|---|---|---|
| `GET /api/cart` | | 200 `CartResponse` (Result와 같은 구조. 단 품목의 `thumbnailKey` 대신 **`thumbnailUrl`** — 1-10 규칙대로 web에서 `ImageUrls.toUrl(key)`로 바꾼다. `CartResponse.from(result, imageUrls)`) |
| `POST /api/cart/items` | `AddCartItemRequest(@NotNull UUID productId, @NotNull @Min(1) @Max(99) Integer quantity)` | 201 `CartItemIdResponse(UUID cartItemId)` |
| `PATCH /api/cart/items/{id}` | `ChangeQuantityRequest(@NotNull @Min(1) @Max(99) Integer quantity)` | 200 `CartResponse` |
| `DELETE /api/cart/items/{id}` | | 204 |

- `order/package-info.java`: `@ApplicationModule(allowedDependencies = {"common", "product::api"})` (이후 단계에서 추가)

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/web/CartControllerTest` | [ ] 같은 상품 두 번 담기(2개, 3개) → 1행, 수량 5 / [ ] 98개 + 5개 → 99 / [ ] 상품 가격을 바꾸면(PATCH 상품) 장바구니 조회에도 바뀐 가격 / [ ] HIDDEN 상품 → `purchasable=false, unavailableReason="NOT_ON_SALE"`, 재고 0 → `"OUT_OF_STOCK"`, totalAmount에서 제외 / [ ] 다른 회원의 장바구니 항목 PATCH·DELETE → 404 `CART_ITEM_NOT_FOUND` / [ ] 없는 상품 담기 → 404 `CART_PRODUCT_NOT_FOUND` / [ ] **장바구니 상품이 2개일 때와 20개일 때 조회 SQL 수가 같다** (1-7의 Hibernate statistics 방식) |
| `order/application/CartConcurrencyTest` | [ ] 같은 상품을 동시에 10번 1개씩 담기 → 1행, 수량 10 |

**테스트 준비** ([N-1](#n-1-단계별-testfixtures-추가))

**이 단계에서 추가하는 헬퍼** (`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `registerProduct(memberId, shopId, long price, int stock)` → productId | `RegisterProductService.register` (이미 있음) | `CartControllerTest` 가격·재고 0 케이스 |
| `ConcurrencyRunner` (`support/`) | 공통 규칙 N 표의 `ConcurrencyRunner` 행(스레드 풀 + latch 2개, 결과는 제출 순서대로) | `CartConcurrencyTest` |

이미 있는 헬퍼(앞 단계·Part 1에서 만든 것, 그대로 쓴다): `signup`, `token`, `login`, `changeStatus`, `openShop`, `closeShop`, `registerProduct`, `hideProduct`, `discontinueProduct`, `registerProductImage`, `putToStorage`

- 판매자·가게·상품: `signup` → `openShop` → `registerProduct(memberId, shopId, price, stock)`. 구매자는 판매자와 **다른 회원**으로 만들고, 컨트롤러 테스트의 장바구니는 `POST /api/cart/items`를 MockMvc로 호출해 만든다.
- 케이스별: HIDDEN은 `hideProduct(productId)`, 재고 0은 `registerProduct(.., price, 0)`, 가격 변경은 상품 수정 API(PATCH)를 MockMvc로 호출한다. 다른 회원은 `signup` 후 `login`(공통 규칙 P-4).
- 쿼리 수: 상품 2개일 때와 20개일 때를 각각 만들어(`registerProduct` 반복) 1-7 `getPublicProductsQueryCountIsConstant`와 같은 방식으로 센다.
- 동시성: `ConcurrencyRunner.run(10, () -> cartService.addItem(memberId, productId, 1))` → 모두 `ok()`, `cart_item` 1행·수량 10. 러너(`support/ConcurrencyRunner`)를 먼저 만든다.
- 일부러 깨뜨려 확인(P-6): `addQuantity`를 "조회 후 INSERT/UPDATE"로 바꿔 동시성 테스트가 실패하는지 본다.

**리뷰 때 물어볼 것**
- 장바구니에 가격을 저장하지 않는 이유는?
- "조회 후 없으면 INSERT, 있으면 UPDATE"로 짜면 동시에 담을 때 무슨 일이 생기나?

---

## 2-2. 체크아웃: 주문 생성과 재고 예약 (+ 멱등 API, 주문 조회) (L)

**목표**
- 장바구니(여러 가게 상품)에서 고른 항목으로 주문서를 만든다. 서버가 가격을 확정하고 재고를 예약한다.
- 주문은 항상 결제 대기(PENDING_PAYMENT)로 만들어진다. 결제(Toss 승인)는 2-4에서 붙인다.
- 같은 요청을 두 번 보내도 주문은 하나다.
- 구매자가 내 주문 목록과 상세를 본다(2-4의 "결제 확인 중" 폴링에 필요).
- 체크아웃은 장바구니 항목을 주문 품목으로 **복사**만 하고 장바구니는 건드리지 않는다. 결제가 완료되면 주문한 수량만큼 장바구니에서 뺀다(2-4). 실패·만료면 장바구니는 그대로다.

**왜 지금**: 1-9(재고 예약)를 주문과 처음 연결하는 지점이다. 여러 모듈(product·shop·member)을 한 유스케이스에서 쓴다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 애그리거트 | `Order`(루트)가 `ShopOrder`·`OrderLine`을 만들고 바꾼다. 바깥에서는 루트를 통해서만 수정. 애그리거트 안에서만 `@OneToMany`를 쓴다 |
| 여러 모듈을 한 트랜잭션으로 | 주문 생성 + 재고 예약을 하나의 로컬 트랜잭션으로. 하나라도 실패하면 전부 롤백. **모놀리스라서 가능한 것**이고 Stage 3에서는 Saga가 된다 (ADR-002) |
| 서버 가격 확정 | 요청에 가격을 받지 않는다. 상품 모듈에서 조회한 가격으로 계산 (POL-17, As-Is ORD-01) |
| 멱등 API (`Idempotency-Key`) | 클라이언트가 보낸 키로 처리 결과를 저장해두고, 같은 키로 다시 오면 저장된 응답을 그대로 돌려준다 |
| AOP | `@Idempotent`가 붙은 컨트롤러 메서드를 감싸는 `@Aspect`. 키 확인 → 실행 → 응답 저장을 한 곳에서 처리 |
| 장바구니 차감 | 연결 컬럼 없이 `(cart_id, product_id)`로 찾아 결제 완료 때 주문 수량만큼 뺀다. 행 단위 원자적 SQL이라 구매자가 동시에 수량을 바꿔도 충돌하지 않는다(2-4) |

**정책 (이 단계에서 정함)**
- 주문 유효시간 15분(POL-05). 설정 `myroutine.order.payment-ttl: 15m`
- PG 주문번호(`pgOrderId`): `"ORD-" + orderId` (Toss orderId 규칙: 6~64자, 영문·숫자·`-`·`_`)
- 한 주문의 품목은 최대 50개, 같은 상품은 한 번만.
- **장바구니는 체크아웃 때 건드리지 않는다.** 주문 품목은 장바구니 항목을 복사한 스냅샷이다. 결제가 완료(PAID)되면 2-4에서 주문 수량만큼 장바구니에서 뺀다(장바구니 수량이 주문 수량 이하면 삭제, 많으면 차감). 결제 실패·만료는 장바구니에 아무것도 하지 않는다. 결제 중에 구매자가 수량을 늘렸거나 같은 상품을 더 담았어도 주문한 수량만 빠진다. (2026-10-12 결정. WooCommerce·Shopify처럼 결제가 확정될 때까지 장바구니를 유지한다. 체크아웃 때 바로 지우면 결제가 실패했을 때 구매자가 다시 담아야 한다)
- 주문과 장바구니 사이에 **연결을 두지 않는다**(`cart_item`에 주문 ID 컬럼 없음). 같은 장바구니 항목으로 체크아웃을 여러 번 하면(키가 다르면) 결제 대기 주문이 둘 이상 생길 수 있다. 각 주문은 독립적으로 재고를 15분 예약하고, 결제하지 않으면 만료로 풀린다. 대형 쇼핑몰에서도 같은 장바구니의 주문서가 둘 생기고 둘 다 결제할 수 있는 것을 확인했다(2026-10-12). **막지 않기로 한 선택**이다.
- **알고 있는 한계**
  1. **중복 주문**: 같은 항목으로 주문이 둘 생길 수 있고, 둘 다 결제하면 주문 2건·결제 2건이 된다. 시스템에서는 각각 정상적인 별개의 주문이다(각 결제는 정확히 한 주문에 한 번 반영되어 INV-02가 유지되고, **같은 주문의 이중 결제는 막힌다**). 구매자가 의도하지 않았다면 결제 전에는 주문 취소 API(2-4)로 바로 풀고, 결제됐다면 발송 전에 한쪽을 품목 취소해 환불받는다(2-8).
  2. **결제 중 장바구니 표시**: 장바구니에는 결제 대기 중인 항목도 평소처럼 보이고, 재고가 그 주문에 예약되어 `OUT_OF_STOCK`으로 보일 수 있다.
  3. **차감은 상품 단위**: 결제 중에 항목을 지우고 같은 상품을 새로 담으면 새로 담은 수량에서 주문 수량만큼 빠진다.
  4. **재고 점유 남용**: 회원당 결제 대기 주문 수와 체크아웃 횟수에 상한이 없어, 한 회원이 체크아웃을 반복하면 인기 상품 재고를 15분씩 계속 붙잡을 수 있다(항목당 수량은 최대 99, 품목은 최대 50). 이번 단계에서는 막지 않는다. 방어는 Part 4(이메일 미인증 회원의 체크아웃 403)와 Part 7(예약 비율 모니터링)에 걸쳐 있고, 직접 막으려면 아래 "추가 항목"의 회원당 상한·속도 제한을 쓴다.
- 차감은 장바구니를 거친 주문만 한다. Part 2의 주문은 모두 장바구니에서 만든 NORMAL이라 조건이 필요 없고, 구독 회차 주문이 생기는 Part 5에서 `type == NORMAL` 조건을 더한다. "바로 구매"를 만들게 되면 출처를 구분할 표시가 필요하다.
- 주문을 사람에게 보여줄 때는 주문 ID를 쓴다(별도 주문번호 없음).
- 이메일 미인증 회원의 주문 제한은 Part 4에서 붙인다.
- 멱등 키: 회원 단위로 구분, 24시간 보관. **2xx 응답만 저장**하고, 예외로 끝나면 키를 지워 같은 키로 재시도할 수 있게 한다. 만료된 키는 다음 요청이 지우고 새로 점유한다.
- **채택하지 않은 선택지**: 검토했고 이번 Part에서는 하지 않기로 한 것이다. 지금의 동작, 하지 않은 이유, 하려면 먼저 필요한 것을 남긴다.
  1. **중복 체크아웃 방지**(위 한계 1): 지금은 막지 않는다. 각 주문이 재고를 독립적으로 예약하고 만료로 풀린다. 검토한 선택지(비용 순):
     - **구매자 취소 + 회원당 1건**: `UNIQUE (member_id) WHERE status IN ('PENDING_PAYMENT','PAYMENT_IN_PROGRESS')`(구독 주문은 `type`으로 제외). 결제 전 취소 API(`PENDING_PAYMENT → CANCELLED`)는 2-4에서 따로 만들므로(2026-10-13) 이 선택지에 남는 건 이 인덱스다. 새 체크아웃은 409(`details.orderId`)이고 구매자가 이어서 결제하거나 취소해야 한다. 재주문이 막히는 UX 비용이 있다
     - **항목 단위**: `order_line.cart_item_id` + 부분 unique 인덱스(항목당 PENDING 품목 1개). 같은 장바구니 항목의 중복만 막고, 결제 중 표시·정확한 항목 차감도 같은 컬럼으로 가능하다. 이탈 후 15분은 재주문이 막힌다
     - **새 체크아웃이 이전 주문을 대체**: 같은 회원의 `PENDING_PAYMENT`를 `EXPIRED`로 바꾸고 예약을 해제한 뒤 새 주문을 만든다. 결제 가능한 주문을 하나로 만들어 중복 주문의 결제까지 막지만, 해제와 예약이 한 트랜잭션에서 이어져 **상품 재고 행의 락 순서가 엇갈리므로**(이전 주문 상품 ∪ 새 상품을 정렬해 먼저 잠가야 함) 같은 회원의 동시 체크아웃 직렬화와 함께 필요하다
  2. **멱등 키 재점유**: `PROCESSING`으로 남은 키를 일정 시간 뒤 다시 점유하는 방식. 비즈니스 트랜잭션과 `complete`가 서로 다른 트랜잭션이라 "실행 전에 죽음"과 "커밋 후에 죽음"을 구분할 수 없어, 재점유하면 주문이 이미 만들어진 채로 같은 요청이 다시 처리된다(주문 2건·재고 이중 예약). 지금은 키가 만료(24시간)될 때까지 409로 막고, 구매자는 새 키로 다시 체크아웃한다(먼저 만든 주문은 15분 뒤 만료된다). 하려면 `complete`를 비즈니스 트랜잭션과 한 트랜잭션으로 묶는 설계가 먼저 필요하다

### 할 일

**1) 멱등 공통 장치** — 패키지 `common.idempotency`

의존성: `implementation 'org.springframework.boot:spring-boot-starter-aspectj'`(버전은 Boot BOM). Boot 4에서 `spring-boot-starter-aop`가 이 이름으로 바뀌었다 — Maven Central에서 `spring-boot-starter-aop`는 4.0.0-M2가 마지막이고 `spring-boot-starter-aspectj`는 4.1.1이 있다(2026-10-09 확인).

마이그레이션 `db/migration/common/V{...}__common_create_idempotency_key.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| member_id | uuid | X | `pk_idempotency_key (member_id, idempotency_key)` |
| idempotency_key | varchar(100) | X | |
| request_hash | varchar(64) | X | SHA-256 hex |
| status | varchar(20) | X | `ck_idempotency_key_status`: `IN ('PROCESSING','COMPLETED')` |
| response_status | int | O | |
| response_body | jsonb | O | |
| expires_at | timestamptz | X | 인덱스 `idx_idempotency_key_expires_at` |
| created_at, updated_at | timestamptz | X | |

| 클래스 | 규격 |
|---|---|
| `Idempotent` | `@Target(METHOD) @Retention(RUNTIME) public @interface Idempotent {}` |
| `IdempotencyRepository` | `JdbcTemplate`으로 직접 작성(엔티티 없음, `@Repository`). 메서드 아래 |
| `IdempotencyAspect` | `@Aspect @Component`. `@Around("@annotation(com.myroutine.common.idempotency.Idempotent)")` |
| `CommonErrorCode` | `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_IN_PROGRESS`, `IDEMPOTENCY_KEY_REUSED` 추가 |

`IdempotencyRepository` (각 메서드는 자체 트랜잭션으로 즉시 커밋된다. 컨트롤러는 트랜잭션 밖이므로)
- `boolean claim(UUID memberId, String key, String hash, Instant now, Instant expiresAt)`: `INSERT ... VALUES (..., 'PROCESSING', ...) ON CONFLICT DO NOTHING` → 1이면 true
- `Optional<StoredKey> find(UUID memberId, String key)` — `StoredKey(String hash, String status, Integer responseStatus, String responseBody, Instant expiresAt, Instant updatedAt)`
- `void complete(UUID memberId, String key, int status, String bodyJson, Instant now)`
- `void delete(UUID memberId, String key)`
- `void deleteExpired(UUID memberId, String key, Instant now)`: `DELETE ... WHERE member_id = ? AND idempotency_key = ? AND expires_at < ?`(반환값은 쓰지 않는다. 뒤따르는 `claim`의 성공 여부가 결과를 말해 준다). **만료 조건이 SQL에 있어야 한다**: 만료를 읽은 뒤 다른 요청이 키를 지우고 새로 점유했다면 조건 없는 `delete`가 그 살아 있는 키를 지워 두 요청이 모두 처리된다

`IdempotencyAspect.around(ProceedingJoinPoint pjp)` 순서
1. `HttpServletRequest`는 `RequestContextHolder`에서, memberId는 `SecurityContextHolder`의 `AuthClaims`에서 얻는다
2. 헤더 `Idempotency-Key`가 없거나 1~100자가 아니면 `BusinessException(IDEMPOTENCY_KEY_REQUIRED)`
3. `hash` = SHA-256(`HTTP 메서드 + " " + 요청 URI + "\n" + 메서드 인자 중 @RequestBody가 붙은 인자의 JSON`). JSON은 주입받은 `ObjectMapper`(`tools.jackson.databind.ObjectMapper`. Part 1의 `RestAuthenticationEntryPoint`와 같은 타입으로 통일한다)로 만든다
4. `claim` 성공 → 5로 / 실패 → `find`. 아래를 **위에서부터 순서대로** 판단한다(앞 조건이 먼저 걸린다):
   1. 비어 있으면(`claim` 실패와 `find` 사이에 다른 요청이 키를 지움) → `IDEMPOTENCY_IN_PROGRESS`. 클라이언트가 재시도하면 새로 점유된다
   2. **만료됐으면**(`expiresAt`이 `now`보다 앞, 상태·해시와 무관) **`deleteExpired(memberId, key, now)`** 후 `claim`을 한 번 더 → 성공하면 5로, 실패하면 `IDEMPOTENCY_IN_PROGRESS`. 만료된 키는 어떤 요청이든 지우고 새로 점유한다(정책). 일반 `delete`를 쓰면 안 된다: 읽은 뒤 다른 요청이 새로 점유한 키를 지워 같은 키가 두 번 처리될 수 있다(`deleteExpired`는 그 사이 새로 점유된 키는 만료 조건에 걸리지 않아 지우지 않고, 뒤따르는 `claim`이 실패해 409가 된다)
   3. 해시가 다르면 `IDEMPOTENCY_KEY_REUSED`
   4. COMPLETED → 저장된 응답 반환: `ResponseEntity.status(responseStatus).body(objectMapper.readTree(responseBody))`
   5. 그 외(PROCESSING) → `IDEMPOTENCY_IN_PROGRESS`
   - 2번에서 재점유에 성공한 경우만 `throw`·`return` 없이 이 단계를 빠져나가도록 쓰면, 5~7은 `claim`이 성공한 경로를 위해 **한 번만** 쓰면 된다
5. `result = pjp.proceed()` — 예외가 나면 `delete` 후 다시 던진다
6. `result`는 `ResponseEntity<?>`여야 한다(아니면 `IllegalStateException`). 2xx면 `complete(..., status, objectMapper.writeValueAsString(body))`, 아니면 `delete`
7. `result` 반환
- `@Idempotent` 메서드에는 반드시 `@CurrentMember`가 있어 인증된 요청이다.
- **이 단계에서 붙일 곳**: `POST /api/shops`(1-6), `POST /api/shops/{shopId}/products`(1-7), `POST .../stock-adjustments`(1-8), `POST /api/orders/checkout`
- **붙이면 기존 호출이 깨진다**: 헤더 없이 부르는 요청은 400 `IDEMPOTENCY_KEY_REQUIRED`가 된다. 이 API를 MockMvc로 부르는 Part 1 테스트(`ShopControllerTest` 2곳, `SellerProductControllerTest` 7곳: 상품 등록 6·재고 조정 1)에 `.header("Idempotency-Key", UUID.randomUUID().toString())`를 더한다. 검증 오류(400 `INVALID_REQUEST`)로 끝나는 요청은 어스펙트 앞에서 막히므로 헤더가 필요 없다(구현하다 확인, 2026-10-12). `TestFixtures`는 서비스를 직접 부르므로 영향이 없다. 로드맵 1-11의 VM 풀스택 `curl`(가게·상품 등록)에도 `-H "Idempotency-Key: $(uuidgen)"`가 필요해진다. CORS는 1-11에서 `Idempotency-Key` 헤더를 이미 허용했다

**2) member.api — 배송지 조회**
```java
public interface MemberApi {
    ShippingAddressInfo getAddress(UUID memberId, UUID addressId);   // 없거나 남의 것이면 MEMBER_ADDRESS_NOT_FOUND
}
public record ShippingAddressInfo(String recipient, String phone, String zipcode, String address1, String address2) {}
```
- `MemberApiImpl` (member.application, package-private, `@Transactional(readOnly = true)`), `member/api/package-info.java`에 `@NamedInterface("api")`
- `order/package-info.java`에 `"shop::api", "member::api"` 추가

**3) 마이그레이션** — `db/migration/order/V{...}__order_create_orders.sql`

`orders.orders`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_orders` |
| member_id | uuid | X | |
| type | varchar(20) | X | DEFAULT 'NORMAL', `ck_orders_type`: `IN ('NORMAL','SUBSCRIPTION')` |
| status | varchar(30) | X | `ck_orders_status`: `IN ('PENDING_PAYMENT','PAYMENT_IN_PROGRESS','PAID','PAYMENT_FAILED','EXPIRED','CANCELLED')` |
| total_amount | bigint | X | `ck_orders_total`: `> 0` — 결제 금액 그 자체 (INV-01) |
| shipping_address | jsonb | X | 배송지 스냅샷 |
| expires_at | timestamptz | X | |
| paid_at | timestamptz | O | |
| failure_reason | varchar(200) | O | 결제 실패·취소 사유 (2-4) |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 인덱스 `idx_orders_member_id_created_at (member_id, created_at DESC, id DESC)`

`orders.shop_order`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_shop_order` |
| order_id | uuid | X | `fk_shop_order_orders` |
| shop_id | uuid | X | `uk_shop_order_order_shop (order_id, shop_id)` |
| shop_name | varchar(50) | X | 스냅샷 |
| status | varchar(20) | X | `ck_shop_order_status`: `IN ('PENDING','PAID','SHIPPED','DELIVERED','COMPLETED','CANCELLED')` |
| carrier | varchar(30) | O | |
| tracking_number | varchar(50) | O | |
| shipped_at, delivered_at, completed_at | timestamptz | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

`orders.order_line`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_order_line` |
| shop_order_id | uuid | X | `fk_order_line_shop_order` |
| product_id | uuid | X | |
| product_name | varchar(100) | X | 스냅샷 |
| thumbnail_key | varchar(255) | O | 스냅샷 |
| unit_price | bigint | X | `> 0` |
| quantity | int | X | `> 0` |
| line_amount | bigint | X | `ck_order_line_amount`: `line_amount = unit_price * quantity` |
| refunded_amount | bigint | X | DEFAULT 0, `ck_order_line_refunded`: `refunded_amount >= 0 AND refunded_amount <= line_amount` (INV-06) |
| status | varchar(20) | X | `ck_order_line_status`: `IN ('PENDING','PAID','SHIPPED','DELIVERED','CONFIRMED','CANCELLED','RETURN_REQUESTED','RETURNED')` |
| confirmed_at | timestamptz | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 인덱스 `idx_order_line_shop_order_id (shop_order_id)`: 주문 상세는 품목을 `WHERE shop_order_id IN (...)`(배치 로딩)으로 읽는다. FK는 인덱스를 자동으로 만들지 않으므로 따로 둔다(`orders`·`shop_order`는 위의 인덱스·`uk_shop_order_order_shop`이 조회 경로를 받친다)
- 가게주문 상태에 `PENDING`(결제 전)이 있다: 체크아웃 때 가게주문이 함께 만들어지기 때문이다. `PENDING → PAID`(결제 완료), `PENDING → CANCELLED`(만료·결제 실패).

**4) order.domain — Order 애그리거트 (이 단계의 핵심, 단위 테스트를 가장 많이)**

| 클래스 | 규격 |
|---|---|
| `OrderStatus` | 전이: `PENDING_PAYMENT → {PAYMENT_IN_PROGRESS, EXPIRED, CANCELLED}`, `PAYMENT_IN_PROGRESS → {PAID, PAYMENT_FAILED}` (결제는 항상 승인 시작을 거친다. `PAYMENT_FAILED`는 승인 시작 뒤의 PG 거절·보상만이고, 결제 전 구매자 취소는 `CANCELLED`다) |
| `ShopOrderStatus` | `PENDING → {PAID, CANCELLED}`, `PAID → {SHIPPED, CANCELLED}`, `SHIPPED → {DELIVERED}`, `DELIVERED → {COMPLETED}` |
| `OrderLineStatus` | `PENDING → {PAID, CANCELLED}`, `PAID → {SHIPPED, CANCELLED}`, `SHIPPED → {DELIVERED}`, `DELIVERED → {CONFIRMED, RETURN_REQUESTED}`, `RETURN_REQUESTED → {DELIVERED, RETURNED}` |
| `ShippingAddress` | `public record ShippingAddress(String recipient, String phone, String zipcode, String address1, String address2)` — `Order`에 `@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")`로 매핑. 문자열 필드 record의 jsonb 저장·조회는 확인했다(Hibernate가 Jackson 2로 직렬화, 공통 규칙 P-8) |
| `CheckoutLine` | `public record CheckoutLine(UUID productId, UUID shopId, String shopName, String productName, String thumbnailKey, Money unitPrice, int quantity)` |
| `Order` | `@Entity @Table(name = "orders", schema = "orders")`, `extends BaseTimeEntity`. 필드는 컬럼과 1:1(`Money totalAmount`). `@OneToMany(cascade = CascadeType.ALL) @JoinColumn(name = "order_id", nullable = false, updatable = false) @OrderBy("createdAt") private List<ShopOrder> shopOrders = new ArrayList<>();` |
| `ShopOrder` | `@Entity`, `extends BaseTimeEntity`. 같은 방식으로 `@OneToMany(cascade = ALL) @JoinColumn(name = "shop_order_id", nullable = false, updatable = false) List<OrderLine> lines`. 조회용으로 `@Column(name = "order_id", insertable = false, updatable = false) private UUID orderId;` |
| `OrderLine` | `@Entity`, `extends BaseTimeEntity`. 조회용 `@Column(name = "shop_order_id", insertable = false, updatable = false) private UUID shopOrderId;` |
| `OrderRepository` | 아래 |
| `OrderErrorCode` | `ORDER_NOT_FOUND`, `OWN_SHOP_PRODUCT` 추가 |

`Order` 메서드 — 누가 언제 부르는지와 어떤 상태여야 하는지를 함께 본다

| 메서드 | 누가·언제 | 전제 (어기면) | 하는 일 |
|---|---|---|---|
| `static Order checkout(UUID memberId, List<CheckoutLine> lines, ShippingAddress address, Instant now, Duration paymentTtl)` | `CheckoutService` 7번. 주문을 처음 만든다 | `lines`가 비어 있지 않다(비면 `IllegalArgumentException`. 사용자 입력은 `@NotEmpty`가 앞에서 막으므로 호출 코드의 버그를 드러내는 방어다) | ① `shopId`로 묶는다(`LinkedHashMap`, 처음 나온 순서 유지) ② 가게마다 `ShopOrder.create(shopId, shopName, 그 가게의 lines)`. `shopName`은 그 가게 첫 줄의 값 ③ `id = Ids.newId()`, `type = "NORMAL"`, status `PENDING_PAYMENT`, `expiresAt = now + paymentTtl`, `totalAmount` = **만들어진 품목들의 `lineAmount` 합**(입력 `lines`에서 `단가 × 수량`을 다시 계산하지 않는다. 공식이 `OrderLine.create` 한 곳에만 있게 해서 INV-01이 구조로 지켜진다) |
| `String pgOrderId()` | PG 호출(2-3·2-4) | | `"ORD-" + id`. 컬럼이 아니라 계산한 값이다 |
| `void markPaid(Instant now)` | 2-4 PG 승인 확인 후 | status가 **`PAYMENT_IN_PROGRESS`**(`PENDING_PAYMENT`에서 바로 부르면 `INVALID_STATE_TRANSITION`. 결제는 항상 승인 시작을 거친다) | status `PAID`, `paidAt = now`, 모든 가게주문·품목 `PENDING → PAID` |
| `void markPaymentFailed(String reason)` | 2-4 PG 거절 확인 후, 2-6 대사의 미승인·보상 | status가 **`PAYMENT_IN_PROGRESS`**(`PENDING_PAYMENT`에서 바로 부르면 `INVALID_STATE_TRANSITION`. 결제 전 구매자 취소는 `CANCELLED`로 가는 별도 경로다) | status `PAYMENT_FAILED`, `failureReason = reason`을 **200자로 자른다**(컬럼이 `varchar(200)`이라 넘으면 실패를 기록하려다 DB 오류가 나 처리 전체가 롤백된다), `cancelPendingChildren()` |
| `void cancelPendingChildren()` | `markPaymentFailed`, 2-5 만료, 2-4 구매자 취소 | | **`PENDING`인 가게주문만** `CANCELLED`로 바꾼다(품목도 함께). `PAID`는 건드리지 않는다: 상태 표가 `PAID → CANCELLED`를 허용하므로 `PENDING` 필터 없이 부르면 결제 완료된 가게주문·품목이 조용히 취소된다 |
| `List<UUID> productIds()`, `Map<UUID, Integer> quantitiesByProduct()` | `CheckoutService` 8번 `productApi.reserve`의 입력 | | 모든 품목의 상품 ID, 상품별 수량. 한 주문에서 같은 상품은 한 번만 들어오므로(장바구니는 상품당 한 행, 상품은 한 가게에만 속함) 같은 상품을 합산할 필요가 없다 |

**"결제 실패 처리"와 "취소"는 층이 다르다**: 주문의 `status`는 끝난 **원인**을 나타내고(`PAYMENT_FAILED` · `EXPIRED` · `CANCELLED`), 가게주문·품목에는 원인 상태가 없어 "이행하지 않음"인 `CANCELLED` 하나로 모인다. 그래서 `markPaymentFailed`가 끝나면 주문은 `PAYMENT_FAILED`, 그 아래 가게주문·품목은 `CANCELLED`이고 사유는 `failureReason`에 남는다. 구매자가 취소한 것이 아니다(그 경로는 주문이 `CANCELLED`).

**`ShopOrder`·`OrderLine` 메서드** — 상태 전이 규칙은 enum의 `transitTo`가 갖고, 엔티티는 얇게 호출만 한다. `Order`는 가게주문만 호출하고 품목은 가게주문이 처리한다(애그리거트 안쪽을 `Order`가 직접 순회하지 않는다).

| 클래스 | 메서드 | 하는 일 |
|---|---|---|
| `ShopOrder` | `static ShopOrder create(UUID shopId, String shopName, List<CheckoutLine> lines)` | `id`, status `PENDING`, `lines`를 `OrderLine.create`로 바꿔 `lines` 컬렉션에 담는다. `lines`는 `new ArrayList<>()`로 초기화해 둔다 |
| `ShopOrder` | `void markPaid()` | 자기 `PENDING → PAID` + 품목 전부 `markPaid()` |
| `ShopOrder` | `void markCancelled()` | 자기 `→ CANCELLED` + 품목도 함께. `PENDING`인지 거르는 일은 호출자(`Order.cancelPendingChildren`)가 한다 |
| `OrderLine` | `static OrderLine create(CheckoutLine line)` | `id`, 스냅샷 복사, `lineAmount = unitPrice.times(quantity)`, `refundedAmount = Money.ZERO`, status `PENDING` |
| `OrderLine` | `void markPaid()`, `void markCancelled()` | 상태 전이만 |

**저장할 때 일어나는 일**
- `order_id`·`shop_order_id`는 코드에서 세팅하지 않는다. `save` 뒤 flush 때 Hibernate가 부모의 `@JoinColumn` 값을 자식 `INSERT`에 넣는다(`nullable = false`라 INSERT 후 UPDATE가 아니다). 자식의 `orderId`·`shopOrderId` 필드는 조회 전용이라 **같은 트랜잭션 안에서는 `null`**이다. 주문 ID가 필요하면 `order.getId()`를 쓴다.
- `@Version`은 래퍼 타입 `Long`으로 둔다. 새 엔티티는 `null`이어야 Spring Data가 새 것으로 보고 `persist`한다(원시 타입이면 ID가 이미 있어 새 것으로 보지 않는다).

**`OrderTest` 준비**
- `markPaid`: `checkout`으로 만든 주문은 `PENDING_PAYMENT`다. 운영에서는 2-4의 승인 시작 CAS가 DB에서 `PAYMENT_IN_PROGRESS`로 바꾼 뒤 `markPaid`가 불리므로, 단위 테스트는 `ReflectionTestUtils.setField(order, "status", OrderStatus.PAYMENT_IN_PROGRESS)`로 상태를 맞춘다(도메인에 테스트만을 위한 전이 메서드를 만들지 않는다).
- `cancelPendingChildren`이 `PAID`를 건드리지 않는지는 위처럼 상태를 맞춘 뒤 `markPaid`한 주문에 호출해 확인한다.

**`OrderRepository`** (도메인 인터페이스, `order.domain`) — 구현은 `order.infrastructure`의 `JpaOrderRepository`가 한다. `JpaOrderRepository`는 `extends JpaRepository<Order, UUID>, OrderRepository`인 인터페이스이고, 아래 표에서 "더한다"고 한 것만 선언한다(`JpaMemberAddressRepository`와 같은 구조).

| 메서드 | 누가 쓰나 | `JpaOrderRepository`에서 |
|---|---|---|
| `Order save(Order o)` | `CheckoutService` 7번 | 더하지 않는다(`JpaRepository`가 같은 시그니처로 제공) |
| `Optional<Order> findById(UUID id)` | 2-4 결제 반영, 2-5 만료, 2-6 대사 | 더하지 않는다. 가게주문·품목은 지연 로딩이고, 같은 트랜잭션에서 상태를 바꿀 때 읽힌다 |
| `Optional<Order> findByIdAndMemberId(UUID id, UUID memberId)` | `OrderQueryService.getMyOrder`(상세) | 같은 시그니처를 다시 선언하고 `@EntityGraph(attributePaths = "shopOrders")`를 붙인다. 쿼리는 메서드 이름에서 만들어진다 |
| `List<Order> findByMemberFirstPage(UUID memberId, Limit limit)` | `getMyOrders`, 첫 페이지 | `@Query`로 더한다 |
| `List<Order> findByMemberNextPage(UUID memberId, Instant cursorCreatedAt, UUID cursorId, Limit limit)` | `getMyOrders`, 다음 페이지 | `@Query`로 더한다 |

```java
// JpaOrderRepository에 더하는 것
@EntityGraph(attributePaths = "shopOrders")
Optional<Order> findByIdAndMemberId(UUID id, UUID memberId);

@Query("""
        select o from Order o
        where o.memberId = :memberId
        order by o.createdAt desc, o.id desc
        """)
List<Order> findByMemberFirstPage(UUID memberId, Limit limit);

@Query("""
        select o from Order o
        where o.memberId = :memberId
          and (o.createdAt < :cursorCreatedAt or (o.createdAt = :cursorCreatedAt and o.id < :cursorId))
        order by o.createdAt desc, o.id desc
        """)
List<Order> findByMemberNextPage(UUID memberId, Instant cursorCreatedAt, UUID cursorId, Limit limit);
```

- `@Param`은 쓰지 않는다(`JpaProductRepository`와 같다).
- 정렬 `createdAt desc, id desc`는 인덱스 `idx_orders_member_id_created_at`과 같은 순서라 인덱스를 탄다. `id`는 `createdAt`이 같은 행의 순서를 정하는 보조 키다.
- **목록 쿼리에는 `@EntityGraph`를 붙이지 않는다.** 컬렉션 fetch와 `Limit`을 같이 쓰면 페이징이 메모리에서 일어난다(개발 가이드 §8.1).
- **상세의 `@EntityGraph`에 `"shopOrders.lines"`를 같이 넣지 않는다.** `List` 두 개를 한 번에 fetch하면 `MultipleBagFetchException`이 난다(2026-10-10 Hibernate 7.4.5로 확인, 공통 규칙 P-7). 품목은 아래 배치 설정으로 읽는다.

**쿼리가 몇 번 나가나** — `application.yaml`의 `spring.jpa.properties.hibernate` 아래(`jdbc.time_zone`과 같은 단계)에 `default_batch_fetch_size: 100`을 추가한다.

| 조회 | 나가는 쿼리 |
|---|---|
| 상세 | ① 주문 + 가게주문(`@EntityGraph`, JOIN 한 번) ② 품목(가게주문 ID들 `IN`, 배치 설정). 가게주문이 1개든 3개든 같다 |
| 목록(`size` ≤ 100) | ① 주문 `LIMIT size + 1` ② 가게주문(주문 ID들 `IN`) ③ 품목(가게주문 ID들 `IN`). 목록의 행 수만큼 쿼리가 늘지 않는다 |

**5) order.application — 체크아웃**

| 클래스 | 규격 |
|---|---|
| `OrderProperties` | `@ConfigurationProperties("myroutine.order") @Validated record OrderProperties(@NotNull Duration paymentTtl)` |
| `CheckoutCommand` | `record (List<UUID> cartItemIds, UUID addressId)` |
| `CheckoutResult` | `record (UUID orderId, OrderStatus status, long totalAmount, String pgOrderId, Instant expiresAt, List<ShopOrderResult> shopOrders)` |
| `ShopOrderResult` | `record (UUID shopId, String shopName, List<CheckoutLineResult> lines)` — 가게별로 묶인 주문 내용. API 명세 §3.1의 `shopOrders[]` 한 칸이다 |
| `CheckoutLineResult` | `record (UUID productId, String name, long unitPrice, int quantity, long lineAmount)` — 체크아웃 응답의 품목 한 줄. `name`은 `OrderLine.productName`(스냅샷). 주문 상세(`OrderDetailResult`)의 품목과 달리 `orderLineId`·`status`·`refundedAmount`가 없다(결제 전이라 의미가 없다) |
| `CheckoutService` | `@Transactional public CheckoutResult checkout(UUID memberId, CheckoutCommand command)` |

`CheckoutResult` 만들기 — 저장한 `Order`를 **`checkout` 메서드(트랜잭션) 안에서** 변환해 반환한다. 컨트롤러로는 엔티티가 아니라 `CheckoutResult`만 나간다(`open-in-view: false`라 트랜잭션이 끝난 뒤에는 `getShopOrders()`·`getLines()`의 지연 로딩을 풀 수 없다). 변환 코드의 위치는 자유다: `CheckoutResult.from(Order)` 정적 팩토리(`AddressResult.from`과 같은 방식)든 서비스의 private 메서드든 상관없다.

| 결과 칸 | 꺼내는 곳 |
|---|---|
| `orderId`, `status`, `expiresAt` | `order.getId()`, `order.getStatus()`, `order.getExpiresAt()` |
| `totalAmount` | `order.getTotalAmount().amount()` (`Money` → `long`) |
| `pgOrderId` | `order.pgOrderId()` |
| `shopOrders[]` | `order.getShopOrders()`의 각 가게주문 → `ShopOrderResult(shopId, shopName, lines)` |
| `lines[]` | 가게주문의 `getLines()`의 각 품목 → `CheckoutLineResult(productId, productName, unitPrice.amount(), quantity, lineAmount.amount())` |

- 주문 ID는 **`order.getId()`**를 쓴다. `ShopOrder.getOrderId()`는 조회 전용이라 같은 트랜잭션 안에서는 `null`이다(저장할 때 일어나는 일 참고).
- 응답 JSON(`CheckoutResponse`)은 이 구조와 같고, 금액은 `long`으로 내려간다.

`checkout` 순서 (전부 한 트랜잭션, 하나라도 예외면 전부 롤백)
1. 내 장바구니의 항목을 꺼내 `(productId, quantity)` Map으로 바꾼다.
   - `cart = cartRepository.findByMemberId(memberId)` — 없으면 `CART_ITEM_NOT_FOUND`(한 번도 담지 않은 회원은 내 항목이 하나도 없다). `CartService.changeQuantity`와 같은 방식이다
   - `cartItems = cartItemRepository.findAllByIdInAndCartId(cartItemIds, cart.getId())` — 쿼리는 이 2개다. 요청 ID 개수와 조회된 행 개수가 다르면 `CART_ITEM_NOT_FOUND`. 같은 ID가 두 번 오면 `IN` 조회가 1행만 돌려주므로, **중복을 제거한 개수**(`new HashSet<>(cartItemIds).size()`)와 비교한다. 남의 항목·삭제된 항목·없는 장바구니가 전부 같은 404라 다른 회원의 ID가 존재하는지도 드러나지 않는다
   - `Map<UUID, Integer> quantityByProduct = cartItems.stream().collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity))` — 장바구니는 상품당 한 행(`uk_cart_item_cart_product`)이라 키가 겹치지 않는다. 이후 단계는 장바구니 엔티티를 쓰지 않고 이 Map만 쓴다: 3번의 `productIds`는 `quantityByProduct.keySet()`, `CheckoutLine`의 `quantity`는 `quantityByProduct.get(productId)`, 8번의 `items`는 Map의 각 항목을 `ReserveItem(productId, quantity)`로 만든다
2. 50개를 넘으면 `INVALID_REQUEST` — 50은 **`cartItemIds`의 개수(품목 종류 수)**이고 수량 합이 아니다(항목당 수량은 최대 99). 웹 요청은 `CheckoutRequest`의 `@Size(max = 50)`이 먼저 막으므로 이 검사는 서비스를 직접 부르는 경우(2-6의 `OrderPlacer`, 테스트)를 위한 방어다. 1번의 DB 조회 전에 해도 되지만 번호는 다른 단계가 참조해서 그대로 둔다
3. `products = productApi.getPurchasable(productIds)` — 판매 중이 아니면 여기서 422 `PRODUCT_NOT_ON_SALE`
4. `shops = shopApi.requireActiveShops(가게 ID들)` — 운영 중이 아니면 422 `SHOP_NOT_ACTIVE`
5. 가게 소유자가 나(memberId)인 가게가 있으면 `OWN_SHOP_PRODUCT`. `details.productIds`에는 **내 가게의 상품 ID만** 골라 넣는다: 3번 `shops`에서 `ownerId == memberId`인 `shopId`들을 모으고, 3번 `products` 중 `shopId`가 그 안에 있는 상품의 `productId`를 담는다(조회를 더 하지 않는다. 클라이언트가 장바구니에서 어떤 상품을 빼야 하는지 알려 주려는 값이라 다른 `details.productIds`와 같은 이름·의미를 쓴다)
6. `address = memberApi.getAddress(memberId, addressId)`
7. `Order.checkout(...)` → `orderRepository.save(order)`
8. `productApi.reserve(order.getId(), items, order.getExpiresAt())` — 재고 부족이면 422 `OUT_OF_STOCK`. `items`는 `List<ReserveItem>`(`new ReserveItem(productId, quantity)`)이다. `reserve`는 같은 상품이 두 번 들어 있으면 `IllegalArgumentException`을 던지는데, 장바구니는 상품당 한 행이라 겹치지 않는다. `orderId`는 `Order.checkout`이 매번 새로 만든다 — `reserve`는 같은 `orderId`의 예약이 하나라도 있으면(해제·만료된 것 포함) 아무것도 하지 않고 돌아오므로(1-9 멱등 규칙), 주문 ID를 재사용하면 예약 없이 성공한 것처럼 보인다
9. `CheckoutResult` 반환 (status PENDING_PAYMENT)
- 8에서 예외가 나면 7의 주문까지 롤백된다. 여러 상품 중 일부만 예약된 상태도 남지 않는다. 이게 "한 트랜잭션"의 의미다.
- 장바구니 항목은 읽기만 한다(지우지도 표시하지도 않는다). 2-1에서 선언한 `CartItemRepository.deleteAll`은 쓰는 곳이 없으므로 이 단계에서 `CartItemRepository`(domain)에서 지운다.

`CheckoutLine` 만들기 (3~6의 결과를 품목마다 합친다)

| `CheckoutLine` 칸 | 꺼내는 곳 |
|---|---|
| `productId`, `shopId`, `productName`, `thumbnailKey`, `unitPrice` | `ProductForCheckout`의 `productId()`, `shopId()`, `name()`, `thumbnailKey()`, `price()`(이미 `Money`) |
| `shopName` | `ShopInfo.name()` — `requireActiveShops` 결과를 `shopId`로 `Map`에 담아 찾는다 |
| `quantity` | 1의 장바구니 항목 |

- 5의 자기 가게 판단은 `ShopInfo.ownerId().equals(memberId)`.
- `getPurchasable`은 판매 상태만 보고 재고는 보지 않는다(`inStock`이 false여도 통과). 재고 부족은 8의 `reserve`가 판정한다.

`OrderQueryService` (`@Transactional(readOnly = true)`)
- `CursorPage<OrderSummaryResult> getMyOrders(UUID memberId, String cursor, int size)` — `OrderSummaryResult(UUID orderId, OrderStatus status, long totalAmount, String firstProductName, int lineCount, Instant createdAt)`
  - 만드는 법(`ProductQueryService.getPublicProducts`와 같다): `cursor`가 없으면 `findByMemberFirstPage(memberId, Limit.of(size + 1))`, 있으면 `Cursor.decode(cursor)`로 `createdAt`·`id`를 꺼내 `findByMemberNextPage(...)`. **`size + 1`건을 읽는 이유**는 다음 페이지가 있는지 알기 위해서다. 결과가 `size`건을 넘으면 앞의 `size`건만 돌려주고 `nextCursor = new Cursor(돌려주는 마지막 항목의 createdAt, id).encode()`(여분 1건이 아니라 **돌려주는 마지막 항목**이 기준이다). `size` 이하면 `nextCursor = null`
  - `OrderSummaryResult`: `firstProductName` = 첫 가게주문의 첫 품목 상품명(대표 표시용이라 로딩된 순서의 첫 품목이면 충분하다), `lineCount` = 모든 가게주문의 품목 수, `totalAmount` = `order.getTotalAmount().amount()`
- `OrderDetailResult getMyOrder(UUID memberId, UUID orderId)` — `findByIdAndMemberId`(`@EntityGraph`로 가게주문까지)로 읽고, 없거나 남의 것이면 `ORDER_NOT_FOUND`(404). **트랜잭션 안에서** `OrderDetailResult.from(order)`로 변환해 돌려준다. 쿼리는 주문+가게주문 1번, 품목 1번(`default_batch_fetch_size`)이라 가게 수와 상관없이 같다.

  | 타입 | 정의 (`order.application`) |
  |---|---|
  | `OrderDetailResult` | `record (UUID orderId, String type, OrderStatus status, long totalAmount, String pgOrderId, ShippingAddressResult shippingAddress, Instant expiresAt, Instant paidAt, Instant createdAt, List<ShopOrderDetailResult> shopOrders)` |
  | `ShippingAddressResult` | `record (String recipient, String phone, String zipcode, String address1, String address2)` — 주문에 저장된 배송지 스냅샷 |
  | `ShopOrderDetailResult` | `record (UUID shopOrderId, UUID shopId, String shopName, ShopOrderStatus status, String carrier, String trackingNumber, List<OrderLineDetailResult> lines)` |
  | `OrderLineDetailResult` | `record (UUID orderLineId, UUID productId, String productName, String thumbnailKey, long unitPrice, int quantity, long lineAmount, long refundedAmount, OrderLineStatus status)` |

  | 칸 | 꺼내는 곳 / 이유 |
  |---|---|
  | `orderId`, `type`, `status`, `createdAt` | `order.getId()`, `getType()`, `getStatus()`, `getCreatedAt()` |
  | `totalAmount` | `order.getTotalAmount().amount()` (품목 금액도 모두 `Money` → `.amount()`로 `long`) |
  | `pgOrderId` | `order.pgOrderId()`. 결제 대기 주문을 나갔다가 다시 열어 Toss 결제창을 띄울 때 필요하다 |
  | `shippingAddress` | `ShippingAddressResult`로 옮긴다(`order.getShippingAddress()`의 5개 필드). 결과 타입에 도메인 `ShippingAddress`를 그대로 두지 않는 이유는 web이 domain을 import하지 않게 하려는 것이다 |
  | `expiresAt` | `order.getExpiresAt()`. `PENDING_PAYMENT`에서 남은 시간을 보여 줄 때 쓴다 |
  | `paidAt` | `order.getPaidAt()`. 결제 전이면 `null` |
  | `shopOrders[]` | `order.getShopOrders()`의 각 가게주문 → `ShopOrderDetailResult` (`getId()`, `getShopId()`, `getShopName()`, `getStatus()`, `getCarrier()`, `getTrackingNumber()`, 품목) |
  | `lines[]` | 가게주문의 `getLines()` → `OrderLineDetailResult` (`getId()`가 `orderLineId`, `getProductName()`, `getThumbnailKey()`, `getUnitPrice().amount()`, `getQuantity()`, `getLineAmount().amount()`, `getRefundedAmount().amount()`, `getStatus()`) |

  - **내려주지 않는 필드**: `memberId`(내 주문뿐이라 의미가 없다), `version`, `failureReason`(내부 사유 문자열이라 구매자에게 보이지 않는다. 결제 실패는 `status`로 알린다), 가게주문의 `shippedAt`·`deliveredAt`·`completedAt`과 품목의 `confirmedAt`(2-7·2-10에서 필요해지면 그때 더한다).
  - 2-8에서 `refunds`가 추가된다. 응답(`OrderDetailResponse`)에서는 `thumbnailKey` 대신 `thumbnailUrl`(`ImageUrls`, 1-10)을 내려준다 — 키는 Result에, URL 조립은 web에서 한다.
  - `CheckoutResult`의 `ShopOrderResult`·`CheckoutLineResult`와는 별개 타입이다(상세에는 `shopOrderId`·`status`·`carrier`·`orderLineId`·`refundedAmount`가 있고 체크아웃에는 없다). 이름이 겹치지 않게 상세 쪽에 `Detail`을 붙였다.
- 썸네일 스냅샷은 **키**만 저장한다. 판매자가 그 이미지를 지우면(1-10 삭제는 저장소 객체도 지운다) 지난 주문의 썸네일 URL은 404가 된다 — 일부러 남기는 한계다(이미지를 복사해 두지 않는다)

**6) order.web** — `OrderController` (`/api/orders`)

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/orders/checkout` `@Idempotent` | `CheckoutRequest` | 201 `CheckoutResponse` (Result와 같은 구조, [API 명세 §3.1](../02-design/07-api-spec.md)) |
| `GET /api/orders?cursor=&size=` | | 200 `CursorPage<OrderSummaryResponse>` |
| `GET /api/orders/{id}` | | 200 `OrderDetailResponse` |

`CheckoutRequest`: `@NotEmpty @Size(max = 50) List<UUID> cartItemIds`, `@NotNull UUID addressId`. 요청에 `price` 같은 필드가 있어도 **record에 없으므로 무시된다**(Jackson 3.1.5의 `FAIL_ON_UNKNOWN_PROPERTIES` 기본값은 `false` — 2026-10-09 `JsonMapper.builder().build()`로 직접 확인, 모르는 필드가 있는 JSON이 예외 없이 읽혔다)

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/domain/OrderTest` | [ ] 가게 2곳·품목 3개 → 가게주문 2개, 가게별 품목 수 맞음(같은 가게 상품은 한 가게주문으로 묶임), `total = Σ(단가 × 수량)` / [ ] `expiresAt = now + 15분` / [ ] `markPaid` → 가게주문·품목 모두 PAID / [ ] `markPaymentFailed` → 가게주문·품목 CANCELLED, 사유 저장 / [ ] 사유가 200자를 넘으면 200자로 저장 / [ ] `markPaid`한 주문에 `cancelPendingChildren` → 가게주문·품목이 PAID 그대로 |
| `order/domain/OrderStatusTest`, `ShopOrderStatusTest`, `OrderLineStatusTest` | [ ] 각각 전체 조합 파라미터화 |
| `common/idempotency/IdempotencyAspectTest` (API 통합, 1-6 가게 개설 API로 검증) | [ ] 키 없이 → 400 `IDEMPOTENCY_KEY_REQUIRED` / [ ] 같은 키·같은 바디 두 번 → 가게 1개, 두 응답 바디가 같다 / [ ] 같은 키·다른 바디 → 422 `IDEMPOTENCY_KEY_REUSED` / [ ] 사업자번호 중복으로 409를 받은 요청의 키는 지워진다: 같은 키·같은 바디로 다시 보내면 저장된 응답이 아니라 다시 처리되어 또 409 / [ ] 만료된 키(JDBC로 `expires_at`을 과거로)와 같은 키로 요청 → 새로 처리됨 / [ ] 만료된 키와 같은 키로 **다른 바디**를 보내도 → 새로 처리됨(422가 아니다) |
| `order/web/OrderControllerTest` (checkout) | [ ] 체크아웃 → 201 PENDING_PAYMENT, `stock.reserved` 증가, `pgOrderId = "ORD-" + orderId`, 장바구니는 그대로(체크아웃 전후 `GET /api/cart` 동일) / [ ] 요청 JSON에 `"price": 1`을 넣어도 서버 가격으로 계산 / [ ] **상품 2개 중 두 번째가 재고 부족 → 422 `OUT_OF_STOCK`, 주문 0건, 첫 번째 상품 reserved 0(예약도 롤백), 장바구니 그대로** / [ ] 자기 가게 상품 → 422 `OWN_SHOP_PRODUCT` / [ ] CLOSED 가게 상품 → 422 `SHOP_NOT_ACTIVE` / [ ] 남의 배송지 → 404 / [ ] 남의 장바구니 항목 ID → 404 `CART_ITEM_NOT_FOUND` / [ ] 같은 `Idempotency-Key`로 두 번 → 주문 1건, 같은 응답 / [ ] 같은 장바구니 항목으로 **다른 키**로 두 번 → 주문 2건, 장바구니 그대로(중복 체크아웃은 막지 않는다. 각 주문이 재고를 독립적으로 예약하고 만료로 풀린다) |
| `order/application/CheckoutIdempotencyConcurrencyTest` | [ ] **같은 키로 동시에 10번**(MockMvc) → 주문 1건, 상태 코드는 201과 409(`IDEMPOTENCY_IN_PROGRESS`)뿐 |
| `order/web/OrderQueryTest` | [ ] 내 주문 목록 커서 페이징 / [ ] 상세에 가게주문·품목 / [ ] 남의 주문 상세 → 404 / [ ] **상세 조회 SQL 수가 가게주문 1개일 때와 3개일 때 같다**(1-7의 Hibernate statistics 방식, 공통 규칙 P-7) |
| `ModularityTest` | [ ] 통과 (order → product·shop·member의 api만 참조) |

**테스트 준비** ([N-1](#n-1-단계별-testfixtures-추가))

**이 단계에서 추가하는 헬퍼** (`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `address(memberId)` → addressId | `MemberAddressService.register(memberId, new AddressCommand(...))` | 체크아웃 전부, "남의 배송지 → 404" |
| `addToCart(memberId, productId, quantity)` → cartItemId | `CartService.addItem` | 체크아웃 전부 |
| `checkout(memberId, List<UUID> cartItemIds, UUID addressId)` → `CheckoutResult` | `CheckoutService.checkout(memberId, new CheckoutCommand(...))` | 체크아웃의 준비 상태, 2-4 이후 전부 |
| `stockOf(productId)` → `StockSnapshot(available, reserved, sold)` (선택) | JDBC `SELECT available, reserved, sold FROM product.stock WHERE product_id = ?` | "reserved 증가·원복"을 보는 2-2·2-4·2-5·2-6·2-8의 단언. 반복되면 만든다 |

이미 있는 헬퍼(앞 단계·Part 1에서 만든 것, 그대로 쓴다): `signup`, `token`, `login`, `changeStatus`, `openShop`, `closeShop`, `registerProduct`, `hideProduct`, `discontinueProduct`, `registerProductImage`, `putToStorage`, `ConcurrencyRunner`

- 멱등 키 테스트(`IdempotencyAspectTest`): 요청은 가게 개설(`POST /api/shops`)이라 `openShop`을 호출하지 않고 `signup`·`token`만 한다. 같은 키·다른 바디는 `OpenShopRequest`의 `name`만 바꾼다. **만료된 키 만들기**: 먼저 요청을 한 번 보내 키를 저장하고, `UPDATE common.idempotency_key SET expires_at = now() - interval '1 hour' WHERE member_id = ? AND idempotency_key = ?`로 만료 시각을 과거로 돌린 뒤 같은 키로 다시 보낸다(JDBC는 테스트 클래스 생성자로 `JdbcTemplate`을 주입받는다. `IntegrationTestSupport.jdbc`는 패키지 전용이라 다른 패키지에서 못 쓴다). **"새로 처리됨"을 보는 법**: 같은 바디면 가게가 이미 있어 사업자번호 중복 **409**가 나온다(재생이었다면 201). 다른 바디(사업자번호를 바꾼다)면 **201에 첫 응답과 다른 `shopId`**가 나온다(재생도 422도 아니다)
- 체크아웃 준비: 판매자·구매자 `signup` → `openShop` → `registerProduct` → `address(buyerId)` → `addToCart(buyerId, productId, quantity)`. 여러 가게는 판매자·가게를 둘 만든다.
- 재고 부족: 두 번째 상품의 재고를 담을 수량보다 작게 만든다(`registerProduct(.., price, 1)`에 2개). 첫 상품의 `reserved`는 `stockOf`(또는 JDBC)로 0인지 본다.
- 자기 가게 상품: 판매자 본인이 `addToCart`. CLOSED 가게: `closeShop(shopId)`. 남의 배송지: `address(otherMemberId)`. 남의 장바구니 항목: 다른 회원의 `addToCart` 결과 ID.
- 동시성(`CheckoutIdempotencyConcurrencyTest`): 같은 `Idempotency-Key`로 `ConcurrencyRunner.run(10, () -> mockMvc.perform(...))` — 값은 `MvcResult`나 상태 코드로 받아 201과 409만 있는지 센다.
- 중복 체크아웃: 재고를 넉넉히(예: 10) 두고 같은 `cartItemIds`로 서로 다른 `Idempotency-Key`를 줘 HTTP로 두 번 보낸다. 주문 건수는 JDBC로 센다.
- 상세 조회 쿼리 수: 가게 1곳 상품만 담은 주문과 3곳 상품을 담은 주문을 각각 만들어 센다.

**추가 항목**
- 바로 구매(장바구니를 거치지 않고 상품·수량으로 체크아웃): 요청에 `items`를 받는 경로 추가. 만들면 결제 완료 때 장바구니 차감 대상에서 빼야 하므로 주문에 출처(`from_cart` 등)를 남긴다
- 재고 점유 남용 방어: 회원당 결제 대기 주문 수 상한(체크아웃 때 `PENDING_PAYMENT`·`PAYMENT_IN_PROGRESS` 건수가 N 이상이면 409, 동시 요청에서는 N을 약간 넘을 수 있는 best-effort), 회원·IP별 체크아웃 속도 제한(Part 4의 Redis 쿨다운과 같은 방식), 한정 수량 상품의 1인당 구매 수량 제한
- 사람이 읽기 쉬운 주문번호(`yyyyMMdd-000123`, DB 시퀀스 + 업무 날짜)
- 만료된 키를 지우는 정리 잡: 만료된 키는 같은 키로 다시 요청이 와야 지워지므로 다시 쓰이지 않는 키는 테이블에 쌓인다. 정합성과는 무관한 운영 편의이고 `idx_idempotency_key_expires_at`이 이미 있다
- 주문 목록이 품목을 전부 읽는 낭비 줄이기(Stage 2 후보): 목록은 첫 품목 이름과 품목 수만 쓰는데 `default_batch_fetch_size`로 페이지의 모든 가게주문·품목 행을 읽는다(쿼리는 3번 고정이고 낭비는 행 수. 주문당 품목 1~5개·페이지 20건이면 20~100행, 상한 50품목이면 최대 1,000행). 목록이 느린 걸로 측정되면 `orders`에 `first_product_name`·`line_count`를 체크아웃 때 저장해 품목 테이블을 읽지 않게 한다(체크아웃 후 바뀌지 않는 스냅샷이라 정합 문제가 없고 쿼리가 1번으로 준다). 프로젝션 쿼리는 엔티티 경로와 쿼리가 둘로 갈라져 채택하지 않는다

**리뷰 때 물어볼 것**
- 주문 생성과 재고 예약을 한 트랜잭션에 묶을 수 있는 이유는? MSA에서는 어떻게 되나?
- Order 애그리거트가 ShopOrder·OrderLine을 직접 만들게 한 이유는?
- 멱등 키가 "처리 중"인 상태로 서버가 죽으면?
- 멱등 키를 응답 저장 없이 "있으면 409"로만 쓰면 무엇이 부족한가?
- 결제가 실패했을 때 장바구니가 그대로인 이유는? 체크아웃 때 지우는 방식과 비교하면 무엇이 단순해지나?
- 같은 장바구니 항목으로 Idempotency-Key가 다른 체크아웃이 오면? 멱등 키가 이걸 막아 주나? 이 프로젝트가 막지 않기로 한 이유와, 둘 다 결제됐을 때 어떻게 회복하나?

---

## 2-3. Toss 클라이언트와 가짜 PG 서버 (M)

**목표**
- Toss 결제 승인·취소·조회 API를 호출하는 클라이언트를 만든다.
- 응답을 **승인 / 명확한 실패 / 불확실** 세 가지로 분류한다.
- 테스트에서 Toss 대신 쓸 가짜 PG 서버를 만든다.

**왜 지금**: 2-4 결제 승인 전에 "외부 API는 실패가 두 종류가 아니라 세 종류"라는 것을 코드로 먼저 정리한다.

**새로 등장**

| 개념·도구 | 한 줄 설명 |
|---|---|
| Toss 결제 흐름 | 결제창은 **인증**만 한다. 실제 **승인**은 우리 서버가 confirm API를 호출할 때 일어난다 (ADR-004) |
| 결과 3분류 | 2xx → 승인 / 거절 코드(4xx) → 명확한 실패 / 타임아웃·5xx·연결 끊김 → **불확실(UNKNOWN)** |
| `RestClient` 타임아웃 | 기본값은 무한 대기. connect·read 타임아웃을 반드시 설정 |
| `RestClient.exchange` | 4xx·5xx에서 예외를 던지는 `retrieve()` 대신, 상태 코드와 본문을 직접 읽어 분류한다 |
| 가짜 PG 서버 | JDK 내장 `HttpServer`로 만든 테스트용 서버. 경로별로 응답·지연·연결 끊기를 설정 |

**Toss API 요약** (2026-10-09 Toss 개발자센터 "코어 API"·"인증 및 기타 헤더 설정" 문서와 대조)

| 작업 | 요청 | 성공 응답 |
|---|---|---|
| 승인 | `POST /v1/payments/confirm` 바디 `{paymentKey, orderId, amount}` | 200, Payment 객체 `status: "DONE"` |
| 취소 | `POST /v1/payments/{paymentKey}/cancel` 바디 `{cancelReason, cancelAmount}` | 200, Payment 객체 `status: "CANCELED"` 또는 `"PARTIAL_CANCELED"` |
| 조회 | `GET /v1/payments/orders/{orderId}` | 200 Payment 객체 / 없으면 404 `NOT_FOUND_PAYMENT` |
| 공통 헤더 | `Authorization: Basic base64(시크릿키 + ":")` — **콜론을 빠뜨리지 않는다**. 승인·취소(POST)에 `Idempotency-Key` | 에러 본문 `{code, message}` |

- 멱등키: 같은 키 + 같은 API 키·주소·메서드면 처음 응답을 그대로 돌려준다. **처음 요청한 날부터 15일 유효**, 300자 이하(넘으면 400 `INVALID_IDEMPOTENCY_KEY`), 첫 요청이 처리 중일 때 다시 보내면 409 `IDEMPOTENT_REQUEST_PROCESSING`.
- 결제창 인증 후 **10분 안에** 승인 API를 부르지 않으면 Toss 쪽 결제가 만료된다(주문 유효시간 15분보다 짧다. 10분이 지나 confirm하면 Toss가 거절 → 결제 실패로 끝난다).
- `approvedAt`은 `yyyy-MM-dd'T'HH:mm:ss±hh:mm` 형식이라 `OffsetDateTime`으로 받는다.

### 할 일

**1) 키와 설정**
- Toss 개발자센터에서 테스트 키 발급 → `.env`에 `TOSS_SECRET_KEY`, `TOSS_CLIENT_KEY` (커밋 금지), `.env.example`에 이름만
- `TOSS_SECRET_KEY`에는 개발자센터의 **시크릿 키**(테스트)를 넣는다. 보안 키가 아니다. 이 단계에서 필수는 시크릿 키뿐이고, 클라이언트 키(`TOSS_CLIENT_KEY`)는 비어 있어도 기동한다
- `payment.infrastructure.TossProperties`: `@ConfigurationProperties("myroutine.toss") @Validated record TossProperties(@NotBlank String baseUrl, @NotBlank String secretKey, String clientKey, @NotNull Duration connectTimeout, @NotNull Duration readTimeout)`
- `application.yaml`: `base-url: https://api.tosspayments.com`, `secret-key: ${TOSS_SECRET_KEY}`, `client-key: ${TOSS_CLIENT_KEY:}`, `connect-timeout: 3s`, `read-timeout: 10s`
- `application-test.yaml`: `secret-key: test_sk_dummy`, `read-timeout: 500ms` (`base-url`은 가짜 서버 주소를 테스트 베이스에서 주입)
- **운영 VM 먼저**: `secret-key`가 필수(`@NotBlank`)라 VM `/opt/myroutine/.env`에 `TOSS_SECRET_KEY`가 없으면 머지 후 자동 배포에서 기동이 실패해 롤백된다(공통 규칙 P-9). 이 PR을 머지하기 **전에** VM `.env`에 Toss **테스트** 시크릿 키를 넣고, `.env.ops.example`에 키 이름을 추가한다

**2) payment.domain — 결과 타입**
```java
public sealed interface PgResult permits PgResult.Approved, PgResult.Rejected, PgResult.Unknown {
    record Approved(String paymentKey, long totalAmount, long balanceAmount, String method, Instant approvedAt) implements PgResult {}
    record Rejected(String code, String message) implements PgResult {}
    record Unknown(String reason) implements PgResult {}
}
```

| 결과 | 뜻 | 호출하는 쪽(2-4)이 하는 일 |
|---|---|---|
| `Approved` | Toss가 결제를 승인했다 | 결제를 승인으로 반영 |
| `Rejected` | Toss가 명확히 거절했다 | 결제를 실패로 반영 |
| `Unknown` | 승인됐는지 모른다(타임아웃·5xx·이미 처리 중 등) | 실패로 단정하지 않고 UNKNOWN으로 두어 나중에 조회로 확인 |

**3) payment.infrastructure — 클라이언트**

`TossClientConfig` — `@Configuration @EnableConfigurationProperties(TossProperties.class)`. `@Bean RestClient tossRestClient(TossProperties p)`를 만든다.

1. `HttpClient.newBuilder().connectTimeout(p.connectTimeout()).build()` — 연결 타임아웃. 기본값은 무한 대기다
2. `JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient)` 다음 `factory.setReadTimeout(p.readTimeout())` — 응답 읽기 타임아웃
3. `RestClient.builder().requestFactory(factory).baseUrl(p.baseUrl()).defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + base64)`를 `build()`한다
   - `base64`는 `Base64.getEncoder().encodeToString((p.secretKey() + ":").getBytes(UTF_8))` — 시크릿 키 뒤에 **콜론**을 붙인다

`RestClient`·`JdkClientHttpRequestFactory`는 spring-web(이미 있는 webmvc 스타터에 포함)에 있어 의존성을 추가하지 않는다. Boot의 `RestClient.Builder` 빈을 주입받으려면 `spring-boot-starter-restclient`가 필요하므로, 여기서는 정적 `RestClient.builder()`를 쓴다.

| 클래스 | 규격 |
|---|---|
| `TossPaymentResponse` | `record (String paymentKey, String orderId, String status, long totalAmount, long balanceAmount, String method, OffsetDateTime approvedAt)` + `@JsonIgnoreProperties(ignoreUnknown = true)` |
| `TossErrorResponse` | `record (String code, String message)` + 같은 어노테이션 |
| `TossClient` | `@Component`. 아래 ① `classify`, ② public 메서드 3개 |

**① `classify`**

`TossClient`의 private 메서드. Toss가 돌려준 응답 하나를 `PgResult`로 바꾼다. 승인·취소·조회가 같은 규칙을 쓰고, public 메서드 3개가 이것을 호출한다.

`private PgResult classify(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse res, Function<TossPaymentResponse, PgResult> onSuccess) throws IOException`

- `res`: Toss 응답(상태 코드 + 본문)
- `onSuccess`: 2xx 응답의 결제 객체(`TossPaymentResponse`)를 `PgResult`로 바꾸는 규칙. 작업마다 달라서 호출하는 쪽이 넘긴다(아래 2번)
- 예외를 던져 분류하지 않고 **항상 `PgResult`를 반환한다**. `getStatusCode()`가 선언한 `IOException`은 `throws`로 올리고, 던져지면 `RestClient`가 `ResourceAccessException`으로 감싸 public 메서드의 `catch`에서 `Unknown`이 된다

위에서 아래로 읽다가 처음 맞는 조건에서 반환한다.

1. `HttpStatusCode code = res.getStatusCode()`로 상태 코드를 읽는다. 이후 `code.is2xxSuccessful()`, `code.is4xxClientError()`, `code.is5xxServerError()`, `code.value()`로 가른다.
2. **2xx**이면 `res.bodyTo(TossPaymentResponse.class)`로 본문을 읽는다.
   - 파싱에 실패하거나(`RestClientException`) 본문이 비어 `bodyTo`가 `null`을 돌려주면 `Unknown("INVALID_BODY")`
   - 읽었으면 `onSuccess`에 넘긴 결과를 반환한다. 작업별 규칙(`status`는 본문의 값):
     - 승인: `"DONE"` → `Approved`, 그 외 → `Unknown(status)`
     - 취소: `"CANCELED"`·`"PARTIAL_CANCELED"` → `Approved`, 그 외 → `Unknown(status)`
     - 조회: `"DONE"`·`"PARTIAL_CANCELED"`·`"CANCELED"` → `Approved`, `"ABORTED"`·`"EXPIRED"` → `Rejected(status, status)`, 그 외(`READY`, `IN_PROGRESS`) → `Unknown(status)`
   - `Approved`는 `TossPaymentResponse`의 `paymentKey`·`totalAmount`·`balanceAmount`·`method`와 `approvedAt.toInstant()`(`OffsetDateTime` → `Instant`)로 만든다. **`approvedAt`이 `null`이면 `Approved`를 만들지 않고 `Unknown("INVALID_BODY")`** — `toInstant()`의 `NullPointerException`이 예외로 새어 나가면 "값으로 분류"하는 설계가 깨진다
3. **4xx**이면 `res.bodyTo(TossErrorResponse.class)`로 본문을 읽는다.
   - 파싱에 실패하거나 `bodyTo`가 `null`을 돌려주면 `Unknown("INVALID_BODY")` — 코드를 읽을 수 없는 4xx는 거절로 단정하지 않는다
   - 본문은 읽었지만 `code`가 `null`이면 `Unknown("HTTP_4xx")` — 이것도 거절로 단정하지 않는다(`Set.of(...).contains(null)`은 `NullPointerException`을 던지므로 불확실 코드 검사 전에 거른다)
   - `code`가 아래 "불확실 코드" 목록에 있으면 `Unknown(code)`
   - 그 외는 `Rejected(code, message)`. 에러 코드마다 따로 분기하지 않는다
   - 예: 조회(`GET /v1/payments/orders/{orderId}`)에서 그 주문의 결제가 Toss에 없으면 404와 `{"code":"NOT_FOUND_PAYMENT", "message":"..."}`가 온다. 이것도 4xx이고 "불확실 코드"에 없으므로 이 줄에서 `Rejected("NOT_FOUND_PAYMENT", message)`가 된다(완료 확인의 "없는 주문 → `Rejected("NOT_FOUND_PAYMENT")`")
4. **5xx**이면 본문에서 `code`를 읽을 수 있으면 `Unknown(code)`, 읽을 수 없으면 `Unknown("HTTP_5xx")`
5. **그 밖의 상태**(3xx 등)는 `Unknown("HTTP_" + code.value())`

**불확실 코드** (`classify` 3번)

- "불확실 코드" 목록(상수 `Set<String> UNCERTAIN_CODES`) — 4xx인데 "거절"이 아니라 "이미 처리 중·처리됨"이라 조회로 확인해야 하는 코드. Toss "API 에러 코드" 전체 목록·멱등키 문서와 대조했다(2026-10-09)

| 코드 | HTTP | 뜻 |
|---|---|---|
| `ALREADY_PROCESSED_PAYMENT` | 400 | 이미 처리된 결제 |
| `ALREADY_PROCESSING_REQUEST` | 400 | 이미 처리 중인 요청 |
| `IDEMPOTENT_REQUEST_PROCESSING` | 409 | 같은 멱등키의 첫 요청이 아직 처리 중 |

- `FAILED_INTERNAL_SYSTEM_PROCESSING`, `FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING`, `UNKNOWN_ERROR` 등은 **500**이라 ①의 4번(5xx)으로 이미 `Unknown`이다(목록에 넣지 않는다). 이전 목록의 `PROVIDER_ERROR`, `UNKNOWN_PAYMENT_ERROR`는 현재 에러 목록에 없어 뺐다.
- API별 에러 표(승인·취소·조회마다 나오는 코드)는 문서 화면에서만 보여 대조하지 못했다(확인 필요). 구현할 때 개발자센터에서 승인·취소 API의 에러 표를 한 번 보고, 위 목록에 더할 코드가 있으면 PR에 적는다(Claude가 문서에 옮긴다).

**② public 메서드 3개**

| 메서드 | 하는 일 |
|---|---|
| `PgResult confirm(UUID paymentId, String paymentKey, String pgOrderId, Money amount)` | 승인 요청. `Idempotency-Key: paymentId.toString()` |
| `PgResult cancel(UUID paymentId, String paymentKey, Money amount, String reason, String idempotencyKey)` | 취소 요청 |
| `PgResult queryByOrderId(UUID paymentId, String pgOrderId)` | 조회 |

세 메서드는 같은 순서로 일한다 (승인 예. 취소·조회는 요청 부분과 `onSuccess`만 다르다)

| 순서 | 하는 일 | 코드 형태 | 반환·예외 |
|---|---|---|---|
| 1 | 시작 시각 기록 | `long start = System.nanoTime()` | |
| 2 | 요청 만들기 | 작업별 요청(아래 목록) | |
| 3 | 보내고 분류 | `.exchange((req, res) -> classify(res, p -> ...))` — `p -> ...`는 ①의 2번에 있는 해당 작업 규칙. `retrieve()`와 달리 4xx·5xx에서도 **예외를 던지지 않고** 람다를 부르며, 람다의 반환값(`PgResult`)이 `exchange`의 반환값이다. 그대로 돌려준다 | |
| 4 | 통신 예외 잡기 | 2~3을 `try`로 감싼다: `catch (ResourceAccessException e)` → `Unknown(e.getClass().getSimpleName())` | 타임아웃·연결 실패·끊김은 응답이 오지 않아 `classify`가 불리지도 않는다 |
| 5 | 로그 | `finally`에서 `log.info("pg {} paymentId={} result={} reason={} latencyMs={}", ...)` — 첫 `{}`는 작업(`confirm`·`cancel`·`query`), `result`는 돌려줄 `PgResult`의 종류(`Approved`·`Rejected`·`Unknown`), `reason`은 `Rejected`면 `code`, `Unknown`이면 `reason`(`Approved`면 비운다). HTTP 상태 코드는 `classify` 안에만 있어 따로 남기지 않는다(`HTTP_5xx`·`HTTP_404` 같은 `Unknown`의 이유에 들어 있다) | `paymentId`는 세 메서드가 모두 받는 인자라 이것으로 로그를 결제와 잇는다(`cancel`에는 `pgOrderId`가 없다). **paymentKey 전체·카드 정보는 남기지 않는다** |

작업별 요청 (2번)

- `confirm`: `tossRestClient.post().uri("/v1/payments/confirm").header("Idempotency-Key", paymentId.toString()).contentType(MediaType.APPLICATION_JSON).body(Map.of("paymentKey", paymentKey, "orderId", pgOrderId, "amount", amount.amount()))`
- `cancel`: `tossRestClient.post().uri("/v1/payments/{key}/cancel", paymentKey).header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON).body(Map.of("cancelReason", reason, "cancelAmount", amount.amount()))`
- `queryByOrderId`: `tossRestClient.get().uri("/v1/payments/orders/{orderId}", pgOrderId)` — GET이라 바디와 `Idempotency-Key`가 없다

**4) 가짜 PG 서버** — `src/test/java/com/myroutine/support/`

JDK `HttpServer`로 Toss 대신 응답하는 테스트용 서버다. 테스트가 경로별로 응답 방식을 정하고(`on`), 서버가 받은 요청을 확인한다(`requests`).

| 클래스 | 규격 |
|---|---|
| `RecordedRequest` | `record (String method, String path, Map<String, String> headers, String body)` — 헤더 이름은 소문자로 저장 |
| `Behavior` | `record (int status, String body, long delayMillis, boolean dropConnection, boolean defaultHandling)`. 아래 팩토리로 만든다. 필드 이름은 팩토리 이름(`drop()`·`approve()`)과 겹치면 record의 accessor와 충돌하므로 다르게 쓴다 |
| `FakePgServer` | `static FakePgServer start()`로 만든다. 필드·메서드·처리 순서는 아래 |

`Behavior` 팩토리

| 팩토리 | 상황 | status | body | delayMillis | dropConnection | defaultHandling |
|---|---|---|---|---|---|---|
| `approve()` | 기본 동작. 승인 처리 후 성공 JSON | 200 | 서버가 만든 JSON | 0 | false | true |
| `reject(String code)` | 명확한 거절 | 400 | `{"code":code,"message":"거절"}` | 0 | false | false |
| `serverError()` | PG 내부 오류 | 500 | 빈 본문 | 0 | false | false |
| `delay(long ms)` | PG는 승인했는데 응답이 늦다 | 200 | 서버가 만든 JSON | ms | false | true |
| `drop()` | 응답 없이 연결 끊김 | — | — | 0 | true | false |

`FakePgServer`

필드 (요청이 서버 스레드에서 처리되므로 `ConcurrentHashMap`·`CopyOnWriteArrayList` 같은 thread-safe 컬렉션)
- `HttpServer server`
- `ObjectMapper mapper = new ObjectMapper()`: `tools.jackson.databind.ObjectMapper`
- `Map<String, Behavior> behaviors`: 경로 접두어 → 동작
- `List<RecordedRequest> requests`: 받은 요청
- `Map<String, ApprovedPayment> approved`: pgOrderId → PG에 승인된 결제
- `Map<String, StoredResponse> idempotent`: `Idempotency-Key` 헤더 값 → 그 키의 첫 응답
- 같은 파일의 private record: `ApprovedPayment(String paymentKey, String orderId, long amount, Instant approvedAt, long canceledAmount)`(`canceledAmount`는 취소 누계. 취소가 오면 올린 값으로 `approved`에 다시 넣는다), `StoredResponse(int status, String body)`(멱등 키별 첫 응답 저장과, 기본 동작이 돌려주는 결과 둘 다에 쓴다)

메서드
- `static FakePgServer start()`: `HttpServer server = HttpServer.create(new InetSocketAddress(0), 0)`(포트 0 = 빈 포트 자동) → `FakePgServer fake = new FakePgServer(server)` → `server.createContext("/", fake::handle)`(모든 경로를 `handle` 하나가 받는다) → `server.setExecutor(Executors.newVirtualThreadPerTaskExecutor())` → `server.start()` → `fake`를 돌려준다. 체크 예외 `IOException`은 `UncheckedIOException`으로 바꿔 던진다(`IntegrationTestSupport`의 static 필드 초기화에서 부르므로)
- `String baseUrl()`: `"http://localhost:" + server.getAddress().getPort()`
- `void on(String pathPrefix, Behavior behavior)`: `behaviors`에 넣는다
- `List<RecordedRequest> requests(String pathPrefix)`: `path`가 접두어로 시작하는 요청만 복사해 돌려준다
- `void reset()`: `behaviors`·`requests`·`approved`·`idempotent`를 모두 비운다
- `void markApproved(String pgOrderId, String paymentKey, long amount, Instant approvedAt)`: `approved`에 `canceledAmount = 0`으로 넣는다. 테스트에서 "PG에는 이미 승인된 상태"를 만든다
- `private void handle(HttpExchange exchange) throws IOException`: 요청 하나를 처리한다. `com.sun.net.httpserver.HttpHandler`의 메서드와 같은 모양이라 `fake::handle`로 등록한다. `exchange`가 요청과 응답을 함께 담은 객체다. 내용은 아래 처리 순서 전부다
- `private StoredResponse ok(ApprovedPayment p)`: 200 + 결제 객체 JSON. 필드는 `paymentKey`=`p.paymentKey`, `orderId`=`p.orderId`, `status`(`p.canceledAmount`가 0이면 `"DONE"`, `p.amount`와 같으면 `"CANCELED"`, 그 외 `"PARTIAL_CANCELED"`), `totalAmount`=`p.amount`, `balanceAmount`=`p.amount - p.canceledAmount`, `method`=`"카드"`, `approvedAt`=`OffsetDateTime.ofInstant(p.approvedAt, ZoneOffset.ofHours(9)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)`(예: `2026-10-14T17:36:22+09:00`). `Map`을 `mapper.writeValueAsString`으로 JSON 문자열로 만든다
- `private StoredResponse notFound()`: 404 + `{"code":"NOT_FOUND_PAYMENT","message":"..."}`

`ok`·`notFound`는 승인·취소·조회에서 둘 이상 쓰므로 따로 두고, 한 곳에서만 쓰는 것은 `handle` 안에 쓴다.

`handle(HttpExchange exchange)` — 요청 하나를 처리하는 순서

1. 요청을 읽어 `RecordedRequest`로 `requests`에 추가한다. path는 `getRequestURI().getPath()`, 헤더는 `getRequestHeaders()`의 이름을 소문자로 바꾸고 값은 첫 번째 값만 담은 `Map<String, String>`(`Headers`가 이름의 대소문자를 바꿔 저장하기 때문), 바디는 `new String(getRequestBody().readAllBytes(), UTF_8)`
2. `behaviors` 키 중 `path`가 그 접두어로 시작하는 것 가운데 가장 긴 접두어의 `Behavior`를 찾는다. 없으면 `Behavior.approve()`로 본다
3. `behavior.dropConnection()`이면 응답 헤더를 보내지 않고 `exchange.close()`만 부르고 끝낸다. 클라이언트는 응답 없이 끊긴 것으로 본다
4. 응답(`status`·`body`)을 정한다. `behavior.defaultHandling()`이 `false`이면 `behavior`의 `status`·`body`를 쓴다. `true`이면 아래처럼 경로로 작업을 가려 만든다. `Idempotency-Key` 헤더(`headers.get("idempotency-key")`)가 `idempotent`에 이미 있으면 처리하지 않고 저장된 응답을 그대로 쓴다.
   - `/v1/payments/orders/{orderId}`(조회): `approved`에서 orderId로 찾아 `ok(p)`. 없으면 `notFound()`
   - `/v1/payments/confirm`(승인): 바디를 `mapper.readValue(바디, Map.class)`로 읽어 `paymentKey`·`orderId`·`amount`(`((Number) 값).longValue()`)로 `ApprovedPayment(…, Instant.now(), 0)`을 만들어 `approved`에 넣고 `ok(p)`. 키가 있으면 응답을 `idempotent`에 저장한다
   - `/v1/payments/{key}/cancel`(취소): `approved`의 값에서 `paymentKey`가 경로의 key와 같은 것을 찾는다. 없으면 `notFound()`. 있으면 바디의 `cancelAmount`를 `canceledAmount`에 더한 새 `ApprovedPayment`를 `approved`에 다시 넣고 `ok(updated)`. 키가 있으면 응답을 `idempotent`에 저장한다
   - 그 밖의 경로: 404 `{"code":"NOT_FOUND","message":"unknown path"}`
5. `behavior.delayMillis()`가 0보다 크면 `Thread.sleep`으로 기다린다(`InterruptedException`은 `Thread.currentThread().interrupt()`로 되돌린다). **4번 뒤**라서 "PG는 승인했는데 우리는 타임아웃" 상황이 된다
6. 응답한다: `Content-Type: application/json` → `sendResponseHeaders(status, 본문이 비면 -1, 아니면 바이트 길이)`(`-1`은 본문 없음) → 본문이 있으면 `getResponseBody().write(bytes)` → `exchange.close()`

`IntegrationTestSupport`에 추가
```java
protected static final FakePgServer pg = FakePgServer.start();

@DynamicPropertySource
static void storageProperties(DynamicPropertyRegistry registry) {   // 1-10에서 만든 메서드에 한 줄 추가
    // 기존 MinIO 등록 4줄
    registry.add("myroutine.toss.base-url", pg::baseUrl);
}

@AfterEach
void resetPg() { pg.reset(); }    // 기존 truncateAllTables 옆에 둔다
```

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `payment/infrastructure/TossClientTest` (`extends IntegrationTestSupport`, `TossClient` 주입) | [ ] 승인 → `Approved`, 금액·paymentKey 일치 / [ ] `reject("REJECT_CARD_COMPANY")` → `Rejected("REJECT_CARD_COMPANY")` / [ ] `serverError()` → `Unknown` / [ ] `delay(1000)`(read-timeout 500ms 초과) → `Unknown`, **그런데 가짜 서버에는 승인 기록이 있다** / [ ] `drop()` → `Unknown` / [ ] `reject("ALREADY_PROCESSED_PAYMENT")` → `Unknown` / [ ] 조회: 승인된 주문 → `Approved`, 없는 주문 → `Rejected("NOT_FOUND_PAYMENT")` / [ ] 요청 헤더에 `idempotency-key`(= paymentId), `authorization`(Basic) |

**테스트 준비** ([N-1](#n-1-단계별-testfixtures-추가))

**이 단계에서 추가하는 헬퍼**: `TestFixtures`에 더할 것은 없다. `FakePgServer`·`Behavior`(`support/`)가 이 단계의 준비물이다.

이미 있는 헬퍼: `signup`, `token`, `login`, `changeStatus`, `openShop`, `closeShop`, `registerProduct`, `hideProduct`, `discontinueProduct`, `registerProductImage`, `putToStorage`, `ConcurrencyRunner`, `address`, `addToCart`, `checkout`, `stockOf`, `StockSnapshot`

- `TestFixtures` 추가 없음. `FakePgServer`는 `IntegrationTestSupport`가 띄운다. 테스트는 `pg.on(경로 접두어, Behavior...)`, `pg.requests(...)`, `pg.markApproved(...)`만 쓴다.
- `paymentId`는 `Ids.newId()`로 만든 임의 UUID면 된다. `pg.requests(...)`는 서버가 받은 요청의 기록이다(개수와 바디, 헤더 확인용). PG에 승인이 기록됐는지는 이어서 `tossClient.queryByOrderId`가 `Approved`를 돌려주는지로 확인한다.

**추가 항목**
- PG 호출 감사 테이블(`payment.pg_call_log`: 작업·HTTP 상태·결과·지연시간·요약) — 지금은 로그 한 줄로 대신한다. Part 7 ELK가 붙으면 로그로 같은 것을 검색할 수 있다
- 외부 호출 메서드 시작 시 `TransactionSynchronizationManager.isActualTransactionActive()`가 true면 예외 — 트랜잭션 안에서 실수로 부르는 것을 막는 안전장치

**리뷰 때 물어볼 것**
- 타임아웃이 났는데 실패로 처리하면 어떤 사고가 나나? (As-Is PAY-01)
- Mockito로 클라이언트를 mock하지 않고 가짜 서버를 쓴 이유는?
- 가짜 서버의 `delay`가 "승인은 하고 늦게 응답"하게 만든 이유는?

---

## 2-4. PG 결제 승인 (L)

**목표**
- 결제 대기 주문을 Toss로 승인한다. 승인되면 PAID(200), 거절되면 결제 실패(402, 재고 원복), 결과가 불확실하면 "결제 확인 중"(202).
- 승인 요청과 주문 만료가 겹쳐도 "만료된 주문에 승인된 결제"가 생기지 않는다.
- 결제 전에 구매자가 주문을 취소할 수 있다(결제창을 닫았을 때 포함). 취소하면 15분을 기다리지 않고 재고 예약이 바로 풀린다.

**왜 지금**: 2-2(주문)와 2-3(Toss 클라이언트)를 연결한다. **이 프로젝트에서 가장 중요한 단계다.**

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 트랜잭션 밖 외부 호출 | DB 트랜잭션을 [상태 선점 → 커밋] → **외부 호출** → [결과 반영 → 커밋]으로 나눈다 (공통 규칙 K) |
| CAS 상태 전이 | `UPDATE ... SET status = 'PAYMENT_IN_PROGRESS' WHERE id = ? AND status = 'PENDING_PAYMENT' AND expires_at > now`. 반영 0건이면 이미 만료·결제된 주문 → PG를 부르지 않는다 |
| 먼저 커밋 | PG를 부르기 **전에** payment(IN_PROGRESS)를 커밋해두면, 호출 중 앱이 죽어도 "확인해야 할 결제"가 남는다 (ADR-004) |
| 결과 반영의 단일 승자 | 결제 결과는 `status IN ('IN_PROGRESS','UNKNOWN')`일 때만 CAS로 반영. 요청 스레드와 대사 잡이 동시에 반영하려 해도 한쪽만 바뀐다 |

### 할 일

**1) 마이그레이션** — `db/migration/payment/V{...}__payment_create_payment.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_payment` |
| order_id | uuid | X | `uk_payment_order` — 주문 1건당 결제 1건 (INV-02) |
| member_id | uuid | X | |
| amount | bigint | X | `> 0` |
| cancelled_amount | bigint | X | DEFAULT 0, `ck_payment_cancelled`: `cancelled_amount >= 0 AND cancelled_amount <= amount` (INV-06) |
| pg_order_id | varchar(64) | X | `uk_payment_pg_order_id` |
| payment_key | varchar(200) | X | `uk_payment_payment_key` |
| billing_key_id | uuid | O | Part 5 |
| method | varchar(30) | O | |
| status | varchar(30) | X | `ck_payment_status`: `IN ('IN_PROGRESS','APPROVED','FAILED','UNKNOWN','PARTIAL_CANCELLED','CANCELLED')` |
| failure_code | varchar(100) | O | 거절 코드 (실패 이력, As-Is ORD-03) |
| approved_at | timestamptz | O | |
| reconcile_attempts | int | X | DEFAULT 0 |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

**2) payment.domain**

| 클래스 | 규격 |
|---|---|
| `PaymentStatus` | 전이: `IN_PROGRESS → {APPROVED, FAILED, UNKNOWN}`, `UNKNOWN → {APPROVED, FAILED}`, `APPROVED → {PARTIAL_CANCELLED, CANCELLED}`, `PARTIAL_CANCELLED → {PARTIAL_CANCELLED, CANCELLED}` |
| `Payment` | `@Entity @Table(name = "payment", schema = "payment")`, `extends BaseTimeEntity`. 필드는 컬럼과 1:1. `static Payment begin(UUID orderId, UUID memberId, Money amount, String pgOrderId, String paymentKey)` → IN_PROGRESS |
| `PaymentRepository` | `save`, `findById`, `Optional<Payment> findByOrderId(UUID orderId)` + CAS 아래 |

`PaymentRepository` CAS (모두 `@Modifying(clearAutomatically = true)`, `version + 1`, `updatedAt = :now` 포함, 반환 `int`)
- `markApproved(UUID id, Collection<PaymentStatus> from, String method, Instant approvedAt, Instant now)` → status APPROVED
- `markFailed(UUID id, Collection<PaymentStatus> from, String failureCode, Instant now)` → FAILED
- `markUnknown(UUID id, Instant now)` → `where status = IN_PROGRESS` → UNKNOWN

**3) payment.api**
```java
public interface PaymentApi {
    UUID begin(StartPayment command);                       // @Transactional. IN_PROGRESS 저장. 같은 orderId면 unique 위반
    PgOutcome callConfirm(UUID paymentId);                  // 트랜잭션 밖 전용. Toss 승인 호출만 하고 payment 상태는 바꾸지 않는다
    PaymentState applyOutcome(UUID paymentId, PgOutcome outcome);   // @Transactional. IN_PROGRESS·UNKNOWN일 때만 반영. 반환: 반영 후 현재 상태
}
public record StartPayment(UUID orderId, UUID memberId, Money amount, String pgOrderId, String paymentKey) {}
public enum PaymentState { IN_PROGRESS, APPROVED, FAILED, UNKNOWN, PARTIAL_CANCELLED, CANCELLED }
public record PgOutcome(PgOutcomeType type, String code, String method, Instant approvedAt) {}   // 승인이 아니면 method·approvedAt은 null. 빌링 결제를 붙이는 5-3에서 `paymentKey`가 더해진다(지금은 넣지 않는다)
public enum PgOutcomeType { APPROVED, REJECTED, UNKNOWN }
```
- `PaymentApiImpl.callConfirm`: payment를 읽어(짧은 읽기 트랜잭션) `tossClient.confirm(...)` → `PgResult`를 `PgOutcome`으로 변환. payment가 없으면 `IllegalStateException`(`paymentId`는 호출자가 방금 `begin`으로 받은 내부 값이라 없으면 클라이언트 잘못이 아니라 버그다. 2-6 `queryPgOutcome`·`cancel`과 같다). 이 단계에는 `PaymentErrorCode`를 만들지 않는다(payment 전용 에러 코드는 5-1의 `BILLING_KEY_*`가 처음이다)
- `applyOutcome`: APPROVED → `markApproved(id, {IN_PROGRESS, UNKNOWN}, ...)`, REJECTED → `markFailed(id, {IN_PROGRESS, UNKNOWN}, code)`, UNKNOWN → `markUnknown(id, now)` → 다시 읽어 현재 상태 반환. 반영 행 수가 0이어도 예외 없이 현재 상태를 반환한다(다른 흐름이 먼저 반영함)
- `payment/api/package-info.java`에 `@NamedInterface("api")` (공통 규칙 J. 없으면 order가 `payment::api`를 쓰는 순간 `ModularityTest`가 실패한다)
- `payment/package-info.java`: `allowedDependencies = {"common"}`, `order/package-info.java`에 `"payment::api"` 추가

**4) order — 결제 승인**

`OrderRepository` 추가
- `@Modifying(clearAutomatically = true) @Query("update Order o set o.status = :to, o.version = o.version + 1, o.updatedAt = :now where o.id = :id and o.status = :from and o.expiresAt > :now") int startPayment(UUID id, OrderStatus from, OrderStatus to, Instant now)` — from = PENDING_PAYMENT, to = PAYMENT_IN_PROGRESS

`OrderErrorCode` 추가: `ORDER_EXPIRED`, `ORDER_ALREADY_PAID`. `CommonErrorCode` 추가: `PAYMENT_FAILED`, `PG_ERROR`, `AMOUNT_MISMATCH`

`OrderPaymentApplier` (order.application, `@Service`) — **PG 결과를 받아 주문 쪽 후속 처리를 하는 곳**

- **왜 필요한가**: payment 모듈은 주문을 모른다(허용 의존이 `common`뿐). `PaymentApi.applyOutcome`은 결제 상태만 바꿔 `PaymentState`를 돌려주므로, 그 결과에 따라 **재고 확정·주문 PAID·장바구니 차감**(성공) 또는 **재고 반환·주문 실패 처리**(거절)를 하는 일은 order가 맡는다.
- **어디서 불리나**: `ConfirmPaymentService`의 트랜잭션 2가 `apply`를 부른다. 2-6 대사 잡은 이미 확정된 결제를 `applyPaymentState`로 처리하는 데 같은 클래스를 재사용한다(결제가 나중에 확정돼도 같은 규칙이 적용되도록).
- **의존성**: `PaymentApi`, `OrderRepository`, `ProductApi`, `CartService`, `Clock`(`markPaid`에 넘길 `Instant now`는 `clock.instant()`)
- 메서드마다 `@Transactional`이 붙지만 서로 `this.`로 부르므로(공통 규칙 K) 새 트랜잭션이 열리는 게 아니라 **호출한 쪽 트랜잭션 안에서 실행**된다. 바깥(트랜잭션 2)이 이미 트랜잭션이라 그대로 동작한다.

| 메서드 | 한 줄 설명 | 입력 → 반환 |
|---|---|---|
| `@Transactional public OrderStatus apply(UUID orderId, UUID paymentId, PgOutcome outcome)` | PG 결과를 payment에 반영하고, 그 결과로 주문을 처리한다 | `PgOutcome` → 처리 뒤 주문 상태 |
| `@Transactional public OrderStatus applyPaymentState(UUID orderId, PaymentState state, String failureCode)` | **결제 상태만 알 때** 주문을 처리한다. 대사는 PG를 직접 조회해 `PaymentState`를 이미 알아서 `PgOutcome`이 없다 | `PaymentState` → 처리 뒤 주문 상태 |
| `@Transactional public void failOrder(UUID orderId, String reason)` | 주문을 실패로 만든다(재고 반환 + `PAYMENT_FAILED`). 2-6에서 쓴다 | 사유 문자열 → 없음 |
| private `void complete(Order order)` | 결제가 승인됐을 때의 주문 처리 | 주문 → 없음 |
| private `void fail(Order order, String reason)` | 결제가 실패했을 때의 주문 처리 | 주문, 사유 → 없음 |

**`apply`** 순서
1. `PaymentState state = paymentApi.applyOutcome(paymentId, outcome)` — payment를 `IN_PROGRESS·UNKNOWN`일 때만 바꾸고 **반영 뒤 현재 상태**를 받는다(다른 흐름이 먼저 반영했으면 그 상태가 온다)
2. `return applyPaymentState(orderId, state, outcome.code())` — `outcome.code()`는 거절이면 거절 코드, 승인이면 null

**`applyPaymentState`** 순서
1. `Order order = orderRepository.findById(orderId)` — 없으면 `IllegalStateException`(이 메서드가 존재를 확인하는 건 아니고, 호출자가 이미 DB에서 확인한 ID만 넘긴다: confirm은 트랜잭션 1의 `findByIdAndMemberId`, 대사는 `findStuckIds`가 읽은 ID. 주문은 삭제하지 않으므로 없으면 버그다. 이때 트랜잭션 2가 통째로 롤백돼 `applyOutcome`이 반영한 payment 상태도 취소된다)
2. `order.getStatus() != PAYMENT_IN_PROGRESS`면 **아무것도 하지 않고 `order.getStatus()`를 반환**한다(이미 처리됨. 같은 결과를 두 번 반영해도 재고·장바구니가 두 번 바뀌지 않게 하는 장치)
3. `state`에 따라
   - `APPROVED` → `complete(order)`
   - `FAILED` → `fail(order, "PG 거절: " + failureCode)`
   - 그 밖(`IN_PROGRESS`, `UNKNOWN`, 취소 계열) → 아무것도 하지 않는다. 주문은 `PAYMENT_IN_PROGRESS`로 남고 대사가 이어서 확정한다
4. `return order.getStatus()` — `complete`·`fail`이 같은 `order` 인스턴스의 상태를 바꿨으므로 바뀐 값이 나온다. 저장은 커밋 때 더티 체킹으로 되니 `save`는 부르지 않는다

**`complete(order)`** — 순서대로 세 가지
1. `productApi.commitReservation(order.getId())` — 예약된 재고를 `reserved → sold`로 확정한다(`void`, 멱등)
2. `order.markPaid(clock.instant())` — 주문·가게주문·품목을 `PAID`로, `paidAt`을 기록한다. 이미 처리된 주문이면 `INVALID_STATE_TRANSITION`이 나므로 위 2번 가드가 먼저 막아야 한다
3. `cartService.deductOrdered(order.getMemberId(), order.quantitiesByProduct())` — 장바구니에서 **주문한 수량만큼** 뺀다(아래). `quantitiesByProduct()`는 `Map<UUID, Integer>`(상품 ID → 수량)다

**`fail(order, reason)`** — 순서대로 두 가지
1. `productApi.releaseReservation(order.getId(), ReleaseReason.PAYMENT_FAILED)` — 예약했던 재고를 `available`로 돌려준다(`void`, 멱등)
2. `order.markPaymentFailed(reason)` — 주문을 `PAYMENT_FAILED`로 바꾸고 `failure_reason`에 사유를 적고 가게주문·품목을 `CANCELLED`로 만든다(사유는 200자까지만 저장된다). **장바구니는 건드리지 않는다**(구매자가 같은 항목으로 다시 체크아웃할 수 있게)

**`failOrder`**: `findById`(없으면 `IllegalStateException`) → `fail(order, reason)`. `applyPaymentState`의 2번 가드는 거치지 않으므로, 호출하는 2-6 쪽이 상태를 확인하고 부른다

**장바구니 차감** — 2-1 코드에 더한다

`CartItemRepository`(domain)와 `JpaCartItemRepository`에 native 쿼리 두 개를 더한다. 모두 `@Modifying`, 반환 `int`.
- `deleteIfQuantityAtMost(UUID cartId, UUID productId, int quantity)`: `DELETE FROM orders.cart_item WHERE cart_id = :cartId AND product_id = :productId AND quantity <= :quantity`
- `deductQuantity(UUID cartId, UUID productId, int quantity, Instant now)`: `UPDATE orders.cart_item SET quantity = quantity - :quantity, version = version + 1, updated_at = :now WHERE cart_id = :cartId AND product_id = :productId AND quantity > :quantity`

`CartService.deductOrdered(UUID memberId, Map<UUID, Integer> quantitiesByProduct)` — `@Transactional`(호출자 트랜잭션에 합류)
1. `cartRepository.findByMemberId` — 없으면 반환
2. 상품마다 `deleteIfQuantityAtMost` → `deductQuantity` 순서로 부른다

- **순서가 중요하다**: ②를 먼저 하면 3개 중 2개를 주문했을 때 3→1로 줄어든 행을 곧바로 ①(`quantity <= 2`)이 지워 버린다. ①을 먼저 하면 원래 수량이 주문 수량 이하인 행만 지워지고, 남은 행만 ②로 차감된다. `quantity`는 1~99 제약(`ck_cart_item_quantity`)이라 0이 되는 UPDATE는 하지 않는다.
- **결제 완료를 막지 않는다**: 행 단위 원자적 SQL이라 구매자가 같은 시각에 수량을 바꿔도 낙관적 락 예외가 우리 쪽에서 나지 않는다(`version + 1`이 올라 구매자의 수정 쪽이 `CONFLICT_RETRY`가 된다). 장바구니에 그 상품이 없으면 0행이라 아무 일도 없다.
- 대사(2-6)도 같은 `applier`를 쓰므로 결제가 나중에 확정돼도 같은 규칙으로 차감된다.
- 구독 회차 주문(Part 5)은 이 단계의 호출을 타지 않도록 그때 `type == NORMAL` 조건을 더한다.

`ConfirmPaymentService` (order.application, `@Service`) — 승인 요청 한 건의 흐름. 트랜잭션 두 개 사이에서 Toss를 부르므로 메서드에 `@Transactional`을 붙이지 않고 `TransactionTemplate`으로 트랜잭션 1·2를 직접 연다(공통 규칙 K)

| 타입 | 규격 |
|---|---|
| `ConfirmPaymentCommand` | `record (String paymentKey, String pgOrderId, long amount)` — `ConfirmPaymentRequest`에서 만든다 |
| `ConfirmResult` | `record (UUID orderId, OrderStatus status)` — 컨트롤러가 `PAID`면 200, `PAYMENT_IN_PROGRESS`면 202로 내려보낸다 |
| `ConfirmResult confirm(UUID memberId, UUID orderId, ConfirmPaymentCommand command)` | 아래 흐름 |

1. **트랜잭션 1** (커밋)
   1. `orderRepository.findByIdAndMemberId(orderId, memberId)` — 없으면(남의 주문 포함) 404 `ORDER_NOT_FOUND`
   2. `command.amount()`가 주문 총액과 다르거나 `command.pgOrderId()`가 `order.pgOrderId()`와 다르면 422 `AMOUNT_MISMATCH` (PG를 부르지 않는다)
   3. `startPayment(orderId, PENDING_PAYMENT, PAYMENT_IN_PROGRESS, now)` — `from`은 **항상 `PENDING_PAYMENT`로 고정**한다(1번에서 읽은 `order.getStatus()`를 넘기면 CAS가 아니라 "지금 상태가 뭐든 바꾼다"가 되어 `PAID` 주문도 `PAYMENT_IN_PROGRESS`로 되돌린다). **0행이면** 주문을 다시 읽어(`findById`. 소유는 1번에서 확인했다. 이 조회는 `clearAutomatically`로 캐시가 비워진 뒤라 DB의 새 상태를 읽는다) 상태로 응답을 정한다. 1번에서 읽은 주문이라 없을 수 없고 주문은 삭제하지 않으므로 못 찾으면 `IllegalStateException`(버그)이다. PG는 부르지 않는다
      - `PAID` → 409 `ORDER_ALREADY_PAID`
      - `PAYMENT_IN_PROGRESS` → 다른 요청이 승인 중이므로 `ConfirmResult(PAYMENT_IN_PROGRESS)`(202)
      - `PENDING_PAYMENT`인데 `expires_at`이 지났다 → 409 `ORDER_EXPIRED`
      - `CANCELLED` → 409 `INVALID_STATE_TRANSITION` (아래 "결제 전 주문 취소"가 먼저 이겼다)
      - `EXPIRED`·`PAYMENT_FAILED` → 409 `INVALID_STATE_TRANSITION`(이미 끝난 주문이라 `CANCELLED`와 같게 처리한다)
   4. 1행이면 `paymentApi.begin(new StartPayment(orderId, memberId, 주문 총액, pgOrderId, paymentKey))` → `paymentId`
   - **트랜잭션 1이 돌려주는 값**: `tx.execute`의 람다는 값을 돌려줘야 하고, 이 트랜잭션은 "결제를 시작했다(`paymentId`로 2번을 이어 간다)"와 "여기서 끝낸다(3번의 202 `ConfirmResult`)" 두 갈래다. 둘 중 하나를 담는 작은 값(예: `record Started(UUID paymentId, ConfirmResult early)`, 한쪽은 null. 이름은 권장안)을 돌려주고, `early`가 있으면 2·3을 건너뛰고 그 결과를 반환한다. 409·422는 `BusinessException`을 던지면 트랜잭션 1이 롤백된다
2. **트랜잭션 밖**: `paymentApi.callConfirm(paymentId)` → `PgOutcome`
3. **트랜잭션 2** (커밋): `orderPaymentApplier.apply(orderId, paymentId, outcome)` → `OrderStatus`
4. 결과를 응답으로 바꾼다
   - `PAID` → `ConfirmResult(orderId, PAID)` (200)
   - `PAYMENT_FAILED` → 402 `PAYMENT_FAILED`, `details.pgCode = outcome.code()`
   - `PAYMENT_IN_PROGRESS` → `ConfirmResult(orderId, PAYMENT_IN_PROGRESS)` (202)

**결제 전 주문 취소** — 구매자가 `PENDING_PAYMENT` 주문을 직접 취소한다

- **누가·언제**: 구매자가 결제창을 닫았거나 마음이 바뀌었을 때 클라이언트가 `POST /api/orders/{id}/cancel`을 부른다. 같은 장바구니로 체크아웃을 반복해 결제 대기 주문이 여러 개 생겼을 때 남은 주문을 치우는 수단이기도 하다(2-2 중복 체크아웃 한계).
- **범위**: 결제가 시작되지 않은 `PENDING_PAYMENT`만. `PAYMENT_IN_PROGRESS`(승인 요청 진행 중)·`PAID`는 취소하지 않는다. 결제 후 취소는 2-8 품목 취소다. PG를 부르지 않고 돈도 움직이지 않으므로 환불이 아니다. 상태는 `PAYMENT_FAILED`가 아니라 `CANCELLED`다(PG 거절만 `PAYMENT_FAILED`라 결제 실패율 같은 지표에 취소가 섞이지 않는다).

`OrderRepository` 추가
- `@Modifying(clearAutomatically = true) @Query("update Order o set o.status = :to, o.version = o.version + 1, o.updatedAt = :now where o.id = :id and o.status = :from") int cancelBeforePayment(UUID id, OrderStatus from, OrderStatus to, Instant now)` — from = `PENDING_PAYMENT`, to = `CANCELLED`. 실패가 아니므로 `failure_reason`은 비워 둔다. 2-5의 `expire`와 달리 `expires_at` 조건은 없다(만료 시각이 지났어도 잡이 아직 안 돌았다면 취소는 허용한다)

`PendingOrderCancelService` (order.application. 2-8의 `CancelOrderLineService`와 다른 클래스다) — `@Transactional public OrderStatus cancel(UUID memberId, UUID orderId)`
1. `findByIdAndMemberId(orderId, memberId)` — 없으면(남의 주문 포함) 404 `ORDER_NOT_FOUND`. 소유 확인을 CAS 조건에 넣지 않고 먼저 읽는 이유는 404와 409를 구분하기 위해서다
2. `cancelBeforePayment(orderId, PENDING_PAYMENT, CANCELLED, now)` — 0행이면 409 `INVALID_STATE_TRANSITION`(그사이 결제가 시작·완료됐거나 이미 만료·취소됨. 같은 취소를 두 번 보내면 두 번째가 여기서 409)
3. `productApi.releaseReservation(orderId, ReleaseReason.PAYMENT_FAILED)` — `ReleaseReason`에 취소 값이 없어 기존 `PAYMENT_FAILED`를 쓴다. 예약은 `RELEASED`가 되고 이 사유는 어디에도 저장되지 않는다. 이름이 어색하면 `CANCELLED`를 더해도 되지만 Part 1 코드(`ReleaseReason`, `ProductApiImpl`의 `switch`)를 고쳐야 해서 이번엔 하지 않는다
4. `order = findById(orderId)`(**CAS 뒤에** 읽는다) → `order.cancelPendingChildren()` → `CANCELLED` 반환

- **락 순서(데드락 회피)**: 3번 `releaseReservation`은 예약을 `findAllByOrderIdAndStatusOrderByProductIdAsc`로 **상품 ID 오름차순**으로 읽어 `stock` 행을 잡는다. `reserve`(자바에서 `productId` 정렬)·`commitReservation`·만료와 같은 순서라서 취소를 위한 정렬 코드를 따로 만들 필요가 없다. 주문 A(상품 x, y)의 취소와 주문 B(상품 y, x)의 체크아웃이 겹쳐도 한쪽이 y를 먼저 잡고 다른 쪽이 x를 먼저 잡는 교차 대기가 생기지 않는다. 락은 항상 **주문 행(CAS) → 재고 행(상품 ID 오름차순)** 순서다.
- **자바 정렬과 DB 정렬**: 자바 `UUID.compareTo`는 부호 있는 비교라 최상위 비트가 다르면 Postgres `uuid` 정렬과 순서가 갈린다(2026-10-13 실행 확인: `10000000-…`·`90000000-…`을 자바는 9가 먼저, Postgres는 1이 먼저). 이 프로젝트의 ID는 `Ids.newId()`가 만드는 UUIDv7이고 앞 48비트가 시각이라 최상위 비트가 항상 0이라 두 순서가 같다. 상품 ID를 임의 UUID(v4)로 만드는 경로가 생기면 `reserve`의 자바 정렬을 DB `ORDER BY`로 맞춰야 한다.
- **승인·만료와 겹칠 때**: 취소·승인 시작(`startPayment`)·만료(`expire`)가 모두 `status = PENDING_PAYMENT`를 조건으로 하는 CAS라 셋 중 하나만 1행을 갱신한다. 승인이 먼저면 취소는 409, 취소가 먼저면 승인은 `startPayment` 0행으로 PG를 부르지 않고 409 `INVALID_STATE_TRANSITION`이다(주문 상태가 `CANCELLED`).
- 멱등 키는 요구하지 않는다(`@Idempotent` 없음). 이미 취소된 주문에 다시 보내면 409라서 안전하다.
- Part 3에서도 이 서비스는 `order-expired` 이벤트를 발행하지 않는다. 소비자가 알림뿐이고 구매자가 직접 한 일이라 "결제에 실패했습니다" 알림이 맞지 않는다([3-3](part3-events.md)). 다른 소비자가 생기면 그때 reason을 더한다.

**5) order.web** — `OrderController`에 추가

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/orders/{id}/payment/confirm` `@Idempotent` | `ConfirmPaymentRequest(@NotBlank String paymentKey, @NotBlank String pgOrderId, @NotNull @Min(1) Long amount)` | 200 `{orderId, status: "PAID"}` / 202 `{orderId, status: "PAYMENT_IN_PROGRESS"}` / 402·409·422 공통 에러 |
| `POST /api/orders/{id}/cancel` | 없음 | 200 `{orderId, status: "CANCELLED"}` / 404 `ORDER_NOT_FOUND` / 409 `INVALID_STATE_TRANSITION` |

**6) (선택) 실제 Toss로 수동 확인**: Toss 결제위젯 예제 HTML을 `src/main/resources/static/toss-test.html`로 두고 로컬에서 결제창 → successUrl에서 받은 paymentKey로 confirm을 직접 호출해 본다. 클라이언트 키는 공개용이지만 파일에 넣지 말고 실행할 때 입력한다. 결과는 PR에 스크린샷으로. `SecurityConfig`는 허용 목록에 없는 경로를 모두 인증 요구하므로 이 페이지도 그대로는 401이다 — 확인하는 동안만 로컬에서 `permitAll`에 추가하고 커밋하지 않는다

### 완료 확인

| 테스트 클래스 | 케이스 (모두 가짜 PG 서버) |
|---|---|
| `payment/domain/PaymentStatusTest` | [ ] 전체 조합 파라미터화 |
| `order/web/PaymentConfirmTest` | 아래 |
| `order/web/OrderCancelTest` | 아래 (결제 전 주문 취소) |
| `payment/application/PaymentApiImplTest` | [ ] `applyOutcome`: IN_PROGRESS → APPROVED / 이미 APPROVED인 결제에 REJECTED 반영 → 그대로 APPROVED / [ ] 같은 orderId로 `begin` 두 번 → `DataIntegrityViolationException`(`uk_payment_order`) |

**테스트 준비** ([N-1](#n-1-단계별-testfixtures-추가))

**이 단계에서 추가하는 헬퍼** (`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `createPendingOrder(UUID buyerId, UUID... productIds)` → `CheckoutResult` | 상품마다 1개씩 `addToCart` → `address` → `checkout`. 수량이 필요하면 `createPendingOrder(UUID buyerId, Map<UUID, Integer>)`(`LinkedHashMap`)를 오버로드 | `PaymentConfirmTest`의 43,000원 주문 |
| `confirmPayment(memberId, orderId)` → `ConfirmResult` | `ConfirmPaymentService.confirm(memberId, orderId, new ConfirmPaymentCommand("pk_" + orderId, "ORD-" + orderId, totalAmount))`. `totalAmount`는 JDBC로 읽는다. 가짜 서버의 기본 동작이 승인이다. 거절·불확실이면 서비스가 예외나 202 결과를 돌려주므로 그대로 둔다 | 2-5·2-6·2-7 이후 "결제된 주문", 2-6의 타임아웃 주입 |
| `setOrderExpired(orderId, Instant)` | JDBC `UPDATE orders.orders SET expires_at = ? WHERE id = ?` | `PaymentConfirmTest` 만료, `OrderExpirationServiceTest` |

이미 있는 헬퍼(앞 단계·Part 1에서 만든 것, 그대로 쓴다): `signup`, `token`, `login`, `changeStatus`, `openShop`, `closeShop`, `registerProduct`, `hideProduct`, `discontinueProduct`, `registerProductImage`, `putToStorage`, `ConcurrencyRunner`, `address`, `addToCart`, `checkout`, `stockOf`, `StockSnapshot`

- 주문: 가격 합이 43,000이 되게 `registerProduct(memberId, shopId, price, stock)`로 상품을 만들고 `createPendingOrder(buyerId, productIds...)`(수량이 필요하면 `Map` 오버로드). 단언은 JDBC와 `stockOf`로 한다.
- 만료: `setOrderExpired(orderId, 과거)`. 타임아웃: `pg.on("/v1/payments/confirm", Behavior.delay(1000))`. 거절: `Behavior.reject("REJECT_CARD_COMPANY")`.
- 동시 2건: `ConcurrencyRunner.run(List.of(작업1, 작업2))` — **서로 다른 `Idempotency-Key`**를 쓰므로 같은 작업을 N번 돌리는 `run(int, Callable)`로는 안 된다([N](#n-테스트-지원-part-2에서-추가)의 오버로드).
- 이미 PAID: `confirmPayment(memberId, orderId)`(기본 승인) 후 다른 키로 HTTP confirm.
- 장바구니 변경: `createPendingOrder`는 `cartItemId`를 돌려주지 않으므로 이 케이스는 `addToCart`로 ID를 받아 `checkout(memberId, List.of(cartItemId), addressId)`을 직접 부른다. 결제 중 변경은 그 뒤에 `addToCart`(수량 합산)·`CartService.changeQuantity`·`removeItem`을 다시 부르고, 결과는 JDBC `SELECT quantity FROM orders.cart_item WHERE id = ?`로 본다.

`PaymentConfirmTest` (43,000원 주문을 `TestFixtures`로 만들어 시작)
- [ ] **승인** → 200 PAID, `stock.sold` 증가·`reserved` 0, 가게주문·품목 PAID, payment APPROVED, 가짜 서버 요청의 `idempotency-key` = paymentId, 주문한 수량만큼 장바구니에서 빠짐(전부 주문했으면 항목 삭제)
- [ ] **거절**(`reject("REJECT_CARD_COMPANY")`) → 402 `PAYMENT_FAILED`(`details.pgCode`), 주문 PAYMENT_FAILED·`failure_reason`에 코드, payment FAILED·`failure_code`, 재고 available 원복, 가게주문·품목 CANCELLED (As-Is ORD-03: 실패 이력이 남음), **장바구니는 그대로**(같은 항목으로 다시 체크아웃할 수 있다)
- [ ] **타임아웃**(`delay(1000)`) → 202, payment UNKNOWN, 주문 PAYMENT_IN_PROGRESS, 재고 아직 reserved, 장바구니 그대로(결과가 확정될 때까지 건드리지 않는다)
- [ ] **만료된 주문**(JDBC로 `expires_at`을 과거로) → 409 `ORDER_EXPIRED`, `pg.requests("/v1/payments/confirm")` 0건
- [ ] **같은 주문 confirm 동시 2건**(서로 다른 Idempotency-Key, `delay(300)`) → PG 승인 요청 1건, 응답은 200 하나 + 202 하나
- [ ] **금액 위변조**(amount + 1) → 422 `AMOUNT_MISMATCH`, PG 요청 0건, 주문 PENDING_PAYMENT 그대로
- [ ] 이미 PAID인 주문 confirm(다른 키) → 409 `ORDER_ALREADY_PAID`
- [ ] **결제 중 장바구니 변경**: 2개 체크아웃 후 같은 상품을 1개 더 담고(2→3) 승인 → 장바구니에 1개 남음(남은 수량이 주문 수량 이하여도 지워지면 안 된다. `deleteIfQuantityAtMost`를 `deductQuantity`보다 먼저 부르는 순서를 이 케이스가 지킨다. 2→5처럼 남는 수량이 주문 수량보다 크면 순서를 뒤집어도 통과해 못 잡는다) / 수량을 1개로 줄이고(2→1) 승인 → 항목 삭제, 오류 없이 PAID / 항목을 직접 삭제하고 승인 → 오류 없이 PAID

`OrderCancelTest` (43,000원 주문을 `createPendingOrder`로 만들어 시작. 호출은 `POST /api/orders/{id}/cancel`)
- [ ] **취소** → 200 **CANCELLED**(`PAYMENT_FAILED`가 아니다), `failure_reason`은 null, 재고 available 원복·예약 RELEASED, 가게주문·품목 CANCELLED, 장바구니 그대로, PG 요청 0건
- [ ] 취소 후 같은 장바구니 항목으로 다시 체크아웃 → 201 (재고가 풀렸다)
- [ ] 남의 주문 → 404 `ORDER_NOT_FOUND`, 주문·재고 그대로
- [ ] PAYMENT_IN_PROGRESS(confirm 타임아웃 202 이후) → 409 `INVALID_STATE_TRANSITION`, 재고 아직 reserved
- [ ] PAID → 409 `INVALID_STATE_TRANSITION`, 재고 sold 그대로
- [ ] 이미 취소·만료된 주문 → 409, 재고가 두 번 풀리지 않는다
- [ ] **같은 주문 동시 취소 2건**(`ConcurrencyRunner.run(2, ...)`) → 200 하나 + 409 하나, `stock.available`이 1회분만 원복
- [ ] **취소된 주문에 confirm** → 409 `INVALID_STATE_TRANSITION`, PG 요청 0건
- [ ] **교차 락**: 상품 x, y를 `productApi.reserve(orderA, [x, y], expiresAt)`로 예약해 둔 주문 A를 만들고, `productApi.releaseReservation(orderA, PAYMENT_FAILED)`(취소가 부르는 메서드)와 `productApi.reserve(orderB, [y, x], expiresAt)`(항목 순서를 뒤집어 **직접** 넘긴다)를 `ConcurrencyRunner.run(List.of(...))`로 동시에 실행 → 둘 다 예외 없이 끝남. 20회 반복해도 같다. **HTTP로 하지 않는 이유**: 체크아웃은 장바구니 항목을 `Collectors.toMap`(HashMap)으로 모아 `reserve`에 넘기므로 요청의 항목 순서가 락 순서를 정하지 못한다(2026-10-15 확인: HTTP로 항목 순서를 뒤집고 `reserve`의 정렬을 지워도 20회 모두 통과했다. `[y, x]`를 직접 넘기면 정렬을 지웠을 때 20회 모두 실패했다)

**리뷰 때 물어볼 것**
- 트랜잭션 1과 2 사이에서 앱이 죽으면 무엇이 남고, 누가 이어서 처리하나? (→ 2-6)
- `@Transactional` 메서드 안에서 Toss를 부르면 정확히 어떤 문제가 생기나? (As-Is ORD-02)
- 만료와 승인이 경쟁할 때 "만료된 주문에 승인된 결제"가 왜 생길 수 없나? CAS에 `expires_at > now`를 넣은 이유는?
- 2PC를 안 쓴 이유는?
- 취소와 승인 시작이 동시에 오면 누가 이기고, 왜 둘 다 성공할 수 없나? (CAS의 `from`)
- 취소가 `stock` 행을 잡는 순서는? 정렬하지 않으면 어떤 교차가 데드락이 되나? 자바 UUID 정렬과 DB 정렬이 다르면 어떻게 되나?

---

## 2-5. 주문 만료 잡 (+ 잡 락) (M)

**목표**: 결제창에서 이탈한 주문(15분 경과)을 자동으로 만료시키고 재고를 되돌린다. 서버가 여러 대여도 잡은 한 곳에서만 돈다.

**왜 지금**: 2-2부터 결제 대기 주문이 생기는데, 아무도 정리하지 않으면 재고가 영원히 묶인다(As-Is CAT-01).

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 스케줄 작업 | `@Scheduled`로 주기 실행. **서버마다** 실행된다는 점에 주의 |
| 잡 락 (세션 advisory lock) | 전용 커넥션에서 `pg_try_advisory_lock` → 작업 → `pg_advisory_unlock`. 다른 서버는 건너뛴다. 트랜잭션 레벨 `pg_advisory_xact_lock`은 첫 커밋에 풀려서 여기선 못 쓴다 |
| 건별 트랜잭션 | 100건을 한 트랜잭션으로 묶지 않고 주문마다 트랜잭션. 한 건 실패가 나머지를 막지 않는다 |

### 할 일

**1) common.job — 잡 공통 장치**

스케줄 잡(만료 2-5, 대사 2-6, 환불 복구 2-8b, 자동 확정 2-10)은 모두 "락을 잡고 → 작업을 돌리고 → 건수를 로그로 남기고 → 예외는 삼킨다"는 같은 틀을 쓴다. 틀을 한 번만 만들어 두고, 각 잡은 아래 흐름의 맨 앞 `XxxJob`만 갖는다.

`XxxJob`(`@Scheduled`) → `JobRunner.run`(로그·예외 처리) → `JobLock.runExclusively`(서버 간 중복 방지) → `XxxService.xxxBatch()`(실제 처리)

| 클래스 | 하는 일 | 규격 |
|---|---|---|
| `JobLock` | 서버가 여러 대여도 같은 이름의 잡이 한 곳에서만 돌게 한다. 락을 잡은 서버만 작업을 실행하고 못 잡은 서버는 건너뛴다 | `@Component`, 의존성 `DataSource`. `boolean runExclusively(String jobName, Runnable task)` — 입력: 잡 이름, 실행할 작업. 반환: 작업을 실행했으면 `true`, 다른 서버가 락을 쥐고 있어 건너뛰었으면 `false` |
| `JobRunner` | 잡 한 번의 실행을 감싼다: 로그에 추적 ID·잡 이름을 붙이고, 락 안에서 작업을 돌리고, 건수·시간을 남기고, 예외를 삼킨다 | `@Component`, 의존성 `JobLock`. `void run(String jobName, Supplier<Integer> task)` — `task`는 **처리한 건수**를 돌려준다 |
| `common.config.SchedulingConfig` | `@Scheduled`를 켠다. 테스트 중에는 스케줄러가 끼어들지 않도록 프로퍼티로 끈다 | `@Configuration @EnableScheduling @ConditionalOnProperty(prefix = "myroutine.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)` — `myroutine.scheduling.enabled`가 `false`이면 이 설정이 등록되지 않아 `@Scheduled`가 동작하지 않는다(생략하면 켜진다) |

`JobLock.runExclusively` 순서 — `Runnable`은 값을 돌려주지 못하므로 처리 건수는 `JobRunner`가 따로 받는다(아래 2번)
1. `try (Connection c = dataSource.getConnection())` — 이 커넥션은 **락 전용**이다. 작업(`task.run()`)은 풀에서 다른 커넥션을 꺼내 쓰므로 `c`를 작업에 넘기지 않는다(잡이 도는 동안 풀의 커넥션 한 개가 락에 묶인다)
2. `PreparedStatement`로 `SELECT pg_try_advisory_lock(hashtext(?))`를 실행한다(파라미터 `"job:" + jobName`). `ResultSet.getBoolean(1)`이 false면 다른 서버가 쥐고 있으므로 작업을 실행하지 않고 `return false`
3. `try { task.run(); } finally { c로 SELECT pg_advisory_unlock(hashtext(?)) }` → `return true`
- **unlock을 빼먹으면** 커넥션이 풀로 돌아가도 락이 남는다(세션 레벨 락은 커넥션이 닫힐 때까지 유지). 그래서 finally에서 반드시 푼다.
- **unlock은 락을 잡은 같은 커넥션 `c`로 한다.** 세션 락은 잡은 세션의 것이라 다른 커넥션에서 풀면 `false`만 돌아오고 락은 남는다.
- `SQLException`은 체크 예외인데 시그니처에 `throws`가 없다. 잡아서 비검사 예외로 감싸 던진다. 예외 종류는 문서에 정해져 있지 않다(확인 필요) — `JobRunner`가 어떤 예외든 로그로 남기고 삼킨다.

`JobRunner.run` 순서
1. `MDC.put(TraceIds.MDC_KEY, TraceIds.newId())`, `MDC.put("job", jobName)` — 요청이 없는 스케줄 스레드에서도 로그에 `traceId`가 찍히게 한다(`org.slf4j.MDC`, `TraceIds`는 `common.web`)
2. 시작 시각 → `boolean ran = jobLock.runExclusively(jobName, () -> count.set(task.get()))` — 람다 안에서는 지역 변수에 대입할 수 없으므로 `AtomicInteger count`에 담는다
3. `ran`이면 `log.info("job {} processed {} in {}ms", ...)`, 아니면(락을 못 잡음) `log.debug("job {} skipped: locked", ...)`
4. 예외는 `log.error("job {} failed", jobName, e)`로 남기고 다시 던지지 않는다(다음 주기에 재실행)
5. `finally`에서 MDC 두 키 제거

- `application-test.yaml`: `myroutine.scheduling.enabled: false`

**2) 만료 처리**

| 대상 | 규격 |
|---|---|
| 마이그레이션 | `db/migration/order/V{...}__order_add_orders_status_expires_at_index.sql`: `CREATE INDEX idx_orders_status_expires_at ON orders.orders (status, expires_at)` — 아래 `findExpiredIds`의 `status`·`expires_at` 조건을 받친다 |
| `OrderRepository` | 쿼리 두 개를 더한다(아래) |
| `OrderExpirationService` (order.application) | 클래스에 `@Transactional` 없음. 의존성 `OrderRepository`, `ProductApi`, `TransactionTemplate tx`, `Clock`(`now`는 `clock.instant()`). `public int expireBatch()` |
| `OrderExpirationJob` (order.infrastructure) | `@Component`. `@Scheduled(fixedDelay = 60_000) void run() { jobRunner.run("expire-orders", expirationService::expireBatch); }` |

**`OrderExpirationJob`**: 스케줄러가 부르는 진입점이다. 로직은 없고 잡 이름(락 이름이자 로그의 `job` 값)과 실제 처리 메서드(`expireBatch`)만 `JobRunner`에 넘긴다. `fixedDelay = 60_000`은 "이전 실행이 끝나고 60초 뒤에 다시"라 서버 한 대 안에서는 실행이 겹치지 않는다.

**`OrderExpirationService.expireBatch`**: 결제 대기 시간(15분)이 지난 `PENDING_PAYMENT` 주문을 최대 100건 만료시킨다. 서비스 전체를 한 트랜잭션으로 묶지 않고, 외부 호출이 없어도 **주문 한 건마다 트랜잭션**을 연다(`TransactionTemplate tx`). 한 건이 실패해도 그 건만 롤백되고 나머지는 처리된다.

`OrderRepository` 추가 — 시그니처는 `order.domain`의 `OrderRepository`에, `@Query`·`@Modifying`은 `order.infrastructure`의 `JpaOrderRepository`에 둔다(2-2 `OrderRepository` 구조)

| 메서드 | 하는 일 | 입력 → 반환 |
|---|---|---|
| `@Query("select o.id from Order o where o.status = :status and o.expiresAt <= :now order by o.expiresAt") List<UUID> findExpiredIds(OrderStatus status, Instant now, Limit limit)` | 만료 후보 주문의 **ID만** 오래된 순으로 읽는다. 처리는 건별 트랜잭션에서 CAS 뒤에 주문을 다시 읽으므로 후보 목록에는 ID면 충분하다 | 상태·기준 시각·`Limit`(`org.springframework.data.domain.Limit`) → ID 목록 |
| `@Modifying(clearAutomatically = true) @Query("update Order o set o.status = :to, o.version = o.version + 1, o.updatedAt = :now where o.id = :id and o.status = :from and o.expiresAt <= :now") int expire(UUID id, OrderStatus from, OrderStatus to, Instant now)` | 주문 상태를 `from`일 때만 `to`로 바꾸는 CAS(공통 규칙 L). 후보를 읽은 뒤 구매자가 취소했거나, 만료 시각 직전에 시작된 승인(2-4 `startPayment`)이 아직 커밋 전이었다면 `status = :from`이 맞지 않아 0행이 된다(커밋 전이면 이 UPDATE가 행 락에서 기다렸다가 커밋 뒤 `status`를 다시 평가한다. 후보를 읽은 **뒤에** 새로 시작하는 승인은 `startPayment`의 `expires_at > :now`에서 거절되므로 여기까지 오지 않는다) | → **1이면 내가 만료시킴, 0이면 그사이 다른 흐름이 바꿈** |

`expireBatch` 순서 → 반환: 만료시킨 건수(`int`)
1. `List<UUID> ids = findExpiredIds(PENDING_PAYMENT, now, Limit.of(100))` — `now`는 `clock.instant()`
2. 각 ID마다 `try { tx.execute(s -> expireOne(id)) } catch (Exception e) { log.error("expire failed orderId={}", id, e); }` — `tx.execute`가 `Boolean`(만료시켰으면 true)을 돌려주므로 true인 것만 센다. 건별 트랜잭션 안에서 예외가 나면 그 건의 CAS까지 롤백되어 주문은 `PENDING_PAYMENT`로 남고 다음 주기에 다시 후보가 된다(테스트의 "한 건 실패 주입"이 이 경우다)
3. 만료시킨 건수 반환

`expireOne(id)` — `expireBatch`의 private 메서드. `tx.execute` 안에서 불리므로 **트랜잭션 안**이다(별도 `@Transactional` 불필요). 반환 `boolean`
1. `expire(id, PENDING_PAYMENT, EXPIRED, now)` → 0이면 return false (그사이 구매자가 취소했거나, 커밋 전이던 승인 시작이 반영됐거나, 다른 실행이 이미 만료시킴. 이 건은 만료시키지 않는다)
2. `productApi.releaseReservation(id, ReleaseReason.EXPIRED)` — 예약된 재고를 `reserved → available`로 돌리고 예약을 `EXPIRED`로 만든다(`void`, 멱등)
3. `Order order = findById(id)` (**CAS 뒤에** 읽는다: `expire`의 `clearAutomatically`가 영속성 컨텍스트를 비우므로 CAS 앞에 읽은 엔티티는 낡은 상태다) → `order.cancelPendingChildren()` — 주문 상태는 이미 `EXPIRED`이고, 이 호출이 `PENDING`인 가게주문·품목을 `CANCELLED`로 바꾼다. 저장은 커밋 때 더티 체킹이라 `save`를 부르지 않는다
4. return true

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `common/job/JobLockTest` | [ ] 스레드 2개가 동시에 같은 잡 이름으로 `runExclusively`(작업 안에서 latch로 잠시 대기) → 하나만 true / [ ] 작업이 예외를 던져도 다음 호출은 true(락이 풀림) / [ ] 다른 잡 이름은 서로 막지 않는다 |
| `order/application/OrderExpirationServiceTest` | [ ] `expires_at`을 과거로 → `expireBatch()` → EXPIRED, 재고 available 원복·예약 EXPIRED, 가게주문·품목 CANCELLED, 장바구니 그대로 / [ ] **PAYMENT_IN_PROGRESS 주문은 `expires_at`이 지나도 만료되지 않는다** / [ ] 아직 안 지난 주문은 그대로 / [ ] **한 주문 처리 실패가 나머지를 막지 않는다**: 주문 3개 중 하나의 재고를 JDBC로 망가뜨려(`stock.reserved`를 0으로, `available`을 그만큼 증가) 해제가 실패하게 하면 → 나머지 2개는 EXPIRED, 반환값 2 |

**테스트 준비** ([N-1](#n-1-단계별-testfixtures-추가))

**이 단계에서 추가하는 헬퍼** (`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `breakReservedStock(productId)` | JDBC `UPDATE product.stock SET available = available + reserved, reserved = 0 WHERE product_id = ?`. `ck_stock_balance`(합 = received)는 합이 같아 통과하고, 이후 해제·확정이 "예약된 재고가 부족해"(`IllegalStateException`)로 실패한다 | `OrderExpirationServiceTest` 한 건 실패 주입, 2-6 보상 |

이미 있는 헬퍼(앞 단계·Part 1에서 만든 것, 그대로 쓴다): `signup`, `token`, `login`, `changeStatus`, `openShop`, `closeShop`, `registerProduct`, `hideProduct`, `discontinueProduct`, `registerProductImage`, `putToStorage`, `ConcurrencyRunner`, `address`, `addToCart`, `checkout`, `stockOf`, `StockSnapshot`, `createPendingOrder`, `confirmPayment`, `setOrderExpired`

- 만료 대상: `createPendingOrder` 후 `setOrderExpired(orderId, now - 1분)` → `expireBatch()`. 아직 안 지난 주문은 그대로 둔다.
- PAYMENT_IN_PROGRESS: `pg.on("/v1/payments/confirm", Behavior.delay(1000))` → `confirmPayment`(202) → `setOrderExpired`으로 과거로.
- 실패 주입: 주문 3개를 **서로 다른 상품**으로 만들고(재고 행이 달라야 하나만 망가진다) 그중 하나만 `breakReservedStock(productId)`.
- `JobLockTest`: `ConcurrencyRunner.run(2, () -> jobLock.runExclusively("job", 작업))` — 작업 안에서 latch로 잠시 붙잡아 두면 결과 중 `true`는 하나다.

**리뷰 때 물어볼 것**
- 트랜잭션 레벨 advisory lock과 세션 레벨 advisory lock의 차이는?
- 잡 락이 있는데도 처리 자체를 멱등하게(CAS) 만든 이유는?
- 왜 100건을 한 트랜잭션이 아니라 건별 트랜잭션으로 처리하나?

---

## 2-6. 결제 대사 (+ PG 취소) (M)

**목표**
- 결과가 불확실했던 결제를 Toss에 조회해서 확정하고, 주문을 완료하거나 실패 처리한다.
- PG는 승인했는데 우리가 주문을 완료할 수 없으면 승인을 취소한다(보상).
- 이 단계에서 PG 취소(`PaymentApi.cancel`)를 처음 만든다(2-8 품목 환불이 재사용).

**왜 지금**: 2-4에서 생긴 "결제 확인 중" 주문을 끝내는 단계다. 이것으로 "PG 승인은 정확히 한 번 반영되고, 반영 못 한 승인은 취소된다"(INV-02)가 완성된다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 대사(reconciliation) | 우리 기록과 외부(PG) 기록을 맞춰보고 차이를 해결하는 작업 |
| 주문이 주도하는 대사 | 주문 모듈이 결제 모듈에게 "이 주문 결제 어떻게 됐어?"라고 묻는다. 이벤트가 필요 없다 (ADR-004) |
| PG 멱등 키 재사용 | 결과가 불확실했던 취소는 **같은 Idempotency-Key로 다시 호출**한다. Toss가 처음 결과를 돌려주므로 두 번 취소되지 않는다. Toss 멱등키는 처음 요청한 날부터 **15일** 유효하다(2-3, 2026-10-09 확인) — 복구 잡(2-8b)은 1분마다 돌며 5분 넘게 멈춘 환불을 다시 처리하므로 이 안에 끝난다 |

**정책 (이 단계에서 정함)**
- 재시도 간격: 잡이 1분마다 돌므로 **조회에 실패하면 다음 실행(1분 뒤)에 다시** 한다. `reconcile_attempts`만 늘린다(Part 7 알림의 근거).
- 대사 대상: PAYMENT_IN_PROGRESS로 30초 넘게 머문 주문.
- 보상 취소: PG 승인 시각으로부터 5분이 지났는데도 주문 완료에 실패하면 전액 취소하고 주문을 PAYMENT_FAILED로.

### 할 일

**구현 순서**: ① 마이그레이션(1) → ② payment 구현물 표 1~8(2) → ③ `PaymentApi`: `find` → `queryPgOutcome` → `cancel` → ④ `TestFixtures` 헬퍼(완료 확인의 "테스트 준비") → `PaymentCancelTest` → ⑤ order(3): `findStuckIds` → `OrderReconcileService` → `OrderReconcileJob` → `OrderReconcileServiceTest`.

**1) 마이그레이션** — `db/migration/payment/V{...}__payment_create_payment_cancel.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_payment_cancel` |
| payment_id | uuid | X | `fk_payment_cancel_payment` |
| idempotency_key | varchar(100) | X | `uk_payment_cancel_idempotency_key` |
| amount | bigint | X | `> 0` |
| reason | varchar(200) | X | |
| status | varchar(20) | X | `ck_payment_cancel_status`: `IN ('REQUESTED','DONE','FAILED','UNKNOWN')` |
| failure_code | varchar(100) | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- `payment_cancel` 한 행은 **Toss 취소 호출 한 건의 기록**이다. `idempotency_key`가 같은 호출을 식별한다(품목 환불은 `refundId`(관리자 재시도는 `refundId-r{n}`), 보상 취소는 `"order-compensate-" + orderId`). `REQUESTED`는 호출 전·호출 중, `DONE`은 Toss가 취소를 승인, `FAILED`는 명확히 거절, `UNKNOWN`은 결과를 모름(같은 키로 다시 불러 확인한다).

**2) payment — 대사·취소**

**왜 payment가 하는가**: 주문은 PG를 직접 모른다. order는 `PaymentApi`로 "이 주문의 결제가 어떻게 됐는지"를 묻고, "이 주문의 결제를 취소해 달라"고 요청한다. Toss 호출과 `payment`·`payment_cancel` 행 갱신은 모두 payment 모듈 안에서 끝난다.

**payment 구현물**: 아래 `queryPgOutcome`·`cancel` 단계 설명에 흩어져 있는 리포지토리·엔티티 작업이다. 1~8을 만든 뒤에 `PaymentApi` 메서드를 구현한다. 이름·시그니처는 권장안이고 쓰다 바꿔도 된다. 구조는 `PaymentRepository`와 같다(인터페이스는 `payment.domain`, `@Query` 구현은 `payment.infrastructure`의 `Jpa...Repository`).

| # | 대상 | 시그니처 · 동작 | 쓰이는 곳 |
|---|---|---|---|
| 1 | `PaymentRepository.increaseReconcileAttempts` | `int (UUID id, Instant now)` — `@Modifying(clearAutomatically = true)` 쿼리 하나. `reconcile_attempts + 1`, `status = UNKNOWN`, `version + 1`, `updated_at = :now`. `where id = :id and status in (IN_PROGRESS, UNKNOWN)`. `where`가 두 상태만 통과시키므로 `IN_PROGRESS`는 `UNKNOWN`이 되고 `UNKNOWN`은 그대로다(`case when` 불필요). 기존 `markUnknown`은 `IN_PROGRESS`에서만 돌고 횟수를 올리지 않아 쓰지 않는다 | `queryPgOutcome` 4 |
| 2 | `PaymentCancel` 엔티티 + `PaymentCancelStatus`(`REQUESTED`·`DONE`·`FAILED`·`UNKNOWN`) | 컬럼은 위 마이그레이션 표. setter 없이 `@Version`, `BaseTimeEntity` 상속(`Payment`과 같게) | 아래 3~7 |
| 3 | `PaymentCancelRepository.insertIfAbsent` | `int (UUID id, UUID paymentId, String idempotencyKey, long amount, String reason, Instant now)` — native `@Modifying`. `INSERT ... 'REQUESTED' ... ON CONFLICT ON CONSTRAINT uk_payment_cancel_idempotency_key DO NOTHING`. NOT NULL 컬럼 `version`(0)·`created_at`·`updated_at`(`:now`)도 INSERT에 넣는다. 반환 0행 = 이미 있음 | `cancel` A-2 |
| 4 | `PaymentCancelRepository.findByIdempotencyKey` | `Optional<PaymentCancel> (String idempotencyKey)` — 파생 쿼리 | `cancel` A-3 |
| 5 | `PaymentCancelRepository.markDone` | `int (UUID id, Instant now)` — `status = DONE`, `version + 1`, `updated_at`. `where id = :id and status in (REQUESTED, UNKNOWN)`. **1행일 때만** payment의 `cancelled_amount`를 올린다 | `cancel` B `Approved` |
| 6 | `PaymentCancelRepository.markFailed` | `int (UUID id, String failureCode, Instant now)` — `status = FAILED`, `failure_code`. `where`는 5와 같다(이미 `DONE`인 행을 `FAILED`로 덮지 않게) | `cancel` B `Rejected` |
| 7 | `PaymentCancelRepository.markUnknown` | `int (UUID id, Instant now)` — `status = UNKNOWN`. `where`는 5와 같다 | `cancel` B `Unknown` |
| 8 | `Payment.applyCancel(Money amount)` | 엔티티 메서드다. 입력은 이번에 PG가 취소한 금액이고 반환은 없다. ① `cancelledAmount = cancelledAmount.plus(amount)` ② `status = status.transitTo(cancelledAmount.equals(this.amount) ? CANCELLED : PARTIAL_CANCELLED)`(`Money`는 record라 `equals`가 값 비교). `APPROVED`·`PARTIAL_CANCELLED`가 아니면 `transitTo`가 `INVALID_STATE_TRANSITION`을 던진다(`cancel` A-4가 먼저 걸러서 보통 닿지 않는다). 호출은 트랜잭션 B 안에서 `paymentRepository.findById(paymentId)`로 다시 읽은 영속 엔티티에 한다(A에서 읽은 `payment`는 트랜잭션 밖 객체라 쓰지 않는다). 커밋 때 변경 감지로 UPDATE되고 `@Version`이 낙관적 락을 건다. 같은 주문의 취소는 2-8이 주문 행 잠금으로 줄 세우고, 누적 취소액이 승인액을 넘는 것은 DB CHECK `ck_payment_cancelled`가 마지막으로 막는다 | `cancel` B `Approved` |

- 3~7은 새 인터페이스 `PaymentCancelRepository`(`payment.domain`) 하나에 두고 `@Query` 구현은 `payment.infrastructure`의 `JpaPaymentCancelRepository`에 둔다. `payment_cancel`이 payment 모듈 안의 별도 테이블이라서다. `PaymentApiImpl`에 주입하며 `PaymentApi`로는 노출하지 않는다(모듈 밖에서는 `cancel`만 부른다).

`PaymentApi` 추가 (`PaymentView`·`CancelOutcome`은 2-4의 `StartPayment`처럼 `payment.api`의 별도 파일)
```java
Optional<PaymentView> find(UUID orderId);
PgOutcome queryPgOutcome(UUID orderId);      // 트랜잭션 밖 전용
CancelOutcome cancel(UUID orderId, Money amount, String reason, String idempotencyKey);  // 트랜잭션 밖 전용

public record PaymentView(UUID paymentId, PaymentState state, long amount, Instant approvedAt, String failureCode) {}
public enum CancelOutcome { DONE, FAILED, UNKNOWN }
```
- 결제는 주문 전용이라(`payment.order_id` unique) 모든 메서드는 `orderId` 하나로 결제를 찾는다.

| 메서드 | 한 줄 설명 | 입력 → 반환 |
|---|---|---|
| `find(orderId)` | 주문의 결제 요약을 읽는다. 대사 잡이 `paymentId`(결과 반영·취소에 필요), `amount`(보상 취소 금액), `state`·`failureCode`·`approvedAt`(이미 확정된 결제를 조회 없이 처리하고 보상 시점을 판단)을 얻는 데 쓴다. 읽기 전용이라 트랜잭션 안팎 어디서든 된다 | 주문 ID → `Optional<PaymentView>`(결제가 없으면 empty) |
| `queryPgOutcome(orderId)` | Toss에 **조회**해서 이 주문의 결제가 지금 어떻게 됐는지 알아낸다. 결과를 `payment`에 반영하지는 않는다(반영은 2-4 `applyOutcome`이고 `reconcileBatch`가 따로 부른다). 조회도 불확실했을 때만 `reconcile_attempts`·`UNKNOWN`을 기록한다 | 주문 ID → `PgOutcome`(2-4와 같은 타입) |
| `cancel(orderId, amount, reason, idempotencyKey)` | 결제 금액 중 `amount`만큼 Toss에 취소를 요청하고, 결과를 `payment_cancel`·`payment`에 반영한다. 같은 키로 다시 불러도 한 번만 취소된다 | → `CancelOutcome`(`DONE`: 취소됨, `FAILED`: 명확히 거절됨, `UNKNOWN`: 모름) |

- `PaymentView`는 `Payment` 엔티티에서 `paymentId = getId()`, `state = PaymentState.valueOf(getStatus().name())`, `amount = Money.amount()`, `approvedAt = getApprovedAt()`, `failureCode = getFailureCode()`로 만든다.
- 구현체 `PaymentApiImpl`에는 2-4에서 이미 `TransactionTemplate`이 주입돼 있다. `queryPgOutcome`·`cancel`은 `@Transactional`이 아니며, 필요한 짧은 트랜잭션은 이 템플릿으로 연다(Toss 호출은 트랜잭션 밖. 공통 규칙 K).

`queryPgOutcome` 순서 → 반환 `PgOutcome(type, code, method, approvedAt)`. `IN_PROGRESS`·`UNKNOWN`인 결제에만 부른다(이미 확정된 결제는 호출자가 `PaymentView.state`로 걸러서 이 메서드를 부르지 않는다)
1. `payment = findByOrderId(orderId)` → 없으면 `IllegalStateException`(호출자가 방금 결제를 확인한 주문이라 없으면 버그. 2-4 `callConfirm`과 같은 이유)
2. `PgResult result = tossClient.queryByOrderId(payment.getId(), payment.getPgOrderId())` (트랜잭션 밖)를 `PgOutcome`으로 바꾼다 — 2-4 `callConfirm`과 같은 변환이다. 반환 형태는 2-4의 `PgOutcome` 주석과 같다(승인이 아니면 `method`·`approvedAt`은 null)
   - `Approved` → `PgOutcome(APPROVED, null, approved.method(), approved.approvedAt())`
   - `Rejected` → `PgOutcome(REJECTED, rejected.code(), null, null)`. 조회에서 Toss에 없는 주문(404 `NOT_FOUND_PAYMENT`)도 여기다(2-3)
   - `Unknown` → 3번
3. `Unknown`이면 `tx.executeWithoutResult(s -> paymentRepository.increaseReconcileAttempts(payment.getId(), clock.instant()))`(표 1번)를 한 뒤 `PgOutcome(UNKNOWN, unknown.reason(), null, null)`을 반환한다. 1번에서 읽은 `payment` 엔티티는 트랜잭션 밖 객체라 값을 바꾸지 않고 `payment.getId()`만 넘긴다.
   - 0행이면 1번에서 읽은 뒤 다른 흐름(2-4 승인 응답)이 `APPROVED`·`FAILED`로 확정한 것이다(표 1번의 `where`가 확정된 결제를 `UNKNOWN`으로 되돌리지 않는다). 예외 없이 `PgOutcome(UNKNOWN, ...)`을 그대로 반환하고, 다음 주기의 조회가 확정된 값을 돌려준다(`applyOutcome`의 0행 처리와 같은 이유).

`cancel` 순서 → 반환 `CancelOutcome`
1. **트랜잭션 A** (`tx.execute`, 커밋. Toss를 부르기 전에 "취소를 시도한다"는 기록을 먼저 남긴다)
   1. `payment = findByOrderId(orderId)` → 없으면 `IllegalStateException`
   2. `insertIfAbsent`(표 3)로 `REQUESTED` 행을 넣는다(`id`는 `Ids.newId()`, `amount`는 `Money.amount()`). 같은 `idempotencyKey`가 이미 있으면 0행이라 **새 행을 만들지 않고** 기존 행을 쓴다
   3. `findByIdempotencyKey`(표 4)로 행을 다시 읽어 status가 `DONE`이면 `return DONE`, `FAILED`면 `return FAILED` (이미 끝난 호출이라 Toss를 다시 부르지 않는다). `REQUESTED`·`UNKNOWN`이면 계속
   4. payment status가 `APPROVED`·`PARTIAL_CANCELLED`가 아니면(승인 전이거나 이미 전액 취소) `IllegalStateException`. 3번 뒤에 두는 이유: 전액 취소가 끝난 payment(`CANCELLED`)에 같은 키로 다시 부르면 3번에서 `DONE`을 돌려줘야 하는데, 상태를 먼저 검사하면 그 전에 예외가 난다. 이 예외로 트랜잭션이 롤백되면 2번의 INSERT도 함께 취소된다
   5. `amount.isGreaterThan(payment.amount.minus(payment.cancelledAmount))`면 `IllegalArgumentException`(호출 코드의 버그를 드러내는 방어. 같은 트랜잭션이라 2번의 INSERT도 함께 롤백된다)
2. **트랜잭션 밖**: `PgResult result = tossClient.cancel(payment.getId(), payment.getPaymentKey(), amount, reason, idempotencyKey)` — A에서 읽은 `payment`의 값을 쓴다(엔티티를 A 밖으로 꺼내도 단순 필드는 읽힌다)
3. **트랜잭션 B** (`tx.execute`): 결과별로 반영한다
   - `Approved`: `markDone`(표 5)이 **1행일 때만** `Payment`에 취소를 반영한다(표 8). 0행이면 다른 흐름이 이미 반영한 것이라 올리지 않는다(같은 키를 두 번 보내도 한 번만 증가하는 이유)
   - `Rejected`: `markFailed`(표 6)에 `rejected.code()`를 넘긴다
   - `Unknown`: `markUnknown`(표 7)
4. 결과(`CancelOutcome`)를 반환: `Approved` → `DONE`, `Rejected` → `FAILED`, `Unknown` → `UNKNOWN`
- 결과가 UNKNOWN이었던 취소를 같은 키로 다시 부르면 1에서 행이 UNKNOWN이므로 2로 가서 **같은 Idempotency-Key로** Toss를 다시 부른다.
- 같은 키는 같은 금액으로 부른다. `cancel`은 저장된 행의 금액과 `amount`가 같은지 확인하지 않는다(보상은 결제 전체 금액, 2-8 환불 복구는 환불 행의 금액을 매번 같게 넘긴다).
- 같은 키로 이미 `DONE`인 호출이 payment가 `CANCELLED`가 된 뒤에도 `DONE`을 돌려주는 경로는 2-8 복구 잡이 "PG 취소는 끝났는데 `markPgCancelled`가 실패한 환불"을 다시 처리할 때(그 환불이 주문의 마지막 금액이었을 때) 닿는다.

**3) order — 대사 잡**

| 대상 | 규격 |
|---|---|
| `OrderRepository` | `@Query("select o.id from Order o where o.status = :status and o.updatedAt < :before order by o.updatedAt") List<UUID> findStuckIds(OrderStatus status, Instant before, Limit limit)` — 시그니처는 `OrderRepository`(domain), `@Query`는 `JpaOrderRepository`(2-5와 같은 구조). 한 상태로 `before`보다 오래 머문 주문의 ID만 오래된 순으로 읽는다(`Limit`·반환 형태는 2-5 `findExpiredIds`와 같다). "머문 시간"은 `updated_at` 기준이다: 2-4 `startPayment`가 `PAYMENT_IN_PROGRESS`로 바꾸면서 `updated_at = now`를 적으므로 승인을 시작한 시각이 된다 |
| `OrderReconcileService` (order.application) | 클래스에 `@Transactional` 없음. 의존성 `OrderRepository`, `PaymentApi`, `OrderPaymentApplier`(2-4. 아래 `applier`), `TransactionTemplate tx`, `Clock`(`now`는 `clock.instant()`). `public int reconcileBatch()` |
| `OrderReconcileJob` (order.infrastructure) | `@Scheduled(fixedDelay = 60_000)` → `jobRunner.run("reconcile-orders", service::reconcileBatch)` (2-5 `OrderExpirationJob`과 같은 모양) |

**`OrderReconcileService.reconcileBatch`**: `PAYMENT_IN_PROGRESS`로 머문 주문을 최대 100건 골라 하나씩 결제 결과를 주문에 반영한다. payment가 이미 `APPROVED`·`FAILED`로 확정돼 있으면 조회 없이 주문에 반영하고, `CANCELLED`면 주문만 `PAYMENT_FAILED`로 닫고, `IN_PROGRESS`·`UNKNOWN`이면 Toss에 조회해 payment를 먼저 확정한 뒤 주문에 반영한다. **누가 부르나**: `OrderReconcileJob`(1분마다). **왜 필요한가**: 승인 요청의 응답이 끊겼거나(2-4에서 202), 승인은 됐는데 우리 완료 트랜잭션이 실패한 주문은 `PAYMENT_IN_PROGRESS`로 남는다. 이 잡이 그것을 `PAID`/`PAYMENT_FAILED`로 끝낸다. 결과 반영(재고·주문·장바구니)은 2-4 `OrderPaymentApplier`를 그대로 쓴다. 주문 처리는 `applyPaymentState`(결제 상태에 따라 주문만 처리), 취소된 결제는 `failOrder`다. payment 확정은 `PaymentApi.applyOutcome`이 하고, 주문 처리와 다른 트랜잭션이다. 반환: 이번에 `PAID` 또는 `PAYMENT_FAILED`로 끝낸 건수(`int`).

`reconcileBatch` 순서 — `List<UUID> ids = findStuckIds(PAYMENT_IN_PROGRESS, now - 30초, Limit.of(100))`의 각 주문마다 try/catch로 감싸서(한 주문의 예외가 나머지를 막지 않게. `log.error`로 남기고 다음 주문):
1. `Optional<PaymentView> view = paymentApi.find(orderId)` (없으면 로그 후 다음 주문. 승인 시작 때 payment가 같은 트랜잭션에서 만들어지므로 보통 있다)
2. `view.state()`로 처리를 고른다.
   - `APPROVED`·`FAILED`(이미 확정): Toss를 부르지 않는다. `state = view.state()`, `failureCode = view.failureCode()`, `approvedAt = view.approvedAt()`로 3번으로 간다.
   - `IN_PROGRESS`·`UNKNOWN`: `PgOutcome outcome = paymentApi.queryPgOutcome(orderId)` (트랜잭션 밖. Toss를 부른다) → `outcome.type()`이 `UNKNOWN`이면 다음 주문(조회도 실패. `reconcile_attempts`는 `queryPgOutcome`이 이미 올렸다). 아니면 `PaymentState state = tx.execute(s -> paymentApi.applyOutcome(view.paymentId(), outcome))` — payment만 확정하는 트랜잭션이고 주문은 건드리지 않는다. `failureCode = outcome.code()`, `approvedAt = outcome.approvedAt()`로 3번으로 간다.
   - `CANCELLED`: 보상 취소가 끝났는데 뒤따르는 `failOrder`가 실패해서 주문이 남은 경우다. 주문이 `PAYMENT_IN_PROGRESS`인데 payment가 취소되는 경우는 이것뿐이다(환불은 `PAID` 이후에만 있다). Toss를 부르지 않고 `tx.executeWithoutResult(s -> applier.failOrder(orderId, "결제 완료 처리 실패로 자동 취소"))` — 재고 반환 + 주문 `PAYMENT_FAILED`(처리 건수에 센다). 다음 주문으로 간다.
   - `PARTIAL_CANCELLED`: 보상이 전액 취소라 나올 수 없다. `log.error`로 남기고 다음 주문.
3. 주문 처리: `OrderStatus status = tx.execute(s -> applier.applyPaymentState(orderId, state, failureCode))`. `status`가 `PAID`나 `PAYMENT_FAILED`면 처리 건수에 센다. `applier`의 `@Transactional` 메서드는 `tx.execute`가 연 트랜잭션에 합류한다. 2번의 payment 확정과 이 트랜잭션은 따로 커밋되므로, 이 사이에 실패하면 payment는 확정이고 주문은 `PAYMENT_IN_PROGRESS`로 남는다. 다음 주기에 `APPROVED`·`FAILED` 처리로 이어진다.
4. 3이 예외로 실패했고(예: 재고 확정 실패) `state`가 `APPROVED`이며 `approvedAt`이 `now - 5분`보다 이전이면 **보상**한다(PG는 돈을 승인했는데 우리가 주문을 완료하지 못하는 상태가 5분 넘게 이어졌을 때 승인을 되돌린다). payment가 이미 `APPROVED`라서 `cancel`의 선행 조건(트랜잭션 A 1번)을 만족한다:
   1. `CancelOutcome cancelled = paymentApi.cancel(orderId, Money.of(view.amount()), "주문 완료 실패로 자동 취소", "order-compensate-" + orderId)` — 전액 취소. 키가 주문마다 고정이라 다음 주기에 다시 불러도 두 번 취소되지 않는다
   2. `DONE`이면 `tx.executeWithoutResult(s -> applier.failOrder(orderId, "결제 완료 처리 실패로 자동 취소"))` — 재고 반환 + 주문 `PAYMENT_FAILED`(카운트에 센다). `FAILED`·`UNKNOWN`이면 아무것도 하지 않고 다음 주기에 다시
5. 처리한(PAID 또는 PAYMENT_FAILED가 된) 건수 반환

### 완료 확인

| 테스트 클래스 | 케이스 (가짜 PG 서버, 대사 대상이 되도록 JDBC로 주문 `updated_at`을 과거로) |
|---|---|
| `payment/application/PaymentCancelTest` | [ ] 전액 취소 → DONE, payment CANCELLED, `cancelled_amount` = 금액 / [ ] 부분 취소 두 번 → PARTIAL_CANCELLED 후 CANCELLED / [ ] 같은 키로 부분 취소를 두 번 → 가짜 서버 취소 요청 1건, 두 번째는 DONE 반환, payment는 PARTIAL_CANCELLED이고 `cancelled_amount`는 한 번만 증가 / [ ] 취소 타임아웃 → UNKNOWN → 같은 키로 다시 → DONE, `cancelled_amount` 한 번만 증가 / [ ] 거절 → FAILED / [ ] 전액 취소 뒤 같은 키로 다시 → DONE, 가짜 서버 취소 요청 추가 0건 |
| `order/application/OrderReconcileServiceTest` | 아래 |

`OrderReconcileServiceTest`
- [ ] 승인 타임아웃(`delay`, 가짜 서버는 승인 기록) → 대사 → 주문 PAID, payment APPROVED, 주문한 수량만큼 장바구니에서 빠짐
- [ ] 승인 타임아웃(`drop`, 승인 기록 없음) → 대사 → 조회 404 → 주문 PAYMENT_FAILED, 재고 원복, 장바구니 그대로
- [ ] 이미 확정된 결제(승인): 승인 타임아웃 → JDBC로 payment를 `APPROVED`(`method`, `approved_at` 포함)로 바꿈 → 대사 → 주문 PAID, 가짜 서버 조회 요청 0건
- [ ] 이미 확정된 결제(거절): 승인 타임아웃 → JDBC로 payment를 `FAILED`(`failure_code` 포함)로 바꿈 → 대사 → 주문 PAYMENT_FAILED, 재고 원복, 가짜 서버 조회 요청 0건
- [ ] 취소된 결제: 승인 타임아웃 → JDBC로 payment를 `CANCELLED`(`cancelled_amount` = `amount`)로 바꿈 → 대사 → 주문 PAYMENT_FAILED, 재고 `available` 원복, 가짜 서버 조회 요청 0건
- [ ] 조회도 실패(`/v1/payments/orders` 경로에 `serverError()`) → 주문 그대로, `reconcile_attempts` 1 → 다시 실행 → `reconcile_attempts` 2
- [ ] **결과 반영 경쟁**: 같은 주문에 `closeByQuery`의 두 단계(`tx.execute(applyOutcome)` → `tx.execute(applyPaymentState)`)를 두 스레드에서 동시에 → 재고 `sold` 한 번만 증가, 재고 이력 COMMIT 1건, 주문 PAID, 장바구니에서 한 번만 빠짐 (진 쪽은 예외 없이 끝나거나 낙관적 락 예외나 `INVALID_STATE_TRANSITION`)
- [ ] **이미 확정된 결제(승인)의 보상**: JDBC로 payment를 `APPROVED`(`approved_at` = 10분 전)로 바꾸고 가게주문 하나를 `CANCELLED`로 바꿔 완료가 실패하게 함 → 대사 → 가짜 서버 취소 요청 1건, payment CANCELLED, 주문 PAYMENT_FAILED, 재고 `available` 원복, 가짜 서버 조회 요청 0건
- [ ] **조회로 승인을 확인한 뒤 완료 불가 → 보상**: 승인 타임아웃 → JDBC로 가게주문 하나를 `CANCELLED`로 바꿔 완료가 실패하게 하고 `pg.markApproved(..., approvedAt = 10분 전)` → 대사 → 가짜 서버 조회 요청 1건, 취소 요청 1건, payment CANCELLED, 주문 PAYMENT_FAILED, 재고 `available` 원복·예약 RELEASED, 장바구니 그대로

**테스트 준비** ([N-1](#n-1-단계별-testfixtures-추가))

**이 단계에서 추가하는 헬퍼** (`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `setOrderUpdatedAt(orderId, Instant)` | JDBC `UPDATE orders.orders SET updated_at = ? WHERE id = ?` | `OrderReconcileServiceTest` (대사 대상 = 30초 넘게 머문 주문) |
| `createPaidOrder(UUID buyerId, UUID... productIds)` → orderId | `createPendingOrder` + `confirmPayment`(승인). `CheckoutResult.orderId()`를 돌려준다 | `PaymentCancelTest`(승인된 결제가 필요), 2-7 이후 |

이미 있는 헬퍼(앞 단계·Part 1에서 만든 것, 그대로 쓴다): `signup`, `token`, `login`, `changeStatus`, `openShop`, `closeShop`, `registerProduct`, `hideProduct`, `discontinueProduct`, `registerProductImage`, `putToStorage`, `ConcurrencyRunner`, `address`, `addToCart`, `checkout`, `stockOf`, `StockSnapshot`, `createPendingOrder`, `confirmPayment`, `setOrderExpired`, `breakReservedStock`

- `PaymentCancelTest`: `createPaidOrder(buyerId, productIds...)`로 승인된 결제를 만들고 `paymentApi.cancel(orderId, amount, reason, key)`를 직접 부른다. 취소 타임아웃·거절은 `pg.on("/v1/payments/" + paymentKey, ...)`처럼 그 결제의 경로 접두어로 건다(`paymentKey`는 `confirmPayment`가 정한 값).
- 대사: `pg.on("/v1/payments/confirm", Behavior.delay(1000))` → `confirmPayment`(202, payment UNKNOWN) → `setOrderUpdatedAt(orderId, now - 1분)` → `reconcileBatch()`. 조회 실패는 `pg.on("/v1/payments/orders", Behavior.serverError())`.
- 보상: JDBC `UPDATE orders.shop_order SET status = 'CANCELLED' WHERE order_id = ?` + `pg.markApproved(pgOrderId, paymentKey, amount, 10분 전)`. 완료 때 `Order.markPaid`가 가게주문 전이(`CANCELLED → PAID`)를 거부해 `INVALID_STATE_TRANSITION`이 나고, 트랜잭션이 롤백돼 예약이 `HELD`로 돌아가 있으므로 보상의 `failOrder`는 성공한다(`markPaymentFailed`의 `cancelPendingChildren`은 `PENDING`인 가게주문만 바꾼다). `breakReservedStock`(`reserved`를 0으로)은 쓰지 않는다: `commitReservation`과 `releaseReservation`은 둘 다 `reserved >= quantity`를 조건으로 하므로 `failOrder`의 재고 반환도 실패해 주문이 `PAYMENT_FAILED`가 되지 못한다.
- 결과 반영 경쟁: `paymentApi.find(orderId)`로 `paymentId`를 얻고 `PgOutcome(APPROVED, null, "카드", 지금)`을 만든 뒤, `ConcurrencyRunner.run(2, ...)`의 각 스레드에서 `tx.execute(s -> paymentApi.applyOutcome(paymentId, outcome))`로 얻은 상태를 `tx.execute(s -> applier.applyPaymentState(orderId, state, null))`에 넘긴다(테스트 클래스에 `TransactionTemplate`·`PaymentApi`·`OrderPaymentApplier` 주입). `reconcileBatch`를 직접 동시에 부르지 않는다: 잡 락이 없고 보상 경로까지 얽힌다. 진 쪽은 `transit(HELD → COMMITTED)`가 0행이라 재고를 건드리지 않고 지나간다. 이어서 `ShopOrder.markPaid`가 이긴 쪽이 이미 커밋한 가게주문 `PAID`를 읽으면 `INVALID_STATE_TRANSITION`(`BusinessException`), 그 전에 읽었다면 `Order.version` 충돌(낙관적 락 예외)로 롤백된다. 타이밍에 따라 둘 중 하나라 `Result`의 예외는 둘 다 허용한다.

**리뷰 때 물어볼 것**
- 외부 결제가 타임아웃 나면 어떻게 처리하나? (면접 단골 — 이 단계의 테스트가 답)
- 타임아웃 즉시 PG에 취소를 보내지 않고 조회부터 하는 이유는?
- 대사가 영영 확정을 못 하면? (Part 7 운영 API·알림 예고)
- 보상 취소가 `DONE`이 됐는데 뒤따르는 `failOrder`가 실패하면 주문은 어떻게 끝나나?

---

## 2-7. 판매자 주문 관리: 발송 · 배송완료 (M)

**목표**: 판매자가 자기 가게의 주문을 상태별로 보고, 송장번호를 입력해 발송하고, 배송완료로 바꾼다.

**왜 지금**: 결제 다음 단계다. 원 프로젝트는 가게별로 주문을 볼 수 없었다(`must.md` #1·2, As-Is ORD-11).

**정책 (이 단계에서 정함)**
- 판매자 주문 목록·상세는 CLOSED 가게 소유자도 볼 수 있다(`ShopApi.verifyOwner`).
- 발송은 가게주문 단위로 한 번에 한다(품목별 부분 발송 없음). 이미 취소된 품목은 발송 대상에서 빠진다.

### 할 일

**구현 순서**: ① 마이그레이션(1) → ② `OrderErrorCode`, `OrderLine`·`ShopOrder`·`Order` 메서드(2) → ③ `OrderRepository` 조회 쿼리(3) → ④ Result record, `SellerOrderQueryService`, `ShipmentService`(4) → ⑤ 응답 record, 컨트롤러(5) → ⑥ `TestFixtures` 헬퍼(완료 확인의 "테스트 준비") → 테스트.

**1) 마이그레이션** — `db/migration/order/V{...}__order_add_shop_order_indexes.sql`
- `idx_shop_order_shop_id_status_created_at ON orders.shop_order (shop_id, status, created_at DESC, id DESC)`
- `idx_shop_order_shop_id_created_at ON orders.shop_order (shop_id, created_at DESC, id DESC)` (상태 필터 없을 때)

**2) order.domain**

판매자는 가게주문(`ShopOrder`)을 보지만, 가게주문과 품목은 `Order` 애그리거트의 일부라 상태를 바꾸는 입구는 `Order`다. 가게주문·품목은 지연 로딩이므로 아래 메서드들은 열려 있는 트랜잭션 안에서 불린다. 서비스가 품목 상태를 직접 바꾸지 않는다: `Order`가 `findShopOrder`로 가게주문을 찾아 맡기고, 가게주문이 자기 품목을 순회한다(2-2 `markPaid`와 같은 방식).

`OrderErrorCode`에 `SHOP_ORDER_NOT_FOUND`(404, "가게 주문을 찾을 수 없습니다.", 공통 규칙 O)를 더한다.

`OrderLine`·`ShopOrder` 메서드 이름은 권장안이다. 기존 `markPaid`·`markCancelled`처럼 상태만 바꾸는 `OrderLine` 메서드는 `mark` + 바뀔 상태의 과거분사로, 인자를 받고 품목까지 다루는 `ShopOrder` 메서드는 `Order`와 같은 동사(`ship`·`deliver`)로 지었다. 테스트가 직접 부르는 것은 `Order.ship`·`Order.deliver`뿐이라 두 클래스의 이름은 바꿔도 된다.

| 대상 | 시그니처 | 규격 | 쓰는 곳 |
|---|---|---|---|
| `OrderLine` | `void markShipped()` | `status = status.transitTo(SHIPPED)`(`PAID → SHIPPED`) | `ShopOrder.ship` |
| `OrderLine` | `void markDelivered()` | `status = status.transitTo(DELIVERED)`(`SHIPPED → DELIVERED`. 2-9의 반품 거절 `RETURN_REQUESTED → DELIVERED`도 이 메서드) | `ShopOrder.deliver`, 2-9 |
| `ShopOrder` | `void ship(String carrier, String trackingNumber, Instant now)` | 가게주문 `PAID → SHIPPED`(`ShopOrderStatus.transitTo`라 `PAID`가 아니면 `INVALID_STATE_TRANSITION` 409), `carrier`·`trackingNumber`·`shippedAt` 기록, **CANCELLED가 아닌** 품목마다 `markShipped()` | `Order.ship` |
| `ShopOrder` | `void deliver(Instant now)` | 가게주문 `SHIPPED → DELIVERED`(아니면 `INVALID_STATE_TRANSITION`), `deliveredAt` 기록, `SHIPPED`인 품목마다 `markDelivered()` | `Order.deliver` |
| `Order` | `ShopOrder findShopOrder(UUID shopOrderId)` | 이 주문의 가게주문 중 ID가 같은 것을 찾는다(`shopOrders`를 순회). 없으면 `BusinessException(SHOP_ORDER_NOT_FOUND)` | 서비스(`shopId` 확인, 응답 만들기), 2-9 |
| `Order` | `void ship(UUID shopOrderId, String carrier, String trackingNumber, Instant now)` | `findShopOrder(shopOrderId).ship(carrier, trackingNumber, now)` | `ShipmentService.ship` |
| `Order` | `void deliver(UUID shopOrderId, Instant now)` | `findShopOrder(shopOrderId).deliver(now)` | `ShipmentService.deliver` |

- `ship`에서 **CANCELLED 품목을 거르는 이유**: 발송 전 품목 취소(2-8)로 이미 `CANCELLED`가 된 품목에 `transitTo(SHIPPED)`를 하면 `CANCELLED`에는 허용 전이가 없어 `INVALID_STATE_TRANSITION`이 난다. `deliver`는 `SHIPPED` 품목만 바꾸므로 취소 품목은 자연히 빠진다.

**3) order.domain — 조회 쿼리** (모두 `OrderRepository`에 더한다. 시그니처는 domain, `@Query`는 `JpaOrderRepository`. `ShopOrder`가 `Order` 애그리거트의 일부라 저장소를 따로 두지 않는다)

| 대상 | 시그니처 · 쿼리 | 쓰는 곳 | 동작 |
|---|---|---|---|
| `ShopOrderListRow` (record, `order.domain`) | `(UUID shopOrderId, UUID orderId, ShopOrderStatus status, ShippingAddress shippingAddress, String carrier, String trackingNumber, Instant shippedAt, Instant deliveredAt, Instant createdAt)` | 아래 목록 쿼리 | 엔티티가 아니라 조회용 record로 바로 받는다(`select new ...`). `SellerShopOrderResult`와 칸이 같도록 송장·발송·배송완료 칸을 둔다. `o.shippingAddress`(jsonb 매핑)는 이 생성자 식에 그대로 넣을 수 있다. |
| `findByShopOrderId` | `Optional<Order> findByShopOrderId(UUID shopOrderId)`: `select o from Order o join o.shopOrders so where so.id = :shopOrderId` | `getShopOrder`·`ship`·`deliver`의 앞단, 2-10 자동 확정 | 가게주문 ID로 그 가게주문이 속한 **주문(루트)**을 읽는다. 조인은 찾기 위한 조건이라 `order.getShopOrders()`에는 그 주문의 가게주문이 모두 들어온다 |
| 목록 첫 페이지 | `List<ShopOrderListRow> findShopOrderFirstPage(UUID shopId, ShopOrderStatus status, Limit limit)`(권장 이름): `select new ...ShopOrderListRow(so.id, so.orderId, so.status, o.shippingAddress, so.carrier, so.trackingNumber, so.shippedAt, so.deliveredAt, so.createdAt) from ShopOrder so join Order o on o.id = so.orderId where so.shopId = :shopId and (:status is null or so.status = :status) order by so.createdAt desc, so.id desc` | `getShopOrders` | 가게의 가게주문을 상태 필터(`status`가 null이면 전체)로 최신순으로 읽는다(위 마이그레이션의 인덱스 순서). 배송지는 주문(`Order`)에 있어 조인한다 |
| 목록 다음 페이지 | `findShopOrderNextPage(UUID shopId, ShopOrderStatus status, Instant cursorCreatedAt, UUID cursorId, Limit limit)`(권장 이름). 첫 페이지 쿼리의 `where`에 `and (so.createdAt < :cursorCreatedAt or (so.createdAt = :cursorCreatedAt and so.id < :cursorId))`를 더한다(1-7 keyset 방식) | `getShopOrders` | 커서 이후의 행을 같은 정렬로 읽는다 |
| 품목 | `List<OrderLine> findLinesByShopOrderIdIn(Collection<UUID> shopOrderIds)`: `shop_order_id IN (...)` | `getShopOrders` | 목록의 품목을 가게주문마다 따로 읽으면 쿼리가 행 수만큼 늘어난다(테스트: 3개와 10개일 때 SQL 수가 같아야 한다). 페이지의 가게주문 ID를 모아 **한 번에** 읽어 가게주문별로 묶는다. `OrderLine.getShopOrderId()`가 조회 전용 필드로 이미 있다 |

**4) order.application**

| 대상 | 규격 |
|---|---|
| `SellerLineResult` (record) | `(UUID orderLineId, UUID productId, String productName, long unitPrice, int quantity, long lineAmount, long refundedAmount, OrderLineStatus status)` |
| `SellerShopOrderResult` (record) | `(UUID shopOrderId, UUID orderId, ShopOrderStatus status, ShippingAddressResult shippingAddress, String carrier, String trackingNumber, Instant shippedAt, Instant deliveredAt, long amount, List<SellerLineResult> lines, Instant createdAt)` — `amount`는 취소되지 않은 품목 `lineAmount`의 합 |
| `SellerOrderQueryService` (`@Transactional(readOnly = true)`. 의존성 `ShopApi`, `OrderRepository`) | `CursorPage<SellerShopOrderResult> getShopOrders(UUID memberId, UUID shopId, ShopOrderStatus status, String cursor, int size)` — 가게의 가게주문 목록 / `SellerShopOrderResult getShopOrder(UUID memberId, UUID shopId, UUID shopOrderId)` — 상세 한 건 |
| `ShipmentService` (`@Transactional`. 의존성 `ShopApi`, `OrderRepository`, `Clock`) | `SellerShopOrderResult ship(UUID memberId, UUID shopId, UUID shopOrderId, String carrier, String trackingNumber)` — 발송 / `SellerShopOrderResult deliver(UUID memberId, UUID shopId, UUID shopOrderId)` — 배송완료. 둘 다 처리한 뒤의 가게주문을 돌려준다 |

**Result 만드는 법** — `OrderDetailResult.from(order)`처럼 record의 `static from`으로 만든다(이름은 권장안). 같은 모양을 두 곳에서 만든다.

| 메서드 | 쓰는 곳 | 꺼내는 곳 |
|---|---|---|
| `SellerShopOrderResult.from(Order order, ShopOrder shopOrder)` | `getShopOrder`, `ship`, `deliver` | 가게주문 칸(`shopOrderId`, `orderId`, `status`, `carrier`, `trackingNumber`, `shippedAt`, `deliveredAt`, `createdAt`)은 `shopOrder`의 `getId()`, `getOrderId()`, `getStatus()`, `getCarrier()`, `getTrackingNumber()`, `getShippedAt()`, `getDeliveredAt()`, `getCreatedAt()`. `lines`는 `shopOrder.getLines()`를 `SellerLineResult.from`으로 바꾼 것. 배송지는 `ShippingAddressResult.from(order.getShippingAddress())` |
| `SellerShopOrderResult.from(ShopOrderListRow row, List<OrderLine> lines)` | `getShopOrders` | 가게주문 칸은 `row`의 같은 이름 칸. `lines`는 `findLinesByShopOrderIdIn` 결과를 가게주문별로 묶은 것 중 이 행의 것. 배송지는 `ShippingAddressResult.from(row.shippingAddress())` |
| `SellerLineResult.from(OrderLine line)` | 위 두 메서드 | `getId()`가 `orderLineId`, `getProductId()`, `getProductName()`, `getUnitPrice().amount()`, `getQuantity()`, `getLineAmount().amount()`, `getRefundedAmount().amount()`, `getStatus()` |

- 금액은 `Money.amount()`로 `long`을 만든다(2-2 `OrderDetailResult`와 같다). 결과 타입에 도메인 `ShippingAddress`를 두지 않는 이유는 web이 domain을 import하지 않게 하려는 것이다.
- `amount`는 두 메서드 모두 `lines` 중 `CANCELLED`가 아닌 품목의 `lineAmount` 합이다.

공통 앞단 (`getShopOrder`·`ship`·`deliver`. `getShopOrders`는 1만 한다) — 요청자가 이 가게의 주인이고 이 가게주문이 이 가게 것임을 확인한다
1. `shopApi.verifyOwner(shopId, memberId)` — 가게가 없으면 404 `SHOP_NOT_FOUND`, 주인이 아니면 403 `FORBIDDEN`(CLOSED 가게도 통과. 정책)
2. `Order order = findByShopOrderId(shopOrderId)` — 없으면 `SHOP_ORDER_NOT_FOUND`
3. `ShopOrder shopOrder = order.findShopOrder(shopOrderId)`의 `shopId`가 요청 `shopId`와 다르면 `SHOP_ORDER_NOT_FOUND` (남의 가게 주문은 없는 것처럼. 403이 아니라 404인 이유: 다른 가게의 주문 ID가 존재하는지 드러내지 않으려고)

- `ship`: 앞단 → `order.ship(shopOrderId, carrier, trackingNumber, clock.instant())` → 이어서 `shopOrder`(같은 인스턴스, 상태가 바뀌어 있다)로 `SellerShopOrderResult`를 만들어 반환. 저장은 커밋 때 더티 체킹이라 `save`를 부르지 않는다
- `deliver`: 앞단 → `order.deliver(shopOrderId, clock.instant())` → 같은 방식으로 `SellerShopOrderResult`
- `getShopOrders`: ① `shopApi.verifyOwner` ② `cursor`가 없으면 첫 페이지, 있으면 `Cursor.decode(cursor)`의 `createdAt`·`id`로 다음 페이지를 `Limit.of(size + 1)`로 읽는다 ③ 결과가 `size`를 넘으면 앞의 `size`건만 돌려주고 `nextCursor = new Cursor(돌려주는 마지막 행의 createdAt, shopOrderId).encode()`(2-2 `getMyOrders`와 같은 방식) ④ 돌려줄 행의 `shopOrderId`를 모아 `findLinesByShopOrderIdIn`을 **한 번** 부르고 가게주문별로 묶어 `SellerShopOrderResult`로 만든다
- 2-8이 `ship`의 앞단 뒤, `order.ship` 앞에 "진행 중 환불이 있으면 `REFUND_IN_PROGRESS`" 검사를 더한다(2-7에서는 비워 둔다).

**5) order.web** — `SellerOrderController` (`/api/shops/{shopId}/orders`). `SellerShopOrderResponse`·`SellerLineResponse`는 각각 `SellerShopOrderResult`·`SellerLineResult`와 같은 필드를 가진 응답 record다(`OrderDetailResponse`가 `OrderLineDetailResponse`를 품는 것과 같은 구조. 배송지는 2-2의 `ShippingAddressResponse`). 컨트롤러는 `@CurrentMember UUID memberId`와 `@PathVariable`을 받아 서비스를 부르고 Result를 Response로 바꿔 `ResponseEntity.ok`로 돌려줄 뿐이다(`OrderController`와 같은 방식). 목록의 `size`는 `getMyOrders`와 같이 기본 20, 1~50이다(`@RequestParam(defaultValue = "20") @Min(1) @Max(50)`, 검증 메시지는 `OrderController`와 같다)

| API | 요청 | 응답 |
|---|---|---|
| `GET /api/shops/{shopId}/orders?status=&cursor=&size=` | | 200 `CursorPage<SellerShopOrderResponse>` |
| `GET /api/shops/{shopId}/orders/{shopOrderId}` | | 200 `SellerShopOrderResponse` |
| `POST /api/shops/{shopId}/orders/{shopOrderId}/ship` | `ShipRequest(@NotBlank @Size(max=30) String carrier, @NotBlank @Pattern(regexp="^[0-9-]{8,30}$") String trackingNumber)` | 200 `SellerShopOrderResponse` |
| `POST /api/shops/{shopId}/orders/{shopOrderId}/deliver` | | 200 `SellerShopOrderResponse` |

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/domain/OrderShipmentTest` | [ ] `ship`: 가게주문 SHIPPED, 송장 기록, PAID 품목 SHIPPED, CANCELLED 품목은 그대로 / [ ] PAID가 아닌 가게주문 `ship` → `INVALID_STATE_TRANSITION` / [ ] `deliver` 전후 상태 / [ ] SHIPPED 아닌데 `deliver` → 예외 |
| `order/web/SellerOrderControllerTest` | [ ] 가게 A·B 상품을 한 주문으로 결제 → A 판매자 목록에 A 가게주문만 / [ ] 상태 필터 / [ ] 발송 → 배송완료 흐름 / [ ] **다른 가게 판매자가 내 가게 shopId로 호출 → 403**, 자기 가게 shopId + 남의 가게주문 ID → 404 `SHOP_ORDER_NOT_FOUND` / [ ] 결제 대기(PENDING) 가게주문 발송 → 409 / [ ] 목록 조회 SQL 수가 가게주문 3개일 때와 10개일 때 같다 |

**테스트 준비** ([N-1](#n-1-단계별-testfixtures-추가))

**이 단계에서 추가하는 헬퍼** (`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `shopOrderIdOf(orderId, shopId)` → shopOrderId | JDBC `SELECT id FROM orders.shop_order WHERE order_id = ? AND shop_id = ?` | `SellerOrderControllerTest`, 2-9·2-10 |
| `shipShopOrder(sellerId, shopId, shopOrderId)`, `deliverShopOrder(sellerId, shopId, shopOrderId)` | `ShipmentService.ship(..., "CJ대한통운", "1234567890")`, `deliver` | 2-8 "발송된 품목 취소", 2-9·2-10 |

이미 있는 헬퍼(앞 단계·Part 1에서 만든 것, 그대로 쓴다): `signup`, `token`, `login`, `changeStatus`, `openShop`, `closeShop`, `registerProduct`, `hideProduct`, `discontinueProduct`, `registerProductImage`, `putToStorage`, `ConcurrencyRunner`, `address`, `addToCart`, `checkout`, `stockOf`, `StockSnapshot`, `createPendingOrder`, `confirmPayment`, `setOrderExpired`, `breakReservedStock`, `setOrderUpdatedAt`, `createPaidOrder`

- 가게 A·B: 판매자 2명·가게 2곳을 만들고 `createPaidOrder(buyerId, 상품A, 상품B)` → `shopOrderIdOf(orderId, shopId)`.
- PENDING 가게주문: `createPendingOrder` 후 `shopOrderIdOf`로 발송을 시도한다.
- 다른 가게 판매자: 두 번째 판매자를 `login`해서 첫 가게의 `shopId`로 호출한다.
- 쿼리 수: 같은 가게 상품으로 `createPaidOrder`를 3번, 10번 반복해 가게주문 3개·10개를 만든다.

**추가 항목**
- 판매자 주문 목록의 기간 필터(`from`~`to`, 업무 날짜 기준)

**리뷰 때 물어볼 것**
- 가게주문 상태와 품목 상태가 어긋나지 않게 누가 책임지나?
- 판매자 목록에서 주문(orders)과 가게주문을 조인해도 되는 근거는? (같은 schema, 같은 모듈)

---

## 2-8. 품목 취소와 환불 (L)
> PR을 둘로 나눈다: **2-8a** 취소·환불, **2-8b** 복구 잡

**목표**
- 발송 전 품목을 하나만 취소하면 **그 품목 금액만** PG 부분취소로 환불된다. 재고도 돌아온다.
- 중간에 실패해도 복구 잡이 이어서 끝낸다.

**왜 지금**: 원 프로젝트의 가장 심각한 결함 중 하나(품목 환불인데 주문 전액 환불, As-Is ORD-04)를 해결한다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 환불 오케스트레이션 | Refund가 단계별 상태(APPROVED → PG_CANCELLED → COMPLETED)를 가지고 진행. 각 단계는 멱등 |
| 부분취소 (POL-08) | 환불액 = 품목 금액 - 이미 환불된 금액. 전액 PG 부분취소. 주문별 누적 취소액 ≤ 승인액은 `payment.cancelled_amount` CHECK가 최종 방어선 |
| 주문 단위 잠금 | 같은 주문의 품목 두 개를 동시에 처리하면 가게주문 상태 재계산이 겹친다 → 주문 행을 `FOR UPDATE`로 잠가 줄을 세운다 |
| 부분 unique 인덱스 | 품목당 "진행 중 환불"은 하나만: `WHERE status IN ('REQUESTED','APPROVED','PG_CANCELLED')` |
| 복구 잡 | 중간 상태로 멈춘 환불을 찾아 남은 단계를 다시 실행 |

**정책 (이 단계에서 정함)**
- 취소는 품목 단위, 그 품목 금액 전체(`lineAmount - refundedAmount`)를 환불한다(부분 수량 취소 없음).
- 진행 중 환불이 있는 가게주문은 발송할 수 없다(409 `REFUND_IN_PROGRESS`).
- PG 취소가 명확히 실패하면 환불은 FAILED로 끝나고 502를 준다. 운영자 처리 대상이다(Part 7 운영 API).

### 할 일 (2-8a)

**구현 순서**: ① 마이그레이션(1) → ② `OrderErrorCode`, `RefundStatus`·`RefundReasonType`·`Refund`, `RefundRepository`, `OrderRepository` 잠금 조회, `OrderLine`·`ShopOrder`·`Order` 메서드(2) → ③ `RefundCompleter` → `RefundProcessor` → `CancelOrderLineService`, 기존 `ShipmentService`·`OrderQueryService` 수정(4) → ④ 컨트롤러·응답 record(5) → ⑤ `TestFixtures` 헬퍼(완료 확인의 "테스트 준비") → 테스트. PG 취소(3)는 2-6의 `PaymentApi.cancel`을 그대로 쓰므로 새로 만들 것이 없다.

**1) 마이그레이션** — `db/migration/order/V{...}__order_create_refund.sql`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_refund` |
| order_id | uuid | X | `fk_refund_orders` |
| shop_order_id | uuid | X | `fk_refund_shop_order` |
| order_line_id | uuid | X | `fk_refund_order_line` |
| member_id | uuid | X | |
| reason_type | varchar(20) | X | `ck_refund_reason_type`: `IN ('BUYER_CANCEL','RETURN','SYSTEM')` |
| reason | varchar(200) | O | |
| amount | bigint | X | `> 0` — PG 부분취소 금액 |
| status | varchar(20) | X | `ck_refund_status`: `IN ('REQUESTED','APPROVED','PG_CANCELLED','COMPLETED','REJECTED','FAILED')` |
| reject_reason | varchar(200) | O | |
| pg_attempts | int | X | DEFAULT 0 — PG 취소를 다시 시도한 횟수(Part 7 관리자 재시도마다 +1). PG 취소 멱등 키를 시도마다 달리하는 데 쓴다 |
| completed_at | timestamptz | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 부분 unique `uk_refund_order_line_active ON orders.refund (order_line_id) WHERE status IN ('REQUESTED','APPROVED','PG_CANCELLED')`
- 인덱스 `idx_refund_order_id`, `idx_refund_shop_order_id_status`, `idx_refund_status_updated_at`

**2) order.domain**

**환불(`Refund`)이 무엇인가**: 품목 하나를 환불하는 **진행 기록**이다. 환불은 "PG 취소 → 재고 복구 → 품목·가게주문 상태 반영"이라는 여러 단계를 거치고 중간에 멈출 수 있어서, 어디까지 했는지를 `status`로 남겨 두고 다음 호출·복구 잡이 이어서 한다. 각 단계는 멱등이라 같은 환불을 몇 번 다시 처리해도 결과가 같다.

| 클래스 | 규격 |
|---|---|
| `OrderErrorCode` | `ORDER_LINE_NOT_FOUND`(404, "주문 품목을 찾을 수 없습니다."), `REFUND_IN_PROGRESS`(409, "진행 중인 환불이 있습니다.") — 공통 규칙 O |
| `RefundStatus` | `REQUESTED → {APPROVED, REJECTED}`, `APPROVED → {PG_CANCELLED, FAILED}`, `PG_CANCELLED → {COMPLETED}`. 다른 enum처럼 `transitTo`로 검사한다 |
| `RefundReasonType` | `BUYER_CANCEL`(구매자 품목 취소), `RETURN`(반품), `SYSTEM`(시스템이 만드는 환불. 이 단계에서 이 값을 쓰는 곳은 문서에 없다) |
| `Refund` | 별도 애그리거트. `@Entity @Table(name = "refund", schema = "orders")`, `extends BaseTimeEntity`, 필드는 컬럼과 1:1(`Money amount`, `@Version Long version`). 메서드는 아래 표 |
| `RefundRepository` | 아래 표 |

각 상태의 뜻: `REQUESTED` 반품 요청됨(판매자 승인 대기, 2-9) / `APPROVED` 환불 진행 승인됨(PG 취소 전이거나 결과 불확실) / `PG_CANCELLED` PG 취소 끝, 재고·품목 반영 전 / `COMPLETED` 모두 끝 / `REJECTED` 판매자가 반품 거절 / `FAILED` PG가 취소를 명확히 거절(운영자 처리).

`Refund` 메서드 — 상태 전이는 `status.transitTo(...)`로 하고, 어긋나면 `INVALID_STATE_TRANSITION`이다

| 메서드 | 한 줄 설명 |
|---|---|
| `static Refund approveBuyerCancel(UUID orderId, UUID shopOrderId, UUID orderLineId, UUID memberId, Money amount, String reason)` | 구매자 품목 취소용 환불을 **만든다**. 구매자 취소는 승인이 필요 없어 `REQUESTED`를 거치지 않고 `APPROVED`로 시작한다. `id = Ids.newId()`, `reasonType = BUYER_CANCEL` |
| `void markPgCancelled()` | PG 취소가 끝났음을 기록한다 `APPROVED → PG_CANCELLED` |
| `void complete(Instant now)` | 환불 완료 `→ COMPLETED`, `completedAt = now` |
| `void fail()` | PG가 취소를 명확히 거절 `APPROVED → FAILED` |
| `boolean needsPgCancel()` | PG 취소를 아직 안 했나 → `status == APPROVED` |
| `String pgIdempotencyKey()` | PG 취소 호출에 쓸 멱등 키(`PaymentApi.cancel`의 `idempotencyKey`). `pgAttempts == 0`이면 `id.toString()`, 재시도 뒤(관리자 재시도, 7-3)에는 `id + "-r" + pgAttempts`. 같은 시도를 다시 처리하면 같은 키라 한 번만 취소되고, `FAILED` 뒤의 재시도는 새 키라 Toss까지 간다(같은 키의 `FAILED` 행이 있으면 `cancel`이 PG를 부르지 않고 `FAILED`를 재생하기 때문) |
| `boolean readyToComplete()` | 재고·품목 반영만 남았나 → `status == PG_CANCELLED` |

`RefundRepository` (domain 인터페이스, 구현은 `JpaRefundRepository`)

| 메서드 | 누가 쓰나 | 입력 → 반환 |
|---|---|---|
| `Refund save(Refund)`, `Optional<Refund> findById(UUID)` | 취소·완료·반품 | `JpaRepository`가 같은 시그니처로 제공 |
| `boolean existsByShopOrderIdAndStatusIn(UUID shopOrderId, Collection<RefundStatus> statuses)` | `ShipmentService.ship`의 발송 가드 | 가게주문 ID·상태들 → 진행 중 환불이 있으면 `true` (파생 쿼리) |
| `List<Refund> findAllByOrderId(UUID orderId)` | `OrderQueryService.getMyOrder`의 `refunds` | 주문 ID → 그 주문의 환불 전부 (파생 쿼리) |
| `List<UUID> findStuckIds(RefundStatus status, Instant before, Limit limit)` | 2-8b 복구 잡 | 상태·기준 시각 → `updated_at < before`인 환불 ID(오래된 순). `@Query("select r.id from Refund r where r.status = :status and r.updatedAt < :before order by r.updatedAt")`(2-6 `OrderRepository.findStuckIds`와 같은 모양) |

`OrderRepository`에 추가 — 시그니처는 `OrderRepository`(domain), `@Lock`은 `JpaOrderRepository`에서 붙여 선언한다

| 메서드 | 쓰는 곳 | 하는 일 |
|---|---|---|
| `@Lock(LockModeType.PESSIMISTIC_WRITE) Optional<Order> findByIdAndMemberIdForUpdate(UUID id, UUID memberId)` | 구매자 품목 취소(`CancelOrderLineService`), 반품 요청(2-9) | 주문 행을 읽으면서 **잠근다**(`SELECT ... FOR UPDATE`). 잠그는 트랜잭션이 끝날 때까지 같은 주문을 잠그려는 다른 트랜잭션은 기다린다. 소유자 확인이 필요한 요청용 |
| `@Lock(LockModeType.PESSIMISTIC_WRITE) Optional<Order> findByIdForUpdate(UUID id)` | `RefundCompleter`, 반품 승인(2-9) | 같은 잠금. 내부 처리용(소유자 확인 없음) |

`OrderLine`·`ShopOrder`에 추가 — 환불 결과를 반영하는 `Order.applyRefund`가 부른다. 이름은 권장안이고 쓰다 바꿔도 된다(둘 다 setter가 없어 `Order`가 필드를 직접 못 바꾼다)

| 대상 | 시그니처 | 규격 |
|---|---|---|
| `OrderLine` | `void addRefundedAmount(Money amount)` | `refundedAmount = refundedAmount.plus(amount)`. DB는 `refunded_amount <= line_amount` CHECK가 최종 방어선 |
| `OrderLine` | `void markReturned()` | `status = status.transitTo(RETURNED)` (`RETURN_REQUESTED → RETURNED`) |
| `ShopOrder` | `void markCancelledWithoutLines()` | `status = status.transitTo(CANCELLED)`만 한다. 기존 `markCancelled()`(2-2)는 품목도 `markCancelled()`로 바꾸는데, 이미 `CANCELLED`인 품목은 나가는 전이가 없어 `INVALID_STATE_TRANSITION`이 나므로 여기서 쓰지 않는다 |
| `ShopOrder` | `void markCompleted(Instant now)` | `status = status.transitTo(COMPLETED)` (`DELIVERED → COMPLETED`), `completedAt = now` |

`Order`에 추가 — 환불·취소·반품·구매확정이 모두 `Order`(루트)를 통해 품목·가게주문 상태를 바꾼다

| 메서드 | 한 줄 설명 |
|---|---|
| `OrderLine findLine(UUID lineId)` | 이 주문의 품목 중 ID가 같은 것. 없으면 `ORDER_LINE_NOT_FOUND`(남의 주문의 품목 ID도 이 주문에는 없다) |
| `ShopOrder shopOrderOf(UUID lineId)` | 그 품목이 속한 가게주문 |
| `void verifyCancellable(UUID lineId)` | 취소할 수 있는 상태인지 검사한다. 주문 `PAID`, 품목 `PAID`, 가게주문 `PAID`(미발송) 중 하나라도 아니면 `INVALID_STATE_TRANSITION` |
| `void applyRefund(UUID lineId, Money amount, RefundReasonType type, Instant now)` | 환불 결과를 주문에 반영한다. 순서는 아래 |

`applyRefund` 순서
1. `OrderLine line = findLine(lineId)`, `ShopOrder shopOrder = shopOrderOf(lineId)`
2. `line.addRefundedAmount(amount)`
3. 품목 상태: `BUYER_CANCEL`이면 `line.markCancelled()`(`PAID → CANCELLED`), `RETURN`이면 `line.markReturned()`(`RETURN_REQUESTED → RETURNED`). `SYSTEM`의 처리는 정해져 있지 않다(확인 필요)
4. `recalculateShopOrder(shopOrder, now)`

`private void recalculateShopOrder(ShopOrder shopOrder, Instant now)` — 품목 상태가 바뀐 뒤 가게주문의 상태를 맞춘다. 위에서 아래로 첫 번째만 적용한다
1. 모든 품목이 `CANCELLED`면 `shopOrder.markCancelledWithoutLines()` (`PAID → CANCELLED`)
2. 가게주문이 `DELIVERED`이고 모든 품목이 `CONFIRMED`·`RETURNED`·`CANCELLED` 중 하나면 `shopOrder.markCompleted(now)`
3. 그 밖에는 바꾸지 않는다

**3) payment — 주문 결제 부분취소**: 새로 만들 것이 없다. 2-6의 `PaymentApi.cancel(orderId, amount, reason, idempotencyKey)`를 `idempotencyKey = refund.pgIdempotencyKey()`로 부른다.

**4) order.application**

| 클래스 | 규격 |
|---|---|
| `RefundResult` | `record (UUID refundId, RefundStatus status, long amount)` — `amount`는 `Refund.getAmount().amount()` |
| `RefundCompleter` (`@Service`. 의존성 `RefundRepository`, `OrderRepository`, `ProductApi`, `Clock`) | `@Transactional public RefundStatus complete(UUID refundId)` — 환불의 **마지막 단계**(재고 복구 + 품목·가게주문 반영 + `COMPLETED`)를 한 트랜잭션으로 한다. 따로 빈으로 둔 이유는 `RefundProcessor`(트랜잭션 없음)와 복구 잡이 트랜잭션 경계를 `this.` 호출 없이 얻기 위해서다(공통 규칙 K) |
| `RefundProcessor` (`@Service`, `@Transactional` 없음 — `paymentApi.cancel`이 트랜잭션 밖 전용이라서. 의존성 `RefundRepository`, `PaymentApi`, `RefundCompleter`, `TransactionTemplate tx`) | `public RefundStatus process(UUID refundId)` — **승인된 환불을 끝까지 진행한다**: PG 취소(필요하면) → 완료. 입력 환불 ID → 반환: 진행 뒤 환불 상태. 2-8b 복구 잡, 2-9 반품 승인이 재사용 |
| `CancelOrderLineService` (`@Transactional` 없음. 의존성 `OrderRepository`, `RefundRepository`, `RefundProcessor`, `TransactionTemplate tx`) | `public RefundResult cancel(UUID memberId, UUID orderId, UUID lineId, String reason)` — 구매자의 품목 취소 요청 한 건의 전체 흐름 |
| `ShipmentService.ship` (2-7) | 의존성에 `RefundRepository`를 더한다. 앞단 뒤, `order.ship` 앞에 `existsByShopOrderIdAndStatusIn(shopOrderId, {REQUESTED, APPROVED, PG_CANCELLED})`면 `REFUND_IN_PROGRESS`(2-7에서 비워 둔 검사). 환불이 진행 중인 가게주문을 발송해 버리면 취소한 품목이 발송되기 때문이다 |
| `OrderQueryService.getMyOrder`, `OrderDetailResult` (2-2) | 의존성에 `RefundRepository`를 더하고, `findAllByOrderId(orderId)`를 항목 record로 바꿔 `OrderDetailResult`에 `List<...> refunds`로 담는다. 항목은 `{refundId, orderLineId, reasonType, status, amount, createdAt}`. 항목 타입 이름은 문서에 정해져 있지 않다(확인 필요) |

`RefundCompleter.complete(refundId)` 순서 (한 트랜잭션)
1. `Refund refund = findById` → `!readyToComplete()`면 현재 상태 반환(이미 완료 등, 멱등)
2. `Order order = findByIdForUpdate(refund.getOrderId())` — 같은 주문의 다른 환불 완료와 가게주문 상태 재계산이 겹치지 않게 주문 행을 잠근다
3. `OrderLine line = order.findLine(refund.getOrderLineId())` → `productApi.restore(orderId, line.getProductId(), line.getQuantity(), refundId)` — 판매된 수량을 `sold → available`로 되돌린다. `refundId`로 재고 이력(`RESTORE`)이 unique라 두 번 불려도 한 번만 복구된다(`void`)
4. `order.applyRefund(refund.getOrderLineId(), refund.getAmount(), refund.getReasonType(), now)`
5. `refund.complete(now)` → COMPLETED 반환

`RefundProcessor.process(refundId)` 순서
1. `Refund refund = refundRepository.findById(refundId)`(없으면 `IllegalStateException`. 내부 값이라 없으면 버그) → `refund.needsPgCancel()`이면:
   - `CancelOutcome outcome = paymentApi.cancel(refund.getOrderId(), refund.getAmount(), "품목 취소", refund.pgIdempotencyKey())` (트랜잭션 밖)
   - `DONE` → `tx.execute(s -> { 환불을 다시 읽어 markPgCancelled() })` / `FAILED` → 같은 방식으로 `fail()`, return `FAILED` / `UNKNOWN` → return `APPROVED`(환불은 `APPROVED`로 남고 복구 잡이 이어서 한다)
   - 트랜잭션 안에서 환불을 **다시 읽는** 이유: 위에서 읽은 `refund`는 트랜잭션 밖에서 읽은 엔티티라 더티 체킹이 되지 않는다
2. `return refundCompleter.complete(refundId)` — 위에서 PG 취소를 막 끝냈거나 이미 `PG_CANCELLED`였던 경우

`CancelOrderLineService.cancel` 순서
1. 트랜잭션 1 (`tx.execute`, 커밋) — 반환: 새 환불의 `refundId`와 `amount`
   1. `order = findByIdAndMemberIdForUpdate` 없으면(남의 주문 포함) 404 `ORDER_NOT_FOUND` — **주문 행을 잠근다**(같은 주문의 다른 취소는 여기서 대기)
   2. `order.verifyCancellable(lineId)` — 발송됐거나 이미 취소된 품목이면 409 `INVALID_STATE_TRANSITION`
   3. `OrderLine line = order.findLine(lineId)`, `Money amount = line.getLineAmount().minus(line.getRefundedAmount())`
   4. `refundRepository.save(Refund.approveBuyerCancel(orderId, shopOrderId, lineId, memberId, amount, reason))` — `shopOrderId = order.shopOrderOf(lineId).getId()`. 같은 품목을 동시에 취소하면 둘 다 2를 통과할 수 있다(첫 번째는 환불만 `APPROVED`로 만들고 품목은 아직 `PAID`다). 늦은 쪽은 커밋 때 부분 unique 위반 → `GlobalExceptionHandler`가 409 `DUPLICATE_RESOURCE`로 바꾼다(서비스가 따로 잡지 않는다)
2. `RefundStatus status = refundProcessor.process(refundId)` (트랜잭션 밖)
3. 반환: COMPLETED → `RefundResult` (컨트롤러가 200) / APPROVED(PG 결과 불확실) → `RefundResult` (컨트롤러가 202) / FAILED → 서비스가 `BusinessException(CommonErrorCode.PG_ERROR)`를 던진다(502. 2-4 `ConfirmPaymentService`가 `PAYMENT_FAILED`를 던지는 것과 같은 방식. FAILED는 이미 커밋돼 있다)

**5) order.web**

| 대상 | 규격 |
|---|---|
| 컨트롤러 | 기존 `OrderController`(`/api/orders`)에 메서드를 더한다(권장) |
| `CancelLineRequest` | `(@Size(max=200) String reason)` |
| `RefundResponse` | `RefundResult`와 같은 필드(`refundId`, `status`, `amount`)를 가진 응답 record다(정의가 문서에 없어 `RefundResult`에 맞췄다. 확인 필요) |
| `OrderDetailResponse` (2-2) | `OrderDetailResult.refunds`에 맞춰 `refunds`를 더한다(항목은 같은 필드의 응답 record) |

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/orders/{id}/lines/{lineId}/cancel` `@Idempotent` | `CancelLineRequest` | 200 `RefundResponse` (COMPLETED) / 202 (APPROVED) / 409 / 502 |

- 컨트롤러는 `RefundResult.status`로 HTTP 상태를 정한다: `COMPLETED`면 `ResponseEntity.ok`, `APPROVED`면 `ResponseEntity.accepted`. `@Idempotent` 메서드는 `ResponseEntity`를 반환해야 한다(`IdempotencyAspect`). 409·502는 서비스가 던진 `BusinessException`이다.

**6) DB CHECK 확인**: `order_line.refunded_amount <= line_amount`(2-2), `payment.cancelled_amount <= amount`(2-4)가 이미 있다. 넘으면 DB가 거부하는지 `CancelOrderLineTest`의 마지막 케이스가 한 번 확인한다(`refunded_amount` 쪽).

### 할 일 (2-8b)

**복구 잡이 필요한 이유**: 환불은 PG 취소와 DB 반영이 서로 다른 트랜잭션이라 중간에서 멈출 수 있다. 멈춘 환불은 `status`가 어디까지 갔는지 보여 주므로, 잡이 그 상태의 환불을 찾아 **남은 단계만** 다시 실행한다(각 단계가 멱등이라 안전하다).

**구현 순서**: `RefundRecoveryService` → `RefundRecoveryJob` → `RefundRecoveryServiceTest`(헬퍼 `refundIdOf`·`setRefundUpdatedAt`은 완료 확인의 "테스트 준비"). `RefundRepository.findStuckIds`는 2-8a에서 만들었다.

| 대상 | 규격 |
|---|---|
| `RefundRecoveryService` (order.application) | 클래스에 `@Transactional` 없음. 의존성 `RefundRepository`, `RefundCompleter`, `RefundProcessor`, `Clock`. `public int recoverBatch()` — 반환: 처리한 건수. 단계마다 건별 try/catch로 감싸 한 환불의 예외가 나머지를 막지 않게 한다(2-5 `expireBatch`와 같다) |
| `RefundRecoveryJob` (order.infrastructure) | `@Scheduled(fixedDelay = 60_000)` → `jobRunner.run("recover-refunds", service::recoverBatch)` (2-5 `OrderExpirationJob`과 같은 모양) |

`recoverBatch` 순서 — 두 단계 모두 `before = now - 5분`이라 `updated_at`이 5분 넘게 바뀌지 않은 환불만 대상이다
1. `findStuckIds(PG_CANCELLED, now - 5분, Limit.of(100))` → 각 `refundCompleter.complete(id)` — PG 취소는 끝났는데 재고·품목 반영이 실패한 환불. 마지막 단계만 다시 한다
2. `findStuckIds(APPROVED, now - 5분, Limit.of(100))` → 각 `refundProcessor.process(id)` — PG 취소가 불확실(UNKNOWN)했거나 호출 전에 죽은 환불. 같은 Idempotency-Key(refundId)로 다시 부르므로 두 번 취소되지 않는다
3. 처리 건수 반환

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/domain/RefundStatusTest` | [ ] 전체 조합 파라미터화 |
| `order/domain/OrderRefundTest` | [ ] `applyRefund` → refundedAmount 증가, 품목 CANCELLED / [ ] 마지막 품목 취소 → 가게주문 CANCELLED / [ ] 발송된 가게주문의 품목 `verifyCancellable` → 예외 |
| `order/web/CancelOrderLineTest` (가짜 PG) | [ ] **품목 3개 중 1개 취소 → 그 품목 금액만 환불, 나머지 품목은 PAID 그대로** / [ ] 가짜 서버 취소 요청 `cancelAmount` = 품목 금액, `Idempotency-Key` = refundId / [ ] 품목 두 개를 차례로 취소 → `refunded_amount`·payment `cancelled_amount` 정확, `cancelled_amount ≤ amount` / [ ] 재고 `sold → available` 복구, 재고 이력 RESTORE 1건 / [ ] 발송된 품목 취소 → 409 `INVALID_STATE_TRANSITION` / [ ] 남의 주문 → 404 / [ ] PG 취소 타임아웃 → 202, refund APPROVED / [ ] PG 취소 거절 → 502, refund FAILED, 재고·품목 변화 없음 / [ ] 진행 중 환불이 있는 가게주문 발송 → 409 `REFUND_IN_PROGRESS` / [ ] DB CHECK: JDBC로 `refunded_amount`를 `line_amount + 1`로 올리는 UPDATE → 예외 |
| `order/application/CancelLineConcurrencyTest` | [ ] **같은 품목 동시 취소 2건**(다른 Idempotency-Key) → 환불 1건 COMPLETED, 나머지는 409 / [ ] 같은 주문의 서로 다른 품목 2개 동시 취소 → 둘 다 COMPLETED, payment `cancelled_amount` = 두 품목 합 ≤ 승인액 |
| `order/application/RefundRecoveryServiceTest` (2-8b) | [ ] **완료 직전 실패 주입**: `@MockitoSpyBean RefundCompleter`로 첫 호출만 예외(`doThrow(...).doCallRealMethod()`) → refund PG_CANCELLED로 남음 → JDBC로 `updated_at` 5분 전 → `recoverBatch()` → COMPLETED, 재고 RESTORE 1건, `refunded_amount` 한 번만 증가 / [ ] 취소 타임아웃으로 APPROVED에 멈춘 환불 → 복구 → 가짜 서버 취소 요청은 같은 키로 2번이지만 `cancelled_amount`는 한 번만 증가 |

**테스트 준비** ([N-1](#n-1-단계별-testfixtures-추가))

**이 단계에서 추가하는 헬퍼** (`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `orderLineIdOf(orderId, productId)` → lineId | JDBC `SELECT ol.id FROM orders.order_line ol JOIN orders.shop_order so ON so.id = ol.shop_order_id WHERE so.order_id = ? AND ol.product_id = ?` | 품목 취소·반품·구매확정 URL의 `lineId` |
| `refundIdOf(orderLineId)` → refundId | JDBC `SELECT id FROM orders.refund WHERE order_line_id = ? ORDER BY created_at DESC LIMIT 1` | 복구 잡 테스트(첫 호출이 예외로 끝나 응답이 없다) |
| `setRefundUpdatedAt(refundId, Instant)` | JDBC `UPDATE orders.refund SET updated_at = ? WHERE id = ?` | `RefundRecoveryServiceTest` ("5분 전") |

이미 있는 헬퍼(앞 단계·Part 1에서 만든 것, 그대로 쓴다): `signup`, `token`, `login`, `changeStatus`, `openShop`, `closeShop`, `registerProduct`, `hideProduct`, `discontinueProduct`, `registerProductImage`, `putToStorage`, `ConcurrencyRunner`, `address`, `addToCart`, `checkout`, `stockOf`, `StockSnapshot`, `createPendingOrder`, `confirmPayment`, `setOrderExpired`, `breakReservedStock`, `setOrderUpdatedAt`, `createPaidOrder`, `shopOrderIdOf`, `shipShopOrder`, `deliverShopOrder`

- 품목 3개: 한 가게의 상품 3개로 `createPaidOrder` → `orderLineIdOf(orderId, productId)`. 발송된 품목은 `shipShopOrder`를 더한다.
- PG 취소 타임아웃·거절: `pg.on("/v1/payments/" + paymentKey, Behavior.delay(..) / reject(..))`.
- 동시 취소: 같은 품목(다른 `Idempotency-Key`)이나 같은 주문의 서로 다른 품목 둘은 **작업이 서로 달라** `ConcurrencyRunner.run(List.of(...))`을 쓴다.
- 복구 잡: `@MockitoSpyBean RefundCompleter`로 첫 호출만 예외 → `refundIdOf(orderLineId)` → `setRefundUpdatedAt(refundId, 5분 전)` → `recoverBatch()`.

**리뷰 때 물어볼 것**
- 부분 환불을 어떻게 구현했나? 누적 취소액이 승인액을 넘지 않는 것은 무엇이 보장하나?
- PG 취소는 성공했는데 DB 반영이 실패하면?
- 각 단계를 멱등하게 만든 키는 각각 무엇인가? (refund 상태 전이, PG Idempotency-Key, 재고 이력 unique)
- 같은 주문의 품목 두 개를 동시에 취소할 때 주문 행을 잠그지 않으면 무엇이 충돌하나?

---

## 2-9. 반품 (S)

**목표**: 배송완료된 품목에 반품을 요청하고, 판매자가 승인하면 2-8과 같은 방식으로 환불한다. 거절하면 원래대로 돌아간다.

**정책 (이 단계에서 정함)**
- 반품 요청은 DELIVERED 품목만, 구매확정 전까지.
- 거절된 품목은 다시 반품을 요청할 수 없다(OPEN-03 권장안).
- 반품 배송비는 다루지 않는다(전액 환불).

### 할 일

반품은 2-8의 `Refund`·`RefundProcessor`를 그대로 쓰고, **구매자가 요청하고 판매자가 승인해야 환불이 시작된다**는 점만 다르다. 요청하면 품목이 `RETURN_REQUESTED`가 되고 환불이 `REQUESTED`로 만들어진다. 승인하면 환불이 `APPROVED`가 되어 2-8과 같은 PG 취소 → 완료가 돌고, 거절하면 품목이 `DELIVERED`로 돌아간다.

**구현 순서**: ① `OrderErrorCode`, `OrderLine`·`Refund`·`Order` 메서드, `RefundRepository`(1) → ② `ReturnService`(2) → ③ 컨트롤러(3) → ④ `TestFixtures` 헬퍼(완료 확인의 "테스트 준비") → `ReturnTest`.

**1) order.domain**

`OrderErrorCode`에 `REFUND_NOT_FOUND`(404, "환불 요청을 찾을 수 없습니다."), `RETURN_NOT_ALLOWED`(409, "반품을 요청할 수 없는 품목입니다.")를 더한다(공통 규칙 O).

| 대상 | 메서드 | 하는 일 |
|---|---|---|
| `OrderLine` | `void markReturnRequested()` | `status = status.transitTo(RETURN_REQUESTED)` (`DELIVERED → RETURN_REQUESTED`). `DELIVERED`가 아니면 — 배송 중·구매확정 후·이미 요청됨 — `INVALID_STATE_TRANSITION`. 이름은 권장안(2-7 `markShipped`·`markDelivered`와 같은 규칙) |
| `Refund` | `static Refund requestReturn(UUID orderId, UUID shopOrderId, UUID orderLineId, UUID memberId, Money amount, String reason)` | 반품 환불을 **만든다** → `REQUESTED`, `reasonType = RETURN`. 2-8의 `approveBuyerCancel`과 시작 상태만 다르다 |
| `Refund` | `void approve()` | 판매자 승인 `REQUESTED → APPROVED` |
| `Refund` | `void reject(String reason)` | 판매자 거절 `REQUESTED → REJECTED`, `reject_reason = reason` |
| `Order` | `void requestReturn(UUID lineId)` | 주문이 `PAID`가 아니면 `INVALID_STATE_TRANSITION`. `findLine(lineId).markReturnRequested()` |
| `Order` | `void rejectReturn(UUID lineId)` | 품목 `RETURN_REQUESTED → DELIVERED`: `findLine(lineId).markDelivered()`(2-7) |
| `RefundRepository` | `boolean existsByOrderLineIdAndStatus(UUID orderLineId, RefundStatus status)` | 그 품목에 해당 상태의 환불이 있는지. `REJECTED`로 거절 이력을 확인한다 (파생 쿼리) |

**2) order.application**

| 클래스 | 메서드 |
|---|---|
| `ReturnService` (의존성 `OrderRepository`, `RefundRepository`, `RefundProcessor`, `ShopApi`, `TransactionTemplate tx`) | `@Transactional RefundResult request(UUID memberId, UUID orderId, UUID lineId, String reason)` — 구매자의 반품 요청 / `RefundResult approve(UUID memberId, UUID shopId, UUID refundId)` (`@Transactional` 없음. PG를 부르므로) — 판매자 승인 / `@Transactional RefundResult reject(UUID memberId, UUID shopId, UUID refundId, String reason)` — 판매자 거절 |

세 메서드 모두 `RefundResult(refundId, status, amount)`를 돌려준다(`amount`는 환불의 `getAmount().amount()`).

`request` 순서 (한 트랜잭션)
1. `Order order = findByIdAndMemberIdForUpdate(orderId, memberId)` — 없으면(남의 주문 포함) 404 `ORDER_NOT_FOUND`. 주문 행을 잠가 같은 품목의 동시 요청을 줄 세운다
2. 그 품목에 `REJECTED` 환불이 있으면(`existsByOrderLineIdAndStatus(lineId, REJECTED)`) 409 `RETURN_NOT_ALLOWED`(한 번 거절되면 다시 요청할 수 없다. 정책)
3. `order.requestReturn(lineId)` — 품목이 `DELIVERED`가 아니면 여기서 409 `INVALID_STATE_TRANSITION`(같은 품목에 요청을 두 번 보내면 두 번째가 여기다)
4. `refundRepository.save(Refund.requestReturn(orderId, shopOrderId, lineId, memberId, amount, reason))` — 금액 = `lineAmount - refundedAmount`, `shopOrderId = order.shopOrderOf(lineId).getId()`. 반환 `RefundResult(refundId, REQUESTED, amount)`

`approve` 순서 — 승인 후 PG를 부르므로 `@Transactional`이 없고 `tx.execute`로 트랜잭션을 연다
1. **트랜잭션** (`tx.execute`, 커밋)
   1. `shopApi.verifyOwner(shopId, memberId)` — 주인이 아니면 403 `FORBIDDEN`
   2. `Refund refund = refundRepository.findById(refundId)` — 없으면 404 `REFUND_NOT_FOUND`
   3. 그 refund의 가게주문이 이 가게 것이 아니면 `REFUND_NOT_FOUND` (남의 환불은 없는 것처럼)
   4. `order = findByIdForUpdate(refund.getOrderId())` → `refund.approve()`(`REQUESTED`가 아니면 `INVALID_STATE_TRANSITION`)
   - (확인 필요) 3번은 가게주문의 `shopId`가 필요한데 `Refund`에는 `shop_order_id`뿐이라 `Order`(`order.findShopOrder(refund.getShopOrderId()).getShopId()`)에서 알 수 있다. 그러면 3번 전에 주문을 읽어야 해서 문서의 순서(3 → 4)와 어긋난다. 어떤 순서로 하는지 정해져 있지 않다.
2. `RefundStatus status = refundProcessor.process(refundId)` (트랜잭션 밖. 2-8과 같음. 완료 시 품목 `RETURNED`, 가게주문 재계산)
3. 반환 규칙은 2-8과 같음(`COMPLETED` → 200, `APPROVED` → 202, `FAILED` → 서비스가 `PG_ERROR` 502)

`reject` 순서 (한 트랜잭션)
1. `shopApi.verifyOwner(shopId, memberId)`, refund 조회 — 그 refund의 가게주문이 이 가게 것이 아니면 `REFUND_NOT_FOUND` (확인 방법과 순서는 위 `approve`와 같다)
2. `refund.reject(reason)` — `REQUESTED`가 아니면 `INVALID_STATE_TRANSITION`
3. `order.rejectReturn(lineId)` — `lineId`는 `refund.getOrderLineId()`, `order`는 `findById(refund.getOrderId())`. 반환 `RefundResult(refundId, REJECTED, amount)`

**3) order.web** — 컨트롤러 위치는 권장안이다: `return-requests`는 기존 `OrderController`(`/api/orders`)에, `/api/shops/{shopId}/refunds/...`는 새 `SellerRefundController`에 둔다. `RefundResponse`는 2-8의 것을 그대로 쓴다.

| API | 요청 | 응답 |
|---|---|---|
| `POST /api/orders/{id}/lines/{lineId}/return-requests` `@Idempotent` | `ReturnRequest(@NotBlank @Size(max=200) String reason)` | 201 `RefundResponse` (REQUESTED) |
| `POST /api/shops/{shopId}/refunds/{refundId}/approve` | | 200 / 202 / 502 |
| `POST /api/shops/{shopId}/refunds/{refundId}/reject` | `RejectReturnRequest(@NotBlank @Size(max=200) String reason)` | 200 `RefundResponse` (REJECTED) |

- 판매자용 반품 목록이 필요하면 2-7 상세 응답의 품목 상태(RETURN_REQUESTED)로 확인한다(별도 목록 API는 만들지 않음).

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/web/ReturnTest` | [ ] 요청 → 승인 → 환불 COMPLETED → 품목 RETURNED, 가게주문의 나머지 품목이 모두 종결이면 COMPLETED / [ ] 요청 → 거절 → 품목 DELIVERED / 다시 요청 → 409 `RETURN_NOT_ALLOWED` / [ ] 배송 중(SHIPPED) 품목 반품 요청 → 409 `INVALID_STATE_TRANSITION` / [ ] **다른 가게 판매자가 승인 → 403** / [ ] 같은 품목에 반품 요청 두 번(다른 키) → 두 번째는 품목이 이미 RETURN_REQUESTED라 409 `INVALID_STATE_TRANSITION` |

**테스트 준비** ([N-1](#n-1-단계별-testfixtures-추가))

**이 단계에서 추가하는 헬퍼** (`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `createDeliveredOrder(UUID buyerId, UUID sellerId, UUID shopId, UUID... productIds)` → orderId | `createPaidOrder` → `shopOrderIdOf` → `shipShopOrder` → `deliverShopOrder`. **한 가게 상품만**(여러 가게면 가게마다 위 헬퍼를 직접 잇는다) | `ReturnTest`, 2-10 |

이미 있는 헬퍼(앞 단계·Part 1에서 만든 것, 그대로 쓴다): `signup`, `token`, `login`, `changeStatus`, `openShop`, `closeShop`, `registerProduct`, `hideProduct`, `discontinueProduct`, `registerProductImage`, `putToStorage`, `ConcurrencyRunner`, `address`, `addToCart`, `checkout`, `stockOf`, `StockSnapshot`, `createPendingOrder`, `confirmPayment`, `setOrderExpired`, `breakReservedStock`, `setOrderUpdatedAt`, `createPaidOrder`, `shopOrderIdOf`, `shipShopOrder`, `deliverShopOrder`, `orderLineIdOf`, `refundIdOf`, `setRefundUpdatedAt`

- 반품 대상: `createDeliveredOrder(buyerId, sellerId, shopId, productIds...)`(한 가게). 배송 중 품목은 `createPaidOrder` 후 `shipShopOrder`만 하고 `deliverShopOrder`는 하지 않는다.
- 다른 가게 판매자: 두 번째 판매자와 가게를 만들어 첫 가게의 `refundId`로 승인을 시도한다.
- 같은 품목에 반품 요청 두 번: 다른 `Idempotency-Key`로 MockMvc를 두 번 호출한다(순차).

**리뷰 때 물어볼 것**
- 2-8 코드를 어떻게 재사용했나? 복사했다면 왜?
- 반품 요청부터 승인까지 품목 상태와 환불 상태를 어떻게 맞췄나?

---

## 2-10. 구매확정 (수동 · 자동) (M)

**목표**: 구매자가 구매확정하거나, 배송완료 후 7일이 지나면 자동으로 확정된다. 확정된 품목은 이후 정산(3-4)과 리뷰(6-1)의 대상이 된다.

**정책 (이 단계에서 정함)**
- 자동 확정 기준: 가게주문 `delivered_at + 7일 < now` (POL-06). 반품 요청 중(RETURN_REQUESTED) 품목은 제외.
- 확정 금액 = `line_amount - refunded_amount` (정산 이벤트는 3-3에서 붙인다).

### 할 일

**구현 순서**: ① 마이그레이션(1) → ② `OrderLine`·`Order` 메서드, `AutoConfirmTarget`, `OrderRepository` 쿼리(2) → ③ `ConfirmPurchaseService` → `AutoConfirmService` → `AutoConfirmJob`(3) → ④ 컨트롤러(4) → ⑤ `TestFixtures` 헬퍼(완료 확인의 "테스트 준비") → 테스트.

**1) 마이그레이션** — `db/migration/order/V{...}__order_add_shop_order_status_delivered_at_index.sql`: `idx_shop_order_status_delivered_at ON orders.shop_order (status, delivered_at, id)`

**2) order.domain**

구매확정은 품목 단위다. 확정한 품목은 정산(3-4)·리뷰(6-1)의 대상이 된다. 메서드 이름은 권장안이다(2-7·2-8의 `OrderLine`·`ShopOrder` 메서드와 같은 규칙).

| 대상 | 시그니처 | 하는 일 |
|---|---|---|
| `OrderLine` | `void markConfirmed(Instant now)` | `status = status.transitTo(CONFIRMED)` (`DELIVERED → CONFIRMED`. `DELIVERED`가 아니면 — 반품 요청 중·이미 확정 포함 — `INVALID_STATE_TRANSITION` 409), `confirmedAt = now` |
| `OrderLine` | `Money confirmedAmount()` | 정산 대상 금액 `lineAmount - refundedAmount`(`Money.minus`). 지금은 쓰는 곳이 없다(정산 이벤트가 3-3에서 붙으면 쓴다). 테스트만 확인한다 |
| `Order` | `void confirmLine(UUID lineId, Instant now)` | 구매자가 한 품목을 확정한다. `findLine(lineId).markConfirmed(now)` → `recalculateShopOrder`(2-8. 마지막 품목이면 가게주문 `COMPLETED`) |
| `Order` | `int autoConfirm(UUID shopOrderId, Instant now)` | 배송완료 후 7일이 지난 가게주문의 품목을 한꺼번에 확정한다. `findShopOrder(shopOrderId)`의 품목 중 `DELIVERED`인 것만 `markConfirmed(now)`(`RETURN_REQUESTED`·`CANCELLED`·`RETURNED` 등은 건너뜀. 건너뛴 품목에 `transitTo`를 하면 예외라 상태를 먼저 거른다) → `recalculateShopOrder`. 확정한 품목 수를 반환한다 |
| `AutoConfirmTarget` | `record (UUID shopOrderId, Instant deliveredAt)` (`order.domain`) | 자동 확정 후보 한 건. 다음 페이지의 커서가 된다 |
| `OrderRepository` | `List<AutoConfirmTarget> findAutoConfirmTargetsFirstPage(ShopOrderStatus status, Instant before, Limit limit)` (권장 이름) | `select new ...AutoConfirmTarget(so.id, so.deliveredAt) from ShopOrder so where so.status = :status and so.deliveredAt < :before order by so.deliveredAt, so.id`. 커서가 없어 `cursorAt`·`cursorId` 조건이 빠진다 |
| `OrderRepository` | `List<AutoConfirmTarget> findAutoConfirmTargets(ShopOrderStatus status, Instant before, Instant cursorAt, UUID cursorId, Limit limit)` | 첫 페이지 쿼리의 `where`에 `and (so.deliveredAt > :cursorAt or (so.deliveredAt = :cursorAt and so.id > :cursorId))`를 더한다. 시그니처는 `OrderRepository`(domain), `@Query`는 `JpaOrderRepository`. `status`는 `ShopOrderStatus.DELIVERED`. offset이 아니라 keyset인 이유: 처리하는 동안 가게주문이 `COMPLETED`로 바뀌어 목록에서 빠져도 offset처럼 다음 페이지가 건너뛰어지지 않는다(As-Is BAT-02) |

**3) order.application**

| 클래스 | 메서드 |
|---|---|
| `ConfirmPurchaseService` (`@Transactional`. 의존성 `OrderRepository`, `Clock`) | `OrderLineResult confirm(UUID memberId, UUID orderId, UUID lineId)` — 구매자의 수동 확정. `OrderLineResult`는 `record (UUID orderLineId, OrderLineStatus status, Instant confirmedAt)` |
| `AutoConfirmService` (`@Transactional` 없음. 의존성 `OrderRepository`, `TransactionTemplate tx`, `Clock`) | `public int confirmBatch()` — 자동 확정 한 번을 돌린다. 반환: 확정한 품목 수 |
| `AutoConfirmJob` (order.infrastructure) | `@Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")`(매시 정각) → `jobRunner.run("auto-confirm", service::confirmBatch)` |

`ConfirmPurchaseService.confirm` 순서
1. `Order order = findByIdAndMemberId(orderId, memberId)` — 없으면(남의 주문 포함) 404 `ORDER_NOT_FOUND`
2. `order.confirmLine(lineId, clock.instant())` — `DELIVERED`가 아니면 409 `INVALID_STATE_TRANSITION`
3. `OrderLine line = order.findLine(lineId)`에서 `OrderLineResult(lineId, line.getStatus(), line.getConfirmedAt())`를 만들어 반환(저장은 커밋 때 더티 체킹). 자동 확정과 같은 품목을 동시에 바꾸면 `@Version` 충돌로 한쪽이 낙관적 락 예외가 되고 `GlobalExceptionHandler`가 409 `CONFLICT_RETRY`로 바꾼다

`confirmBatch` 순서 — 후보를 100건씩 읽어 가게주문 한 건마다 트랜잭션으로 확정한다
1. `Instant now = clock.instant()`, `before = now - 7일`, 커서 없음으로 시작 (`delivered_at + 7일 < now`와 같다)
2. `List<AutoConfirmTarget> targets` = 커서가 없으면 `findAutoConfirmTargetsFirstPage(DELIVERED, before, Limit.of(100))`, 있으면 `findAutoConfirmTargets(DELIVERED, before, cursor.deliveredAt(), cursor.shopOrderId(), Limit.of(100))` → 비면 종료
3. 각 대상마다 `try { count += tx.execute(s -> findByShopOrderId(id).orElseThrow().autoConfirm(id, now)) } catch (Exception e) { log }` — `tx.execute`가 확정한 품목 수(`int`)를 돌려준다. 낙관적 락 충돌(구매자가 동시에 수동 확정)은 커밋 때 나지만 `tx.execute` 안이라 여기서 잡혀 그 건만 건너뛴다
4. `targets`가 100건 미만이면 마지막 페이지라 종료한다(다음 조회는 빈 결과일 뿐이다). 아니면 커서 = 마지막 대상 `(deliveredAt, id)` → 2로
5. 확정한 품목 수 반환

**4) order.web**: 기존 `OrderController`에 `POST /api/orders/{id}/lines/{lineId}/confirm`을 더한다(권장) → 200 `OrderLineResponse(orderLineId, status, confirmedAt)` — `OrderLineResult`를 그대로 옮긴 응답 record다. `@Idempotent`는 붙이지 않는다(이미 확정된 품목에 다시 보내면 409라서 안전하다)

### 완료 확인

| 테스트 클래스 | 케이스 |
|---|---|
| `order/domain/OrderConfirmTest` | [ ] `confirmLine`: DELIVERED만 / [ ] 마지막 품목 확정 → 가게주문 COMPLETED / [ ] `autoConfirm`: RETURN_REQUESTED 품목은 그대로 / [ ] `confirmedAmount` = lineAmount - refundedAmount |
| `order/application/AutoConfirmServiceTest` | [ ] `delivered_at`을 8일 전으로 → 자동 확정 / 6일 전 → 그대로 / [ ] 반품 요청 중인 품목은 확정되지 않는다 / [ ] 대상 150건(100건 넘게) → 모두 확정 |
| `order/application/ConfirmConcurrencyTest` | [ ] 수동 확정과 `confirmBatch()`를 동시에 20회 반복 → 매번 품목 CONFIRMED, `confirmed_at` 1개, 예외는 낙관적 락·409 외에 없음 |
| `order/web/ConfirmPurchaseTest` | [ ] DELIVERED 아닌 품목 확정 → 409 / [ ] 남의 주문 → 404 |

**테스트 준비** ([N-1](#n-1-단계별-testfixtures-추가))

**이 단계에서 추가하는 헬퍼** (`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다)

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `setShopOrderDeliveredAt(shopOrderId, Instant)` | JDBC `UPDATE orders.shop_order SET delivered_at = ? WHERE id = ?` | `AutoConfirmServiceTest`(8일 전·6일 전), `ConfirmConcurrencyTest` |

이미 있는 헬퍼(앞 단계·Part 1에서 만든 것, 그대로 쓴다): `signup`, `token`, `login`, `changeStatus`, `openShop`, `closeShop`, `registerProduct`, `hideProduct`, `discontinueProduct`, `registerProductImage`, `putToStorage`, `ConcurrencyRunner`, `address`, `addToCart`, `checkout`, `stockOf`, `StockSnapshot`, `createPendingOrder`, `confirmPayment`, `setOrderExpired`, `breakReservedStock`, `setOrderUpdatedAt`, `createPaidOrder`, `shopOrderIdOf`, `shipShopOrder`, `deliverShopOrder`, `orderLineIdOf`, `refundIdOf`, `setRefundUpdatedAt`, `createDeliveredOrder`

- 자동 확정 대상: `createDeliveredOrder` 후 `setShopOrderDeliveredAt(shopOrderId, now - 8일)`(6일 전도 같은 방식). 반품 요청 중 품목은 `ReturnService.request` 또는 HTTP로 만든다.
- 150건: 상품 재고를 150 이상으로 만들고 `createDeliveredOrder`를 150번 반복한다. 느리면 JDBC로 `shop_order`·`order_line`을 직접 채우는 방법을 검토한다(확인 필요).
- 동시성(`ConfirmConcurrencyTest`): 반복할 때마다 새 주문을 만들고, 수동 확정과 `confirmBatch()`는 **서로 다른 작업**이라 `ConcurrencyRunner.run(List.of(수동, 배치))`를 쓴다.

**리뷰 때 물어볼 것**
- 원 프로젝트의 스케줄러(As-Is ORD-07)와 무엇이 다른가?
- 자동 확정을 offset 페이징으로 돌리면 무슨 일이 생기나?

---

## Part 2 완료
- [ ] develop → main, 태그 `v0.2.0`. Release 노트에 "결제 불확실성 처리"와 "품목 단위 환불" 요약
- [ ] Part 3 문서 다듬기
- [ ] Release 노트용 "알려진 한계"를 Part 1처럼 이 절에 표로 모아 둔다

**알려진 한계 (2-2 시점, 단계가 끝날 때마다 더한다)**

| 한계 | 영향 | 회복·방어 | 출처 |
|---|---|---|---|
| 중복 주문 | 같은 장바구니로 결제 대기 주문이 둘 생기고, 둘 다 결제하면 주문 2건 | 결제 전이면 주문 취소(2-4)로 바로 풀고, 결제됐다면 발송 전 품목 취소·환불(2-8). 같은 주문의 이중 결제는 막힘. 방어 선택지는 2-2 "채택하지 않은 선택지" | 2-2 |
| 결제 중 장바구니 표시 | 결제 대기 중인 항목이 구매 가능·`OUT_OF_STOCK`으로 보임 | 표시만 영향 | 2-2 |
| 차감이 상품 단위 | 결제 중 항목을 삭제 후 재담기하면 새 수량에서 차감 | 드문 경우, 수용 | 2-2 |
| 재고 점유 남용 | 회원당 결제 대기 수·체크아웃 횟수 상한 없음 → 반복 점유 가능 | Part 4 이메일 인증, Part 7 모니터링, 2-2 "추가 항목" | 2-2 |
