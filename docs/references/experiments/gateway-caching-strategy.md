# [Architecture] MSA API Gateway 인가(Authorization) 최적화 전략

## 1. 배경 및 문제점

- **기존 구조:** 모든 요청마다 API Gateway가 `member-service`를 호출하여 인가 여부 확인.
- **문제점:** 네트워크 I/O 오버헤드 발생, 응답 지연(Latency) 증가, `member-service` 장애 시 전체 시스템 마비(Single Point of Failure).

## 2. 핵심 아키텍처: 하이브리드 로컬 캐싱

Gateway의 무상태성(Stateless)을 유지하면서 성능을 극대화하기 위해 두 가지 캐시 전략을 혼합하여 사용함.

### 2.1. 정적 권한-패턴 캐시 (`volatile Map`)

- **대상:** API 경로 패턴 및 권한 매핑 정보 (예: `ROLE_USER:GET -> [/api/v1/products/**]`)
- **구조:** `volatile Map<String, List<String>> rolePatternCache`
- **특징:**
  - 데이터 양이 적고(1,000개 미만) 모든 유저에게 공통 적용됨.
  - `volatile`을 사용하여 라이브러리 오버헤드 없이 최신 가시성 확보.
  - TTL이 필요 없으며 데이터 변경 시 전체 교체(Swapping) 방식으로 갱신.

### 2.2. 동적 유저-권한 캐시 (`Caffeine Cache`)

- **대상:** 유저별 보유 권한 정보 (예: `user_123 -> [ROLE_USER, ROLE_VIP]`)
- **구조:** `Caffeine.newBuilder().expireAfterWrite(TTL).maximumSize(SIZE).build()`
- **특징:**
  - 유저 수에 따라 데이터가 유동적이므로 **TTL**과 **최대 크기 제한**이 필수.
  - 보안을 위해 JWT에는 `User ID`만 담고, 상세 권한은 이 캐시를 통해 확인.

## 3. 인가(Authorization) 프로세스

1. **추출:** 요청에서 `Method`, `Path`, 토큰 내 `User ID`를 추출.
2. **유저 권한 조회:** `userRoleCache`에서 유저의 권한 목록을 가져옴 (없으면 `member-service` 조회 후 캐싱).
3. **패턴 매핑:**
   - 유저의 권한별로 `rolePatternCache`에서 패턴 리스트를 확보.
   - `AntPathMatcher`를 사용하여 현재 `Path`와 일치하는 패턴이 있는지 확인.
4. **결과:** 일치하면 백엔드로 라우팅, 일치하지 않으면 `403 Forbidden`.

## 4. 성능 최적화 기법 (Optimization)

1. **정적 경로 우선 순위:** 변수(`*`, `{}`)가 없는 정적 경로는 `Map` 조회를 통해 $O(1)$로 처리하여 `AntPathMatcher` 연산 생략.
2. **결과 메모이제이션 (L1 캐시):** `(User_ID, Method, Path) -> Boolean` 결과를 아주 짧은 TTL(1분 내외)로 캐싱하여 중복 연산 제거.
3. **Prefix 그룹화:** 경로의 첫 세그먼트(예: `/products`)를 인덱스로 사용하여 매칭이 필요한 패턴 범위를 최소화.

## 5. 데이터 동기화 (Cache Invalidation)

- **이벤트 기반:** `member-service`에서 권한 정책이 변경되면 Kafka/Redis Pub-Sub으로 이벤트 발행.
- **즉시 전파:** 모든 Gateway 인스턴스는 해당 이벤트를 수신하여 `rolePatternCache`를 즉시 갱신하고 관련 결과 캐시를 삭제(Evict).

## 6. 결론: "Lightweight Gateway" 원칙 준수

- 본 설계는 Gateway에 비즈니스 로직(계산, 상태 변경)을 넣는 것이 아님.
- 인프라 보안 관점에서 **'인가 정책의 집행(Enforcement)'**만을 수행하므로 Gateway의 본연의 책임에 집중하면서도 최고의 성능을 보장함.
