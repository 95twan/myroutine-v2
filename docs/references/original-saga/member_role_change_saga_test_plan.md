# MemberRoleChange Saga 테스트 계획

현재 상태:

- 구현은 완료되었고, 자동 테스트 코드는 아직 없음
- 대상 구조는 다음과 같음
  - `shop-service`: 등록/삭제 요청 시 Outbox 저장, `memberId` 기반 DB 트랜잭션 락 적용
  - `member-service`: `requested` 소비 후 권한 변경, `completed/failed/dead` 발행
  - Kafka 토픽 분리: `requested/completed/failed/dead`
  - Kafka key: 권한 Saga 이벤트 전부 `memberId`

---

## 1. 테스트 목표

- 동시성/순서/멱등 요구사항이 실제로 충족되는지 검증
- 실패 재시도와 DLT 전이 시 상태 일관성이 유지되는지 검증
- 설계 문서와 구현 동작이 일치하는지 검증

---

## 2. 테스트 범위

- `shop-service`
  - `registerShop`, `deleteMyShop`
  - `registerShopCompleted/Failed/Dead`
  - `deleteShopCompleted/Failed/Dead`
  - Outbox 발행 경로
- `member-service`
  - `MemberRoleChangeRequestedConsumer`
  - `MemberService.addMemberRole/deleteMemberRole`
  - `completed/failed/dead` 발행 경로
- Kafka
  - 토픽 분리/키(`memberId`) 적용
  - retry + DLT 동작

---

## 3. 우선순위

- `P0`: 동시성/정합성 핵심 시나리오
- `P1`: 실패/재시도/DTL 시나리오
- `P2`: 운영/관측성 보조 시나리오

---

## 4. P0 시나리오

### P0-1 마지막 상점 동시 삭제

- 조건: 같은 `memberId`에 활성 상점 2개
- 실행: 두 상점을 거의 동시에 `deleteMyShop` 호출
- 기대:
  - `count == 0` 분기 오판 없음
  - 최종적으로 SELLER 권한 회수 경로가 1회 정상 수행
  - `shop_deletion` 상태 전이 이상 없음

### P0-2 삭제와 등록 교차 요청

- 조건: 같은 `memberId`
- 실행: `deleteMyShop`와 `registerShop`를 교차 호출
- 기대:
  - DB 락으로 내부 판단(`count`) 정합성 유지
  - 이벤트 key가 모두 `memberId`인지 확인
  - 최종 권한 상태가 비즈니스 기대와 일치

### P0-3 멱등 삭제 요청

- 조건: 같은 `shopId`에 삭제 요청 중복
- 실행: 동일 삭제 API를 연속 호출
- 기대:
  - `REQUESTED/COMPLETED` 상태에서 중복 호출은 no-op
  - 중복 이벤트/중복 상태 전이 없음

### P0-4 토픽/키 계약 검증

- 조건: Kafka 메시지 추적 가능 환경
- 실행: 등록/삭제 전체 플로우 실행 후 레코드 확인
- 기대:
  - 토픽이 `member-role-change-requested/completed/failed/dead`로 분리됨
  - 각 레코드 key가 모두 `memberId`

---

## 5. P1 시나리오

### P1-1 비즈니스 실패 -> FAILED

- 조건: `member-service`에서 권한 변경 비즈니스 예외 유도
- 실행: `requested` 소비
- 기대:
  - `failed` 이벤트 발행
  - `shop-service`에서 해당 Saga 상태가 `FAILED`로 전이
  - 실패 사유(`reasonCode`, `reasonMessage`) 저장

### P1-2 인프라 실패 재시도 -> DLT -> DEAD

- 조건: `member-service` 소비 중 예외 지속 유도
- 실행: retry 소진까지 유도
- 기대:
  - `requested.dlt`로 이동
  - DLT consumer가 `dead` 이벤트 발행
  - `shop-service`에서 상태 `DEAD` 전이

### P1-3 완료/실패/DEAD 중복 소비

- 실행: 같은 이벤트를 재전송 또는 중복 소비 유도
- 기대:
  - 상태 전이가 안전(no-op)
  - 데이터 손상/중복 처리 없음

---

## 6. P2 시나리오

### P2-1 Outbox 장기 체류

- 조건: 브로커 일시 중단 등으로 발행 지연 유도
- 기대:
  - `READY/PROCESSING/FAILED` 상태가 정책대로 변함
  - 재기동 후 발행 재개

### P2-2 운영 로그 추적성

- 기대:
  - `memberId`, `shopId`, saga 상태를 로그로 역추적 가능
  - 장애 시 원인 구간(`requested`/`failed`/`dead`) 식별 가능

---

## 7. 권장 구현 순서

1. 단위 테스트
- `shop-service` 서비스 레벨 상태 전이/분기 검증
- `member-service` 권한 변경 + 실패 이벤트 생성 검증

2. 슬라이스/통합 테스트
- Kafka listener + producer 경로 검증
- Outbox 핸들러 경로 검증

3. E2E 테스트
- 실제 두 서비스 + Kafka + DB 환경에서 P0/P1 시나리오 실행

---

## 8. 완료 기준 (Definition of Done)

- P0 전 시나리오 통과
- P1 전 시나리오 통과
- CI에서 최소 단위/통합 테스트 자동 실행
- 수동 E2E 결과를 문서로 기록
