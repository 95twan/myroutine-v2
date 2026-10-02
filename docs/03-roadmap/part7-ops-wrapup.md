# Part 7. 관측 · 운영 · 마무리

> **끝나면**: 로그(Kibana)와 메트릭(Grafana)으로 시스템을 보고, 정합성 문제가 생기면 알림이 오고, 운영 API로 처리할 수 있다. 전체 시나리오가 E2E 테스트로 증명되고, Stage 1이 `v1.0.0`으로 릴리스된다.
> **인프라 추가**: **ELK**(Logstash, Kibana — ES는 Part 6 것을 같이 씀), **Prometheus, Grafana**
> Part 7 시작 전에 이 문서를 다시 다듬는다.

| 단계 | 제목 | 크기 |
|---|---|---|
| 7-1 | ELK: 로그 수집과 traceId 검색 | M |
| 7-2 | Prometheus · Grafana: 메트릭 · 대시보드 · 알림 | L |
| 7-3 | 운영 API | M |
| 7-4 | E2E 시나리오 · 불변식 점검 | L |
| 7-5 | README · 회고 · Stage 1 릴리스 | M |

---

## 7-1. ELK: 로그 수집과 traceId 검색 (M)
**목표**: 앱 로그를 Kibana에서 검색한다. traceId 하나로 HTTP 요청 → DB → Kafka 소비 → 알림까지 한 흐름이 보인다.
**왜 지금**: 기능이 다 모였고, 흐름이 길어져서(결제 → 이벤트 → 정산 → 알림) 로그 파일로는 따라가기 어렵다. Stage 2 성능 분석도 이것이 전제다.
**새로 등장**: Logstash(로그 수신·가공) → Elasticsearch(저장) → Kibana(검색·화면)
**할 일**
1. logback JSON 출력(logstash-logback-encoder), Logstash 파이프라인, docker-compose `observability` 프로필
2. MDC에 traceId·memberId·orderId, 스케줄 작업은 `job` 필드
3. Kibana 인덱스 패턴과 저장 검색("traceId로 흐름 보기", "ERROR만", "결제 UNKNOWN")
4. 로그에 토큰·카드·계좌·인증코드가 없는지 점검
**완료 확인**: 결제 1건의 traceId로 검색 → 체크아웃·승인·이벤트 발행·consumer 로그가 시간순으로 보임 (스크린샷)
**리뷰 때 물어볼 것**: 분산 트레이싱(Tempo 등) 없이 로그로만 추적하면 무엇이 부족한가? (ADR-010)

## 7-2. Prometheus · Grafana: 메트릭 · 대시보드 · 알림 (L)
**목표**: 서비스 상태와 **정합성 지표**를 대시보드로 보고, 문제가 생기면 알림이 온다.
**새로 등장**: Prometheus(메트릭 수집), Grafana(대시보드·알림), Micrometer(앱에서 메트릭 만들기)
**할 일**
1. `/actuator/prometheus`, Prometheus 수집 설정, Grafana 프로비저닝(파일로 관리)
2. 비즈니스 메트릭([ADR-010](../adr/ADR-010-observability.md)): outbox 대기·최고 대기 시간·DEAD, DLT 적재, 결제 UNKNOWN 수·최고 대기, 정체 Saga(주문 PAYMENT_IN_PROGRESS, 환불 PG_CANCELLED), 예약 만료 수, 원장 불일치 수, 외부 호출 시간(`pg_call_seconds` 등)
3. 원장 정합성 점검 잡(매일): `balance = Σ ledger`
4. 대시보드 3개(서비스 개요, 결제·정합성, Kafka·Outbox), 알림 규칙(DLT > 0, outbox 대기 > 60초, DEAD > 0, UNKNOWN 대기 > 10분, 정체 Saga > 0, 원장 불일치 > 0)
5. 장애 주입(가짜 PG 지연, Kafka 중단)으로 알림이 실제로 울리는지 확인
**완료 확인**: 장애 주입 → 해당 알림 발생 → 복구 → 해제 (스크린샷)
**리뷰 때 물어볼 것**: 정합성이 깨지면 어떻게 알 수 있나? 알림 임계값은 어떻게 정했나?

## 7-3. 운영 API (M)
**목표**: 알림을 받았을 때 처리할 수단. 모두 ADMIN만, 실행 이력을 로그로 남긴다.
**할 일**: DEAD outbox 재발행, DLT 조회·재처리, UNKNOWN 결제 조회·즉시 대사, FAILED 환불·알림 재처리, 정산 수동 실행. `docs/runbook.md`(알림별 의미 → 볼 곳 → 조치)
**완료 확인**: DEAD 재발행 → SENT, DLT 같은 메시지 두 번 재처리 → 결과 한 번

## 7-4. E2E 시나리오 · 불변식 점검 (L)
**목표**: [요구사항 §6](../01-requirements/README.md)의 시나리오 S1~S7을 테스트로 증명하고, 끝날 때마다 불변식(INV-01~07)을 점검한다.
**할 일**
1. 불변식 점검 유틸: 주문 금액 합, PG 승인 반영, 재고 등식, 원장 합계, 환불 한도, 정산 1회
2. 시나리오: 복합결제 주문 / 결제 중 이탈 / 결제 결과 불확실 / 품목 취소 / 구독 회차 / 정산 / 가게 폐업
3. 장애 시나리오: 체크아웃 중 Kafka 중단 → 복구 후 이벤트 처리 완료, 정합성 유지
**완료 확인**: 시나리오 전체 통과, CI 시간 기록

## 7-5. README · 회고 · Stage 1 릴리스 (M)
**목표**: 리포만 보고 프로젝트를 이해하고 실행할 수 있다. 이력서에 쓸 근거가 정리된다.
**할 일**
1. README: 소개, 아키텍처 다이어그램, 실행 방법, 문서 안내, 핵심 설계 요약(정합성·동시성)
2. `docs/retrospectives/stage1.md`: 설계와 달라진 점과 이유, 리뷰에서 반복된 지적, 어려웠던 점
3. 이력서 bullet 초안(근거 링크 포함), 면접 예상 질문 답변 초안
4. Stage 2 준비: 시드 데이터 규모·생성 방식, k6 시나리오 목록 → Stage 2 로드맵 작성
5. develop → main, 태그 `v1.0.0`
**리뷰 때 물어볼 것**: 이력서 문장마다 근거(테스트, 문서, 수치)가 있는가?
