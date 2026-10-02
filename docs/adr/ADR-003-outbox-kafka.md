# ADR-003. 비동기 이벤트는 직접 구현한 Outbox + Kafka 단일 브로커로 한다

- 상태: 승인 (2026-10-02)
- 관련: [05-events.md](../02-design/05-events.md)

## 맥락
- As-Is에서는 이벤트 발행 방식이 서비스마다 달랐다(Outbox / AFTER_COMMIT / 트랜잭션 내 직접 발행). 그 결과 유실(ORD-09, SHOP-05), 유령 이벤트(CAT-03), 중복 발행(PAY-03)이 생겼다.
- shop 가게 등록·삭제 Saga에서 Outbox + SKIP LOCKED + DLT 패턴을 이미 설계·구현했다(본인 작업). 이를 일반화한다.
- 알림에 Kafka와 RabbitMQ를 함께 써서 브로커가 둘이었고, RabbitMQ에는 DLQ가 없었다(SUP-03).

## 결정
1. 모든 비동기 이벤트는 상태 변경과 같은 트랜잭션에서 `outbox_event`에 쓰고, 릴레이가 Kafka로 발행한다.
2. 릴레이는 `PROCESSING + locked_until` 리스로 행을 선점하고, 트랜잭션 밖에서 동기 발행(`send().get()`)한 뒤 결과를 기록한다. 리스가 만료되면 회수한다.
3. consumer는 `processed_message(consumer, eventId)`로 멱등 처리하고, 3회 재시도 후 DLT로 보낸다. DLT는 관리 API로 재처리한다.
4. 브로커는 **Kafka 하나**로 통일한다. 알림도 Kafka consumer + 재시도 + DLT로 처리하고 RabbitMQ는 제거한다.
5. Spring Modulith의 Event Publication Registry는 쓰지 않는다. Outbox를 직접 구현해 동작 원리를 설명할 수 있게 하고, 원 프로젝트 패턴을 일반화하는 연속성을 보인다.

## 고려한 대안
| 대안 | 기각 이유 |
|---|---|
| `@TransactionalEventListener(AFTER_COMMIT)` + 직접 발행 | 커밋 후 발행 실패 시 유실 (As-Is ORD-09) |
| CDC (Debezium) | 발행 지연·순서 측면에서 우수하지만 인프라(Kafka Connect)가 추가됨. Stage 2에서 폴링 릴레이 지연이 목표(NFR-PERF-06)를 넘으면 재검토 |
| Spring Modulith 이벤트 레지스트리 + 외부화 | 구현은 쉽지만 내부 동작이 가려짐. 면접에서 Outbox를 설명할 근거가 약해짐 |
| Kafka + RabbitMQ 유지 | 알림은 처리량이 낮고 재시도·DLT를 Kafka로 충분히 구현 가능. 브로커가 둘이면 운영·장애 지점이 둘 |
| 모놀리스 내부 이벤트는 인메모리로 | 앱이 죽으면 유실. Stage 3에서 consumer만 옮기면 되는 구조를 잃음 |

## 결과
- 커밋된 변경의 이벤트는 유실되지 않는다. 대신 중복은 생길 수 있으므로 consumer 멱등이 필수다.
- 모놀리스 안에서 Kafka를 쓰는 비용(지연 수십 ms, 인프라 1개)이 있다. 대신 Stage 3에서 notification·search·recommendation을 떼어낼 때 consumer 코드를 옮기기만 하면 된다.
- 순서: 같은 키는 같은 파티션. 릴레이를 여러 개 띄우면 순서가 바뀔 수 있어 Stage 1은 단일 릴레이(advisory lock으로 한 인스턴스만 실행)로 시작한다. 순서에 민감한 consumer는 집계 version으로 오래된 이벤트를 무시한다.
- 운영 지표: outbox READY 건수·최고 대기 시간, DEAD 건수, DLT 적재 건수 → 알림.

## 면접 연결 ([면접 질문](../references/notes/interview-questions.md) 1.4, 1.5)
- Polling vs CDC, 폴링 주기 선정 → 백오프 폴링 + 지연 측정 결과
- "발행 성공 후 상태 업데이트 실패하면?" → 리스 회수로 재발행 → consumer 멱등
- "Outbox 테이블이 커지면?" → SENT 7일 후 삭제, Stage 2 파티셔닝 후보
- "왜 Kafka 하나로 끝내지 않았나요?" → v2에서는 실제로 하나로 통일했고, 그 근거
