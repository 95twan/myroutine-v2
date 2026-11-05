# 서브: 로그 보강

> 버전 0.1 · 2026-11-05 · 6-1이 남긴 로그 빈 곳 세 가지(알림 발송 실패, 도메인 상태 전이, JWT 거부 사유)를 서브로 옮겼다(단계 ID SUB-8)

> **서브**: 로드맵에서 뺀 기능이다. Stage 1(Part 6) 뒤에 한다.
> **따르는 규칙**: 로드맵 Part 1~6의 공통 규칙과 [6-1](../03-roadmap/part6-ops-wrapup.md)의 로그 규격(`key=value` 형식, 예외는 마지막 인자, 민감정보 금지).

---

## SUB-8. 로그 보강 (S)

**목표**: 6-1의 목표(Kibana에서 `traceId` 하나로 흐름을 따라가고, 회원·주문으로 찾는다)가 실패 경로와 상태 전이에서도 끊기지 않게 한다. 6-1은 요청·이벤트·릴레이·알림 성공·잡의 정상 경로를 다뤘고, 아래 세 곳은 로그가 없다.

**왜 서브인가**: 6-1의 완료 확인(`LogFlowTest`, `LogSafetyTest`)은 충족했고, 아래는 규격에 없던 부분이라 Stage 1 범위를 넓히지 않는다.

### 빈 곳

| # | 위치 | 지금 | 깨지는 상황 |
|---|---|---|---|
| 1 | `NotificationDispatcher.dispatchBatch`의 안쪽 `catch (Exception e)` | `markFailed`로 DB에만 기록하고 `continue`한다. 성공은 `notification sent`를 찍는데 실패는 한 줄도 없다 | 메일 서버가 죽어 알림이 전부 실패해도 Kibana에는 아무것도 남지 않는다. DB를 직접 봐야 안다 |
| 2 | 도메인 상태 전이(주문 결제 완료, 환불 완료, 정산 생성, 품목 취소 등) | `http` 한 줄과 `event handled` 로그로 추정해야 한다. 상태를 바꾼 사실을 직접 남기는 곳은 `AutoConfirmService`·`OrderExpirationService`(각 한 줄)와 대사·환불 복구뿐이다 | 이벤트를 내지 않는 전이는 어떤 로그에도 안 나와, "이 주문이 언제 왜 PAID가 됐나"를 로그로 답할 수 없다 |
| 3 | `JwtProvider.parse`의 `catch (JwtException \| IllegalArgumentException e)` | `Optional.empty()`만 돌려주고 `JwtAuthenticationFilter`에도 로그가 없다. 401 status만 `http` 로그에 남는다 | 만료, 변조, 토큰 버전 불일치를 구분할 수 없어 로그인 실패 급증의 원인을 못 찾는다 |

### 할 일

세 곳 모두 구현 위치와 문구를 아직 정하지 않았다. 착수할 때 README의 "구현 때 정할 것"을 먼저 푼다.

- 1: 실패 경로에 `log.warn`을 더한다. 형식은 기존 `Failed to ... . id={}`를 따르고 마지막 인자는 예외다. 문구는 요청하면 Claude가 넣고(CLAUDE.md의 로그 메시지 예외), `catch` 구조는 사용자가 쓴다.
- 2: 로그를 더할 전이의 범위를 정한다(전부 vs 돈·재고가 움직이는 전이만). 형식은 `order paid orderId={} ...`처럼 `key=value`다.
- 3: 거부 사유를 구분해 남긴다. 토큰 값, 클레임 원문은 남기지 않는다(`LogSafetyTest`가 지키는 규칙).

### 완료 확인

`LogFlowTest`와 같은 방식(`OutputCaptureExtension`, `output.getAll()`)으로 확인한다. 케이스는 착수할 때 [로드맵 README 4.1](../03-roadmap/README.md)의 기준으로 정한다.

- 알림 발송이 예외로 실패하면 `notificationId`를 포함한 `warn` 로그가 남는다.
- 거부된 토큰으로 보낸 요청은 거부 사유를 포함한 로그를 남기고, 로그에 토큰 문자열은 없다.
- 2번은 정한 범위의 전이마다 한 케이스씩이다.
