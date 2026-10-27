# Part 3. 이벤트

> 버전 0.71 · 2026-10-27 · **3-5 `dispatchBatch`의 try/catch 구조를 3-4 `payoutAll`과 같게 적었고, 같은 내용이 두 곳에 있던 것을 한 곳으로 합쳤다**(리뷰: 건별 try/catch 범위가 모호했다. 9) 테스트용 발송기 절, "헬퍼를 쓰는 방법"의 알림 행·보낸 메일 줄, `MailSender`·`dedup_key`·재시도 횟수 설명의 반복을 정리했다. 3-5 밖에서도 3-1 "10) 테스트 헬퍼" 절을 "테스트 준비" 표로 합치고 3-1~3-6의 "헬퍼를 쓰는 방법"이 되풀이하던 필드 목록, 3-2·3-3·3-4·3-6 구현 설명이 앞의 표·정책과 되풀이하던 문장을 정리했다): 안쪽 `try`는 `send`만, 바깥 `try`는 알림 한 건 전체를 감싼다. 가게 이메일을 바꾸는 `setShopEmail` 헬퍼 대신 `openShop`에 이메일을 받는 오버로드를 쓴다. 3번에 섞여 있던 "5분 뒤 재시도"와 "FAILED는 다시 보내지 않음" 확인을 7·8번 케이스로 나눴다. 3-4에 빠져 있던 `SettlementStatusTest`를 더했다(다른 상태 enum과 같이 전이표 전 쌍을 확인한다). 3-5 밖에서도 한 케이스에 섞인 서로 다른 확인을 나눴다(README 4.1의 5번. 원래 번호는 그대로 두고 뗀 확인은 클래스 끝 번호 뒤에 이었다): `OutboxPublisherTest` 3번(trace_id를 5번으로), `OutboxRelayTest` 1·4번(다시 발행하지 않음을 6·7번으로), `InboxGuardTest` 2번(다른 eventId를 6번으로), 상품 `EventPublishingTest` 4번(단종을 6번으로), order 이벤트 테스트 1·6·7·8·11번(13~17번), `SettlementServiceTest` 5·6번(10·11번), `SettlementControllerTest` 6번(7번), `WithdrawTest` 6번(10·11번)
> 0.70 · 2026-10-26 · **3-5 알림 본문 문구를 매핑 표에 정했다**(사용자 요청: 본문은 Claude가 채운다): 이벤트마다 "본문 예" 열을 더했고 테스트는 `isNotBlank()`만 확인한다
> 0.69 · 2026-10-26 · **3-5 가게 알림을 가게 이메일로 보내도록 바꿨다**(사용자 지적: 가게마다 이메일이 있다): `ShopApi.getOwnerId`를 `getContact(shopId)`(`ShopContact(ownerId, email)`)로 대체하고, `NotificationDraft`에 `shopId`를 더해 가게 알림은 `shopId`로 수신 대상을 지정한다. `Composer`는 가게주문마다 가게 draft를 만들고 주인 조회·목록 인자가 없어진다. `dedup_key`는 `eventId:수신 대상:type`이고 같은 판매자의 가게 둘이면 알림이 가게마다 간다. 매핑 표·Composer 테스트·`NotificationFlowTest` 1·5·6번과 `setShopEmail` 헬퍼를 맞췄다
> 0.68 · 2026-10-26 · **3-5 `SHOP_ORDER_RECEIVED` 제목에서 "상품 N개"를 뺐다**(사용자 지적: 판매자에게는 새 주문이 왔다는 알림이면 되고, 같은 판매자의 가게가 둘이면 `dedup_key`로 알림이 1건이라 개수가 첫 가게의 값일 뿐이다): `compose(OrderPaidEvent, List<UUID> ownerIds)`로 가게 주인 목록만 받고, consumer가 가게마다 구한 주인을 목록으로 넘긴다. 매핑 표·Composer 테스트 2건·`NotificationFlowTest` 1번의 제목을 맞췄다
> 0.67 · 2026-10-26 · **3-5 `NotificationStatus`에 `transitTo`와 전이표를 더했다**(사용자 지적: 상태 enum이 전이를 직접 검사하지 않는다. 개발 가이드 5.4는 엔티티 메서드로 상태를 바꾸는 enum에 전이표와 전이 테스트를 요구한다): `Notification.markSent`·`markFailed`가 `transitTo`를 부르고, 완료 확인에 `NotificationStatusTest`(9쌍)를 더했다. 구현 순서 줄의 번호 표기도 정리했다
> 0.66 · 2026-10-26 · **3-0~3-6 "할 일"을 Part 1·2처럼 계층별 절(`N) 모듈.계층`)과 `클래스 | 규격` 표로 통일했다**(사용자 지적: 로드맵 1·2와 모양이 달랐다). 규격·동작 변경 없음
> 0.65 · 2026-10-26 · **3-5 착수로 미결 행을 문서에 확정했다**: `Notification`을 3-4 정산과 같은 모양(JPA 엔티티, 중복 건너뛰기 INSERT만 native, `markSent`·`markFailed`로 결과 기록)으로 정해 저장소 규격을 적었고, 알림 제목 숫자는 천 단위 구분 기호 없이 쓰고, `getContact`의 회원 없음은 `MEMBER_NOT_FOUND`, 공통 규칙 S에서 notification의 `payment::api`를 빼고 order 행을 "Part 2와 같다"로 고쳤다. 3-5 클래스 표 2·3·7~11번과 `MemberContact`에 패키지를 적었다(사용자 지적: 3-4와 달리 위치가 빠져 있었다)
> 0.64 · 2026-10-26 · **3-5 `MemberContact.active`가 `BANNED` 회원도 `true`임을 정했다**(4-4의 제재 알림이 `enqueue`의 `active` 검사에 걸려 버려지지 않게). 0.63: **3-4b "7) 테스트 헬퍼" 절을 없애고 `FakePayoutGateway` 규격을 "테스트 준비" 표 한 곳으로 합쳤다**(사용자 지적: 같은 헬퍼 규격이 두 곳에 있고, 아직 나오지 않은 3-5의 `FakeMailSender`를 가리키는 문구가 있다). 같은 이유로 3-4b의 `LogMailSender`(3-5) 언급과 3-2 `processedCountOf`의 "쓰는 곳"에 적힌 뒤 단계 이름도 뺐다. 규격·동작 변경 없음. 0.62: **3-4b 착수로 미결 행(`SettlementItemRow`의 `Instant` 매핑)을 확인했다**(임시 통합 테스트로 native 인터페이스 프로젝션의 `timestamptz` → `Instant` 매핑과 keyset 다음 쪽 조회를 실행해 확인하고 테스트는 지웠다). 0.61: **3-4a 리뷰에서 `BusinessProperties`·`SettlementProperties` 규격에 `@Validated`를 명시했다**(개발 가이드 설정 규칙: `@ConfigurationProperties` + `@Validated` record). 0.60: **3-4a 테스트를 실행해 `@MockitoSpyBean`이 저장소(JDK 프록시)에 붙는 것을 확인하고 완료 확인 5번의 "실행해 본 것은 아니다" 문구를 뺐다**(`SettlementServiceTest`·`SettlementItemConsumerTest`·`SettlementPeriodTest` 11건 통과). 0.59: **공통 규칙 표의 "키" 행에 같은 파티션의 의미(순서·동시 처리 방지)와 하위 엔티티 이벤트가 집계 ID를 키로 쓰는 이유를 더했다**(사용자 질문: 품목 단위 확정 이벤트의 키가 왜 `orderId`인가). 0.58: **3-4a 할 일 끝의 "테스트 헬퍼" 안내 줄을 지웠다**(완료 확인이 바로 뒤에 있고 같은 내용이 "공통 준비"·"테스트 준비"에 있다). 0.57: **3-4a 완료 확인을 3-4b 할 일 앞으로 옮겼다**(사용자 지적: 3-4a 테스트가 3-4b 구현 뒤에 있다. 순서는 3-4a 할 일 → 3-4a 완료 확인 → 3-4b 할 일 → 3-4b 완료 확인). 0.56: **3-4a `settleShop`이 새로 만들었는지(`boolean`)를 돌려주도록 `run` 4)를 구체화했다**(`settledShops`가 `insertIfAbsent` 0행으로 건너뛴 가게를 세지 않게). 0.55: **3-4 착수로 미결 행을 확정했다**(사용자 결정 3건): ① `payoutAll()`은 `PayoutSummary(paid, payoutFailed)`를 돌려주고 `run`이 `SettlementRunResult`로 합친다(3-4a의 `run`은 `(n, 0, 0)`) ② `MockPayoutGateway`·`LogMailSender`에서 `@Profile("!prod")`를 뺀다(ops 실습 환경이 `prod` 프로필로 떠서 빈이 없으면 기동 실패) ③ `@MockitoSpyBean`은 JDK 프록시(Spring Data 저장소)에 붙는다(Spring Framework 소스로 확인, 실행은 하지 않음). 문서에서 확정한 것: `payoutAttempts` +1은 `markPayoutFailed` 안, `SettlementSum`은 인터페이스 프로젝션, `insertIfAbsent` 초깃값, `aggregateType = "SETTLEMENT"`, 목록 keyset 시그니처, 남의 가게 정산은 404, 공통 규칙 P에 `LocalDate` 허용. 3-4의 모든 클래스에 패키지를 적었다. 3-4b 내용(의존성·`run` 3번)은 3-4a 절에서 빼고 3-4b 절 5) 표 7번으로 옮겼다(사용자 지적: 일부만 적혀 있었다. 기존 모듈 규칙: 엔티티·enum·값 객체·저장소 인터페이스·에러 코드는 `domain`, 서비스·설정은 `application`, Jpa 저장소·잡·consumer·토픽 설정은 `infrastructure`, 컨트롤러·응답은 `web`). 조회 서비스 `SettlementQueryService`를 더했다(사용자 요청: 기존 모듈처럼 컨트롤러가 저장소를 직접 부르지 않는다). 엔티티 없는 `JpaSettlementItemRepository`는 `@RepositoryDefinition`으로 등록한다(사용자 결정, 리뷰에서 빈이 만들어지지 않는 것을 지적)
> 0.54 · 2026-10-23 · **3-3 완료 확인의 order 테스트를 흐름별 세 클래스로 나눴다**(사용자 지적: 한 클래스가 서비스 11개를 주입받는다): `PaymentEventPublishingTest`(결제 완료·만료·실패, 1~5번), `FulfillmentEventPublishingTest`(발송·배송완료·환불, 6~8번), `ConfirmEventPublishingTest`(구매확정, 9~12번). 케이스 번호와 규격은 그대로다
> 0.53 · 2026-10-23 · **3-3 완료 확인에서 겹치는 롤백 케이스 셋을 뺐다**(사용자 요청, README 4.1 기준): member 2번(중복 이메일 가입), product 6번(단종 상품 수정), order 12번의 재고 부족 항목. 예외가 나면 트랜잭션이 롤백되는 것은 Spring 동작이고 이 단계가 확인할 것은 order 12번의 "발행 실패 → 상태 변경 취소" 하나다. 상품 수정·상태 변경에서 `PATCH`를 쓰는 케이스가 없어졌다
> 0.52 · 2026-10-23 · **3-3 완료 확인 order 12번의 발행 실패 주입 대상을 `OutboxPublisher`에서 `OrderEventPublisher.lineConfirmed`로 바꿨다**(구현 중 확인): `OutboxPublisher.publish`는 `@Transactional(MANDATORY)` 프록시라 테스트에서 `doThrow(...).when(outboxPublisher).publish(...)`를 쓰는 순간 트랜잭션 없음 예외(`IllegalTransactionStateException`)가 난다. 롤백 검증 의도는 같다
> 0.51 · 2026-10-23 · **3-3 완료 확인에서 상품 수정·상태 변경 케이스(상품 2~4번)를 `PATCH` 대신 `TestFixtures` 헬퍼(`UpdateProductService` 호출)로 바꿨다**(사용자 요청: 응답을 확인하지 않는 케이스는 서비스 호출로 충분하고, 서비스 주입은 테스트가 아니라 `TestFixtures`가 한다). 응답 코드를 확인하는 롤백(6번)만 `PATCH`로 남기고, 2번의 "응답 200"을 뺐다. 규격·동작 변경 없음
> 0.50 · 2026-10-23 · **3-3에 `ProductEventPublisher`를 정했다**(사용자 결정: 상품 이벤트 조립 코드가 등록·수정·상태 변경·가게 폐업 단종 네 곳에서 같아 한 곳에 모은다): 구현 순서 ③에 더하고, 발행 위치 3)에 메서드 3개(`upserted`·`priceChanged`·`discontinued`)와 `flush()`는 서비스에 남긴다는 규칙을 적었다. member는 그대로 서비스에서 `OutboxPublisher`를 부른다. 이벤트 규격 변경 없음
> 0.49 · 2026-10-23 · **3-3 구현물 5번의 `lineConfirmed` 인자를 `(order, shopOrder, line)`으로 맞췄다**(구현 중 확인): `shipped`·`delivered`와 같이 가게주문을 받는다. 규격·동작 변경 없음
> 0.48 · 2026-10-23 · **3-3 외 구간의 다른 단계 참조를 그 자리에서 읽히게 풀어 썼다**(사용자 지적): 공통 규칙 Q의 두 설정, 3-1의 `JobRunner`·`memberSnapshotOf`(9필드)·`countOf`, 3-2의 `existsByShopIdAndStatusIn` JPQL·`shopSnapshotOf`·`productSnapshotOf`, 3-4b의 keyset 페이징, 3-5의 native 저장소·`OrderSnapshot.status`·이벤트 record 9개의 필드 순서, 3-6의 `MemberTopicConfig` 빈 추가·발송 검사 집합·`AddressState`·`CartSnapshot`, 각 단계의 "이미 있는 헬퍼"를 시그니처와 동작으로. 규격·동작 변경 없음
> 0.47 · 2026-10-23 · **3-3 구현 순서를 바로잡았다**(사용자 지적: 만들지 않은 `OrderEventPublisher`를 부르는 단계가 앞에 있었다): 구현물 4·5번(`Order.autoConfirm` 반환 타입 변경, `ConfirmPurchaseService`의 `lineConfirmed` 호출)은 `lineConfirmed`가 있어야 하고 4번은 반환 타입만 바꾸면 `AutoConfirmService`가 컴파일되지 않으므로 ②에서 빼고 ④의 구매확정 행으로 옮겼다. 규격·동작 변경 없음
> 0.46 · 2026-10-23 · **3-3의 다른 단계 참조를 현재 코드로 풀어 썼다**(사용자 지적): 구현물 표 2번(`.dlt` 토픽 만드는 모양), 4번("int, 2-10"), `closed`를 부르는 위치("2-5의 3번 다음" → `expireOne`의 `cancelPendingChildren()` 다음), 헬퍼 표의 `ProductSnapshot` 필드(`priceChanges` 정의를 "1-8 표"에서 가져오던 것을 레코드·쿼리까지 적음), 이미 있는 헬퍼 목록과 `snapshotOf`의 출처 표기(2-8a·2-9·2-10). 규격·동작 변경 없음
> 0.45 · 2026-10-23 · **3-3 구현물 표 5번이 2-10을 가리키지 않게 현재 코드로 풀어 썼다**(사용자 지적: 2-10을 또 확인해야 한다). 규격·동작 변경 없음
> 0.44 · 2026-10-23 · **3-2 구현물 표 2번의 "확인 필요"를 확정했다**(3-3 착수): `existsByShopIdAndStatusIn`의 `@Query`는 `JpaOrderRepository`에 있고 `ShopOrderQueryRepository`는 만들어지지 않았다. 규격·동작 변경 없음
> 0.43 · 2026-10-23 · **3-2 `CloseShopTest` 6번의 확인 2 설명을 바로잡았다**(사용자 지적: 낙관적 락 충돌이 날 일이 없다): 이 케이스가 확인하는 것은 처리 시점에 활성 가게 수를 다시 센다는 점이고, 충돌이 안 나는 이유는 리스너 스레드가 하나라서이다. 규격·동작 변경 없음
> 0.42 · 2026-10-23 · **3-2 `CloseShopTest` 5번에서 `eventIdB`를 읽는 시점을 바로잡았다**(사용자 지적: A만 폐업한 시점에 B의 이벤트가 있을 수 없다): `eventIdA`는 동작 1에서, `eventIdB`는 B를 폐업한 동작 2에서 읽는다. 규격·동작 변경 없음
> 0.41 · 2026-10-23 · **`AlwaysFailListener`의 호출 횟수 세기를 예와 함께 풀어 썼다**(사용자 지적: 무슨 말인지 모르겠다). 규격·동작 변경 없음
> 0.40 · 2026-10-23 · **헬퍼 표의 남은 시그니처를 맞췄다**: 변경 행 2개의 인자 타입과 3-1 `memberSnapshotOf` 행의 코드 상태 문구. 규격·동작 변경 없음
> 0.39 · 2026-10-23 · **테스트 준비 헬퍼 표의 시그니처를 타입까지 적었다**(사용자 지적: 어떤 메서드를 어디에 만드는지 안 보인다): 매개변수 타입과 반환 타입(`void` 포함)을 붙였고, 클래스·record만 적혀 있던 행(`OutboxSnapshot`·`TestKafka`·`ReceivedMessage`·`TestKafkaTopics`·`IntegrationTestSupport.kafka`·`FakePayoutGateway`·`FakeMailSender`/`SentMail`·`@AfterEach` 행 둘)은 public 메서드·record 필드 시그니처를 만드는 방법 칸에 적었다. 규격·동작 변경 없음
> 0.38 · 2026-10-23 · **`AlwaysFailListener` 표 행에 메서드 시그니처를 적었다**(사용자 지적: 어떤 메서드를 만드는지 안 보인다): 리스너 `onMessage`와 `attempts(String key)`, 의존성·필드, ①이 ②보다 먼저인 이유. 규격·동작 변경 없음
> 0.37 · 2026-10-23 · **`AlwaysFailListener` 만드는 방법을 헬퍼 표로 올렸다**(사용자 지적: 표가 아래 `KafkaDltTest` 준비를 가리켰다): 리스너 설명을 표 행으로 옮기고 `KafkaDltTest`의 준비는 그 리스너를 쓴다고만 적었다. 규격·동작 변경 없음
> 0.36 · 2026-10-23 · **3-2 헬퍼 표 "대상" 칸에 어느 파일에 만드는지 적었다**(사용자 지적: `processedCountOf`·`AlwaysFailListener`를 어디에 두는지 알 수 없다): `TestKafka`·`TestFixtures`에 메서드를 더하고 `AlwaysFailListener`는 새 파일로 만든다. 규격·동작 변경 없음
> 0.35 · 2026-10-23 · **3-0~3-6 나머지 단계도 완료 확인의 "테스트 준비"를 테스트 클래스 앞으로 옮겼다**(사용자 요청): 3-1·3-3·3-4a·3-4b·3-5·3-6의 헬퍼 표와 이미 있는 헬퍼를 "### 완료 확인" 바로 아래로 올리고 도입부의 "읽는 방법"은 표 뒤 "헬퍼를 쓰는 방법"으로 두었다. 표에 같은 내용이 있던 문장(3-1 회원 헬퍼 추가 안내, 3-4b `reset()`, 3-5 `setNotificationsDue()`·`reset()`)은 표 행에 흡수했다. 3-0은 헬퍼가 없어 그대로 둔다. 규격·동작 변경 없음
> 0.34 · 2026-10-23 · **3-2의 "읽는 방법"과 "테스트 준비"를 합쳐 완료 확인 맨 앞으로 옮겼다**(사용자 지적: 헬퍼 설명이 테스트보다 뒤에 있다): 헬퍼 표와 쓰는 방법을 테스트 클래스 앞에 두고 중복이던 읽는 방법 항목(`processedCountOf`·스냅샷·`send`)은 표로 합쳤다. 다른 단계 구조는 그대로. 규격·동작 변경 없음
> 0.33 · 2026-10-23 · **`TestKafka.send`의 `json`이 어디서 나오는지 적었다**("읽는 방법"과 헬퍼 표 모두): 봉투를 `objectMapper.writeValueAsString`으로 문자열로 만들어 보낸다(`KafkaTemplate<String, String>`). 규격·동작 변경 없음
> 0.32 · 2026-10-23 · **consumer 클래스 이름을 `MemberShopEventConsumer`·`ProductShopEventConsumer`로 바꿨다**: member의 `ShopEventConsumer`와 같은 이름이면 빈 이름 충돌로 앱이 기동하지 않는다(3-2 구현 중 `ShopControllerTest`로 확인). 규격·동작 변경 없음
> 0.31 · 2026-10-22 · **3-2 착수 전 확인 필요를 풀었다**: `InboxGuard.runOnce`는 MDC를 따로 설정하지 않고 `EventContext.run` 안에서 실행한다(중복 제거). 규격·동작은 0.30과 같다
> 0.30 · 2026-10-22 · **3-1 리뷰를 반영했다**: 3-2 전까지 consumer 예외는 기본 핸들러가 10번 즉시 재시도한 뒤 건너뛴다는 실제 동작(깨진 JSON으로 확인)을 3-1 consumer 설명에 적었다. 그 밖의 규격·동작은 0.29와 같다
> 0.29 · 2026-10-22 · **테스트 헬퍼 두 가지를 코드에 맞췄다**: `TestKafka`가 읽는 `spring.kafka.bootstrap-servers`가 컨테이너가 아닌 `localhost:9092`라서 `IntegrationTestSupport`의 `@DynamicPropertySource`에 주소를 더하게 했고, `outboxSnapshotOf`는 `OutboxAssert`에 두는 것으로 정리했다. 그 밖의 규격·동작은 0.28과 같다
> 0.28 · 2026-10-22 · **3-1의 `memberSnapshotOf`에서 `withdrawnAt`을 3-6으로 옮겼다**: `member.member.withdrawn_at` 컬럼은 3-6 마이그레이션이 만들어 3-1에서 읽으면 SQL이 실패한다. 3-1은 `role`·`status`·`email`만 더한다. 그 밖의 규격·동작은 0.27과 같다
> 0.27 · 2026-10-21 · **outbox 시각을 앱 `Clock`으로 통일했다**: 릴레이 SQL의 `now()`를 `Clock`에서 얻은 파라미터로 바꿔 `OutboxPublisher`와 다른 job과 같은 시계를 쓴다(릴레이도 `JobRunner`로 한 서버만 돌아 DB 시각이 필요 없다). `OutboxRelay` 의존성에 `Clock` 추가. 그 밖의 규격·동작은 0.26과 같다
> 0.26 · 2026-10-21 · **릴레이에서 `TransactionTemplate`을 뺐다**: `OutboxRelay`의 회수·선점·결과 기록이 모두 `UPDATE` 한 문장이라 트랜잭션 없이 한 문장씩 커밋한다. 그 밖의 규격·동작은 0.25와 같다
> 0.25 · 2026-10-21 · **3-1 착수 전 확인 필요를 풀었다**: 릴레이는 한 행이 발행에 실패하면 그 호출의 남은 행을 시도하지 않고 되돌린다(사용자 결정, `OutboxRelayTest` 5번 추가), Kafka 환경변수 이름·Jackson 3 `treeToValue`·`@NamedInterface`의 하위 패키지 처리(`api/event`에도 `package-info.java` 필요)·봉투 `occurredAt`을 확인해 확정했다
> 0.24 · 2026-10-21 · **3-0을 서명 주소 하나로 줄였다**: 프록시 뒤 POST 403을 `server.forward-headers-strategy: native`로 풀던 것을 `CORS_ALLOWED_ORIGINS`에 공개 주소를 넣는 방식으로 바꿨다. CORS 설정이 없는 경로는 Spring이 검사 없이 통과시키고(`DefaultCorsProcessor`), `src/main`에 요청 scheme에 기대는 코드(리다이렉트·`Secure` 쿠키·절대 URL 생성)가 없어 `native`가 풀어 줄 것이 없었다. 코드 할 일에서 `application-prod.yaml` 설정이, 완료 확인에서 `ForwardedHeadersTest`가 빠졌다
> 0.23 · 2026-10-21 · **Part 3 앞에 3-0(외부 공개용 설정)을 더했다**: 도메인(`myroutine.dev`)을 구입해 Cloudflare Tunnel로 외부에 공개하기로 해(ADR-011 "외부 접속" 갱신), HTTPS 프록시 뒤에서 POST가 403 `Invalid CORS request`가 되는 문제와 업로드 URL의 서명 호스트 문제를 푸는 설정 두 가지를 단계로 올렸다. 값을 넣기 전에는 동작이 같은 설정 추가라 Part 3 본문(3-1~3-6)은 바꾸지 않았다
> 0.22 · 2026-10-20 · **Part 2 구현에 맞춰 다듬었다**: Part 3이 가리키는 Part 1·2 클래스·메서드·`TestFixtures` 헬퍼를 코드와 대조해 모두 있음을 확인했다(없는 이름은 모두 Part 3이 새로 만드는 것). 공통 규칙 Q에 테스트 중 스케줄러를 끄려면 두 설정이 모두 필요하다는 근거(Part 2 규칙 M)를 이었고, 3-1의 `memberSnapshotOf`가 코드에 아직 없어 이 단계에서 추가한다는 점을 본문에도 맞췄다. 규격 외 변경 없음
> 0.21 · 2026-10-20 · **4.1 기준 점검에서 '하' 후보를 권장안대로 정리했다**(사용자 요청): 3-4a의 `netAmount` 공식 재서술, 3-4b의 정산 건수 재확인·`payoutFailed`, 3-4b 컨트롤러 5번의 정산 건수 0, 3-6 재가입의 `$.memberId` 비교·`reasons` 개수·성공 케이스의 `WITHDRAWN` 재확인. 규격 외 변경 없음
> 0.20 · 2026-10-20 · **4.1 기준 점검에서 '중' 후보를 뺐다**(사용자 요청): 3-1·3-3의 outbox `eventType`·`status`(3-1 `OutboxPublisherTest`가 확인), 3-2 `InboxGuardTest`·3-4a·3-5의 `processedCountOf`와 초기값(`channel`·`attempts`·`sentAt`), 3-3 결제 전 취소의 `$.status`·롤백 케이스의 이벤트 0건/`confirmedAt`, 3-4a의 `periodStart`·가게 수·`net_amount`, 3-4b의 `payoutAttempts`·`lastError`·0원 정산의 `gross`·`net`, 3-5의 재고·발송 실패 반환값. 규격 외 변경 없음
> 0.19 · 2026-10-20 · **4.1 기준 점검에서 확신 '상'을 뺐다**(사용자 요청): 3-1 `OutboxPublisherTest` 3번과 3-4a 소비 1번의 `id()` not null, 3-2 `InboxGuardTest` 1번의 `processed_at` not null(NOT NULL 컬럼), 3-4a 3번의 "예외 없이 끝난다"(반환값을 읽는다), 3-5 2번의 알림 행 총 5개와 5·6번의 개수 중복. 규격 외 변경 없음
> 0.18 · 2026-10-20 · **3-2 `CloseShopTest` 3번에서 준비가 만든 `product3`의 `DISCONTINUED` 확인을 뺐다**(사용자 요청). `product3`은 이미 단종된 상품이 있어도 폐업이 예외 없이 끝나는지 보는 준비로 남는다. 규격 외 변경 없음
> 0.17 · 2026-10-20 · **3-3·3-5·3-6 완료 확인에서 '중' 후보를 뺐다**(사용자 요청): 상품 등록 이벤트의 가격 변경·단종 0건, 발송·배송완료의 `shipped` 1건, 알림 흐름 4번의 `SENT`, `MemberTest`의 `role`, 탈퇴 전 배송지 수와 거부 케이스의 `before` 초기값. 규격 외 변경 없음
> 0.16 · 2026-10-18 · **읽기 헬퍼 정의를 단계 사이에서 맞췄다**: Part 1·2에서 정의한 `memberSnapshotOf`·`countOf`(3-1), `shopSnapshotOf`·`productSnapshotOf`(3-2), 가격 이력(3-3), 배송지·`cartSnapshotOf`(3-6)를 처음 쓰는 단계의 `TestFixtures` 추가 행으로 옮기고, 3-3의 `orderLineConfirmedAtOf`를 2-10의 `OrderSnapshot.LineState.confirmedAt`으로 합쳤으며, 3-5의 주문 `status` 확인을 `snapshotOf`로 바꿨다. 규격 변경 없음
> 0.15 · 2026-10-18 · **완료 확인에서 값을 읽는 방법을 읽기 헬퍼로 올리고 중복 확인을 걷어냈다**(사용자 지적: 확인할 값마다 JDBC 쿼리·`Map` 변환·캐스팅 코드를 테스트마다 짜야 해서 비효율): outbox 행은 `OutboxAssert.rows`·`outboxSnapshotOf`(`OutboxSnapshot`), Kafka 메시지는 `TestKafka.messages`(`ReceivedMessage`), 회원·가게·상품은 Part 1의 `memberSnapshotOf`·`shopSnapshotOf`·`productSnapshotOf`에 필드(`role`·`status`·`email`·`withdrawnAt`, `closedAt`, `version`·가격 이력 `changedAt`)를 더해, 정산은 `settlementSnapshotOf`·`settlementItemSnapshotOf`, 알림은 `notificationRowsOf`(`NotificationRow`)로 한 번 읽고 단언을 한 줄로 쓴다. 주문·결제는 2-8a의 `snapshotOf(orderId)`, 장바구니는 2-1의 `cartSnapshotOf`, 행 수는 1-3의 `countOf`를 쓴다. `processed_message`는 `processedCountOf`, 역할 변경은 `changeRole`, 알림 재시도 시각 당기기는 `setNotificationsDue`로 올렸다. 각 단계 "이 단계에서 추가하는 헬퍼" 표에 읽는 테이블·컬럼과 반환 필드를 적었다. 같은 클래스에서 값이 목적이 아닌 케이스가 되풀이하던 확인(3-2 `CloseShopTest` 3·7번의 역할·단종 결과, 3-5 4번의 FAILED 재확인)과 어느 케이스도 쓰지 않던 읽기(3-3 `orders.orders`의 `status`·`failure_reason`)를 지웠다. 부정 확인과 구체 수치는 그대로이고 규격·동작 변경 없음
> 0.14 · 2026-10-18 · **3-1~3-6의 완료 확인을 케이스마다 준비·동작·확인할 값으로 다시 썼다**(사용자 지적: 무엇을 확인하는지 모호하다): 값을 읽는 방법(outbox 행과 payload 필드, Kafka 메시지, `processed_message`, 알림 행, 응답 jsonPath), 공통 준비 수치, 케이스별 응답 코드·DB 컬럼·토픽·키·payload 값, 중복 전달·재처리가 남겨야 하는 정확한 건수, 부정 확인(0건·그대로)을 적었다. 3-4는 3-4a·3-4b로 나눴고, 센티널 메시지로 중복 소비를 확인하는 방법과 테스트 헬퍼(`OutboxAssert.clear`·`publishReady`, `TestKafka`, `TestKafkaTopics`, `insertSettlementItem(s)`, `FakePayoutGateway` 호출 기록)를 정했다. 규격에 없던 값은 "확인 필요"로 남겼다. 규격 외 변경 없음. 케이스를 더한 곳: 3-1 `MemberRoleServiceTest`·`OutboxPublisherTest` 4번·`OutboxRelayTest` 3번 일부, 3-2 `InboxGuardTest` 2·4·5번·`CloseShopTest` 2·4번과 `revokeSellerIfNoActiveShop`, 3-3 롤백(발행 실패) 등, 3-4 늦게 온 품목·0원 정산·한 가게 실패·컨트롤러 조회/관리자 성공, 3-5 같은 판매자 가게 둘·탈퇴 회원, 3-6 환불 경계·동시 탈퇴 등
> 0.13 · 2026-10-16 · **쓰지 않는 값과 불필요한 단계를 걷어냈다**(리뷰 지적): 3-1 `relayOnce`의 회수와 선점을 한 트랜잭션으로 묶었고, 3-3 구매확정 이벤트가 2-10 3번에서 이미 찾은 품목을 넘겨 쓰게 했으며, 3-4 `insertIgnoringConflict`·`assign`의 쓰지 않는 반환값과 3-5 `MemberContact`의 `memberId`·`nickname`을 지웠다. 그 밖의 규격·동작은 0.12와 같다
> 0.12 · 2026-10-16 · **"할 일"을 구현 순서대로 다시 놓고 설명 속에 숨어 있던 구현물을 표로 올렸다**(구현 중 지적): 3-1~3-6 "할 일" 맨 앞에 구현 순서 한 줄을 더했다. 3-1 `OutboxRelayJob`·`TypedEnvelope`, 3-2 폐업 구현물 14행(`ShopApi` 3개·저장소 조회·DLT 토픽·소비 클래스, 존재하지 않는 `shopOrderRepository`를 `OrderRepository`로), 3-3 구현물 표(`order/api` 패키지·토픽 설정 3개·`ProductRepository.flush`·`autoConfirm` 반환 변경), 3-4 `SettlementConfig`·`SettlementSum`·`SettlementItemService`·consumer·`SettlementService`·`SettlementJob`·package-info 행과 3-4b 이벤트·조회·오류 코드 표(`myroutine.business-zone`은 이미 `application.yaml`에 있음을 반영), 3-5 클래스 표 11행(저장소·package-info 포함)과 설명 순서, 3-6 구현물 15행(저장소 조회·장바구니 삭제·토픽 설정)을 적었다. 같은 설명이 반복된 곳(`EventContext`·`InboxGuard` MDC, `flush`·`version`, 가게 주인 조회, `MANDATORY`)은 한 곳만 남겼다. 규격·동작은 0.11과 같다
> 0.11 · 2026-10-15 · **`EventPublishingTest` 대상에서 payment를 뺐다**(리뷰 결정): 3-3 이벤트 표에 payment가 발행하는 이벤트가 없다. 그 밖의 규격은 0.10과 같다
> 0.10 · 2026-10-15 · **Part 3 클래스·메서드 설명을 풀어 썼다**(구현 중 "뭔지 모르겠다" 지적): 표의 한 줄 규격이던 클래스·메서드마다 역할·호출하는 곳·입력과 반환·순서·함정을 적었다(3-1 outbox·릴레이·consumer, 3-2 멱등 소비·에러 핸들러·폐업, 3-3 이벤트 발행 위치, 3-4 정산·지급, 3-5 알림, 3-6 탈퇴). 문서에 정해져 있지 않던 부분은 "확인 필요"로 표시했다. 규격·동작은 직전 버전과 같다
> 0.9 · 2026-10-14 · **표현·구조 정리**(설계·동작 변경 없음): 3-3 `OrderEventPublisher`와 3-4 `SettlementPaidEvent`를 쓰는 표·순서 앞에서 정의하고, 3-4 `SettlementSum`·`SettlementItemService`를 정의했으며, 한 셀에 몰려 있던 저장소 메서드를 목록으로 풀었다
> 0.8 · 2026-10-12 · **용어 정리**: 단계 끝 "제안 (선택)"을 "추가 항목"으로 바꿨다. "제안"은 리뷰어의 의견(근거 없는 지적)에만 쓴다. 내용은 그대로다
> 0.7 · 2026-10-11 · JSON 변환 타입을 `ObjectMapper`로 통일(Part 1 코드·Part 2 멱등 장치와 같은 타입)
> 0.6 · 2026-10-09 · **사실 확인·정합**: Kafka 이미지 태그(`apache/kafka:4.2.2`)·Boot 4 스타터 이름·`CommonErrorHandler` 자동 연결을 확인해 확정, 테스트 컨테이너 기동에 1-10의 MinIO 포함, 정산 테이블에 빠져 있던 `payout_reference` 컬럼과 `markPaid` 인자 통일, Part 2에서 제안으로 옮긴 `FailPaymentService` 잔재 제거
> 0.5 · 2026-10-02 · **덜어내기**: 가게 개설·폐업 advisory lock 제거(재조회로 충분), 폐업 이벤트의 남은 가게 수 필드 제거, 재시도 간격은 고정값으로(백오프는 제안), 회원 역할은 단일 `role`
> 0.4 · 2026-10-02 · 예치금 제거: 정산 지급은 `PayoutGateway`(은행 송금 Mock), 충전 이벤트·지갑 탈퇴 조건 삭제
> 0.3 · 이 문서만 보고 개발할 수 있게 구체화

> **끝나면**: 가게 개설·폐업에 따른 역할 변경, 정산, 알림 같은 **후속 작업**이 비동기로 처리된다. 서버가 죽거나 Kafka가 멈춰도 이벤트가 유실되지 않고, 두 번 전달돼도 결과는 한 번이다.
> **인프라 추가**: **Kafka**
> **릴리스**: `v0.3.0`

| 단계 | 제목 | 크기 |
|---|---|---|
| 3-0 | 외부 공개용 설정 (업로드 URL 서명 주소) | S |
| 3-1 | 이벤트가 필요한 이유 · Kafka · Outbox (가게 개설 → SELLER 역할) | L |
| 3-2 | 멱등 소비 · 재시도 · DLT · 가게 폐업 | M |
| 3-3 | 기존 흐름에 이벤트 발행 붙이기 | M |
| 3-4 | 정산: 적재 · 정산 잡 · 지급 | L |
| 3-5 | 알림 | M |
| 3-6 | 회원 탈퇴 | M |

---

## Part 3 공통 규칙 (Part 1·2 공통 규칙에 더해서)

> [Part 1 공통 규칙](part1-foundation.md#part-1-공통-규칙-모든-단계에-적용), [Part 2 공통 규칙](part2-order-money.md#part-2-공통-규칙-part-1-공통-규칙에-더해서)은 그대로 적용된다. 리뷰 기준도 같다.

### P. 이벤트 작성 규칙

| 항목 | 규칙 |
|---|---|
| 위치 | 이벤트 record와 토픽 상수는 **발행하는 모듈**의 `{module}.api.event` 패키지. 구독하는 모듈은 이것을 import한다(그래서 구독자는 발행자의 `api`에 의존한다) |
| 토픽 상수 | `public final class OrderTopics { public static final String ORDER_PAID = "order.order-paid.v1"; ... }` — 이름 규칙 `{module}.{event}.v{n}` |
| 이벤트 record | 이름은 과거형 `XxxEvent`. 필드는 원시 타입·`UUID`·`String`·`long`·`Instant`·`LocalDate`·`List`만(엔티티·`Money` 금지, 금액은 `long`). `LocalDate`는 정산 기간처럼 시각 없는 날짜에만 쓰고 JSON에서는 `2026-09-01` 문자열이 된다(3-4b 완료 확인 6번이 확인) |
| 키 | 집계 ID(aggregateId). 같은 집계의 이벤트는 같은 파티션으로 가서 순서가 지켜진다. 같은 consumer 그룹에서 한 파티션은 consumer 하나가 맡으므로 같은 집계의 이벤트가 동시에 처리되지도 않는다. 집계 안의 하위 엔티티 이벤트(주문 품목의 `ORDER_LINE_CONFIRMED`)도 하위 엔티티 ID가 아니라 집계 ID(`orderId`)를 키로 쓴다. 품목 ID를 키로 쓰면 한 주문의 이벤트가 여러 파티션으로 흩어져 순서와 동시 처리 방지가 사라진다. 순서가 필요 없는 consumer(정산 적재: 품목마다 독립이고 중복은 `InboxGuard`와 유니크 제약이 막는다)도 발행하는 쪽이 정한 키를 그대로 받는다 |
| 발행 | **상태 변경과 같은 트랜잭션** 안에서 `OutboxPublisher.publish(...)`. `KafkaTemplate`을 비즈니스 코드에서 직접 부르지 않는다 |
| 소비 | 모든 consumer는 `InboxGuard.runOnce(...)` 안에서 처리한다(3-2부터). 같은 이벤트를 두 번 받아도 결과가 한 번 |
| 직렬화 | Kafka 메시지 값은 **JSON 문자열**(`StringSerializer`). 봉투 → JSON 변환은 주입받은 `ObjectMapper`(`tools.jackson.databind.ObjectMapper`)로 직접 한다(Kafka용 Jackson 직렬화 설정을 따로 다루지 않기 위해) |
| 문서 | 새 이벤트·필드는 [이벤트 카탈로그](../02-design/05-events.md)와 일치해야 한다(Claude가 리뷰 때 문서를 맞춘다) |

### Q. 비동기 테스트
- Kafka는 Testcontainers로 띄운다(3-1). 스케줄러가 꺼져 있으므로 **릴레이는 테스트에서 직접 호출**한다(`outboxRelay.relayOnce()`). 스케줄러를 끄는 데는 `application-test.yaml`의 두 설정이 모두 필요하다: `myroutine.scheduling.enabled: false`(우리 `SchedulingConfig`를 끈다)와 `spring.modulith.moments.enabled: false`(`@EnableScheduling`을 가진 Spring Modulith `MomentsAutoConfiguration`을 끈다. [Part 2 공통 규칙 M](part2-order-money.md#m-시간-흉내-내기)). 하나라도 빠지면 1초마다 도는 `OutboxRelayJob`이 테스트 중에 행을 먼저 발행해 `relayOnce()`의 반환값과 outbox 상태 확인이 어긋난다. consumer(`@KafkaListener`)는 테스트 중에도 동작한다.
- "잠시 후 반영"은 직접 만든 헬퍼 `support/Eventually.await(Duration timeout, ThrowingRunnable assertion)`로 기다린다(동작은 3-1 "테스트 준비"). 새 라이브러리(Awaitility)는 쓰지 않는다.
- 이전 테스트가 남긴 메시지가 다음 테스트에서 소비될 수 있다(테이블은 비워졌는데 메시지는 남음). consumer는 **대상이 없으면 경고 로그 후 정상 종료**해야 한다(재시도 대상이 아님).

### R. Part 3 에러 코드

| 코드 | HTTP | 메시지 | 정의 위치 | 단계 |
|---|---|---|---|---|
| `SHOP_HAS_ACTIVE_ORDERS` | 422 | 진행 중인 주문이 있어 폐업할 수 없습니다. | `OrderErrorCode` | 3-2 |
| `SETTLEMENT_NOT_FOUND` | 404 | 정산 내역을 찾을 수 없습니다. | `SettlementErrorCode` | 3-4 |
| `MEMBER_HAS_ACTIVE_RESOURCES` | 422 | 탈퇴할 수 없는 상태입니다. (details.reasons) | `MemberErrorCode` | 3-6 |

### S. 모듈 의존 (Part 3 끝 기준, Part 2 표에 추가·변경된 것만)

| 모듈 | `allowedDependencies` | 추가 이유 |
|---|---|---|
| member | `common`, `shop::api` | shop 이벤트 구독, 활성 가게 수 조회 |
| product | `common`, `shop::api` | shop-closed 구독 (변경 없음) |
| order | Part 2와 같다 (`common`, `product::api`, `shop::api`, `member::api`, `payment::api`) | 변경 없음. `order::api`를 공개하기 시작할 뿐(이벤트·`OrderTopics`) order 자신의 의존은 늘지 않는다 |
| settlement | `common`, `order::api`, `shop::api`, `member::api` | 이벤트 구독, 내 가게 목록 조회, 탈퇴 조건 구현 |
| notification | `common`, `member::api`, `shop::api`, `order::api`, `settlement::api` | 이벤트 구독, 수신자 연락처·가게 주인 조회 (payment는 이벤트를 발행하지 않고 알림 대상도 아니라 의존하지 않는다) |

- **순환 금지**: member가 shop을 참조하므로 shop은 member를 참조할 수 없다. 그래서 "운영 중인 가게가 있으면 탈퇴 불가"는 shop이 `WithdrawalPrecondition`을 구현하지 않고 **member가 `ShopApi.countActiveShops`로 직접 확인**한다.

---

## 3-0. 외부 공개용 설정 (S)

**목표**: HTTPS 리버스 프록시(Cloudflare Tunnel → nginx) 뒤에서 상품 이미지 업로드 URL이 브라우저가 닿는 주소로 발급된다. 같은 환경에서 로그인·주문 같은 POST가 막히지 않게 하는 일은 코드가 아니라 운영 `.env`의 `CORS_ALLOWED_ORIGINS`로 한다(운영 반영 순서 2번). 새 설정 값을 넣기 전에는 지금과 똑같이 동작한다.

**왜 지금**: 외부 공개([ADR-011](../adr/ADR-011-ops-practice-environment.md) "외부 접속")가 이 설정을 기다린다. 기존 동작을 바꾸지 않는 설정 추가라서 Kafka가 들어오는 3-1 전에 끝내 두면, 이후 배포에서 공개 설정 문제와 이벤트 문제가 섞이지 않는다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 오리진 검사 | Spring은 요청에 `Origin` 헤더가 있으면 그 값(scheme·host·port)을 요청을 받은 주소와 비교한다. 같으면 통과하고, 다르면 CORS 요청으로 보고 `CORS_ALLOWED_ORIGINS`에 있는 오리진만 통과시킨다. 프록시 뒤에서는 앱이 요청을 내부 주소의 `http`로 받는데 브라우저는 `Origin: https://{공개 주소}`를 붙여 보내므로 달라 보이고, 목록에 없으면 `/api/**`의 POST가 403 `Invalid CORS request`가 된다. GET은 `Origin` 헤더가 없어 통과한다. 목록에 공개 주소를 넣으면 풀린다 |
| 서명 호스트 | presigned URL은 호스트를 서명에 넣는다. URL을 받아 쓰는 브라우저가 닿는 주소로 서명해야 하고, 서버가 MinIO를 부르는 주소와 달라질 수 있다 |

**정책 (이 단계에서 정함)**
- `STORAGE_PRESIGN_ENDPOINT`가 비어 있으면 `STORAGE_ENDPOINT`로 서명한다(지금 동작). 값이 있으면 **`S3Presigner`만** 그 주소로 서명한다. `S3Client`(버킷 초기화, 객체 확인·삭제)는 언제나 `STORAGE_ENDPOINT`로 MinIO에 직접 간다.
- 서명 주소를 `STORAGE_ENDPOINT` 하나로 합쳐 공개 주소로 바꾸지 않는다. 서버의 MinIO 호출까지 인터넷과 프록시를 돌게 되고, 프록시가 읽기·업로드만 MinIO로 넘기면 `BucketInitializer`의 호출이 실패해 앱이 기동하지 못한다.
- 프록시 뒤의 오리진 검사는 운영 `.env`의 `CORS_ALLOWED_ORIGINS`에 공개 주소(`https://{공개 주소}`)를 더해 푼다. 앱은 요청을 `http`로 아는 채로 둔다. 앱이 요청의 scheme·호스트로 URL을 만들거나(리다이렉트·절대 URL) `Secure` 쿠키를 쓰게 되면 그때 `server.forward-headers-strategy: native`와 프록시의 `X-Forwarded-Proto` 전달을 도입한다.
- 이미지 공개 URL은 `STORAGE_PUBLIC_BASE_URL`이 만든다(`ImageUrls.toUrl`은 DB의 객체 키 앞에 이 값을 붙인다). 코드 변경이 없고, 값을 바꾸면 기존 이미지의 응답 주소도 같이 바뀐다.

### 할 일

**구현 순서**: 1) `common.storage`(`StorageProperties` → `StorageConfig`) → 2) 설정 파일 → 3) 문서(Claude) → 테스트. 운영에 반영하는 순서는 아래 "운영 반영 순서"에 있다.

**1) common.storage**

| 클래스 | 규격 |
|---|---|
| `StorageProperties` | 서명 주소를 받는다. 필드 `String presignEndpoint`를 `endpoint` 다음에 더한다(`myroutine.storage.presign-endpoint`). 검증 어노테이션은 붙이지 않는다 |
| `StorageConfig` | 서명 주소를 고른다. `static URI presignEndpointOf(StorageProperties properties)`: `presignEndpoint`가 null이거나 공백이면 `endpoint()`, 아니면 `URI.create(presignEndpoint)`. `s3Presigner`의 `endpointOverride`만 `presignEndpointOf(properties)`로 바꾼다 |

- `URI`가 아니라 `String`인 이유: 환경변수가 비어 있을 때 `URI`가 null이 아니라 빈 주소로 바인딩되는지 확인하지 않았고, 비어 있음을 코드가 직접 판단하게 하는 편이 분명하다.
- record 생성자 순서가 바뀐다. `new StorageProperties(...)`를 쓰는 곳이 있으면 같이 고친다(Part 2 기준 `src`에는 없다).
- `s3Client`는 건드리지 않는다. path-style 서명(`pathStyleAccessEnabled(true)`)은 그대로 둔다 — 서명 URL이 `https://{공개 주소}/{버킷}/{키}` 모양이 되고, 프록시가 이 경로를 MinIO로 그대로 넘기려면 필요하다.

**2) 설정 파일**
- `application.yaml`: `myroutine.storage.presign-endpoint: ${STORAGE_PRESIGN_ENDPOINT:}` — `endpoint` 아래에 한 줄. 기본값은 빈 문자열이다.
- `.env.ops.example`: `STORAGE_PRESIGN_ENDPOINT=`를 `STORAGE_ENDPOINT=` 다음 줄에 더한다. 운영 compose는 `env_file`로 `.env` 전체를 넘기므로 compose 파일은 고치지 않는다.

**3) 문서** (Claude)
- [vm-setup.md](../ops/vm-setup.md) `.env` 표에 `STORAGE_PRESIGN_ENDPOINT` 행, [ADR-011](../adr/ADR-011-ops-practice-environment.md) "외부 접속"에 결정을 반영한다. 이 문서의 버전 줄과 함께 이 단계에서 이미 반영돼 있다.

**운영 반영 순서** — 외부 공개를 켜기 전에 이 순서를 지킨다.

| 순서 | 할 일 | 이 시점의 상태 |
|---|---|---|
| 1 | 이 단계를 develop에 머지한다(새 값은 비어 있다) | 지금 접속과 똑같이 동작한다 |
| 2 | 프록시·터널을 연결하고, 운영 `.env`의 `CORS_ALLOWED_ORIGINS`에 `https://{공개 주소}`를 쉼표로 더한 뒤 앱 컨테이너를 다시 만든다 | `https://{공개 주소}`로 화면과 API가 열리고 로그인 POST가 통과한다. 이미지는 아직 기존 주소다 |
| 3 | 운영 `.env`에 `STORAGE_PRESIGN_ENDPOINT=https://{공개 주소}`, `STORAGE_PUBLIC_BASE_URL=https://{공개 주소}/{버킷}`을 넣고 앱 컨테이너를 다시 만든다. `STORAGE_ENDPOINT`는 그대로 둔다 | 업로드 URL과 이미지 URL이 공개 주소로 나온다 |

되돌리려면 2번의 `CORS_ALLOWED_ORIGINS`와 3번의 두 값을 원래대로 돌리고 컨테이너를 다시 만든다. 1번은 되돌릴 필요가 없다.

### 완료 확인

값을 읽는 방법: 서명된 URL은 `presigner.presignPutObject(...)`가 돌려주는 `PresignedPutObjectRequest.url()`(`java.net.URL`의 `getProtocol()`·`getHost()`·`getPort()`·`getPath()`)로 읽는다.

#### `common/storage/StorageConfigTest` (Spring 없이 `new StorageConfig().s3Presigner(properties)` 호출)

준비: `endpoint` = `http://minio.internal:9000`, 나머지 필수 값은 아무 값. 키는 `products/p1/a.jpg`. 서명은 네트워크를 쓰지 않는다.

**1. 서명 주소가 있으면 그 주소로 서명** — 동작: `presignEndpoint` = `https://files.example.test`로 서명.
- `url().getProtocol()` = `"https"`, `getHost()` = `"files.example.test"`, `getPath()`는 `"/myroutine/products/p1/a.jpg"`로 시작(버킷이 앞에 붙는 path-style).

**2. 비어 있으면 endpoint로 서명** — 동작: `presignEndpoint`가 `null`, `""`, `" "`일 때 각각 서명(`@ParameterizedTest` + `@NullAndEmptySource` + `@ValueSource(strings = " ")`).
- 세 경우 모두 `getHost()` = `"minio.internal"`, `getPort()` = `9000`.

#### `common/storage/PresignEndpointIntegrationTest` (`IntegrationTestSupport` 상속, `@TestPropertySource(properties = "myroutine.storage.presign-endpoint=https://files.example.test")`)

준비: 컨테이너(MinIO 포함)가 뜬 `IntegrationTestSupport`의 기본 설정에 서명 주소만 더한다.

**3. 업로드 URL만 공개 주소, 서버의 MinIO 호출은 그대로** — 동작: 컨텍스트가 뜬 뒤 `objectStorage.presignPut("products/p1/a.jpg", "image/jpeg", 1024)`, 이어서 `objectStorage.exists("products/p1/none.jpg")`.
- 컨텍스트가 뜬다: `BucketInitializer`가 `endpoint`로 MinIO에 닿아야 한다(서명 주소로 가면 5초 타임아웃으로 기동이 실패한다).
- `presigned.url()`이 `"https://files.example.test/myroutine/products/p1/a.jpg"`로 시작한다.
- `exists(...)`가 예외 없이 `false`다(`S3Client`가 실제 MinIO에 닿았다).

기존 `ProductImageControllerTest`는 서명 주소를 넣지 않은 채 그대로 통과해야 한다. 새 값이 비어 있을 때 동작이 같다는 회귀 확인이다.

**리뷰 때 물어볼 것**
- 프록시 뒤에서 GET은 되는데 POST만 403인 이유는?
- 서명 주소를 `STORAGE_ENDPOINT` 하나로 합치면 무엇이 깨지나? 어느 시점에 드러나나?
- `CORS_ALLOWED_ORIGINS`에 공개 주소를 넣으면 403이 풀리는 이유는? 목록에 `/api/**` 밖의 경로는 어떻게 되나?
- 이 방법이 부족해지는 때는 언제이고, 그때는 무엇을 도입하나?

---

## 3-1. 이벤트가 필요한 이유 · Kafka · Outbox (가게 개설 → SELLER 역할) (L)

**목표**: 가게를 열면 회원에게 SELLER 역할이 붙는다. 이 연결을 **Outbox + Kafka**로 만든다.

**왜 지금**: 처음으로 "A 모듈의 변화에 B 모듈이 반응해야 하는데, A가 B를 직접 부르면 곤란한" 상황이 나온다.
- shop이 member를 직접 호출하면: 의존 방향이 늘어나고(member는 이미 shop을 조회함 → 순환), 역할 변경이 실패하면 가게 개설까지 실패한다.
- `@TransactionalEventListener(AFTER_COMMIT)`로 메모리 안에서 알리면: 커밋 직후 서버가 죽으면 알림이 사라진다(As-Is ORD-09, SHOP-05).
- **Outbox**: 가게 저장과 "보낼 이벤트" 저장을 **같은 트랜잭션**에 넣고, 별도 릴레이가 Kafka로 보낸다. 커밋된 변경의 이벤트는 사라지지 않는다.

**새로 등장**

| 개념·도구 | 한 줄 설명 |
|---|---|
| Kafka | 이벤트를 저장하고 구독자에게 전달하는 브로커. 지금은 같은 앱 안의 다른 모듈이 구독한다 (ADR-003) |
| Transactional Outbox | 상태 변경 + 이벤트 행을 한 트랜잭션에 저장 → 릴레이가 발행 |
| 리스(lease) 선점 | 릴레이가 행을 `PROCESSING + locked_until`로 표시하고 가져간다. 릴레이가 죽으면 시간이 지나 다시 READY로 |
| 이벤트 봉투 | `eventId, eventType, occurredAt, aggregateId, traceId, payload` |
| `Propagation.MANDATORY` | 트랜잭션이 없으면 예외. "outbox 저장은 반드시 상태 변경 트랜잭션 안에서"를 코드로 강제 |

**정책 (이 단계에서 정함)**
- 토픽은 파티션 3, 복제 1. 앱이 시작할 때 `NewTopic` 빈으로 만든다(브로커 자동 생성에 기대지 않음).
- 릴레이: 1초마다, 한 번에 100건, 리스 30초. 발행에 실패하면 **10초 뒤** 다시. **10번 실패하면 DEAD**. 한 행이 실패하면 그 호출에서 아직 시도하지 않은 행은 시도하지 않는다(브로커가 멈췄을 때 행마다 기다리다 리스를 넘기지 않으려는 것).
- SENT 행 정리는 Part 7(운영)에서 한다.

**흐름 한눈에** (가게 개설 기준. 아래 클래스가 어느 줄을 맡는지 보면서 읽는다)
1. `OpenShopService.open`이 한 트랜잭션에서 `shop` 행과 `common.outbox_event` 행(`READY`)을 함께 INSERT한다 — `OutboxPublisher.publish`가 outbox 행을 넣는다.
2. 1초마다 `OutboxRelayJob` → `OutboxRelay.relayOnce()`가 `READY` 행을 집어(선점) Kafka 토픽 `shop.shop-opened.v1`로 보내고, 성공하면 `SENT`로 바꾼다.
3. member 모듈의 `MemberShopEventConsumer`(`@KafkaListener`)가 같은 토픽에서 메시지를 받아 `EventEnvelopes.parse`로 풀고 `MemberRoleService.grantSeller`를 부른다.
- 1~2 사이, 2~3 사이 어디서 서버가 죽어도 outbox 행이나 Kafka 메시지가 남아 있어 이어서 처리된다.

### 할 일

**구현 순서**: ① 1) 인프라(compose·의존성·`application.yaml`·`IntegrationTestSupport`) → ② 2) 마이그레이션 → ③ 3) `common.outbox`: `EventEnvelope` → `OutboxPublisher` → `OutboxRelay` → `OutboxRelayJob` → `EventEnvelopes` → `EventContext` → ④ shop: 4) `shop.api.event`(`package-info`·`ShopTopics`·`ShopOpenedEvent`) → 5) `ShopTopicConfig` → 6) `OpenShopService` 수정 → ⑤ member: 7) `Member.promoteToSeller`·`demoteToUser` → 9) `MemberRoleService` → `member/package-info.java` → 8) `MemberShopEventConsumer` → ⑥ 테스트 헬퍼(완료 확인 "테스트 준비": `Eventually`, `OutboxSnapshot`, `OutboxAssert`) → 테스트.

**1) 인프라**
- `docker/docker-compose.yml`에 Kafka 추가: 이미지 `apache/kafka:4.2.2`(KRaft 단일 브로커. 2026-10-09 Docker Hub의 4.2 줄 최신 패치 — Boot 4.1.1이 관리하는 kafka-clients가 4.2.1이라 같은 줄로 맞췄다. 4.3.x도 있다), 포트 `127.0.0.1:9092:9092`. 운영 compose(`docker/docker-compose.ops.yml`)에도 같은 태그로 추가한다. 운영에서는 앱이 컨테이너라 브로커가 광고하는 주소(advertised listeners)가 `localhost`면 붙지 못한다 — 서비스 이름(`kafka:9092`)으로 광고하게 설정한다(브로커 설정은 `KAFKA_` 접두사에 대문자로, 점은 밑줄로 바꾼 환경변수로 준다: `advertised.listeners` → `KAFKA_ADVERTISED_LISTENERS`. 이미지 문서의 예제는 `KAFKA_LISTENERS`·`KAFKA_ADVERTISED_LISTENERS`·`KAFKA_LISTENER_SECURITY_PROTOCOL_MAP`·`KAFKA_INTER_BROKER_LISTENER_NAME`을 함께 준다. 운영 compose의 단일 브로커 환경변수 조합은 같은 값으로 컨테이너를 띄워 기동·`kafka-broker-api-versions.sh`·토픽 생성까지 확인했다)
- 의존성: `implementation 'org.springframework.boot:spring-boot-starter-kafka'`(Boot 4.1.1에 있다, 2026-10-09 Maven Central 확인. 3.x처럼 `spring-kafka`를 직접 넣지 않는다), 테스트 `testImplementation 'org.testcontainers:testcontainers-kafka'`(버전은 Boot가 import하는 Testcontainers BOM 2.0.5가 관리)
- `application.yaml`
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      properties:
        enable.idempotence: true
        delivery.timeout.ms: 10000
        request.timeout.ms: 5000
        max.block.ms: 5000
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
    listener:
      ack-mode: record
```
- 설정 읽는 법
  - `acks: all` + `enable.idempotence: true`: 리더와 복제본에 기록된 뒤에야 성공 응답을 받고, 프로듀서가 내부에서 재전송해도 브로커가 중복을 걸러낸다. 릴레이가 같은 행을 **다시 발행**하는 중복(아래 `relayOnce` 3~4번 사이에서 죽은 경우)은 걸러 주지 않는다.
  - `delivery.timeout.ms`를 줄인 이유: 기본값(2분)이면 릴레이가 "실패"로 기록한 뒤에도 프로듀서가 뒤에서 계속 재전송해 늦게 도착할 수 있다. 줄여도 중복은 생길 수 있으므로 consumer 멱등이 전제다. `request.timeout.ms`·`max.block.ms`도 브로커가 멈췄을 때 `send`가 오래 붙잡히지 않게 하려는 값이다.
  - `auto-offset-reset: earliest`: 이 consumer 그룹이 읽은 위치(offset)를 아직 저장한 적이 없을 때(처음 뜰 때) 토픽의 맨 앞부터 읽는다.
  - `enable-auto-commit: false` + `listener.ack-mode: record`: offset을 주기적으로 자동 저장하지 않고, **리스너 메서드가 예외 없이 끝난 레코드마다** Spring이 저장한다. 예외로 끝난 레코드는 저장되지 않아 다시 전달된다(재시도·DLT는 3-2).
- `IntegrationTestSupport`: `@ServiceConnection static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.2")`(패키지 `org.testcontainers.kafka`. `@ServiceConnection`이 컨테이너 주소를 `spring.kafka.bootstrap-servers`에 자동으로 넣어 준다), static 블록의 `postgres.start()`(1-10 이후 `minio.start()`도)를 `Startables.deepStart(postgres, minio, kafka).join()`(`org.testcontainers.lifecycle.Startables`, 컨테이너를 병렬로 띄운다)으로 바꾼다

**2) 마이그레이션** — `db/migration/common/V{...}__common_create_outbox_event.sql`

| 컬럼 | 타입 | NULL | 제약·설명 |
|---|---|---|---|
| id | uuid | X | `pk_outbox_event` — 이 값이 봉투의 `eventId` |
| aggregate_type | varchar(50) | X | 예: `SHOP` |
| aggregate_id | uuid | X | Kafka 키 |
| event_type | varchar(100) | X | 이벤트 record 이름, 예: `ShopOpenedEvent` |
| topic | varchar(100) | X | |
| payload | jsonb | X | 이벤트 record의 JSON |
| trace_id | varchar(64) | O | 저장 시점 MDC traceId |
| status | varchar(20) | X | `ck_outbox_event_status`: `IN ('READY','PROCESSING','SENT','DEAD')` |
| attempts | int | X | DEFAULT 0 |
| next_attempt_at | timestamptz | X | |
| locked_until | timestamptz | O | |
| sent_at | timestamptz | O | |
| last_error | varchar(500) | O | |
| created_at, updated_at | timestamptz | X | |

- 인덱스: `idx_outbox_event_ready ON common.outbox_event (next_attempt_at) WHERE status = 'READY'`, `idx_outbox_event_processing ON common.outbox_event (locked_until) WHERE status = 'PROCESSING'`

**3) common.outbox**

| 클래스 | 규격 |
|---|---|
| `EventEnvelope` | Kafka 메시지 한 건의 겉봉투(record). 릴레이가 만들고, consumer가 푼다 |
| `OutboxPublisher` | 이벤트를 "보낼 목록(outbox 행)"에 올리는 입구. 상태를 바꾸는 서비스(`OpenShopService` 등)가 부른다 |
| `OutboxRelay` | outbox 행을 Kafka로 실제 발행한다. `OutboxRelayJob`(1초마다)이 부르고, 테스트가 직접 호출한다 |
| `OutboxRelayJob` | `@Scheduled(fixedDelay = 1000)` → `jobRunner.run("outbox-relay", relay::relayOnce)`. `common.job.JobRunner.run(String jobName, Supplier<Integer> task)`는 같은 잡을 서버 한 대만 돌리고(`task`가 돌려준 처리 건수를 로그에 남긴다) 예외를 로그로 남기고 삼킨다. 스케줄러다. 테스트에서는 스케줄이 꺼져 있어 `relayOnce()`를 직접 부른다 |
| `EventEnvelopes` + `TypedEnvelope<T>`(봉투와 이벤트 record를 묶는 record) | Kafka에서 받은 JSON 문자열을 봉투 + 이벤트 record로 푼다. 모든 consumer의 첫 줄이다 |
| `EventContext` | 처리하는 동안 MDC에 `traceId`·`eventId`를 넣고 뺀다. consumer가 쓴다(3-2부터는 `InboxGuard`가 안에서 부른다) |

**`EventEnvelope`** — `public record EventEnvelope(UUID eventId, String eventType, Instant occurredAt, UUID aggregateId, String traceId, JsonNode payload)` (`tools.jackson.databind.JsonNode`)
- 어떤 이벤트든 같은 모양으로 감싸 보내서, 받는 쪽이 이벤트 종류를 모르고도 `eventId`(멱등 키)·`traceId`(로그 추적)를 먼저 꺼낼 수 있게 한다. 그래서 `payload`는 이벤트 record가 아니라 JSON 트리(`JsonNode`)이고, 이벤트 record로의 변환은 consumer가 `EventEnvelopes.parse`로 한다.
- outbox 행 → 봉투: `id` → `eventId`, `event_type` → `eventType`, `aggregate_id` → `aggregateId`, `trace_id` → `traceId`, `payload`(jsonb) → `payload`. `occurredAt`은 선점 쿼리가 돌려주는 `created_at`(outbox 행을 만든 시각)이다.

**`OutboxPublisher`** (`@Component`, 의존성 `JdbcTemplate`, `ObjectMapper`, `Clock`) — 비즈니스 코드가 이벤트를 내보내는 **유일한 입구**다. Kafka로 직접 보내지 않고, 상태 변경과 **같은 DB 트랜잭션에서 `common.outbox_event` 행 하나를 INSERT**만 한다. 트랜잭션이 롤백되면 이 행도 같이 사라진다 — 이것이 Outbox의 핵심이다.

`@Transactional(propagation = Propagation.MANDATORY) public void publish(String topic, String aggregateType, UUID aggregateId, Object event)` — 반환 없음

| 인자 | 의미 | 들어가는 컬럼 |
|---|---|---|
| `topic` | 보낼 토픽 이름. 발행 모듈의 상수(`ShopTopics.SHOP_OPENED`) | `topic` |
| `aggregateType` | 변경된 대상의 종류를 나타내는 문자열(`"SHOP"`). 조회·로그용 | `aggregate_type` |
| `aggregateId` | 변경된 대상의 ID. Kafka 키가 되어 같은 대상의 이벤트 순서를 지킨다 | `aggregate_id` |
| `event` | 이벤트 record(`ShopOpenedEvent`). 필드만 JSON이 된다 | `payload`, 클래스 이름(`event.getClass().getSimpleName()`)이 `event_type` |

순서
1. `UUID id = Ids.newId()` — 이 값이 봉투의 `eventId`다(consumer 멱등 키가 된다).
2. `String payload = objectMapper.writeValueAsString(event)` — Jackson 3의 예외는 unchecked라 `throws`가 없다.
3. `jdbcTemplate.update("INSERT INTO common.outbox_event (...) VALUES (..., ?::jsonb, ...)", ...)` — `status = 'READY'`, `trace_id = MDC.get(TraceIds.MDC_KEY)`(HTTP 요청이면 `TraceIdFilter`가 넣어 둔 값. 없으면 `null`), `attempts`는 DEFAULT 0. NOT NULL인 `next_attempt_at`·`created_at`·`updated_at`은 INSERT에서 `now`로 직접 채운다. JDBC로 시각을 넘길 때는 `Instant`를 `OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)`로 바꿔 넘긴다(`IdempotencyRepository`와 같다).
- `MANDATORY`: 트랜잭션 밖에서 부르면 `IllegalTransactionStateException`("상태는 롤백됐는데 이벤트만 나가는" 사고를 코드로 막는다). 상태를 바꾸는 서비스의 `@Transactional` 안에서 부르면 JPA와 같은 트랜잭션에 참여한다.

**`OutboxRelay`** (`@Component`, 의존성 `JdbcTemplate`, `KafkaTemplate<String, String>`(Boot가 `spring.kafka.*`로 만든 빈), `ObjectMapper`, `Clock`) — `@Transactional`도 `TransactionTemplate`도 쓰지 않는다: 회수·선점·결과 기록은 모두 `UPDATE` 한 문장이라 `jdbcTemplate`이 한 문장씩 바로 커밋하고, Kafka 호출은 어떤 트랜잭션에도 들어가지 않는다. 그래서 `relayOnce()`를 다른 트랜잭션 안에서 부르면 안 된다(Kafka 전송은 외부 호출이라 트랜잭션 밖에서 한다. [Part 2 공통 규칙 K](part2-order-money.md#k-외부-호출과-트랜잭션)).
- `public int relayOnce()` → 이번 호출에서 SENT로 바꾼 건수

`relayOnce()` 순서
1. 회수(`jdbcTemplate.update` 한 문장): 앞서 릴레이가 죽어 `PROCESSING`으로 남은 행을 다시 `READY`로 돌린다. 바로 이어 2의 선점이 방금 돌린 행까지 집는다. `relayOnce()` 시작에서 `now`를 한 번 얻어 1·2에 쓴다(아래 시각 설명).
```sql
UPDATE common.outbox_event SET status = 'READY', locked_until = NULL, updated_at = ? WHERE status = 'PROCESSING' AND locked_until < ?   -- ? = now, now
```
2. 선점(`UPDATE ... RETURNING` 한 문장): "내가 이 행들을 맡았다"고 표시하고 그 행을 바로 읽는다. `jdbcTemplate.query(sql, rowMapper)`로 `RETURNING` 결과를 `List<OutboxRow>`로 받는다(`OutboxRow`는 릴레이 안에서만 쓰는 private record: `id, aggregateId, eventType, topic, payload(String), traceId, attempts, createdAt`). `FOR UPDATE SKIP LOCKED`는 다른 트랜잭션이 잠근 행을 기다리지 않고 건너뛰고, `PROCESSING + locked_until`은 커밋 뒤에도 다른 인스턴스가 못 가져가게 하는 표시다.
```sql
UPDATE common.outbox_event SET status = 'PROCESSING', locked_until = ?, updated_at = ?   -- ? = now + 30초, now
 WHERE id IN (SELECT id FROM common.outbox_event
               WHERE status = 'READY' AND next_attempt_at <= ?   -- ? = now
               ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED)
RETURNING id, aggregate_id, event_type, topic, payload, trace_id, attempts, created_at
```
3. **트랜잭션 밖에서** 선점한 행을 `created_at` 순으로 하나씩(행마다 `try/catch`). 한 행이 실패하면 4에서 그 행의 실패를 기록하고 **반복을 멈춘다**. 아직 시도하지 않은 행은 "건너뜀"으로 4에서 되돌린다
   1. 봉투를 만든다: `new EventEnvelope(row.id(), row.eventType(), row.createdAt(), row.aggregateId(), row.traceId(), objectMapper.readTree(row.payload()))` → `objectMapper.writeValueAsString(envelope)`가 Kafka 메시지 값(JSON 문자열)이다.
   2. `kafkaTemplate.send(row.topic(), row.aggregateId().toString(), json)` — 키는 집계 ID 문자열. `send`는 `CompletableFuture<SendResult<String, String>>`을 **즉시** 돌려주고 실제 전송은 뒤에서 일어난다. 그래서 `.get(5, TimeUnit.SECONDS)`로 브로커의 응답을 기다려야 "보냈다"를 알 수 있다. `get`은 `InterruptedException`·`ExecutionException`·`TimeoutException`을 던질 수 있고, 전부 "발행 실패"로 취급한다(`catch (Exception e)`).
4. 결과 기록(행마다 `WHERE id = ?`의 `UPDATE` 한 문장)
   - 성공 → `SET status = 'SENT', sent_at = ?, locked_until = NULL, updated_at = ?`(둘 다 지금 시각)
   - 실패 → `attempts + 1`이 10 이상이면 `status = 'DEAD'`, 아니면 `status = 'READY'` + `next_attempt_at` = 지금 시각 + 10초. 둘 다 `attempts = attempts + 1`, `locked_until = NULL`, `last_error`(예외 메시지를 **500자로 잘라** 넣는다. `last_error`가 `varchar(500)`이라 넘치면 이 UPDATE가 실패해서 행이 `PROCESSING`으로 남는다. `.get(5, SECONDS)`의 `TimeoutException`은 `getMessage()`가 `null`이라 `e.toString()`처럼 **예외 종류가 보이는 문자열**을 대신 넣는다(빈 문자열이면 원인을 알 수 없다). `null`에 `length()`를 부르면 `NullPointerException`이 나서 이 기록 자체가 실패한다)
   - 건너뜀(3에서 멈춰 시도하지 않은 행) → 한 번의 `UPDATE common.outbox_event SET status = 'READY', locked_until = NULL, updated_at = ? WHERE id = ANY(?)`. `attempts`·`next_attempt_at`·`last_error`는 바꾸지 않는다(시도하지 않았다)
5. 3~4가 성공한 건수를 반환
- 시각은 다른 job과 같이 앱 `Clock`에서 얻는다: `OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC)`를 파라미터로 넘긴다. 1·2는 `relayOnce()` 시작에서 얻은 값 하나를 쓰고, 4는 Kafka 응답을 기다린 뒤라서 기록하기 직전에 다시 얻는다. `OutboxPublisher`의 INSERT도 같은 `Clock`이라 한 컬럼을 두 시계로 쓰지 않는다. 릴레이는 `JobRunner`의 advisory lock으로 한 번에 한 서버만 돌아 서버 간 시계 차이로 리스를 겨루는 일이 없다.
- 3의 발행이 성공했는데 4의 기록 전에 서버가 죽으면 행이 `PROCESSING`으로 남았다가 리스(30초)가 지나 1번에서 회수되어 **다시 발행된다**. 같은 `eventId`로 두 번 가므로 consumer가 `eventId`로 중복을 걸러야 한다(3-2). 이것이 "리뷰 때 물어볼 것"의 첫 질문이다.
- Kafka가 멈춰 있으면 한 호출이 붙잡히는 시간은 실패한 한 행의 대기뿐이라 리스 30초 안에 끝난다(보통 `get`의 5초. 프로듀서가 토픽 메타데이터를 한 번도 못 받은 상태면 `send`가 `max.block.ms` 5초까지 먼저 막혀 최대 약 10초). 실패한 행은 10초 뒤로 미뤄지므로 다음 호출은 그 행을 건너뛰고 그 뒤의 행부터 시도한다. 장애가 길면 오래된 행부터 실패가 쌓여 `DEAD`가 될 수 있다(10번 실패 규칙은 그대로다). 릴레이가 한 개이고 `fixedDelay`라 호출이 겹치지 않는다.

**`EventEnvelopes`** (`@Component`, 의존성 `ObjectMapper`)
- `<T> TypedEnvelope<T> parse(String json, Class<T> payloadType)` → `record TypedEnvelope<T>(EventEnvelope envelope, T payload)`. 사용: `TypedEnvelope<ShopOpenedEvent> typed = envelopes.parse(message, ShopOpenedEvent.class);` 뒤에 `typed.envelope()`(봉투), `typed.payload()`(이벤트 record)를 쓴다.
- 순서: ① `objectMapper.readValue(json, EventEnvelope.class)` ② `objectMapper.treeToValue(envelope.payload(), payloadType)` ③ 둘을 `TypedEnvelope`로 묶어 반환. `treeToValue(JsonNode, Class<T>)`는 `JsonNode`를 지정한 타입으로 바꾸는 메서드다(`tools.jackson.databind.ObjectMapper`, Jackson 3.1.5 소스로 확인. 실패하면 unchecked `JacksonException`).
- 깨진 JSON이거나 필드가 맞지 않으면 `tools.jackson.core.JacksonException`(unchecked)을 **그대로 던진다**. 다시 시도해도 같은 결과라서 3-2에서 "재시도하지 않을 예외"로 분류해 바로 DLT로 보낸다.

**`EventContext`** (`public final class`) — `static void run(EventEnvelope envelope, Runnable action)`
1. `MDC.put(TraceIds.MDC_KEY, 봉투의 traceId)` — 봉투의 `traceId`가 `null`이면 `TraceIds.newId()`로 새로 만든다. `MDC.put("eventId", envelope.eventId().toString())`
2. `try { action.run(); } finally { 두 키를 MDC.remove }` — consumer 스레드는 다음 메시지에도 재사용되므로 값이 남지 않게 finally에서 지운다.
- 왜: HTTP 요청의 `traceId`가 outbox → 봉투 → consumer 로그까지 이어져, Kibana에서 `traceId` 하나로 가게 개설부터 역할 부여까지 한 번에 검색된다(ADR-010).

**4) shop.api.event**

| 클래스 | 규격 |
|---|---|
| `ShopTopics` | 토픽 상수 클래스. `SHOP_OPENED = "shop.shop-opened.v1"` |
| `ShopOpenedEvent` | `public record ShopOpenedEvent(UUID shopId, UUID memberId)` |
| `shop/api/event/package-info.java` | `@NamedInterface("api")` |

- 다른 모듈(member)이 import하므로 `shop.api` 쪽에 둔다. `@NamedInterface`는 붙인 패키지 하나만 공개하고 하위 패키지는 포함하지 않는다. 같은 이름의 인터페이스는 합쳐지므로(Spring Modulith 2.1.1 `NamedInterfaces`·`NamedInterface` 소스로 확인), `shop/api/event/package-info.java`에도 `@NamedInterface("api")`를 붙이면 `shop.api`와 `shop.api.event`가 모두 `shop::api`로 보인다. 다른 모듈의 `api.event`도 같다. `ModularityTest`가 마지막으로 확인한다.

**5) shop.infrastructure**

| 클래스 | 규격 |
|---|---|
| `ShopTopicConfig` | `@Bean NewTopic shopOpenedTopic()` → `TopicBuilder.name(SHOP_OPENED).partitions(3).replicas(1).build()` (`org.springframework.kafka.config.TopicBuilder`. 이후 토픽도 각 발행 모듈의 infrastructure에 같은 방식으로) |

- 앱이 시작할 때 Boot가 만든 `KafkaAdmin`이 `NewTopic` 빈을 보고 브로커에 토픽이 없으면 만든다 — 그래서 브로커가 떠 있어야 앱이 시작된다.

**6) shop.application**

| 클래스 | 메서드 |
|---|---|
| `OpenShopService` | `open` 수정: 의존성에 `OutboxPublisher`를 더하고, 기존 `Shop savedShop = shopRepository.save(shop)` 바로 다음 같은 트랜잭션에서 `outboxPublisher.publish(SHOP_OPENED, "SHOP", savedShop.getId(), new ShopOpenedEvent(savedShop.getId(), memberId))` |

- 사업자번호 중복 예외는 `save` 앞에서 나므로 그때는 outbox 행도 생기지 않는다.

**7) member.domain**

| 클래스 | 규격 |
|---|---|
| `Member` | `promoteToSeller()`: USER면 SELLER로, 이미 SELLER·ADMIN이면 아무것도 안 함. `demoteToUser()`: SELLER면 USER로, 아니면 아무것도 안 함 |

- 예외를 던지지 않는 이유는 같은 이벤트가 두 번 와도, ADMIN 회원이 가게를 열고 닫아도 결과가 안전해야 해서다(`MemberRole`은 `USER, SELLER, ADMIN`).

**8) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `MemberShopEventConsumer` | **Kafka 메시지를 받아 서비스를 부르는 얇은 어댑터**. 의존성 `EventEnvelopes`, `MemberRoleService`. 메서드는 아래 `onShopOpened` |

```java
@KafkaListener(topics = ShopTopics.SHOP_OPENED, groupId = "member")
void onShopOpened(String message) {
    TypedEnvelope<ShopOpenedEvent> typed = envelopes.parse(message, ShopOpenedEvent.class);
    EventContext.run(typed.envelope(), () -> roleService.grantSeller(typed.payload().memberId()));
}
```
- `@KafkaListener`: Spring이 이 메서드를 컨테이너 스레드에서 호출한다. `message`는 레코드의 **값**(JSON 문자열, `StringDeserializer`)이다. 키·offset이 필요하면 `ConsumerRecord<String, String>`을 받을 수도 있지만 지금은 값만 쓴다.
- `groupId = "member"`: consumer 그룹 이름. **그룹마다 메시지를 한 번씩 받는다**(member 그룹, 나중의 notification 그룹이 같은 토픽을 각자 받는다). 같은 그룹 안에서는 파티션을 나눠 가진다. 그룹 이름은 모듈 이름으로 통일한다.
- 메서드가 정상 반환하면 offset이 저장된다(`ack-mode: record`). 예외가 나면 에러 핸들러가 처리하는데, 3-2에서 만들기 전인 3-1에는 없어서 Spring Kafka의 기본 핸들러가 간격 없이 10번 시도한 뒤 ERROR 로그(`Backoff ... exhausted`)를 남기고 그 메시지를 **건너뛴다**(다음 메시지로 넘어가 그 메시지는 사라진다. 깨진 JSON을 보내 확인했다). 재시도 간격·DLT는 3-2가 만든다.

**9) member.application**

`MemberRoleService`(`@Service`, 의존성 `MemberRepository`)는 consumer가 부르는 "역할 바꾸기" 유스케이스다.

| 클래스 | 메서드 |
|---|---|
| `MemberRoleService` | `@Transactional void grantSeller(UUID memberId)` |

1. `memberRepository.findById(memberId)` — 회원이 없으면 `log.warn` 후 return(예외 금지. 공통 규칙 Q: 테스트 사이에 남은 메시지일 수 있다)
2. `member.promoteToSeller()` — `save`는 부르지 않는다. 변경은 커밋 때 더티 체킹으로 저장된다.
- `member/package-info.java`: `allowedDependencies = {"common", "shop::api"}` — 현재 값은 `{"common"}`이다. `ShopTopics`·`ShopOpenedEvent`가 `shop::api`에 있어서 추가한다.
- 역할 반영 전에도 판매자 기능은 소유권으로 동작한다(1-6). 토큰의 role은 다음 로그인 때 갱신된다.

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 이 단계에서 만드는 헬퍼는 다음과 같다(`support/`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 대상 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `Eventually` (공통 규칙 Q) | `static void await(Duration timeout, ThrowingRunnable assertion)`. `ThrowingRunnable`은 `void run() throws Exception`을 가진 함수형 인터페이스다(JDK에 없어 `support`에 함께 만든다). 100ms마다 `assertion.run()`을 호출해 예외 없이 끝나면 return하고, `timeout`이 지나면 **마지막으로 난 예외**를 던진다. 사용: `Eventually.await(Duration.ofSeconds(5), () -> assertThat(role(memberId)).isEqualTo("SELLER"));` | consumer가 처리한 결과를 기다리는 모든 케이스 |
| `OutboxSnapshot` | `support/`의 record. `public record OutboxSnapshot(UUID id, UUID aggregateId, String aggregateType, String eventType, String topic, String status, String traceId, String lastError, int attempts, Instant nextAttemptAt, Instant lockedUntil, Instant sentAt, JsonNode payload)`. `traceId`·`lastError`·`lockedUntil`·`sentAt`은 없으면 null이다. `JsonNode`는 `tools.jackson.databind.JsonNode` | 아래 세 행과 모든 outbox 확인 |
| `OutboxAssert.rows(String topic)` → `List<OutboxSnapshot>` | JDBC `SELECT id, aggregate_type, aggregate_id, event_type, topic, status, trace_id, last_error, attempts, next_attempt_at, locked_until, sent_at, payload FROM common.outbox_event WHERE topic = ? ORDER BY created_at, id`. `payload`는 `PGobject`의 `toString()`을 `objectMapper.readTree`로 푼다. 시각 컬럼은 `OffsetDateTime`으로 읽어 `toInstant()` | 3-1 이후 모든 outbox 확인 |
| `OutboxAssert.outboxSnapshotOf(UUID outboxId)` → `OutboxSnapshot` | 위와 같은 컬럼·변환으로 `WHERE id = ?` 한 행(`queryForObject`). 행이 없으면 `EmptyResultDataAccessException`(없는 행을 읽는 케이스가 없다) | `OutboxRelayTest`, 행 id를 아는 확인 |
| `OutboxAssert.clear()` → `void` | JDBC `DELETE FROM common.outbox_event` | 개수를 세거나 `relayOnce()`를 부르는 모든 케이스의 준비 끝 |
| `OutboxAssert.publishReady(String topic, UUID aggregateId)` → `UUID` | `tx.executeWithoutResult(s -> outboxPublisher.publish(topic, "TEST", aggregateId, new TestEvent("x")))` 뒤 `SELECT id FROM common.outbox_event WHERE aggregate_id = ?`로 읽은 행 id를 돌려준다 | `OutboxRelayTest` |
| `TestEvent` | `support/`의 `public record TestEvent(String value)` | 위 |
| `TestKafka` (`@Component`) | 의존성 `ObjectMapper`, `@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers`. 메서드 둘. ⓐ `public List<ConsumerRecord<String, String>> readAll(String topic)`: 새 `group.id`(`UUID`), `auto.offset.reset=earliest`, `StringDeserializer`로 `KafkaConsumer`를 만들어 `partitionsFor(topic)`으로 얻은 모든 파티션을 `assign`하고 `seekToBeginning`한 뒤(`subscribe`는 그룹 배정을 기다리는 동안 빈 `poll`이 나와 일찍 끝날 수 있다), 새 레코드가 없는 `poll(Duration.ofSeconds(1))`이 연속 2번 나올 때까지 모아 반환한 뒤 닫는다. ⓑ `public List<ReceivedMessage> messages(String topic, String key)`: `readAll(topic)`에서 `record.key()`가 `key`인 레코드만 골라 `ReceivedMessage`로 바꾼다 | 3-1 이후 모든 Kafka 메시지 확인 |
| `ReceivedMessage` | `support/`의 `public record ReceivedMessage(String key, int partition, String value, EventEnvelope envelope)`. `envelope`은 `objectMapper.readValue(value, EventEnvelope.class)`의 결과이고, 읽기에 실패하면(봉투가 아닌 문자열) `null` | `TestKafka.messages` |
| `memberSnapshotOf(UUID memberId)` → `MemberSnapshot` | 이 단계에서 `TestFixtures`와 `support/MemberSnapshot`을 새로 만든다(처음 쓰는 단계). JDBC `SELECT nickname, name, phone, password_hash, role, status, email, created_at, updated_at FROM member.member WHERE id = ?`(`created_at`·`updated_at`은 `Timestamp`로 읽어 `toInstant()`), `public record MemberSnapshot(String nickname, String name, String phone, String passwordHash, String role, String status, String email, Instant createdAt, Instant updatedAt)`로 돌려준다. 탈퇴 시각(`withdrawnAt`)과 배송지(`addresses`)는 3-6이 더한다(`withdrawn_at` 컬럼이 3-6 마이그레이션에서 생기기 전이라 지금 읽으면 SQL이 실패한다) | 3-1 `MemberRoleServiceTest`·`ShopOpenedFlowTest`, 3-2 `CloseShopTest`, 3-6 `WithdrawTest`, Part 4 |
| `countOf(String table)` → `long` | 이 단계에서 `TestFixtures`에 추가한다. JDBC `SELECT COUNT(*) FROM {table}`(`table`은 `"common.outbox_event"`처럼 테스트 코드의 상수만 넘긴다) | 3-1 이후 행 수 확인 |
| `changeRole(UUID memberId, String role)` → `void` | JDBC `UPDATE member.member SET role = ? WHERE id = ?` | 3-1 `MemberRoleServiceTest`, 3-2 `CloseShopTest` 준비, 3-6 |
| `TestKafkaTopics` (`@Configuration`) | `@Bean public NewTopic` 메서드 3개: `testOutboxRelay()`(토픽 `test.outbox-relay.v1`), `testAlwaysFail()`(`test.always-fail.v1`), `testAlwaysFailDlt()`(`test.always-fail.v1.dlt`). 각각 `TopicBuilder.name(토픽).partitions(3).replicas(1).build()` | `OutboxRelayTest`, 3-2 `KafkaDltTest` |
| `IntegrationTestSupport.kafka`와 브로커 주소 | 필드를 `protected static final KafkaContainer kafka`(`org.testcontainers.kafka.KafkaContainer`)로 바꾼다(`static final`이던 것. 다른 패키지의 `OutboxRelayTest`가 `kafka.getDockerClient()`를 쓴다). 이어서 기존 `@DynamicPropertySource static void storageProperties(DynamicPropertyRegistry registry)`에 한 줄 `registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)`를 더한다. `@ServiceConnection`은 접속 정보 빈만 바꾸고 이 프로퍼티는 그대로 두어서, 더하지 않으면 `@Value`로 읽는 `TestKafka`가 `application.yaml`의 `localhost:9092`(개발용 Kafka 또는 없는 브로커)를 읽는다(프로퍼티 `localhost:9092`, 컨테이너 `localhost:60385`로 확인) | `OutboxRelayTest` |

이미 있는 헬퍼(Part 1·2에서 만든 것, 그대로 쓴다, `TestFixtures`): `UUID signup(String email)`(가입하고 memberId를 돌려준다. 비밀번호 `pass1234`, 닉네임은 이메일의 `@` 앞부분, 이름 `테스터`), `String token(String email)`(같은 가입 + access 토큰), `String login(String email)`(가입된 회원의 access 토큰), `UUID openShop(UUID memberId, String businessNumber)`(가게를 열고 shopId를 돌려준다).

헬퍼를 쓰는 방법은 다음과 같다(3-2 이후도 같다).
- 응답: `jsonPath`. 에러 응답의 코드는 `$.code`. 응답 헤더는 `result.getResponse().getHeader("X-Request-Id")`.
- outbox: 토픽의 행은 `OutboxAssert.rows(topic)`(생성 순), id를 아는 행 하나는 `OutboxAssert.outboxSnapshotOf(outboxId)`로 읽는다. payload 필드는 `payload().get("shopId").asText()`처럼 읽는다. 전체 행 수는 `testFixtures.countOf("common.outbox_event")`.
- 회원: `testFixtures.memberSnapshotOf(memberId)`의 `role()`.
- Kafka 메시지: `TestKafka.messages(topic, key)`로 읽는다. 브로커는 테스트 JVM 전체에서 하나라 이전 테스트가 남긴 메시지가 섞여 있어서 키(집계 ID 문자열)로 거른다. 봉투의 payload 필드는 `envelope().payload().get("shopId").asText()`처럼 읽는다.
- consumer가 처리한 결과: `Eventually.await(Duration.ofSeconds(5), () -> assertThat(...))` 안에서 읽는다.

**공통 준비**: 판매자 `seller@test.com`을 `signup`(역할 `USER`), 가게는 `openShop(sellerId, "1111111111")`. 준비 호출도 outbox 행을 만든다(이 단계에서는 `openShop`이 `shop.shop-opened.v1` 1건. `signup`은 3-5부터 `member.member-registered.v1`을 만든다). `relayOnce()`는 READY인 행을 전부 발행하므로 준비가 만든 행이 섞이면 반환값과 consumer 결과가 달라진다. 개수를 세거나 `relayOnce()`를 부르는 케이스는 준비를 마친 직후 `OutboxAssert.clear()`로 비우고 시작한다.

#### `common/outbox/OutboxPublisherTest`

공통 준비: `OutboxPublisher`, `TransactionTemplate tx`, `JdbcTemplate` 주입. 호출은 `outboxPublisher.publish("shop.shop-opened.v1", "SHOP", shopId, new ShopOpenedEvent(shopId, memberId))`이고 `shopId`·`memberId`는 `Ids.newId()`로 만든 임의 UUID다(회원·가게 행은 필요 없다).

**1. 트랜잭션 없이 publish** — 동작: `tx` 없이 위 호출을 그대로 한다.
- `IllegalTransactionStateException`이 던져진다(`assertThatThrownBy`).
- `countOf("common.outbox_event")` = 0.

**2. 롤백되면 행이 없다** — 동작: `tx.execute(s -> { publish 호출; s.setRollbackOnly(); return null; })`.
- `OutboxAssert.rows("shop.shop-opened.v1")`가 비어 있다(0건).

**3. 커밋되면 READY 행이 한 개** — 동작: `tx.executeWithoutResult(s -> publish 호출)`.
- `OutboxAssert.rows("shop.shop-opened.v1")`가 정확히 1건이다.
- 그 행: `status()` = `READY`, `aggregateType()` = `SHOP`, `aggregateId()` = shopId, `eventType()` = `ShopOpenedEvent`, `topic()` = `shop.shop-opened.v1`, `attempts()` = 0.
- payload: `shopId` = shopId 문자열, `memberId` = memberId 문자열.

**4. MDC가 없으면 trace_id는 null** — 동작: MDC를 넣지 않고 `tx.executeWithoutResult(s -> publish 호출)`.
- 그 행의 `traceId()`가 `null`이다.

**5. 커밋되면 trace_id는 MDC 값** — 준비: `MDC.put("traceId", "trace-test-1")`(키는 `TraceIds.MDC_KEY`). 동작: 3번과 같은 호출 → `finally`에서 `MDC.remove("traceId")`.
- `OutboxAssert.rows("shop.shop-opened.v1")`의 행 `traceId()` = `trace-test-1`.

#### `common/outbox/OutboxRelayTest`

공통 준비: 테스트 전용 토픽 `test.outbox-relay.v1`(`TestKafkaTopics`가 만든다)에 READY 행을 `UUID id = OutboxAssert.publishReady("test.outbox-relay.v1", aggregateId)`로 만든다(행의 `id`를 돌려준다). 행은 `OutboxAssert.outboxSnapshotOf(id)`로 읽고, Kafka 메시지는 `TestKafka.messages("test.outbox-relay.v1", aggregateId.toString())`로 읽는다. 각 케이스는 `OutboxAssert.clear()`로 시작한다.

**1. READY 3건 발행** — 준비: `a1`, `a2`, `a3`(임의 UUID)로 3행. 동작: `int sent = outboxRelay.relayOnce()`.
- `sent` = 3.
- 3행 모두 `status()` = `SENT`, `sentAt()` not null, `lockedUntil()` null, `attempts()` = 0.
- `messages(토픽, a1)`·`messages(토픽, a2)`·`messages(토픽, a3)`(키는 UUID 문자열)이 각각 정확히 1개.
- 각 메시지의 봉투: `eventId()` = 그 행의 `id()`, `aggregateId()` = 키, `eventType()` = `TestEvent`, `traceId()` = 행의 `traceId()`(null).

**2. Kafka가 멈췄다 돌아온 경우** — 준비: READY 1행(`a1`). 동작 1: `kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec()` → `relayOnce()`(브로커 응답을 기다리느라 몇 초 걸린다).
- 반환값 0.
- 그 행: `status()` = `READY`, `attempts()` = 1, `lastError()` not null(길이 500 이하), `lockedUntil()` null.
- `nextAttemptAt()`이 `Instant.now()`보다 뒤다(10초 뒤로 미뤄졌다).
- 동작 2(`finally`에서도 반드시): `unpauseContainerCmd(...)` → JDBC `UPDATE common.outbox_event SET next_attempt_at = now() - interval '1 second' WHERE id = ?` → `relayOnce()`.
- 반환값 1, 그 행 `status()` = `SENT`, `attempts()` = 1(성공해도 지우지 않는다).
- `messages(토픽, a1)`은 1개 또는 2개이고 모두 `envelope().eventId()`가 그 행의 `id()`다(멈춘 동안 보낸 첫 시도가 브로커가 돌아온 뒤 늦게 도착할 수 있다. 그래서 consumer 멱등이 필요하다).

**3. 리스가 끝난 PROCESSING 행 회수, 끝나지 않은 행은 그대로** — 준비: READY 2행(`a1`, `a2`)을 만든 뒤 JDBC로 `UPDATE common.outbox_event SET status = 'PROCESSING', locked_until = now() - interval '1 minute' WHERE aggregate_id = a1`, `... locked_until = now() + interval '1 minute' WHERE aggregate_id = a2`. 동작: `relayOnce()`.
- 반환값 1.
- `a1` 행: `status()` = `SENT`, `messages(토픽, a1)` 1개.
- `a2` 행: `status()` = `PROCESSING`, `lockedUntil()`이 `Instant.now()`보다 뒤(그대로 미래), `messages(토픽, a2)` 0개(남의 리스를 가져가지 않는다).

**4. 10번째 실패는 DEAD** — 준비: READY 1행(`a1`) → JDBC `UPDATE common.outbox_event SET attempts = 9 WHERE aggregate_id = a1`. 동작: Kafka를 멈추고(2번과 같은 방법) `relayOnce()` → `finally`에서 unpause.
- 반환값 0.
- 그 행: `status()` = `DEAD`, `attempts()` = 10, `lastError()` not null, `lockedUntil()` null.

**5. 첫 실패에서 중단** — 준비: READY 2행(`a1`, `a2`. `a1`을 먼저 만든다). 동작: Kafka를 멈추고(2번과 같은 방법) `relayOnce()` → `finally`에서 unpause.
- 반환값 0.
- `a1` 행: `status()` = `READY`, `attempts()` = 1, `lastError()` not null, `lockedUntil()` null, `nextAttemptAt()`이 `Instant.now()`보다 뒤.
- `a2` 행: `status()` = `READY`, `attempts()` = 0, `lastError()` null, `lockedUntil()` null, `nextAttemptAt()`이 `Instant.now()` 이전(시도하지 않았으니 미뤄지지 않았다).

**6. SENT 행은 다시 발행하지 않는다** — 준비: 1번의 동작까지(3행이 `SENT`). 동작: `relayOnce()`를 한 번 더.
- 반환값 0.
- `messages(토픽, a1)`·`messages(토픽, a2)`·`messages(토픽, a3)`이 각각 그대로 1개.

**7. DEAD 행은 다시 집지 않는다** — 준비: 4번의 동작까지(`DEAD`가 된 상태, unpause 포함). 동작: `relayOnce()`.
- 반환값 0.
- 그 행은 `status()` = `DEAD` 그대로.

#### `member/application/MemberRoleServiceTest`

회원 역할 변환을 consumer 없이 서비스로 직접 확인한다. 준비: 회원 `signup`, 역할은 `changeRole(memberId, role)`로 맞춘다. 동작은 `memberRoleService.grantSeller(memberId)`. 역할은 `memberSnapshotOf(memberId).role()`로 읽는다.

| 준비한 역할 | 확인할 것 |
|---|---|
| `USER` | 호출 뒤 `role()` = `SELLER` |
| `SELLER` | 예외 없이 끝나고 `role()` = `SELLER` 그대로 |
| `ADMIN` | 예외 없이 끝나고 `role()` = `ADMIN` 그대로 |
| 없는 회원(`Ids.newId()`) | 예외 없이 끝난다(경고 로그만 남는다) |

#### `shop/application/ShopOpenedFlowTest`

**1. 가게 개설이 SELLER 역할로 이어진다** — 동작 1: `OutboxAssert.clear()` → `shopId = openShop(sellerId, "1111111111")`.
- `OutboxAssert.rows("shop.shop-opened.v1")`가 정확히 1건(`row`): `aggregateType()` = `SHOP`, `aggregateId()` = shopId, payload `shopId` = shopId 문자열, `memberId` = sellerId 문자열.
- 이 시점 `memberSnapshotOf(sellerId).role()` = `USER`(릴레이 전에는 바뀌지 않는다).
- 동작 2: `outboxRelay.relayOnce()`.
- 반환값 1, `outboxSnapshotOf(row.id()).status()` = `SENT`.
- `TestKafka.messages("shop.shop-opened.v1", shopId.toString())` 1개, `envelope().eventId()` = `row.id()`.
- `Eventually`(5초) 안에서 `memberSnapshotOf(sellerId).role()` = `SELLER`.

**2. 사업자번호 중복이면 이벤트도 없다** — 준비: 판매자 `seller@test.com`이 `openShop(sellerId, "1111111111")` 성공 → `OutboxAssert.clear()`. 동작: 같은 `sellerId`로 `openShop(sellerId, "1111111111")`을 다시 부른다(서비스 직접 호출. 사업자번호는 판매자와 무관하게 유일해서 같은 판매자여도 같은 위치에서 실패한다. 예외를 409로 바꾸는 것은 1-6이 확인한다).
- `BusinessException`이 던져지고 `getErrorCode()` = `ShopErrorCode.SHOP_BUSINESS_NUMBER_DUPLICATED`.
- `countOf("common.outbox_event")` = 0.
- `countOf("shop.shop")` = 1.

**3. 요청의 traceId가 outbox와 봉투까지 이어진다** — 준비: `token("seller2@test.com")`으로 가입·토큰, `OutboxAssert.clear()`. 동작: 위 2번과 같은 `POST /api/shops`(사업자번호는 `"2222222222"`) → 201. 응답 헤더 `X-Request-Id` 값을 `traceId`로 읽고 응답의 `$.shopId`를 `shopId`로 읽는다.
- `OutboxAssert.rows("shop.shop-opened.v1")`의 `aggregateId()` = shopId인 행의 `traceId()` = `traceId`.
- 동작 2: `relayOnce()`. `TestKafka.messages("shop.shop-opened.v1", shopId.toString())`의 봉투 `traceId()` = `traceId`.

**리뷰 때 물어볼 것**
- Kafka에 보내고 SENT로 바꾸기 전에 죽으면? 그래서 consumer가 반드시 해야 하는 것은?
- 폴링 방식 Outbox와 CDC(Debezium)의 차이는?
- 원 프로젝트에선 이 흐름이 Saga였는데 왜 단순해졌나? (소유권 인가, ADR-006)
- 선점을 `FOR UPDATE SKIP LOCKED` + 상태값(PROCESSING)으로 하는 이유는? 락만 쓰면 무엇이 문제였나? (As-Is PAY-03)

---

## 3-2. 멱등 소비 · 재시도 · DLT · 가게 폐업 (M)

**목표**
- consumer가 같은 이벤트를 두 번 받아도 한 번만 처리한다. 계속 실패하는 메시지는 DLT에 모인다.
- 가게 폐업: 진행 중인 주문이 없어야 폐업할 수 있고, 폐업하면 상품이 단종되고, 마지막 가게였다면 SELLER가 회수된다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| Inbox (멱등 소비) | `processed_message(consumer, eventId)`에 기록하고 이미 있으면 건너뛴다. **기록과 처리를 같은 트랜잭션**에 |
| 재시도 · DLT | 일시적 오류는 3번 재시도, 그래도 실패하면 `{topic}.dlt`. 역직렬화 오류는 바로 DLT |
| 의존 역전 | 폐업 조건(진행 중 주문)은 order 데이터인데 shop → order 의존은 순환이다(order는 이미 `shop::api`를 쓴다). shop이 `ShopClosePrecondition` 인터페이스만 `shop.api`에 정의하고 order가 구현체를 빈으로 등록 |

**정책 (이 단계에서 정함)**
- 재시도: 1초 → 2초 → 4초(3번) 후 DLT. 재시도하지 않는 예외: `JacksonException`(역직렬화), `IllegalArgumentException`, `BusinessException`(비즈니스 규칙 위반은 다시 해도 같음)
- DLT 토픽은 원본과 같은 파티션 수(3). DLT 조회·재처리 API는 Part 7.
- 폐업을 막는 진행 중 가게주문: `PENDING`, `PAID`, `SHIPPED`, `DELIVERED`(구매확정·반품 종결 전). 구독은 Part 5에서 추가.

### 할 일

**구현 순서**: ① 1) 마이그레이션 → 2) `InboxGuard` → ② 3) `KafkaConfig` → ③ 13)의 `onShopOpened`에 `InboxGuard` 적용 → ④ 폐업: 4)~9) shop(`ShopClosePrecondition`이 있어야 order 구현체를 만든다) → 10)~12) order → 14) `MemberRoleService.revokeSellerIfNoActiveShop` → 13)의 `onShopClosed` → 15)~17) product → 테스트(`InboxGuardTest` → `KafkaDltTest` → `CloseShopTest`).

폐업 구현물의 이름은 권장안이고 쓰다 바꿔도 된다. 저장소 구조는 Part 2와 같다(인터페이스는 `domain`, `@Query` 구현은 `infrastructure`의 `Jpa...Repository`).

**1) 마이그레이션** — `db/migration/common/V{...}__common_create_processed_message.sql`: `common.processed_message` — `consumer varchar(100) X`, `event_id uuid X`, `processed_at timestamptz X`, `pk_processed_message (consumer, event_id)`

**2) common.inbox**

| 클래스 | 규격 |
|---|---|
| `InboxGuard` | `@Component`, 의존성 `JdbcTemplate`, `Clock`. **"이 이벤트를 이 consumer가 이미 처리했는가"를 확인하고 처음일 때만 처리하는 문지기**. Kafka는 같은 메시지를 두 번 줄 수 있어서(릴레이 재발행, 재시도, 리밸런스) 모든 consumer가 이것을 거친다. 부르는 곳: 각 모듈의 `@KafkaListener` 메서드(`inboxGuard.runOnce(...)`) |

- `@Transactional public void runOnce(String consumer, EventEnvelope envelope, Runnable action)` → 반환 없음
  - `consumer`: 처리 주체의 이름(규칙 `{구독 모듈}.{이벤트}`, 예 `member.shop-opened`). 같은 이벤트를 member와 notification이 각자 한 번씩 처리해야 하므로 `(consumer, eventId)` 쌍으로 중복을 판단한다.
  - `envelope`: 파싱한 봉투. `eventId`가 멱등 키다.
  - `action`: 비즈니스 처리(예: `() -> roleService.grantSeller(memberId)`). **처음 본 이벤트일 때만** 실행된다.
  - 아래 1~2를 `EventContext.run(envelope, () -> { ... })` 안에서 실행한다. `traceId`·`eventId`의 MDC 설정과 정리는 `EventContext`가 하고 `InboxGuard`는 따로 put/remove하지 않는다. 람다 안에서 "이미 처리함"으로 끝낼 때는 `return`한다.
  1. `jdbcTemplate.update("INSERT INTO common.processed_message (consumer, event_id, processed_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", ...)`의 **반환값**이 0이면 이미 처리한 이벤트다 → `log.info("duplicate event skipped")` 후 return(예외 아님. 정상 종료라 offset이 저장된다). 같은 이벤트가 동시에 두 스레드로 와도 두 번째 INSERT는 첫 트랜잭션이 끝나길 기다렸다가 0이 된다.
  2. `action.run()` — **1의 INSERT와 같은 트랜잭션**이다. `action` 안의 `@Transactional(REQUIRED)` 서비스도 이 트랜잭션에 합류한다. 예외가 나면 INSERT도 함께 롤백되어 "처리 안 한 이벤트"로 남고, 재시도 때 다시 처리된다. 기록과 처리를 다른 트랜잭션에 두면 "기록만 남고 처리는 실패"해서 영영 처리되지 않는다.

**3) common.config**

| 클래스 | 규격 |
|---|---|
| `KafkaConfig` | consumer 메서드가 예외를 던졌을 때 **다시 시도하고, 끝내 안 되면 DLT(dead letter topic, 처리 못 한 메시지를 모아 두는 `{topic}.dlt` 토픽)로 옮기는** 규칙을 정한다. 이 설정이 없으면 실패한 메시지는 Spring Kafka의 기본 동작을 따른다 |

- `@Bean CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template)`: 빈 한 개를 만들어 반환한다. 만드는 순서는 다음과 같다.
  1. `DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template, (record, ex) -> new TopicPartition(record.topic() + ".dlt", record.partition()))` — 재시도를 다 쓴 레코드를 **같은 키·값 그대로** DLT로 보내는 객체. 람다는 "어느 토픽·파티션으로 보낼지"를 정하며, 원본과 **같은 파티션 번호**를 쓰므로 DLT도 원본처럼 파티션 3개여야 한다(아래 `NewTopic`).
  2. `ExponentialBackOff backOff = new ExponentialBackOff(1000, 2.0)` — 첫 대기 1000ms, 이후 2배씩. `backOff.setMaxAttempts(3)`으로 재시도를 3번으로 제한한다. 그래서 **최초 1번 + 재시도 3번 = 4번 시도** 후 DLT로 간다(완료 확인 `KafkaDltTest`의 "4번 시도").
  3. `DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff)` — 리스너가 예외를 던지면 같은 레코드를 대기 후 다시 전달하고, 횟수를 다 쓰면 `recoverer`를 부른다.
  4. `errorHandler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class, BusinessException.class)` — 이 예외는 재시도 없이 **바로** DLT로 보낸다(시도 1번). 이 목록에 없는 예외(`DataAccessException`, 낙관적 락 충돌 등 일시적 오류)는 재시도 대상이다.
  5. `return errorHandler`
- Boot는 `CommonErrorHandler` 빈을 기본 리스너 컨테이너 팩토리에 자동으로 연결한다(Boot 4.1.1의 `KafkaAnnotationDrivenConfiguration`이 `ObjectProvider<CommonErrorHandler>`를 받아 팩토리에 넣는 것을 jar로 확인, 2026-10-09). 그래서 `ConcurrentKafkaListenerContainerFactory`를 직접 만들지 않는다
- DLT 토픽도 `NewTopic`으로 만든다. 발행 모듈의 `XxxTopicConfig`에서 원본마다 `{topic}.dlt`를 같이 만든다(shop은 아래 `shop.infrastructure` 절)

**4) shop.domain**

| 클래스 | 규격 |
|---|---|
| `Shop` | `close(Instant now)`: `this.status = this.status.transitTo(ShopStatus.CLOSED)`(`ShopStatus`가 이미 ACTIVE → CLOSED만 허용), `closedAt = now`(`closed_at` 컬럼·필드는 이미 있다) |
| `ShopRepository` | `ShopApi.countActiveShops`가 쓰는 건수 조회를 새로 더한다(이름은 자유, 예: `countByMemberIdAndStatus`) |

**5) shop.api**

| 클래스 | 규격 |
|---|---|
| `ShopClosePrecondition` | `void check(UUID shopId)` — 조건을 어기면 `BusinessException`을 던지고 통과하면 아무 일도 없다. shop은 스프링이 주입하는 `List<ShopClosePrecondition>`을 돌며 확인만 하고, 구현체가 하나도 없으면 목록이 비어 모두 통과한다 |
| `ShopApi.countActiveShops` | `int (UUID memberId)` — 그 회원 소유의 **ACTIVE** 가게 수. SELLER 회수 판단(`MemberRoleService.revokeSellerIfNoActiveShop`)과 3-6 탈퇴 조건이 쓴다 |
| `ShopApi.getOwnerId` | `UUID (UUID shopId)` — 가게 주인의 memberId. 가게가 없으면 `SHOP_NOT_FOUND`. `shopRepository.findById`로 읽는다. 3-5에서 `getContact(shopId)`로 대체한다 |
| `ShopApi.getShopIdsByOwner` | `List<UUID> (UUID memberId)` — 그 회원 소유의 가게 ID 전부(**CLOSED 포함**). 기존 `ShopRepository.findAllByMemberIdOrderByCreatedAtDesc`로 가능. 3-6 탈퇴 전 미정산 확인이 쓴다 |

**6) shop.api.event**

| 클래스 | 규격 |
|---|---|
| `ShopTopics` | `SHOP_CLOSED = "shop.shop-closed.v1"` |
| `ShopClosedEvent` | `record ShopClosedEvent(UUID shopId, UUID memberId)` |

**7) shop.infrastructure**

| 클래스 | 규격 |
|---|---|
| `ShopTopicConfig` | `NewTopic` 3개를 더한다: `SHOP_CLOSED`, 그리고 두 `.dlt` 토픽 `SHOP_OPENED + ".dlt"`·`SHOP_CLOSED + ".dlt"`(전부 파티션 3·복제 1. 3-1의 `shopOpenedTopic()`과 같은 방식). `KafkaConfig`의 DLT 전송이 쓴다 |

**8) shop.application**

| 클래스 | 메서드 |
|---|---|
| `CloseShopService` | 가게 폐업 유스케이스(`@Service`). 의존성 `ShopRepository`, `List<ShopClosePrecondition> preconditions`, `OutboxPublisher`, `Clock`. `@Transactional void close(UUID memberId, UUID shopId)`. 순서는 표 아래 |
| `ShopApiImpl` | `ShopApi`에 더한 세 메서드(`countActiveShops`, `getOwnerId`, `getShopIdsByOwner`)를 구현한다 |

`CloseShopService.close` 순서 (한 트랜잭션)
1. `shopRepository.findById(shopId)` — 없으면 404 `SHOP_NOT_FOUND` → `shop.verifyOwner(memberId)`(남의 가게면 403 `FORBIDDEN`) → `shop.verifyActive()`(이미 CLOSED면 `SHOP_NOT_ACTIVE`)
2. `preconditions.forEach(p -> p.check(shopId))` — 하나라도 던지면 여기서 끝난다(아직 바꾼 것이 없어 되돌릴 것도 없다)
3. `shop.close(Instant.now(clock))` — 저장은 커밋 때 더티 체킹으로 된다
4. `outboxPublisher.publish(SHOP_CLOSED, "SHOP", shopId, new ShopClosedEvent(shopId, memberId))`

**9) shop.web**

| 클래스 | 규격 |
|---|---|
| `ShopController` | `@DeleteMapping("/{id}")` 추가 — `@CurrentMember UUID memberId`, `@PathVariable UUID id` → `closeShopService.close(memberId, id)` → 204 No Content(`ResponseEntity.noContent().build()`) |

**10) order.domain**

| 클래스 | 규격 |
|---|---|
| `OrderErrorCode` | `SHOP_HAS_ACTIVE_ORDERS` 추가(422, 메시지는 공통 규칙 R) |
| `OrderRepository` | `boolean existsByShopIdAndStatusIn(UUID shopId, Collection<ShopOrderStatus> statuses)` — `ShopOrder`에서 `shopId`가 같고 `status`가 집합에 속하는 행이 있는지. `ShopOrder`는 `Order` 애그리거트 안의 엔티티라 전용 저장소가 없다 |

**11) order.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaOrderRepository` | `existsByShopIdAndStatusIn`의 `@Query`를 JPQL로 둔다(`select count(so) > 0 from ShopOrder so where so.shopId = :shopId and so.status in :statuses`. `ShopOrderQueryRepository`는 만들어지지 않았다) |

**12) order.application**

| 클래스 | 메서드 |
|---|---|
| `OrderShopClosePrecondition` | `@Component`, `implements ShopClosePrecondition`. `void check(UUID shopId)`: 그 가게의 가게주문 중 `PENDING`·`PAID`·`SHIPPED`·`DELIVERED`가 하나라도 있으면(`orderRepository.existsByShopIdAndStatusIn(shopId, 그 집합)`) `BusinessException(SHOP_HAS_ACTIVE_ORDERS)` |

**13) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `MemberShopEventConsumer.onShopOpened` | 3-1의 `EventContext.run(...)`을 `inboxGuard.runOnce("member.shop-opened", typed.envelope(), () -> roleService.grantSeller(typed.payload().memberId()))`로 바꾼다(`typed = envelopes.parse(...)`는 그대로). 의존성에 `InboxGuard`를 더한다 |
| `MemberShopEventConsumer.onShopClosed` | `@KafkaListener(topics = ShopTopics.SHOP_CLOSED, groupId = "member")` → `inboxGuard.runOnce("member.shop-closed", ...)` → `MemberRoleService.revokeSellerIfNoActiveShop` |

**14) member.application**

| 클래스 | 메서드 |
|---|---|
| `MemberRoleService` | `@Transactional void revokeSellerIfNoActiveShop(UUID memberId)`(의존성에 `ShopApi`를 더한다): ① 회원 조회(없으면 `log.warn` 후 return) ② `shopApi.countActiveShops(memberId)`를 **처리하는 시점에** 조회 ③ 0이면 `member.demoteToUser()` |

- 이벤트에 "남은 가게 수"를 담지 않는 이유: 같은 회원의 가게 2개가 동시에 폐업되면 각 이벤트가 만들어질 때의 "남은 수"는 서로 모르는 값이라 둘 다 1이 되어 SELLER가 안 회수될 수 있다. 처리 시점에 다시 세면 마지막 이벤트는 항상 0을 본다. 두 consumer가 동시에 같은 회원을 바꾸다 낙관적 락 충돌이 나면 재시도 대상이라 다음 시도에서 해소된다

**15) product.domain**

| 클래스 | 규격 |
|---|---|
| `ProductRepository` | `List<Product> findAllByShopIdAndStatusIn(UUID shopId, Collection<ProductStatus> statuses)` — 파생 쿼리. 현재 `ProductRepository`에는 없다 |

**16) product.infrastructure**

| 클래스 | 규격 |
|---|---|
| `ProductShopEventConsumer` | 다른 모듈 consumer와 클래스 이름이 같으면 `@Component` 기본 빈 이름이 충돌해 앱이 기동하지 않는다. `onShopClosed`: `@KafkaListener(topics = ShopTopics.SHOP_CLOSED, groupId = "product")` → `inboxGuard.runOnce("product.shop-closed", ...)` → `ProductDiscontinueService.discontinueAllByShop` |

- member의 `onShopClosed`와 이 consumer는 서로 다른 그룹(`member`, `product`)이라 같은 `shop-closed` 메시지를 각자 한 번씩 받는다.

**17) product.application**

| 클래스 | 메서드 |
|---|---|
| `ProductDiscontinueService` | `@Service`. `@Transactional void discontinueAllByShop(UUID shopId)`: ① `findAllByShopIdAndStatusIn(shopId, {ON_SALE, HIDDEN})` ② 각 상품에 `product.changeStatus(DISCONTINUED)`. 이미 단종된 상품은 조회에서 빠지므로 이벤트가 두 번 와도 안전하다 |

- 모듈 의존: member는 3-1에서 `shop::api`를 이미 추가했다. product는 이미 `shop::api`를 허용한다.

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 값을 읽는 방법은 3-1과 같고, 이 단계에서 더하는 헬퍼는 다음과 같다(이름·인자는 권장안이라 쓰다 바꿔도 된다. 대상 칸에 만들 파일을 적었다. 모두 `src/test/java/com/myroutine/support/`에 있다).

| 대상 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `TestKafka.send(String topic, String key, UUID eventId, Object event)` → `int` (기존 `TestKafka`에 메서드 추가) | ① `new EventEnvelope(eventId, event.getClass().getSimpleName(), Instant.now(), UUID.fromString(key), null, objectMapper.valueToTree(event))` ② `String json = objectMapper.writeValueAsString(envelope)` ③ `kafkaTemplate.send(topic, key, json).get(5, SECONDS)`(`KafkaTemplate<String, String>`. `OutboxRelay`와 같다. 이벤트 객체를 그대로 보내면 프로듀서가 문자열로 직렬화하지 못한다) ④ `getRecordMetadata().partition()` 반환 | `KafkaDltTest`, `CloseShopTest` 7번, 3-4 이후 |
| `TestKafka.sendRaw(String topic, String key, String value)` → `int` (기존 `TestKafka`에 메서드 추가) | 위와 같고 봉투 만들기(①②)만 건너뛰고 `value`를 그대로 보낸다 | `KafkaDltTest` 2번 |
| `AlwaysFailListener` (새 파일 `AlwaysFailListener.java`, `@Component`. 의존성 `ObjectMapper`, 필드 `ConcurrentHashMap<String, AtomicInteger>`) | 메서드 두 개를 만든다. ⓐ `@KafkaListener(topics = "test.always-fail.v1", groupId = "test-always-fail") void onMessage(ConsumerRecord<String, String> record)`(이름은 자유): ① 메시지 키(`record.key()`)별 호출 횟수를 맵에 센다. 이 메서드가 불릴 때마다 그 키의 숫자에 1을 더한다(재시도로 같은 메시지가 다시 와도 더한다. 예: 키 `K1`이 최초 1번 + 재시도 3번 불리면 `attempts("K1")` = 4). 키가 처음이면 `AtomicInteger`를 새로 넣고(`computeIfAbsent`) `incrementAndGet`으로 올린다 ② `objectMapper.readTree(record.value())`를 부르고(깨진 JSON이면 `JacksonException`) ③ 성공하면 `throw new IllegalStateException("always fail")`. ①이 ②보다 먼저여야 깨진 JSON도 1번으로 센다. ⓑ `int attempts(String key)`: 그 키의 호출 횟수(없으면 0). 테스트가 이 클래스를 주입받아 부른다 | `KafkaDltTest` |
| `processedCountOf(String consumer, UUID eventId)` → `int` (기존 `TestFixtures`에 메서드 추가) | JDBC `SELECT COUNT(*) FROM common.processed_message WHERE consumer = ? AND event_id = ?`. 그 consumer가 그 이벤트를 처리한 기록 수(0 또는 1) | `InboxGuardTest`, `CloseShopTest` |
| `shopSnapshotOf(UUID shopId)` → `ShopSnapshot` (`TestFixtures`에 메서드 추가) | 이 단계에서 `TestFixtures`에 추가한다. JDBC `SELECT name, email, phone, address, status, business_number, closed_at FROM shop.shop WHERE id = ?`, `support/ShopSnapshot`은 `public record ShopSnapshot(String name, String email, String phone, String address, String status, String businessNumber, Instant closedAt)`이다(`closed_at`은 `Timestamp`로 읽어 `toInstant()`, 폐업 전이면 null). 이 단계는 `status()`·`closedAt()`을 읽는다 | `CloseShopTest` |
| `productSnapshotOf(UUID productId)` → `ProductSnapshot` (`TestFixtures`에 메서드 추가) | 이 단계에서 `TestFixtures`에 추가한다. 쿼리 2개로 한 번에 읽는다: 상품·총입고 `SELECT p.name, p.status, p.price, p.subscribable, s.received FROM product.product p JOIN product.stock s ON s.product_id = p.id WHERE p.id = ?`, 재고 이력 `SELECT type, quantity, ref_type, ref_id FROM product.stock_movement WHERE product_id = ? ORDER BY created_at, id`. `support/ProductSnapshot`은 `public record ProductSnapshot(String name, String status, long price, boolean subscribable, int received, List<MovementState> movements)`이고 `record MovementState(String type, int quantity, String refType, UUID refId)`를 함께 둔다(상품 행이 없으면 `null`). 이 단계는 `status()`만 읽는다. 가격 이력(`priceChanges`)은 3-3이 더한다 | `CloseShopTest`, 3-3 |

이미 있는 헬퍼(`TestFixtures`): `signup(email)`·`token(email)`·`login(email)`·`openShop(memberId, businessNumber)`(3-1과 같다), `closeShop(UUID shopId)`(JDBC로 `shop.shop.status`를 `CLOSED`로), `registerProduct(memberId, shopId, price, initialStock)`(productId), `hideProduct(productId)`·`discontinueProduct(productId)`(JDBC로 `product.product.status`를 `HIDDEN`·`DISCONTINUED`로), `createPaidOrder(buyerId, productIds...)`(상품마다 수량 1로 결제까지 마친 orderId), `shopOrderIdOf(orderId, shopId)`(그 가게의 shopOrderId), `ConcurrencyRunner.run(int threads, Callable<T> task)`·`run(List<Callable<T>> tasks)`(동시에 출발시켜 `List<Result<T>>`로 돌려준다), 3-1의 `Eventually`·`OutboxAssert`·`TestKafka.messages`·`memberSnapshotOf`·`countOf`·`changeRole`.

헬퍼를 쓰는 방법은 다음과 같다.
- 가게 폐업 이벤트의 `eventId`: `OutboxAssert.rows("shop.shop-closed.v1")`의 `id()`(= `processed_message.event_id`). consumer가 그 이벤트를 처리했는지는 `processedCountOf`로 안다.
- 센티널: "중복으로 보낸 메시지가 처리되지 않았다"는 기다려도 알 수 없다. 같은 키로 다른 `eventId`의 메시지를 한 건 더 보내고, 그 메시지의 처리 신호가 나오면 앞의 중복 메시지는 이미 지나간 것이다(같은 키는 같은 파티션이라 보낸 순서대로 처리된다).

#### `common/inbox/InboxGuardTest`

준비: `InboxGuard`, `JdbcTemplate`, `ObjectMapper` 주입. 봉투는 `new EventEnvelope(eventId, "TestEvent", Instant.now(), aggregateId, "trace-test-1", objectMapper.createObjectNode())`(`eventId`·`aggregateId`는 `Ids.newId()`). action은 `AtomicInteger counter`를 올리는 람다다. consumer 이름은 `test.consumer`.

**1. 같은 eventId로 두 번** — 동작: `inboxGuard.runOnce("test.consumer", envelope, () -> counter.incrementAndGet())`을 두 번.
- 두 번째 호출도 예외 없이 끝난다.
- `counter` = 1.
- `processedCountOf("test.consumer", eventId)` = 1.

**2. consumer 이름이 다르면 각자 한 번** — 동작: 1번 뒤 같은 봉투를 consumer 이름 `test.consumer2`로 `runOnce`.
- `counter` = 2.
- `processedCountOf("test.consumer2", eventId)` = 1(consumer 이름이 다르면 같은 eventId가 각각 기록된다).

**3. action이 예외를 던지면 기록도 롤백** — 동작: action이 `throw new IllegalStateException("fail")`인 `runOnce` → 이어서 같은 봉투로 `counter`를 올리는 action의 `runOnce`.
- 첫 호출은 `IllegalStateException`이 그대로 던져진다.
- 첫 호출 직후 `processedCountOf("test.consumer", eventId)` = 0.
- 둘째 호출 뒤 `counter` = 1, `processedCountOf("test.consumer", eventId)` = 1(재시도 때 처리된다).

**4. 같은 eventId 동시 2건** — 동작: `ConcurrencyRunner.run(2, () -> { inboxGuard.runOnce("test.consumer", envelope, () -> { counter.incrementAndGet(); Thread.sleep(300); }); return null; })`(action 안에서 300ms `Thread.sleep`으로 잠시 붙잡아 두 스레드가 겹치게 한다. `InterruptedException`은 `try/catch`로 감싼다).
- 두 스레드 모두 `Result.ok()`(예외 없음).
- `counter` = 1.
- `processedCountOf("test.consumer", eventId)` = 1.

**5. 처리 중 MDC** — 동작: action 안에서 `MDC.get("eventId")`, `MDC.get("traceId")`를 변수에 담는다. 호출이 끝난 뒤 같은 값을 다시 읽는다.
- action 안: `eventId` = eventId 문자열, `traceId` = `trace-test-1`.
- 호출이 끝난 뒤: 둘 다 `null`(다음 메시지에 값이 남지 않는다).

**6. 다른 eventId는 따로 처리한다** — 준비: 1번의 동작까지(`counter` = 1). 동작: 다른 `eventId`(`Ids.newId()`)의 봉투를 `test.consumer`로 `runOnce`.
- `counter` = 2.

#### `common/config/KafkaDltTest`

준비: 위 `AlwaysFailListener`를 쓴다. 각 케이스는 새 UUID를 키로 쓴다. DLT 확인은 `TestKafka.messages("test.always-fail.v1.dlt", key)`로 읽는다.

**1. 일시적 오류는 4번 시도 후 DLT** — 동작: `int partition = TestKafka.send("test.always-fail.v1", key, eventId, new TestEvent("x"))`.
- `Eventually`(20초; 재시도 대기 1+2+4초) 안에서 `messages("test.always-fail.v1.dlt", key)`가 정확히 1개.
- 그 메시지의 `value()` = `messages("test.always-fail.v1", key).get(0).value()`(같은 값 그대로 옮긴다), `partition()` = 위 `partition`(원본과 같은 파티션).
- DLT 메시지가 보인 시점에 `attempts(key)` = 4(최초 1번 + 재시도 3번).

**2. 깨진 JSON은 재시도 없이 바로 DLT** — 동작: `TestKafka.sendRaw("test.always-fail.v1", key, "{not-json")`.
- `Eventually`(3초; 재시도 대기 7초보다 짧아야 한다) 안에서 `messages("test.always-fail.v1.dlt", key)`가 정확히 1개, `value()` = `{not-json`.
- `attempts(key)` = 1.

#### `member/application/MemberRoleServiceTest`에 추가 (3-1의 클래스에 더한다)

동작은 `memberRoleService.revokeSellerIfNoActiveShop(memberId)`. 가게는 `openShop`, 폐업은 `closeShop(shopId)`(JDBC로 CLOSED). 역할은 `changeRole`로 맞추고 `memberSnapshotOf(memberId).role()`로 읽는다.

| 준비 | 확인할 것 |
|---|---|
| 역할 `SELLER`, ACTIVE 가게 0개(가게 1개를 `closeShop`) | `role()` = `USER` |
| 역할 `SELLER`, ACTIVE 가게 1개(가게 2개 중 1개만 `closeShop`) | `role()` = `SELLER` 그대로 |
| 역할 `ADMIN`, ACTIVE 가게 0개 | `role()` = `ADMIN` 그대로 |
| 없는 회원(`Ids.newId()`) | 예외 없이 끝난다 |

#### `shop/web/CloseShopTest`

공통 준비: 판매자 `seller@test.com`(`signup`, 토큰은 `login`), 가게 A `openShop(sellerId, "1111111111")`, 상품 `product1`(`registerProduct(sellerId, shopA, 1000, 10)`)·`product2`(2000)·`product3`(3000), 구매자 `buyer@test.com`. 판매자 역할은 `changeRole(sellerId, "SELLER")`로 맞춘다(개설 이벤트를 릴레이하지 않는다. 나가면 폐업 이벤트와 순서가 섞인다). 준비 끝에 `OutboxAssert.clear()`. 폐업 요청은 `DELETE /api/shops/{shopId}`, `Authorization: Bearer {판매자 토큰}`(`Idempotency-Key` 없음).

**1. 진행 중 가게주문이 있으면 거부** — 준비: `createPaidOrder(buyerId, product1Id)`(가게주문 `PAID`). 동작: 가게 A 폐업 요청.
- 응답 422, `$.code` = `SHOP_HAS_ACTIVE_ORDERS`.
- `shopSnapshotOf(shopA)`: `status()` = `ACTIVE`, `closedAt()` null.
- `productSnapshotOf(product1Id).status()` = `ON_SALE` 그대로.
- `countOf("common.outbox_event")` = 0(`shop.shop-closed.v1` 행이 없다).

**2. 가게주문 상태별 경계** — 준비: 1번의 주문을 만든 뒤 JDBC `UPDATE orders.shop_order SET status = ? WHERE id = ?`(shopOrderId는 `shopOrderIdOf`)로 상태를 맞춘다. 동작: 가게 A 폐업 요청.

| 가게주문 상태 | 응답 | 확인할 것 |
|---|---|---|
| `PENDING`, `PAID`, `SHIPPED`, `DELIVERED` | 422 `SHOP_HAS_ACTIVE_ORDERS` | `shopSnapshotOf(shopA).status()` = `ACTIVE` |
| `COMPLETED`, `CANCELLED` | 204 | `shopSnapshotOf(shopA).status()` = `CLOSED` |

**3. 폐업 성공: 상태, 이벤트, 상품 단종** — 준비: `hideProduct(product2Id)`(`HIDDEN`), `discontinueProduct(product3Id)`(`DISCONTINUED`), 가게주문 없음. 동작 1: 가게 A 폐업 요청.
- 응답 204, 본문 없음.
- `shopSnapshotOf(shopA)`: `status()` = `CLOSED`, `closedAt()` not null.
- `OutboxAssert.rows("shop.shop-closed.v1")`가 정확히 1건: `aggregateType()` = `SHOP`, `aggregateId()` = shopA, `status()` = `READY`, payload `shopId` = shopA 문자열, `memberId` = sellerId 문자열.
- 동작 2: `outboxRelay.relayOnce()`.
- 반환값 1.
- `Eventually`(5초) 안에서 `product1`·`product2`의 `productSnapshotOf(...).status()`가 모두 `DISCONTINUED`.

**4. 폐업 요청 자체의 거부** — 동작과 확인:

| 준비 | 요청 | 응답 | 확인할 것 |
|---|---|---|---|
| 다른 판매자 `seller2@test.com`의 토큰 | 가게 A 폐업 | 403 `FORBIDDEN` | `shopSnapshotOf(shopA).status()` = `ACTIVE`, `countOf("common.outbox_event")` = 0 |
| 가게 A를 먼저 폐업(204)한 뒤 `OutboxAssert.clear()` | 가게 A 폐업을 한 번 더 | 422 `SHOP_NOT_ACTIVE` | `countOf("common.outbox_event")` = 0(두 번째 요청이 이벤트를 만들지 않았다) |
| 없는 가게 ID(`Ids.newId()`) | 폐업 | 404 `SHOP_NOT_FOUND` | `countOf("common.outbox_event")` = 0 |

**5. 가게 2개 중 1개 폐업 → SELLER 유지, 나머지 폐업 → 회수** — 준비: 같은 판매자가 가게 B(`openShop(sellerId, "2222222222")`)도 연다. 상품은 만들지 않아도 된다. `OutboxAssert.clear()`.
- 동작 1: 가게 A 폐업 요청(204) → `relayOnce()` = 1. 이 시점의 `OutboxAssert.rows("shop.shop-closed.v1")`는 A의 행 1건뿐이고, 그 행의 `id()`가 폐업 이벤트의 `eventId`(`eventIdA`)다. `Eventually`(5초) 안에서 `processedCountOf("member.shop-closed", eventIdA)` = 1.
- 확인 1: `memberSnapshotOf(sellerId).role()` = `SELLER`(가게 B가 ACTIVE다).
- 동작 2: 가게 B 폐업 요청(204) → `relayOnce()` = 1. 이제 `rows("shop.shop-closed.v1")` 중 `aggregateId()` = B인 행의 `id()`가 `eventIdB`다. `Eventually` 안에서 `processedCountOf("member.shop-closed", eventIdB)` = 1.
- 확인 2: `memberSnapshotOf(sellerId).role()` = `USER`.

**6. 같은 회원의 가게 2개 동시 폐업** — 준비: 5번과 같은 가게 A·B. 동작 1: `ConcurrencyRunner.run(List.of(A 폐업, B 폐업))`. 각 작업은 `DELETE` 요청의 HTTP 상태 코드를 돌려준다.
- 상태 코드: 둘 다 204.
- `OutboxAssert.rows("shop.shop-closed.v1")` 2건(`aggregateId()`가 A, B 하나씩. 두 행의 `id()`가 `eventIdA`, `eventIdB`).
- 동작 2: `relayOnce()` = 2. `Eventually`(10초) 안에서 `processedCountOf("member.shop-closed", eventIdA)` = 1, `processedCountOf("member.shop-closed", eventIdB)` = 1.
- 확인 2: `memberSnapshotOf(sellerId).role()` = `USER`. (처리 신호가 둘 다 나온 뒤에 읽는다. 이 케이스가 확인하는 것은 락 충돌이 아니라, consumer가 이벤트가 만들어진 시점이 아니라 처리하는 시점의 활성 가게 수를 센다는 점이다. 두 폐업이 모두 커밋된 뒤에 처리되므로 어느 이벤트를 먼저 처리해도 0개를 본다. `member` 그룹의 `shop-closed` 리스너는 스레드가 하나라(`concurrency` 설정 없음) 두 이벤트를 차례로 처리하므로 이 테스트에서는 충돌이 나지 않는다.)

**7. 같은 shop-closed 이벤트를 두 번 소비** — 준비: 가게 A를 JDBC로 폐업 상태로 만든다(`closeShop(shopA)`). `@MockitoSpyBean ProductDiscontinueService`, `@MockitoSpyBean MemberRoleService`. 동작: `eventId1`로 `TestKafka.send(ShopTopics.SHOP_CLOSED, shopA.toString(), eventId1, new ShopClosedEvent(shopA, sellerId))`를 두 번 보내고, 이어서 센티널로 `eventId2`를 같은 키로 한 번 더 보낸다.
- `Eventually`(10초) 안에서 `processedCountOf("product.shop-closed", eventId2)` = 1, `processedCountOf("member.shop-closed", eventId2)` = 1.
- `processedCountOf("product.shop-closed", eventId1)` = 1, `processedCountOf("member.shop-closed", eventId1)` = 1.
- `verify(productDiscontinueService, times(2)).discontinueAllByShop(shopA)`와 `verify(memberRoleService, times(2)).revokeSellerIfNoActiveShop(sellerId)`(eventId1 한 번 + 센티널 한 번. 3이면 중복이 처리된 것이다).

**추가 항목**
- 같은 회원의 가게 개설·폐업을 `pg_advisory_xact_lock(hashtext('shop-member:' + memberId))`로 줄 세우기 — SELLER 판단은 consumer 재조회로 이미 안전하므로, 줄 세우기는 "동시에 열고 닫는" 이상한 요청을 막는 용도
- 재시도 간격을 실패 횟수에 따라 늘리기(지수 백오프) — 릴레이·알림 발송 공통

**리뷰 때 물어볼 것**
- 이벤트에 "남은 가게 수"를 담지 않고 consumer가 다시 조회하는 이유는? (같은 회원의 가게 2개를 동시에 폐업하는 경우를 생각해 보라)
- 의존 역전을 쓰지 않으면 어떤 구조가 되나?
- `processed_message` INSERT와 비즈니스 처리를 다른 트랜잭션에 두면 어떤 문제가 생기나?
- `BusinessException`을 재시도하지 않는 이유는? 그럼 그 메시지는 어디로 가나?

---

## 3-3. 기존 흐름에 이벤트 발행 붙이기 (M)

**목표**: Part 1~2에서 만든 흐름에 이벤트를 발행한다. 구독자는 이후 단계(정산, 알림, 검색, 추천)에서 붙는다.

**정책 (이 단계에서 정함)**
- 결제 실패와 만료는 같은 이벤트(`order-expired`)로 내고 `reason`으로 구분한다(`EXPIRED`, `PAYMENT_FAILED`).
- 구매확정은 **품목마다** 이벤트 1건(자동 확정으로 한 번에 여러 품목이 확정돼도).
- 상품 이벤트의 `version`은 발행 시점 상품 버전이다(읽는 방법은 아래 "상품 이벤트 공통").

### 할 일

**구현 순서**: ① 이벤트 정의: 1)·5)·9)·10) → ② 토픽 설정과 `flush()`: 2)·4)·6)·11) → ③ `ProductEventPublisher`(7)·`OrderEventPublisher`(12) → ④ 발행 위치를 member(3) → product(7) → order(12) 순서로. 8)의 `Order.autoConfirm` 반환 타입 변경과 12)의 `AutoConfirmService`·`ConfirmPurchaseService` 수정은 order의 마지막 행인 구매확정에서 한다: `AutoConfirmService`와 `ConfirmPurchaseService`가 ③에서 만든 `OrderEventPublisher.lineConfirmed`를 부르고, `Order.autoConfirm`의 반환 타입을 바꾸면 `AutoConfirmService`가 함께 고쳐져야 컴파일되기 때문이다 → ⑤ 테스트.

이벤트 record의 필드 타입은 공통 규칙 P대로 원시 타입이다. 엔티티의 값을 옮길 때: 금액은 `Money.amount()`(long), enum은 `.name()`(String), 시각은 `Instant`.

**발행 위치** — 해당 상태 변경과 같은 트랜잭션 안, 도메인 메서드 호출 직후

발행하는 모양은 모두 같다: 서비스(또는 엔티티를 바꾼 직후의 코드)가 `outboxPublisher.publish(토픽, aggregateType, aggregateId, 이벤트 record)`를 부른다. 서비스의 `@Transactional` 안에서 부르므로 상태 변경이 롤백되면 이벤트도 사라지고(완료 확인 order 12번: 발행이 실패하면 outbox 0건), 이 단계에서 서비스에 `OutboxPublisher`(order는 `OrderEventPublisher`, product는 `ProductEventPublisher`) 의존성을 더한다. member는 `SignupService`가 `OutboxPublisher`를 바로 부른다. 한 흐름에서 이벤트가 둘이면(상품 수정 + 가격 변경) 같은 키(`productId`)라 **호출한 순서대로** 같은 파티션에서 소비된다.

**1) member.api.event**

| 클래스 | 규격 |
|---|---|
| `MemberTopics` | 토픽 상수 클래스. `MEMBER_REGISTERED = "member.member-registered.v1"` |
| `MemberRegisteredEvent` | `record (UUID memberId, String email, String nickname)` |
| `member/api/event/package-info.java` | `@NamedInterface("api")` (3-1) |

**2) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `MemberTopicConfig` | 토픽 1개(`MEMBER_REGISTERED`)마다 `NewTopic`(파티션 3·복제 1)과 `{topic}.dlt`(본 토픽 이름 뒤에 `.dlt`를 붙인 토픽, 파티션·복제 수는 같다)를 만든다. `ShopTopicConfig`와 같이 `@Bean NewTopic`을 토픽마다 두 개씩 두고 `TopicBuilder.name(OrderTopics.ORDER_PAID).partitions(3).replicas(1).build()`, `TopicBuilder.name(OrderTopics.ORDER_PAID + ".dlt").partitions(3).replicas(1).build()`처럼 쓴다 |

**3) member.application**

| 흐름 | 클래스·메서드 | 발행 | aggregateType / aggregateId |
|---|---|---|---|
| 가입 (1-3) | `SignupService.signUp` | `MEMBER_REGISTERED` | `MEMBER` / memberId |

- **가입** `SignupService.signUp`: `memberRepository.save(member)`가 돌려준 `savedMember`로 `MemberRegisteredEvent(savedMember.getId(), savedMember.getEmail(), savedMember.getNickname())`를 발행한다. 중복 이메일 예외는 save 앞이라 이벤트가 나가지 않는다.

**4) product.domain**

| 클래스 | 규격 |
|---|---|
| `ProductRepository` | `void flush()` — 현재 `ProductRepository` 인터페이스에는 없다(`MemberAddressRepository.flush()`와 같은 방식으로 JPA `flush`를 위임). 엔티티를 수정한 직후에는 `@Version`이 아직 오르지 않았으므로(flush 때 오른다) 이벤트를 만들기 **전에** 부르고 `product.getVersion()`을 읽는다. 등록(`save` 직후)은 `flush()` 없이 초기값을 쓴다. 상품 이벤트 4곳(`product.application`의 "상품 이벤트 공통")이 쓴다 |

**5) product.api.event**

| 클래스 | 규격 |
|---|---|
| `ProductTopics` | 토픽 상수 클래스. `PRODUCT_UPSERTED = "product.product-upserted.v1"`, `PRODUCT_PRICE_CHANGED = "product.product-price-changed.v1"`, `PRODUCT_DISCONTINUED = "product.product-discontinued.v1"` |
| `ProductUpsertedEvent` | `record (UUID productId, UUID shopId, String name, String description, String category, long price, String status, boolean subscribable, long version)` |
| `ProductPriceChangedEvent` | `record (UUID productId, long oldPrice, long newPrice, Instant changedAt)` |
| `ProductDiscontinuedEvent` | `record (UUID productId, UUID shopId, String reason)` — reason `SELLER`, `SHOP_CLOSED` |
| `product/api/event/package-info.java` | `@NamedInterface("api")` (3-1) |

**6) product.infrastructure**

| 클래스 | 규격 |
|---|---|
| `ProductTopicConfig` | 토픽 3개(`PRODUCT_UPSERTED`, `PRODUCT_PRICE_CHANGED`, `PRODUCT_DISCONTINUED`)마다 `NewTopic`과 `{topic}.dlt`를 만든다. 파티션·복제 수와 만드는 방식은 `MemberTopicConfig`와 같다 |

**7) product.application**

`ProductEventPublisher` (`@Component`, 의존성 `OutboxPublisher`) — **Product를 받아 상품 이벤트 record를 조립해 `OutboxPublisher`로 보내는 곳**. `OrderEventPublisher`와 같이 `@Transactional`을 붙이지 않는다. 인자는 문서에 정해져 있지 않아 아래는 권장안이다.

| 메서드 | 이벤트 · aggregate | 필드를 채우는 방법 |
|---|---|---|
| `upserted(Product product)` | `PRODUCT_UPSERTED` · `PRODUCT`/`product.getId()` | 아래 "상품 이벤트 공통"의 9개 필드를 `product`에서 |
| `priceChanged(Product product, PriceChange change, Instant changedAt)` | `PRODUCT_PRICE_CHANGED` · `PRODUCT`/`product.getId()` | `ProductPriceChangedEvent(product.getId(), change.oldPrice().amount(), change.newPrice().amount(), changedAt)` |
| `discontinued(Product product, String reason)` | `PRODUCT_DISCONTINUED` · `PRODUCT`/`product.getId()` | `ProductDiscontinuedEvent(product.getId(), product.getShopId(), reason)`. `reason`은 `"SELLER"` 또는 `"SHOP_CLOSED"` |

`flush()`는 publisher가 아니라 **서비스**가 `product`를 바꾼 뒤 `upserted`를 부르기 전에 부른다. publisher 안에서 부르면 호출자가 `product.getVersion()`을 읽는 시점과 어긋난다.

| 흐름 | 클래스·메서드 | 발행 | aggregateType / aggregateId |
|---|---|---|---|
| 상품 등록 (1-7) | `RegisterProductService.register` | `PRODUCT_UPSERTED` | `PRODUCT` / productId |
| 상품 수정 (1-8) | `UpdateProductService.update` | `PRODUCT_UPSERTED`, 가격이 바뀌었으면 `PRODUCT_PRICE_CHANGED` | `PRODUCT` / productId |
| 상품 상태 (1-8) | `UpdateProductService.changeStatus` | `PRODUCT_UPSERTED`, DISCONTINUED면 `PRODUCT_DISCONTINUED`(reason `SELLER`) | `PRODUCT` / productId |
| 가게 폐업 단종 (3-2) | `ProductDiscontinueService.discontinueAllByShop` | 상품마다 `changeStatus(DISCONTINUED)` → `flush()` 뒤 `PRODUCT_UPSERTED` + `PRODUCT_DISCONTINUED`(reason `SHOP_CLOSED`) | `PRODUCT` / productId |

- **상품 이벤트 공통**: `ProductUpsertedEvent`는 `productId`, `shopId`, `name`, `description`, `category`(`product.getCategory().name()`), `price`(`product.getPrice().amount()`), `status`(`product.getStatus().name()`), `subscribable`, `version`을 현재 상품 상태에서 채운다. 상품 변경마다 **전체 스냅샷**을 보낸다(search가 이 값으로 색인을 덮어쓴다). 같은 조립 코드를 등록·수정·상태 변경·단종 네 곳이 쓰므로 위 `ProductEventPublisher`에 모은다.
- **`version`**: 읽는 방법은 위 `product.domain`의 `flush()` 항목이다. 색인 소비자(Part 6)가 이 값이 작은 이벤트를 "오래된 것"으로 무시한다.
- **수정** `UpdateProductService.update`: `product.update(...)`가 돌려주는 `Optional<PriceChange>`가 비어 있으면 가격이 안 바뀐 것이다. 값이 있으면 `priceChanged(product, change, 가격 이력 저장에 쓴 now)`를 `PRODUCT_UPSERTED` 다음에 부른다. 이미 이력 저장 코드(`insertPriceHistory`)가 같은 `ifPresent` 안에 있으므로 거기에 붙인다.
- **상태 변경** `UpdateProductService.changeStatus`: `to == DISCONTINUED`일 때만 `discontinued(product, "SELLER")`를 `PRODUCT_UPSERTED` 다음에 추가한다.

**8) order.domain**

| 클래스 | 규격 |
|---|---|
| `Order` | `autoConfirm` 반환 타입 변경: 확정한 품목 수(int) 대신 확정한 품목 목록 `List<OrderLine>`을 돌려준다(품목마다 이벤트를 내기 위해). 반환 타입만 바꾸면 `AutoConfirmService`의 `count += tx.execute(...)`가 컴파일되지 않으므로 `Order.autoConfirm`과 `AutoConfirmService`는 한 번에 고친다. 시점은 위 구현 순서의 구매확정 행이다 |

**9) order.api**

| 클래스 | 규격 |
|---|---|
| `order/api/package-info.java` | `@NamedInterface("api")`. `member/api`·`product/api`·`payment/api`·`shop/api`에는 이미 있고 `order/api`만 현재 코드에 없어 이 단계에서 `order::api`가 처음 공개된다. `OrderTopics`·이벤트 record를 import하는 다른 모듈(3-4~3-6)이 쓴다 |

**10) order.api.event**

| 클래스 | 규격 |
|---|---|
| `OrderTopics` | 토픽 상수 클래스. `ORDER_PAID = "order.order-paid.v1"`, `ORDER_EXPIRED = "order.order-expired.v1"`, `SHOP_ORDER_SHIPPED = "order.shop-order-shipped.v1"`, `SHOP_ORDER_DELIVERED = "order.shop-order-delivered.v1"`, `ORDER_LINE_REFUNDED = "order.order-line-refunded.v1"`, `ORDER_LINE_CONFIRMED = "order.order-line-confirmed.v1"` |
| `OrderPaidEvent` | `OrderPaidEvent(UUID orderId, UUID memberId, long totalAmount, List<PaidShopOrder> shopOrders)` |
| `PaidShopOrder` | `PaidShopOrder(UUID shopOrderId, UUID shopId, List<PaidLine> lines)` |
| `PaidLine` | `PaidLine(UUID orderLineId, UUID productId, String productName, int quantity)` |
| `OrderExpiredEvent` | `OrderExpiredEvent(UUID orderId, UUID memberId, String reason)` |
| `ShopOrderShippedEvent` | `ShopOrderShippedEvent(UUID shopOrderId, UUID orderId, UUID memberId, String carrier, String trackingNumber)` |
| `ShopOrderDeliveredEvent` | `ShopOrderDeliveredEvent(UUID shopOrderId, UUID orderId, UUID memberId)` |
| `OrderLineRefundedEvent` | `OrderLineRefundedEvent(UUID refundId, UUID orderLineId, UUID orderId, UUID memberId, long amount)` |
| `OrderLineConfirmedEvent` | `OrderLineConfirmedEvent(UUID orderLineId, UUID orderId, UUID shopId, UUID productId, UUID memberId, long amount, Instant confirmedAt)` — amount = `confirmedAmount()` |
| `order/api/event/package-info.java` | `@NamedInterface("api")` (3-1) |

**11) order.infrastructure**

| 클래스 | 규격 |
|---|---|
| `OrderTopicConfig` | 토픽 6개(`OrderTopics`의 상수 전부)마다 `NewTopic`과 `{topic}.dlt`를 만든다. 파티션·복제 수와 만드는 방식은 `MemberTopicConfig`와 같다 |

**12) order.application**

`OrderEventPublisher` (`@Component`, 의존성 `OutboxPublisher`) — **Order 애그리거트를 받아 이벤트 record를 조립해 `OutboxPublisher`로 보내는 곳**. 이벤트 조립 코드가 `OrderPaymentApplier`·`OrderExpirationService`·`ShipmentService` 등에 흩어지지 않게 한다. 모든 메서드는 `@Transactional`을 붙이지 않는다(호출자 트랜잭션 안에서 불리고, `OutboxPublisher`가 `MANDATORY`로 검사한다). 인자는 문서에 정해져 있지 않아 아래는 권장안이다.

| 메서드 | 이벤트 · aggregate | 필드를 채우는 방법 |
|---|---|---|
| `paid(Order order)` | `ORDER_PAID` · `ORDER`/`order.getId()` | `orderId`·`memberId`는 `order.getId()`·`order.getMemberId()`, `totalAmount`는 `order.getTotalAmount().amount()`. `shopOrders`는 `order.getShopOrders()`의 가게주문마다 `PaidShopOrder(shopOrder.getId(), shopOrder.getShopId(), lines)`, `lines`는 품목마다 `PaidLine(line.getId(), line.getProductId(), line.getProductName(), line.getQuantity())` |
| `closed(Order order, String reason)` | `ORDER_EXPIRED` · `ORDER`/`order.getId()` | `OrderExpiredEvent(order.getId(), order.getMemberId(), reason)`. `reason`은 문자열 `"EXPIRED"` 또는 `"PAYMENT_FAILED"` |
| `shipped(...)` / `delivered(...)` | `SHOP_ORDER_SHIPPED` / `SHOP_ORDER_DELIVERED` · `ORDER`/**`orderId`** | 가게주문(`shopOrder.getId()`, `getCarrier()`, `getTrackingNumber()`)과 주문(`orderId`, `memberId`)에서 |
| `lineRefunded(...)` | `ORDER_LINE_REFUNDED` · `ORDER`/`orderId` | 환불(`refundId`, 금액)과 품목·주문 ID에서 |
| `lineConfirmed(...)` | `ORDER_LINE_CONFIRMED` · `ORDER`/`orderId` | 품목(`productId`, `confirmedAt`, **`confirmedAmount().amount()`**), 그 품목이 속한 가게주문의 `shopId`, 주문의 `memberId`에서. 품목 엔티티에는 `shopId`가 없어 가게주문을 거쳐야 한다 |

- `closed`의 `reason`은 이벤트의 값이다. `OrderPaymentApplier.fail(order, reason)`의 `reason`(`"PG 거절: ..."`, `failure_reason`에 저장되는 문장)과 다르다 — `fail` 안에서는 `closed(order, "PAYMENT_FAILED")`처럼 상수를 따로 넘긴다.
- **엔티티를 먼저 읽은 뒤 발행한다**: CAS UPDATE(`expire` 등)는 엔티티를 거치지 않으므로, `memberId`가 필요한 `closed`는 `OrderExpirationService.expireOne`에서 부른다. `expireOne`은 CAS(`orderRepository.expire`)로 상태를 바꾸고 `releaseReservation` 뒤 `findById`로 `order`를 읽어 `order.cancelPendingChildren()`을 부르는데, `closed(order, "EXPIRED")`는 그 다음에 같은 `order`로 부른다(`return true` 앞).
- **이미 처리된 호출은 발행하지 않는다**: `OrderPaymentApplier.applyPaymentState`의 "이미 처리됨" 가드, `RefundCompleter.complete`의 "`!readyToComplete()`면 현재 상태 반환" 가드에서 일찍 return하는 경로에는 발행 코드가 닿지 않는다. 발행은 **실제로 상태를 바꾼 경로에만** 둔다(`complete`·`fail`·`markPaid` 뒤). 그래야 같은 결과를 두 번 반영해도 이벤트가 한 번만 나간다.

| 흐름 | 클래스·메서드 | 발행 | aggregateType / aggregateId |
|---|---|---|---|
| 결제 완료 (2-4 승인, 2-6 대사) | `OrderEventPublisher.paid(order)` — `OrderPaymentApplier.complete`에서 호출 | `ORDER_PAID` | `ORDER` / orderId |
| 만료·결제 실패 (2-4, 2-5, 2-6) | `OrderEventPublisher.closed(order, reason)` — `OrderPaymentApplier.fail`, `OrderExpirationService.expireOne` (2-4의 결제 전 주문 취소 `PendingOrderCancelService`는 **발행하지 않는다** — 구매자가 직접 한 일이라 알림이 필요 없고 소비자가 알림뿐이다) | `ORDER_EXPIRED` | `ORDER` / orderId |
| 발송·배송완료 (2-7) | `ShipmentService.ship` / `deliver` | `SHOP_ORDER_SHIPPED` / `SHOP_ORDER_DELIVERED` | `ORDER` / orderId (같은 주문 이벤트 순서 유지) |
| 환불 완료 (2-8, 2-9) | `RefundCompleter.complete` | `ORDER_LINE_REFUNDED` | `ORDER` / orderId |
| 구매확정 (2-10) | `ConfirmPurchaseService.confirm`, `AutoConfirmService`(품목마다) | `ORDER_LINE_CONFIRMED` | `ORDER` / orderId |

- **`AutoConfirmService`**: 트랜잭션 안에서 받은 목록(`Order.autoConfirm`이 돌려주는 `List<OrderLine>`)을 순회하며 품목마다 `lineConfirmed`를 부르고, 건수는 `list.size()`로 센다.
- **`ConfirmPurchaseService.confirm`**의 `lineConfirmed` 호출(수동 구매확정): 현재 코드는 `order.confirmLine(lineId, clock.instant())` 다음 줄에서 `OrderLine line = order.findLine(lineId)`로 품목을 찾아 응답(`OrderLineResult`)을 만든다. 이 `line`을 `orderEventPublisher.lineConfirmed(order, order.shopOrderOf(lineId), line)`에도 넘겨(`shopId`가 가게주문에 있어 가게주문을 함께 받는다), `new OrderLineResult(...)` 앞에서 한 번 부른다. 품목을 다시 찾지 않고 `Order.confirmLine`의 반환 타입도 바꾸지 않는다.

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 값을 읽는 방법은 3-1과 같고, 이 단계에서 더하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `productSnapshotOf(UUID productId)`에 필드 추가 | `ProductSnapshot`(`support/ProductSnapshot.java`)에 필드 둘을 더하고 `productSnapshotOf`가 채운다. ① `long version` — 기존 상품 조회에 `product.product.version` 컬럼을 더 읽는다. ② `List<PriceChangeState> priceChanges` — `record PriceChangeState(long oldPrice, long newPrice, Instant changedAt)`를 새로 만들고, `SELECT old_price, new_price, changed_at FROM product.price_history WHERE product_id = ? ORDER BY changed_at, id`로 읽는다(`changed_at`은 `Timestamp`로 읽어 `toInstant()`). `ProductSnapshot`을 만드는 기존 코드도 새 필드에 맞춘다 | 상품 `EventPublishingTest` 1~6번 |
| `updateProduct(UUID memberId, UUID shopId, UUID productId, String name, Long price)` | `TestFixtures`에 `UpdateProductService`를 주입(`private final UpdateProductService updateProductService;`)하고 `updateProductService.update(memberId, shopId, productId, new UpdateProductCommand(name, null, null, price, null))`을 부른다. 바꾸지 않을 값은 `null`로 넘긴다 | 상품 `EventPublishingTest` 2~3번 |
| `changeProductStatus(UUID memberId, UUID shopId, UUID productId, ProductStatus to)` | `updateProductService.changeStatus(memberId, shopId, productId, to)`를 부른다. 이미 있는 `changeStatus(UUID, String)`은 회원 상태를 JDBC로 바꾸는 헬퍼라 이름이 겹치지 않게 `changeProductStatus`로 한다. `hideProduct`·`discontinueProduct`는 JDBC라 이벤트가 나가지 않으므로 이 케이스에는 쓰지 않는다 | 상품 `EventPublishingTest` 4·6번 |

이미 `TestFixtures`에 있는 헬퍼: `createDeliveredOrder(buyerId, sellerId, shopId, productIds...)`, `setShopOrderDeliveredAt(shopOrderId, Instant)`, `orderLineIdOf`, `snapshotOf(orderId)`(`line(productId).confirmedAt()` 포함), `setRefundUpdatedAt`, `signup`, `login`, `openShop`, `registerProduct`, `hideProduct`, `discontinueProduct`, `address`, `addToCart`, `checkout`, `createPendingOrder`, `confirmPayment`, `setOrderExpired`, `setOrderUpdatedAt`, `createPaidOrder`, `shopOrderIdOf`, `shipShopOrder`, `deliverShopOrder`, 3-1의 `OutboxAssert`·`countOf`.

헬퍼를 쓰는 방법은 다음과 같다.
- 이벤트는 `OutboxAssert.rows(토픽)`의 `OutboxSnapshot` 하나다. 확인 항목은 `aggregateType()`, `aggregateId()`와 payload의 필드 값이다(`eventType()`·`status()`는 3-1 `OutboxPublisherTest` 3번이 확인한다). payload 필드는 `payload().get("필드").asText()`(문자열·UUID), `asLong()`(금액·`version`), `asBoolean()`, `size()`(배열 길이)로 읽는다. "정확히 N건"은 `rows(토픽).size()`, "없다"는 0건이다.
- DB 값과 비교하는 필드: 상품 `productSnapshotOf(productId)`의 `version()`·`price()`, 가격 이력 `productSnapshotOf(productId).priceChanges()`(`oldPrice()`, `newPrice()`, `changedAt()`), 품목 상태·확정 시각·결제 키 `snapshotOf(orderId)`의 `line(productId).status()`·`line(productId).confirmedAt()`·`payment().paymentKey()`.
- 모든 케이스는 준비를 마친 직후 `OutboxAssert.clear()`로 outbox를 비운 뒤 동작한다(준비의 가입·상품 등록·주문이 이벤트를 만든다). "정확히 1건"은 비운 뒤 동작이 만든 행만 센다.
- 이벤트를 일으키는 방법은 서비스를 직접 부르는 헬퍼(`signup`, `registerProduct`, `createPaidOrder` 등)를 우선 쓰고, 응답·요청 형태가 의미 있는 곳만 MockMvc로 한다. 상품 수정·상태 변경은 위 `updateProduct`·`changeProductStatus` 헬퍼로 한다. 품목 취소는 `POST /api/orders/{orderId}/lines/{lineId}/cancel`(`Idempotency-Key`: 새 UUID, 본문 `{"reason":"단순 변심"}`), 구매확정은 `POST /api/orders/{orderId}/lines/{lineId}/confirm`(`Idempotency-Key` 없음), 결제 전 주문 취소는 `POST /api/orders/{orderId}/cancel`.

**공통 준비** (`EventPublishingTest` 세 클래스와 order의 세 클래스가 쓴다): 판매자 `seller@test.com`(`signup`, 토큰은 `login`), 가게 `openShop(sellerId, "1111111111")`, 구매자 `buyer@test.com`. 상품은 `product1`(`registerProduct(sellerId, shopId, 1000, 10)`), `product2`(2000), `product3`(3000). 이 픽스처의 상품은 이름 `product`, 설명 `description`, 카테고리 `ETC`, 정기구독 불가(`false`)다.

#### `member/application/EventPublishingTest`

**1. 가입** — 준비: `OutboxAssert.clear()`. 동작: `memberId = signup("test@test.com")`(닉네임 `test`).
- `rows("member.member-registered.v1")` 정확히 1건: `aggregateType()` = `MEMBER`, `aggregateId()` = memberId.
- payload: `memberId` = memberId 문자열, `email` = `test@test.com`, `nickname` = `test`. 필드는 이 셋뿐이다(`payload.size()` = 3. 비밀번호 같은 값이 새지 않는다).

#### `product/application/EventPublishingTest`

**1. 상품 등록** — 동작: `productId = registerProduct(sellerId, shopId, 1000, 10)`.
- `rows("product.product-upserted.v1")` 정확히 1건: `aggregateType()` = `PRODUCT`, `aggregateId()` = productId.
- payload: `productId`, `shopId` = 문자열 일치, `name` = `product`, `description` = `description`, `category` = `ETC`, `price` = 1000, `status` = `ON_SALE`, `subscribable` = false, `version` = `productSnapshotOf(productId).version()`.

**2. 가격 변경 없는 수정** — 준비: 상품 등록 → 등록 직후 `productSnapshotOf(productId).version()`을 `v0`로 읽음 → `OutboxAssert.clear()`. 동작: `updateProduct(sellerId, shopId, productId, "renamed", 1000L)`(가격은 같은 값).
- `product-upserted` 정확히 1건: payload `name` = `renamed`, `price` = 1000, `version` = `productSnapshotOf(productId).version()` = `v0 + 1`.
- `product-price-changed` 0건.

**3. 가격 변경** — 준비: 상품 등록 → `OutboxAssert.clear()`. 동작: `updateProduct(sellerId, shopId, productId, null, 1200L)`.
- `product-upserted` 정확히 1건: payload `price` = 1200, `version` = `productSnapshotOf(productId).version()`.
- `product-price-changed` 정확히 1건: `aggregateType()` = `PRODUCT`, `aggregateId()` = productId, payload `productId`, `oldPrice` = 1000, `newPrice` = 1200, `changedAt` = `productSnapshotOf(productId).priceChanges().get(0).changedAt()`과 같은 시각(둘 다 `truncatedTo(ChronoUnit.MILLIS)`로 맞춰 비교).

**4. 숨김 상태 변경** — 준비: 상품 등록 → `OutboxAssert.clear()`. 동작: `changeProductStatus(sellerId, shopId, productId, ProductStatus.HIDDEN)`.
- `product-upserted` 1건: payload `status` = `HIDDEN`, `version` = `productSnapshotOf(productId).version()`.
- `product-discontinued` 0건.

**5. 가게 폐업 단종** — 준비: `product1`은 그대로 `ON_SALE`, `hideProduct(product2Id)`(`HIDDEN`), `discontinueProduct(product3Id)`(이미 `DISCONTINUED`) → `OutboxAssert.clear()`. 동작: `productDiscontinueService.discontinueAllByShop(shopId)`를 직접 호출(서비스의 `@Transactional`이 경계다).
- `product-upserted` 정확히 2건: `aggregateId()`가 {`product1`, `product2`}, 둘 다 payload `status` = `DISCONTINUED`, `version` = 각 상품의 `productSnapshotOf(...).version()`.
- `product-discontinued` 정확히 2건: `aggregateId()`가 {`product1`, `product2`}, 둘 다 payload `reason` = `SHOP_CLOSED`, `shopId` = shopId 문자열.
- `product3`의 행은 없다(이미 단종된 상품은 이벤트를 만들지 않는다).
- 한 번 더 `discontinueAllByShop(shopId)` → 두 토픽 모두 건수 그대로(2건, 2건).

**6. 단종 상태 변경** — 준비: 4번의 준비와 동작까지(`HIDDEN`) → `OutboxAssert.clear()`. 동작: `changeProductStatus(sellerId, shopId, productId, ProductStatus.DISCONTINUED)`.
- `product-upserted` 1건: payload `status` = `DISCONTINUED`, `version` = `productSnapshotOf(productId).version()`.
- `product-discontinued` 1건: payload `productId`, `shopId`, `reason` = `SELLER`.

#### `order/application` 테스트 세 클래스

주문 이벤트는 흐름마다 부르는 서비스가 달라 세 클래스로 나눈다. 번호는 3클래스에 걸쳐 이어서 매기고(13~17은 각 클래스의 끝에 있다), 각 클래스는 아래 서비스만 주입받는다. 서비스를 직접 부르는 동작이 여러 케이스에 반복되면 `TestFixtures` 헬퍼로 옮긴다(예: JDBC로 `refunded_amount`를 바꾸는 10번의 준비).

| 클래스 | 케이스 | 주입받는 것 |
|---|---|---|
| `PaymentEventPublishingTest` | 1~5, 13 | `TestFixtures`, `OutboxAssert`, `ObjectMapper`, `OrderPaymentApplier`, `OrderReconcileService`, `OrderExpirationService`, `MockMvc`(5번) |
| `FulfillmentEventPublishingTest` | 6~8, 14~16 | `TestFixtures`, `OutboxAssert`, `RefundCompleter`, `RefundRecoveryService`, `MockMvc`(품목 취소 요청) |
| `ConfirmEventPublishingTest` | 9~12, 17 | `TestFixtures`, `OutboxAssert`, `AutoConfirmService`, `MockMvc`(구매확정 요청), `@MockitoSpyBean OrderEventPublisher` |

#### `PaymentEventPublishingTest`

**1. 결제 완료(order-paid)** — 준비: 구매자가 `createPendingOrder(buyerId, product1Id, product2Id)` → `OutboxAssert.clear()`. 동작: `confirmPayment(buyerId, orderId)`(가짜 PG 기본 승인).
- `rows("order.order-paid.v1")` 정확히 1건: `aggregateType()` = `ORDER`, `aggregateId()` = orderId.
- payload: `orderId`, `memberId` = buyerId 문자열, `totalAmount` = 3000, `shopOrders` 길이 1.
- `shopOrders[0]`: `shopOrderId` = `shopOrderIdOf(orderId, shopId)`, `shopId`, `lines` 길이 2.
- `lines`의 각 원소: `orderLineId` = `orderLineIdOf(orderId, productId)`, `productId`, `productName` = `product`, `quantity` = 1. `product1`과 `product2`가 하나씩 들어 있다.

**2. 대사로 늦게 확정된 결제** — 준비: `pg.on("/v1/payments/confirm", Behavior.delay(1000))` → `createPendingOrder(buyerId, product1Id)` → `confirmPayment`(202, 주문 `PAYMENT_IN_PROGRESS`) → `setOrderUpdatedAt(orderId, 지금 - 60초)` → `OutboxAssert.clear()`.
- 확인 1: `rows("order.order-paid.v1")` 0건(승인이 불확실한 동안 이벤트가 없다).
- 동작: `orderReconcileService.reconcileBatch()`.
- 확인 2: 반환값 1, `order-paid` 정확히 1건(`aggregateId()` = orderId, payload `totalAmount` = 1000).

**3. 만료** — 준비: `createPendingOrder(buyerId, product1Id)` → `setOrderExpired(orderId, 지금 - 1분)` → `OutboxAssert.clear()`. 동작: `orderExpirationService.expireBatch()`.
- 반환값 1.
- `rows("order.order-expired.v1")` 정확히 1건: `aggregateId()` = orderId, payload `orderId`, `memberId` = buyerId 문자열, `reason` = `EXPIRED`. 필드는 이 셋뿐이다.

**4. 결제 실패** — 준비: `pg.on("/v1/payments/confirm", Behavior.reject("REJECT_CARD_COMPANY"))` → `createPendingOrder(buyerId, product1Id)` → `OutboxAssert.clear()`. 동작: `confirmPayment(buyerId, orderId)`.
- `BusinessException`, `errorCode` = `PAYMENT_FAILED`.
- `order-expired` 정확히 1건: payload `reason` = `PAYMENT_FAILED`(`orders.orders.failure_reason`에 저장되는 `REJECT_CARD_COMPANY`가 든 문장이 아니다).
- `order-paid` 0건.

**5. 결제 전 주문 취소는 이벤트가 없다** — 준비: `createPendingOrder(buyerId, product1Id)` → `OutboxAssert.clear()`. 동작: `POST /api/orders/{orderId}/cancel`.
- 응답 200.
- `countOf("common.outbox_event")` = 0.

**13. 이미 반영한 결제 결과는 다시 반영해도 이벤트가 늘지 않는다** — 준비: 1번의 준비와 동작까지(`order-paid` 1건). 동작: `orderPaymentApplier.applyPaymentState(orderId, PaymentState.APPROVED, null)`.
- 반환값 `PAID`("이미 처리됨" 가드).
- `order-paid`는 그대로 1건.

#### `FulfillmentEventPublishingTest`

**6. 발송** — 준비: `createPaidOrder(buyerId, product1Id)` → `shopOrderId = shopOrderIdOf(orderId, shopId)` → `OutboxAssert.clear()`. 동작: `shipShopOrder(sellerId, shopId, shopOrderId)`.
- `rows("order.shop-order-shipped.v1")` 정확히 1건: `aggregateId()` = **orderId**(shopOrderId가 아니다), payload `shopOrderId`, `orderId`, `memberId` = buyerId 문자열, `carrier` = `CJ대한통운`, `trackingNumber` = `1234567890`.

**7. 환불 완료** — 준비: `createPaidOrder(buyerId, product1Id, product2Id)` → `lineId = orderLineIdOf(orderId, product1Id)` → `OutboxAssert.clear()`. 동작 1: `product1` 품목 취소 요청(HTTP) → 200.
- `rows("order.order-line-refunded.v1")` 정확히 1건: `aggregateId()` = orderId, payload `refundId` = 응답의 `$.refundId`, `orderLineId` = lineId, `orderId`, `memberId` = buyerId 문자열, `amount` = 1000.

**8. 환불이 끝나지 않았으면 이벤트가 없다** — 준비: 7번과 같은 주문에 `pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000))`(`paymentKey`는 `snapshotOf(orderId).payment().paymentKey()`) → `OutboxAssert.clear()`. 동작: `product1` 품목 취소 요청 → 202, `$.status` = `APPROVED`.
- `rows("order.order-line-refunded.v1")` 0건.

**14. 배송완료** — 준비: 6번의 준비와 동작까지(발송). 동작: `deliverShopOrder(sellerId, shopId, shopOrderId)`.
- `rows("order.shop-order-delivered.v1")` 정확히 1건: `aggregateId()` = orderId, payload `shopOrderId`, `orderId`, `memberId`.

**15. 이미 완료된 환불을 다시 완료해도 이벤트가 늘지 않는다** — 준비: 7번의 준비와 동작까지(품목 취소 200, `order-line-refunded` 1건). 동작: `refundCompleter.complete(refundId)`를 한 번 더 호출(이미 완료된 환불).
- `order-line-refunded` 건수 그대로 1건.

**16. 복구 배치가 환불을 끝내면 이벤트가 나간다** — 준비: 8번의 준비와 동작까지(환불이 끝나지 않은 채 `order-line-refunded` 0건. `refundId`는 202 응답의 `$.refundId`). 동작: 정상 응답으로 되돌리고(`pg.on(..., Behavior.delay(0))`) `setRefundUpdatedAt(refundId, 지금 - 6분)` → `refundRecoveryService.recoverBatch()`.
- 반환값 1.
- `order-line-refunded` 정확히 1건.

#### `ConfirmEventPublishingTest`

**9. 수동 구매확정** — 준비: `createDeliveredOrder(buyerId, sellerId, shopId, product1Id, product2Id)` → `lineId = orderLineIdOf(orderId, product1Id)` → `OutboxAssert.clear()`. 동작: 구매확정 요청(HTTP) → 200.
- `rows("order.order-line-confirmed.v1")` 정확히 1건: `aggregateId()` = orderId, payload `orderLineId` = lineId, `orderId`, `shopId`, `productId` = product1Id, `memberId` = buyerId 문자열, `amount` = 1000, `confirmedAt` = `snapshotOf(orderId).line(product1Id).confirmedAt()`(밀리초까지 맞춰 비교).
- `product2` 품목의 이벤트는 없다(품목마다 1건).

**10. 확정 금액은 환불을 뺀 값** — 준비: 9번의 주문에서 JDBC `UPDATE orders.order_line SET refunded_amount = 300 WHERE id = lineId` → `OutboxAssert.clear()`. 동작: 구매확정 요청.
- `order-line-confirmed` 1건, payload `amount` = 700(`lineAmount - refundedAmount`).

**11. 자동 구매확정은 품목마다 1건** — 준비: `createDeliveredOrder(buyerId, sellerId, shopId, product1Id, product2Id, product3Id)` → `setShopOrderDeliveredAt(shopOrderId, 지금 - 8일)` → `OutboxAssert.clear()`. 동작: `autoConfirmService.confirmBatch()`.
- 반환값 3.
- `rows("order.order-line-confirmed.v1")` 정확히 3건: `aggregateId()`가 모두 orderId, payload `orderLineId`의 집합 = 세 품목 ID, `amount`의 집합 = {1000, 2000, 3000}.

**12. 발행이 실패하면 상태 변경도 취소** — 준비: 9번의 주문과 `@MockitoSpyBean OrderEventPublisher`에 `doThrow(new IllegalStateException("test")).when(orderEventPublisher).lineConfirmed(any(), any(), any())`(`OutboxPublisher`는 `MANDATORY` 트랜잭션 프록시라 트랜잭션 밖에서 `when(...).publish(...)`를 부르면 스텁을 걸기 전에 예외가 난다. `OrderEventPublisher`는 트랜잭션 어노테이션이 없다. 주문 준비 단계는 `lineConfirmed`를 부르지 않으므로 준비 앞뒤 어디에 걸어도 되지만 동작 직전에 건다) → `OutboxAssert.clear()`. 동작: 구매확정 요청(HTTP) → 응답 500, `$.code` = `INTERNAL_ERROR`. `snapshotOf(orderId).line(product1Id).status()` = `DELIVERED`, / `countOf("common.outbox_event")` = 0(상태 변경과 이벤트가 한 트랜잭션이다).

**17. 이미 확정한 품목은 자동 구매확정을 다시 해도 이벤트가 늘지 않는다** — 준비: 11번의 준비와 동작까지(`confirmBatch()` = 3, `order-line-confirmed` 3건). 동작: `confirmBatch()`를 한 번 더.
- 반환값 0.
- `order-line-confirmed` 건수 그대로 3건.

#### `ModularityTest`

기존 `ModularityTest.verifyModules()`가 `ApplicationModules.of(MyRoutineApplication.class).verify()`에서 예외 없이 끝난다. 이 단계에서 `order/api/package-info.java`(`@NamedInterface("api")`)가 새로 생기므로 `order.api`·`order.api.event`가 다른 모듈에서 import 가능한 이름 있는 인터페이스인지가 여기서 갈린다. 실패하면 메시지에 나온 모듈 의존 위반을 `package-info.java`의 `allowedDependencies`나 `@NamedInterface` 위치로 고친다.

**리뷰 때 물어볼 것**
- 이벤트에 어떤 필드를 넣을지 어떻게 정했나? (너무 많으면? 너무 적으면?)
- 발송 이벤트의 키를 shopOrderId가 아니라 orderId로 한 이유는?

---

## 3-4. 정산: 적재 · 정산 잡 · 지급 (L)
> PR 둘: **3-4a** 적재 + 정산 잡, **3-4b** 지급 + 조회

**목표**: 구매확정 품목을 가게별로 월 1회 정산하고, 수수료를 뺀 금액을 판매자에게 지급한다(은행 송금은 Mock). 정확히 한 번.

**왜 지금**: 3-3의 `order-line-confirmed` 이벤트가 생겼다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| keyset 배치 | 가게 ID 순서로 500개씩. offset 페이징으로 상태를 바꾸며 읽으면 누락된다(As-Is BAT-02, ADR-009) |
| 정산 1회 보장 | `settlement(shop_id, period_start)` unique + 품목에 정산 ID 할당 |
| 할당 후 합계 | 합계를 "조건에 맞는 품목"이 아니라 **"이번 정산 ID가 붙은 품목"**으로 계산한다. 계산 도중 새 품목이 들어와도 숫자가 어긋나지 않는다 |
| 외부 송금 원칙 | 지급(송금) 호출은 트랜잭션 밖에서, 멱등 키(settlementId)로. 결과는 호출 뒤 짧은 트랜잭션으로 기록한다 (As-Is WAL-02의 교훈) |

**정책 (이 단계에서 정함)**
- 정산 주기: 매월 5일 03:00(Asia/Seoul)에 **전월** 구매확정분(POL-12). 기간은 업무 날짜 기준 전월 1일 00:00 ~ 당월 1일 00:00(미포함).
- 수수료: 500bp(5%), 설정 `myroutine.settlement.fee-rate-bp: 500`. 원 미만 내림(OPEN-06): `fee = gross * bp / 10000` (정수 나눗셈)
- 정산액 0원인 정산은 입금 없이 PAID.
- 정산 실행 뒤에 늦게 도착한 전월 품목은 그 정산에 들어가지 않는다 → 다음 달 정산 기간에 맞지 않으므로 남는다. 정산 전에 consumer 지연이 없는지 확인하는 것을 운영 절차로 둔다(ADR-009). 남은 품목은 Part 7 점검 대상.
- 관리자 수동 실행: `POST /admin/api/settlements/run?period=2026-09` (ADMIN 역할). 관리자 계정은 DB에서 `role`을 `ADMIN`으로 직접 바꿔 만든다.

### 할 일 (3-4a)

**흐름 한눈에**
1. **적재**: 구매확정(3-3)이 `order-line-confirmed`를 내면 settlement의 consumer가 품목 한 건을 `settlement_item`에 쌓는다(`settlement_id`는 NULL = 아직 정산 전).
2. **정산**: 매월 5일 03:00에 `SettlementJob`이 `SettlementService.run(전월)`을 부른다. 미정산 품목이 있는 가게마다 `settleShop`이 `settlement` 행을 만들고, 그 기간의 품목에 정산 ID를 붙이고(`assign`), 붙은 품목의 합계로 수수료·정산액을 계산한다(`CALCULATED`).
3. **지급**(3-4b): 이어서 `payoutAll()`이 `CALCULATED`·`PAYOUT_FAILED` 정산마다 `PayoutGateway`로 송금하고 `PAID` + `settlement-paid` 이벤트를 낸다.

**구현 순서**: ① 1) 마이그레이션 → ② 설정: 2) `common.config`, 5)의 `SettlementProperties`·`SettlementConfig`, `settlement/package-info.java` → 도메인: 3) `settlement.domain` → 저장소: 4)의 `JpaSettlementItemRepository`·`JpaSettlementRepository` → 서비스·consumer: 5)의 `SettlementItemService`·`SettlementRunResult`·`SettlementService`, 4)의 `OrderEventConsumer` → 잡: 4)의 `SettlementJob` → ③ 정산 대상 JDBC 적재 헬퍼 → 테스트(`SettlementStatusTest` → `SettlementPeriodTest` → `SettlementItemConsumerTest` → `SettlementServiceTest`).

**1) 마이그레이션** — `db/migration/settlement/V{...}__settlement_create_settlement.sql` (`CREATE SCHEMA IF NOT EXISTS settlement;`)

`settlement.settlement`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_settlement` |
| shop_id | uuid | X | `uk_settlement_shop_period (shop_id, period_start)` |
| period_start, period_end | date | X | period_end는 포함(말일) |
| gross_amount, fee_amount, net_amount | bigint | X | DEFAULT 0, `ck_settlement_net`: `net_amount = gross_amount - fee_amount` |
| fee_rate_bp | int | X | |
| item_count | int | X | DEFAULT 0 |
| status | varchar(20) | X | `ck_settlement_status`: `IN ('CALCULATED','PAID','PAYOUT_FAILED')` |
| payout_attempts | int | X | DEFAULT 0 |
| payout_reference | varchar(100) | O | 지급 게이트웨이가 돌려준 이체 참조 ID (3-4b) |
| last_error | varchar(500) | O | |
| paid_at | timestamptz | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 인덱스 `idx_settlement_status (status)`, `idx_settlement_shop_id_period_start (shop_id, period_start DESC)`

`settlement.settlement_item`

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_settlement_item` |
| order_line_id | uuid | X | `uk_settlement_item_order_line` (INV-07) |
| shop_id, order_id, product_id | uuid | X | |
| amount | bigint | X | `>= 0` |
| confirmed_at | timestamptz | X | |
| settlement_id | uuid | O | `fk_settlement_item_settlement`, null = 미정산 |
| created_at, updated_at | timestamptz | X | |

- 부분 인덱스 `idx_settlement_item_unsettled ON settlement.settlement_item (shop_id, confirmed_at) WHERE settlement_id IS NULL`, `idx_settlement_item_settlement_id (settlement_id)`

**2) common.config**

| 클래스 | 규격 |
|---|---|
| `BusinessProperties` | 업무 시간대 설정을 처음 읽는 곳이라 여기서 만든다: `@ConfigurationProperties("myroutine") @Validated record (@NotNull ZoneId businessZone)`. `application.yaml`의 `myroutine.business-zone: Asia/Seoul`은 이미 있고 읽는 클래스만 없다. "정산 월의 경계"처럼 날짜가 필요한 곳에서 `businessProperties.businessZone()`을 쓴다 |
| `ClockConfig` | `@EnableConfigurationProperties(BusinessProperties.class)`를 더한다 |

**3) settlement.domain** — 표의 행은 클래스 단위이고, `Settlement`·`SettlementPeriod`·저장소 메서드의 규격은 표 뒤에 메서드별로 적었다.

| 클래스 | 규격 |
|---|---|
| `SettlementStatus` | 상태 enum. 허용 전이: `CALCULATED → {PAID, PAYOUT_FAILED}`, `PAYOUT_FAILED → {PAID, PAYOUT_FAILED}`(실패한 정산을 다시 시도). `ShopStatus.transitTo`처럼 `transitTo(to)`가 허용 밖이면 `INVALID_STATE_TRANSITION`을 던진다 |
| `Settlement` | `@Entity`. 정산 한 건. 필드는 컬럼과 1:1. 메서드는 아래 `Settlement` 메서드 표 |
| `SettlementPeriod` | 정산 기간 값 객체 `record (LocalDate start, LocalDate end)`. 메서드는 아래 `SettlementPeriod` 절 |
| `SettlementSum` | `interface SettlementSum { long getGrossAmount(); int getItemCount(); }` — `SettlementItemRepository.sumBySettlementId`의 반환(인터페이스인 이유는 아래 `sumBySettlementId`) |
| `SettlementItemRepository` | 정산 대상 품목 저장소(인터페이스). 구현은 `settlement.infrastructure`의 `JpaSettlementItemRepository`. 메서드는 아래 표. 이 단계에서 쓰는 4개만 만든다 |
| `SettlementRepository` | 정산 저장소(인터페이스). 구현은 `settlement.infrastructure`의 `JpaSettlementRepository`. 메서드는 아래 표. `findIdsToPay`는 3-4b의 `payoutAll`이 쓴다 |

**`Settlement` 메서드** (`Money`는 `common.model.Money`, `Money.of(long)`·`.amount()`)

| 메서드 | 하는 일 |
|---|---|
| (생성) | 엔티티를 `save`하지 않고 `SettlementRepository.insertIfAbsent`(native INSERT)로 만든다 — 같은 가게·기간이 이미 있으면 예외가 아니라 0행으로 알려 주기 위해서다. 그 뒤 `findById`로 읽어 아래 메서드를 쓴다 |
| `calculate(Money gross, int bp, int itemCount)` | `grossAmount = gross`, `feeRateBp = bp`, `itemCount`를 기록하고 `feeAmount`는 정책의 수수료 식으로(12,345원·500bp면 617), `netAmount = gross - feeAmount` |
| `markPaid(String payoutReference, Instant now)` | `status.transitTo(PAID)`, `payoutReference`, `paidAt = now`. 0원 정산은 `payoutReference`에 `null` |
| `markPayoutFailed(String error)` | `status.transitTo(PAYOUT_FAILED)`, `lastError = error`. `payoutAttempts`를 1 올린다(실패 전이와 시도 횟수가 한 메서드에서 같이 바뀐다. `markPaid`는 올리지 않는다) |

**`SettlementPeriod`** — "2026년 9월 정산"이 어느 날짜·시각 구간인지를 한곳에서 계산한다. 시간대를 잘못 쓰면 경계(8/31 23:59 대 9/1 00:00)의 품목이 다른 달로 간다.
- `static SettlementPeriod of(YearMonth month)`: `start = month.atDay(1)`, `end = month.atEndOfMonth()`(말일, **포함**. `period_end` 컬럼도 포함이다)
- `Instant fromInclusive(ZoneId zone)`: `start.atStartOfDay(zone).toInstant()` — 시작 시각(포함)
- `Instant toExclusive(ZoneId zone)`: `end.plusDays(1).atStartOfDay(zone).toInstant()` — 다음 달 1일 00:00(미포함). 쿼리는 `confirmed_at >= from AND confirmed_at < to`
- 2026-09, `Asia/Seoul` → `2026-08-31T15:00:00Z` ~ `2026-09-30T15:00:00Z`(완료 확인 `SettlementPeriodTest`). 순수 계산이라 단위 테스트로 검증한다.

**`SettlementItemRepository`** — domain 인터페이스 + infrastructure의 Spring Data 인터페이스에 native `@Query`(Part 2와 같은 방식). 조회 API용 목록 메서드는 3-4b에서 더한다.

| 메서드 | 의미 | 입력 → 반환 |
|---|---|---|
| native `void insertIgnoringConflict(...)` | 구매확정 품목 한 건을 적재한다. `order_line_id` unique(`uk_settlement_item_order_line`, INV-07)와 충돌하면 `ON CONFLICT ON CONSTRAINT ... DO NOTHING`으로 조용히 건너뛴다 | `id`(`Ids.newId()`), `orderLineId`, `shopId`, `orderId`, `productId`, `amount`, `confirmedAt`, 시각 → 없음 |
| `List<UUID> findShopIdsToSettle(Instant from, Instant to, UUID afterShopId, int limit)` | 기간 내 **미정산** 품목이 있는 가게 ID를 `afterShopId` 다음부터 오름차순으로 `limit`개 | → 가게 ID 목록(비면 끝) |
| `void assign(UUID settlementId, UUID shopId, Instant from, Instant to, Instant now)` | 그 가게의 기간 내 미정산 품목에 이번 정산 ID를 붙인다. 품목 수는 `sumBySettlementId`가 센다 | → 없음 |
| `SettlementSum sumBySettlementId(UUID settlementId)` | 이 정산 ID가 붙은 품목의 합계와 건수 | → `SettlementSum`(`getGrossAmount()`, `getItemCount()`) |

- `sumBySettlementId`는 인터페이스 프로젝션으로 받는다(`SettlementSum`이 인터페이스인 이유다. native 쿼리는 `select new`를 쓸 수 없고, record 매핑은 확인되지 않아 별칭으로 매핑되는 인터페이스 프로젝션을 쓴다). SQL은 `SELECT COALESCE(SUM(amount), 0)::bigint AS "grossAmount", COUNT(*)::int AS "itemCount" FROM settlement.settlement_item WHERE settlement_id = :settlementId`다. PostgreSQL에서 `SUM(bigint)`은 `numeric`, `COUNT(*)`는 `bigint`라서 `::bigint`·`::int`로 캐스팅하고, 별칭은 따옴표로 감싸야 대소문자가 유지돼 getter 이름(`getGrossAmount` ↔ `grossAmount`)과 맞는다. 매핑이 틀리면 `SettlementServiceTest` 1번(합계 15000·건수 2)이 실패한다.

**`SettlementRepository`**

| 메서드 | 의미 | 입력 → 반환 |
|---|---|---|
| native `int insertIfAbsent(UUID id, UUID shopId, LocalDate start, LocalDate end, int bp, Instant now)` | 그 가게·기간의 정산 행을 만든다. `uk_settlement_shop_period`와 충돌하면 `ON CONFLICT ON CONSTRAINT ... DO NOTHING` | → 1(내가 만듦) / 0(이미 있음 = 이 기간은 이미 정산됨) |
| `Optional<Settlement> findById(UUID id)` | 정산 읽기 | |
| `List<UUID> findIdsToPay(Collection<SettlementStatus> statuses, UUID afterId, Limit limit)` | 지급할 정산 ID를 `afterId` 다음부터 `limit`개 (`org.springframework.data.domain.Limit`) | → ID 목록 |

- native INSERT는 JPA를 거치지 않아 `@Version`·`@CreatedDate`가 동작하지 않는다. `NOT NULL`인 `version`(0)·`created_at`·`updated_at`·`status`를 INSERT에서 직접 채운다. `status`는 `'CALCULATED'`이고, 금액·`item_count`·`payout_attempts`는 컬럼 DEFAULT 0에 맡긴다(`calculate`가 곧바로 채운다).

```sql
-- findShopIdsToSettle: 미정산 품목이 있는 가게 ID를 keyset으로
SELECT DISTINCT shop_id FROM settlement.settlement_item
 WHERE settlement_id IS NULL AND confirmed_at >= :from AND confirmed_at < :to AND shop_id > :afterShopId
 ORDER BY shop_id LIMIT :limit
-- assign: 이번 정산 ID를 붙인다
UPDATE settlement.settlement_item SET settlement_id = :settlementId, updated_at = :now
 WHERE shop_id = :shopId AND settlement_id IS NULL AND confirmed_at >= :from AND confirmed_at < :to
```
- 첫 페이지의 `afterShopId`는 `'00000000-0000-0000-0000-000000000000'`

**4) settlement.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaSettlementItemRepository` | `SettlementItemRepository`의 구현(Spring Data 인터페이스가 domain 인터페이스를 상속, native `@Query`, Part 2와 같은 방식). `settlement_item`에는 엔티티가 없어 `JpaRepository<엔티티, UUID>`를 상속할 수 없으므로 `@RepositoryDefinition(domainClass = Settlement.class, idClass = UUID.class)`를 붙여 저장소로 등록한다(`Repository`를 상속하거나 이 어노테이션이 없으면 Spring Data가 스캔하지 않아 빈이 없다. 메서드가 모두 `@Query`라서 `domainClass`는 형식상 값이다) |
| `JpaSettlementRepository` | `SettlementRepository`의 구현 |
| `OrderEventConsumer` | `order-line-confirmed`를 받는 `@KafkaListener` 어댑터. 의존성 `EventEnvelopes`, `InboxGuard`, `SettlementItemService`. 동작은 표 아래 |
| `SettlementJob` | `@Scheduled(cron = "0 0 3 5 * *", zone = "Asia/Seoul")` → `jobRunner.run("settlement", () -> service.run(전월).settledShops())`. `전월`은 `YearMonth.now(clock.withZone(businessZone)).minusMonths(1)`처럼 업무 시간대 기준 현재 월에서 한 달을 뺀 값이다(`JobRunner.run`은 `Supplier<Integer>`를 받아 `settledShops()`를 처리 건수로 로그에 남긴다) |

- 적재 consumer 동작: `OrderEventConsumer`의 `@KafkaListener(topics = OrderTopics.ORDER_LINE_CONFIRMED, groupId = "settlement")` 메서드가 `parse(message, OrderLineConfirmedEvent.class)` → `inboxGuard.runOnce("settlement.order-line-confirmed", typed.envelope(), () -> itemService.register(typed.payload()))`.

**5) settlement.application**

| 클래스 | 규격 |
|---|---|
| `SettlementProperties` | `OrderProperties`와 같은 위치에 둔다. `@ConfigurationProperties("myroutine.settlement") @Validated record (@Min(0) @Max(10000) int feeRateBp)`(`@Validated`가 없으면 제약이 시작 시점에 검증되지 않는다. `OrderProperties`와 같다) — `application.yaml`에 `myroutine.settlement.fee-rate-bp: 500`(500 = 5%)을 추가한다 |
| `SettlementConfig` | `OrderConfig`와 같은 위치에 둔다. `@Configuration`에 `@EnableConfigurationProperties(SettlementProperties.class)`(`OrderConfig`와 같은 방식) |

| 클래스 | 메서드 |
|---|---|
| `SettlementItemService` | `@Service`. `@Transactional void register(OrderLineConfirmedEvent event)` — 품목 한 건을 적재한다. `OrderEventConsumer`가 부른다 |
| `SettlementRunResult` | `record SettlementRunResult(int settledShops, int paid, int payoutFailed)` — `SettlementService.run`의 반환이고 `POST /admin/api/settlements/run`(3-4b)의 응답이다 |
| `SettlementService` | `@Service`. `public SettlementRunResult run(YearMonth month)`와 가게 한 곳을 정산하는 `settleShop`. 가게 단위 트랜잭션과 지급의 외부 호출을 직접 나누려고 `@Transactional` 없이 `TransactionTemplate`을 쓴다(공통 규칙 K). 의존성 `SettlementRepository`, `SettlementItemRepository`, `SettlementProperties`, `BusinessProperties`, `Clock`, `TransactionTemplate tx`. 순서는 아래 "정산 실행" |

- `register`: 이벤트 필드를 그대로 `insertIgnoringConflict`에 넘긴다 — `orderLineId`, `shopId`, `orderId`, `productId`, `amount`(`long`), `confirmedAt`(이미 `Instant`), 새 `id = Ids.newId()`.
- 중복 방어가 둘이다: `InboxGuard`(같은 이벤트 두 번 → 한 번)와 `uk_settlement_item_order_line`(다른 eventId로 같은 품목이 와도 한 번 — 최종 방어, INV-07). 완료 확인 `SettlementItemConsumerTest`가 앞쪽을 확인한다.
- `settlement/package-info.java`: `@ApplicationModule(allowedDependencies = {"common", "order::api"})`. 현재 settlement 모듈에는 코드가 없어 파일이 없다. `shop::api`는 3-4b 조회 API(`verifyOwner`)가, `member::api`는 3-6(`WithdrawalPrecondition`)이 쓸 때 더한다(공통 규칙 S는 Part 3 끝 기준 값이다)

**정산 실행** — `SettlementService`의 동작.

`public SettlementRunResult run(YearMonth month)` — `month`는 **정산 대상 월**이다(9월 구매확정분이면 `2026-09`). `SettlementJob`과 관리자 API가 부른다. `settledShops`는 이번 실행에서 정산을 새로 만든 가게 수다
1. `period = SettlementPeriod.of(month)`, `from = period.fromInclusive(zone)`, `to = period.toExclusive(zone)` (`zone = businessProperties.businessZone()`)
2. keyset 루프: `after`는 `00000000-0000-0000-0000-000000000000`에서 시작 → `shopIds = findShopIdsToSettle(from, to, after, 500)` → 비면 종료 → 각 가게 `try { created = tx.execute(s -> settleShop(shopId, period)) } catch (Exception e) { log }` → `created`가 `true`일 때만 `settledShops`를 올린다 → `after = 마지막 shopId`. 한 가게가 실패해도 `after`는 계속 전진시켜야 한다(안 그러면 같은 가게를 영원히 반복한다). 실패한 가게는 커밋된 것이 없어서 다음 실행(수동 실행 포함)이 다시 처리한다.
3. `new SettlementRunResult(settledShops, 0, 0)`을 돌려준다(지급이 없어 `paid`·`payoutFailed`는 0)

`boolean settleShop(shopId, period)` — 가게 하나의 정산을 만들고, 새로 만들었으면 `true`, 이미 있어 건너뛰었으면 `false`를 돌려준다. **한 트랜잭션**(`tx.execute` 안에서 호출. 같은 클래스 안에서 부르므로 `@Transactional`이 아니라 `TransactionTemplate`이 경계다)
1. `id = Ids.newId()`, `insertIfAbsent(id, shopId, period.start(), period.end(), properties.feeRateBp(), now)` → 0이면 `return false`(이미 이 기간 정산이 있음. 그 경우 `assign`을 하지 않으므로 이미 끝난 정산에 품목이 더 붙지 않는다)
2. `assign(id, shopId, from, to, now)` — 기간 내 미정산 품목에 `id`를 붙인다. 이 UPDATE가 끝나면 이 품목들은 다른 정산에 못 들어간다.
3. `sum = sumBySettlementId(id)` — 이번 정산 ID가 **붙은** 품목으로 합계를 낸다(조건으로 다시 찾지 않으므로 2와 3 사이에 새 품목이 들어와도 숫자가 어긋나지 않는다)
4. `settlement = findById(id)` → `settlement.calculate(Money.of(sum.getGrossAmount()), properties.feeRateBp(), sum.getItemCount())` → fee·net 계산, CALCULATED. 저장은 커밋 때 더티 체킹이다. `Settlement`의 금액 필드(`grossAmount`·`feeAmount`·`netAmount`)는 `Order.totalAmount`처럼 `Money`(컬럼은 bigint, `MoneyConverter`)이고 프로젝션의 합계는 `long`이라 `Money.of(...)`로 감싼다
5. `return true`

### 완료 확인 (3-4a)

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 값을 읽는 방법은 3-1·3-2와 같고, 이 단계에서 더하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `insertSettlementItem(UUID shopId, long amount, Instant confirmedAt)` → `UUID`(만든 품목의 `orderLineId`) | JDBC `INSERT INTO settlement.settlement_item (id, order_line_id, shop_id, order_id, product_id, amount, confirmed_at, settlement_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, now(), now())`. `id`·`order_line_id`·`order_id`·`product_id`는 `Ids.newId()`. `Instant`는 `OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)`로 넘긴다 | 기간 경계·늦은 품목·3-6 |
| `insertSettlementItems(List<UUID> shopIds, int itemsPerShop, long amount, Instant confirmedAt)` → `void` | 위와 같은 INSERT를 `jdbcTemplate.batchUpdate`로 가게마다 `itemsPerShop`개 | 1,200개 가게, 둘 이상의 가게 |
| `settlementSnapshotOf(UUID shopId, YearMonth month)`, `settlementSnapshotOf(UUID shopId)` → `SettlementSnapshot` | 쿼리 2개: 정산(`settlement.settlement`의 `id`, `period_start`, `period_end`, `status`, `gross_amount`, `fee_amount`, `net_amount`, `fee_rate_bp`, `item_count`, `payout_attempts`, `payout_reference`, `last_error`, `paid_at` — `WHERE shop_id = ? AND period_start = ?`에 `month.atDay(1)`), 그 정산에 붙은 품목(`settlement.settlement_item`의 `order_line_id`, `amount`, `confirmed_at` — `WHERE settlement_id = ? ORDER BY confirmed_at, order_line_id`). `support/`의 record로 `id`(UUID), `periodStart`·`periodEnd`(`LocalDate`), `status`·`payoutReference`·`lastError`(String, 뒤 둘은 없으면 null), `grossAmount`·`feeAmount`·`netAmount`(long), `feeRateBp`·`itemCount`·`payoutAttempts`(int), `paidAt`(`Instant`, 없으면 null), `items`(`List<ItemState(UUID orderLineId, long amount, Instant confirmedAt)>`)를 가진다. 정산이 없으면 `null`. 달을 받지 않는 `settlementSnapshotOf(shopId)`는 `period_start` 조건 없이 읽고 정산이 둘 이상이면 `IllegalStateException`을 던진다. 지급 필드(`payoutAttempts` 이하)는 3-4b가 쓴다 | 3-4a·3-4b `SettlementServiceTest`, `SettlementControllerTest` |
| `settlementItemSnapshotOf(UUID orderLineId)` → `SettlementItemSnapshot` | JDBC `SELECT i.id, i.shop_id, i.order_id, i.product_id, i.amount, i.confirmed_at, i.settlement_id, s.period_start FROM settlement.settlement_item i LEFT JOIN settlement.settlement s ON s.id = i.settlement_id WHERE i.order_line_id = ?`. `support/`의 record로 `id`·`shopId`·`orderId`·`productId`·`settlementId`(UUID, `settlementId`는 미정산이면 null), `amount`(long), `confirmedAt`(`Instant`), `periodStart`(`LocalDate`, 미정산이면 null)를 가진다. 행이 없으면 `null` | `SettlementItemConsumerTest`, `SettlementServiceTest` |
| `unsettledItemCount()` → `long` | JDBC `SELECT COUNT(*) FROM settlement.settlement_item WHERE settlement_id IS NULL` | `SettlementServiceTest` 1·5번, `SettlementControllerTest` 5번 |

이미 있는 헬퍼: 3-1의 `Eventually.await(Duration timeout, ThrowingRunnable assertion)`·`countOf(String table)`(`TestFixtures`), 3-2의 `TestKafka.send(String topic, String key, UUID eventId, Object event)`(봉투를 만들어 보내고 파티션 번호를 돌려준다)·`processedCountOf(String consumer, UUID eventId)`.

헬퍼를 쓰는 방법은 다음과 같다.
- 정산: 가게의 정산이 하나면 `settlementSnapshotOf(shopId)` 한 번으로 읽고, 달이 여럿이면 `settlementSnapshotOf(shopId, YearMonth.of(2026, 9))`로 달을 정한다. 정산 건수는 `countOf("settlement.settlement")`.
- 정산 대상 품목 하나: `settlementItemSnapshotOf(orderLineId)`. 품목 수는 `countOf("settlement.settlement_item")`, 미정산 품목 수는 `unsettledItemCount()`.
- 실행 결과: `SettlementRunResult result = settlementService.run(YearMonth.of(2026, 9))`의 `result.settledShops()`.

3-4a에는 지급이 없으므로 `run` 뒤 정산 `status`는 `CALCULATED`다. 3-4b에서 `run`이 끝에 `payoutAll()`을 부르면 `PAID`로 바뀌므로, 그때 이 단계 케이스의 `status` 단언만 `PAID`로 고친다.

**공통 준비** (`SettlementServiceTest`): 정산 대상은 이벤트가 아니라 JDBC로 직접 적재한다. 정산 월은 `YearMonth.of(2026, 9)`, 업무 시간대 `Asia/Seoul`, 수수료율 500bp(기본 설정), 품목 확정 시각은 기간 한가운데인 `Instant.parse("2026-09-15T03:00:00Z")`(KST 9월 15일 12시)로 쓴다. 가게는 `Ids.newId()`로 만든 UUID이며 `shop` 모듈에 실제 가게가 없어도 된다. 가게 1곳의 품목이 2개이고 각 7,500원이면 `gross` 15,000, `fee` 750(15,000 × 500 / 10,000), `net` 14,250이다.

#### `settlement/domain/SettlementStatusTest` (순수 JUnit, `@ParameterizedTest`로 3×3 = 9쌍)

| 케이스 | 확인할 것 |
|---|---|
| 허용 4쌍: `CALCULATED→PAID`, `CALCULATED→PAYOUT_FAILED`, `PAYOUT_FAILED→PAID`, `PAYOUT_FAILED→PAYOUT_FAILED` | `from.transitTo(to)`의 반환값이 `to`다 |
| 허용 밖 5쌍: `CALCULATED→CALCULATED`, `PAID→CALCULATED`, `PAID→PAID`, `PAID→PAYOUT_FAILED`, `PAYOUT_FAILED→CALCULATED` | `BusinessException`이 던져지고 `getErrorCode()` = `CommonErrorCode.INVALID_STATE_TRANSITION` |

#### `settlement/domain/SettlementPeriodTest`

**1. 정산 기간의 경계 시각** — 동작: `SettlementPeriod period = SettlementPeriod.of(YearMonth.of(2026, 9))`, `ZoneId zone = ZoneId.of("Asia/Seoul")`.
- `period.start()` = `LocalDate.of(2026, 9, 1)`, `period.end()` = `LocalDate.of(2026, 9, 30)`(말일, 포함).
- `period.fromInclusive(zone)` = `Instant.parse("2026-08-31T15:00:00Z")`.
- `period.toExclusive(zone)` = `Instant.parse("2026-09-30T15:00:00Z")`.

**2. 수수료는 원 미만 내림** — 준비: `Settlement settlement = BeanUtils.instantiateClass(Settlement.class)`(`org.springframework.beans.BeanUtils`. 엔티티는 native INSERT로만 만들어 테스트용 생성 메서드가 없으므로 기본 생성자를 리플렉션으로 부른다). 동작: `settlement.calculate(Money.of(gross), bp, itemCount)`. 값은 `getGrossAmount().amount()`·`getFeeAmount().amount()`·`getNetAmount().amount()`(`Money`), `getFeeRateBp()`, `getItemCount()`로 읽는다.

| gross | bp | itemCount | `feeAmount` | `netAmount` |
|---|---|---|---|---|
| 12345 | 500 | 3 | 617 (617.25의 내림) | 11728 |
| 19 | 500 | 1 | 0 (0.95의 내림) | 19 |
| 0 | 500 | 0 | 0 | 0 |

모든 행에서 `getGrossAmount()` = gross, `getFeeRateBp()` = bp, `getItemCount()` = itemCount다.

#### `settlement/application/SettlementItemConsumerTest`

준비: `orderId`, `shopId`, `productId`, `memberId`는 `Ids.newId()`. 이벤트 `OrderLineConfirmedEvent(orderLineId1, orderId, shopId, productId, memberId, 15000, Instant.parse("2026-09-15T03:00:00Z"))`(레코드 필드 순서는 `UUID orderLineId, UUID orderId, UUID shopId, UUID productId, UUID memberId, long amount, Instant confirmedAt`). 보낼 때는 `TestKafka.send(OrderTopics.ORDER_LINE_CONFIRMED, orderId.toString(), eventId, event)`.

**1. 같은 확정 이벤트 두 번** — 동작: 같은 `eventId1`로 두 번 보낸 뒤, 센티널로 같은 키에 다른 `eventId2`·다른 `orderLineId2`의 이벤트를 한 번 더 보낸다.
- `Eventually`(5초) 안에서 `settlementItemSnapshotOf(orderLineId2)`가 `null`이 아니다.
- `settlementItemSnapshotOf(orderLineId1)`: `shopId()` = shopId, `orderId()` = orderId, `productId()` = productId, `amount()` = 15000, `confirmedAt()` = 이벤트의 `confirmedAt`, `settlementId()` null.
- `countOf("settlement.settlement_item")` = 2.

**2. 다른 eventId로 같은 품목이 와도 한 번** — InboxGuard를 거치지 않는 최종 방어(`uk_settlement_item_order_line`)를 확인한다. 동작: `settlementItemService.register(event)`를 `amount` 15000으로 한 번, 같은 `orderLineId1`에 `amount` 99999로 한 번 더 호출.
- 두 번째 호출도 예외 없이 끝난다.
- `countOf("settlement.settlement_item")` = 1이고 `settlementItemSnapshotOf(orderLineId1).amount()` = 15000(두 번째 값 99999가 덮어쓰지 않는다).

#### `settlement/application/SettlementServiceTest`

**1. 가게 1,200곳 정산** — 준비: 가게 1,200곳 × 품목 2개(각 7,500원) = 2,400행(`insertSettlementItems`). 가게 수가 `findShopIdsToSettle`의 한 페이지(500)보다 커서 keyset이 3페이지(500, 500, 200)를 돈다. 동작: `settlementService.run(YearMonth.of(2026, 9))`.
- `result.settledShops()` = 1200.
- `countOf("settlement.settlement")` = 1200.
- `SELECT COUNT(*) FROM settlement.settlement WHERE gross_amount = 15000 AND item_count = 2 AND fee_rate_bp = 500 AND period_start = DATE '2026-09-01' AND period_end = DATE '2026-09-30' AND status = 'CALCULATED'` = 1200.
- `unsettledItemCount()` = 0.

**2. run을 두 번** — 준비: 가게 3곳 × 품목 2개(각 7,500원). 동작: `run`을 두 번(결과를 `first`, `second`로 받는다).
- `first.settledShops()` = 3, `second.settledShops()` = 0.
- 첫 `run` 뒤 `countOf("settlement.settlement")` = 3.
- 세 가게의 `settlementSnapshotOf(shopId)`(가게마다 정산이 하나)를 첫 `run` 뒤에 `List`로 모아 두고(`before`) 둘째 `run` 뒤에 다시 모으면(`after`) `after`가 `before`와 같다(`isEqualTo`. 정산 ID·금액·`itemCount`(2)·`grossAmount`(15000)·붙은 품목 목록이 그대로이고, 품목이 다른 정산으로 옮겨 붙지 않았다).

**3. 정산 뒤에 늦게 도착한 전월 품목은 들어가지 않는다** — 준비: 가게 1곳 × 품목 2개(각 7,500원) → `run`. 이어서 같은 가게의 9월 15일 확정 품목을 `insertSettlementItem(shopId, 7500, 위 확정 시각)`으로 하나 더 넣는다(`lateLineId`). 동작: `run`을 다시.
- `second.settledShops()` = 0(같은 가게를 다시 집어도 `insertIfAbsent`가 0행이라 건너뛴다).
- 정산은 1건 그대로: `settlementSnapshotOf(shopId)`의 `grossAmount()` = 15000, `itemCount()` = 2.
- `settlementItemSnapshotOf(lateLineId).settlementId()`는 null(다음 달 정산 기간에도 맞지 않아 남는다).

**4. 기간 경계** — 준비: 가게 1곳에 품목 4개를 `insertSettlementItem`으로 넣는다.

| 품목 | 금액 | `confirmed_at` | KST |
|---|---|---|---|
| A | 1000 | `2026-08-31T14:59:59Z` | 8월 31일 23:59:59 |
| B | 2000 | `2026-08-31T15:00:00Z` | 9월 1일 00:00:00 |
| C | 4000 | `2026-09-30T14:59:59Z` | 9월 30일 23:59:59 |
| D | 8000 | `2026-09-30T15:00:00Z` | 10월 1일 00:00:00 |

동작과 확인:
- `insertSettlementItem`이 돌려준 `orderLineId`를 품목 이름대로 `lineA`~`lineD`라 한다.
- `run(YearMonth.of(2026, 8))` → `result.settledShops()` = 1. `settlementSnapshotOf(shopId, YearMonth.of(2026, 8))`: `grossAmount()` = 1000, `itemCount()` = 1.
- `run(YearMonth.of(2026, 9))` → `settledShops` = 1. `settlementSnapshotOf(shopId, YearMonth.of(2026, 9))`: `grossAmount()` = 6000, `itemCount()` = 2. `settlementItemSnapshotOf(lineD).settlementId()`는 null(10월 몫).
- `run(YearMonth.of(2026, 10))` → `settlementSnapshotOf(shopId, YearMonth.of(2026, 10))`: `grossAmount()` = 8000, `itemCount()` = 1.

**5. 한 가게의 실패가 다른 가게를 막지 않는다** — 준비: `@MockitoSpyBean SettlementRepository`. 가게 3곳 × 품목 2개. DB 순서로 첫 가게를 `badShopId`로 한다(`SELECT shop_id FROM settlement.settlement_item ORDER BY shop_id LIMIT 1`). `doThrow(new RuntimeException("test")).when(settlementRepository).insertIfAbsent(any(), eq(badShopId), any(), any(), anyInt(), any())`. 저장소는 Spring Data가 만든 JDK 프록시인데, `@MockitoSpyBean`은 JDK 프록시를 인터페이스 mock에 원본 프록시로 위임(`delegatesTo`)하는 방식으로 감싸므로 스텁하지 않은 메서드는 그대로 실행된다(스파이가 붙지 않으면 이 케이스의 첫 단언이 먼저 실패한다). 동작: `run(YearMonth.of(2026, 9))`.
- 예외가 밖으로 나오지 않는다.
- `result.settledShops()` = 2, `countOf("settlement.settlement")` = 2, `settlementSnapshotOf(badShopId)`가 `null`(정산 0건).
- `unsettledItemCount()` = 2(`badShopId`의 품목 2개만 미정산으로 남는다).

**10. 실패했던 가게는 다음 `run`에서 정산된다** — 준비: 5번의 준비와 동작까지(`badShopId`만 정산되지 않은 상태). 동작: `Mockito.reset(settlementRepository)` → `run` 다시.
- `result.settledShops()` = 1, `countOf("settlement.settlement")` = 3, `settlementSnapshotOf(badShopId).grossAmount()` = 15000.

### 할 일 (3-4b)

**구현 순서**: ① 5)의 `PayoutGateway` → 4)의 `MockPayoutGateway` → ② 2)·3) `settlement.api`와 `settlement.api.event` → 4)의 `SettlementTopicConfig` → ③ 5)의 `SettlementService`(의존성 추가, `run` 3번)·`payoutAll` → ④ 1) `SettlementErrorCode` → 목록 조회 저장소 메서드 → 5)의 `SettlementQueryService`(Result 포함) → 6) `SettlementController` → 7) `SecurityConfig` → ⑤ `FakePayoutGateway` → 테스트.

**1) settlement.domain**

| 클래스 | 규격 |
|---|---|
| `SettlementErrorCode` | (`ShopErrorCode`와 같은 위치) `SETTLEMENT_NOT_FOUND`(404, 공통 규칙 R). 정산 모듈의 `ErrorCode` enum이며 `MemberErrorCode`와 같은 모양이다(`code()`, `status()`, `message()`) |
| `SettlementRepository` | 목록 조회 메서드를 더한다: 가게의 정산 목록을 최신 기간 먼저 keyset 조회(JPQL, `Settlement` 엔티티). 페이징 규칙은 `SettlementQueryService`가 적용한다. 시그니처: `List<Settlement> findByShopIdFirstPage(UUID shopId, Limit limit)`와 `findByShopIdAfter(UUID shopId, Instant cursorAt, UUID cursorId, Limit limit)`. 정렬은 `createdAt desc, id desc`이고(월 단위로 만들어져 최신 기간 먼저와 같다. 지난 달을 뒤늦게 수동 실행하면 그 정산이 맨 앞에 온다) 다음 쪽 조건은 `createdAt < :cursorAt or (createdAt = :cursorAt and id < :cursorId)` |
| `SettlementItemRepository` | 목록 조회 메서드를 더한다: 정산 ID가 붙은 품목 목록을 keyset 조회(native). 페이징 규칙은 `SettlementQueryService`가 적용한다. 시그니처: `List<SettlementItemRow> findBySettlementIdFirstPage(UUID settlementId, Limit limit)`와 `findBySettlementIdAfter(UUID settlementId, Instant cursorAt, UUID cursorId, Limit limit)`. 정렬은 `created_at, id` 오름차순이고 다음 쪽 조건은 `created_at > :cursorAt or (created_at = :cursorAt and id > :cursorId)` |
| `SettlementItemRow` | 읽기 모델. `SettlementSum`처럼 인터페이스 프로젝션이다: `getId()`·`getOrderLineId()`·`getOrderId()`·`getProductId()`(UUID), `getAmount()`(long), `getConfirmedAt()`·`getCreatedAt()`(Instant). 별칭은 따옴표로 감싼다. `timestamptz` 컬럼은 `Instant`로 매핑되고(확인함), `Cursor`가 `Instant.toString()`으로 마이크로초까지 담아서 다음 쪽 조건의 `created_at = :cursorAt` 비교도 어긋나지 않는다 |

**2) settlement.api**

| 클래스 | 규격 |
|---|---|
| `settlement/api/package-info.java` | `@NamedInterface("api")`(다른 모듈의 `api/package-info.java`와 같다). notification이 `settlement::api`로 import한다 |

**3) settlement.api.event**

| 클래스 | 규격 |
|---|---|
| `SettlementTopics` | `SETTLEMENT_PAID = "settlement.settlement-paid.v1"` |
| `SettlementPaidEvent` | `SettlementPaidEvent(UUID settlementId, UUID shopId, long netAmount, LocalDate periodStart, LocalDate periodEnd)`(3-5 알림용) |
| `settlement/api/event/package-info.java` | `@NamedInterface("api")` (3-1) |

**4) settlement.infrastructure**

| 클래스 | 규격 |
|---|---|
| `MockPayoutGateway` | `@Component`(프로필 제한 없음, 이유는 아래). 은행 대신 메모리에서 흉내 낸다: `ConcurrentHashMap<UUID, String>`에 `computeIfAbsent(settlementId, id -> "PAYOUT-" + Ids.newId())`로 참조 ID를 만들고(같은 키면 기존 값을 돌려줘 멱등) 로그를 남긴다. 실제 은행 연동은 범위 밖(POL-12) |
| `SettlementTopicConfig` | `NewTopic` `SETTLEMENT_PAID`와 `.dlt`(파티션 3·복제 1. `ShopTopicConfig`와 같은 방식) |

- `MockPayoutGateway`에는 `@Profile`을 붙이지 않는다. ops 실습 환경(`docker-compose.ops.yml`)이 `SPRING_PROFILES_ACTIVE: prod`로 앱을 띄우므로, 프로필을 제한하면 `PayoutGateway` 빈이 없어 기동이 실패하고 develop 자동 배포가 깨진다. 실제 은행 연동은 범위 밖이라 돈이 나가지 않는다.

**5) settlement.application**

| 클래스 | 메서드 |
|---|---|
| `PayoutGateway` | (인터페이스) **판매자에게 정산금을 송금하는 외부 시스템(은행)의 자리**. 실제 은행 대신 Mock을 꽂을 수 있게 인터페이스로 둔다. `String payout(UUID settlementId, UUID shopId, long amount)` → 이체 참조 ID. 실패 시 예외. **같은 settlementId로 다시 부르면 같은 참조 ID를 돌려준다**(멱등. 두 번 호출돼도 돈이 한 번만 나가게). `SettlementService.payoutAll`이 부른다 |
| `PayoutSummary` | `record PayoutSummary(int paid, int payoutFailed)` — `payoutAll()`의 반환. `run`이 `SettlementRunResult`로 합친다 |
| `SettlementService` | 수정: 의존성에 `PayoutGateway`와 `OutboxPublisher`를 더한다. `run`의 끝(3번)을 `PayoutSummary payout = payoutAll()` → `new SettlementRunResult(settledShops, payout.paid(), payout.payoutFailed())`로 바꾼다(3-4a는 `(settledShops, 0, 0)`). `payoutAll`은 아래 |
| `SettlementQueryService`, `SettlementResult`·`SettlementItemResult`·`SettlementDetailResult` | 조회 두 개를 맡는 서비스(`SellerOrderQueryService`와 같은 방식). `@Service`, 의존성 `ShopApi`, `SettlementRepository`, `SettlementItemRepository`. 두 메서드 모두 `@Transactional(readOnly = true)`이고 첫 줄에서 `shopApi.verifyOwner(shopId, memberId)`를 부른다 — 남의 가게면 403 `FORBIDDEN`. 메서드는 아래 `SettlementQueryService` 절 |

- `settlement/package-info.java` 수정: `allowedDependencies`에 `shop::api` 추가 — `SettlementQueryService`가 `ShopApi.verifyOwner`를 쓴다

`PayoutSummary payoutAll()` — **지급 대기 정산을 모두 송금**하고 `PAID`가 된 건수(0원 정산 포함)와 `PAYOUT_FAILED`가 된 건수를 돌려준다. `run`의 3번에서 불린다. 정산을 `PAID`로 바꾸는 같은 트랜잭션에서 `outboxPublisher.publish(SETTLEMENT_PAID, "SETTLEMENT", settlementId, event)`로 발행한다. `findIdsToPay({CALCULATED, PAYOUT_FAILED}, after, Limit.of(500))` keyset 루프(`after`는 첫 페이지에 영(0) UUID, 이후 마지막 ID), 정산마다 try/catch(한 건의 실패가 나머지를 막지 않는다. `after`는 실패해도 전진)
1. 정산 조회(짧은 읽기): `settlementRepository.findById(id)`. `net == 0`이면 송금 없이 트랜잭션에서 `markPaid(null, now)` + `publish(SETTLEMENT_PAID)` 후 다음으로
2. **트랜잭션 밖**: `reference = payoutGateway.payout(settlementId, shopId, net)` — `net`은 `long`(`netAmount`의 `amount()`). 외부 호출을 트랜잭션 안에서 하지 않는 이유는 공통 규칙 K와 같다(송금이 느리면 DB 커넥션을 붙잡는다).
3. 성공 → 트랜잭션(`tx.executeWithoutResult`): **정산을 다시 `findById`로 읽어** `settlement.markPaid(reference, now)` + `publish(SETTLEMENT_PAID)`. 1번에서 읽은 엔티티는 트랜잭션 밖에서 읽은 것이라 여기서 수정해도 저장되지 않는다.
4. 실패(`payout`이 던진 예외) → 트랜잭션: 다시 읽어 `markPayoutFailed(e.getMessage())`(메시지는 `last_error` `varchar(500)`에 맞게 자른다. `payoutAttempts` +1은 `markPayoutFailed` 안에서 한다)
- 2와 3 사이에 죽으면 정산은 CALCULATED로 남는다 → 다음 실행에서 같은 settlementId로 다시 부르고, 게이트웨이가 같은 참조 ID를 돌려주므로 두 번 지급되지 않는다.

**`SettlementQueryService`** (settlement.application)

| 메서드 | 하는 일 |
|---|---|
| `CursorPage<SettlementResult> getSettlements(UUID memberId, UUID shopId, String cursor, int size)` | `verifyOwner` 뒤 커서가 없으면 `findByShopIdFirstPage`, 있으면 `Cursor.decode(cursor)`로 풀어 `findByShopIdAfter`로 읽는다. 각 `Settlement`을 `SettlementResult.from`으로 바꾼다 |
| `SettlementDetailResult getSettlement(UUID memberId, UUID shopId, UUID settlementId, String cursor, int size)` | `verifyOwner` 뒤 `settlementRepository.findById(settlementId)`(없으면 404 `SETTLEMENT_NOT_FOUND`). 그 정산의 `shopId`가 인자 `shopId`와 다르면 같은 404다(남의 가게 정산이 id로 보이면 안 된다). 이어서 품목을 `findBySettlementIdFirstPage`·`findBySettlementIdAfter`로 같은 방식으로 읽는다 |

- 페이징: `cursor`·`size`는 기존 커서 페이징을 따른다. 조회는 `Limit.of(size + 1)`로 한 건 더 읽고, 초과분이 있으면 `size`번째 행의 `new Cursor(createdAt, id).encode()`를 `nextCursor`로 하며(`Cursor`는 `common.web`의 `record Cursor(Instant createdAt, UUID id)`, 다음 쪽 요청은 `Cursor.decode(cursor)`로 푼다), 응답은 `common.web.CursorPage<T>(List<T> items, String nextCursor)`다(예: `SellerOrderQueryService.getShopOrders`).
- `SettlementResult(UUID id, LocalDate periodStart, LocalDate periodEnd, long grossAmount, long feeAmount, long netAmount, int itemCount, SettlementStatus status, Instant paidAt)` — `static from(Settlement)`, 금액은 `Money.amount()`. `SettlementItemResult(UUID orderLineId, UUID orderId, UUID productId, long amount, Instant confirmedAt)` — `static from(SettlementItemRow)`. `SettlementDetailResult(SettlementResult settlement, CursorPage<SettlementItemResult> items)`

**6) settlement.web**

| 클래스 | 규격 |
|---|---|
| `SettlementController`, `SettlementResponse`·`SettlementDetailResponse`·`SettlementItemResponse` | 아래 API 표. 조회 두 개는 `@CurrentMember UUID memberId`를 받아 `SettlementQueryService`를 부르고 `Result`를 `Response`로 바꿔 돌려준다(`SellerOrderController`와 같은 방식이다. 컨트롤러가 저장소를 직접 부르지 않는다). 수동 실행은 `period`(`yyyy-MM`)를 `YearMonth`로 바꿔 `SettlementService.run`을 부른다. 응답 record의 필드는 API 표와 같다 |

| API | 권한 | 응답 |
|---|---|---|
| `GET /api/shops/{shopId}/settlements?cursor=&size=` | 가게 주인(`shopApi.verifyOwner`) | 200 `CursorPage<SettlementResponse(id, periodStart, periodEnd, grossAmount, feeAmount, netAmount, itemCount, status, paidAt)>` (최신 기간 먼저) |
| `GET /api/shops/{shopId}/settlements/{id}?cursor=&size=` | 가게 주인 | 200 `SettlementDetailResponse(settlement, items: CursorPage<SettlementItemResponse(orderLineId, orderId, productId, amount, confirmedAt)>)` |
| `POST /admin/api/settlements/run?period=yyyy-MM` | ADMIN | 200 `SettlementRunResult` |

**7) common.security**

| 클래스 | 규격 |
|---|---|
| `SecurityConfig` | `.requestMatchers("/admin/**").hasRole("ADMIN")`를 `anyRequest().authenticated()` 앞에 더한다. 토큰의 역할이 `ROLE_ADMIN` 권한으로 바뀌는 코드는 이미 있다(`JwtAuthenticationFilter`). DB에서 role을 바꾼 뒤에는 **다시 로그인**해야 토큰에 `ADMIN`이 담긴다 |

### 완료 확인 (3-4b)

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 읽는 방법과 공통 준비는 3-4a와 같고, 이 단계에서 더하는 헬퍼는 다음과 같다(`support/`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 대상 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `FakePayoutGateway` (`support/`, `@Component @Primary`, `implements PayoutGateway`) | `@Primary`는 테스트에서 `MockPayoutGateway` 대신 주입되게 한다. 메서드와 타입: ⓐ `public String payout(UUID settlementId, UUID shopId, long amount)`(`PayoutGateway`의 구현. 호출을 기록하고, `failNext`로 남은 실패 횟수가 있으면 하나 줄이고 예외를 던지며, 아니면 Mock과 같이 같은 `settlementId`에 같은 참조 ID를 돌려준다) ⓑ `public List<PayoutCall> calls()`(기록한 호출, 호출 순서) ⓒ `public void failNext(int times)` ⓓ `public void reset()`(호출 기록과 남은 실패 횟수를 비운다). 함께 둘 record는 `public record PayoutCall(UUID settlementId, UUID shopId, long amount)` | 3-4b 지급 케이스 |
| `IntegrationTestSupport`의 `@AfterEach void resetFakePayoutGateway()` → `void` (메서드 이름은 자유) | `@Autowired FakePayoutGateway fakePayoutGateway` 필드를 더하고 본문에서 `fakePayoutGateway.reset()`을 호출한다. 기존 `@AfterEach void resetPg()`와 같은 모양이다 | 모든 테스트 |

이미 있는 헬퍼: 3-4a의 `insertSettlementItem(shopId, amount, confirmedAt)`·`insertSettlementItems(shopIds, itemsPerShop, amount, confirmedAt)`·`settlementSnapshotOf(shopId)`·`unsettledItemCount()`, 3-1의 `changeRole(UUID memberId, String role)`(JDBC로 `member.member.role` 변경)·`countOf(String table)`, `signup(email)`·`login(email)`·`openShop(memberId, businessNumber)`(3-1과 같다).

헬퍼를 쓰는 방법은 다음과 같다.
- 지급 값은 3-4a의 `settlementSnapshotOf(shopId)`로, 지급 이벤트는 `OutboxAssert.rows("settlement.settlement-paid.v1")`로 읽는다.

#### `settlement/application/SettlementServiceTest` (지급)

공통 준비: 가게 1곳 × 품목 2개(각 7,500원), `net` 14,250. 3-4a의 케이스 1~5와 10은 `status` 단언을 `PAID`로 고쳐 그대로 통과해야 한다.

**6. 지급 성공** — 동작: `run(YearMonth.of(2026, 9))`.
- `s = settlementSnapshotOf(shopId)`: `status()` = `PAID`, `payoutReference()` not null, `paidAt()` not null.
- `fakePayoutGateway.calls()` 정확히 1건: `settlementId` = `s.id()`, `shopId`, `amount` = 14250.
- `rows("settlement.settlement-paid.v1")` 정확히 1건: `aggregateId()` = `s.id()`(`aggregateType()`은 `SETTLEMENT`), payload `settlementId`, `shopId`, `netAmount` = 14250, `periodStart` = `2026-09-01`, `periodEnd` = `2026-09-30`.

**7. 0원 정산은 송금 없이 PAID** — 준비: 가게 1곳에 `insertSettlementItem(shopId, 0, 확정 시각)` 한 개. 동작: `run`.
- `settlementSnapshotOf(shopId)`: `status()` = `PAID`, `payoutReference()` null, `paidAt()` not null.
- `fakePayoutGateway.calls()` 0건.
- `rows("settlement.settlement-paid.v1")` 1건, payload `netAmount` = 0.

**8. 지급 실패 후 재시도** — 준비: `fakePayoutGateway.failNext(1)`. 동작 1: `run`.
- `settlementSnapshotOf(shopId)`: `status()` = `PAYOUT_FAILED`, `payoutAttempts()` = 1, `lastError()` not null(길이 500 이하), `payoutReference()` null, `paidAt()` null.
- `rows("settlement.settlement-paid.v1")` 0건.
- 동작 2: `run` 다시.
- `settlementSnapshotOf(shopId)`: `status()` = `PAID`, `payoutReference()` not null, `payoutAttempts()` = 1(성공은 올리지 않는다), `lastError()`는 이전 실패 메시지가 남을 수 있어 확인하지 않는다.
- `rows("settlement.settlement-paid.v1")` 정확히 1건.

**9. 같은 정산을 두 번 지급해도 참조 ID는 같다** — 준비: `run`으로 `PAID`가 된 정산의 `settlementSnapshotOf(shopId).payoutReference()`를 `ref1`으로 읽는다. JDBC `UPDATE settlement.settlement SET status = 'CALCULATED', payout_reference = NULL, paid_at = NULL WHERE id = ?`로 되돌린다(송금은 됐지만 기록 전에 죽은 상황). 동작: `run`.
- `settlementSnapshotOf(shopId)`: `status()` = `PAID`, `payoutReference()` = `ref1`(같은 참조 ID).
- `fakePayoutGateway.calls()` 정확히 2건이고 두 건의 `settlementId`가 같다(게이트웨이는 두 번 불렸지만 같은 키라 같은 이체로 취급한다).

**11. PAID 정산은 다시 지급하지 않는다** — 준비: 6번의 동작까지(`PAID`, 지급 호출과 `settlement-paid` 이벤트 1건씩). 동작: `run`을 한 번 더.
- `fakePayoutGateway.calls()` 1건 그대로.
- `rows("settlement.settlement-paid.v1")` 1건 그대로.

#### `settlement/web/SettlementControllerTest`

공통 준비: 판매자 A(`seller@test.com`)와 가게 A(`openShop(sellerA, "1111111111")`), 판매자 B(`seller2@test.com`)와 가게 B(`"2222222222"`). 토큰은 `login`. 가게 A·B 각각에 `insertSettlementItems(List.of(shopId), 2, 7500, 확정 시각)` 뒤 `settlementService.run(YearMonth.of(2026, 9))`로 정산을 만든다. 정산 ID는 `settlementSnapshotOf(shopId).id()`(`settlementA`, `settlementB`).

**1. 가게 주인의 목록 조회** — 동작: A의 토큰으로 `GET /api/shops/{shopA}/settlements`.
- 응답 200. `$.items.length()` = 1, `$.nextCursor` null.
- `$.items[0]`: `id` = settlementA, `periodStart` = `2026-09-01`, `periodEnd` = `2026-09-30`, `grossAmount` = 15000, `feeAmount` = 750, `netAmount` = 14250, `itemCount` = 2, `status` = `PAID`, `paidAt` not null.

**2. 가게 주인의 상세 조회** — 동작: A의 토큰으로 `GET /api/shops/{shopA}/settlements/{settlementA}`.
- 응답 200. `$.settlement.id` = settlementA, `$.settlement.netAmount` = 14250.
- `$.items.items.length()` = 2. 각 원소에 `orderLineId`, `orderId`, `productId`, `confirmedAt`가 있고 `amount` = 7500.

**3. 남의 가게 정산 조회 거부** — 동작: B의 토큰으로 `GET /api/shops/{shopA}/settlements`, 이어서 `GET /api/shops/{shopA}/settlements/{settlementA}`.
- 둘 다 응답 403, `$.code` = `FORBIDDEN`.

**4. 내 가게 경로에 남의 정산 ID** — 동작: A의 토큰으로 `GET /api/shops/{shopA}/settlements/{settlementB}`.
- 응답 404, `$.code` = `SETTLEMENT_NOT_FOUND`(남의 정산이 ID로 보이면 안 된다).

**5. 일반 회원의 수동 실행 거부** — 준비: 이 케이스만 정산을 만들지 않고 가게 A에 미정산 품목 2개만 적재한다. 동작: A의 토큰(역할 `USER`/`SELLER`)으로 `POST /admin/api/settlements/run?period=2026-09`.
- 응답 403, `$.code` = `FORBIDDEN`.
- `unsettledItemCount()` = 2(실행되지 않았다).

**6. 관리자의 수동 실행** — 준비: 5번과 같은 미정산 품목. 회원 `admin@test.com`을 `signup`으로 가입해 `adminId`를 받고 → `changeRole(adminId, "ADMIN")` → `login`(역할을 바꾼 뒤에 로그인해야 토큰에 `ADMIN`이 담긴다). 동작: 관리자 토큰으로 `POST /admin/api/settlements/run?period=2026-09`.
- 응답 200, `$.settledShops` = 1. `$.paid` = 1(`PayoutSummary.paid`).
- `countOf("settlement.settlement")` = 1, `settlementSnapshotOf(shopA).status()` = `PAID`.

**7. 역할을 바꾸기 전에 받은 토큰은 거부** — 준비: 5번과 같은 미정산 품목. 회원 `admin@test.com`을 `signup`으로 가입해 `adminId`를 받고 `login`으로 `oldToken`을 받은 뒤 `changeRole(adminId, "ADMIN")`. 동작: `oldToken`으로 `POST /admin/api/settlements/run?period=2026-09`.
- 응답 403, `$.code` = `FORBIDDEN`.

**리뷰 때 물어볼 것**
- offset 페이징으로 상태를 바꾸며 읽으면 왜 누락되나? (원 프로젝트에서 실제로 있던 결함)
- 정산과 지급을 왜 분리했나?
- 합계를 "정산 ID가 붙은 품목"으로 계산하는 이유는?

---

## 3-5. 알림 (M)

**목표**: 결제 완료·발송·환불·정산 지급 등에서 이메일 알림을 보낸다. 실패하면 재시도하고, 끝내 실패한 건은 보관해 나중에 다시 보낼 수 있다. 알림이 실패해도 주문·결제는 영향받지 않는다.

**새로 등장**

| 개념 | 한 줄 설명 |
|---|---|
| 저장과 발송 분리 | consumer는 "보낼 알림"을 DB에 저장만 하고, 발송 잡이 따로 보낸다. 메일 서버 장애가 consumer 재시도·DLT로 번지지 않는다 |
| 포트 | `common.mail.MailSender` 인터페이스. local은 로그로 출력, 테스트는 가짜 구현, SMTP는 선택. 4-2의 인증 메일도 같은 포트를 쓴다(그래서 notification이 아니라 common에 둔다) |

**정책 (이 단계에서 정함)**
- 채널은 이메일만. 수신 주소는 알림 생성 시점의 이메일을 저장한다. 회원 알림은 회원의 이메일, 가게 알림(새 주문, 정산 지급)은 가게의 이메일(`shop.shop.email`)이다.
- 발송 재시도: 실패하면 **5분 뒤** 다시, **5번 실패하면 FAILED**. FAILED 재처리 API는 Part 7.
- 같은 이벤트·같은 수신자·같은 종류의 알림은 1건(`dedup_key`).

**흐름 한눈에**
1. 다른 모듈의 이벤트(`order-paid` 등)가 Kafka로 오면 `NotificationEventConsumer`가 받아 `InboxGuard` 안에서 처리한다.
2. `NotificationComposer`가 이벤트를 `NotificationDraft`(수신 대상 memberId 또는 shopId + 제목 + 본문) 목록으로 바꾼다.
3. `NotificationService.enqueue`가 draft마다 수신자 이메일을 조회해 `notification` 행(`PENDING`)으로 **저장만** 한다.
4. 10초마다 `NotificationDispatchJob` → `NotificationDispatcher.dispatchBatch()`가 `PENDING` 행을 `MailSender`로 보내고 `SENT`로 바꾼다(실패 재시도는 위 정책).

### 할 일

**구현 순서**: 1) 마이그레이션 → 2)·3) `MemberApi.getContact` → 4) `ShopApi.getContact` → 5) `common.mail` → 6) `notification.domain` → 7) `JpaNotificationRepository` → 8) `NotificationComposer`·`NotificationService`·`NotificationDispatcher` → 7) `NotificationDispatchJob`·`NotificationEventConsumer` → `FakeMailSender`(완료 확인 "테스트 준비") → 테스트(`NotificationStatusTest` → `NotificationComposerTest` → `NotificationFlowTest`).

**1) 마이그레이션** — `db/migration/notification/V{...}__notification_create_notification.sql` (`CREATE SCHEMA IF NOT EXISTS notification;`)

| 컬럼 | 타입 | NULL | 제약 |
|---|---|---|---|
| id | uuid | X | `pk_notification` |
| member_id | uuid | X | |
| type | varchar(40) | X | 아래 표의 종류 |
| channel | varchar(20) | X | DEFAULT 'EMAIL' |
| recipient | varchar(255) | X | 이메일 |
| title | varchar(200) | X | |
| body | text | X | |
| dedup_key | varchar(200) | X | `uk_notification_dedup_key` (값의 형식은 8) `enqueue` 3번) |
| status | varchar(20) | X | `ck_notification_status`: `IN ('PENDING','SENT','FAILED')` |
| attempts | int | X | DEFAULT 0 |
| last_error | varchar(500) | O | |
| next_attempt_at | timestamptz | X | |
| sent_at | timestamptz | O | |
| version | bigint | X | |
| created_at, updated_at | timestamptz | X | |

- 인덱스 `idx_notification_pending ON notification.notification (next_attempt_at) WHERE status = 'PENDING'`

**2) member.api**

알림 행에는 수신 주소(이메일)를 저장하므로, 이벤트에 없는 "수신자의 이메일"을 동기 호출로 알아낸다.

| 클래스 | 규격 |
|---|---|
| `MemberApi` | `MemberContact getContact(UUID memberId)`를 더한다 |
| `MemberContact` | `record MemberContact(String email, boolean active)`(`member.api`의 별도 파일). 탈퇴 회원은 `active = false` |

- `active`는 탈퇴 여부만 나타내므로 `BANNED` 회원도 `true`다(4-4의 제재 알림이 제재된 회원에게 가야 한다).

**3) member.application**

| 클래스 | 메서드 |
|---|---|
| `MemberApiImpl` | (이미 있음) `getContact`를 `@Transactional(readOnly = true)`로 더한다. 회원이 없으면 `MEMBER_NOT_FOUND`(`MemberErrorCode`에 이미 있다)이고, consumer 안이라 재시도 없이 DLT로 간다 |

**4) shop.api · shop.application**

가게 알림(새 주문, 정산 지급)은 주인 회원의 이메일이 아니라 가게 이메일로 보내므로, 가게의 연락처를 동기 호출로 알아낸다.

| 클래스 | 규격 |
|---|---|
| `shop.api.ShopApi` | `ShopContact getContact(UUID shopId)`를 더한다. 3-2에서 더한 `getOwnerId`는 쓰는 곳이 이 메서드로 바뀌므로 지운다 |
| `shop.api.ShopContact` | `record ShopContact(UUID ownerId, String email)`(`shop.api`의 별도 파일). `ownerId`는 가게 주인의 memberId, `email`은 `shop.shop.email`. CLOSED 가게도 돌려준다(폐업 뒤 정산 지급 알림도 가게 이메일로 간다) |
| `shop.application.ShopApiImpl` | `getContact`를 `@Transactional(readOnly = true)`로 구현한다. `shopRepository.findById`로 읽고 가게가 없으면 `SHOP_NOT_FOUND`(BusinessException)이다. consumer 안이라 재시도 없이 DLT로 간다 |

**5) common.mail**

| 클래스 | 규격 |
|---|---|
| `MailSender` | (인터페이스) `void send(String to, String title, String body)` — 보내지 못하면 예외 |
| `LogMailSender` | `@Component`(프로필 제한 없음, 3-4b `MockPayoutGateway`와 같은 이유), 실제 발송 대신 `log.info`로 출력(본문은 앞 100자만) |

**6) notification.domain**

- `notification/package-info.java`: `@ApplicationModule(allowedDependencies = {"common", "member::api", "shop::api", "order::api", "settlement::api"})`(공통 규칙 S). 현재 notification 모듈에는 코드가 없어 파일이 없다. consumer가 `OrderTopics`·`MemberTopics`·`SettlementTopics`를, `NotificationService`가 `MemberApi`·`ShopApi`를 import한다
- `Notification`·`NotificationRepository`는 3-4 정산(`Settlement`·`SettlementRepository`·`JpaSettlementRepository`)과 같은 모양이다: 엔티티는 JPA, 중복 건너뛰기 INSERT만 native `@Query`, 나머지는 JPQL.

| 클래스 | 규격 |
|---|---|
| `NotificationType` | 알림 종류 enum(`type` 컬럼 값): `WELCOME, ORDER_PAID, SHOP_ORDER_RECEIVED, ORDER_CLOSED, SHIPPED, DELIVERED, REFUNDED, SETTLEMENT_PAID` (Part 5에서 구독 관련, 4-4에서 `BANNED` 추가) |
| `NotificationDraft` | "보낼 알림 초안" `record (UUID memberId, UUID shopId, NotificationType type, String title, String body)`. 수신 대상은 둘 중 **정확히 하나**만 채운다: 회원 알림이면 `memberId`(`shopId`는 null), 가게 알림이면 `shopId`(`memberId`는 null). 수신 주소는 아직 없다(저장할 때 조회). 만드는 곳에서 헷갈리지 않게 `toMember(...)`·`toShop(...)` 같은 정적 팩터리를 두는 것을 권한다 |
| `NotificationStatus` | 상태 enum. 허용 전이: `PENDING → {SENT, FAILED}`, `FAILED → {PENDING}`(관리 API의 재시도, 7-3), `SENT → {}`. 상태머신 문서([04-state-machines 9절](../02-design/04-state-machines.md))와 같다. `ShopStatus`처럼 `transitTo(to)`가 허용 밖이면 `INVALID_STATE_TRANSITION`(409). `FAILED → PENDING`은 7-3이 native `UPDATE ... WHERE status = 'FAILED'`로 하므로 이 단계에서는 엔티티가 부르지 않는다(개발 가이드 5.4: 엔티티 경로가 있는 상태는 CAS와 함께 써도 전이표를 둔다) |
| `Notification` | `BaseTimeEntity`를 상속하고 1)의 컬럼을 매핑한다(`version`은 `@Version`). `markSent(Instant now)`: `status = status.transitTo(SENT)`, `sentAt = now`. `markFailed(String error, Instant now)`: `attempts++`, `lastError`는 `error`를 500자로 자른 값, `attempts >= 5`이면 `status = status.transitTo(FAILED)`, 아니면 `nextAttemptAt = now + 5분`(`PENDING` 유지). 5회·5분은 이 클래스의 상수다 |
| `NotificationRepository` | `int insertIfAbsent(UUID id, UUID memberId, String type, String recipient, String title, String body, String dedupKey, Instant now)`(native `INSERT ... ON CONFLICT ON CONSTRAINT uk_notification_dedup_key DO NOTHING`. 삽입된 행 수를 돌려주고 0이면 이미 있던 알림이다. `type`은 `NotificationType.name()`을 문자열로 넘긴다. native 쿼리라 `@Enumerated` 매핑이 적용되지 않는다), `List<Notification> findDue(Instant now, Limit limit)`(JPQL: `status = PENDING and nextAttemptAt <= :now order by nextAttemptAt`), `Optional<Notification> findById(UUID id)` |

**7) notification.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaNotificationRepository` | `NotificationRepository`의 구현. 3-4 `JpaSettlementRepository`와 같은 모양이다 |
| `NotificationDispatchJob` | `@Scheduled(fixedDelay = 10_000)` → `jobRunner.run("dispatch-notifications", dispatcher::dispatchBatch)` |
| `NotificationEventConsumer` | 토픽마다 `@KafkaListener(topics = ..., groupId = "notification")` 메서드 → 파싱 → `inboxGuard.runOnce("notification.{event}", ...)` → composer → service. 동작은 아래 |

`NotificationEventConsumer` — 토픽 7개마다 메서드 하나(`member-registered`, `order-paid`, `order-expired`, `shop-order-shipped`, `shop-order-delivered`, `order-line-refunded`, `settlement-paid`). 한 클래스에 `@KafkaListener` 메서드가 여럿이어도 `groupId = "notification"`이 같으면 한 그룹이다. 의존성 `EventEnvelopes`, `InboxGuard`, `NotificationComposer`, `NotificationService`. 메서드 하나의 모양(`order-paid` 예)
1. `typed = envelopes.parse(message, OrderPaidEvent.class)`
2. `inboxGuard.runOnce("notification.order-paid", typed.envelope(), () -> { ... })` 안에서
   1. `drafts = composer.compose(typed.payload())`
   2. `notificationService.enqueue(typed.envelope().eventId(), drafts)`

**8) notification.application**

| 클래스 | 메서드 |
|---|---|
| `NotificationComposer` | `@Component`. 이벤트 record → `List<NotificationDraft>`로 바꾸는 **순수 변환**(DB·다른 모듈 조회를 하지 않는다). 이벤트마다 오버로드 하나씩(예: `compose(MemberRegisteredEvent e)`, `compose(OrderPaidEvent e)` — 인자는 권장안). `order-paid`는 구매자 draft 1개와 `e.shopOrders()`의 가게주문마다 가게 draft 1개(`shopId` = `so.shopId()`)를 만든다. 가게 주인·이메일 조회는 `enqueue`가 한다. 매핑은 아래 표. 단위 테스트 대상(`NotificationComposerTest`) |
| `NotificationService` | `@Service`. **초안을 `notification` 행으로 저장**한다. `@Transactional void enqueue(UUID eventId, List<NotificationDraft> drafts)`. 동작은 아래 |
| `NotificationDispatcher` | `@Component`, `@Transactional` 없음. **PENDING 알림을 실제로 보내는 곳**. `public int dispatchBatch()` → 이번에 SENT로 바꾼 건수. 동작은 아래 |

이벤트 → 알림 매핑 (`NotificationComposer`)

| 이벤트 | 수신자 | type | 제목 예 | 본문 예 |
|---|---|---|---|---|
| `member-registered` | 회원 | WELCOME | `[MyRoutine] 가입을 환영합니다` | `{nickname}님, MyRoutine에 가입해 주셔서 감사합니다.` |
| `order-paid` | 구매자 | ORDER_PAID | `주문 결제가 완료되었습니다 ({totalAmount}원)` | `주문번호 {orderId}의 결제가 완료되었습니다. 결제 금액은 {totalAmount}원입니다.` |
| `order-paid` | 가게주문마다 해당 **가게**(가게 이메일) | SHOP_ORDER_RECEIVED | `새 주문이 들어왔습니다` | `주문번호 {orderId}에 가게주문 {shopOrderId}가 접수되었습니다.` |
| `order-expired` | 구매자 | ORDER_CLOSED | reason에 따라 `주문이 만료되었습니다` / `결제에 실패했습니다` | `주문번호 {orderId}가 닫혔습니다. 사유: {제목과 같은 문구}` |
| `shop-order-shipped` | 구매자 | SHIPPED | `상품이 발송되었습니다 ({carrier} {trackingNumber})` | `주문번호 {orderId}의 상품이 {carrier} 운송장 {trackingNumber}로 발송되었습니다.` |
| `shop-order-delivered` | 구매자 | DELIVERED | `배송이 완료되었습니다` | `주문번호 {orderId}의 배송이 완료되었습니다.` |
| `order-line-refunded` | 구매자 | REFUNDED | `{amount}원이 환불되었습니다` | `주문번호 {orderId}의 품목 환불 {amount}원이 처리되었습니다.` |
| `settlement-paid` | **가게**(가게 이메일) | SETTLEMENT_PAID | `{periodStart}~{periodEnd} 정산금 {netAmount}원이 지급되었습니다` | `정산 기간 {periodStart}~{periodEnd}의 정산금 {netAmount}원이 지급되었습니다.` |

제목의 금액·개수는 천 단위 구분 기호 없이 숫자 그대로 쓴다(`3000원`). `LocalDate`는 `toString()`의 ISO 형식(`2026-09-01`)이다.

`NotificationService.enqueue(eventId, drafts)` — consumer가 `InboxGuard` 트랜잭션 안에서 부르므로 그 트랜잭션에 합류한다.
1. draft마다 수신 대상을 해석한다.
   - 회원 알림(`memberId`가 있음): `MemberContact contact = memberApi.getContact(draft.memberId())`. `!contact.active()`(탈퇴 회원)면 그 draft는 건너뛴다. `member_id` = `draft.memberId()`, `recipient` = `contact.email()`, 수신 대상 id = `draft.memberId()`
   - 가게 알림(`shopId`가 있음): `ShopContact contact = shopApi.getContact(draft.shopId())`. `member_id` = `contact.ownerId()`, `recipient` = `contact.email()`, 수신 대상 id = `draft.shopId()`. 탈퇴 검사는 하지 않는다(운영 중인 가게나 미정산이 있으면 탈퇴할 수 없다)
2. 해석한 `member_id`·`recipient`·수신 대상 id로 다음 단계에 들어간다
3. `notificationRepository.insertIfAbsent(Ids.newId(), memberId, draft.type().name(), recipient, draft.title(), draft.body(), eventId + ":" + 수신 대상 id + ":" + draft.type(), now)`. `now`는 `Instant.now(clock)`이다. 이 값이 `next_attempt_at`·`created_at`·`updated_at`에 들어가고(native INSERT라 JPA 감사가 동작하지 않는다), `status = 'PENDING'`, `version = 0`은 쿼리에 고정한다
- 수신 대상이 `shopId`라 한 판매자의 가게가 둘이어도 키가 달라 **가게마다 1건**씩 간다. 같은 이벤트를 두 번 받는 경우는 `InboxGuard`가 `enqueue` 앞에서 막고, 키는 한 이벤트에서 같은 수신 대상·같은 종류의 알림이 두 번 저장되는 일을 DB에서 막는다.

`dispatchBatch` 순서 — 클래스에 `@Transactional`을 붙이지 않고 `TransactionTemplate`으로 구간을 나눈다(외부 호출인 메일 발송을 트랜잭션 밖에서 하려고, 공통 규칙 K)
1. `notificationRepository.findDue(now, Limit.of(100))` — 보낼 알림 최대 100건 읽기(잡 락으로 한 인스턴스만 실행)
2. 각 알림마다(알림 한 건 전체를 바깥 `try`/`catch`로 감싸 한 건의 예외가 나머지를 막지 않게 한다. 바깥 `catch`는 `log.error`만 남긴다): **트랜잭션 밖에서** 안쪽 `try`로 `send`만 감싸 `mailSender.send(notification.recipient(), title, body)`
   - 성공: 짧은 트랜잭션으로 `findById`로 다시 읽어 `markSent(now)`
   - 실패(`send`가 예외, 안쪽 `catch`): 짧은 트랜잭션으로 `findById`로 다시 읽어 `markFailed(error, now)`(`error`는 `e.getMessage()`, 없으면 `e.toString()`) 후 다음 알림으로 넘어간다
3. 발송 성공 건수 반환
- 1번에서 읽은 엔티티를 트랜잭션 밖에서 바꾸면 저장되지 않으므로, 결과 기록은 반드시 트랜잭션 안에서 다시 읽은 엔티티에 한다(3-4 `payoutAll`과 같다).
- 안쪽 `try`에 `markSent`까지 넣으면 기록 실패가 발송 실패로 `markFailed`에 기록되어 `attempts`가 오르고, 메일이 나갔는데도 `FAILED`가 될 수 있다. `markSent`·`markFailed` 기록이 실패하면 바깥 `catch`가 로그를 남기고, 그 알림은 `PENDING` 그대로라 다음 실행(10초 뒤)이 다시 읽는다. `markSent` 기록이 실패한 알림은 그때 다시 발송되므로 발송은 최소 한 번(at-least-once)이다.

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 값을 읽는 방법은 3-1~3-4와 같고, 이 단계에서 더하는 헬퍼는 다음과 같다(`support/`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 대상 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `FakeMailSender` (`support/`, `@Component @Primary`, `implements MailSender`)와 `SentMail` | `@Primary`는 테스트에서 `LogMailSender` 대신 이 빈이 주입되게 한다. 여러 스레드(스케줄러·consumer)가 건드릴 수 있어 보낸 목록은 동시성 안전한 컬렉션(`CopyOnWriteArrayList` 등)으로 둔다. 메서드와 타입: ⓐ `public void send(String to, String title, String body)`(`MailSender`의 구현. 보낸 목록에 `SentMail`을 더하고, 남은 실패 횟수가 있으면 하나 줄이고 예외를 던진다) ⓑ `public List<SentMail> sent()` ⓒ `public void failNext(int times)` ⓓ `public void reset()`(목록과 남은 실패 횟수를 비운다). 함께 둘 record는 `public record SentMail(String to, String title, String body)` | 3-5 `NotificationFlowTest` |
| `IntegrationTestSupport`의 `@AfterEach void resetFakeMailSender()` → `void` (메서드 이름은 자유) | `@Autowired FakeMailSender fakeMailSender` 필드를 더하고 본문에서 `fakeMailSender.reset()` 호출 | 모든 테스트 |
| `notificationRowsOf(UUID memberId)` → `List<NotificationRow>` | `TestFixtures`. JDBC `SELECT member_id, type, channel, recipient, title, status, attempts, last_error, dedup_key, sent_at, next_attempt_at FROM notification.notification WHERE member_id = ? ORDER BY created_at, id`. `NotificationRow`(`support/`의 record) 필드: `memberId`(UUID), `type`·`channel`·`recipient`·`title`·`status`·`lastError`·`dedupKey`(String, `lastError`는 없으면 null), `attempts`(int), `sentAt`·`nextAttemptAt`(`Instant`, `sentAt`은 없으면 null) | 3-5 `NotificationFlowTest`, 5-4 |
| `notificationRows()` → `List<NotificationRow>` | 위와 같은 컬럼·변환으로 `WHERE`와 회원 조건 없이 전체 | `NotificationFlowTest` 1·2·5·6번 |
| `openShop(UUID memberId, String businessNumber, String email)` → `UUID` | `TestFixtures`의 오버로드. 기존 `openShop(memberId, businessNumber)`가 가게 이메일을 `shop@test.com`으로 고정해서 가게별 수신 주소를 구분할 수 없으므로, `OpenShopCommand`의 `email`을 인자로 받는다. 기존 2인자 메서드는 이 메서드에 `"shop@test.com"`을 넘기게 한다 | `NotificationFlowTest` 1·5번 |
| `setNotificationsDue()` → `void` | 재시도 시각을 당긴다. JDBC `UPDATE notification.notification SET next_attempt_at = now() - interval '1 second'` | `NotificationFlowTest` 3·4번 |
| `OrderSnapshot`에 주문 `status` 추가 | `support/OrderSnapshot`(필드 `linesByProduct`, `shopOrderStatuses`, `refundStatuses`, `payment`)에 `String status`를 더하고 `TestFixtures.snapshotOf(orderId)`가 `SELECT status FROM orders.orders WHERE id = ?`로 채운다(주문 행이 없으면 `null`). `new OrderSnapshot(...)` 호출도 새 필드에 맞춘다 | `NotificationFlowTest` 4번 |

이미 있는 헬퍼(`TestFixtures`): `signup(email)`·`openShop(memberId, businessNumber)`(3-1과 같다), `registerProduct(memberId, shopId, price, initialStock)`(productId), `createPendingOrder(buyerId, productIds...)`(상품마다 수량 1로 체크아웃한 `CheckoutResult`. `orderId()`가 주문 ID), `confirmPayment(memberId, orderId)`(가짜 PG로 승인까지 부른다. `ConfirmResult`), `stockOf(productId)`(재고 `StockSnapshot`), `snapshotOf(orderId)`(주문 `OrderSnapshot`. 품목 상태 `line(productId).status()`, 결제 `payment().status()`), 3-1의 `Eventually`·`OutboxAssert`, 3-2의 `TestKafka.send(String topic, String key, UUID eventId, Object event)`·`processedCountOf(String consumer, UUID eventId)`.

헬퍼를 쓰는 방법은 다음과 같다.
- 알림 개수는 `notificationRows().size()`이고, 이벤트별 개수는 `dedupKey()`가 `eventId + ":"`로 시작하는 행을 `stream().filter`로 센다.
- 알림이 만들어졌는지 기다릴 때는 `Eventually.await(Duration.ofSeconds(5), ...)` 안에서 `notificationRows().size()`를 읽는다.

#### `notification/domain/NotificationStatusTest` (순수 JUnit, `@ParameterizedTest`로 3×3 = 9쌍)

| 케이스 | 확인할 것 |
|---|---|
| 허용 3쌍: `PENDING→SENT`, `PENDING→FAILED`, `FAILED→PENDING` | `from.transitTo(to)`의 반환값이 `to`다 |
| 허용 밖 6쌍: `PENDING→PENDING`, `SENT→PENDING`, `SENT→SENT`, `SENT→FAILED`, `FAILED→SENT`, `FAILED→FAILED` | `BusinessException`이 던져지고 `getErrorCode()` = `CommonErrorCode.INVALID_STATE_TRANSITION` |

#### `notification/application/NotificationComposerTest`

DB 없이 `new NotificationComposer()`로 만든다. 인자는 권장안(`compose(이벤트)`, 이벤트마다 오버로드 하나)이다. 결과 `List<NotificationDraft>`의 수신 대상(`memberId` 또는 `shopId`), `type`, `title`을 아래 값과 `containsExactlyInAnyOrder`로 비교한다. 제목은 매핑 표의 "제목 예"를 그대로 쓰고, `body`는 매핑 표의 "본문 예"로 만들지만 테스트는 `isNotBlank()`만 확인한다.

공통 값: `buyerId`, `ownerA`, `ownerB`, `shopA`, `shopB`, `orderId`는 서로 다른 임의 UUID.

아래 표의 이벤트 record 필드 순서: `MemberRegisteredEvent(UUID memberId, String email, String nickname)`, `OrderPaidEvent(UUID orderId, UUID memberId, long totalAmount, List<PaidShopOrder> shopOrders)`, `PaidShopOrder(UUID shopOrderId, UUID shopId, List<PaidLine> lines)`, `PaidLine(UUID orderLineId, UUID productId, String productName, int quantity)`, `OrderExpiredEvent(UUID orderId, UUID memberId, String reason)`, `ShopOrderShippedEvent(UUID shopOrderId, UUID orderId, UUID memberId, String carrier, String trackingNumber)`, `ShopOrderDeliveredEvent(UUID shopOrderId, UUID orderId, UUID memberId)`, `OrderLineRefundedEvent(UUID refundId, UUID orderLineId, UUID orderId, UUID memberId, long amount)`, `SettlementPaidEvent(UUID settlementId, UUID shopId, long netAmount, LocalDate periodStart, LocalDate periodEnd)`. 주문 이벤트의 `memberId`는 구매자다.

| 입력 | 기대하는 draft |
|---|---|
| `MemberRegisteredEvent(buyerId, "a@test.com", "nick")` | 1개: `buyerId`, `WELCOME`, `[MyRoutine] 가입을 환영합니다` |
| `OrderPaidEvent(orderId, buyerId, 3000, [PaidShopOrder(so1, shopA, [PaidLine(l1, p1, "상품A", 1), PaidLine(l2, p2, "상품B", 2)])])` | 2개: (회원 `buyerId`, `ORDER_PAID`, `주문 결제가 완료되었습니다 (3000원)`), (가게 `shopA`, `SHOP_ORDER_RECEIVED`, `새 주문이 들어왔습니다`) |
| `OrderPaidEvent`에 가게주문 둘: `so1`(`shopA`, 품목 1개), `so2`(`shopB`, 품목 3개). `totalAmount` 5000 | 3개: (회원 `buyerId`, `ORDER_PAID`, `주문 결제가 완료되었습니다 (5000원)`), (가게 `shopA`, `SHOP_ORDER_RECEIVED`, `새 주문이 들어왔습니다`), (가게 `shopB`, `SHOP_ORDER_RECEIVED`, `새 주문이 들어왔습니다`) |
| `OrderExpiredEvent(orderId, buyerId, "EXPIRED")` | 1개: `buyerId`, `ORDER_CLOSED`, `주문이 만료되었습니다` |
| `OrderExpiredEvent(orderId, buyerId, "PAYMENT_FAILED")` | 1개: `buyerId`, `ORDER_CLOSED`, `결제에 실패했습니다` |
| `ShopOrderShippedEvent(so1, orderId, buyerId, "CJ대한통운", "1234567890")` | 1개: `buyerId`, `SHIPPED`, `상품이 발송되었습니다 (CJ대한통운 1234567890)` |
| `ShopOrderDeliveredEvent(so1, orderId, buyerId)` | 1개: `buyerId`, `DELIVERED`, `배송이 완료되었습니다` |
| `OrderLineRefundedEvent(refundId, lineId, orderId, buyerId, 1000)` | 1개: `buyerId`, `REFUNDED`, `1000원이 환불되었습니다` |
| `SettlementPaidEvent(settlementId, shopA, 14250, 2026-09-01, 2026-09-30)` | 1개: 가게 `shopA`, `SETTLEMENT_PAID`, `2026-09-01~2026-09-30 정산금 14250원이 지급되었습니다` |

#### `notification/application/NotificationFlowTest`

공통 준비: 판매자 `seller1@test.com`(가게 1 `openShop(seller1, "1111111111", "shop1@test.com")`, `product1` = `registerProduct(seller1, shop1, 1000, 10)`), 판매자 `seller2@test.com`(가게 2 `"2222222222"`, 이메일 `shop2@test.com`, `product2` 2000원), 구매자 `buyer@test.com`. 주문은 `createPendingOrder(buyerId, product1Id, product2Id)`(합계 3,000원, 가게주문 둘). 준비 끝에 `OutboxAssert.clear()`(가입·개설 이벤트가 알림 행을 섞지 않게 한다).

**1. 결제 승인에서 발송까지** — 동작 1: `confirmPayment(buyerId, orderId)`(가짜 PG 기본 승인) → `outboxRelay.relayOnce()`.
- `relayOnce()` 반환값 1(`order-paid` 한 건).
- `Eventually`(5초) 안에서 `notificationRows().size()` = 3. 이후 읽을 때도 3이다.
- 세 행(모두 `status()` = `PENDING`):

| `memberId()` | `type()` | `recipient()` | `title()` |
|---|---|---|---|
| buyerId | `ORDER_PAID` | `buyer@test.com` | `주문 결제가 완료되었습니다 (3000원)` |
| seller1 | `SHOP_ORDER_RECEIVED` | `shop1@test.com` | `새 주문이 들어왔습니다` |
| seller2 | `SHOP_ORDER_RECEIVED` | `shop2@test.com` | `새 주문이 들어왔습니다` |

- `dedupKey()` = `{order-paid 이벤트의 eventId}:{수신 대상}:{type()}`(eventId는 `OutboxAssert.rows("order.order-paid.v1")`의 `id()`. 수신 대상은 구매자 알림이면 `buyerId`, 가게 알림이면 그 가게의 `shopId`다).
- 동작 2: `notificationDispatcher.dispatchBatch()`.
- 반환값 3. `notificationRows()`의 세 행 모두 `status()` = `SENT`.
- `fakeMailSender.sent()` 정확히 3건이고 (`to`, `title`)의 집합이 위 표의 (`recipient`, `title`)과 같다. 각 `body`는 비어 있지 않다.
- `dispatchBatch()`를 한 번 더 → 반환값 0, `sent()` 3건 그대로(보낸 알림을 다시 보내지 않는다).

**2. 같은 이벤트를 두 번 받아도 알림은 그대로** — 준비: 1번의 동작 1까지 한다(`confirmPayment` → `relayOnce()`. 알림이 만들어지기를 기다리지 않아도 된다. 같은 키라 같은 파티션에서 순서대로 처리된다). `@MockitoSpyBean NotificationService`. 동작: 릴레이가 같은 행을 다시 발행한 상황을 만든다. JDBC `UPDATE common.outbox_event SET status = 'READY', next_attempt_at = now() WHERE topic = 'order.order-paid.v1'` → `relayOnce()`(반환값 1, 같은 `eventId1`·같은 내용의 메시지가 한 번 더 나간다). 이어서 센티널로 `TestKafka.send(OrderTopics.ORDER_PAID, orderId.toString(), eventId2, 다른 주문의 OrderPaidEvent)`(같은 키, 가게주문은 `shop1`의 1개).
- `Eventually`(5초) 안에서 `dedupKey()`가 `eventId2:`로 시작하는 행이 2개(구매자 `ORDER_PAID`, seller1 `SHOP_ORDER_RECEIVED`).
- `dedupKey()`가 `eventId1:`로 시작하는 행은 3개다.
- `verify(notificationService, times(1)).enqueue(eq(eventId1), anyList())`.

**3. 메일 발송이 5번 실패하면 FAILED** — 준비(1번의 주문 대신 알림 한 건짜리): `OutboxAssert.clear()` → `memberId = signup("welcome@test.com")` → `relayOnce()` → `Eventually` 안에서 `notificationRowsOf(memberId)`가 1개(`type()` = `WELCOME`, `status()` = `PENDING`). `fakeMailSender.failNext(5)`. 동작: 아래를 `i = 1..5`로 반복한다. `dispatchBatch()` 호출 → 확인(`n = notificationRowsOf(memberId).get(0)`) → `setNotificationsDue()`.
- 호출 `i` 뒤: `n.attempts()` = `i`, `n.lastError()` not null(길이 500 이하), `n.sentAt()` null.
- `i` < 5이면 `n.status()` = `PENDING`, `i` = 5이면 `n.status()` = `FAILED`.

**4. 발송 실패는 주문·결제에 영향이 없다** — 준비: 1번의 동작 1까지(알림 3행). `fakeMailSender.failNext(15)`. 동작: 3번과 같은 방법으로 `dispatchBatch()`를 5번 부른다(3행이라 호출마다 3번의 실패를 쓴다. 세 알림이 `FAILED`가 되는 것은 3번이 확인한다). `s = snapshotOf(orderId)`.
- `s.status()` = `PAID`, `s.payment().status()` = `APPROVED`.

**5. 한 판매자의 가게 둘이면 가게마다 알림** — 준비: 판매자 `seller1`이 가게 하나를 더 연다(`openShop(seller1, "3333333333", "shop3@test.com")`, `product3` 3000원). 주문은 `createPendingOrder(buyerId, product1Id, product3Id)`(같은 판매자의 가게 둘, 합계 4,000원) → `OutboxAssert.clear()`. 동작: `confirmPayment` → `relayOnce()`.
- `Eventually` 안에서 `notificationRows()`가 정확히 3개: (`memberId()` = buyerId, `type()` = `ORDER_PAID`), (seller1, `SHOP_ORDER_RECEIVED`, `recipient()` = `shop1@test.com`), (seller1, `SHOP_ORDER_RECEIVED`, `recipient()` = `shop3@test.com`).
- 두 가게 알림의 `dedupKey()`는 `eventId:{shop1Id}:SHOP_ORDER_RECEIVED`, `eventId:{shop3Id}:SHOP_ORDER_RECEIVED`로 서로 다르다.

**6. 탈퇴 회원은 건너뛰고 나머지는 만든다** — 준비: 1번의 주문. `confirmPayment` 뒤 `relayOnce()` 전에 JDBC `UPDATE member.member SET status = 'WITHDRAWN' WHERE id = buyerId`. 동작: `relayOnce()`.
- `Eventually` 안에서 `notificationRows()`가 정확히 2개: (seller1, `SHOP_ORDER_RECEIVED`), (seller2, `SHOP_ORDER_RECEIVED`). 구매자의 `ORDER_PAID`는 없다(회원 알림만 탈퇴 검사를 한다).

**7. 실패하면 5분 뒤에 다시 시도한다** — 준비: 3번과 같은 알림 한 건짜리, `fakeMailSender.failNext(5)`(바로 다시 시도하는 구현이 있다면 다시 실패해 `attempts()`가 2가 되도록 실패 횟수를 남겨 둔다). 동작: `dispatchBatch()` 한 번 → `n = notificationRowsOf(memberId).get(0)`.
- `n.attempts()` = 1, `n.nextAttemptAt()`이 `Instant.now().plus(4분)`보다 뒤이고 `Instant.now().plus(6분)`보다 앞이다(지금 + 5분 안팎).
- 시각을 당기지 않고 `dispatchBatch()`를 한 번 더 부르면 `attempts()` = 1 그대로(5분 전에는 다시 시도하지 않는다). 반환값은 성공 건수라 시도해서 실패해도 0이므로 `attempts()`로 확인한다.

**8. FAILED가 된 알림은 다시 보내지 않는다** — 준비: 3번의 동작까지(`failNext(5)`로 5번 실패시켜 `FAILED`가 된 상태). 동작: `setNotificationsDue()` → `dispatchBatch()`.
- `n.attempts()` = 5, `n.status()` = `FAILED` 그대로. `fakeMailSender.sent()`는 5건 그대로(`FAILED` 뒤에는 `send`가 불리지 않는다. 실패한 시도도 기록되어 5건이다).

**리뷰 때 물어볼 것**
- 원 프로젝트에서 RabbitMQ를 따로 쓴 이유와, v2에서 Kafka만으로 충분한 이유는? (ADR-003)
- consumer에서 메일을 바로 보내지 않는 이유는?

---

## 3-6. 회원 탈퇴 (M)

**목표**: 진행 중인 주문·운영 중인 가게·정산 대기 금액이 없으면 탈퇴할 수 있다(구독은 Part 5에서 추가). 개인정보는 마스킹된다.

**정책 (이 단계에서 정함)**
- 막는 조건(`details.reasons`에 모두 담는다)

| reason | 조건 | 확인하는 곳 |
|---|---|---|
| `ACTIVE_SHOP` | ACTIVE 가게가 있다 | member가 `ShopApi.countActiveShops` 직접 호출 |
| `ACTIVE_ORDER` | 결제 대기·진행 중 주문, 또는 구매자로서 종결되지 않은 가게주문(PAID·SHIPPED·DELIVERED), 진행 중 환불 | order 구현 |
| `PENDING_SETTLEMENT` | 내 가게(CLOSED 포함)에 미정산 품목 또는 PAID가 아닌 정산이 있다 (OPEN-07) | settlement 구현 |

- 마스킹(OPEN-01: 같은 이메일로 재가입 가능하게): email `withdrawn+{memberId}@deleted.invalid`, nickname `탈퇴-{memberId 앞 8자}`, name `탈퇴회원`, phone null, password_hash null, 배송지 전부 삭제.
- 탈퇴한 회원의 기존 토큰은 Part 4(4-4 tokenVersion, 그때 탈퇴에도 버전 올리기를 붙인다)부터 즉시 막힌다. 그 전까지는 토큰 만료(1시간)까지 유효하다는 한계를 PR에 적는다.

### 할 일

**구현 순서**: ① 1) 마이그레이션 → 2) `Member`·`MemberErrorCode` → ② 3) `WithdrawalPrecondition` → 4) `MemberTopics`·`MemberWithdrawnEvent` → 5) `MemberTopicConfig` → ③ order·settlement 구현체: 8) `OrderRepository` 조회 → 9) `JpaOrderRepository` → 10) `OrderWithdrawalPrecondition` → 11) settlement 저장소의 탈퇴 조건 조회 → 12) `settlement/package-info.java`·`SettlementWithdrawalPrecondition` → ④ 2)의 `MemberRepository` 잠금 조회 → 6) `WithdrawService` → 7) `MemberController.withdraw` → ⑤ order consumer: 10)의 장바구니 삭제 → 9)의 `MemberEventConsumer` → 테스트(`MemberTest` → `WithdrawTest`).

**1) 마이그레이션** — `db/migration/member/V{...}__member_add_withdrawn_at.sql`: `member.member`에 `withdrawn_at timestamptz NULL`

**2) member.domain**

| 클래스 | 규격 |
|---|---|
| `Member` | 필드 `withdrawnAt`과 메서드 `withdraw(Instant now)`를 더한다. `@Column(name = "withdrawn_at") Instant withdrawnAt`. `withdraw`: `this.status = this.status.transitTo(MemberStatus.WITHDRAWN)`(`MemberStatus`가 ACTIVE → WITHDRAWN만 허용하므로 BANNED·이미 WITHDRAWN이면 `INVALID_STATE_TRANSITION`), 위 정책의 마스킹 값을 필드에 대입(`{memberId}`는 `id`, 앞 8자는 `id.toString().substring(0, 8)`), `withdrawnAt = now`. 원래 이메일이 사라지므로 같은 이메일로 다시 가입해도 중복 검사에 걸리지 않는다(완료 확인) |
| `MemberErrorCode` | `MEMBER_HAS_ACTIVE_RESOURCES` 추가: 422(공통 규칙 R) |
| `MemberRepository` | 잠금 조회를 더한다: `@Lock(PESSIMISTIC_WRITE)`를 단 `Optional<Member>` 조회(이름은 자유, `member.infrastructure`의 `JpaMemberRepository`에 구현). 같은 회원의 탈퇴 요청이 겹쳐도 한 번에 하나씩 처리된다 |

**3) member.api**

| 클래스 | 규격 |
|---|---|
| `WithdrawalPrecondition` | `List<String> violations(UUID memberId)` — 위반 이유 코드 목록, 위반이 없으면 빈 목록. 탈퇴를 막는 조건을 각 모듈이 대신 확인해 주는 인터페이스다(3-2의 `ShopClosePrecondition`과 같은 의존 역전: member가 order·settlement의 데이터를 직접 볼 수 없다). 반환이 `List<String>`인 이유: `ShopClosePrecondition.check`는 첫 위반에서 예외를 던지지만, 탈퇴는 **모든 이유를 한 번에** 응답(`details.reasons`)에 담아야 해서 이유 코드를 돌려준다 |

**4) member.api.event**

| 클래스 | 규격 |
|---|---|
| `MemberTopics` | `MEMBER_WITHDRAWN = "member.member-withdrawn.v1"` |
| `MemberWithdrawnEvent` | `MemberWithdrawnEvent(UUID memberId)` |

**5) member.infrastructure**

| 클래스 | 규격 |
|---|---|
| `MemberTopicConfig` | 3-3이 만든 클래스에 `memberWithdrawnTopic()`과 `.dlt` 토픽 빈을 같은 모양(`TopicBuilder.name(토픽).partitions(3).replicas(1).build()`)으로 더한다 |

**6) member.application**

| 클래스 | 메서드 |
|---|---|
| `WithdrawService` | `@Service`. 탈퇴 유스케이스. 의존성 `MemberRepository`, `MemberAddressRepository`, `ShopApi`, `List<WithdrawalPrecondition> preconditions`, `OutboxPublisher`, `Clock`. `@Transactional void withdraw(UUID memberId)`. 순서는 표 아래 |

`WithdrawService.withdraw` 순서 (한 트랜잭션)
1. 회원을 `MemberRepository`의 잠금 조회로 읽는다
2. `List<String> reasons = new ArrayList<>()` → `shopApi.countActiveShops(memberId) > 0`이면 `reasons.add("ACTIVE_SHOP")` → `preconditions.forEach(p -> reasons.addAll(p.violations(memberId)))`
3. 비어 있지 않으면 `throw new BusinessException(MemberErrorCode.MEMBER_HAS_ACTIVE_RESOURCES, Map.of("reasons", reasons))` → 422, 응답의 `details.reasons`에 담긴다(`BusinessException(ErrorCode errorCode, Map<String, Object> details)` 생성자의 `details`가 응답의 `details`가 된다)
4. 배송지 전부 삭제(`memberAddressRepository.findAllByMemberId` → 각각 `delete`) → `member.withdraw(Instant.now(clock))` → `outboxPublisher.publish(MEMBER_WITHDRAWN, "MEMBER", memberId, new MemberWithdrawnEvent(memberId))`

- member는 구현체(`OrderWithdrawalPrecondition`, `SettlementWithdrawalPrecondition`)를 모르고 스프링이 주입해 준 목록만 돈다. 가게 조건(`ACTIVE_SHOP`)만 직접 부르는 이유는 공통 규칙 S에 있다.

**7) member.web**

| 클래스 | 규격 |
|---|---|
| `MemberController` | `withdraw` 추가: `@DeleteMapping("/me")`, `@CurrentMember UUID memberId` → `withdrawService.withdraw(memberId)` → `ResponseEntity.noContent().build()` → `DELETE /api/members/me` 204 |

**8) order.domain**

| 클래스 | 규격 |
|---|---|
| `OrderRepository` | 구매자 진행 중 조회를 더한다: 정책 표 `ACTIVE_ORDER`의 세 조건(결제 대기·진행 중 주문 `PENDING_PAYMENT`·`PAYMENT_IN_PROGRESS` / 구매자로서 종결되지 않은 가게주문 `PAID`·`SHIPPED`·`DELIVERED` / 진행 중 환불)을 `memberId`로 조회한다. 시그니처는 `OrderRepository`(domain), `@Query`는 `JpaOrderRepository`. 이름은 문서에 없다(확인 필요). 진행 중 환불의 상태 집합은 문서에 명시가 없어, `ShipmentService.ship`이 `refundRepository.existsByShopOrderIdAndStatusIn`에 넘기는 집합(`REQUESTED`, `APPROVED`, `PG_CANCELLED`)과 같은 집합을 쓰는 것으로 읽힌다(확인 필요) |

**9) order.infrastructure**

| 클래스 | 규격 |
|---|---|
| `JpaOrderRepository` | `OrderRepository`의 구매자 진행 중 조회에 `@Query`를 둔다 |
| `MemberEventConsumer` | `@KafkaListener(topics = MemberTopics.MEMBER_WITHDRAWN, groupId = "order")` → `parse(message, MemberWithdrawnEvent.class)` → `inboxGuard.runOnce("order.member-withdrawn", typed.envelope(), () -> 장바구니 삭제(memberId))`. order의 허용 의존에 `member::api`가 이미 있어 `MemberTopics`를 import할 수 있다 |

**10) order.application**

| 클래스 | 메서드 |
|---|---|
| `OrderWithdrawalPrecondition` | `@Component`, `implements WithdrawalPrecondition`. `OrderRepository`의 구매자 진행 중 조회 조건 중 하나라도 있으면 `List.of("ACTIVE_ORDER")`, 없으면 `List.of()` |
| `CartService` 또는 별도 서비스 | 장바구니 삭제: 그 회원의 장바구니와 항목(`cart_item`)을 삭제한다(이름은 예시로 `deleteByMember`). `fk_cart_item_cart` 때문에 항목을 먼저 지우고 장바구니를 지운다(현재 `CartItemRepository`에는 `deleteAll`이 있고 `CartRepository`에는 삭제 메서드가 없어 더해야 한다). 장바구니가 없으면 아무 일도 하지 않고 정상 종료한다(공통 규칙 Q) |

**11) settlement.domain**

| 클래스 | 규격 |
|---|---|
| `SettlementItemRepository`·`SettlementRepository`(3-4) | 탈퇴 조건 조회를 더한다: 가게 ID 목록에 대해 ① `settlement_item.settlement_id IS NULL`인 품목이 있는지 ② `PAID`가 아닌 정산이 있는지. 시그니처·이름은 문서에 없다(확인 필요). |

**12) settlement.application**

| 클래스 | 메서드 |
|---|---|
| `SettlementWithdrawalPrecondition` | `@Component`, `implements WithdrawalPrecondition`. ① `shopApi.getShopIdsByOwner(memberId)`(CLOSED 포함) → 가게가 하나도 없으면 `List.of()` ② `settlement.domain`의 탈퇴 조건 조회로 미정산 품목이나 `PAID`가 아닌 정산이 있으면 `List.of("PENDING_SETTLEMENT")` |

- `settlement/package-info.java`: `allowedDependencies`에 `member::api` 추가(공통 규칙 S)

### 완료 확인

**테스트 준비**

아래 테스트를 쓰기 전에 만든다. 값을 읽는 방법은 3-1~3-5와 같고, 이 단계에서 더하는 헬퍼는 다음과 같다(`support/TestFixtures`. 이름·인자는 권장안이라 쓰다 바꿔도 된다).

| 메서드 | 만드는 방법 | 쓰는 곳 |
|---|---|---|
| `memberSnapshotOf(UUID memberId)`에 탈퇴 시각·배송지 추가 | 3-1에서 추가한 `MemberSnapshot`에 `withdrawnAt`(`Instant`, 없으면 null. 읽는 컬럼 `member.member.withdrawn_at`은 이 단계 1번 마이그레이션이 만든다)과 `List<AddressState> addresses`를 더한다(`record AddressState(UUID id, String recipient, String address2, boolean isDefault)`). 배송지는 JDBC `SELECT id, recipient, address2, is_default FROM member.member_address WHERE member_id = ? ORDER BY created_at, id`로 읽고, 회원 쿼리에는 `withdrawn_at`을 더한다. `MemberSnapshot`을 만드는 `new MemberSnapshot(...)` 호출도 새 필드에 맞춘다 | `WithdrawTest` 배송지 수·삭제 확인 |
| `cartSnapshotOf(UUID memberId)` → `CartSnapshot` | 이 단계에서 `TestFixtures`에 추가한다. JDBC 쿼리 2개로 한 번에 읽는다: `SELECT COUNT(*) FROM orders.cart WHERE member_id = ?`(`cartCount`), `SELECT ci.product_id, ci.quantity FROM orders.cart_item ci JOIN orders.cart c ON c.id = ci.cart_id WHERE c.member_id = ?`(`quantityByProduct`). `support/CartSnapshot`은 `public record CartSnapshot(int cartCount, Map<UUID, Integer> quantityByProduct)`이고 `itemCount()`(맵 크기)를 가진다 | `WithdrawTest` 장바구니 삭제 확인 |

이미 있는 헬퍼(`TestFixtures`): 3-4의 `insertSettlementItem(shopId, amount, confirmedAt)`·`FakePayoutGateway`(`failNext(int times)`), `signup(email)`·`login(email)`·`openShop(memberId, businessNumber)`(3-1과 같다), `closeShop(shopId)`·`registerProduct(memberId, shopId, price, initialStock)`·`createPaidOrder(buyerId, productIds...)`·`shopOrderIdOf(orderId, shopId)`(3-2와 같다), `address(UUID memberId)`(배송지 하나를 등록하고 addressId를 돌려준다), `addToCart(UUID memberId, UUID productId, int quantity)`(장바구니 항목 ID), `createPendingOrder(buyerId, productIds...)`·`confirmPayment(memberId, orderId)`(3-5와 같다), `shipShopOrder(sellerId, shopId, shopOrderId)`·`deliverShopOrder(sellerId, shopId, shopOrderId)`(서비스를 불러 가게주문을 발송·배송완료로), `orderLineIdOf(UUID orderId, UUID productId)`(품목 ID), `ConcurrencyRunner`(3-2와 같다), 3-1의 `memberSnapshotOf`(`role`·`status`·`email`을 읽는다)·`countOf`·`OutboxAssert`·`Eventually`, 3-2의 `processedCountOf`.

헬퍼를 쓰는 방법은 다음과 같다.
- 응답: 거부 응답의 이유 목록은 `$.details.reasons`. 목록을 통째로 비교할 때는 `JsonPath.read(body, "$.details.reasons")`로 `List<String>`을 읽는다.
- 회원: `MemberSnapshot s = memberSnapshotOf(memberId)` 한 번으로 읽는다. 배송지 수는 `s.addresses().size()`. 값 전체를 비교할 때는 `MemberSnapshot` record의 `isEqualTo`를 쓴다.
- 장바구니: 2-1의 `cartSnapshotOf(memberId)` → `CartSnapshot`의 `cartCount()`(장바구니 행 수), `itemCount()`(항목 수).
- 탈퇴 요청: `DELETE /api/members/me`, `Authorization: Bearer {회원 토큰}`(`Idempotency-Key` 없음).

#### `member/domain/MemberTest`

준비: `Member member = Member.signUp("test@test.com", "pass1234", "test", "테스트")` → `member.changeProfile("test", "테스트", "010-1234-5678")`(전화번호를 채운다). `Instant now = Instant.parse("2026-10-18T00:00:00Z")`. `id = member.getId()`.

**1. 탈퇴하면 개인정보가 마스킹된다** — 동작: `member.withdraw(now)`.
- `getStatus()` = `WITHDRAWN`.
- `getEmail()` = `"withdrawn+" + id + "@deleted.invalid"`.
- `getNickname()` = `"탈퇴-" + id.toString().substring(0, 8)`.
- `getName()` = `탈퇴회원`.
- `getPhone()` = null, `getPasswordHash()` = null.
- `getWithdrawnAt()` = `now`.

**2. BANNED 회원은 탈퇴할 수 없고 값이 바뀌지 않는다** — 준비: `ReflectionTestUtils.setField(member, "status", MemberStatus.BANNED)`. 동작: `member.withdraw(now)`.
- `BusinessException`, `getErrorCode()` = `CommonErrorCode.INVALID_STATE_TRANSITION`.
- `getEmail()` = `test@test.com`, `getNickname()` = `test`, `getName()` = `테스트`, `getPhone()` = `010-1234-5678`, `getWithdrawnAt()` = null, `getStatus()` = `BANNED`(마스킹이 시작되지 않았다).

**3. 이미 탈퇴한 회원의 두 번째 탈퇴** — 동작: `member.withdraw(now)` 뒤 `member.withdraw(now.plusSeconds(60))`.
- 두 번째가 `BusinessException`, `INVALID_STATE_TRANSITION`.
- `getWithdrawnAt()` = 첫 번째 `now` 그대로.

#### `member/web/WithdrawTest`

공통 준비: 탈퇴할 회원 `member@test.com`(`signup`, 토큰은 `login`, 닉네임 `member`), 판매자 `seller@test.com`과 가게 `openShop(sellerId, "1111111111")`, 상품 `product1`(`registerProduct(sellerId, shopId, 1000, 10)`). 정산 월은 `YearMonth.of(2026, 9)`, 미정산 품목의 확정 시각은 `Instant.parse("2026-09-15T03:00:00Z")`. 거부 케이스의 공통 확인: 탈퇴 요청 전에 `before = memberSnapshotOf(memberId)`를 읽어 두고, 요청 뒤 `memberSnapshotOf(memberId)`가 `before`와 같다(`isEqualTo`. 이메일·닉네임·이름·전화번호·비밀번호 해시·배송지가 그대로다). `OutboxAssert.rows("member.member-withdrawn.v1")`가 비어 있다. 성공(204) 케이스의 공통 확인: `memberSnapshotOf(memberId).status()` = `WITHDRAWN`.

**1. 운영 중인 가게가 있으면 거부** — 준비: 회원이 `openShop(memberId, "2222222222")`. 동작: 탈퇴 요청.
- 응답 422, `$.code` = `MEMBER_HAS_ACTIVE_RESOURCES`, `$.details.reasons` = `["ACTIVE_SHOP"]`.
- 거부 공통 확인.

**2. 진행 중인 주문이 있으면 거부, 끝난 주문은 막지 않는다** — 준비: 행마다 새 회원으로 아래 상태를 만든 뒤 탈퇴 요청. 구매자는 탈퇴하는 회원 본인이다.

| 준비 | 응답 | 확인할 것 |
|---|---|---|
| `createPendingOrder(memberId, product1Id)` (주문 `PENDING_PAYMENT`) | 422 | `$.details.reasons` = `["ACTIVE_ORDER"]`, 거부 공통 확인 |
| `pg.on("/v1/payments/confirm", Behavior.delay(1000))` → `createPendingOrder` → `confirmPayment`(202, `PAYMENT_IN_PROGRESS`) | 422 | 위와 같음 |
| `createPaidOrder(memberId, product1Id)` (가게주문 `PAID`) | 422 | 위와 같음 |
| 위 + `shipShopOrder(sellerId, shopId, shopOrderId)` (`SHIPPED`) | 422 | 위와 같음 |
| 위 + `deliverShopOrder(sellerId, shopId, shopOrderId)` (`DELIVERED`) | 422 | 위와 같음 |
| `createPendingOrder` → `POST /api/orders/{orderId}/cancel` (200, `CANCELLED`) | 204 | 성공 공통 확인 |
| `createPaidOrder` → JDBC `UPDATE orders.shop_order SET status = 'COMPLETED' WHERE id = ?` | 204 | 성공 공통 확인 |
| `createPaidOrder` → JDBC `UPDATE orders.shop_order SET status = 'CANCELLED' WHERE id = ?` | 204 | 성공 공통 확인 |

**3. 진행 중인 환불이 있으면 거부** — 준비: `createPaidOrder(memberId, product1Id)` → `pg.on("/v1/payments/" + paymentKey, Behavior.delay(1000))` → 품목 취소 요청(HTTP) → 202, `$.status` = `APPROVED` → JDBC `UPDATE orders.shop_order SET status = 'COMPLETED'`(가게주문 조건만 걷어 환불만 남긴다). 동작: 탈퇴 요청.
- 응답 422, `$.details.reasons` = `["ACTIVE_ORDER"]`, 거부 공통 확인.
- 이어서 JDBC `UPDATE orders.refund SET status = 'COMPLETED' WHERE id = ?`(`id`는 202 응답의 `$.refundId`) 뒤 탈퇴 요청 → 204.

**4. 정산이 끝나지 않았으면 거부** — 준비: 행마다 회원이 가게를 열고 `closeShop(shopId)`로 CLOSED로 만든다(ACTIVE 가게가 아니라 `ACTIVE_SHOP`은 해당 없다). 동작: 탈퇴 요청.

| 준비 | 응답 | 확인할 것 |
|---|---|---|
| 그 가게에 `insertSettlementItem(shopId, 1000, 확정 시각)` (미정산 품목) | 422 | `$.details.reasons` = `["PENDING_SETTLEMENT"]`, 거부 공통 확인 |
| 위 + `fakePayoutGateway.failNext(1)` → `settlementService.run(YearMonth.of(2026, 9))` (정산 `PAYOUT_FAILED`, 미정산 품목 0) | 422 | 위와 같음 |
| 위 + `settlementService.run(YearMonth.of(2026, 9))` 한 번 더 (정산 `PAID`) | 204 | 성공 공통 확인 |
| 품목 없이 CLOSED 가게만 | 204 | 성공 공통 확인 |

**5. 이유가 둘 이상이면 모두 담는다** — 준비: 회원이 ACTIVE 가게 `openShop(memberId, "2222222222")`와 CLOSED 가게 `"3333333333"`(`closeShop` + 미정산 품목 `insertSettlementItem`)를 갖고, `createPendingOrder(memberId, product1Id)`로 결제 대기 주문도 있다. 동작: 탈퇴 요청.
- 응답 422, `$.code` = `MEMBER_HAS_ACTIVE_RESOURCES`.
- `$.details.reasons`: 첫 번째 원소가 `ACTIVE_SHOP`이고, 세 값은 `ACTIVE_SHOP`, `ACTIVE_ORDER`, `PENDING_SETTLEMENT`가 하나씩이다(뒤 두 개의 순서는 정해져 있지 않다).
- 거부 공통 확인.

**6. 탈퇴 성공** — 준비: 회원에게 `address(memberId)`를 두 번(배송지 2개), `addToCart(memberId, product1Id, 2)`, 조건 위반 없음. `OutboxAssert.clear()`. 동작: 탈퇴 요청.
- 응답 204, 본문 없음.
- 탈퇴 뒤 `s = memberSnapshotOf(memberId)`: `email()` = `withdrawn+{memberId}@deleted.invalid`, `nickname()` = `탈퇴-{memberId 앞 8자}`, `name()` = `탈퇴회원`, `phone()` null, `passwordHash()` null, `withdrawnAt()` not null, `addresses().size()` = 0.
- `rows("member.member-withdrawn.v1")` 정확히 1건: `aggregateType()` = `MEMBER`, `aggregateId()` = memberId, payload `memberId` = memberId 문자열.

**7. 탈퇴 뒤 장바구니가 비워진다** — 준비: 6번의 회원(장바구니 항목 1개)과 다른 구매자 `other@test.com`(`addToCart(otherId, product1Id, 1)`). 탈퇴 요청(204) 뒤 `eventId` = `rows("member.member-withdrawn.v1")`의 `id()`. 동작: `outboxRelay.relayOnce()`.
- 반환값 1.
- `Eventually`(5초) 안에서 `processedCountOf("order.member-withdrawn", eventId)` = 1.
- `cartSnapshotOf(memberId)`: `cartCount()` = 0, `itemCount()` = 0(항목과 장바구니 행이 모두 지워졌다).
- 다른 구매자는 그대로: `cartSnapshotOf(otherId)`: `cartCount()` = 1, `itemCount()` = 1.

**8. 장바구니가 없는 회원의 탈퇴 이벤트** — 준비: 장바구니를 만든 적 없는 회원. 탈퇴 요청(204) → `relayOnce()` = 1.
- `Eventually`(5초) 안에서 `processedCountOf("order.member-withdrawn", 그 이벤트의 eventId)` = 1(예외 없이 끝났다는 신호다. 실패했다면 이 행이 롤백돼 남지 않는다).

**9. 같은 회원의 탈퇴 요청 두 건 동시** — 준비: 조건 위반이 없는 회원. 동작: `ConcurrencyRunner.run(2, ...)`로 같은 토큰의 탈퇴 요청을 동시에 보내 HTTP 상태 코드를 모은다.
- 상태 코드: 하나는 204, 하나는 409이고 409 쪽 `$.code` = `INVALID_STATE_TRANSITION`(잠금 조회로 줄 서서, 뒤의 요청은 이미 `WITHDRAWN`인 회원을 읽는다).
- `rows("member.member-withdrawn.v1")` 정확히 1건.
- `memberSnapshotOf(memberId)`: `status()` = `WITHDRAWN`, `email()` = `withdrawn+{memberId}@deleted.invalid` 한 형태(두 번 마스킹되지 않았다).

**10. 탈퇴한 회원은 로그인할 수 없다** — 준비: 6번의 준비와 동작까지(탈퇴 204). 동작: `POST /api/auth/login` 본문 `{"email":"member@test.com","password":"pass1234"}`.
- 응답 401, `$.code` = `LOGIN_FAILED`.

**11. 탈퇴한 회원의 이메일로 다시 가입할 수 있다** — 준비: 6번의 준비와 동작까지(탈퇴 204). 동작: `POST /api/auth/signup` 본문 `{"email":"member@test.com","password":"pass1234","nickname":"member","name":"테스터"}`.
- 응답 201. `SELECT COUNT(*) FROM member.member WHERE email = 'member@test.com'` = 1(새 회원).

**리뷰 때 물어볼 것**
- 탈퇴 조건 검사와 탈퇴 처리 사이에 새 주문이 들어오면?
- 가게 조건만 member가 직접 확인하는 이유는? (순환 의존)

---

## Part 3 완료
- [ ] `v0.3.0` 릴리스, Part 4 문서 다듬기
