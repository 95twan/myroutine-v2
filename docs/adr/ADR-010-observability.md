# ADR-010. 관측성: ELK(로그) + Prometheus/Grafana(메트릭), traceId는 직접 전파

- 상태: 승인 (2026-10-02, 개정: 학습 범위 제한 반영)
- 관련: NFR-OBS-01~04, [02-architecture-stage1.md §6](../02-design/02-architecture-stage1.md)

## 맥락
- As-Is는 actuator health만 있었다(INF-02). Saga 정체·이벤트 유실을 탐지할 방법이 없었다.
- Stage 2(성능 개선)는 측정이 전제다. Baseline부터 지표가 쌓여 있어야 전후 비교가 가능하다.
- **제약**: 새로 학습할 기술은 ELK, Prometheus/Grafana, k6로 한정한다. 분산 트레이싱 스택(OTel Collector, Tempo, Loki)은 이번 범위에서 제외한다.

## 결정
1. **로그 = ELK**: 앱은 JSON 로그(logstash-logback-encoder)를 Logstash로 보내고, Elasticsearch에 저장하고, Kibana로 조회한다.
   - 로컬에서는 검색용 ES와 같은 클러스터를 쓰고 인덱스로 나눈다(`products` / `logs-*`). 운영이라면 분리하는 것이 맞다는 점을 기록해 둔다(로그 폭주가 검색에 영향).
2. **traceId 직접 전파** (라이브러리 없이):
   - 서블릿 필터가 요청마다 traceId를 만들어 MDC에 넣고 응답 헤더 `X-Request-Id`로 돌려준다. (요청 헤더의 값을 이어 쓰는 것은 2026-10-02 덜어내기에서 선택 사항으로 돌렸다)
   - Outbox 저장 시 현재 MDC의 traceId를 이벤트 봉투에 넣고, consumer는 처리 전에 봉투의 traceId를 MDC에 복원한다. 스케줄 작업은 실행마다 새 traceId를 만든다.
   - Kibana에서 `traceId:"..."`로 검색하면 HTTP 요청 → DB 처리 → Kafka 소비 → 알림 발송까지 한 흐름이 시간순으로 보인다.
3. **메트릭 = Prometheus + Grafana**: actuator `/actuator/prometheus`를 Prometheus가 수집하고, Grafana로 대시보드와 알림을 만든다. 기본 지표(HTTP 지연·에러, JVM, HikariCP, Kafka consumer)에 더해 비즈니스 메트릭을 둔다.
   - `outbox_ready_count`, `outbox_oldest_age_seconds`, `outbox_dead_count`
   - `kafka_dlt_records_total{topic}`
   - `payment_unknown_count`, `payment_unknown_oldest_age_seconds`
   - `saga_stuck_count{type}` (refund APPROVED/PG_CANCELLED 지속, order PAYMENT_IN_PROGRESS 지속)
   - `stock_reservation_expired_total`, `stock_balance_mismatch_count`(재고 등식 점검)
4. **외부 호출 지연**은 Micrometer `Timer`(actuator 기본 포함)로 직접 기록한다: `pg_call_seconds{operation, result}`, `mail_send_seconds`, `openai_call_seconds`.
5. Grafana 대시보드(서비스 개요, 결제·정합성, Kafka·Outbox)와 알림 규칙, Kibana 저장 검색은 리포의 `docker/` 아래에 파일로 둔다.

## 고려한 대안
| 대안 | 결정 |
|---|---|
| OTel + Tempo(분산 트레이싱) + Loki | 구간별 소요 시간 그래프(waterfall)를 볼 수 있어 가장 강력하지만 학습 범위 밖. **Stage 3(MSA)에서 서비스 간 호출이 생길 때 재검토**한다 |
| Micrometer Tracing 라이브러리로 traceId 자동 전파 | 의존성 추가만으로 되지만 동작 원리가 가려짐. 모놀리스에서는 필터 + MDC + 봉투 필드로 충분하고, 직접 구현해서 설명할 수 있는 쪽을 택함 |
| 상용 APM | 비용, 로컬 재현 불가 |
| Stage 2에서 도입 | Baseline 측정에 필요한 지표를 처음부터 쌓아야 전후 비교가 가능 |

## 결과
- "어떤 요청이 어디를 거쳤나"는 Kibana의 traceId 검색으로, "얼마나 느리고 얼마나 실패하나"는 Grafana로 본다.
- 한계: 한 요청 안에서 어느 구간이 느린지는 로그 타임스탬프와 외부 호출 Timer로 추정해야 한다. Stage 2에서 병목 분석이 부족하다고 판단되면 이 ADR을 개정한다(그 판단 자체가 Stage 2 기록 대상).
- 리소스: ES + Logstash + Kibana는 로컬 메모리를 많이 쓴다(수 GB). docker-compose에 프로필을 둬서 관측 스택을 선택적으로 띄운다.
- 정합성 지표(결과미확정 결제, 정체 Saga, 재고 불일치)를 운영 대시보드로 보여줄 수 있다. 면접에서 "정합성이 깨지면 어떻게 아나요?"에 대한 답이다.
