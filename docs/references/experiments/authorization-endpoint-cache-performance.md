# Authorization Endpoint Cache 성능 테스트 최종 정리

## 1. 실험 목적

Authorization 경로에서 **endpoint 캐시 적용 여부**가 성능과 처리 한계(capacity)에 미치는 영향을 정량적으로 검증한다.

- 테스트 대상: Authorization + `GET /member-service/api/v1/members/me`
- 비교 항목: **Cache OFF vs Cache ON**
- 목표:
  - 처리량(RPS) 변화
  - 평균 및 p95 지연 시간 변화
  - 고부하 환경에서 처리 한계 개선 여부 확인

---

## 2. 테스트 환경 및 조건

- 부하 테스트 도구: **k6**
- 테스트 시간: **2분**
- Endpoint: `GET /member-service/api/v1/members/me`
- TOKEN_LIMIT: **500**
- 캐시 구조:
  - `(권한, HTTP Method) → 허용 Endpoint 목록`
  - JVM 메모리 기반 (immutable Map, volatile 참조)
- 각 케이스 **2회 반복 실행**

### 부하 조건

- VUS = **100**
- VUS = **300**

---

## 3. 테스트 설계 원칙

본 테스트는 **캐시 유무 외 모든 변수를 고정**하는 것을 원칙으로 한다.

- 단일 endpoint 사용
- 동일한 HTTP method
- 동일한 실행 경로
- 실패율 0% (401/403 없음)

이를 통해 캐시 효과를 다른 로직 분기나 404, 비즈니스 로직 노이즈와 분리하였다.

---

## 4. VUS = 100 결과 요약

### Cache OFF (Baseline)

- RPS: **~5,180 req/s**
- avg latency: **~19.3 ms**
- p90 latency: **~27.9 ms**
- p95 latency: **~33.2 ms**

### Cache ON

- RPS: **~5,830 req/s**
- avg latency: **~17.1 ms**
- p90 latency: **~25.9 ms**
- p95 latency: **~30.9 ms**

### 개선 효과

- RPS: **+12~13%**
- avg latency: **-11~12%**
- p95 latency: **-6~7%**

---

## 5. VUS = 300 결과 요약 (포화 구간)

### Cache OFF

- RPS: **~5,175 req/s**
- avg latency: **~57.9 ms**
- p90 latency: **~84.2 ms**
- p95 latency: **~96.5 ms**

### Cache ON

- RPS: **~6,015 req/s**
- avg latency: **~49.8 ms**
- p90 latency: **~77.6 ms**
- p95 latency: **~91.5 ms**

### 개선 효과

- RPS: **+16~17%**
- avg latency: **-13~14%**
- p95 latency: **-5~6%**

---

## 6. 핵심 분석

### 1) 처리 한계(capacity) 개선

- Cache OFF 상태에서는 VUS 증가 시 RPS가 증가하지 않고 latency만 상승
- Cache ON 적용 시:
  - RPS 증가
  - latency 동시 감소

→ Authorization 경로의 **실질적인 처리 한계가 확장됨**

---

### 2) endpoint 캐시의 의미

- 단순한 미세 최적화가 아님
- 고부하 환경에서:
  - CPU contention 완화
  - 큐잉 감소
  - 처리량 증가로 직결

---

### 3) 실험 신뢰성

- 단일 endpoint
- 동일 실행 경로
- 반복 실행 재현성 확보
- 실패율 0%

→ 결과를 설계 결정 근거로 사용 가능

---

## 7. 최종 결론

`GET /member-service/api/v1/members/me` 기준으로 endpoint 캐시를 적용한 결과,  
고부하(VUS 300) 환경에서 처리량은 약 **16% 증가**하고, 평균 지연 시간은 약 **14%**,  
p95 지연 시간은 약 **6% 감소**하였다.

이는 endpoint 캐시가 Authorization 경로에서 단순한 성능 미세 개선이 아니라,  
**시스템의 처리 한계를 유의미하게 확장하는 핵심 최적화 기법임을 의미한다.**

---

## 8. 설계 판단

- endpoint 캐시: **유지 및 기본 적용**
- 다음 최적화 후보:
  - `memberId → 권한` 캐시 (짧은 TTL)
