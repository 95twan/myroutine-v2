# ADR-007. 데이터: 단일 Postgres, 모듈별 schema, Flyway, UUIDv7, 정수 금액

- 상태: 승인 (2026-10-02)
- 관련: [03-erd.md](../02-design/03-erd.md)

## 맥락
- As-Is: 단일 Postgres + 서비스별 schema였지만 DDL은 수동이었고(`docs/ddl/*.sql`), 일부 서비스가 `sql.init.mode: always`를 썼다. order 테이블에 PK가 없었다(ORD-11). batch가 shop schema에 직접 접속했다(BAT-03). 금액은 `numeric(38,2)`를 `long`으로 반올림해 오갔다.

## 결정
1. Postgres 17(pgvector) 하나, **모듈별 schema**. 다른 schema 조회·조인과 schema 간 FK를 금지한다.
2. **Flyway**로 마이그레이션한다. 모듈별 위치(`db/migration/{module}`)에 두고, 운영 프로필에서는 `ddl-auto: validate`를 쓴다.
3. ID는 앱에서 생성하는 **UUIDv7**이다. 시간 순서라 B-tree 지역성이 좋고, 샤딩·서비스 분리 시 충돌이 없다.
4. 금액은 **원 단위 BIGINT**(`Money` 값 객체)로 다룬다. 비율은 basis point 정수로 표현한다(수수료 500 = 5%).
5. 불변식 일부를 DB 제약으로 강제한다: CHECK(잔액 ≥ 0, 재고 ≥ 0, 환불액 ≤ 결제액), UNIQUE(멱등 키들).
6. Stage 2에서 레플리카를 추가하면 `@Transactional(readOnly = true)`를 replica로 라우팅한다. 이를 위해 Stage 1부터 조회 유스케이스에 readOnly를 정확히 표기한다.

## 고려한 대안
| 대안 | 기각 이유 |
|---|---|
| 모듈별 DB 인스턴스 | 로컬 트랜잭션(ADR-002)을 쓸 수 없음. Stage 3에서 할 일 |
| 단일 schema | 모듈 소유권이 흐려지고, Stage 3에서 데이터 분리가 어려움 |
| BIGINT IDENTITY | 성능은 좋지만 ID 노출 시 추측 가능, 분리·샤딩 시 충돌. UUIDv7로 지역성 문제 해결 |
| UUIDv4 | 무작위라 인덱스 페이지 분할, 캐시 효율 저하 → Stage 2에서 v4와 v7 비교 측정도 가능 |
| BigDecimal 금액 | 원화는 소수점이 없음. 반올림 실수를 원천 차단 |

## 결과
- Stage 3의 데이터 분리 = schema 단위로 dump·이관이 가능하다.
- Stage 2 파티셔닝 대상 테이블(주문·원장)은 파티션 키가 PK에 포함돼야 하므로, 파티셔닝 시 PK를 `(id, created_at)`으로 바꾸는 마이그레이션이 필요하다. 이것도 Stage 2 기록 대상이다.
