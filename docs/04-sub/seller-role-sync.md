# 서브: SELLER 역할 맞추기

> 버전 0.1 · 2026-11-14 · 가게 개설·폐업 이벤트를 받으면 둘 다 회원 행을 잠근 뒤 그 시점의 활성 가게 수로 SELLER·USER를 맞춘다(단계 ID SUB-22)

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙. 역할 부여·회수 규격의 출발점은 [로드맵 3-1·3-2](../03-roadmap/part3-events.md)다.

---

## SUB-22. SELLER 역할 맞추기 (S)

**목표**: 가게 개설·폐업 이벤트가 어떤 순서로 오더라도, 처리가 끝나면 회원 역할이 그 시점의 활성 가게 수와 맞게 한다(INV-09). 활성 가게가 없는데 SELLER로 남거나, 활성 가게가 있는데 USER로 남는 일을 막는다.

**드러나는 때**: `shop-opened`과 `shop-closed`는 토픽이 달라서 같은 가게의 이벤트라도 처리 순서가 보장되지 않는다(Part 3 공통 규칙 P의 "키" 행: 순서는 같은 토픽·같은 키 안에서만 지켜진다).

| 경우 | 흐름 | 남는 상태 |
|---|---|---|
| ① 늦게 처리된 개설 | 개설 처리가 DB 오류로 재시도를 다 쓰고 `.dlt`로 간다 → 가게가 폐업된다(새 가게는 진행 중 주문이 없어 바로 폐업할 수 있다). 폐업 처리는 활성 가게 0개를 보고 USER로 둔다 → 관리자가 `AdminDltService.replay`로 개설 이벤트를 다시 보낸다. 실패할 때 inbox 행도 함께 롤백됐으므로 처음 본 이벤트로 처리된다. 발행이 10번 실패해 `DEAD`가 된 개설 이벤트를 폐업 뒤에 `AdminOutboxService.retry`로 다시 보내도 같다 | 활성 가게 0개인 SELLER |
| ② 겹친 폐업과 개설 | 가게 A 하나를 가진 SELLER 회원이 A를 폐업하고 곧바로 새 가게 B를 연다. A의 폐업 처리가 활성 가게 수를 0으로 읽는다(B의 개설은 아직 커밋 전) → B의 개설이 커밋되고, B의 개설 처리가 회원을 SELLER로 읽고 아무것도 쓰지 않고 끝난다 → A의 폐업 처리가 USER로 커밋한다 | 활성 가게 1개인 USER |

①은 가게가 `ACTIVE → CLOSED`뿐이라 그 회원이 다른 가게를 열고 닫기 전까지 바로잡히지 않는다. ②는 폐업 처리 트랜잭션이 릴레이 주기(1초)와 소비 시간보다 오래 열려 있어야 생긴다.

### 빈 곳

- `MemberRoleService.grantSeller`는 활성 가게 수를 보지 않고 SELLER로 올린다. 회수 쪽(`revokeSellerIfNoActiveShop`)만 처리 시점의 가게 수를 본다.
- 두 메서드 모두 회원을 `findById`로 읽는다. 행을 잠그지 않으므로 두 처리가 겹치면 먼저 커밋한 쪽의 결과를 늦게 커밋한 쪽이 덮는다. 위 ②에서 개설 처리가 아무것도 쓰지 않는 것은 JPA가 바뀐 필드만 UPDATE하는데, 읽은 역할이 이미 SELLER라 바뀐 것이 없기 때문이다.

역할 값을 읽는 곳은 `/api/members/me` 응답의 `role`, 관리자 회원 목록(`AdminMemberResult`)의 `role`, JWT의 `role` 클레임이다. 인가는 소유권으로 하고 `hasRole`은 `"ADMIN"` 하나뿐이라 `ROLE_SELLER`를 검사하는 곳이 없다. 그래서 틀린 역할이 권한에는 닿지 않고, 화면 표시와 관리자 목록이 틀린다.

### 정책

- 역할은 이벤트 종류가 아니라 **처리하는 시점의 활성 가게 수**로 정한다. 1개 이상이면 SELLER, 0개면 USER다. 개설·폐업 이벤트는 "이 회원의 역할을 다시 맞춰라"는 신호이고, 어떤 이벤트가 먼저 오든 마지막 처리가 지금의 가게 상태를 반영한다.
- ADMIN은 바꾸지 않는다(지금과 같다).
- 역할을 맞추는 동안 회원 행을 잠가 같은 회원의 처리가 차례로 실행되게 한다.

### 고른 방법

| 방법 | 막는 경우 | 판단 |
|---|---|---|
| 두 consumer가 같은 메서드를 부르고, 그 메서드가 회원 행을 잠근 뒤 가게 수로 맞춘다(할 일) | ①, ② | 고른다. 바뀌는 곳이 서비스 메서드 하나와 consumer 두 줄이고, 잠금 조회 `findByIdForUpdate`는 탈퇴(`WithdrawService`)가 이미 쓴다. outbox·inbox로 역할을 바꾸는 3-1의 흐름은 그대로 남는다 |
| 같은 메서드로 합치고 잠그지 않는다 | ① | 고르지 않는다. ②가 남고, 잠금을 더하는 비용이 거의 없어 뺄 이유가 없다 |
| 이벤트 체인을 없애고 역할을 읽을 때 가게 수로 계산한다 | ①, ② | 고르지 않는다. 이벤트를 거치지 않으니 순서 문제가 아예 생기지 않는다. 대신 `/me`·관리자 회원 목록·JWT 발급 세 곳이 가게 수를 세야 하고(관리자 목록은 페이지의 회원별 가게 수를 한 번에 세는 조회가 필요하다), `shop-opened`의 유일한 consumer가 없어져 3-1의 outbox 첫 사례가 사라진다 |

### 할 일

**1) member.application**

| 클래스 | 규격 |
|---|---|
| `MemberRoleService` | `grantSeller`·`revokeSellerIfNoActiveShop`을 지우고 `@Transactional void syncSellerRole(UUID memberId)` 하나로 바꾼다. ① `memberRepository.findByIdForUpdate(memberId)`로 회원 행을 잠그며 읽는다(없으면 지금처럼 `log.warn("회원을 찾을 수 없습니다. memberId={}", memberId)` 후 return) ② `shopApi.countActiveShops(memberId)` ③ 0보다 크면 `member.promoteToSeller()`, 0이면 `member.demoteToUser()`. 두 도메인 메서드는 지금처럼 ADMIN을 바꾸지 않는다 |

①과 ②의 순서가 ②의 경합을 막는다. 위 ②에서 B의 개설 처리는 가게 B가 커밋된 뒤에야 시작된다(outbox 행은 커밋 뒤에 릴레이된다). 그래서 폐업 처리가 잠금을 쥐고 있으면 개설 처리는 잠금을 기다렸다가 폐업이 커밋한 USER를 읽고, 그다음에 세는 가게 수에는 B가 들어 있어 SELLER로 바꾼다. Postgres의 기본 격리 수준(READ COMMITTED)은 문장마다 그때까지 커밋된 데이터를 보므로, 잠금 뒤의 `countActiveShops`가 B를 본다. 가게 수를 먼저 세고 잠그면 잠금을 기다리기 전에 센 값으로 쓰게 되어 막지 못한다.

**2) member.infrastructure**

`MemberShopEventConsumer`의 `onShopOpened`·`onShopClosed`가 `inboxGuard.runOnce(...)` 안에서 `memberRoleService.syncSellerRole(typed.payload().memberId())`를 부른다. inbox consumer 이름(`member.shop-opened`·`member.shop-closed`)은 그대로다.

**3) 기존 테스트**

- `CloseShopTest`의 "같은 shop-closed 이벤트를 두 번 받아도 한 번만 처리한다."에서 `then(memberRoleService).should(times(2)).revokeSellerIfNoActiveShop(sellerId)`를 `syncSellerRole(sellerId)`로 바꾼다.
- `MemberRoleServiceTest`는 기존 8개를 아래 완료 확인 1~6번으로 고친다. 동작은 모두 `syncSellerRole`로 바꾸고, 메서드명·DisplayName도 새 동작에 맞춘다.

| 기존 메서드 | 고칠 것 | 완료 확인 |
|---|---|---|
| `grantSellerPromotesUser` | 준비에 `openShop`을 더한다 | 1 |
| `revokeSellerDemotesSellerWithoutActiveShop` | 동작만 바꾼다 | 2 |
| `revokeSellerKeepsSellerWithActiveShop` | 동작만 바꾼다 | 3 |
| `grantSellerKeepsAdmin` | 준비에 `openShop`을 더한다 | 4 |
| `revokeSellerKeepsAdmin` | 동작만 바꾼다 | 5 |
| `grantSellerIgnoresMissingMember`, `revokeSellerIgnoresMissingMember` | 하나로 합친다 | 6 |
| `grantSellerKeepsSeller` | 지운다. 가게 없는 SELLER가 SELLER로 남는다는 결론이 새 규칙(2번, USER가 된다)과 반대다 | - |

### 완료 확인

케이스는 [로드맵 README 4.1](../03-roadmap/README.md)의 기준으로 골랐다. 격리 복사본에서 할 일 1·2를 넣으면 7번과 `ShopOpenedFlowTest` 4번이 통과하고, 1의 잠금을 `findById`로 바꾸거나 가게 수를 센 뒤에 잠그면 7번이 실패하며(USER), 가게 수를 보지 않고 SELLER로 올리면 `ShopOpenedFlowTest` 4번이 실패하는(SELLER) 것을 확인했다.

새로 만드는 테스트 헬퍼는 없다. `openShop`은 `OpenShopService`를 부르고, 테스트는 예약 작업이 꺼져 있어(`myroutine.scheduling.enabled: false`) outbox 행이 릴레이되지 않으므로 준비 중에 역할이 바뀌지 않는다.

#### `member/application/MemberRoleServiceTest`

동작은 `memberRoleService.syncSellerRole(memberId)`, 역할은 `testFixtures.memberSnapshotOf(memberId).role()`로 읽는다. 준비의 회원은 `testFixtures.signup("test@test.com")`이다.

| 케이스 | 준비 | 확인할 것 |
|---|---|---|
| 1. ACTIVE 가게가 있는 USER 회원은 SELLER가 된다 | `openShop(memberId, "1111111111")` | 역할 = `SELLER` |
| 2. ACTIVE 가게가 없는 SELLER 회원은 USER가 된다 | `openShop` → `closeShop(shopId)` → `changeRole(memberId, "SELLER")` | 역할 = `USER` |
| 3. ACTIVE 가게가 남은 SELLER 회원은 SELLER 그대로 둔다 | 가게 2개 `openShop`(`"1111111111"`·`"2222222222"`) → 첫 가게 `closeShop` → `changeRole(memberId, "SELLER")` | 역할 = `SELLER` |
| 4. ADMIN 회원은 ACTIVE 가게가 있어도 ADMIN 그대로 둔다 | `openShop` → `changeRole(memberId, "ADMIN")` | 역할 = `ADMIN` |
| 5. ADMIN 회원은 ACTIVE 가게가 없어도 ADMIN 그대로 둔다 | `openShop` → `closeShop(shopId)` → `changeRole(memberId, "ADMIN")` | 역할 = `ADMIN` |
| 6. 없는 회원이면 예외 없이 끝난다 | 없음. 동작은 `syncSellerRole(Ids.newId())` | `assertThatCode(...).doesNotThrowAnyException()` |

**7. 폐업 처리가 커밋하기 전에 다른 가게의 개설 처리가 와도 SELLER로 끝난다** — 테스트 클래스에 `@MockitoSpyBean ShopApi shopApi` 필드를 더한다. 스텁하지 않은 1~6번은 실제 메서드를 쓴다.

준비: `shopAId = openShop(memberId, "1111111111")` → `changeRole(memberId, "SELLER")` → `closeShop(shopAId)`. `AtomicBoolean pauseOnce = new AtomicBoolean(true)`, `CountDownLatch entered = new CountDownLatch(1)`, `CountDownLatch release = new CountDownLatch(1)`와 `willAnswer(inv -> { Object count = inv.callRealMethod(); if (pauseOnce.getAndSet(false)) { entered.countDown(); release.await(5, TimeUnit.SECONDS); } return count; }).given(shopApi).countActiveShops(memberId)` → `CompletableFuture<Void> closing = CompletableFuture.runAsync(() -> memberRoleService.syncSellerRole(memberId))` → `entered.await(5, TimeUnit.SECONDS)` → `openShop(memberId, "2222222222")`. 가게 A의 폐업 처리가 가게 수 0을 읽은 채, 커밋하기 전에 멈추고, 그동안 가게 B가 커밋된다.

동작: `CompletableFuture<Void> opening = CompletableFuture.runAsync(() -> memberRoleService.syncSellerRole(memberId))` → `opening.get(1, TimeUnit.SECONDS)`(`TimeoutException`은 무시) → `release.countDown()` → `closing.get(10, TimeUnit.SECONDS)` → `opening.get(10, TimeUnit.SECONDS)`. 잠금이 없으면 B의 개설 처리는 1초 안에 아무것도 쓰지 않고 끝나고, 잠금이 있으면 폐업 처리의 잠금을 기다리므로 1초 제한 뒤 폐업 처리를 풀어 준다.
- DB(`memberSnapshotOf(memberId)`): `role()` = `SELLER`.

#### `shop/application/ShopOpenedFlowTest`

**4. 폐업한 가게의 개설 이벤트가 늦게 처리되어도 SELLER를 주지 않는다** — 준비: `sellerId = signup("seller@test.com")`, `shopId = openShop(sellerId, "1111111111")`, `closeShop(shopId)`, `eventId = Ids.newId()`. DLT 리플레이처럼 폐업 뒤에 개설 이벤트가 처음 처리되는 상황이다. 동작: `testKafka.send(ShopTopics.SHOP_OPENED, shopId.toString(), eventId, new ShopOpenedEvent(shopId, sellerId))` → `Eventually.await(Duration.ofSeconds(10), ...)` 안에서 `processedCountOf("member.shop-opened", eventId)` = 1이 될 때까지 기다린다.
- DB(`memberSnapshotOf(sellerId)`): `role()` = `USER`.

### 문서 반영

단계를 마칠 때 Claude가 고친다.

| 문서 | 고칠 곳 |
|---|---|
| [로드맵 3-2](../03-roadmap/part3-events.md) | 구현 순서, `ShopApi.countActiveShops` 설명, `onShopOpened`·`onShopClosed` 행, `MemberRoleService` 행과 그 아래 이유, `MemberRoleServiceTest`(3-1의 클래스를 다시 쓴다), `ShopOpenedFlowTest`에 추가, `CloseShopTest` 7번의 스파이 확인. 3-1은 그 단계가 만든 `grantSeller`를 그대로 둔다 |
| [상태 조합](../02-design/10-state-combinations.md) | `member.role` 행의 `grantSeller`를 `syncSellerRole`로 |
| [알려진 문제](../known-issues.md) | "SELLER 부여의 처리 순서" 행을 지운다 |

**리뷰 때 물어볼 것**

- 개설·폐업을 같은 메서드로 처리해도 되는 이유는? inbox가 늦게 온 개설 이벤트를 막지 못하는 이유는?
- 회원 행을 가게 수를 세기 전에 잠가야 하는 이유는? 순서를 바꾸면 7번은 어떻게 되나?
- 잠금이 없을 때 7번에서 개설 처리가 SELLER를 쓰지 않는 이유는?
